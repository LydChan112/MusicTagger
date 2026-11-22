package com.musictagger.app.service;

import android.net.Uri;

import com.musictagger.app.model.MatchItem;

import java.util.ArrayList;
import java.util.List;

/** Keeps the batch review list alive between the list screen and the review screen. */
public final class BatchSession {

    private static final List<MatchItem> ITEMS = new ArrayList<>();
    private static Uri treeUri;

    private BatchSession() {
    }

    public static void set(List<MatchItem> items, Uri tree) {
        ITEMS.clear();
        ITEMS.addAll(items);
        treeUri = tree;
    }

    public static List<MatchItem> items() {
        return ITEMS;
    }

    public static Uri treeUri() {
        return treeUri;
    }

    public static void clear() {
        ITEMS.clear();
        treeUri = null;
    }
}
