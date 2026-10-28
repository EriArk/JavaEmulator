// SPDX-License-Identifier: GPL-3.0-only
package ru.playsoftware.j2meloader.localization;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.util.AppLanguage;

public final class LanguageProbeProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        Bundle result = new Bundle();
        LanguageProbeActivity activity = LanguageProbeActivity.active;
        result.putString("title", getContext().getString(R.string.settings_title));
        result.putString("guest", AppLanguage.systemLocale(getContext()).toLanguageTag());
        result.putString("selected", AppLanguage.selected(getContext()));
        result.putInt("starts", LanguageProbeActivity.starts);
        if (activity != null) {
            result.putString("screen", activity.displayed);
            if ("finish".equals(method)) activity.runOnUiThread(activity::finish);
        }
        return result;
    }
    @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { throw new UnsupportedOperationException(); }
    @Override public String getType(Uri u) { return null; }
    @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException(); }
}
