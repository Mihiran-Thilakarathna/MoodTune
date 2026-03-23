"""
generate_emotion_model.py
─────────────────────────
Trains a CNN emotion recognition model on the FER2013 dataset and
exports it as a TFLite flatbuffer file matching what FaceAnalyzer expects:

  Input  : float32 [1, 48, 48, 1]   (greyscale normalised to [0, 1])
  Output : float32 [1, 7]            (softmax scores)
  Labels : angry, disgusted, fearful, happy, neutral, sad, surprised

HOW TO USE:
  1. Download fer2013.csv from https://www.kaggle.com/datasets/msambare/fer2013
     and place it in the same directory as this script.
  2. Install dependencies:
         pip install tensorflow pandas numpy scikit-learn
  3. Run:
         python generate_emotion_model.py
"""

import os
import numpy as np
import pandas as pd
import tensorflow as tf
from tensorflow.keras.callbacks import EarlyStopping, ReduceLROnPlateau, ModelCheckpoint
from sklearn.utils import class_weight

# ── Hyper-parameters ─────────────────────────────────────────────────────────
INPUT_SHAPE  = (48, 48, 1)
NUM_CLASSES  = 7
BATCH_SIZE   = 64
EPOCHS       = 50

CSV_PATH = os.path.join(os.path.dirname(__file__), "fer2013.csv")
OUT_PATH = r"e:\Rajarata University\Acedmic\Projects\MoodTune\app\src\main\assets\emotion_model.tflite"

EMOTION_LABELS = ["angry", "disgusted", "fearful", "happy", "sad", "surprised", "neutral"]

# ── Load FER2013 CSV ──────────────────────────────────────────────────────────
def load_fer2013(csv_path):
    """
    Reads fer2013.csv which has columns: emotion, pixels, Usage
    Returns (X_train, y_train, X_val, y_val) as float32 numpy arrays.
    """
    print(f"Loading FER2013 from: {csv_path}")
    df = pd.read_csv(csv_path)

    def row_to_image(pixel_str):
        arr = np.array(pixel_str.split(), dtype=np.float32) / 255.0
        return arr.reshape(48, 48, 1)

    train_df = df[df["Usage"] == "Training"]
    val_df   = df[df["Usage"] == "PublicTest"]

    X_train = np.stack(train_df["pixels"].apply(row_to_image).values)
    y_train = tf.keras.utils.to_categorical(train_df["emotion"].values, NUM_CLASSES)

    X_val = np.stack(val_df["pixels"].apply(row_to_image).values)
    y_val = tf.keras.utils.to_categorical(val_df["emotion"].values, NUM_CLASSES)

    print(f"  Training samples  : {len(X_train)}")
    print(f"  Validation samples: {len(X_val)}")
    return X_train, y_train, X_val, y_val


# ── Model definition (standard FER-2013 CNN) ─────────────────────────────────
def build_fer_model():
    input_face = tf.keras.Input(shape=INPUT_SHAPE, name="input_face")

    # ── Augmentation Layers (Active during training only) ──
    # Helps with generalization for rotation, zoom, contrast
    x = tf.keras.layers.RandomFlip("horizontal")(input_face)
    x = tf.keras.layers.RandomRotation(0.10)(x)  # ±10% rotation
    x = tf.keras.layers.RandomZoom(0.10)(x)
    x = tf.keras.layers.RandomContrast(0.1)(x)

    # ── Block 1 ──
    x = tf.keras.layers.Conv2D(64, (3, 3), padding="same", use_bias=False)(x)
    x = tf.keras.layers.BatchNormalization()(x)
    x = tf.keras.layers.Activation("relu")(x)
    x = tf.keras.layers.Conv2D(64, (3, 3), padding="same", use_bias=False)(x)
    x = tf.keras.layers.BatchNormalization()(x)
    x = tf.keras.layers.Activation("relu")(x)
    x = tf.keras.layers.MaxPooling2D(pool_size=(2, 2))(x)
    x = tf.keras.layers.Dropout(0.2)(x)

    # ── Block 2 ──
    x = tf.keras.layers.Conv2D(128, (3, 3), padding="same", use_bias=False)(x)
    x = tf.keras.layers.BatchNormalization()(x)
    x = tf.keras.layers.Activation("relu")(x)
    x = tf.keras.layers.Conv2D(128, (3, 3), padding="same", use_bias=False)(x)
    x = tf.keras.layers.BatchNormalization()(x)
    x = tf.keras.layers.Activation("relu")(x)
    x = tf.keras.layers.MaxPooling2D(pool_size=(2, 2))(x)
    x = tf.keras.layers.Dropout(0.3)(x)

    # ── Block 3 ──
    x = tf.keras.layers.Conv2D(256, (3, 3), padding="same", use_bias=False)(x)
    x = tf.keras.layers.BatchNormalization()(x)
    x = tf.keras.layers.Activation("relu")(x)
    x = tf.keras.layers.Conv2D(256, (3, 3), padding="same", use_bias=False)(x)
    x = tf.keras.layers.BatchNormalization()(x)
    x = tf.keras.layers.Activation("relu")(x)
    x = tf.keras.layers.MaxPooling2D(pool_size=(2, 2))(x)
    x = tf.keras.layers.Dropout(0.4)(x)

    # ── Block 4 ──
    x = tf.keras.layers.Conv2D(512, (3, 3), padding="same", use_bias=False)(x)
    x = tf.keras.layers.BatchNormalization()(x)
    x = tf.keras.layers.Activation("relu")(x)
    x = tf.keras.layers.MaxPooling2D(pool_size=(2, 2))(x)
    x = tf.keras.layers.Dropout(0.5)(x)

    # ── Dense Layers ──
    x = tf.keras.layers.Flatten()(x)

    # L2 regularization to prevent overfitting to the "happy/neutral" majority
    x = tf.keras.layers.Dense(512, kernel_regularizer=tf.keras.regularizers.l2(0.02), use_bias=False)(x)
    x = tf.keras.layers.BatchNormalization()(x)
    x = tf.keras.layers.Activation("relu")(x)
    x = tf.keras.layers.Dropout(0.5)(x)

    x = tf.keras.layers.Dense(256, kernel_regularizer=tf.keras.regularizers.l2(0.02), use_bias=False)(x)
    x = tf.keras.layers.BatchNormalization()(x)
    x = tf.keras.layers.Activation("relu")(x)
    x = tf.keras.layers.Dropout(0.5)(x)

    outputs = tf.keras.layers.Dense(NUM_CLASSES, activation="softmax", name="emotion")(x)

    return tf.keras.Model(input_face, outputs, name="FER_CNN_Improved")


# ── Main ──────────────────────────────────────────────────────────────────────
if not os.path.exists(CSV_PATH):
    print(f"\n❌  fer2013.csv not found at: {CSV_PATH}")
    print("    Please download it from https://www.kaggle.com/datasets/msambare/fer2013")
    print("    and place it next to this script, then re-run.\n")
    exit(1)

# Load data
X_train, y_train, X_val, y_val = load_fer2013(CSV_PATH)

# Compute class weights key to solving the imbalance (Happy/Neutral dominance)
# This gives higher weight to under-represented classes like Disgusted, Fearful, Sad
y_ints = np.argmax(y_train, axis=1)
class_weights = class_weight.compute_class_weight(
    class_weight="balanced",
    classes=np.unique(y_ints),
    y=y_ints
)
class_weight_dict = dict(enumerate(class_weights))
print(f"\n⚖️  Computed Class Weights: {class_weight_dict}")

# Build model
print("\nBuilding FER CNN model...")
model = build_fer_model()
model.summary()

# Compile
model.compile(
    optimizer=tf.keras.optimizers.Adam(learning_rate=0.001),
    loss="categorical_crossentropy",
    metrics=["accuracy"]
)

# ── Data augmentation using tf.data (TF 2.20 compatible) ────────────────────
# NOTE: We moved augmentation INTO the model (layers.Random...) so we don't
# need a complex map function here anymore.
def preprocess(image, label):
    """Basic preprocessing if needed."""
    return image, label

AUTOTUNE = tf.data.AUTOTUNE

train_ds = (
    tf.data.Dataset.from_tensor_slices((X_train, y_train))
    .shuffle(buffer_size=len(X_train), seed=42)
    # .map(augment, num_parallel_calls=AUTOTUNE)  <-- Removed, using layers now
    .batch(BATCH_SIZE)
    .prefetch(AUTOTUNE)
)

val_ds = (
    tf.data.Dataset.from_tensor_slices((X_val, y_val))
    .batch(BATCH_SIZE)
    .prefetch(AUTOTUNE)
)

# Callbacks
callbacks = [
    EarlyStopping(monitor="val_accuracy", patience=7, restore_best_weights=True, verbose=1),
    ReduceLROnPlateau(monitor="val_loss", factor=0.5, patience=3, min_lr=1e-6, verbose=1),
    ModelCheckpoint("best_fer_model.keras", monitor="val_accuracy", save_best_only=True, verbose=1),
]

# Train
print("\nTraining... (this may take 30–60 min on CPU)")
history = model.fit(
    train_ds,
    epochs=EPOCHS,
    validation_data=val_ds,
    callbacks=callbacks,
    verbose=1,
    class_weight=class_weight_dict
)

val_acc = max(history.history["val_accuracy"])
print(f"\n✅  Best validation accuracy: {val_acc * 100:.2f}%")

# ── Convert to TFLite ─────────────────────────────────────────────────────────
print("\nConverting to TFLite...")
converter = tf.lite.TFLiteConverter.from_keras_model(model)
# Do NOT use Optimize.DEFAULT — dynamic-range quantization produces
# FULLY_CONNECTED op version 12 which requires TFLite runtime >= 2.17.
# Keeping the model as float32 ensures compatibility with TFLite 2.16.x.
tflite_model = converter.convert()

# ── Save to assets ────────────────────────────────────────────────────────────
os.makedirs(os.path.dirname(OUT_PATH), exist_ok=True)
with open(OUT_PATH, "wb") as f:
    f.write(tflite_model)

size_kb = len(tflite_model) / 1024
print(f"\n✅  Saved to : {OUT_PATH}")
print(f"   File size : {size_kb:.1f} KB")

# ── Quick load-back verification ─────────────────────────────────────────────
print("\nVerifying saved TFLite model...")
interpreter = tf.lite.Interpreter(model_path=OUT_PATH)
interpreter.allocate_tensors()

in_details  = interpreter.get_input_details()
out_details = interpreter.get_output_details()

print(f"  Input  detail : {in_details[0]['shape']}  dtype={in_details[0]['dtype']}")
print(f"  Output detail : {out_details[0]['shape']}  dtype={out_details[0]['dtype']}")

dummy = np.zeros((1, 48, 48, 1), dtype=np.float32)
interpreter.set_tensor(in_details[0]['index'], dummy)
interpreter.invoke()
result = interpreter.get_tensor(out_details[0]['index'])
print(f"  Sample output : {result}")
print("\n🎉  All done! Rebuild your Android app to use the trained model.")
