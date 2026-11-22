package com.musictagger.app.provider;

import com.musictagger.app.model.Lyrics;
import com.musictagger.app.model.Track;
import com.musictagger.app.util.FilenameParser;
import com.musictagger.app.util.TextUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Runs the available providers and applies the fallback chain.
 * Providers are health-checked once per session; sources that do not answer are
 * skipped instead of costing a timeout on every song.
 */
public final class ProviderHub {

    private static final NetEaseProvider NETEASE = new NetEaseProvider();
    private static final KugouProvider KUGOU = new KugouProvider();
    private static final QQProvider QQ = new QQProvider();
    private static final ItunesProvider ITUNES = new ItunesProvider();
    private static final LrclibProvider LRCLIB = new LrclibProvider();

    private static final MusicProvider[] ALL = {NETEASE, KUGOU, QQ, ITUNES};

    // Provider calls must not queue behind other files' searches, otherwise the
    // per-search timeout would cancel work that never got a thread.
    private static final ExecutorService SEARCH_POOL = Executors.newCachedThreadPool();
    private static volatile ExecutorService IO_POOL = Executors.newFixedThreadPool(3);

    /** Rebuilds the download pool; called at startup and when the setting changes. */
    public static synchronized void setDownloadThreads(int threads) {
        int size = Math.max(1, Math.min(8, threads));
        if (IO_POOL instanceof java.util.concurrent.ThreadPoolExecutor) {
            int current = ((java.util.concurrent.ThreadPoolExecutor) IO_POOL).getCorePoolSize();
            if (current == size) {
                return;
            }
        }
        ExecutorService old = IO_POOL;
        IO_POOL = Executors.newFixedThreadPool(size);
        old.shutdown();
    }

    /** null = not probed yet (treated as usable). */
    private static final Map<String, Boolean> AVAILABLE = new ConcurrentHashMap<>();
    private static final Map<String, Long> LATENCY = new ConcurrentHashMap<>();

    private static final String PROBE_QUERY = "周杰伦 晴天";

    private ProviderHub() {
    }

    public static class Health {
        public final String name;
        public final boolean ok;
        public final long ms;
        public final String error;

        Health(String name, boolean ok, long ms, String error) {
            this.name = name;
            this.ok = ok;
            this.ms = ms;
            this.error = error;
        }

        public String label() {
            return ok ? name + "  ✓ " + ms + " ms" : name + "  ✗ 无响应";
        }
    }

    private interface Probe {
        boolean run() throws Exception;
    }

    private static Health check(String name, Probe probe) {
        long start = System.currentTimeMillis();
        boolean ok;
        String error = "";
        try {
            ok = probe.run();
        } catch (Throwable e) {
            ok = false;
            error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
        long ms = System.currentTimeMillis() - start;
        AVAILABLE.put(name, ok);
        LATENCY.put(name, ms);
        return new Health(name, ok, ms, error);
    }

    /** Probe every source in parallel; total wait is capped at 6 s. */
    public static List<Health> probe() {
        AVAILABLE.clear();
        Map<String, Callable<Health>> tasks = new LinkedHashMap<>();
        for (final MusicProvider p : ALL) {
            tasks.put(p.name(), () -> check(p.name(), () -> {
                List<Track> r = p.search(PROBE_QUERY);
                return r != null && !r.isEmpty();
            }));
        }
        tasks.put(LRCLIB.name(), () -> check(LRCLIB.name(), () -> {
            List<LrclibProvider.Hit> r = LRCLIB.searchRich(PROBE_QUERY);
            return r != null && !r.isEmpty();
        }));

        List<String> names = new ArrayList<>(tasks.keySet());
        List<Future<Health>> futures = new ArrayList<>();
        for (Callable<Health> task : tasks.values()) {
            futures.add(IO_POOL.submit(task));
        }
        List<Health> out = new ArrayList<>();
        long deadline = System.currentTimeMillis() + 6000;
        for (int i = 0; i < futures.size(); i++) {
            String name = names.get(i);
            long remain = deadline - System.currentTimeMillis();
            Health h = null;
            try {
                if (remain > 0) {
                    h = futures.get(i).get(remain, TimeUnit.MILLISECONDS);
                }
            } catch (Exception ignored) {
                futures.get(i).cancel(true);
            }
            if (h == null) {
                h = new Health(name, false, -1, "timeout");
                AVAILABLE.put(name, false);
            }
            out.add(h);
        }
        return out;
    }

    public static boolean anyAvailable() {
        for (Health h : snapshot()) {
            if (h.ok) {
                return true;
            }
        }
        return AVAILABLE.isEmpty();
    }

    private static List<Health> snapshot() {
        List<Health> out = new ArrayList<>();
        for (String name : AVAILABLE.keySet()) {
            out.add(new Health(name, Boolean.TRUE.equals(AVAILABLE.get(name)),
                    LATENCY.getOrDefault(name, -1L), ""));
        }
        return out;
    }

    private static boolean isAvailable(String name) {
        Boolean value = AVAILABLE.get(name);
        return value == null || value;
    }

    public static String queryFor(String title, String artist) {
        if (artist == null || artist.trim().isEmpty()) {
            return title == null ? "" : title.trim();
        }
        return (title == null ? "" : title.trim()) + " " + artist.trim();
    }

    /** Search every healthy provider, score the hits against what we already know, best first. */
    public static List<Track> search(String title, String artist) {
        List<FilenameParser.Guess> guesses = new ArrayList<>();
        if ((title != null && !title.trim().isEmpty()) || (artist != null && !artist.trim().isEmpty())) {
            guesses.add(new FilenameParser.Guess(title, artist));
        }
        return searchFor(queryFor(title, artist), guesses);
    }

    public static List<Track> searchFor(String query, List<FilenameParser.Guess> guesses) {
        final List<Track> all = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) {
            return all;
        }
        final String q = query;
        List<Callable<List<Track>>> tasks = new ArrayList<>();
        for (final MusicProvider p : ALL) {
            if (!isAvailable(p.name())) {
                continue;
            }
            tasks.add(() -> {
                try {
                    return p.search(q);
                } catch (Exception e) {
                    return new ArrayList<Track>();
                }
            });
        }
        if (tasks.isEmpty()) {
            return all;
        }
        List<Future<List<Track>>> futures = new ArrayList<>();
        try {
            futures = SEARCH_POOL.invokeAll(tasks, 8, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        for (Future<List<Track>> f : futures) {
            try {
                if (!f.isCancelled()) {
                    List<Track> hits = f.get();
                    if (hits != null) {
                        all.addAll(hits);
                    }
                }
            } catch (Exception ignored) {
                // Skip the provider that failed or timed out.
            }
        }
        for (Track t : all) {
            t.score = bestScore(guesses, t);
        }
        Collections.sort(all, new Comparator<Track>() {
            @Override
            public int compare(Track a, Track b) {
                return Float.compare(b.score, a.score);
            }
        });
        return all;
    }

    public static float bestScore(List<FilenameParser.Guess> guesses, Track track) {
        float best = 0f;
        if (guesses == null) {
            return best;
        }
        for (FilenameParser.Guess g : guesses) {
            best = Math.max(best, TextUtil.score(g.title, g.artist, track.title, track.artist));
        }
        return best;
    }

    // ---------- cover / lyrics with fallbacks ----------

    public static byte[] cover(Track best, String title, String artist) {
        // Everything written into a file goes through the 500 KB budget.
        return com.musictagger.app.util.CoverCompressor.limit(coverRaw(best, title, artist));
    }

    private static byte[] coverRaw(Track best, String title, String artist) {
        if (best != null && isAvailable(best.source)) {
            try {
                byte[] art = providerFor(best).cover(best);
                if (art != null && art.length > 0) {
                    return art;
                }
            } catch (Exception ignored) {
            }
        }
        if (isAvailable(NETEASE.name())) {
            try {
                List<Track> hits = NETEASE.search(queryFor(title, artist));
                if (hits != null && !hits.isEmpty()) {
                    byte[] art = NETEASE.cover(hits.get(0));
                    if (art != null && art.length > 0) {
                        return art;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (isAvailable(QQ.name())) {
            try {
                List<Track> hits = QQ.search(queryFor(title, artist));
                if (hits != null && !hits.isEmpty()) {
                    byte[] art = QQ.cover(hits.get(0));
                    if (art != null && art.length > 0) {
                        return art;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (isAvailable(ITUNES.name())) {
            try {
                List<Track> hits = ITUNES.search(queryFor(title, artist));
                if (hits != null && !hits.isEmpty()) {
                    byte[] art = ITUNES.cover(hits.get(0));
                    if (art != null && art.length > 0) {
                        return art;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public static Lyrics lyrics(Track best, String title, String artist, int durationSec) {
        if (best != null && isAvailable(best.source)) {
            try {
                Lyrics l = providerFor(best).lyrics(best);
                if (l != null && !l.isEmpty()) {
                    return l;
                }
            } catch (Exception ignored) {
            }
        }
        if (isAvailable(LRCLIB.name())) {
            try {
                Lyrics l = LRCLIB.fetch(title, artist, durationSec);
                if (l != null && !l.isEmpty()) {
                    return l;
                }
            } catch (Exception ignored) {
            }
            try {
                List<LrclibProvider.Hit> hits = LRCLIB.searchRich(queryFor(title, artist));
                if (hits != null && !hits.isEmpty()) {
                    LrclibProvider.Hit h = hits.get(0);
                    Lyrics l = new Lyrics();
                    l.source = LRCLIB.name();
                    l.lrc = h.synced != null && !h.synced.isEmpty() ? h.synced : h.plain;
                    if (l.lrc != null && !l.lrc.trim().isEmpty()) {
                        return l;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (isAvailable(NETEASE.name())) {
            try {
                List<Track> hits = NETEASE.search(queryFor(title, artist));
                if (hits != null && !hits.isEmpty()) {
                    Lyrics l = NETEASE.lyrics(hits.get(0));
                    if (l != null && !l.isEmpty()) {
                        return l;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (isAvailable(QQ.name())) {
            try {
                List<Track> hits = QQ.search(queryFor(title, artist));
                if (hits != null && !hits.isEmpty()) {
                    Lyrics l = QQ.lyrics(hits.get(0));
                    if (l != null && !l.isEmpty()) {
                        return l;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public static Future<byte[]> submitCover(final Track best, final String title, final String artist) {
        return IO_POOL.submit(() -> cover(best, title, artist));
    }

    /** Small preview for lists: asks the provider for its low-res variant. */
    public static Future<byte[]> submitThumbnail(final Track best) {
        return IO_POOL.submit(() -> thumbnail(best));
    }

    private static byte[] thumbnail(Track best) {
        if (best != null) {
            try {
                byte[] data = providerFor(best).thumbnail(best);
                if (data != null && data.length > 0) {
                    return data;
                }
            } catch (Exception ignored) {
            }
        }
        // Fall back to the full cover only when the source has no size variants.
        return best == null ? null : cover(best, best.title, best.artist);
    }

    public static byte[] thumbnailWithDeadline(Track best, long timeoutMs) {
        Future<byte[]> f = submitThumbnail(best);
        try {
            return f.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            f.cancel(true);
            return null;
        }
    }

    public static Future<Lyrics> submitLyrics(final Track best, final String title, final String artist,
                                              final int durationSec) {
        return IO_POOL.submit(() -> lyrics(best, title, artist, durationSec));
    }

    public static byte[] coverWithDeadline(Track best, String title, String artist, long timeoutMs) {
        Future<byte[]> f = submitCover(best, title, artist);
        try {
            return f.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            f.cancel(true);
            return null;
        }
    }

    public static Lyrics lyricsWithDeadline(Track best, String title, String artist, int durationSec,
                                            long timeoutMs) {
        Future<Lyrics> f = submitLyrics(best, title, artist, durationSec);
        try {
            return f.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            f.cancel(true);
            return null;
        }
    }

    private static MusicProvider providerFor(Track t) {
        if (t == null || t.source == null) {
            return NETEASE;
        }
        if (t.source.equals(KUGOU.name())) {
            return KUGOU;
        }
        if (t.source.equals(QQ.name())) {
            return QQ;
        }
        if (t.source.equals(ITUNES.name())) {
            return ITUNES;
        }
        return NETEASE;
    }
}
