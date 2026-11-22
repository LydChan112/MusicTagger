import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Verifies that lyrics go into the tag itself (USLT) and no sidecar file is involved. */
public class LyricsEmbedTest {

    public static void main(String[] args) throws Exception {
        Logger.getLogger("org.jaudiotagger").setLevel(Level.OFF);
        File mp3 = new File(args[0]);

        String lrc = "[00:00.00]晴天\n[00:02.25]词：周杰伦\n[00:04.50]曲：周杰伦";
        AudioFile af = AudioFileIO.read(mp3);
        Tag tag = af.getTagOrCreateAndSetDefault();
        tag.setField(FieldKey.TITLE, "晴天");
        tag.setField(FieldKey.ARTIST, "周杰伦");
        tag.setField(FieldKey.LYRICS, lrc);
        af.commit();

        AudioFile back = AudioFileIO.read(mp3);
        String read = back.getTag().getFirst(FieldKey.LYRICS);
        System.out.println("lyrics length=" + (read == null ? 0 : read.length()));
        System.out.println("first line=" + (read == null ? "" : read.split("\n")[0]));
        System.out.println("bytes on disk=" + mp3.length());
        System.out.println("RESULT=" + (read != null && read.contains("晴天") && read.contains("[00:02.25]") ? "PASS" : "FAIL"));
    }
}
