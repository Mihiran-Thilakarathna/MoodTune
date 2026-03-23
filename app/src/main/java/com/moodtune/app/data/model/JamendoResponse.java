package com.moodtune.app.data.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Root response from GET https://api.jamendo.com/v3.0/tracks/
 *
 * Relevant JSON structure:
 * {
 *   "results": [
 *     {
 *       "name":        "Song Title",
 *       "artist_name": "Artist Name",
 *       "image":       "https://...album-art.jpg",
 *       "audio":       "https://...track.mp3"
 *     }
 *   ]
 * }
 */
public class JamendoResponse {

    @SerializedName("results")
    private List<Track> results;

    public List<Track> getResults() {
        return results;
    }

    // ── Inner model ───────────────────────────────────────────────────────────

    public static class Track {

        @SerializedName("name")
        private String name;

        @SerializedName("artist_name")
        private String artistName;

        /** Album art thumbnail URL */
        @SerializedName("image")
        private String image;

        /** Direct MP3 stream URL for the full track */
        @SerializedName("audio")
        private String audio;

        public String getName()       { return name;       }
        public String getArtistName() { return artistName; }
        public String getImage()      { return image;      }
        public String getAudio()      { return audio;      }
    }
}
