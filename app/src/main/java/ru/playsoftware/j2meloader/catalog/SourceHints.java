package ru.playsoftware.j2meloader.catalog;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;

/** Optional, untrusted filename evidence. Never stores a URI, path or launch identity. */
public final class SourceHints {
    public static final String FILE = "source-hints.json";
    private SourceHints() { }

    public static String fileName(String value) {
        if (value == null) return null;
        value = value.replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1).trim();
        if (value.isEmpty() || value.length() > 512) return null;
        for (int i = 0; i < value.length(); i++) if (Character.isISOControl(value.charAt(i))) return null;
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jar") || lower.endsWith(".jad") || lower.endsWith(".kjx") ? value : null;
    }

    public static String fromUri(Context context, Uri uri) {
        if (uri == null) return null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (column >= 0) return fileName(cursor.getString(column));
                }
            } catch (RuntimeException ignored) { }
            // Opaque document IDs and provider paths are not filenames.
            return null;
        }
        return fileName(uri.getLastPathSegment());
    }

    public static String decode(String json) {
        if (json == null || json.length() > 4096) return null;
        try {
            JSONObject object = new JSONObject(json);
            return object.optInt("version") == 1 ? fileName(object.optString("filename", null)) : null;
        } catch (Exception ignored) { return null; }
    }

    public static String read(File directory) {
        File file = new File(directory, FILE);
        if (!file.isFile() || file.length() > 4096) return null;
        try { return decode(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)); }
        catch (IOException ignored) { return null; }
    }

    public static void write(File directory, String name) throws IOException {
        File target = new File(directory, FILE);
        name = fileName(name);
        if (name == null) { Files.deleteIfExists(target.toPath()); return; }
        try {
            JSONObject object = new JSONObject();
            object.put("version", 1); object.put("filename", name);
            Files.write(target.toPath(), object.toString().getBytes(StandardCharsets.UTF_8));
        } catch (org.json.JSONException impossible) { throw new IOException(impossible); }
    }
}
