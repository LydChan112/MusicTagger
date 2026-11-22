import com.musictagger.app.util.FilenameParser;

import java.util.List;

public class FilenameParserTest {

    private static int pass;
    private static int fail;

    public static void main(String[] args) {
        // name, expected title, expected artist ("" when there is no artist part)
        check("01. 周杰伦 - 晴天.mp3", "晴天", "周杰伦");
        check("周杰伦 - 晴天.flac", "晴天", "周杰伦");
        check("周杰伦－晴天.mp3", "晴天", "周杰伦");
        check("01 - 周杰伦 - 晴天.mp3", "晴天", "周杰伦");
        check("01 晴天.mp3", "晴天", "");
        check("晴天.mp3", "晴天", "");
        check("周杰伦《晴天》.mp3", "晴天", "周杰伦");
        check("晴天(周杰伦).mp3", "晴天", "周杰伦");
        check("晴天[FLAC].flac", "晴天", "");
        check("晴天_周杰伦.mp3", "周杰伦", "晴天");
        check("Jay-Z - 99 Problems.mp3", "99 Problems", "Jay-Z");
        check("99 Problems.mp3", "99 Problems", "");
        check("1-01 周杰伦 - 晴天.mp3", "晴天", "周杰伦");
        check("小芸音乐@1401530822@Push My Luck@The Chainsmokers.mp3", "Push My Luck", "The Chainsmokers");
        check("小芸音乐@1401530822@晴天@周杰伦.mp3", "晴天", "周杰伦");
        check("小芸音乐@1401530822@晴天.mp3", "小芸音乐@1401530822@晴天", "");
        check("A@B@C@D@E.mp3", "A@B@C@D@E", "");

        // title - artist order must still be reachable through the swapped guess
        List<FilenameParser.Guess> variants = FilenameParser.variants("晴天 - 周杰伦.mp3");
        boolean hasSwap = false;
        for (FilenameParser.Guess g : variants) {
            if (g.title.equals("周杰伦") && g.artist.equals("晴天")) {
                hasSwap = true;
            }
        }
        report(variants.size() >= 2 && hasSwap, "variants keep swapped reading");
        System.out.println("\nPASS=" + pass + " FAIL=" + fail);
        if (fail > 0) {
            System.exit(1);
        }
    }

    private static void check(String name, String title, String artist) {
        FilenameParser.Guess g = FilenameParser.parse(name);
        boolean ok = g.title.equals(title) && g.artist.equals(artist);
        report(ok, name + " -> title=[" + g.title + "] artist=[" + g.artist + "]"
                + (ok ? "" : "  expected [" + title + "]/[" + artist + "]"));
    }

    private static void report(boolean ok, String msg) {
        if (ok) {
            pass++;
            System.out.println("PASS " + msg);
        } else {
            fail++;
            System.out.println("FAIL " + msg);
        }
    }
}
