package com.musictagger.app.provider;

import android.util.Base64;

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
 * QQ Music public web endpoints.
 * Search -> /soso/fcgi-bin/client_search_cp, cover -> y.gtimg.cn album art,
 * lyrics -> /lyric/fcgi-bin/fcg_query_lyric_new.fcg (base64 LRC).
 */
public class QQProvider implements MusicProvider {

    private static final Map<String, String> HEADERS = new HashMap<>();

    static {
        HEADERS.put("Referer", "https://y.qq.com/");
        HEADERS.put("Accept", "application/json, text/plain, */*");
    }

    @Override
    public String name() {
        return "QQ音乐";
    }

    @Override
    public List<Track> search(String keyword) throws Exception {
        String url = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?p=1&n=20&format=json&w="
                + java.net.URLEncoder.encode(keyword, "UTF-8");
        JSONObject root = new JSONObject(Http.get(url, HEADERS));
        JSONObject data = root.optJSONObject("data");
        List<Track> out = new ArrayList<>();
        if (data == null) {
            return out;
        }
        JSONObject song = data.optJSONObject("song");
        if (song == null) {
            return out;
        }
        JSONArray list = song.optJSONArray("list");
        if (list == null) {
            return out;
        }
        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.getJSONObject(i);
            Track t = new Track();
            t.source = name();
            t.id = o.optString("songmid");
            t.title = o.optString("songname");
            t.artist = joinSingers(o.optJSONArray("singer"));
            t.album = o.optString("albumname");
            t.durationSec = o.optInt("interval");
            t.extra = o.optString("albummid");
            out.add(t);
        }
        return out;
    }

    private static String joinSingers(JSONArray arr) {
        if (arr == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject s = arr.optJSONObject(i);
            if (s == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('/');
            }
            sb.append(s.optString("name"));
        }
        return sb.toString();
    }

    private static String coverUrl(Track track, int size) {
        String albumMid = track.extra;
        if (albumMid == null || albumMid.isEmpty()) {
            return null;
        }
        return "https://y.gtimg.cn/music/photo_new/T002R" + size + "x" + size + "M000" + albumMid + ".jpg";
    }

    @Override
    public byte[] cover(Track track) throws Exception {
        String url = coverUrl(track, 800);
        return url == null ? null : Http.getBytes(url, HEADERS);
    }

    @Override
    public byte[] thumbnail(Track track) {
        try {
            String url = coverUrl(track, 150);
            return url == null ? null : Http.getBytes(url, HEADERS);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Lyrics lyrics(Track track) throws Exception {
        if (track.id == null || track.id.isEmpty()) {
            return null;
        }
        String url = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg?songmid="
                + track.id + "&format=json&g_tk=5381";
        JSONObject root = new JSONObject(Http.get(url, HEADERS));
        if (root.optInt("retcode", -1) != 0) {
            return null;
        }
        String encoded = root.optString("lyric", "");
        if (encoded == null || encoded.isEmpty()) {
            return null;
        }
        String text = new String(Base64.decode(encoded, Base64.DEFAULT), "UTF-8");
        text = unescape(text);
        Lyrics lyrics = new Lyrics();
        lyrics.source = name();
        lyrics.lrc = text;
        if (lyrics.lrc.trim().isEmpty()) {
            return null;
        }
        JSONObject trans = root.optJSONObject("trans");
        if (trans != null) {
            String t = trans.optString("lyric", "");
            if (t != null && !t.isEmpty()) {
                lyrics.translation = new String(Base64.decode(t, Base64.DEFAULT), "UTF-8");
            }
        }
        return lyrics;
    }

    private static String unescape(String s) {
        return s.replace("&apos;", "'")
                .replace("&quot;", "\"")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&");
    }
}
