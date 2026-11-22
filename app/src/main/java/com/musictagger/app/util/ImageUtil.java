package com.musictagger.app.util;

/** Minimal JPEG/PNG dimension reader so artwork metadata never touches java.awt. */
public final class ImageUtil {

    private ImageUtil() {
    }

    /** @return {width, height} or {0, 0} when unknown. */
    public static int[] size(byte[] d) {
        try {
            if (d.length > 24 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') {
                int w = ((d[16] & 0xFF) << 24) | ((d[17] & 0xFF) << 16) | ((d[18] & 0xFF) << 8) | (d[19] & 0xFF);
                int h = ((d[20] & 0xFF) << 24) | ((d[21] & 0xFF) << 16) | ((d[22] & 0xFF) << 8) | (d[23] & 0xFF);
                return new int[]{w, h};
            }
            if ((d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8) {
                int i = 2;
                while (i + 9 < d.length) {
                    if ((d[i] & 0xFF) != 0xFF) {
                        i++;
                        continue;
                    }
                    int marker = d[i + 1] & 0xFF;
                    if (marker == 0xD8 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                        i += 2;
                        continue;
                    }
                    int len = ((d[i + 2] & 0xFF) << 8) | (d[i + 3] & 0xFF);
                    boolean isSof = (marker >= 0xC0 && marker <= 0xC3)
                            || (marker >= 0xC5 && marker <= 0xC7)
                            || (marker >= 0xC9 && marker <= 0xCB)
                            || (marker >= 0xCD && marker <= 0xCF);
                    if (isSof) {
                        int h = ((d[i + 5] & 0xFF) << 8) | (d[i + 6] & 0xFF);
                        int w = ((d[i + 7] & 0xFF) << 8) | (d[i + 8] & 0xFF);
                        return new int[]{w, h};
                    }
                    if (marker == 0xDA) {
                        break;
                    }
                    i += 2 + len;
                }
            }
        } catch (Exception ignored) {
        }
        return new int[]{0, 0};
    }
}
