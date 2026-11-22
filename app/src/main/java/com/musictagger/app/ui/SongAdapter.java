package com.musictagger.app.ui;

import android.content.Context;
import android.graphics.Color;
import android.text.format.Formatter;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
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
        void onClick(DocumentFile file, int position);

        void onLongClick(DocumentFile file, int position);
    }

    private final List<DocumentFile> data;
    private final java.util.Map<String, Long> sizes;
    private final java.util.Map<String, String> names;
    private final Listener listener;
    private final Set<String> selected = new HashSet<>();
    private boolean selectionMode;
    private boolean rangeMode;
    private int anchor = -1;

    public SongAdapter(List<DocumentFile> data, java.util.Map<String, Long> sizes,
                       java.util.Map<String, String> names, Listener listener) {
        this.data = data;
        this.sizes = sizes;
        this.names = names;
        this.listener = listener;
    }

    public boolean isSelectionMode() {
        return selectionMode;
    }

    public void setSelectionMode(boolean on) {
        selectionMode = on;
        rangeMode = false;
        anchor = -1;
        if (!on) {
            selected.clear();
        }
        notifyDataSetChanged();
    }

    public boolean isRangeMode() {
        return rangeMode;
    }

    /** Entering range mode anchors on the first already-selected row, if any. */
    public void setRangeMode(boolean on) {
        rangeMode = on;
        anchor = on ? firstSelectedPosition() : -1;
        notifyDataSetChanged();
    }

    public int getAnchor() {
        return anchor;
    }

    public void setAnchor(int position) {
        anchor = position;
        notifyDataSetChanged();
    }

    public void selectRange(int from, int to) {
        int low = Math.min(from, to);
        int high = Math.max(from, to);
        for (int i = low; i <= high && i < data.size(); i++) {
            selected.add(data.get(i).getUri().toString());
        }
        anchor = -1;
        notifyDataSetChanged();
    }

    private int firstSelectedPosition() {
        for (int i = 0; i < data.size(); i++) {
            if (selected.contains(data.get(i).getUri().toString())) {
                return i;
            }
        }
        return -1;
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
        String key = f.getUri().toString();
        // Name and size come from the scan: DocumentFile.getName()/length() would hit the
        // provider on the UI thread for every bind.
        String name = names == null ? null : names.get(key);
        if (name == null) {
            name = f.getName() == null ? "" : f.getName();
        }
        holder.binding.title.setText(name);
        Long length = sizes == null ? null : sizes.get(key);
        String sizeText = length != null && length > 0
                ? Formatter.formatShortFileSize(holder.itemView.getContext(), length) : "";
        String ext = SafStore.extensionOf(name).toUpperCase();
        if (sizeText.isEmpty()) {
            holder.binding.subtitle.setText(ext);
        } else {
            holder.binding.subtitle.setText(ext.isEmpty() ? sizeText : ext + "  ·  " + sizeText);
        }

        boolean checked = selected.contains(key);
        boolean isAnchor = rangeMode && position == anchor;
        Context ctx = holder.itemView.getContext();

        holder.binding.check.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
        holder.binding.check.setChecked(selectionMode && checked);
        holder.binding.chevron.setVisibility(selectionMode ? View.GONE : View.VISIBLE);

        MaterialCardView card = (MaterialCardView) holder.itemView;
        boolean highlight = isAnchor || (selectionMode && checked);
        card.setStrokeWidth((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                highlight ? 2 : 1, ctx.getResources().getDisplayMetrics()));
        card.setStrokeColor(highlight
                ? ContextCompat.getColor(ctx, R.color.brand_primary)
                : ContextCompat.getColor(ctx, R.color.outline));
        if (isAnchor) {
            card.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.brand_container));
        } else if (selectionMode && checked) {
            card.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.surface_variant));
        } else {
            card.setCardBackgroundColor(Color.TRANSPARENT);
        }

        holder.itemView.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                listener.onClick(f, pos);
            }
        });
        holder.itemView.setOnLongClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                listener.onLongClick(f, pos);
            }
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
