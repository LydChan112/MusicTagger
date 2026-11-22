package com.musictagger.app.service;

import android.content.Context;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import com.musictagger.app.model.Lyrics;
import com.musictagger.app.model.Song;
import com.musictagger.app.model.Track;
import com.musictagger.app.provider.ProviderHub;
import com.musictagger.app.storage.SafStore;
import com.musictagger.app.tag.TagIO;
import com.musictagger.app.util.Diag;
import com.musictagger.app.util.FilenameParser;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Match and write pipeline, split so the UI can review results before writing. */
public final class AutoTagger {

    public interface Progress {
        void onStep(String step);

        boolean isCancelled();
    }

    public static class MatchOutcome {
        public final List<Track> candidates;
        public final String existingTitle;
        public final String existingArtist;
        public final String error;

        MatchOutcome(List<Track> candidates, String existingTitle, String existingArtist, String error) {
            this.candidates = candidates;
            this.existingTitle = existingTitle;
            this.existingArtist = existingArtist;
            this.error = error;
        }
    }

    public static class Result {
        public boolean ok;
        public boolean cancelled;
        public String message = "";
        public String title = "";
        public String artist = "";
    }

    private AutoTagger() {
    }

    /** Copies the file, reads its current tags and searches; the copy is deleted afterwards. */
    public static MatchOutcome match(Context ctx, DocumentFile doc) {
        File cache = null;
        try {
            cache = SafStore.copyToCache(ctx, doc);
            Song song = new Song();
            song.displayName = doc.getName() == null ? "audio" : doc.getName();
            try {
                TagIO.read(cache, song);
            } catch (Exception ignored) {
                // Unreadable tags are fine: matching falls back to the file name.
            }
            String title = song.title == null || song.title.trim().isEmpty()
                    ? Song.stripExtension(song.displayName) : song.title.trim();
            String artist = song.artist == null ? "" : song.artist.trim();

            List<FilenameParser.Guess> guesses = new ArrayList<>(FilenameParser.variants(song.displayName));
            if (!title.isEmpty() || !artist.isEmpty()) {
                guesses.add(new FilenameParser.Guess(title, artist));
            }
            if (guesses.isEmpty()) {
                return new MatchOutcome(Collections.emptyList(), title, artist, "文件名无法识别");
            }
            FilenameParser.Guess fileGuess = FilenameParser.parse(song.displayName);
            String query = fileGuess.query.isEmpty() ? ProviderHub.queryFor(title, artist) : fileGuess.query;
            List<Track> hits = ProviderHub.searchFor(query, guesses, song.durationSec,
                    com.musictagger.app.util.Settings.durationTolerance(ctx));
            return new MatchOutcome(hits, title, artist, null);
        } catch (Throwable e) {
            Diag.error("搜索", e);
            return new MatchOutcome(Collections.emptyList(), "", "", Diag.describe("搜索", e));
        } finally {
            if (cache != null) {
                //noinspection ResultOfMethodCallIgnored
                cache.delete();
            }
        }
    }

    /** Writes the chosen track's tags, cover and lyrics into the document. */
    public static Result write(Context ctx, Uri treeUri, DocumentFile doc, Track chosen, Progress progress) {
        Result result = new Result();
        File cache = null;
        String step = "复制文件";
        try {
            report(progress, step);
            cache = SafStore.copyToCache(ctx, doc);

            step = "读取标签";
            report(progress, step);
            Song song = new Song();
            song.displayName = doc.getName() == null ? "audio" : doc.getName();
            try {
                TagIO.read(cache, song);
            } catch (Exception ignored) {
            }

            song.title = chosen.title;
            if (chosen.artist != null && !chosen.artist.isEmpty()) {
                song.artist = chosen.artist;
            }
            if (chosen.album != null && !chosen.album.isEmpty()) {
                song.album = chosen.album;
            }
            if (cancelled(progress)) {
                result.cancelled = true;
                result.message = "已取消";
                return result;
            }

            step = "下载封面与歌词";
            report(progress, step);
            Future<byte[]> coverFuture = ProviderHub.submitCover(chosen, chosen.title, chosen.artist);
            Future<Lyrics> lyricsFuture = ProviderHub.submitLyrics(chosen, chosen.title, chosen.artist,
                    chosen.durationSec);
            final long deadline = System.currentTimeMillis() + 8000;
            byte[] cover = awaitCover(coverFuture, Math.max(1, deadline - System.currentTimeMillis()));
            Lyrics lyrics = awaitLyrics(lyricsFuture, Math.max(1, deadline - System.currentTimeMillis()));
            if (lyrics != null && !lyrics.isEmpty()) {
                song.lyrics = lyrics.asText();
            }
            if (cancelled(progress)) {
                result.cancelled = true;
                result.message = "已取消";
                return result;
            }

            step = "写入标签";
            report(progress, step);
            TagIO.write(cache, song, cover);

            step = "写回原文件";
            report(progress, step);
            SafStore.copyFromCache(ctx, cache, doc, treeUri);

            result.ok = true;
            result.title = song.title;
            result.artist = song.artist;
            result.message = "已写入";
            return result;
        } catch (Throwable e) {
            Diag.error(step, e);
            result.message = Diag.describe(step, e);
            return result;
        } finally {
            if (cache != null) {
                //noinspection ResultOfMethodCallIgnored
                cache.delete();
            }
        }
    }

    /** One-shot convenience: match the best hit and write it. */
    public static Result run(Context ctx, Uri treeUri, DocumentFile doc, Progress progress) {
        MatchOutcome outcome = match(ctx, doc);
        if (outcome.error != null) {
            Result r = new Result();
            r.message = outcome.error;
            return r;
        }
        Track best = outcome.candidates.isEmpty() ? null : outcome.candidates.get(0);
        if (best == null || best.score < 0.30f) {
            Result r = new Result();
            r.message = "未找到可靠匹配";
            return r;
        }
        return write(ctx, treeUri, doc, best, progress);
    }

    private static void report(Progress progress, String step) {
        if (progress != null) {
            progress.onStep(step);
        }
    }

    private static boolean cancelled(Progress progress) {
        return progress != null && progress.isCancelled();
    }

    private static byte[] awaitCover(Future<byte[]> future, long millis) {
        try {
            return future.get(millis, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            future.cancel(true);
            return null;
        }
    }

    private static Lyrics awaitLyrics(Future<Lyrics> future, long millis) {
        try {
            return future.get(millis, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            future.cancel(true);
            return null;
        }
    }
}
