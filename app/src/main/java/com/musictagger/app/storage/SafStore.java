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

    public static List<DocumentFile> listAll(Context ctx, Uri treeUri) {
        List<DocumentFile> out = new ArrayList<>();
        DocumentFile root = tree(ctx, treeUri);
        if (root != null) {
            scan(root, out, 0);
        }
        return out;
    }
}
