package ru.playsoftware.j2meloader.util;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import androidx.preference.PreferenceManager;
import ru.playsoftware.j2meloader.BuildConfig;

/** Owns this preference in the main process; Java games run in :midlet. */
public final class ScreenshotSettingsProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        android.content.SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        if ("set".equals(method)) {
            if (extras == null || !extras.containsKey("visible")) throw new IllegalArgumentException("Missing value");
            if (!preferences.edit().putBoolean(Screenshots.BUTTON_PREFERENCE, extras.getBoolean("visible")).commit())
                throw new IllegalStateException("Could not save screenshot preference");
        } else if (!"get".equals(method)) throw new IllegalArgumentException("Unknown operation");
        Bundle result = new Bundle();
        result.putBoolean("visible", preferences.getBoolean(Screenshots.BUTTON_PREFERENCE, !BuildConfig.HANDHELD_MODE));
        return result;
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) { throw new UnsupportedOperationException(); }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
