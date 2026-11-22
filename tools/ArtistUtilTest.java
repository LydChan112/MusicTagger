import com.musictagger.app.util.ArtistUtil;

public class ArtistUtilTest {
    private static int pass, fail;

    public static void main(String[] args) {
        check("slash to ideographic comma", "周杰伦/费玉清", "、", "周杰伦、费玉清");
        check("semicolon to ampersand", "A; B", " & ", "A & B");
        check("ideographic to slash", "A、B", "/", "A/B");
        check("pipe to comma", "A|B", ", ", "A, B");
        check("single artist untouched", "周杰伦", "、", "周杰伦");
        check("empty stays empty", "", "、", "");
        check("empty separator keeps original", "A/B", "", "A/B");
        check("comma inside name untouched", "Earth, Wind & Fire", "、", "Earth, Wind & Fire");
        check("three artists", "A/B/C", " / ", "A / B / C");
        check("spaces around separator trimmed", " A / B ", "、", "A、B");
        System.out.println("PASS=" + pass + " FAIL=" + fail);
        if (fail > 0) System.exit(1);
    }

    private static void check(String name, String raw, String sep, String expect) {
        String got = ArtistUtil.normalize(raw, sep);
        boolean ok = expect.equals(got);
        if (ok) pass++; else fail++;
        System.out.println((ok ? "PASS " : "FAIL ") + name + " -> [" + got + "] expected [" + expect + "]");
    }
}
