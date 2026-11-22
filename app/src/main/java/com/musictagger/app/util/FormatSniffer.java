package com.musictagger.app.util;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/** Detects the real container from magic bytes instead of trusting the file extension. */
public final class FormatSniffer {

    private FormatSniffer() {
    }

    /** @return one of mp3, flac, ogg, wav, m4a, aiff, ape, dsf, dff, wma or "unknown". */
    public static String detect(File file) {
        byte[] head = new byte[16];
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            int n = raf.read(head);
            if (n < 4) {
                return "unknown";
            }
        } catch (IOException e) {
            return "unknown";
        }
        return detect(head);
    }

    public static String detect(byte[] h) {
        if (h == null || h.length < 4) {
            return "unknown";
        }
        if (h[0] == 'f' && h[1] == 'L' && h[2] == 'a' && h[3] == 'C') {
            return "flac";
        }
        if (h[0] == 'O' && h[1] == 'g' && h[2] == 'g' && h[3] == 'S') {
            return "ogg";
        }
        if (h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F') {
            return "wav";
        }
        if (h[0] == 'M' && h[1] == 'A' && h[2] == 'C' && h[3] == ' ') {
            return "ape";
        }
        if (h[0] == 'D' && h[1] == 'S' && h[2] == 'D' && h[3] == ' ') {
            return "dsf";
        }
        if (h[0] == 'F' && h[1] == 'R' && h[2] == 'M' && h[3] == '8') {
            return "dff";
        }
        if (h[0] == 'F' && h[1] == 'O' && h[2] == 'R' && h[3] == 'M') {
            return "aiff";
        }
        if (h.length >= 8 && h[4] == 'f' && h[5] == 't' && h[6] == 'y' && h[7] == 'p') {
            return "m4a";
        }
        if (h[0] == 'I' && h[1] == 'D' && h[2] == '3') {
            return "mp3";
        }
        if ((h[0] & 0xFF) == 0xFF && (h[1] & 0xE0) == 0xE0) {
            return "mp3";
        }
        // ASF (WMA) GUID 30 26 B2 75 8E 66 CF 11
        if (h.length >= 8 && (h[0] & 0xFF) == 0x30 && (h[1] & 0xFF) == 0x26
                && (h[2] & 0xFF) == 0xB2 && (h[3] & 0xFF) == 0x75) {
            return "wma";
        }
        return "unknown";
    }

    /** Formats jaudiotagger can actually write. */
    public static boolean isWritable(String format) {
        return "mp3".equals(format) || "flac".equals(format) || "ogg".equals(format)
                || "wav".equals(format) || "m4a".equals(format) || "wma".equals(format);
    }

    public static String display(String format) {
        return format == null || format.isEmpty() ? "未知" : format.toUpperCase();
    }
}
