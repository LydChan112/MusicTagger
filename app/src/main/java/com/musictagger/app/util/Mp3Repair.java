package com.musictagger.app.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;

/**
 * Repairs MP3 files whose first valid MPEG frame is buried behind a corrupt or
 * oversized leading block. jaudiotagger refuses such files with
 * "No audio header found"; trimming to the first frame makes them readable again.
 */
public final class Mp3Repair {

    private static final int[] BITRATE_V1_L3 = {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0};
    private static final int[] BITRATE_V2_L3 = {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, 0};
    private static final int[] SAMPLE_V1 = {44100, 48000, 32000, 0};
    private static final int[] SAMPLE_V2 = {22050, 24000, 16000, 0};
    private static final int[] SAMPLE_V25 = {11025, 12000, 8000, 0};

    private Mp3Repair() {
    }

    /** @return byte offset of the first validated MPEG-1/2/2.5 Layer III frame, or -1. */
    public static int findFirstFrame(File file) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            int len = (int) Math.min(raf.length(), 4L * 1024 * 1024);
            if (len < 8) {
                return -1;
            }
            byte[] buf = new byte[len];
            raf.readFully(buf);
            for (int i = 0; i + 4 < len; i++) {
                if (!looksLikeFrame(buf, i)) {
                    continue;
                }
                int frameLen = frameLength(buf, i);
                if (frameLen <= 0) {
                    continue;
                }
                int next = i + frameLen;
                if (next + 4 <= len) {
                    // Require the following frame to line up, which rejects random 0xFF bytes.
                    if (looksLikeFrame(buf, next) && frameLength(buf, next) > 0) {
                        return i;
                    }
                } else {
                    return i;
                }
            }
        } catch (IOException ignored) {
        }
        return -1;
    }

    private static boolean looksLikeFrame(byte[] b, int i) {
        if ((b[i] & 0xFF) != 0xFF || (b[i + 1] & 0xE0) != 0xE0) {
            return false;
        }
        int version = (b[i + 1] >> 3) & 0x03;
        int layer = (b[i + 1] >> 1) & 0x03;
        int bitrate = (b[i + 2] >> 4) & 0x0F;
        int sample = (b[i + 2] >> 2) & 0x03;
        return version != 1 && layer == 1 && bitrate != 0 && bitrate != 15 && sample != 3;
    }

    private static int frameLength(byte[] b, int i) {
        int version = (b[i + 1] >> 3) & 0x03;
        int bitrateIndex = (b[i + 2] >> 4) & 0x0F;
        int sampleIndex = (b[i + 2] >> 2) & 0x03;
        int padding = (b[i + 2] >> 1) & 0x01;

        int bitrate;
        int sampleRate;
        if (version == 3) {
            bitrate = BITRATE_V1_L3[bitrateIndex];
            sampleRate = SAMPLE_V1[sampleIndex];
        } else {
            bitrate = BITRATE_V2_L3[bitrateIndex];
            sampleRate = version == 2 ? SAMPLE_V2[sampleIndex] : SAMPLE_V25[sampleIndex];
        }
        if (bitrate <= 0 || sampleRate <= 0) {
            return -1;
        }
        int coefficient = version == 3 ? 144 : 72;
        return coefficient * bitrate * 1000 / sampleRate + padding;
    }

    /** Drops everything before {@code offset} in place. Returns false when nothing was changed. */
    public static boolean stripLeading(File file, int offset) {
        if (offset <= 0 || offset >= file.length()) {
            return false;
        }
        File tmp = new File(file.getParentFile(), file.getName() + ".repair");
        try (InputStream in = new FileInputStream(file);
             OutputStream out = new FileOutputStream(tmp)) {
            long skipped = 0;
            while (skipped < offset) {
                long s = in.skip(offset - skipped);
                if (s <= 0) {
                    break;
                }
                skipped += s;
            }
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return false;
        }
        if (!file.delete() || !tmp.renameTo(file)) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return false;
        }
        return true;
    }
}
