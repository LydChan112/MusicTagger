package com.musictagger.app.provider;

import com.musictagger.app.model.Lyrics;
import com.musictagger.app.model.Track;
import com.musictagger.app.net.Http;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Apple iTunes Search API: reliable high resolution artwork for most releases. */
public class ItunesProvider implements MusicProvider {

    @Override
    public String name() {
        return "iTunes";
    }

    @Override
    public List<Track> search(String keyword) throws Exception {
        String url = "https://itunes.apple.com/search?term="
                + java.net.URLEncoder.encode(keyword, "UTF-8")
                + "&media=music&entity=song&limit=20&country=CN";
        JSONObject root = new JSONObject(Http.get(url, null));
        JSONArray results = root.optJSONArray("results");
        List<Track> out = new ArrayList<>();
        if (results == null) {
            return out;
        }
        for (int i = 0; i < results.length(); i++) {
            JSONObject o = results.getJSONObject(i);
            Track t = new Track();
            t.source = name();
            t.id = o.optString("trackId");
            t.title = o.optString("trackName");
            t.artist = o.optString("artistName");
            t.album = o.optString("collectionName");
            t.durationSec = o.optInt("trackTimeMillis") / 1000;
            t.coverUrl = o.optString("artworkUrl100", "");
            out.add(t);
        }
        return out;
    }

    private static String sized(String url, int size) {
        return url.replace("100x100bb", size + "x" + size + "bb")
                .replace("100x100", size + "x" + size);
    }

    @Override
    public byte[] cover(Track track) throws Exception {
        String url = track.coverUrl;
        if (url == null || url.isEmpty()) {
            return null;
        }
        // Apple caps artwork at the original size; walk down until one variant answers.
        for (int size : new int[]{1200, 600, 300, 100}) {
            try {
                byte[] data = Http.getBytes(sized(url, size), null);
                if (data != null && data.length > 0) {
                    return data;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    @Override
    public byte[] thumbnail(Track track) {
        try {
            String url = track.coverUrl;
            return url == null || url.isEmpty() ? null : Http.getBytes(sized(url, 200), null);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Lyrics lyrics(Track track) {
        return null;
    }
}
