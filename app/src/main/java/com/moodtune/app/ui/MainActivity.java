package com.moodtune.app.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.AnimationUtils;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.common.util.concurrent.ListenableFuture;
import com.moodtune.app.R;
import com.moodtune.app.data.model.EmotionResult;
import com.moodtune.app.databinding.ActivityMainBinding;
import com.moodtune.app.utils.FaceAnalyzer;
import com.moodtune.app.viewmodel.ScanViewModel;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MainActivity — The face-scanning screen.
 *
 * Flow:
 *  1. Request CAMERA permission if not already granted
 *  2. Start CameraX with the front camera
 *  3. On "Scan My Mood" button tap, enable ImageAnalysis
 *  4. Feed frames to FaceAnalyzer (ML Kit + TFLite)
 *  5. On successful emotion detection, navigate to ResultActivity
 *
 * MVVM: UI state is driven by {@link ScanViewModel}.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    // How many consecutive frames we analyse before giving up (no-face case)
    private static final int MAX_ANALYSIS_FRAMES = 30;

    // ── View binding & ViewModel ──────────────────────────────────────────────
    private ActivityMainBinding binding;
    private ScanViewModel       viewModel;

    // ── Camera & analysis ─────────────────────────────────────────────────────
    private ProcessCameraProvider       cameraProvider;
    private ImageAnalysis               imageAnalysis;
    private FaceAnalyzer                faceAnalyzer;
    private final ExecutorService       cameraExecutor = Executors.newSingleThreadExecutor();

    /** Guards against multiple successful callbacks firing simultaneously */
    private final AtomicBoolean         resultDelivered = new AtomicBoolean(false);

    /** Counts frames analysed during a single scan attempt */
    private int frameCount = 0;

    // ── Permission launcher ───────────────────────────────────────────────────
    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    showCameraUI();
                    startCamera();
                } else {
                    showPermissionDeniedUI();
                }
            });

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding  = ActivityMainBinding.inflate(getLayoutInflater());
        viewModel = new ViewModelProvider(this).get(ScanViewModel.class);
        setContentView(binding.getRoot());

        faceAnalyzer = new FaceAnalyzer(this);

        setupObservers();
        setupClickListeners();
        checkCameraPermission();

        // Start the scan ring pulse animation
        binding.outerPulseRing.startAnimation(
                AnimationUtils.loadAnimation(this, R.anim.pulse)
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        faceAnalyzer.release();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Permission
    // ─────────────────────────────────────────────────────────────────────────

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            showCameraUI();
            startCamera();
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void showCameraUI() {
        binding.permissionOverlay.setVisibility(View.GONE);
        binding.scanContainer.setVisibility(View.VISIBLE);
        binding.statusCard.setVisibility(View.VISIBLE);
        binding.btnScanMood.setVisibility(View.VISIBLE);
    }

    private void showPermissionDeniedUI() {
        binding.permissionOverlay.setVisibility(View.VISIBLE);
        binding.scanContainer.setVisibility(View.GONE);
        binding.btnScanMood.setVisibility(View.GONE);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Camera setup (CameraX)
    // ─────────────────────────────────────────────────────────────────────────

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "CameraX init failed: " + e.getMessage());
                viewModel.onScanError("Camera initialisation failed.");
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null) return;

        // Preview use case — renders to PreviewView
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(binding.cameraPreview.getSurfaceProvider());

        // ImageAnalysis use case — disabled until user taps "Scan"
        imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        // Analyser is NOT set here — we set it on the button tap

        try {
            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    preview,
                    imageAnalysis
            );
        } catch (Exception e) {
            Log.e(TAG, "Camera binding failed: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Button click / scan logic
    // ─────────────────────────────────────────────────────────────────────────

    private void setupClickListeners() {
        binding.btnScanMood.setOnClickListener(v -> beginScan());
        binding.btnGrantPermission.setOnClickListener(v ->
                permissionLauncher.launch(Manifest.permission.CAMERA)
        );
    }

    private void beginScan() {
        // Reset state
        resultDelivered.set(false);
        frameCount = 0;
        viewModel.startScanning();

        // Attach the image analyser — starts pulling frames
        imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeFrame);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Frame analysis
    // ─────────────────────────────────────────────────────────────────────────

    @androidx.camera.core.ExperimentalGetImage
    private void analyzeFrame(@NonNull ImageProxy imageProxy) {
        // If we already delivered a result, ignore remaining queued frames
        if (resultDelivered.get()) {
            imageProxy.close();
            return;
        }

        frameCount++;

        // Give up after MAX_ANALYSIS_FRAMES with no face detected
        if (frameCount > MAX_ANALYSIS_FRAMES) {
            imageProxy.close();
            clearAnalyzer();
            runOnUiThread(() -> viewModel.onNoFaceDetected());
            return;
        }

        faceAnalyzer.analyze(imageProxy, new FaceAnalyzer.AnalysisCallback() {
            @Override
            public void onSuccess(@NonNull EmotionResult result) {
                if (resultDelivered.compareAndSet(false, true)) {
                    clearAnalyzer();
                    runOnUiThread(() -> viewModel.onEmotionDetected(result));
                }
            }

            @Override
            public void onNoFaceDetected() {
                // Keep trying until frameCount limit is reached
            }

            @Override
            public void onError(@NonNull Exception e) {
                Log.e(TAG, "Analysis error: " + e.getMessage());
                if (resultDelivered.compareAndSet(false, true)) {
                    clearAnalyzer();
                    runOnUiThread(() -> viewModel.onScanError(e.getMessage()));
                }
            }
        });
    }

    /** Detaches the analyser so no more frames are processed */
    private void clearAnalyzer() {
        if (imageAnalysis != null) {
            imageAnalysis.clearAnalyzer();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ViewModel observers → UI updates
    // ─────────────────────────────────────────────────────────────────────────

    private void setupObservers() {
        viewModel.getScanState().observe(this, state -> {
            switch (state) {
                case IDLE:
                    setIdleUI();
                    break;
                case SCANNING:
                    setScanningUI();
                    break;
                case DETECTED:
                    // Navigation is triggered by observing emotionResult below
                    break;
                case NO_FACE:
                    setNoFaceUI();
                    break;
                case ERROR:
                    setIdleUI(); // Reset to idle so user can retry
                    break;
            }
        });

        viewModel.getEmotionResult().observe(this, result -> {
            if (result != null) {
                navigateToResult(result);
            }
        });

        viewModel.getErrorMessage().observe(this, message -> {
            if (message != null && !message.isEmpty()) {
                binding.tvStatusTitle.setText(message);
                binding.tvStatusSubtitle.setText("Please try again");
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // UI state helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void setIdleUI() {
        binding.btnScanMood.setEnabled(true);
        binding.btnScanMood.setText(getString(R.string.scan_mood_button));
        binding.lottieScanning.setVisibility(View.GONE);
        binding.scanProgress.setVisibility(View.GONE);
        binding.tvStatusTitle.setText(getString(R.string.align_face_hint));
        binding.tvStatusSubtitle.setText("We'll detect your emotion using AI");
    }

    private void setScanningUI() {
        binding.btnScanMood.setEnabled(false);
        binding.btnScanMood.setText(getString(R.string.scanning_text));
        binding.lottieScanning.setVisibility(View.VISIBLE);
        binding.lottieScanning.playAnimation();
        binding.scanProgress.setVisibility(View.VISIBLE);
        binding.tvStatusTitle.setText(getString(R.string.detecting_emotion));
        binding.tvStatusSubtitle.setText("Hold still…");
    }

    private void setNoFaceUI() {
        binding.lottieScanning.setVisibility(View.GONE);
        binding.scanProgress.setVisibility(View.GONE);
        binding.tvStatusTitle.setText(getString(R.string.no_face_detected));
        binding.tvStatusSubtitle.setText("Make sure your face is clearly visible");
        binding.btnScanMood.setEnabled(true);
        binding.btnScanMood.setText(getString(R.string.scan_mood_button));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Navigation
    // ─────────────────────────────────────────────────────────────────────────

    private void navigateToResult(@NonNull EmotionResult result) {
        Intent intent = new Intent(this, ResultActivity.class);
        intent.putExtra(ResultActivity.EXTRA_EMOTION_LABEL,       result.getLabel());
        intent.putExtra(ResultActivity.EXTRA_EMOTION_CONFIDENCE,  result.getConfidence());
        startActivity(intent);

        // Slide up transition
        overridePendingTransition(R.anim.slide_up, R.anim.fade_in);

        // Reset ViewModel so user can scan again on back-press
        viewModel.resetToIdle();
    }
}
