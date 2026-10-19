package io.github.eriark.abyssme.symbian;

import android.content.SharedPreferences;
import android.util.AtomicFile;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.Map;

/** Portable titles are scoped to a phone; Android row IDs and play history are not. */
final class PhoneLibraryTitles {
    static final String FILE = "library-titles.json";
    static final int MAX_BYTES = 8 << 20;

    private static File file(File files, String system) throws IOException {
        if (system == null || !system.matches("[0-9a-f]{64}")) throw new IOException("Invalid phone identity");
        return new File(files, "symbian-systems/" + system + "/" + FILE);
    }

    static JSONObject validate(JSONObject document) throws IOException {
        try {
            if (document.getInt("version") != 1) throw new IOException("Unsupported library titles");
            JSONObject titles = document.getJSONObject("titles");
            if (titles.length() > 10000) throw new IOException("Too many library titles");
            for (Iterator<String> keys = titles.keys(); keys.hasNext();) {
                String uid = keys.next();
                if (!uid.matches("[1-9][0-9]{0,9}") || Long.parseLong(uid) > 0xffffffffL)
                    throw new IOException("Invalid title game identity");
                Object value = titles.get(uid);
                if (!(value instanceof String)) throw new IOException("Invalid library title");
                validateTitle((String) value);
            }
            return document;
        } catch (JSONException e) { throw new IOException("Invalid library titles", e); }
    }

    private static void validateTitle(String title) throws IOException {
        if (title == null || title.trim().isEmpty() || title.length() > 160)
            throw new IOException("Game titles must contain 1 to 160 characters");
        for (int i = 0; i < title.length(); i++)
            if (Character.isISOControl(title.charAt(i))) throw new IOException("Game titles cannot contain control characters");
    }

    static synchronized JSONObject read(File files, String system) throws IOException {
        File target = file(files, system);
        if (Files.isSymbolicLink(target.toPath()) || Files.isSymbolicLink(new File(target + ".bak").toPath()))
            throw new IOException("Linked library titles cannot be used");
        AtomicFile atomic = new AtomicFile(target);
        try (InputStream in = atomic.openRead(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            for (int n; (n = in.read(buffer)) != -1;) {
                if (out.size() + n > MAX_BYTES) throw new IOException("Library titles are too large");
                out.write(buffer, 0, n);
            }
            return validate(new JSONObject(out.toString("UTF-8")));
        } catch (FileNotFoundException e) {
            if (target.exists()) throw e;
            try { return new JSONObject().put("version", 1).put("titles", new JSONObject()); }
            catch (JSONException impossible) { throw new IOException(impossible); }
        } catch (JSONException e) { throw new IOException("Invalid library titles", e); }
    }

    private static void write(File files, String system, JSONObject document) throws IOException {
        byte[] bytes = validate(document).toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new IOException("Library titles are too large");
        File target = file(files, system);
        Files.createDirectories(target.getParentFile().toPath());
        AtomicFile atomic = new AtomicFile(target);
        FileOutputStream out = null;
        try {
            out = atomic.startWrite(); out.write(bytes); atomic.finishWrite(out);
        } catch (IOException e) { if (out != null) atomic.failWrite(out); throw e; }
    }

    static synchronized void rename(File files, String system, long uid, String title) throws IOException {
        validateTitle(title);
        JSONObject document = read(files, system);
        try { document.getJSONObject("titles").put(Long.toString(uid), title.trim()); }
        catch (JSONException e) { throw new IOException(e); }
        write(files, system, document);
    }

    static synchronized JSONObject migrate(File files, String system, SharedPreferences preferences) throws IOException {
        JSONObject document = read(files, system);
        String prefix = "symbian:" + system + "/";
        SharedPreferences.Editor remove = preferences.edit();
        boolean changed = false;
        try {
            JSONObject titles = document.getJSONObject("titles");
            for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
                String key = entry.getKey();
                if (!key.startsWith(prefix) || !key.endsWith(".title")) continue;
                String uid = key.substring(prefix.length(), key.length() - ".title".length());
                if (!titles.has(uid)) titles.put(uid, entry.getValue());
                remove.remove(key); changed = true;
            }
            if (changed) {
                // Persist before removing legacy values; a retry never overwrites a newer title.
                write(files, system, document);
                if (!remove.commit()) throw new IOException("Cannot finish migrating library titles");
            }
            return document.getJSONObject("titles");
        } catch (JSONException e) { throw new IOException(e); }
    }
}
