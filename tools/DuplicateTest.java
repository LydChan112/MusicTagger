import com.musictagger.app.model.Track;
import com.musictagger.app.util.DuplicateUtil;

public class DuplicateTest {
    private static int pass, fail;

    public static void main(String[] args) {
        check("same title+artist", "晴天", "周杰伦", "晴天", "周杰伦", true);
        check("live suffix ignored", "晴天 (Live)", "周杰伦", "晴天", "周杰伦", true);
        check("artist separator noise", "晴天", "周杰伦/费玉清", "晴天", "周杰伦", true);
        check("missing existing artist", "晴天", "", "晴天", "周杰伦", false);
        check("different song", "红豆", "王菲", "晴天", "周杰伦", false);
        check("same title other artist", "晴天", "孙燕姿", "晴天", "周杰伦", false);
        check("empty title", "", "周杰伦", "晴天", "周杰伦", false);
        check("null track", "晴天", "周杰伦", null, null, false);
        System.out.println("PASS=" + pass + " FAIL=" + fail);
        if (fail > 0) System.exit(1);
    }

    private static void check(String name, String et, String ea, String tt, String ta, boolean expect) {
        Track t = tt == null ? null : new Track("test", "1", tt, ta, "");
        boolean got = DuplicateUtil.sameSong(et, ea, t);
        boolean ok = got == expect;
        if (ok) pass++; else fail++;
        System.out.println((ok ? "PASS " : "FAIL ") + name + " -> " + got + " (expected " + expect + ")");
    }
}
