package com.moodtune.app.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageProxy;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.moodtune.app.data.model.EmotionResult;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.ops.ResizeOp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;

/**
 * Analyzes camera frames to detect faces and predict emotions using a TFLite model.
 * Supports 6 emotion classes.
 */
public class FaceAnalyzer {

    private static final String TAG = "FaceAnalyzer";
    private static final String MODEL_FILE = "emotion_model.tflite";
    private static final int INPUT_SIZE = 48;

    // Emotion labels matching the model's output indices (Standard FER2013 - 7 classes)
    private static final String[] EMOTION_LABELS = {
            "angry", "disgusted", "fearful", "happy", "sad", "surprised", "neutral"
    };

    private final FaceDetector faceDetector;
    private Interpreter tfliteInterpreter;

    public interface AnalysisCallback {
        void onSuccess(@NonNull EmotionResult result);
        void onNoFaceDetected();
        void onError(@NonNull Exception e);
    }

    public FaceAnalyzer(@NonNull Context context) {
        // Setup ML Kit face detector for fast bounding-box detection
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setMinFaceSize(0.15f)
                .enableTracking()
                .build();

        faceDetector = FaceDetection.getClient(options);


        // Load the TFLite model from assets
        try {
            MappedByteBuffer modelBuffer = FileUtil.loadMappedFile(context, MODEL_FILE);
            Interpreter.Options tfliteOptions = new Interpreter.Options();
            tfliteOptions.setNumThreads(2);
            tfliteInterpreter = new Interpreter(modelBuffer, tfliteOptions);
            Log.d(TAG, "TFLite model loaded successfully.");
        } catch (IOException e) {
            Log.e(TAG, "Failed to load TFLite model: " + e.getMessage());
        }
    }

    @androidx.camera.core.ExperimentalGetImage
    public void analyze(@NonNull ImageProxy imageProxy, @NonNull AnalysisCallback callback) {
        if (tfliteInterpreter == null) {
            imageProxy.close();
            callback.onError(new IllegalStateException("TFLite model not loaded"));
            return;
        }

        InputImage inputImage = InputImage.fromMediaImage(
                imageProxy.getImage(),
                imageProxy.getImageInfo().getRotationDegrees()
        );

        // Detect faces
        faceDetector.process(inputImage)
                .addOnSuccessListener(faces -> {
                    if (faces.isEmpty()) {
                        imageProxy.close();
                        callback.onNoFaceDetected();
                        return;
                    }

                    // Get the largest face in the frame
                    Face largestFace = getLargestFace(faces);

                    // Convert frame to Bitmap
                    Bitmap frameBitmap = imageProxyToBitmap(imageProxy);
                    imageProxy.close();

                    if (frameBitmap == null) {
                        callback.onError(new RuntimeException("Could not convert camera frame to bitmap"));
                        return;
                    }

                    // Crop the face region
                    Bitmap faceBitmap = cropFace(frameBitmap, largestFace.getBoundingBox());
                    if (faceBitmap == null) {
                        callback.onError(new RuntimeException("Could not crop face region"));
                        return;
                    }

                    // Predict emotion using the custom TFLite model
                    try {
                        EmotionResult result = runInference(faceBitmap);
                        callback.onSuccess(result);
                    } catch (Exception e) {
                        Log.e(TAG, "TFLite inference error: " + e.getMessage());
                        callback.onError(e);
                    }
                })
                .addOnFailureListener(e -> {
                    imageProxy.close();
                    Log.e(TAG, "ML Kit face detection failed: " + e.getMessage());
                    callback.onError(e);
                });
    }

    public void release() {
        faceDetector.close();
        if (tfliteInterpreter != null) {
            tfliteInterpreter.close();
            tfliteInterpreter = null;
        }
    }

    private Face getLargestFace(@NonNull java.util.List<Face> faces) {
        Face largest = faces.get(0);
        for (Face face : faces) {
            Rect b = face.getBoundingBox();
            Rect lb = largest.getBoundingBox();
            if (b.width() * b.height() > lb.width() * lb.height()) {
                largest = face;
            }
        }
        return largest;
    }

    private Bitmap imageProxyToBitmap(@NonNull ImageProxy imageProxy) {
        try {
            ImageProxy.PlaneProxy[] planes = imageProxy.getPlanes();
            ByteBuffer yBuffer  = planes[0].getBuffer();
            ByteBuffer uBuffer  = planes[1].getBuffer();
            ByteBuffer vBuffer  = planes[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            byte[] nv21 = new byte[ySize + uSize + vSize];
            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            YuvImage yuvImage = new YuvImage(
                    nv21, ImageFormat.NV21,
                    imageProxy.getWidth(), imageProxy.getHeight(), null
            );

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(
                    new Rect(0, 0, imageProxy.getWidth(), imageProxy.getHeight()),
                    90, out
            );

            byte[] jpegBytes = out.toByteArray();
            Bitmap bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.length);

            // Rotate bitmap to match camera orientation
            int rotation = imageProxy.getImageInfo().getRotationDegrees();
            if (rotation != 0) {
                Matrix matrix = new Matrix();
                matrix.postRotate(rotation);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0,
                        bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            }
            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "imageProxyToBitmap error: " + e.getMessage());
            return null;
        }
    }

    private Bitmap cropFace(@NonNull Bitmap bitmap, @NonNull Rect box) {
        try {
            // Add 10% padding around the detected face (reduced from 20% for tighter crop)
            int padding = (int) (box.width() * 0.10f);
            int left   = Math.max(0, box.left   - padding);
            int top    = Math.max(0, box.top    - padding);
            int right  = Math.min(bitmap.getWidth(),  box.right  + padding);
            int bottom = Math.min(bitmap.getHeight(), box.bottom + padding);

            int width  = right  - left;
            int height = bottom - top;

            if (width <= 0 || height <= 0) return null;
            return Bitmap.createBitmap(bitmap, left, top, width, height);
        } catch (Exception e) {
            Log.e(TAG, "cropFace error: " + e.getMessage());
            return null;
        }
    }

    /**
     * Runs TFLite model to predict emotion.
     * Input: 48x48 grayscale image, normalized [0, 1]
     * Output: Probabilities for 6 emotions.
     */
    private EmotionResult runInference(@NonNull Bitmap faceBitmap) {
        Bitmap resized = Bitmap.createScaledBitmap(faceBitmap, INPUT_SIZE, INPUT_SIZE, true);

        // Convert image to grayscale and normalize [0, 1]
        float[][][][] input = new float[1][INPUT_SIZE][INPUT_SIZE][1];
        for (int y = 0; y < INPUT_SIZE; y++) {
            for (int x = 0; x < INPUT_SIZE; x++) {
                int pixel = resized.getPixel(x, y);
                float r = ((pixel >> 16) & 0xFF) / 255.0f;
                float g = ((pixel >> 8)  & 0xFF) / 255.0f;
                float b = (pixel         & 0xFF) / 255.0f;
                input[0][y][x][0] = 0.299f * r + 0.587f * g + 0.114f * b;
            }
        }

        // Get predictions for the 6 emotion classes
        float[][] output = new float[1][EMOTION_LABELS.length];
        tfliteInterpreter.run(input, output);

        // Find the emotion with the highest probability
        int bestIndex = 0;
        float bestScore = output[0][0];
        for (int i = 1; i < EMOTION_LABELS.length; i++) {
            if (output[0][i] > bestScore) {
                bestScore = output[0][i];
                bestIndex = i;
            }
        }

        Log.d(TAG, "TFLite emotion: " + EMOTION_LABELS[bestIndex] + " (" + bestScore + ")");
        return new EmotionResult(EMOTION_LABELS[bestIndex], bestScore);
    }
}