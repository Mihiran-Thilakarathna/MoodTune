package com.moodtune.app.ui;

import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.moodtune.app.R;
import com.moodtune.app.data.model.EmotionResult;
import com.moodtune.app.data.model.JamendoResponse;
import com.moodtune.app.data.model.YouTubeResponse;
import com.moodtune.app.databinding.ActivityResultBinding;
import com.moodtune.app.utils.MoodColorHelper;
import com.moodtune.app.viewmodel.ResultViewModel;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ResultActivity extends AppCompatActivity {

    public static final String EXTRA_EMOTION_LABEL      = "extra_emotion_label";
    public static final String EXTRA_EMOTION_CONFIDENCE = "extra_emotion_confidence";

    private ActivityResultBinding binding;
    private ResultViewModel       viewModel;
    private MediaPlayer           mediaPlayer;

    // ── Playlist state ────────────────────────────────────────────────────────
    private List<JamendoResponse.Track> playlist;
    private int                         currentIndex  = 0;
    private boolean                     isPlayerReady = false;

    // ── SeekBar update handler ────────────────────────────────────────────────
    private final Handler  seekHandler  = new Handler();
    private       Runnable seekRunnable;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding   = ActivityResultBinding.inflate(getLayoutInflater());
        viewModel = new ViewModelProvider(this).get(ResultViewModel.class);
        setContentView(binding.getRoot());

        binding.tvSongTitle.setSelected(true); // enable marquee scroll

        setupYouTubeRecyclerView();
        setupObservers();
        setupClickListeners();

        if (savedInstanceState == null) {
            processIntentExtras();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        releasePlayer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releasePlayer();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.fade_in, R.anim.slide_down);
    }

    // ── Intent extras ─────────────────────────────────────────────────────────

    private void processIntentExtras() {
        String label      = getIntent().getStringExtra(EXTRA_EMOTION_LABEL);
        float  confidence = getIntent().getFloatExtra(EXTRA_EMOTION_CONFIDENCE, 0f);
        if (label == null || label.isEmpty()) label = "neutral";

        EmotionResult emotion = new EmotionResult(label, confidence);

        if (com.moodtune.app.utils.NetworkUtils.isNetworkAvailable(this)) {
            viewModel.setEmotionAndFetchMusic(emotion);
        } else {
            binding.loadingContainer.setVisibility(View.GONE);
            binding.playerCard.setVisibility(View.GONE);
            binding.tvError.setVisibility(View.VISIBLE);
            binding.tvError.setText("No internet connection! Please connect to Wi-Fi or Mobile Data and try again.");
            updateMoodUI(emotion);
        }
    }

    // ── YouTube RecyclerView setup ────────────────────────────────────────────

    private void setupYouTubeRecyclerView() {
        binding.rvYoutubeSongs.setLayoutManager(new LinearLayoutManager(this));
        binding.rvYoutubeSongs.setNestedScrollingEnabled(false);
    }

    // ── LiveData observers ────────────────────────────────────────────────────

    private void setupObservers() {
        viewModel.getDetectedEmotion().observe(this, this::updateMoodUI);

        viewModel.getIsLoading().observe(this, loading -> {
            binding.loadingContainer.setVisibility(loading ? View.VISIBLE : View.GONE);
            binding.playerCard.setVisibility(loading ? View.GONE : View.VISIBLE);
        });

        // Playlist loaded — shuffle, show track info only (no auto-play)
        viewModel.getPlaylist().observe(this, tracks -> {
            if (tracks != null && !tracks.isEmpty()) {
                playlist     = tracks;
                currentIndex = 0;
                Collections.shuffle(playlist);
                binding.tvError.setVisibility(View.GONE);
                isPlayerReady = true;
                displayTrack(playlist.get(currentIndex));
                setPlayingState(false);
            }
        });

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                binding.tvError.setVisibility(View.VISIBLE);
                binding.tvError.setText(error);
                binding.playerCard.setVisibility(View.GONE);
            }
        });

        // YouTube loading spinner
        viewModel.getYoutubeLoading().observe(this, loading ->
                binding.pbYoutubeLoading.setVisibility(loading ? View.VISIBLE : View.GONE));

        // YouTube results
        viewModel.getYoutubeResults().observe(this, videos -> {
            binding.pbYoutubeLoading.setVisibility(View.GONE);
            if (videos != null && !videos.isEmpty()) {
                binding.rvYoutubeSongs.setVisibility(View.VISIBLE);
                binding.tvYoutubeError.setVisibility(View.GONE);
                binding.rvYoutubeSongs.setAdapter(new YouTubeSongAdapter(this, videos));
            } else {
                binding.rvYoutubeSongs.setVisibility(View.GONE);
                binding.tvYoutubeError.setVisibility(View.VISIBLE);
                binding.tvYoutubeError.setText("No YouTube songs found.");
            }
        });
    }

    // ── Playlist navigation ───────────────────────────────────────────────────

    private void playTrackAt(int index) {
        if (playlist == null || playlist.isEmpty()) return;
        currentIndex = index;
        JamendoResponse.Track track = playlist.get(currentIndex);
        displayTrack(track);
        streamTrack(track.getAudio());
    }

    private void playNext() {
        if (playlist == null || playlist.isEmpty()) return;
        currentIndex = (currentIndex + 1) % playlist.size();
        playTrackAt(currentIndex);
    }

    private void playPrevious() {
        if (playlist == null || playlist.isEmpty()) return;
        currentIndex = (currentIndex - 1 + playlist.size()) % playlist.size();
        playTrackAt(currentIndex);
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private void updateMoodUI(EmotionResult emotion) {
        binding.tvMoodLabel.setText(emotion.getDisplayLabel());
        binding.tvMoodEmoji.setText(emotion.toEmoji());
        binding.tvConfidence.setText("Confidence: " + emotion.getConfidencePercent() + "%");

        int color = MoodColorHelper.getColorForMood(this, emotion.getLabel());
        binding.tvMoodLabel.setShadowLayer(20f, 0f, 0f, color);
        binding.moodHeaderCard.setCardBackgroundColor(
                MoodColorHelper.getGlowColorForMood(this, emotion.getLabel()));
    }

    private void displayTrack(JamendoResponse.Track track) {
        binding.tvSongTitle.setText(track.getName());
        binding.tvArtistName.setText(track.getArtistName());

        Glide.with(this)
                .load(track.getImage())
                .transition(DrawableTransitionOptions.withCrossFade())
                .centerCrop()
                .placeholder(R.drawable.ic_music_placeholder)
                .error(R.drawable.ic_music_placeholder)
                .into(binding.ivAlbumArt);
    }

    private void setPlayingState(boolean playing) {
        if (playing) {
            binding.lottieEqualizer.setVisibility(View.VISIBLE);
            binding.lottieEqualizer.playAnimation();
            binding.ibPlayPause.setImageResource(R.drawable.ic_pause_circle);
        } else {
            binding.lottieEqualizer.pauseAnimation();
            binding.lottieEqualizer.setVisibility(View.INVISIBLE);
            binding.ibPlayPause.setImageResource(R.drawable.ic_play_circle);
        }
    }

    // ── MediaPlayer ───────────────────────────────────────────────────────────

    private void streamTrack(String url) {
        if (mediaPlayer == null) {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build());
        } else {
            mediaPlayer.reset();
        }

        if (url == null || url.isEmpty()) {
            Toast.makeText(this, "Audio URL unavailable, skipping…", Toast.LENGTH_SHORT).show();
            playNext();
            return;
        }

        try {
            mediaPlayer.setDataSource(url);
            mediaPlayer.prepareAsync();

            binding.ibPlayPause.setEnabled(false);
            setPlayingState(false);

            mediaPlayer.setOnPreparedListener(mp -> {
                binding.ibPlayPause.setEnabled(true);
                // Set seekbar max to track duration
                binding.seekBar.setMax(mp.getDuration());
                binding.tvTotalTime.setText(formatTime(mp.getDuration()));
                binding.tvElapsedTime.setText("0:00");
                mp.start();
                setPlayingState(true);
                startSeekBarUpdate();
            });

            // Auto-advance to next track on completion
            mediaPlayer.setOnCompletionListener(mp -> {
                stopSeekBarUpdate();
                binding.seekBar.setProgress(0);
                binding.tvElapsedTime.setText("0:00");
                playNext();
            });

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Toast.makeText(this, "Could not play track, skipping…", Toast.LENGTH_SHORT).show();
                binding.ibPlayPause.setEnabled(true);
                stopSeekBarUpdate();
                playNext();
                return true;
            });

        } catch (Exception e) {
            Toast.makeText(this, "Playback error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            playNext();
        }
    }

    private void releasePlayer() {
        stopSeekBarUpdate();
        if (mediaPlayer != null) {
            try { if (mediaPlayer.isPlaying()) mediaPlayer.stop(); } catch (Exception ignored) {}
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    // ── SeekBar update ────────────────────────────────────────────────────────

    private void startSeekBarUpdate() {
        stopSeekBarUpdate();
        seekRunnable = new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                    int current = mediaPlayer.getCurrentPosition();
                    binding.seekBar.setProgress(current);
                    binding.tvElapsedTime.setText(formatTime(current));
                    seekHandler.postDelayed(this, 500);
                }
            }
        };
        seekHandler.post(seekRunnable);
    }

    private void stopSeekBarUpdate() {
        if (seekRunnable != null) {
            seekHandler.removeCallbacks(seekRunnable);
            seekRunnable = null;
        }
    }

    private String formatTime(int millis) {
        int seconds = (millis / 1000) % 60;
        int minutes = (millis / 1000) / 60;
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    // ── Click listeners ───────────────────────────────────────────────────────

    private void setupClickListeners() {
        binding.btnScanAgain.setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            overridePendingTransition(R.anim.fade_in, R.anim.slide_down);
            finish();
        });

        // Play button — stream starts only on first tap
        binding.ibPlayPause.setOnClickListener(v -> {
            if (!isPlayerReady || playlist == null || playlist.isEmpty()) return;

            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                stopSeekBarUpdate();
                setPlayingState(false);
            } else if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
                mediaPlayer.start();
                setPlayingState(true);
                startSeekBarUpdate();
            } else {
                // First tap — begin streaming
                streamTrack(playlist.get(currentIndex).getAudio());
            }
        });

        // SeekBar drag to seek position
        binding.seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null) {
                    mediaPlayer.seekTo(progress);
                    binding.tvElapsedTime.setText(formatTime(progress));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        binding.ibPrevious.setOnClickListener(v -> playPrevious());
        binding.ibNext.setOnClickListener(v -> playNext());
    }
}