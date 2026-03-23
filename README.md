# MoodTune — Android App

## Overview
MoodTune detects your facial emotion using the front camera and plays a matching YouTube song.

## Architecture
```
MVVM  (Model → Repository → ViewModel → Activity/View)
```

## Folder Structure
```
app/src/main/
├── assets/
│   ├── emotion_model.tflite   ← ⚠️  YOU MUST ADD THIS FILE (see below)
│   ├── music_wave.json        (Lottie – splash animation)
│   ├── face_scan.json         (Lottie – scan overlay animation)
│   ├── music_loading.json     (Lottie – loading animation)
│   └── camera_permission.json (Lottie – permission prompt animation)
├── java/com/moodtune/app/
│   ├── data/
│   │   ├── model/
│   │   │   ├── EmotionResult.java
│   │   │   ├── VideoData.java
│   │   │   └── YouTubeSearchResponse.java
│   │   └── repository/
│   │       └── YouTubeRepository.java
│   ├── network/
│   │   ├── RetrofitClient.java
│   │   └── YouTubeApiService.java
│   ├── ui/
│   │   ├── SplashActivity.java
│   │   ├── MainActivity.java
│   │   └── ResultActivity.java
│   ├── utils/
│   │   ├── FaceAnalyzer.java
│   │   ├── MoodColorHelper.java
│   │   └── NetworkUtils.java
│   └── viewmodel/
│       ├── ScanViewModel.java
│       └── ResultViewModel.java
└── res/
    ├── anim/          (slide_up, slide_down, fade_in, pulse)
    ├── drawable/      (backgrounds, icons)
    ├── font/          (Poppins — see note below)
    ├── layout/        (activity_splash, activity_main, activity_result)
    └── values/        (colors, strings, themes, dimens)
```

---

## ⚠️ Required Manual Steps

### 1. Add TFLite Emotion Model
Place your `emotion_model.tflite` file in:
```
app/src/main/assets/emotion_model.tflite
```
The model must accept input shape `[1, 48, 48, 1]` (grayscale face, normalised 0–1)  
and output shape `[1, 7]` with labels in this order:
```
angry, disgusted, fearful, happy, neutral, sad, surprised
```
A compatible pre-trained model: https://github.com/omar178/Emotion-recognition

### 2. Add Poppins Fonts
Download from https://fonts.google.com/specimen/Poppins and place in `app/src/main/res/font/`:
- `poppins_regular.ttf`
- `poppins_medium.ttf`
- `poppins_semibold.ttf`
- `poppins_bold.ttf`

### 3. Replace Lottie Animations (Optional but Recommended)
Download high-quality animations from https://lottiefiles.com and replace the placeholder JSONs in `assets/`:
- `music_wave.json`   → search "music wave" or "audio equalizer"
- `face_scan.json`    → search "face scan" or "face recognition"
- `music_loading.json`→ search "music loading"

### 4. Update local.properties
Set your Android SDK path in `local.properties`.

### 5. Gradle Wrapper
Run from your project root to generate the wrapper:
```
gradle wrapper --gradle-version 8.2
```
Or open the project in Android Studio which will handle this automatically.

---

## Build & Run
1. Open the project in **Android Studio Hedgehog** (or later)
2. Complete the manual steps above
3. Connect a physical device (camera required) or use emulator with virtual front camera
4. Click **Run ▶**

---

## API Key
The YouTube Data API key is embedded in `app/build.gradle` via `BuildConfig.YOUTUBE_API_KEY`.  
For production, move it to `local.properties` and read it via `buildConfigField`.
