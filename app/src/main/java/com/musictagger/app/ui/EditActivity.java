package com.musictagger.app.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.musictagger.app.R;
import com.musictagger.app.databinding.ActivityEditBinding;
import com.musictagger.app.databinding.DialogLyricsBinding;
import com.musictagger.app.databinding.ItemCandidateBinding;
import com.musictagger.app.model.Lyrics;
import com.musictagger.app.model.Song;
import com.musictagger.app.model.Track;
import com.musictagger.app.provider.ProviderHub;
import com.musictagger.app.storage.SafStore;
import com.musictagger.app.tag.TagIO;
import com.musictagger.app.util.Diag;
import com.musictagger.app.util.ArtistUtil;
import com.musictagger.app.util.FilenameParser;
import com.musictagger.app.util.FormatSniffer;
import com.musictagger.app.util.Settings;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EditActivity extends AppCompatActivity {

    public static final String EXTRA_URI = "uri";
    public static final String EXTRA_NAME = "name";
    public static final String EXTRA_TREE = "tree";

    private ActivityEditBinding b;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private Uri fileUri;
    private Uri treeUri;
    private String displayName = "";
    private DocumentFile doc;
    private File cache;
    private Song song = new Song();
    private byte[] artwork;
    private String lyricsText = "";
    private String detectedFormat = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityEditBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        setSupportActionBar(b.toolbar);
        b.toolbar.setNavigationOnClickListener(v -> finish());

        fileUri = Uri.parse(getIntent().getStringExtra(EXTRA_URI));
        displayName = getIntent().getStringExtra(EXTRA_NAME);
        if (displayName == null) {
            displayName = "audio";
        }
        String tree = getIntent().getStringExtra(EXTRA_TREE);
        if (tree != null) {
            treeUri = Uri.parse(tree);
        }

        b.btnMatch.setOnClickListener(v -> autoMatch());
        b.btnSearch.setOnClickListener(v -> openSearchDialog());
        b.btnSave.setOnClickListener(v -> save());
        b.btnLyrics.setOnClickListener(v -> showLyricsDialog());
        b.btnLyricSearch.setOnClickListener(v -> searchLyrics());
        b.cover.setOnClickListener(v -> openSearchDialog());

        load();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_edit, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_parse_name) {
            fillFromFilename();
            return true;
        }
        if (item.getItemId() == R.id.action_self_test) {
            selfTest();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void load() {
        setBusy(true);
        io.execute(() -> {
            final Song s = new Song();
            s.uri = fileUri;
            s.displayName = displayName;
            byte[] art = null;
            String error = null;
            try {
                doc = DocumentFile.fromSingleUri(this, fileUri);
                if (doc == null) {
                    throw new IllegalStateException("无法打开文件");
                }
                s.size = doc.length();
                cache = SafStore.copyToCache(this, doc);
                s.format = FormatSniffer.detect(cache);
                TagIO.read(cache, s);
                art = TagIO.readArtwork(cache);
            } catch (Exception e) {
                error = e.getMessage() == null ? e.toString() : e.getMessage();
                if (cache != null) {
                    error = "检测到实际内容：" + FormatSniffer.display(FormatSniffer.detect(cache))
                            + "（文件扩展名 " + SafStore.extensionOf(displayName) + "）\n" + error;
                }
            }
            final Song loaded = s;
            final byte[] artFinal = art;
            final String errorFinal = error;
            main.post(() -> {
                setBusy(false);
                song = loaded;
                artwork = artFinal;
                lyricsText = loaded.lyrics == null ? "" : loaded.lyrics;
                bind();
                if (errorFinal != null) {
                    showError("读取标签失败", errorFinal);
                }
            });
        });
    }

    private void bind() {
        b.inputTitle.setText(song.title);
        b.inputArtist.setText(song.artist);
        b.inputAlbum.setText(song.album);
        b.inputAlbumArtist.setText(song.albumArtist);
        b.inputYear.setText(song.year);
        b.inputTrack.setText(song.track);
        b.inputGenre.setText(song.genre);
        b.inputComment.setText(song.comment);
        b.headerTitle.setText(song.effectiveTitle());
        b.headerFile.setText(displayName);

        StringBuilder meta = new StringBuilder();
        if (!song.format.isEmpty()) {
            meta.append(song.format.toUpperCase(Locale.ROOT));
        }
        if (song.durationSec > 0) {
            meta.append(meta.length() > 0 ? "  ·  " : "")
                    .append(String.format(Locale.ROOT, "%d:%02d", song.durationSec / 60, song.durationSec % 60));
        }
        if (song.bitrate > 0) {
            meta.append(meta.length() > 0 ? "  ·  " : "").append(song.bitrate).append(" kbps");
        }
        if (song.sampleRate > 0) {
            meta.append(meta.length() > 0 ? "  ·  " : "")
                    .append(String.format(Locale.ROOT, "%.1f kHz", song.sampleRate / 1000f));
        }
        if (song.size > 0) {
            meta.append(meta.length() > 0 ? "  ·  " : "")
                    .append(Formatter.formatShortFileSize(this, song.size));
        }
        b.headerMeta.setText(meta.toString());

        if (artwork != null && artwork.length > 0) {
            showCover(artwork);
        } else {
            b.cover.setImageResource(R.drawable.ic_album);
        }
        updateLyricsPreview();
    }

    private void updateLyricsPreview() {
        if (lyricsText == null || lyricsText.trim().isEmpty()) {
            b.lyricsPreview.setText("尚未获取歌词");
            return;
        }
        String[] lines = lyricsText.split("\n");
        StringBuilder sb = new StringBuilder();
        int taken = 0;
        for (String line : lines) {
            String t = line.replaceAll("\\[\\d{1,2}:\\d{1,2}([.:]\\d{1,3})?]", "").trim();
            if (t.isEmpty()) {
                continue;
            }
            if (taken > 0) {
                sb.append('\n');
            }
            sb.append(t);
            if (++taken >= 4) {
                break;
            }
        }
        b.lyricsPreview.setText(sb.length() == 0 ? "尚未获取歌词" : sb.toString());
    }

    private void showCover(byte[] data) {
        try {
            Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);
            if (bmp != null) {
                b.cover.setImageBitmap(bmp);
            }
        } catch (Exception ignored) {
        }
    }

    private void setStatus(String text) {
        runOnUiThread(() -> b.toolbar.setSubtitle(text));
    }

    private List<FilenameParser.Guess> buildGuesses() {
        List<FilenameParser.Guess> guesses = new ArrayList<>();
        String title = text(b.inputTitle);
        String artist = text(b.inputArtist);
        if (!title.isEmpty() || !artist.isEmpty()) {
            guesses.add(new FilenameParser.Guess(title, artist));
        }
        guesses.addAll(FilenameParser.variants(displayName));
        if (guesses.isEmpty()) {
            guesses.add(new FilenameParser.Guess(Song.stripExtension(displayName), ""));
        }
        return guesses;
    }

    private String buildQuery() {
        String title = text(b.inputTitle);
        String artist = text(b.inputArtist);
        if (!title.isEmpty() || !artist.isEmpty()) {
            return ProviderHub.queryFor(title, artist);
        }
        FilenameParser.Guess parsed = FilenameParser.parse(displayName);
        return parsed.query.isEmpty() ? Song.stripExtension(displayName) : parsed.query;
    }

    private void fillFromFilename() {
        FilenameParser.Guess parsed = FilenameParser.parse(displayName);
        if (parsed.isEmpty()) {
            snack("无法从文件名识别出标题");
            return;
        }
        b.inputTitle.setText(parsed.title);
        if (!parsed.artist.isEmpty()) {
            b.inputArtist.setText(parsed.artist);
        }
        snack(parsed.artist.isEmpty()
                ? "已填入标题：" + parsed.title
                : "已填入：" + parsed.title + " - " + parsed.artist);
    }

    private void autoMatch() {
        final List<FilenameParser.Guess> guesses = buildGuesses();
        final String query = buildQuery();
        setBusy(true);
        snack("正在按文件名搜索并匹配…");
        setStatus("搜索中…");
        io.execute(() -> {
            List<Track> hits = ProviderHub.searchFor(query, guesses);
            setStatus("下载封面与歌词…");
            Track best = hits.isEmpty() ? null : hits.get(0);
            if (best == null || best.score < 0.30f) {
                main.post(() -> {
                    setBusy(false);
                    setStatus(null);
                    snack("没有找到可靠的匹配，试试「搜索候选」手动选择");
                });
                return;
            }
            byte[] art = ProviderHub.coverWithDeadline(best, best.title, best.artist, 12000);
            Lyrics ly = ProviderHub.lyricsWithDeadline(best, best.title, best.artist, best.durationSec, 12000);
            main.post(() -> {
                applyTrack(best);
                if (art != null && art.length > 0) {
                    artwork = art;
                    showCover(art);
                }
                if (ly != null && !ly.isEmpty()) {
                    lyricsText = ly.asText();
                    updateLyricsPreview();
                }
                setBusy(false);
                setStatus(null);
                save();
            });
        });
    }

    private void openSearchDialog() {
        final List<FilenameParser.Guess> guesses = buildGuesses();
        final String query = buildQuery();
        setBusy(true);
        io.execute(() -> {
            List<Track> hits = ProviderHub.searchFor(query, guesses);
            main.post(() -> {
                setBusy(false);
                if (hits.isEmpty()) {
                    snack("没有搜索到候选");
                } else {
                    showCandidateDialog(hits);
                }
            });
        });
    }

    private void showCandidateDialog(List<Track> hits) {
        CandidatePickerDialog.show(this, "选择匹配结果", hits, -1, (index, track) -> applyCandidate(track));
    }

    private void applyCandidate(Track track) {
        applyTrack(track);
        final Track t = track;
        setBusy(true);
        setStatus("下载封面与歌词…");
        io.execute(() -> {
            byte[] art = ProviderHub.coverWithDeadline(t, t.title, t.artist, 12000);
            Lyrics ly = ProviderHub.lyricsWithDeadline(t, t.title, t.artist, t.durationSec, 12000);
            main.post(() -> {
                if (art != null && art.length > 0) {
                    artwork = art;
                    showCover(art);
                }
                if (ly != null && !ly.isEmpty()) {
                    lyricsText = ly.asText();
                    updateLyricsPreview();
                }
                setBusy(false);
                setStatus(null);
                snack("已填入信息，点「保存」写入文件");
            });
        });
    }

    private void applyTrack(Track t) {
        b.inputTitle.setText(t.title);
        if (t.artist != null && !t.artist.isEmpty()) {
            b.inputArtist.setText(ArtistUtil.normalize(t.artist, Settings.artistSeparator(this)));
        }
        if (t.album != null && !t.album.isEmpty()) {
            b.inputAlbum.setText(t.album);
        }
        b.headerTitle.setText(t.title);
    }

    private void save() {
        if (cache == null) {
            snack("文件不可写：没有拿到缓存副本");
            return;
        }
        final Song s = collect();
        setBusy(true);
        setStatus("写入中…");
        io.execute(() -> {
            String step = "写入标签";
            String error = null;
            try {
                TagIO.write(cache, s, artwork);
                step = "写回原文件";
                SafStore.copyFromCache(this, cache, doc, treeUri);
            } catch (Throwable e) {
                Diag.error(step, e);
                error = Diag.describe(step, e);
            }
            final String errorFinal = error;
            main.post(() -> {
                setBusy(false);
                setStatus(null);
                if (errorFinal == null) {
                    song = s;
                    snack("已写入标签");
                } else {
                    showError("写入失败", errorFinal);
                }
            });
        });
    }

    /** Writes a probe copy in the app cache: separates "library broken" from "no write permission". */
    private void selfTest() {
        final Song snapshot = collect();
        setBusy(true);
        io.execute(() -> {
            StringBuilder sb = new StringBuilder();
            File probe = null;
            try {
                String ext = cache == null ? SafStore.extensionOf(displayName)
                        : SafStore.extensionOf(cache.getName());
                probe = new File(getCacheDir(), "selftest_" + System.currentTimeMillis() + "." + ext);
                copyFile(cache, probe);
                sb.append("① 缓存副本：正常（内容 ").append(FormatSniffer.display(FormatSniffer.detect(probe)))
                        .append("）\n");
                TagIO.write(probe, snapshot, artwork);
                sb.append("② 写入标签：成功\n");
                Song back = new Song();
                TagIO.read(probe, back);
                sb.append("③ 读回：标题=").append(back.title)
                        .append(" 艺术家=").append(back.artist).append('\n');
                sb.append("\n结论：标签库和该格式没问题，失败在写回原文件（SAF 权限）。");
            } catch (Throwable e) {
                Diag.error("写入自检", e);
                sb.append("失败：").append(Diag.describe("自检", e));
                sb.append("\n\n结论：标签库处理不了这个文件。");
            } finally {
                if (probe != null) {
                    //noinspection ResultOfMethodCallIgnored
                    probe.delete();
                }
            }
            final String text = sb.toString();
            main.post(() -> {
                setBusy(false);
                showError("写入自检", text);
            });
        });
    }

    private void copyFile(File from, File to) throws Exception {
        if (from == null) {
            throw new IllegalStateException("没有缓存副本");
        }
        try (java.io.InputStream in = new java.io.FileInputStream(from);
             java.io.OutputStream out = new java.io.FileOutputStream(to)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        }
    }

    private void showError(String title, String detail) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(detail.length() > 4000 ? detail.substring(0, 4000) + "…" : detail)
                .setPositiveButton("复制详情", (d, w) -> {
                    Diag.copy(this, detail);
                    snack("已复制到剪贴板");
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private Song collect() {
        Song s = new Song();
        s.uri = fileUri;
        s.displayName = displayName;
        s.title = text(b.inputTitle);
        s.artist = ArtistUtil.normalize(text(b.inputArtist), Settings.artistSeparator(this));
        s.album = text(b.inputAlbum);
        s.albumArtist = text(b.inputAlbumArtist);
        s.year = text(b.inputYear);
        s.track = text(b.inputTrack);
        s.genre = text(b.inputGenre);
        s.comment = text(b.inputComment);
        s.lyrics = lyricsText;
        s.size = song.size;
        return s;
    }

    private void searchLyrics() {
        FilenameParser.Guess parsed = FilenameParser.parse(displayName);
        String title = text(b.inputTitle);
        String artist = text(b.inputArtist);
        if (title.isEmpty()) {
            title = parsed.title.isEmpty() ? Song.stripExtension(displayName) : parsed.title;
        }
        if (artist.isEmpty()) {
            artist = parsed.artist;
        }
        final String qTitle = title;
        final String qArtist = artist;
        setBusy(true);
        snack("正在搜索歌词…");
        setStatus("搜索歌词…");
        io.execute(() -> {
            final Lyrics ly = ProviderHub.lyricsWithDeadline(null, qTitle, qArtist, song.durationSec, 12000);
            main.post(() -> {
                setBusy(false);
                setStatus(null);
                if (ly == null || ly.isEmpty()) {
                    snack("没有找到歌词，可手动编辑");
                    return;
                }
                lyricsText = ly.asText();
                updateLyricsPreview();
                snack("已获取歌词（" + ly.source + "）");
            });
        });
    }

    private void showLyricsDialog() {
        DialogLyricsBinding lb = DialogLyricsBinding.inflate(getLayoutInflater());
        lb.inputLyrics.setText(lyricsText == null ? "" : lyricsText);
        new MaterialAlertDialogBuilder(this)
                .setTitle("歌词（LRC）")
                .setView(lb.getRoot())
                .setPositiveButton("确定", (d, w) -> {
                    lyricsText = lb.inputLyrics.getText().toString();
                    updateLyricsPreview();
                })
                .setNeutralButton("清空", (d, w) -> {
                    lyricsText = "";
                    updateLyricsPreview();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String text(EditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    private void setBusy(boolean busy) {
        b.progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        b.btnMatch.setEnabled(!busy);
        b.btnSearch.setEnabled(!busy);
        b.btnSave.setEnabled(!busy);
    }

    private void snack(String msg) {
        Snackbar.make(b.getRoot(), msg, Snackbar.LENGTH_SHORT).show();
    }
}
