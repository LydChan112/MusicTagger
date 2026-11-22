package com.musictagger.app.util;

import android.content.Context;
import android.content.SharedPreferences;

/** User-tunable behaviour stored in the shared preferences file. */
public final class Settings {

    public static final String PREFS = "musictagger";

    private static final String KEY_SEARCH_THREADS = "search_threads";
    private static final String KEY_DOWNLOAD_THREADS = "download_threads";

    public static final int DEFAULT_SEARCH_THREADS = 2;
    public static final int DEFAULT_DOWNLOAD_THREADS = 3;

    private Settings() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static int searchThreads(Context context) {
        int value = prefs(context).getInt(KEY_SEARCH_THREADS, DEFAULT_SEARCH_THREADS);
        return Math.max(1, Math.min(5, value));
    }

    public static void setSearchThreads(Context context, int threads) {
        prefs(context).edit().putInt(KEY_SEARCH_THREADS, Math.max(1, Math.min(5, threads))).apply();
    }

    public static int downloadThreads(Context context) {
        int value = prefs(context).getInt(KEY_DOWNLOAD_THREADS, DEFAULT_DOWNLOAD_THREADS);
        return Math.max(1, Math.min(8, value));
    }

    public static void setDownloadThreads(Context context, int threads) {
        prefs(context).edit().putInt(KEY_DOWNLOAD_THREADS, Math.max(1, Math.min(8, threads))).apply();
    }
}
