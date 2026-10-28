package ru.playsoftware.j2meloader.util;

import android.content.Context;
import android.content.res.Configuration;
import androidx.appcompat.app.AppCompatDelegate;

/** Applies the app override to engine activities that do not use AppCompat. */
public final class AppearanceContext {
    private static String enginePalette;
    private AppearanceContext() { }

    public static android.os.Bundle refresh(Context context) {
        android.os.Bundle result = context.getContentResolver().call(
                Screenshots.settingsUri(), "appearance", null, null);
        if (result != null && !android.app.Application.getProcessName().equals(context.getPackageName()))
            enginePalette = result.getString("palette", "classic");
        return result;
    }

    public static String palette(Context context) {
        if (!android.app.Application.getProcessName().equals(context.getPackageName()) && enginePalette != null)
            return enginePalette;
        return androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
                .getString("pref_palette", "classic");
    }

    public static Context wrap(Context base) {
        base = AppLanguage.wrapNative(base);
        android.os.Bundle appearance = refresh(base);
        int choice = appearance == null ? AppCompatDelegate.getDefaultNightMode()
                : appearance.getInt("nightMode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        if (choice != AppCompatDelegate.MODE_NIGHT_NO && choice != AppCompatDelegate.MODE_NIGHT_YES) return base;
        Configuration override = new Configuration();
        override.uiMode = (base.getResources().getConfiguration().uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                | (choice == AppCompatDelegate.MODE_NIGHT_YES
                ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        return base.createConfigurationContext(override);
    }
}
