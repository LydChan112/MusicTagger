package com.musictagger.app.provider;

import com.musictagger.app.model.Lyrics;
import com.musictagger.app.model.Track;

import java.util.List;

public interface MusicProvider {
    String name();

    List<Track> search(String keyword) throws Exception;

    /** Cover bytes for a specific hit, at the highest resolution the source offers. */
    byte[] cover(Track track) throws Exception;

    /** Small preview used in lists; providers override this to ask for a low-res variant. */
    default byte[] thumbnail(Track track) throws Exception {
        return cover(track);
    }

    /** Lyrics for a specific hit, or null when unavailable. */
    Lyrics lyrics(Track track) throws Exception;
}
