package com.moodtune.app.network;

import com.moodtune.app.data.model.JamendoResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * Retrofit interface for the Jamendo public tracks API v3.
 *
 * Base URL  : https://api.jamendo.com/v3.0/
 * Docs      : https://developer.jamendo.com/v3.0/tracks
 * Auth      : client_id query param (set in ResultViewModel — never hardcoded here)
 *
 * All tracks returned carry a direct MP3 stream URL in the "audio" field.
 */
public interface JamendoApiService {

    /**
     * Fetch tracks matching a mood tag.
     *
     * @param clientId   Jamendo client_id
     * @param format     Always "json"
     * @param limit      Max tracks to return
     * @param fuzzyTags  Mood tag (e.g. "happy", "sad", "energetic")
     * @param audioFormat  "mp32" for direct MP3 URLs
     */
    @GET("tracks/")
    Call<JamendoResponse> searchTracks(
            @Query("client_id")   String clientId,
            @Query("format")      String format,
            @Query("limit")       int    limit,
            @Query("fuzzytags")   String fuzzyTags,
            @Query("audioformat") String audioFormat,
            /** Restrict to vocal/sung tracks — avoids pure instrumentals */
            @Query("vocalinstrumental") String vocalInstrumental
    );
}
