package com.moodtune.app.network;

import com.moodtune.app.data.model.YouTubeResponse;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * Retrofit interface for YouTube Data API v3.
 * Base URL: https://www.googleapis.com/youtube/v3/
 */
public interface YouTubeApiService {

    @GET("search")
    Call<YouTubeResponse> searchVideos(
            @Query("part")       String part,
            @Query("q")          String query,
            @Query("type")       String type,
            @Query("maxResults") int    maxResults,
            @Query("key")        String apiKey
    );
}