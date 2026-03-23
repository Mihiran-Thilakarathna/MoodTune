package com.moodtune.app.data.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * YouTube Data API v3 search response model.
 * GET https://www.googleapis.com/youtube/v3/search
 */
public class YouTubeResponse {

    @SerializedName("items")
    private List<VideoItem> items;

    public List<VideoItem> getItems() {
        return items;
    }

    // ── Video Item ────────────────────────────────────────────────────────────

    public static class VideoItem {

        @SerializedName("id")
        private VideoId id;

        @SerializedName("snippet")
        private Snippet snippet;

        public String getVideoId() {
            return (id != null) ? id.videoId : null;
        }

        public String getTitle() {
            return (snippet != null) ? snippet.title : null;
        }

        public String getThumbnailUrl() {
            if (snippet == null || snippet.thumbnails == null) return null;
            if (snippet.thumbnails.medium != null) return snippet.thumbnails.medium.url;
            if (snippet.thumbnails.defaultThumb != null) return snippet.thumbnails.defaultThumb.url;
            return null;
        }

        // Firebase Remote Config fallback සඳහා
        public static VideoItem fromTitle(String title) {
            VideoItem item = new VideoItem();
            item.snippet = new Snippet();
            item.snippet.title = title;
            return item;
        }
    }

    // ── Nested classes ────────────────────────────────────────────────────────

    public static class VideoId {
        @SerializedName("videoId")
        public String videoId;
    }

    public static class Snippet {
        @SerializedName("title")
        public String title;

        @SerializedName("thumbnails")
        public Thumbnails thumbnails;
    }

    public static class Thumbnails {
        @SerializedName("default")
        public Thumbnail defaultThumb;

        @SerializedName("medium")
        public Thumbnail medium;
    }

    public static class Thumbnail {
        @SerializedName("url")
        public String url;
    }
}