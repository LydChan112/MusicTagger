import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;
import org.jaudiotagger.tag.images.ArtworkFactory;

import java.io.File;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Desktop sanity check for the same jaudiotagger calls TagIO makes. */
public class TagSmokeTest {

    public static void main(String[] args) throws Exception {
        Logger.getLogger("org.jaudiotagger").setLevel(Level.OFF);
        File mp3 = new File(args[0]);
        byte[] cover = Files.readAllBytes(new File(args[0]).getParentFile().toPath().resolve("cover.jpg"));

        AudioFile af = AudioFileIO.read(mp3);
        AudioHeader h = af.getAudioHeader();
        System.out.println("format=" + h.getFormat() + " bitrate=" + h.getBitRate()
                + " sampleRate=" + h.getSampleRate() + " seconds=" + h.getTrackLength());

        Tag tag = af.getTagOrCreateAndSetDefault();
        tag.setField(FieldKey.TITLE, "晴天");
        tag.setField(FieldKey.ARTIST, "周杰伦");
        tag.setField(FieldKey.ALBUM, "叶惠美");
        tag.setField(FieldKey.YEAR, "2003");
        tag.setField(FieldKey.TRACK, "3");
        tag.setField(FieldKey.LYRICS, "[00:00.00] 晴天");

        Artwork art = ArtworkFactory.getNew();
        art.setMimeType("image/jpeg");
        art.setBinaryData(cover);
        art.setWidth(1);
        art.setHeight(1);
        tag.deleteArtworkField();
        tag.setField(art);
        af.commit();

        AudioFile re = AudioFileIO.read(mp3);
        Tag rt = re.getTag();
        System.out.println("title=" + rt.getFirst(FieldKey.TITLE));
        System.out.println("artist=" + rt.getFirst(FieldKey.ARTIST));
        System.out.println("album=" + rt.getFirst(FieldKey.ALBUM));
        System.out.println("year=" + rt.getFirst(FieldKey.YEAR));
        System.out.println("track=" + rt.getFirst(FieldKey.TRACK));
        Artwork back = rt.getFirstArtwork();
        System.out.println("artwork=" + (back == null ? "none" : back.getBinaryData().length + " bytes, "
                + back.getMimeType() + ", " + back.getWidth() + "x" + back.getHeight()));
        System.out.println("RESULT=" + (back != null && "晴天".equals(rt.getFirst(FieldKey.TITLE)) ? "PASS" : "FAIL"));
    }
}
