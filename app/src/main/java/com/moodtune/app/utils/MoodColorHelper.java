package com.moodtune.app.utils;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;

import androidx.annotation.ColorInt;

import com.moodtune.app.R;

/**
 * Utility class providing mood-to-color and mood-to-resource mappings used across the UI.
 */
public class MoodColorHelper {

    private MoodColorHelper() {} // Utility class – no instantiation

    /**
     * Returns the primary accent color for a given emotion label.
     *
     * @param context Android context
     * @param label   Emotion label (case-insensitive), e.g. "happy", "sad"
     * @return        ARGB color int
     */
    @ColorInt
    public static int getColorForMood(Context context, String label) {
        if (label == null) return context.getColor(R.color.mood_neutral);
        switch (label.toLowerCase()) {
            case "happy":     return context.getColor(R.color.mood_happy);
            case "sad":       return context.getColor(R.color.mood_sad);
            case "angry":     return context.getColor(R.color.mood_angry);
            case "surprised": return context.getColor(R.color.mood_surprised);
            case "fearful":   return context.getColor(R.color.mood_fearful);
            case "disgusted": return context.getColor(R.color.mood_disgusted);
            case "neutral":
            default:          return context.getColor(R.color.mood_neutral);
        }
    }

    /**
     * Returns a semi-transparent (40% alpha) version of the mood colour,
     * useful for backgrounds / glass tints.
     */
    @ColorInt
    public static int getGlowColorForMood(Context context, String label) {
        int base = getColorForMood(context, label);
        // Overlay 40% alpha
        return Color.argb(
                102, // ~40% of 255
                Color.red(base),
                Color.green(base),
                Color.blue(base)
        );
    }

    /**
     * Returns a ColorStateList wrapping the mood colour (for tinting icons, progress bars, etc.)
     */
    public static ColorStateList getMoodColorStateList(Context context, String label) {
        return ColorStateList.valueOf(getColorForMood(context, label));
    }
}
