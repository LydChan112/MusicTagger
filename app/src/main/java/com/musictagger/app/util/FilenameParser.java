package com.musictagger.app.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Turns a file name into searchable guesses.
 * Handles "01. Artist - Title", "Artist - Title", "Artist《Title》", "Title (Artist)"
 * and plain "Title", including full width dashes and underscore separators.
 */
public final class FilenameParser {

    /** One interpretation of a file name. */
    public static class Guess {
        public final String title;
        public final String artist;
        public final String query;

        public Guess(String title, String artist) {
            this.title = title == null ? "" : title.trim();
            this.artist = artist == null ? "" : artist.trim();
            this.query = (this.title + " " + this.artist).trim();
        }

        public boolean isEmpty() {
            return title.isEmpty() && artist.isEmpty();
        }
    }

    private static final Pattern TRACK_PREFIX =
            Pattern.compile("^\\s*(?:cd\\s*)?\\d{1,3}\\s*[.、)．_\\-]\\s*", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRACK_PREFIX_SPACE = Pattern.compile("^\\s*0\\d{1,2}\\s+");
    private static final Pattern NOISE = Pattern.compile(
            "(?i)[\\[【(（]\\s*(?:flac|mp3|ape|wav|wma|m4a|ogg|aac|320k|320kbps|128k|hq|sq|"
                    + "无损|高音质|超高音质|vbr|dts|ost|原唱|伴奏|纯音乐)\\s*[\\]】)）]");
    private static final String[] DASHES = {" - ", " – ", " — ", " － ", " -", "- ", "－", "—", "–"};

    private FilenameParser() {
    }

    public static Guess parse(String fileName) {
        String base = stripExtension(fileName == null ? "" : fileName);
        base = stripAtPrefixes(base);
        String raw = base.trim();
        int underscore = raw.indexOf('_');
        if (underscore > 0 && underscore == raw.lastIndexOf('_') && raw.indexOf('-') < 0) {
            // "Artist_Title" is a common separator, not a space.
            String left = clean(raw.substring(0, underscore));
            String right = clean(raw.substring(underscore + 1));
            if (!left.isEmpty() && !right.isEmpty()) {
                return new Guess(right, left);
            }
        }
        String s = base.replace('_', ' ').replace('\u3000', ' ').trim();
        s = NOISE.matcher(s).replaceAll(" ");
        s = s.replaceAll("\\s{2,}", " ").trim();
        s = TRACK_PREFIX.matcher(s).replaceFirst("");
        if (s.matches("^\\s*0\\d{1,2}\\s+.*")) {
            s = TRACK_PREFIX_SPACE.matcher(s).replaceFirst("");
        }
        s = s.replaceAll("^[\\s\\-–—－]+|[\\s\\-–—－]+$", "").trim();
        if (s.isEmpty()) {
            return new Guess("", "");
        }

        int cut = -1;
        int len = 0;
        for (String dash : DASHES) {
            int i = s.indexOf(dash);
            if (i > 0 && i < s.length() - dash.length()) {
                cut = i;
                len = dash.length();
                break;
            }
        }

        if (cut > 0) {
            String left = clean(s.substring(0, cut));
            String right = clean(s.substring(cut + len));
            if (!left.isEmpty() && !right.isEmpty()) {
                return new Guess(right, left);
            }
        }

        int open = s.indexOf('《');
        int close = s.indexOf('》');
        if (open > 0 && close > open) {
            String artist = clean(s.substring(0, open));
            String title = clean(s.substring(open + 1, close));
            if (!artist.isEmpty() && !title.isEmpty()) {
                return new Guess(title, artist);
            }
        }

        int paren = s.lastIndexOf('(');
        if (paren > 0 && s.endsWith(")")) {
            String artist = clean(s.substring(paren + 1, s.length() - 1));
            String title = clean(s.substring(0, paren));
            if (!artist.isEmpty() && !title.isEmpty()) {
                return new Guess(title, artist);
            }
        }

        return new Guess(clean(s), "");
    }

    private static String stripExtension(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 ? name.substring(0, i) : name;
    }

    /**
     * Drops the first two '@'-marked prefixes, e.g. "@pop@cn@晴天 - 周杰伦" -> "晴天 - 周杰伦".
     * A leading '@' means each prefix carries its own marker, so two prefixes plus the
     * marker before the real content have to be consumed; otherwise the '@' acts as a
     * separator and only two of them are consumed.
     */
    public static String stripAtPrefixes(String name) {
        if (name == null || name.indexOf('@') < 0) {
            return name == null ? "" : name;
        }
        String s = name.replace('＠', '@').trim();
        boolean leading = s.startsWith("@");
        int target = leading ? 3 : 2;
        int seen = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '@') {
                seen++;
                if (seen == target) {
                    String rest = s.substring(i + 1).trim();
                    return rest.isEmpty() ? s : rest;
                }
            }
        }
        return s;
    }

    private static String clean(String s) {
        String out = s.trim();
        out = out.replaceAll("^[\"'“”‘’]+|[\"'“”‘’]+$", "").trim();
        return out;
    }

    /**
     * Both interpretations of a file name. "A - B" is usually "artist - title",
     * but libraries that store "title - artist" are common enough to keep the swap
     * as a scoring candidate instead of guessing once.
     */
    public static List<Guess> variants(String fileName) {
        Guess primary = parse(fileName);
        if (primary.isEmpty()) {
            return Collections.emptyList();
        }
        List<Guess> out = new ArrayList<>();
        out.add(primary);
        if (!primary.artist.isEmpty() && !primary.title.isEmpty()) {
            Guess swapped = new Guess(primary.artist, primary.title);
            if (!swapped.query.equals(primary.query)) {
                out.add(swapped);
            }
        }
        return out;
    }
}
