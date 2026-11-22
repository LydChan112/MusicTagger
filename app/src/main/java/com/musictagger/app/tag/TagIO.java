package com.musictagger.app.tag;

import com.musictagger.app.model.Song;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.audio.flac.metadatablock.MetadataBlockDataPicture;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.flac.FlacTag;
import org.jaudiotagger.tag.images.Artwork;
import org.jaudiotagger.tag.images.ArtworkFactory;

import java.io.File;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.musictagger.app.util.ImageUtil;
import com.musictagger.app.util.Mp3Repair;

/** Read and write tags through jaudiotagger (MP3/FLAC/M4A/OGG/WMA/WAV). */
public final class TagIO {

    static {
        Logger.getLogger("org.jaudiotagger").setLevel(Level.OFF);
    }

    private TagIO() {
    }

    public static void read(File file, Song song) throws Exception {
        AudioFile af = open(file);
        Tag tag = af.getTag();
        if (tag != null) {
            song.title = value(tag, FieldKey.TITLE);
            song.artist = value(tag, FieldKey.ARTIST);
            song.album = value(tag, FieldKey.ALBUM);
            song.albumArtist = value(tag, FieldKey.ALBUM_ARTIST);
            song.year = value(tag, FieldKey.YEAR);
            song.track = value(tag, FieldKey.TRACK);
            song.genre = value(tag, FieldKey.GENRE);
            song.comment = value(tag, FieldKey.COMMENT);
            song.lyrics = value(tag, FieldKey.LYRICS);
            song.hasArtwork = tag.getFirstArtwork() != null;
        }
        AudioHeader header = af.getAudioHeader();
        if (header != null) {
            song.format = header.getFormat();
            song.durationSec = header.getTrackLength();
            try {
                song.bitrate = (int) header.getBitRateAsNumber();
            } catch (Exception ignored) {
            }
            try {
                song.sampleRate = header.getSampleRateAsNumber();
            } catch (Exception ignored) {
            }
        }
    }

    private static String value(Tag tag, FieldKey key) {
        try {
            String v = tag.getFirst(key);
            return v == null ? "" : v;
        } catch (Exception e) {
            return "";
        }
    }

    public static byte[] readArtwork(File file) {
        try {
            AudioFile af = open(file);
            Tag tag = af.getTag();
            if (tag == null) {
                return null;
            }
            Artwork art = tag.getFirstArtwork();
            return art == null ? null : art.getBinaryData();
        } catch (Exception e) {
            return null;
        }
    }

    /** @return false only when artwork was supplied but could not be embedded. */
    public static boolean write(File file, Song song, byte[] artwork) throws Exception {
        AudioFile af = open(file);
        Tag tag = af.getTagOrCreateAndSetDefault();

        put(tag, FieldKey.TITLE, song.title);
        put(tag, FieldKey.ARTIST, song.artist);
        put(tag, FieldKey.ALBUM, song.album);
        put(tag, FieldKey.ALBUM_ARTIST, song.albumArtist);
        put(tag, FieldKey.YEAR, song.year);
        put(tag, FieldKey.TRACK, song.track);
        put(tag, FieldKey.GENRE, song.genre);
        put(tag, FieldKey.COMMENT, song.comment);
        if (song.lyrics != null && !song.lyrics.trim().isEmpty()) {
            put(tag, FieldKey.LYRICS, song.lyrics);
        }

        boolean artworkWritten = true;
        if (artwork != null && artwork.length > 0) {
            artworkWritten = writeArtwork(tag, artwork);
        }
        af.commit();
        return artworkWritten;
    }

    /**
     * Opens the file, repairing a broken MP3 leading block first.
     * Files whose first valid MPEG frame sits behind junk make jaudiotagger throw
     * "No audio header found"; trimming to that frame makes them readable again.
     */
    private static AudioFile open(File file) throws Exception {
        try {
            return AudioFileIO.read(file);
        } catch (Exception first) {
            if (repairMp3(file)) {
                return AudioFileIO.read(file);
            }
            throw first;
        }
    }

    private static boolean repairMp3(File file) {
        String name = file.getName().toLowerCase(java.util.Locale.ROOT);
        if (!name.endsWith(".mp3")) {
            return false;
        }
        try {
            int offset = Mp3Repair.findFirstFrame(file);
            return offset > 0 && Mp3Repair.stripLeading(file, offset);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean writeArtwork(Tag tag, byte[] data) {
        String mime = mimeOf(data);
        int[] size = ImageUtil.size(data);
        if (tag instanceof FlacTag) {
            // jaudiotagger's generic path decodes the image with java.awt, which Android lacks.
            try {
                FlacTag flac = (FlacTag) tag;
                List<MetadataBlockDataPicture> images = flac.getImages();
                images.clear();
                images.add(new MetadataBlockDataPicture(data, 3, mime, "", size[0], size[1], 0, 0));
                return true;
            } catch (Throwable e) {
                com.musictagger.app.util.Diag.error("写入 FLAC 封面", e);
            }
        }
        try {
            Artwork art = ArtworkFactory.getNew();
            art.setMimeType(mime);
            art.setBinaryData(data);
            art.setWidth(size[0]);
            art.setHeight(size[1]);
            tag.deleteArtworkField();
            tag.setField(art);
            return true;
        } catch (Throwable e) {
            com.musictagger.app.util.Diag.error("写入封面", e);
            return false;
        }
    }

    private static void put(Tag tag, FieldKey key, String value) {
        try {
            if (value == null || value.trim().isEmpty()) {
                tag.deleteField(key);
            } else {
                tag.setField(key, value.trim());
            }
        } catch (Exception ignored) {
            // Some formats refuse individual fields; keep the rest of the write going.
        }
    }

    public static String mimeOf(byte[] data) {
        if (data != null && data.length > 3
                && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8) {
            return "image/jpeg";
        }
        if (data != null && data.length > 8 && (data[1] & 0xFF) == 0x50
                && (data[2] & 0xFF) == 0x4E && (data[3] & 0xFF) == 0x47) {
            return "image/png";
        }
        return "image/jpeg";
    }
}
