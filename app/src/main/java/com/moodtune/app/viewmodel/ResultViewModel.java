package com.moodtune.app.viewmodel;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.moodtune.app.data.model.EmotionResult;
import com.moodtune.app.data.model.JamendoResponse;
import com.moodtune.app.data.model.YouTubeResponse;
import com.moodtune.app.network.JamendoRetrofitClient;
import com.moodtune.app.network.YouTubeRetrofitClient;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ResultViewModel extends ViewModel {

    private static final String JAMENDO_CLIENT_ID = "251df82c";
    // Updated with the new valid API key
    private static final String YOUTUBE_API_KEY   = "AIzaSyDbwuOUBBMkKycZeGhhA5oA-2Q7SucdwNc";

    private final MutableLiveData<EmotionResult>                detectedEmotion = new MutableLiveData<>();
    private final MutableLiveData<List<JamendoResponse.Track>>  playlist        = new MutableLiveData<>();
    private final MutableLiveData<Boolean>                      isLoading       = new MutableLiveData<>(false);
    private final MutableLiveData<String>                       errorMessage    = new MutableLiveData<>();

    // YouTube LiveData
    private final MutableLiveData<List<YouTubeResponse.VideoItem>> youtubeResults = new MutableLiveData<>();
    private final MutableLiveData<Boolean>                         youtubeLoading = new MutableLiveData<>(false);

    public LiveData<EmotionResult>                   getDetectedEmotion() { return detectedEmotion; }
    public LiveData<List<JamendoResponse.Track>>     getPlaylist()         { return playlist;        }
    public LiveData<Boolean>                         getIsLoading()        { return isLoading;       }
    public LiveData<String>                          getErrorMessage()     { return errorMessage;    }
    public LiveData<List<YouTubeResponse.VideoItem>> getYoutubeResults()   { return youtubeResults;  }
    public LiveData<Boolean>                         getYoutubeLoading()   { return youtubeLoading;  }

    public void setEmotionAndFetchMusic(EmotionResult emotion) {
        detectedEmotion.setValue(emotion);
        fetchTrack(emotionToTag(emotion.getLabel()));
        fetchYouTubeVideos(emotion.getLabel());
    }

    public void retry() {
        EmotionResult emotion = detectedEmotion.getValue();
        if (emotion != null) {
            fetchTrack(emotionToTag(emotion.getLabel()));
            fetchYouTubeVideos(emotion.getLabel());
        }
    }

    /** Maps emotion label to Jamendo tag */
    private String emotionToTag(String label) {
        if (label == null) return "pop";
        switch (label.toLowerCase()) {
            case "happy":     return "happy";
            case "sad":       return "sad";
            case "angry":     return "energetic";
            case "fearful":   return "ambient";
            case "disgusted": return "dark";
            case "surprised": return "upbeat";
            default:          return "relaxing";
        }
    }

    /** Maps emotion label to YouTube search query */
    private String emotionToYouTubeQuery(String label) {
        if (label == null) return "relaxing chill songs 2025";
        switch (label.toLowerCase()) {
            case "happy":     return "happy songs 2025 hits";
            case "sad":       return "sad songs 2025 hindi";
            case "angry":     return "energetic workout songs 2025";
            case "fearful":   return "calming ambient music 2025";
            case "disgusted": return "dark alternative music 2025";
            case "surprised": return "upbeat pop songs 2025";
            default:          return "relaxing chill songs 2025";
        }
    }

    /** YouTube API call — fallback to Firebase Remote Config on failure */
    public void fetchYouTubeVideos(String emotionLabel) {
        youtubeLoading.setValue(true);

        String query = emotionToYouTubeQuery(emotionLabel);

        YouTubeRetrofitClient.getInstance().searchVideos(
                "snippet", query, "video", 10, YOUTUBE_API_KEY
        ).enqueue(new Callback<YouTubeResponse>() {
            @Override
            public void onResponse(@NonNull Call<YouTubeResponse> call,
                                   @NonNull Response<YouTubeResponse> response) {
                youtubeLoading.postValue(false);

                if (response.isSuccessful()
                        && response.body() != null
                        && response.body().getItems() != null
                        && !response.body().getItems().isEmpty()) {

                    youtubeResults.postValue(response.body().getItems());
                } else {
                    // --- LOGIC: Print the exact API Error to Logcat ---
                    if (!response.isSuccessful()) {
                        try {
                            String errorBody = response.errorBody() != null ? response.errorBody().string() : "Empty error body";
                            android.util.Log.e("YouTubeAPI", "Error Code: " + response.code() + " | Details: " + errorBody);
                        } catch (Exception e) {
                            android.util.Log.e("YouTubeAPI", "Could not read error body", e);
                        }
                    } else {
                        android.util.Log.e("YouTubeAPI", "Response successful but body/items are null or empty.");
                    }
                    // ------------------------------------------------------

                    loadYoutubeFallback(emotionLabel);
                }
            }

            @Override
            public void onFailure(@NonNull Call<YouTubeResponse> call, @NonNull Throwable t) {
                youtubeLoading.postValue(false);
                android.util.Log.e("YouTubeAPI", "Network Failure: " + t.getMessage());
                loadYoutubeFallback(emotionLabel);
            }
        });
    }

    /** Firebase Remote Config fallback */
    private void loadYoutubeFallback(String emotionLabel) {
        FirebaseRemoteConfig remoteConfig = FirebaseRemoteConfig.getInstance();

        FirebaseRemoteConfigSettings settings = new FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(3600)
                .build();
        remoteConfig.setConfigSettingsAsync(settings);

        remoteConfig.fetchAndActivate().addOnCompleteListener(task -> {
            String json = remoteConfig.getString("mood_songs");

            if (json != null && !json.isEmpty()) {
                try {
                    Type mapType = new TypeToken<Map<String, List<String>>>() {}.getType();
                    Map<String, List<String>> fallbackMap = new Gson().fromJson(json, mapType);

                    String key = emotionLabel != null ? emotionLabel.toLowerCase() : "neutral";
                    List<String> titles = fallbackMap.containsKey(key)
                            ? fallbackMap.get(key)
                            : fallbackMap.get("neutral");

                    List<YouTubeResponse.VideoItem> items = new ArrayList<>();
                    if (titles != null) {
                        for (String title : titles) {
                            items.add(YouTubeResponse.VideoItem.fromTitle(title));
                        }
                    }
                    youtubeResults.postValue(items);
                    return;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            // Remote Config fail on empty list
            youtubeResults.postValue(new ArrayList<>());
        });
    }

    private void fetchTrack(String tag) {
        isLoading.setValue(true);
        errorMessage.setValue(null);

        JamendoRetrofitClient.getInstance().searchTracks(
                JAMENDO_CLIENT_ID, "json", 50, tag, "mp32", "vocal"
        ).enqueue(new Callback<JamendoResponse>() {
            @Override
            public void onResponse(@NonNull Call<JamendoResponse> call,
                                   @NonNull Response<JamendoResponse> response) {
                isLoading.postValue(false);
                if (response.isSuccessful() && response.body() != null) {
                    List<JamendoResponse.Track> results = response.body().getResults();
                    if (results != null) {
                        List<JamendoResponse.Track> valid = new ArrayList<>();
                        for (JamendoResponse.Track t : results) {
                            if (t.getAudio() != null && !t.getAudio().isEmpty()) valid.add(t);
                        }
                        if (!valid.isEmpty()) {
                            playlist.postValue(valid);
                            return;
                        }
                    }
                }
                errorMessage.postValue("No music found for this mood. Try scanning again.");
            }

            @Override
            public void onFailure(@NonNull Call<JamendoResponse> call, @NonNull Throwable t) {
                isLoading.postValue(false);
                errorMessage.postValue("Network error. Please check your connection.");
            }
        });
    }
}