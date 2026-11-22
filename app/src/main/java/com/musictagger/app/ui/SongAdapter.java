package com.musictagger.app.ui;

import android.content.Context;
import android.graphics.Color;
import android.text.format.Formatter;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.musictagger.app.R;
import com.musictagger.app.databinding.ItemSongBinding;
import com.musictagger.app.storage.SafStore;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SongAdapter extends RecyclerView.Adapter<SongAdapter.Holder> {

    public interface Listener {
        void onClick(DocumentFile file);

        void onLongClick(DocumentFile file);
    }

    private final List<DocumentFile> data;
    private final Listener listener;
    private final Set<String> selected = new HashSet<>();
    private boolean selectionMode;

    public SongAdapter(List<DocumentFile> data, Listener listener) {
        this.data = data;
        this.listener = listener;
    }

    public boolean isSelectionMode() {
        return selectionMode;
    }

    public void setSelectionMode(boolean on) {
        selectionMode = on;
        if (!on) {
            selected.clear();
        }
        notifyDataSetChanged();
    }

    public void toggle(DocumentFile f) {
        String key = f.getUri().toString();
        if (!selected.remove(key)) {
            selected.add(key);
        }
        notifyDataSetChanged();
    }

    public int selectedCount() {
        return selected.size();
    }

    public void selectAll(boolean all) {
        selected.clear();
        if (all) {
            for (DocumentFile f : data) {
                selected.add(f.getUri().toString());
            }
        }
        notifyDataSetChanged();
    }

    public List<DocumentFile> selectedFiles() {
        List<DocumentFile> out = new ArrayList<>();
        for (DocumentFile f : data) {
            if (selected.contains(f.getUri().toString())) {
                out.add(f);
            }
        }
        return out;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSongBinding b = ItemSongBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new Holder(b);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        DocumentFile f = data.get(position);
        String name = f.getName() == null ? "" : f.getName();
        holder.binding.title.setText(name);
        String size = Formatter.formatShortFileSize(holder.itemView.getContext(), f.length());
        String ext = SafStore.extensionOf(name).toUpperCase();
        holder.binding.subtitle.setText(ext.isEmpty() ? size : ext + "  ·  " + size);

        boolean checked = selected.contains(f.getUri().toString());
        Context ctx = holder.itemView.getContext();
        holder.binding.check.setVisibility(selectionMode ? android.view.View.VISIBLE : android.view.View.GONE);
        holder.binding.check.setChecked(selectionMode && checked);
        holder.binding.chevron.setVisibility(selectionMode ? android.view.View.GONE : android.view.View.VISIBLE);

        MaterialCardView card = (MaterialCardView) holder.itemView;
        card.setStrokeWidth((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                selectionMode && checked ? 2 : 1, ctx.getResources().getDisplayMetrics()));
        card.setStrokeColor(selectionMode && checked
                ? ContextCompat.getColor(ctx, R.color.brand_primary)
                : ContextCompat.getColor(ctx, R.color.outline));
        card.setCardBackgroundColor(selectionMode && checked
                ? ContextCompat.getColor(ctx, R.color.surface_variant)
                : Color.TRANSPARENT);

        holder.itemView.setOnClickListener(v -> listener.onClick(f));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onLongClick(f);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ItemSongBinding binding;

        Holder(ItemSongBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
