package com.musictagger.app.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.musictagger.app.databinding.ItemCandidateCoverBinding;
import com.musictagger.app.model.Track;
import com.musictagger.app.util.CoverLoader;

import java.util.List;
import java.util.Locale;

/** Candidate list with cover thumbnails, used by the editor and the batch review. */
public final class CandidatePickerDialog {

    public interface OnPick {
        void onPick(int index, Track track);
    }

    private CandidatePickerDialog() {
    }

    public static void show(Context context, String title, List<Track> tracks, int selected, OnPick onPick) {
        if (tracks == null || tracks.isEmpty()) {
            Toast.makeText(context, "没有可选的候选", Toast.LENGTH_SHORT).show();
            return;
        }
        ViewGroup root = (ViewGroup) LayoutInflater.from(context)
                .inflate(com.musictagger.app.R.layout.dialog_candidates, null, false);
        RecyclerView list = root.findViewById(com.musictagger.app.R.id.candidates);
        list.setLayoutManager(new LinearLayoutManager(context));

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setView(root)
                .setNegativeButton("取消", null)
                .create();

        list.setAdapter(new Adapter(tracks, selected, (index, track) -> {
            dialog.dismiss();
            onPick.onPick(index, track);
        }));
        dialog.show();
    }

    private static class Adapter extends RecyclerView.Adapter<Adapter.Holder> {

        private final List<Track> items;
        private final int selected;
        private final OnPick listener;

        Adapter(List<Track> items, int selected, OnPick listener) {
            this.items = items;
            this.selected = selected;
            this.listener = listener;
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(ItemCandidateCoverBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Track t = items.get(position);
            holder.binding.title.setText(t.title);
            StringBuilder sub = new StringBuilder(t.artist == null ? "" : t.artist);
            if (t.album != null && !t.album.isEmpty()) {
                sub.append("  ·  ").append(t.album);
            }
            holder.binding.subtitle.setText(sub.toString());
            holder.binding.source.setText(t.source + " " + Math.round(t.score * 100) + "%");
            CoverLoader.load(t, holder.binding.cover);
            holder.itemView.setBackgroundColor(position == selected
                    ? 0x1A5B4BC4 : Color.TRANSPARENT);
            holder.itemView.setOnClickListener(v -> listener.onPick(position, t));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class Holder extends RecyclerView.ViewHolder {
            final ItemCandidateCoverBinding binding;

            Holder(ItemCandidateCoverBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }

    static String score(Track t) {
        return String.format(Locale.ROOT, "%d%%", Math.round(t.score * 100));
    }
}
