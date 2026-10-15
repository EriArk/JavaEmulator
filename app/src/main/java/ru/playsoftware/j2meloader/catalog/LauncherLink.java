package ru.playsoftware.j2meloader.catalog;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.json.JSONException;
import org.json.JSONObject;
import ru.playsoftware.j2meloader.applist.AppItem;

/** Portable launch identity, never a path into an engine's private runtime. */
public final class LauncherLink {
    private static final int MAX_BYTES = 4096;
    private LauncherLink() { }

    public static boolean accepts(Context context, Uri uri) {
        String name = uri.getLastPathSegment();
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
            } catch (RuntimeException ignored) { }
        }
        return name != null && name.toLowerCase(Locale.ROOT).endsWith(".abyssme");
    }

    public static String encode(String packageName, String key) throws IOException {
        validate(key);
        try {
            return new JSONObject().put("format", "abyssme-launch").put("version", 1)
                    .put("package", packageName).put("game", key).toString();
        } catch (JSONException error) { throw new IOException("Cannot create launch file", error); }
    }

    static String decode(InputStream input, String packageName) throws IOException {
        if (input == null) throw new IOException("Cannot read launch file");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        for (int count; (count = input.read(buffer)) != -1;) {
            if (bytes.size() + count > MAX_BYTES) throw new IOException("Launch file is too large");
            bytes.write(buffer, 0, count);
        }
        try {
            JSONObject data = new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
            if (!"abyssme-launch".equals(data.getString("format")) || data.getInt("version") != 1)
                throw new IOException("Unsupported launch file. Export a new one from Game actions.");
            if (!packageName.equals(data.getString("package")))
                throw new IOException("This launch file belongs to another AbyssME app. Select that app in your launcher, or export a new file here.");
            String key = data.getString("game"); validate(key); return key;
        } catch (JSONException error) { throw new IOException("Invalid AbyssME launch file", error); }
    }

    private static void validate(String key) throws IOException {
        if (key == null || !key.matches("symbian:[0-9a-f]{64}/[0-9]{1,10}"))
            throw new IOException("Invalid installed game identity");
        long uid = Long.parseLong(key.substring(key.lastIndexOf('/') + 1));
        if (uid == 0 || uid > 0xffffffffL) throw new IOException("Invalid installed game identity");
    }

    static ExternalLibrary.Item resolve(Context context, Uri uri) throws IOException {
        if (!"content".equals(uri.getScheme()) && !"file".equals(uri.getScheme()))
            throw new IOException("Select a local launch file");
        String key;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            key = decode(input, context.getPackageName());
        }
        for (AppItem item : ExternalLibrary.read(context))
            if (key.equals(item.getPath()) && item instanceof ExternalLibrary.Item)
                return (ExternalLibrary.Item) item;
        throw new IOException("This game or its phone files are no longer installed in this AbyssME app. Import them, then export a new launch file.");
    }
}
