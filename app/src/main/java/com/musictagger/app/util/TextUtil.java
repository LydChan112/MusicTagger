package com.musictagger.app.util;

/** String normalisation and fuzzy comparison used by the matcher. */
public final class TextUtil {

    private TextUtil() {
    }

    public static String normalize(String s) {
        if (s == null) {
            return "";
        }
        String out = s.toLowerCase();
        out = out.replaceAll("\\([^)]*\\)", " ");
        out = out.replaceAll("\\[[^\\]]*\\]", " ");
        out = out.replaceAll("(?i)\\b(feat|ft|featuring)\\b.*$", " ");
        out = out.replaceAll("[\\p{Punct}\\s·・、，。！？：；“”‘’（）《》]+", "");
        return out.trim();
    }

    public static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return prev[b.length()];
    }

    /** 0..1 similarity of two free-form strings. */
    public static float similarity(String a, String b) {
        String x = normalize(a);
        String y = normalize(b);
        if (x.isEmpty() || y.isEmpty()) {
            return 0f;
        }
        if (x.equals(y)) {
            return 1f;
        }
        if (x.contains(y) || y.contains(x)) {
            return 0.85f;
        }
        int d = levenshtein(x, y);
        float ratio = 1f - (float) d / Math.max(x.length(), y.length());
        return Math.max(0f, Math.min(1f, ratio));
    }

    /** Combined title/artist score; artist is optional but weighted when present. */
    public static float score(String wantTitle, String wantArtist,
                              String gotTitle, String gotArtist) {
        float t = similarity(wantTitle, gotTitle);
        float a = similarity(wantArtist, gotArtist);
        if (wantArtist == null || wantArtist.trim().isEmpty()) {
            return t;
        }
        // Artist match matters, but a missing/featured-artist field should not kill the hit.
        return (float) (t * 0.65 + a * 0.35);
    }
}
