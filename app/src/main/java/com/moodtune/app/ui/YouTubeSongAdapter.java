package com.moodtune.app.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.moodtune.app.R;
import com.moodtune.app.data.model.YouTubeResponse;

import java.util.List;

/**
 * RecyclerView Adapter for YouTube song list.
 * Click → opens YouTube app (or browser) with the video or search query.
 */
public class YouTubeSongAdapter extends
        RecyclerView.Adapter<YouTubeSongAdapter.ViewHolder> {

    private final Context context;
    private final List<YouTubeResponse.VideoItem> items;

    public YouTubeSongAdapter(Context context, List<YouTubeResponse.VideoItem> items) {
        this.context = context;
        this.items   = items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context)
                .inflate(R.layout.item_youtube_song, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        YouTubeResponse.VideoItem item = items.get(position);

        // Set song title
        holder.tvTitle.setText(item.getTitle() != null ? item.getTitle() : "Unknown");

        // Load thumbnail — show placeholder if URL is null (Firebase fallback items)
        String thumbUrl = item.getThumbnailUrl();
        if (thumbUrl != null && !thumbUrl.isEmpty()) {
            Glide.with(context)
                    .load(thumbUrl)
                    .centerCrop()
                    .placeholder(R.drawable.ic_music_placeholder)
                    .error(R.drawable.ic_music_placeholder)
                    .into(holder.ivThumbnail);
        } else {
            holder.ivThumbnail.setImageResource(R.drawable.ic_music_placeholder);
        }

        // Click → open YouTube app or browser
        holder.itemView.setOnClickListener(v -> {
            Uri uri;
            if (item.getVideoId() != null) {
                // Direct video link
                uri = Uri.parse("https://www.youtube.com/watch?v=" + item.getVideoId());
            } else {
                // Fallback: search by title
                uri = Uri.parse("https://www.youtube.com/results?search_query="
                        + Uri.encode(item.getTitle()));
            }
            context.startActivity(new Intent(Intent.ACTION_VIEW, uri));
        });
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    // ── ViewHolder ────────────────────────────────────────────────────────────

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivThumbnail;
        TextView  tvTitle;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumbnail = itemView.findViewById(R.id.ivYoutubeThumbnail);
            tvTitle     = itemView.findViewById(R.id.tvYoutubeTitle);
        }
    }
}