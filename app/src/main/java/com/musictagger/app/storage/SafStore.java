package com.musictagger.app.storage;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.DocumentsContract;

import androidx.documentfile.provider.DocumentFile;

import com.musictagger.app.util.FormatSniffer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** SAF helpers: enumerate audio files in a tree, copy in and out, write back. */
public final class SafStore {

    public static final String PREFS = "musictagger";
    public static final String KEY_TREE = "tree_uri";

    private static final Set<String> AUDIO_EXT = new HashSet<>(Arrays.asList(
            "mp3", "flac", "m4a", "mp4", "aac", "ogg", "oga", "opus", "wav",
            "wma", "ape", "dsf", "dff", "tta", "wv", "aiff", "aif", "mpc"));

    private SafStore() {
    }

    public static String extensionOf(String name) {
        int i = name.lastIndexOf('.');
        return i > 0 && i < name.length() - 1 ? name.substring(i + 1).toLowerCase(Locale.ROOT) : "";
    }

    public static boolean isAudio(DocumentFile f) {
        return f != null && f.isFile() && AUDIO_EXT.contains(extensionOf(f.getName() == null ? "" : f.getName()));
    }

    public static void saveTree(Context ctx, Uri tree) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        p.edit().putString(KEY_TREE, tree.toString()).apply();
    }

    public static Uri savedTree(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String s = p.getString(KEY_TREE, null);
        return s == null ? null : Uri.parse(s);
    }

    public static DocumentFile tree(Context ctx, Uri uri) {
        return DocumentFile.fromTreeUri(ctx, uri);
    }

    /** Depth-first walk, capped so a huge tree cannot lock the UI thread. */
    public static void scan(DocumentFile dir, List<DocumentFile> out, int depth) {
        if (dir == null || depth > 12 || out.size() > 5000) {
            return;
        }
        DocumentFile[] children = dir.listFiles();
        for (DocumentFile f : children) {
            if (f.isDirectory()) {
                scan(f, out, depth + 1);
            } else if (isAudio(f)) {
                out.add(f);
            }
        }
    }

    public static File copyToCache(Context ctx, DocumentFile doc) throws IOException {
        String name = doc.getName() == null ? "audio" : doc.getName();
        File tmp = new File(ctx.getCacheDir(), "src_" + Math.abs(doc.getUri().hashCode()) + ".bin");
        try (InputStream in = ctx.getContentResolver().openInputStream(doc.getUri());
             OutputStream os = new FileOutputStream(tmp)) {
            if (in == null) {
                throw new IOException("无法读取文件");
            }
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                os.write(buf, 0, n);
            }
        }
        // Trust the content, not the extension: renamed FLAC/APE files are common.
        String detected = FormatSniffer.detect(tmp);
        String ext = "unknown".equals(detected) ? extensionOf(name) : detected;
        if (ext.isEmpty()) {
            ext = "bin";
        }
        File out = new File(ctx.getCacheDir(), "edit_" + Math.abs(doc.getUri().hashCode()) + "." + ext);
        if (out.exists() && !out.delete()) {
            throw new IOException("无法写入缓存");
        }
        if (!tmp.renameTo(out)) {
            try (InputStream in = new FileInputStream(tmp);
                 OutputStream os = new FileOutputStream(out)) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) {
                    os.write(buf, 0, n);
                }
            } finally {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
        }
        return out;
    }

    public static void copyFromCache(Context ctx, File src, DocumentFile target) throws IOException {
        copyFromCache(ctx, src, target, null);
    }

    /**
     * Write the edited cache copy back over the original document.
     * If the single-document URI refuses the write, retry through the tree grant,
     * which is the same file addressed with its parent context.
     */
    public static void copyFromCache(Context ctx, File src, DocumentFile target, Uri treeUri) throws IOException {
        Exception first = null;
        try {
            writeInto(ctx, src, target.getUri());
            return;
        } catch (Exception e) {
            first = e;
        }
        if (treeUri != null) {
            try {
                String docId = DocumentsContract.getDocumentId(target.getUri());
                Uri alt = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId);
                if (!alt.equals(target.getUri())) {
                    writeInto(ctx, src, alt);
                    return;
                }
            } catch (Exception ignored) {
            }
        }
        throw new IOException("无法写回原文件"
                + (first == null || first.getMessage() == null ? "" : "：" + first.getMessage()), first);
    }

    private static void writeInto(Context ctx, File src, Uri uri) throws IOException {
        OutputStream os = openOut(ctx, uri);
        try (InputStream in = new java.io.FileInputStream(src)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                os.write(buf, 0, n);
            }
        } finally {
            os.close();
        }
    }

    /** Some providers reject "wt" but accept "rwt"; try both before failing. */
    private static OutputStream openOut(Context ctx, Uri uri) throws IOException {
        for (String mode : new String[]{"wt", "rwt"}) {
            try {
                OutputStream os = ctx.getContentResolver().openOutputStream(uri, mode);
                if (os != null) {
                    return os;
                }
            } catch (Exception ignored) {
            }
        }
        throw new IOException("无法写入文件（没有写入权限）");
    }

    public static String documentId(DocumentFile f) {
        try {
            return DocumentsContract.getDocumentId(f.getUri());
        } catch (Exception e) {
            return f.getUri().toString();
        }
    }

    // ---------- fast scanning ----------

    public interface ScanListener {
        void onFile(DocumentFile file);

        void onDone(int total);
    }

    private static final int SCAN_THREADS = 4;
    private static final int MAX_FILES = 20000;

    /**
     * Walks the tree with raw content queries instead of DocumentFile.listFiles(),
     * four directories at a time. DocumentFile asks the provider once per entry and
     * once more per property, which is what made large libraries slow.
     */
    public static void scanTree(Context ctx, Uri treeUri, ScanListener listener) {
        ExecutorService pool = Executors.newFixedThreadPool(SCAN_THREADS);
        AtomicInteger pending = new AtomicInteger(1);
        AtomicInteger count = new AtomicInteger();
        String rootId;
        try {
            rootId = DocumentsContract.getTreeDocumentId(treeUri);
        } catch (Exception e) {
            pool.shutdown();
            listener.onDone(0);
            return;
        }
        scanDir(ctx, treeUri, rootId, listener, pool, pending, count);
    }

    private static void scanDir(Context ctx, Uri treeUri, String dirId, ScanListener listener,
                                ExecutorService pool, AtomicInteger pending, AtomicInteger count) {
        pool.execute(() -> {
            try {
                String[] projection = {
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE};
                Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dirId);
                try (android.database.Cursor cursor = ctx.getContentResolver()
                        .query(children, projection, null, null, null)) {
                    if (cursor != null) {
                        while (cursor.moveToNext() && count.get() < MAX_FILES) {
                            String id = cursor.getString(0);
                            String name = cursor.getString(1);
                            String mime = cursor.getString(2);
                            if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                                pending.incrementAndGet();
                                scanDir(ctx, treeUri, id, listener, pool, pending, count);
                            } else if (isAudioName(name)) {
                                Uri docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, id);
                                DocumentFile file = DocumentFile.fromSingleUri(ctx, docUri);
                                if (file != null) {
                                    listener.onFile(file);
                                    count.incrementAndGet();
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
                // Unreadable directory: skip it and keep the rest.
            } finally {
                if (pending.decrementAndGet() == 0) {
                    pool.shutdown();
                    listener.onDone(count.get());
                }
            }
        });
    }

    private static boolean isAudioName(String name) {
        return name != null && AUDIO_EXT.contains(extensionOf(name));
    }

    // ---------- scan cache ----------

    private static File cacheFile(Context ctx, Uri treeUri) {
        return new File(ctx.getCacheDir(), "scan_" + Math.abs(treeUri.toString().hashCode()) + ".json");
    }

    /** Previously scanned entries, rebuilt from document ids so opening the app is instant. */
    public static List<DocumentFile> readCache(Context ctx, Uri treeUri) {
        List<DocumentFile> out = new ArrayList<>();
        File file = cacheFile(ctx, treeUri);
        if (!file.exists()) {
            return out;
        }
        try {
            String text = new String(java.nio.file.Files.readAllBytes(file.toPath()), "UTF-8");
            org.json.JSONArray array = new org.json.JSONArray(text);
            for (int i = 0; i < array.length(); i++) {
                org.json.JSONObject item = array.getJSONObject(i);
                Uri docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri,
                        item.getString("id"));
                DocumentFile df = DocumentFile.fromSingleUri(ctx, docUri);
                if (df != null) {
                    out.add(df);
                }
            }
        } catch (Throwable ignored) {
            out.clear();
        }
        return out;
    }

    public static void writeCache(Context ctx, Uri treeUri, List<DocumentFile> files) {
        try {
            org.json.JSONArray array = new org.json.JSONArray();
            for (DocumentFile f : files) {
                org.json.JSONObject item = new org.json.JSONObject();
                item.put("id", documentId(f));
                item.put("name", f.getName() == null ? "" : f.getName());
                array.put(item);
            }
            File file = cacheFile(ctx, treeUri);
            try (java.io.FileOutputStream os = new java.io.FileOutputStream(file)) {
                os.write(array.toString().getBytes("UTF-8"));
            }
        } catch (Throwable ignored) {
        }
    }

    public static void clearCache(Context ctx, Uri treeUri) {
        //noinspection ResultOfMethodCallIgnored
        cacheFile(ctx, treeUri).delete();
    }
}
