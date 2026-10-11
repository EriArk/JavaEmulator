package ru.playsoftware.j2meloader.util;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.IOException;
import java.io.OutputStream;
import java.util.UUID;
import ru.playsoftware.j2meloader.BuildConfig;

/** Shared screenshot preference and gallery storage for both game engines. */
public final class Screenshots {
    public static final String BUTTON_PREFERENCE = "pref_screenshot_button";
    private Screenshots() { }

    public static boolean isButtonVisible(Context context) {
        return context.getContentResolver().call(settingsUri(), "get", null, null).getBoolean("visible");
    }

    public static void setButtonVisible(Context context, boolean visible) {
        android.os.Bundle values = new android.os.Bundle();
        values.putBoolean("visible", visible);
        context.getContentResolver().call(settingsUri(), "set", null, values);
    }

    private static Uri settingsUri() {
        return Uri.parse("content://" + BuildConfig.APPLICATION_ID + ".screenshotSettings");
    }

    /** Call off the UI thread. Pending entries are removed if writing fails. */
    public static Uri save(Context context, Bitmap bitmap) throws IOException {
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, "AbyssME_" + UUID.randomUUID() + ".png");
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AbyssME");
        values.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IOException("Cannot create screenshot");
        try {
            try (OutputStream out = resolver.openOutputStream(uri)) {
                if (out == null || !bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
                    throw new IOException("Cannot write screenshot");
            }
            values.clear();
            values.put(MediaStore.Images.Media.IS_PENDING, 0);
            if (resolver.update(uri, values, null, null) != 1)
                throw new IOException("Cannot publish screenshot");
            return uri;
        } catch (IOException | RuntimeException failure) {
            try { resolver.delete(uri, null, null); }
            catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }
}
