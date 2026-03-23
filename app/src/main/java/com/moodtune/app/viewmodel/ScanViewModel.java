package com.moodtune.app.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.moodtune.app.data.model.EmotionResult;

/**
 * ViewModel for MainActivity (the face scanner screen).
 *
 * Holds the current scan state and the detected emotion so that it
 * survives configuration changes (screen rotation, etc.)
 */
public class ScanViewModel extends ViewModel {

    // ── Scan state enum ────────────────────────────────────────────────────────

    public enum ScanState {
        IDLE,          // Waiting for user to tap "Scan"
        SCANNING,      // Camera open, actively scanning
        DETECTED,      // Emotion successfully detected
        NO_FACE,       // Face not found in frame
        ERROR          // Generic processing error
    }

    // ── LiveData fields ────────────────────────────────────────────────────────

    private final MutableLiveData<ScanState>    scanState    = new MutableLiveData<>(ScanState.IDLE);
    private final MutableLiveData<EmotionResult> emotionResult = new MutableLiveData<>();
    private final MutableLiveData<String>        errorMessage  = new MutableLiveData<>();

    // ── Getters ────────────────────────────────────────────────────────────────

    public LiveData<ScanState>    getScanState()    { return scanState; }
    public LiveData<EmotionResult> getEmotionResult() { return emotionResult; }
    public LiveData<String>        getErrorMessage()  { return errorMessage; }

    // ── State setters (called from Activity) ──────────────────────────────────

    public void startScanning() {
        scanState.setValue(ScanState.SCANNING);
    }

    public void onEmotionDetected(EmotionResult result) {
        emotionResult.setValue(result);
        scanState.setValue(ScanState.DETECTED);
    }

    public void onNoFaceDetected() {
        scanState.setValue(ScanState.NO_FACE);
    }

    public void onScanError(String message) {
        errorMessage.setValue(message);
        scanState.setValue(ScanState.ERROR);
    }

    public void resetToIdle() {
        scanState.setValue(ScanState.IDLE);
    }
}
