package com.musictagger.app.model;

import android.net.Uri;

/** A local audio file plus its editable tag fields. */
public class Song {
    public Uri uri;
    public String displayName = "";

    public String title = "";
    public String artist = "";
    public String album = "";
    public String albumArtist = "";
    public String year = "";
    public String track = "";
    public String genre = "";
    public String comment = "";
    public String lyrics = "";

    public String format = "";
    public int durationSec;
    public int bitrate;
    public int sampleRate;
    public long size;
    public boolean hasArtwork;

    public String effectiveTitle() {
        return title.isEmpty() ? stripExtension(displayName) : title;
    }

    public static String stripExtension(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(0, i) : name;
    }
}
