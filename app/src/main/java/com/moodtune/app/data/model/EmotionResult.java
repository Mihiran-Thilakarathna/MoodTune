package com.moodtune.app.data.model;

/**
 * Represents a detected emotion with a label and confidence score.
 */
public class EmotionResult {

    /** Raw emotion label returned by the TFLite model (e.g. "happy", "sad") */
    private final String label;

    /** Confidence score in range [0, 1] */
    private final float confidence;

    public EmotionResult(String label, float confidence) {
        this.label = label;
        this.confidence = confidence;
    }

    public String getLabel() {
        return label;
    }

    /** Returns confidence as 0..100 percentage */
    public int getConfidencePercent() {
        return Math.round(confidence * 100f);
    }

    public float getConfidence() {
        return confidence;
    }

    /**
     * Returns a display-friendly capitalised label, e.g. "Happy".
     */
    public String getDisplayLabel() {
        if (label == null || label.isEmpty()) return "Unknown";
        return label.substring(0, 1).toUpperCase() + label.substring(1).toLowerCase();
    }

    /**
     * Maps emotion to an emoji string for UI display.
     */
    public String toEmoji() {
        switch (label.toLowerCase()) {
            case "happy":     return "😄";
            case "sad":       return "😢";
            case "angry":     return "😠";
            case "surprised": return "😲";
            case "fearful":   return "😨";
            case "disgusted": return "🤢";
            case "neutral":
            default:          return "😐";
        }
    }

    @Override
    public String toString() {
        return "EmotionResult{label='" + label + "', confidence=" + confidence + "}";
    }
}
