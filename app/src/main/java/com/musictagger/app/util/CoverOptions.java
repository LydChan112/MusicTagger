package com.musictagger.app.util;

/** Cover download preference shared by the providers (0 means "largest available"). */
public final class CoverOptions {

    public static final int ORIGINAL = 0;

    private static volatile int size = 800;

    private CoverOptions() {
    }

    public static int size() {
        return size;
    }

    public static void setSize(int pixels) {
        size = Math.max(0, pixels);
    }
}
