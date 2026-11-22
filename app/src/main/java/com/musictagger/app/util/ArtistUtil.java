package com.musictagger.app.util;

import java.util.regex.Pattern;

/** Normalises multi-artist strings onto one separator chosen in the settings. */
public final class ArtistUtil {

    /** Separators providers actually use between artists; commas and "&" are left alone. */
    private static final Pattern SPLIT = Pattern.compile("\\s*[/;；、|]\\s*");

    private ArtistUtil() {
    }

    public static String normalize(String raw, String separator) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        if (value.isEmpty() || separator == null || separator.isEmpty()) {
            return value;
        }
        String[] parts = SPLIT.split(value);
        if (parts.length <= 1) {
            return value;
        }
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            String item = part.trim();
            if (item.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(separator);
            }
            sb.append(item);
        }
        return sb.length() == 0 ? value : sb.toString();
    }
}
