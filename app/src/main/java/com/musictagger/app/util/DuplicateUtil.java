package com.musictagger.app.util;

import com.musictagger.app.model.Track;

/** Decides whether an existing tag already describes the matched song. */
public final class DuplicateUtil {

    private static final float TITLE_SAME = 0.90f;
    private static final float ARTIST_SAME = 0.70f;

    private DuplicateUtil() {
    }

    /**
     * True when the file already carries this song's title and artist.
     * A missing artist on either side is not treated as a match, because the tag
     * would still need to be completed.
     */
    public static boolean sameSong(String existingTitle, String existingArtist, Track track) {
        if (track == null || isEmpty(existingTitle) || isEmpty(track.title)) {
            return false;
        }
        float titleScore = TextUtil.similarity(existingTitle, track.title);
        if (titleScore < TITLE_SAME) {
            return false;
        }
        if (isEmpty(existingArtist) || isEmpty(track.artist)) {
            return false;
        }
        return TextUtil.similarity(existingArtist, track.artist) >= ARTIST_SAME;
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }
}
