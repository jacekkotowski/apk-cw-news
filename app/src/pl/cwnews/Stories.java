package pl.cwnews;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * The stories the story station can play: the nets shipped in assets/stories/ ("asset:NAME") and .txt files the
 * user added, copied to filesDir/stories/ ("file:NAME"). Parsed scripts are kept, so the menus stay quick.
 */
final class Stories {
    static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final Map<String, Script> cache = new HashMap<>();
    private static final Map<String, Long> stamp = new HashMap<>();

    private Stories() { }

    /** The preference that holds a story's position (an over index). */
    static String lineKey(String id) { return "storyLine:" + id; }

    static File dir(Context c) { return new File(c.getFilesDir(), "stories"); }

    /** The parsed story, or null if it cannot be read or holds nothing to send. */
    static Script load(Context c, String id) {
        if (!Script.validId(id)) return null;
        long when = id.startsWith("file:") ? new File(dir(c), id.substring(5)).lastModified() : 0;
        synchronized (cache) {
            Script s = cache.get(id);
            Long t = stamp.get(id);
            if (s != null && t != null && t == when) return s;
        }
        String text = read(c, id);
        if (text == null) return null;
        Script s = Script.parse(id.substring(id.indexOf(':') + 1), text);
        if (s.lines.isEmpty()) return null;
        synchronized (cache) { cache.put(id, s); stamp.put(id, when); }
        return s;
    }

    private static String read(Context c, String id) {
        try {
            InputStream in = id.startsWith("asset:") ? c.getAssets().open("stories/" + id.substring(6)) : new java.io.FileInputStream(new File(dir(c), id.substring(5)));
            try { byte[] b = readAll(in); return b == null ? null : utf8(b); } finally { in.close(); }
        } catch (Exception e) { return null; }
    }

    /** At most MAX_BYTES, or null if there is more. */
    private static byte[] readAll(InputStream in) throws java.io.IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        for (int n; (n = in.read(buf)) > 0; ) {
            out.write(buf, 0, n);
            if (out.size() > MAX_BYTES) return null;
        }
        return out.toByteArray();
    }

    /** Strict UTF-8: null if the bytes are not UTF-8 text. */
    private static String utf8(byte[] b) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(b)).toString();
        } catch (CharacterCodingException e) { return null; }
    }

    /** All stories as JSON for the page: [{"id","title","added","total","episodes":[{"t","first"}]}], shipped ones first. */
    static String listJson(Context c) {
        StringBuilder b = new StringBuilder("[");
        java.util.List<String> ids = new java.util.ArrayList<>();
        try {
            String[] a = c.getAssets().list("stories");
            if (a != null) { java.util.Arrays.sort(a); for (String n : a) if (n.endsWith(".txt")) ids.add("asset:" + n); }
        } catch (java.io.IOException ignored) { }
        String[] f = dir(c).list();
        if (f != null) { java.util.Arrays.sort(f); for (String n : f) ids.add("file:" + n); }
        for (String id : ids) {
            Script s = load(c, id);
            if (s == null) continue;
            if (b.length() > 1) b.append(',');
            b.append(s.json(id));
        }
        return b.append(']').toString();
    }

    /**
     * Copies a picked document into filesDir/stories/. Returns {"id": ...} on success or {"error": ...} with a message
     * for the page. The file must be UTF-8, at most 5 MB, and hold something that can be sent.
     */
    static String add(Context c, Uri uri) {
        String name = "story";
        try (Cursor q = c.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (q != null && q.moveToFirst() && !q.isNull(0)) name = q.getString(0);
        } catch (RuntimeException ignored) { }
        byte[] bytes;
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            if (in == null) return err("The file could not be opened.");
            bytes = readAll(in);
        } catch (Exception e) { return err("The file could not be read."); }
        if (bytes == null) return err("The file is larger than 5 MB.");
        String text = utf8(bytes);
        if (text == null) return err("The file is not UTF-8 text.");
        String file = Script.fileName(name);
        Script s = Script.parse(file, text);
        if (s.lines.isEmpty()) return err("Nothing in this file can be sent in Morse.");
        File d = dir(c);
        if (!d.isDirectory() && !d.mkdirs()) return err("The file could not be saved.");
        File out = new File(d, file);
        for (int i = 2; out.exists(); i++) out = new File(d, file.substring(0, file.length() - 4) + " (" + i + ").txt");
        try (FileOutputStream o = new FileOutputStream(out)) { o.write(bytes); }
        catch (Exception e) { out.delete(); return err("The file could not be saved."); }
        return "{\"id\":" + Script.quote("file:" + out.getName()) + ",\"title\":" + Script.quote(s.title) + "}";
    }

    /** Deletes an added file. Shipped stories cannot be removed. */
    static boolean remove(Context c, String id) {
        if (!Script.validId(id) || !id.startsWith("file:")) return false;
        synchronized (cache) { cache.remove(id); stamp.remove(id); }
        return new File(dir(c), id.substring(5)).delete();
    }

    /** Whether the story exists (it may still fail to parse). */
    static boolean exists(Context c, String id) {
        if (!Script.validId(id)) return false;
        if (id.startsWith("file:")) return new File(dir(c), id.substring(5)).isFile();
        try { c.getAssets().open("stories/" + id.substring(6)).close(); return true; } catch (java.io.IOException e) { return false; }
    }

    private static String err(String m) { return "{\"error\":" + Script.quote(m) + "}"; }
}
