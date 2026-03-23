package com.moodtune.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.animation.AnimationUtils;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

import com.moodtune.app.R;
import com.moodtune.app.databinding.ActivitySplashBinding;

/**
 * SplashActivity — Entry point, shown at app launch.
 *
 * Displays an animated Lottie splash screen for ~2.5 seconds,
 * then transitions to MainActivity with a fade animation.
 *
 * Uses the AndroidX Core SplashScreen API to keep the system splash
 * consistent on Android 12+ while showing our custom animated version.
 */
public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_DURATION_MS = 2800L;

    private ActivitySplashBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Install system splash screen (no-op on < API 31)
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);

        super.onCreate(savedInstanceState);
        binding = ActivitySplashBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Kick off launch animation sequence
        startSplashAnimations();

        // Schedule navigation to MainActivity
        binding.getRoot().postDelayed(this::navigateToMain, SPLASH_DURATION_MS);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Animations
    // ─────────────────────────────────────────────────────────────────────────

    private void startSplashAnimations() {
        // App title — fade in after a short delay
        binding.appTitle.setAlpha(0f);
        binding.appTitle.animate()
                .alpha(1f)
                .setStartDelay(600)
                .setDuration(700)
                .start();

        // Tagline — fade in slightly after title
        binding.appTagline.setAlpha(0f);
        binding.appTagline.animate()
                .alpha(1f)
                .setStartDelay(1000)
                .setDuration(700)
                .start();

        // Decorative glow blobs — gentle scale pulse
        binding.glowTopLeft.animate()
                .scaleX(1.2f).scaleY(1.2f)
                .setDuration(2000)
                .start();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Navigation
    // ─────────────────────────────────────────────────────────────────────────

    private void navigateToMain() {
        if (isFinishing() || isDestroyed()) return;

        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);

        // Custom crossfade transition
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}
