package com.musictagger.app.model;

import androidx.documentfile.provider.DocumentFile;

import java.util.ArrayList;
import java.util.List;

/** One file waiting in the batch review list. */
public class MatchItem {

    public static final int READY = 0;
    public static final int DUPLICATE_TAG = 1;
    public static final int DUPLICATE_BATCH = 2;
    public static final int NO_MATCH = 3;
    public static final int ERROR = 4;
    /** Tags already match, but the file has no artwork: still worth writing. */
    public static final int MISSING_ART = 5;

    public DocumentFile file;
    public String fileName = "";
    public String existingTitle = "";
    public String existingArtist = "";
    public boolean hasArtwork;
    public List<Track> candidates = new ArrayList<>();
    public int chosen = -1;
    public int status = NO_MATCH;
    public String note = "";

    public Track chosenTrack() {
        return chosen >= 0 && chosen < candidates.size() ? candidates.get(chosen) : null;
    }

    public boolean writable() {
        return (status == READY || status == MISSING_ART) && chosenTrack() != null;
    }

    public String statusLabel() {
        switch (status) {
            case READY:
                return "待写入";
            case MISSING_ART:
                return "补封面";
            case DUPLICATE_TAG:
                return "重复·跳过";
            case DUPLICATE_BATCH:
                return "列表内重复";
            case ERROR:
                return "失败";
            default:
                return "无匹配";
        }
    }
}
