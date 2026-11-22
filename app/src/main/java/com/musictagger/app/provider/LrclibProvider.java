package com.musictagger.app.provider;

import com.musictagger.app.model.Lyrics;
import com.musictagger.app.net.Http;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** LRCLIB: free, no-key lyrics database with real time stamps. */
public class LrclibProvider implements MusicProvider {

    @Override
    public String name() {
        return "LRCLIB";
    }

    public static class Hit {
        public String title;
        public String artist;
        public String album;
        public int duration;
        public String synced;
        public String plain;
    }

    @Override
    public List<com.musictagger.app.model.Track> search(String keyword) {
        return new ArrayList<>();
    }

    public List<Hit> searchRich(String keyword) throws Exception {
        String url = "https://lrclib.net/api/search?q=" + java.net.URLEncoder.encode(keyword, "UTF-8");
        JSONArray arr = new JSONArray(Http.get(url, null));
        List<Hit> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            Hit h = new Hit();
            h.title = o.optString("trackName");
            h.artist = o.optString("artistName");
            h.album = o.optString("albumName");
            h.duration = (int) Math.round(o.optDouble("duration", 0));
            h.synced = o.optString("syncedLyrics", "");
            h.plain = o.optString("plainLyrics", "");
            if ((h.synced != null && !h.synced.isEmpty()) || (h.plain != null && !h.plain.isEmpty())) {
                out.add(h);
            }
        }
        return out;
    }

    /** Exact lookup, closest duration wins. */
    public Lyrics fetch(String title, String artist, int durationSec) throws Exception {
        StringBuilder url = new StringBuilder("https://lrclib.net/api/get?");
        url.append("track_name=").append(java.net.URLEncoder.encode(title, "UTF-8"));
        if (artist != null && !artist.isEmpty()) {
            url.append("&artist_name=").append(java.net.URLEncoder.encode(artist, "UTF-8"));
        }
        if (durationSec > 0) {
            url.append("&duration=").append(durationSec);
        }
        try {
            JSONObject o = new JSONObject(Http.get(url.toString(), null));
            Lyrics l = new Lyrics();
            l.source = name();
            l.lrc = o.optString("syncedLyrics", "");
            if (l.lrc == null || l.lrc.isEmpty()) {
                String plain = o.optString("plainLyrics", "");
                l.lrc = plain == null ? "" : plain;
            }
            return l.isEmpty() ? null : l;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public byte[] cover(com.musictagger.app.model.Track track) {
        return null;
    }

    @Override
    public Lyrics lyrics(com.musictagger.app.model.Track track) {
        return null;
    }
}
