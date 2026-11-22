package com.musictagger.app.provider;

import com.musictagger.app.model.Lyrics;
import com.musictagger.app.model.Track;
import com.musictagger.app.net.Http;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Kugou search (mobile CDN JSON API). Covers come from the album info endpoint. */
public class KugouProvider implements MusicProvider {

    @Override
    public String name() {
        return "酷狗";
    }

    @Override
    public List<Track> search(String keyword) throws Exception {
        String url = "http://mobilecdn.kugou.com/api/v3/search/song?format=json&keyword="
                + java.net.URLEncoder.encode(keyword, "UTF-8")
                + "&page=1&pagesize=20&showtype=1";
        JSONObject root = new JSONObject(Http.get(url, null));
        JSONObject data = root.optJSONObject("data");
        List<Track> out = new ArrayList<>();
        if (data == null) {
            return out;
        }
        JSONArray info = data.optJSONArray("info");
        if (info == null) {
            return out;
        }
        for (int i = 0; i < info.length(); i++) {
            JSONObject o = info.getJSONObject(i);
            Track t = new Track();
            t.source = name();
            t.extra = o.optString("hash");
            t.id = o.optString("album_id");
            t.title = o.optString("songname");
            t.artist = o.optString("singername");
            t.album = o.optString("album_name");
            t.durationSec = o.optInt("duration");
            out.add(t);
        }
        return out;
    }

    /** Album image template, still containing "{size}". */
    private String imageTemplate(Track track) throws Exception {
        if (track.id == null || track.id.isEmpty() || "0".equals(track.id)) {
            return null;
        }
        String url = "http://mobilecdn.kugou.com/api/v3/album/info?albumid=" + track.id;
        JSONObject root = new JSONObject(Http.get(url, null));
        JSONObject data = root.optJSONObject("data");
        if (data == null) {
            return null;
        }
        for (String key : new String[]{"imgurl", "imgurl_500", "imgurl_1000"}) {
            String v = data.optString(key, "");
            if (v != null && !v.isEmpty()) {
                return v;
            }
        }
        return null;
    }

    @Override
    public byte[] cover(Track track) throws Exception {
        String template = imageTemplate(track);
        if (template == null) {
            return null;
        }
        int size = com.musictagger.app.util.CoverOptions.size();
        return Http.getBytes(template.replace("{size}", String.valueOf(size <= 0 ? 1000 : size)), null);
    }

    @Override
    public byte[] thumbnail(Track track) {
        try {
            String template = imageTemplate(track);
            return template == null ? null : Http.getBytes(template.replace("{size}", "150"), null);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Lyrics lyrics(Track track) {
        // Kugou serves KRC (encrypted) for lyrics; use NetEase / LRCLIB instead.
        return null;
    }
}
