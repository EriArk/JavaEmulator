// SPDX-License-Identifier: GPL-3.0-only
package ru.playsoftware.j2meloader.util;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/** Single writer for Android 10-12; intentionally not exported. */
public final class AppLanguageProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public synchronized Bundle call(String method, String arg, Bundle extras) {
        android.content.SharedPreferences preferences = getContext().getSharedPreferences("app-language", Context.MODE_PRIVATE);
        if ("set".equals(method)) {
            if (!AppLanguage.supported(arg)) throw new IllegalArgumentException("Unsupported app language");
            if (!preferences.edit().putString("language", arg).commit())
                throw new IllegalStateException("Could not save app language");
            getContext().getContentResolver().notifyChange(AppLanguage.uri(getContext()), null);
        } else if (!"get".equals(method)) throw new IllegalArgumentException("Unknown language operation");
        String selected = preferences.getString("language", "");
        Bundle result = new Bundle();
        result.putString("language", AppLanguage.supported(selected) ? selected : "");
        return result;
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) { throw new UnsupportedOperationException(); }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
