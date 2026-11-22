package com.musictagger.app.util;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.util.Log;

/** Keeps the last failure with its step label so problems can be reported and copied. */
public final class Diag {

    private static final String TAG = "MusicTagger";
    private static String lastError = "";

    private Diag() {
    }

    public static void log(String message) {
        Log.d(TAG, message);
    }

    public static void error(String step, Throwable t) {
        String detail = describe(step, t);
        lastError = detail;
        Log.e(TAG, detail, t);
    }

    public static String last() {
        return lastError;
    }

    public static String describe(String step, Throwable t) {
        StringBuilder sb = new StringBuilder(step).append("失败：");
        sb.append(t.getClass().getName());
        if (t.getMessage() != null && !t.getMessage().isEmpty()) {
            sb.append(": ").append(t.getMessage());
        }
        StackTraceElement[] frames = t.getStackTrace();
        for (int i = 0; i < Math.min(5, frames.length); i++) {
            sb.append("\n    at ").append(frames[i]);
        }
        Throwable cause = t.getCause();
        if (cause != null && cause != t) {
            sb.append("\n  原因：").append(cause.getClass().getName());
            if (cause.getMessage() != null) {
                sb.append(": ").append(cause.getMessage());
            }
        }
        return sb.toString();
    }

    public static void copy(Context ctx, String text) {
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("MusicTagger", text));
        }
    }
}
