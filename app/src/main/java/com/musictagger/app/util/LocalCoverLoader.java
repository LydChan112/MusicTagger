package com.musictagger.app.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.widget.ImageView;

import com.musictagger.app.R;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loads the artwork embedded in a local file for list rows, with a placeholder fallback. */
public final class LocalCoverLoader {

    private static final android.util.LruCache<String, Bitmap> CACHE = new android.util.LruCache<>(80);
    private static final ExecutorService POOL = Executors.newFixedThreadPool(2);
    /** Marker meaning "this file has no embedded picture", so we do not retry. */
    private static final Bitmap NONE = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);

    private LocalCoverLoader() {
    }

    public static void load(Context context, Uri uri, ImageView view) {
        if (uri == null) {
            view.setImageResource(R.drawable.ic_album);
            return;
        }
        final String key = uri.toString();
        Bitmap cached = CACHE.get(key);
        if (cached == NONE) {
            view.setImageResource(R.drawable.ic_album);
            return;
        }
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        view.setImageResource(R.drawable.ic_album);
        view.setTag(key);
        final Context app = context.getApplicationContext();
        POOL.execute(() -> {
            Bitmap bitmap = read(app, uri);
            if (bitmap == null) {
                CACHE.put(key, NONE);
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

    private static Bitmap read(Context context, Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            byte[] data = retriever.getEmbeddedPicture();
            if (data == null || data.length == 0) {
                return null;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 2;
            return BitmapFactory.decodeByteArray(data, 0, data.length, options);
        } catch (Throwable e) {
            return null;
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
    }

    public static void clear() {
        CACHE.evictAll();
    }
}
