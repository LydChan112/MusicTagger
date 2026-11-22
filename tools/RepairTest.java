import com.musictagger.app.util.FormatSniffer;
import com.musictagger.app.util.Mp3Repair;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;

import java.io.File;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RepairTest {

    public static void main(String[] args) throws Exception {
        Logger.getLogger("org.jaudiotagger").setLevel(Level.OFF);
        File broken = new File(args[0]);
        System.out.println("detected=" + FormatSniffer.detect(broken));
        System.out.println("size=" + broken.length());

        boolean failedBefore = false;
        try {
            AudioFileIO.read(broken);
        } catch (Exception e) {
            failedBefore = true;
            System.out.println("before repair: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }

        int offset = Mp3Repair.findFirstFrame(broken);
        System.out.println("first frame offset=" + offset);
        System.out.println("stripped=" + Mp3Repair.stripLeading(broken, offset));
        System.out.println("size after strip=" + broken.length());

        AudioFile af = AudioFileIO.read(broken);
        System.out.println("after repair format=" + af.getAudioHeader().getFormat()
                + " bitrate=" + af.getAudioHeader().getBitRate());

        Tag tag = af.getTagOrCreateAndSetDefault();
        tag.setField(FieldKey.TITLE, "晴天");
        tag.setField(FieldKey.ARTIST, "周杰伦");
        af.commit();

        AudioFile back = AudioFileIO.read(broken);
        System.out.println("readback title=" + back.getTag().getFirst(FieldKey.TITLE)
                + " artist=" + back.getTag().getFirst(FieldKey.ARTIST));

        // a renamed FLAC should be sniffed as flac even with .mp3 name
        File fakeFlac = new File(broken.getParentFile(), "fake.mp3");
        byte[] flac = new byte[64];
        flac[0] = 'f'; flac[1] = 'L'; flac[2] = 'a'; flac[3] = 'C';
        Files.write(fakeFlac.toPath(), flac);
        System.out.println("fake.mp3 detected=" + FormatSniffer.detect(fakeFlac));

        boolean ok = failedBefore && offset > 0 && "晴天".equals(back.getTag().getFirst(FieldKey.TITLE))
                && "flac".equals(FormatSniffer.detect(fakeFlac));
        System.out.println("RESULT=" + (ok ? "PASS" : "FAIL"));
    }
}
