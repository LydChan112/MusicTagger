package com.musictagger.app.util;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayOutputStream;

/**
 * Keeps embedded artwork under a hard byte budget.
 * Re-encodes as JPEG and only downscales when lowering quality alone is not enough.
 */
public final class CoverCompressor {

    /** Hard limit for artwork written into a file. */
    public static final int MAX_BYTES = 1024 * 1024;

    private static final int[] QUALITY_STEPS = {90, 80, 70, 60, 50};

    private CoverCompressor() {
    }

    public static byte[] limit(byte[] data) {
        if (data == null || data.length == 0 || data.length <= MAX_BYTES) {
            return data;
        }
        Bitmap bitmap;
        try {
            bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
        } catch (Throwable e) {
            return data;
        }
        if (bitmap == null) {
            return data;
        }

        Bitmap current = bitmap;
        try {
            for (int round = 0; round < 6; round++) {
                for (int quality : QUALITY_STEPS) {
                    byte[] out = compress(current, quality);
                    if (out != null && out.length > 0 && out.length <= MAX_BYTES) {
                        return out;
                    }
                }
                int width = (int) (current.getWidth() * 0.8f);
                int height = (int) (current.getHeight() * 0.8f);
                if (width < 320 || height < 320) {
                    break;
                }
                Bitmap scaled = Bitmap.createScaledBitmap(current, width, height, true);
                if (scaled != current) {
                    if (current != bitmap) {
                        current.recycle();
                    }
                    current = scaled;
                }
            }
            byte[] fallback = compress(current, 40);
            return fallback != null && fallback.length > 0 ? fallback : data;
        } catch (Throwable e) {
            return data;
        } finally {
            if (current != bitmap) {
                current.recycle();
            }
            bitmap.recycle();
        }
    }

    private static byte[] compress(Bitmap bitmap, int quality) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out);
        return out.toByteArray();
    }
}
