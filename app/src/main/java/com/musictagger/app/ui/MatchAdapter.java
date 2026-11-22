package com.musictagger.app.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.musictagger.app.R;
import com.musictagger.app.databinding.ItemMatchBinding;
import com.musictagger.app.model.MatchItem;
import com.musictagger.app.model.Track;
import com.musictagger.app.util.CoverLoader;

import java.util.List;
import java.util.Locale;

public class MatchAdapter extends RecyclerView.Adapter<MatchAdapter.Holder> {

    public interface Listener {
        void onItemClick(MatchItem item);
    }

    private final List<MatchItem> items;
    private final Listener listener;

    public MatchAdapter(List<MatchItem> items, Listener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemMatchBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        MatchItem item = items.get(position);
        Track track = item.chosenTrack();
        Context ctx = holder.itemView.getContext();

        if (track != null) {
            holder.binding.title.setText(track.title);
            StringBuilder sub = new StringBuilder(track.artist == null ? "" : track.artist);
            if (track.album != null && !track.album.isEmpty()) {
                sub.append("  ·  ").append(track.album);
            }
            sub.append("  ·  ").append(track.source)
                    .append(' ').append(Math.round(track.score * 100)).append('%');
            holder.binding.subtitle.setText(sub.toString());
            CoverLoader.load(track, holder.binding.cover);
        } else {
            holder.binding.title.setText(item.fileName);
            holder.binding.subtitle.setText(item.note == null || item.note.isEmpty()
                    ? "没有找到候选，点一下手动搜索" : item.note);
            holder.binding.cover.setImageResource(R.drawable.ic_album);
        }
        holder.binding.fileName.setText(item.fileName);

        holder.binding.status.setText(item.statusLabel());
        int bg;
        int fg;
        switch (item.status) {
            case MatchItem.READY:
            case MatchItem.MISSING_ART:
                bg = ContextCompat.getColor(ctx, R.color.brand_container);
                fg = ContextCompat.getColor(ctx, R.color.brand_on_container);
                break;
            case MatchItem.ERROR:
            case MatchItem.NO_MATCH:
                bg = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorErrorContainer,
                        0x33FF0000);
                fg = MaterialColors.getColor(holder.itemView, com.google.android.material.R.attr.colorOnErrorContainer,
                        0xFF000000);
                break;
            default:
                bg = ContextCompat.getColor(ctx, R.color.surface_variant);
                fg = ContextCompat.getColor(ctx, R.color.text_secondary);
                break;
        }
        holder.binding.status.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(bg));
        holder.binding.status.setTextColor(fg);

        holder.itemView.setOnClickListener(v -> listener.onItemClick(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemMatchBinding binding;

        Holder(ItemMatchBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    static String percent(Track t) {
        return String.format(Locale.ROOT, "%d%%", Math.round(t.score * 100));
    }
}
