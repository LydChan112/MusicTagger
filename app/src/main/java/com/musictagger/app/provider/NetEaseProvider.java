package com.musictagger.app.provider;

import com.musictagger.app.model.Lyrics;
import com.musictagger.app.model.Track;
import com.musictagger.app.net.Http;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NetEase Cloud Music public web endpoints.
 * Search -> /api/search/get, detail -> /api/song/detail, lyrics -> /api/song/lyric.
 */
public class NetEaseProvider implements MusicProvider {

    private static final String BASE = "https://music.163.com";
    private static final Map<String, String> HEADERS = new HashMap<>();

    static {
        HEADERS.put("Referer", "https://music.163.com/");
        HEADERS.put("Cookie", "appver=2.0.2; os=android");
    }

    @Override
    public String name() {
        return "网易云";
    }

    @Override
    public List<Track> search(String keyword) throws Exception {
        String url = BASE + "/api/search/get?s=" + java.net.URLEncoder.encode(keyword, "UTF-8")
                + "&type=1&offset=0&limit=20";
        JSONObject root = new JSONObject(Http.get(url, HEADERS));
        List<Track> out = new ArrayList<>();
        JSONObject result = root.optJSONObject("result");
        if (result == null) {
            return out;
        }
        JSONArray songs = result.optJSONArray("songs");
        if (songs == null) {
            return out;
        }
        for (int i = 0; i < songs.length(); i++) {
            JSONObject s = songs.getJSONObject(i);
            Track t = new Track();
            t.source = name();
            t.id = String.valueOf(s.optLong("id"));
            t.title = s.optString("name");
            t.album = s.optJSONObject("album") != null
                    ? s.optJSONObject("album").optString("name") : "";
            t.durationSec = s.optInt("duration") / 1000;
            t.artist = joinArtists(s.optJSONArray("artists"));
            out.add(t);
        }
        return out;
    }

    private static String joinArtists(JSONArray arr) {
        if (arr == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject a = arr.optJSONObject(i);
            if (a == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('/');
            }
            sb.append(a.optString("name"));
        }
        return sb.toString();
    }

    private String picUrl(Track track) throws Exception {
        String url = BASE + "/api/song/detail?ids=[" + track.id + "]";
        JSONObject root = new JSONObject(Http.get(url, HEADERS));
        JSONArray songs = root.optJSONArray("songs");
        if (songs == null || songs.length() == 0) {
            return null;
        }
        JSONObject album = songs.getJSONObject(0).optJSONObject("album");
        if (album == null) {
            return null;
        }
        String pic = album.optString("picUrl");
        if (pic == null || pic.isEmpty()) {
            return null;
        }
        return pic.replace("http://", "https://");
    }

    private static String withParam(String url, int size) {
        String base = url.contains("?") ? url.substring(0, url.indexOf('?')) : url;
        return base + "?param=" + size + "y" + size;
    }

    /** Original file first when "largest" is chosen; otherwise the requested square size. */
    @Override
    public byte[] cover(Track track) throws Exception {
        String pic = picUrl(track);
        if (pic == null) {
            return null;
        }
        int size = com.musictagger.app.util.CoverOptions.size();
        if (size > 0) {
            return Http.getBytes(withParam(pic, size), HEADERS);
        }
        String original = pic.contains("?") ? pic.substring(0, pic.indexOf('?')) : pic;
        try {
            byte[] data = Http.getBytes(original, HEADERS);
            if (data != null && data.length > 0) {
                return data;
            }
        } catch (Exception ignored) {
        }
        return Http.getBytes(withParam(pic, 1024), HEADERS);
    }

    @Override
    public byte[] thumbnail(Track track) {
        try {
            String pic = picUrl(track);
            return pic == null ? null : Http.getBytes(withParam(pic, 120), HEADERS);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Lyrics lyrics(Track track) throws Exception {
        String url = BASE + "/api/song/lyric?id=" + track.id + "&lv=-1&kv=-1&tv=-1";
        JSONObject root = new JSONObject(Http.get(url, HEADERS));
        JSONObject lrc = root.optJSONObject("lrc");
        if (root.optInt("code", 200) != 200 && lrc == null) {
            return null;
        }
        Lyrics l = new Lyrics();
        l.source = name();
        l.lrc = lrc == null ? "" : lrc.optString("lyric");
        JSONObject tlyric = root.optJSONObject("tlyric");
        if (tlyric != null) {
            l.translation = tlyric.optString("lyric");
        }
        return l.isEmpty() ? null : l;
    }
}
