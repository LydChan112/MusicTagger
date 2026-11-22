package com.musictagger.app.model;

/** One search hit from an online provider. */
public class Track {
    public String source;
    public String id;
    public String title = "";
    public String artist = "";
    public String album = "";
    public int durationSec;
    public String coverUrl;
    /** Provider specific payload, e.g. kugou hash. */
    public String extra;

    /** 0..1 relevance, filled by the matcher. */
    public float score;

    public Track() {
    }

    public Track(String source, String id, String title, String artist, String album) {
        this.source = source;
        this.id = id;
        this.title = title == null ? "" : title;
        this.artist = artist == null ? "" : artist;
        this.album = album == null ? "" : album;
    }

    public String label() {
        StringBuilder sb = new StringBuilder(title);
        if (!artist.isEmpty()) {
            sb.append(" - ").append(artist);
        }
        if (!album.isEmpty()) {
            sb.append("  ·  ").append(album);
        }
        return sb.toString();
    }
}
