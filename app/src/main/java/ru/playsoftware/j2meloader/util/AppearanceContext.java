package ru.playsoftware.j2meloader.util;

import android.content.Context;
import android.content.res.Configuration;
import androidx.appcompat.app.AppCompatDelegate;

/** Applies the app override to engine activities that do not use AppCompat. */
public final class AppearanceContext {
    private AppearanceContext() { }

    public static Context wrap(Context base) {
        int choice = AppCompatDelegate.getDefaultNightMode();
        if (choice != AppCompatDelegate.MODE_NIGHT_NO && choice != AppCompatDelegate.MODE_NIGHT_YES) return base;
        Configuration override = new Configuration();
        override.uiMode = choice == AppCompatDelegate.MODE_NIGHT_YES
                ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO;
        return base.createConfigurationContext(override);
    }
}
