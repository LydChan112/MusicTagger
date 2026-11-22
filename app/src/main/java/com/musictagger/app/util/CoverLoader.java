package com.musictagger.app.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;

import com.musictagger.app.R;
import com.musictagger.app.model.Track;
import com.musictagger.app.provider.ProviderHub;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Small async cover loader for list rows. */
public final class CoverLoader {

    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(60);
    private static volatile ExecutorService POOL = Executors.newFixedThreadPool(2);

    /** Rebuilds the thumbnail pool; called at startup and when the setting changes. */
    public static synchronized void setThreads(int threads) {
        int size = Math.max(1, Math.min(8, threads));
        if (POOL instanceof java.util.concurrent.ThreadPoolExecutor) {
            int current = ((java.util.concurrent.ThreadPoolExecutor) POOL).getCorePoolSize();
            if (current == size) {
                return;
            }
        }
        ExecutorService old = POOL;
        POOL = Executors.newFixedThreadPool(size);
        old.shutdown();
    }

    private CoverLoader() {
    }

    public static void load(final Track track, final ImageView view) {
        if (track == null) {
            view.setImageResource(R.drawable.ic_album);
            return;
        }
        final String key = track.source + ":" + track.id;
        Bitmap cached = CACHE.get(key);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        view.setImageResource(R.drawable.ic_album);
        view.setTag(key);
        POOL.execute(() -> {
            // Lists only need a thumbnail; the full-resolution cover is fetched on write.
            byte[] data = ProviderHub.thumbnailWithDeadline(track, 4000);
            if (data == null || data.length == 0) {
                return;
            }
            Bitmap bitmap = decode(data, 160);
            if (bitmap == null) {
                return;
            }
            CACHE.put(key, bitmap);
            view.post(() -> {
                if (key.equals(view.getTag())) {
                    view.setImageBitmap(bitmap);
                }
            });
        });
    }

    private static Bitmap decode(byte[] data, int targetPx) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        int sample = 1;
        if (bounds.outWidth > 0) {
            while (bounds.outWidth / sample > targetPx * 2) {
                sample *= 2;
            }
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        try {
            return BitmapFactory.decodeByteArray(data, 0, data.length, options);
        } catch (Exception e) {
            return null;
        }
    }

    public static void clear() {
        CACHE.evictAll();
    }
}
