package com.musictagger.app.model;

/** Lyrics fetched from a provider: synced LRC plus optional translation. */
public class Lyrics {
    public String lrc = "";
    public String translation = "";
    public String source = "";

    public boolean isEmpty() {
        return lrc.trim().isEmpty() && translation.trim().isEmpty();
    }

    public String asText() {
        if (translation == null || translation.trim().isEmpty()) {
            return lrc;
        }
        return lrc + "\n\n" + translation;
    }

    /** First non-tag lines, for the collapsed preview. */
    public String preview(int maxLines) {
        StringBuilder sb = new StringBuilder();
        int lines = 0;
        for (String line : lrc.split("\n")) {
            String t = line.replaceAll("\\[\\d{1,2}:\\d{1,2}([.:]\\d{1,3})?]", "").trim();
            if (t.isEmpty()) {
                continue;
            }
            if (lines > 0) {
                sb.append('\n');
            }
            sb.append(t);
            if (++lines >= maxLines) {
                break;
            }
        }
        return sb.toString();
    }
}
