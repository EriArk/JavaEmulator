// SPDX-License-Identifier: GPL-3.0-only
package ru.playsoftware.j2meloader.util;

import android.app.Activity;
import android.app.Application;
import android.app.LocaleManager;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.LocaleList;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.WeakHashMap;

/** Platform/AppCompat own UI locale changes; the provider persists pre-33 choices across processes. */
public final class AppLanguage {
    public static final String[] TAGS = {"", "en", "es", "pt-BR", "ru", "fr", "de"};
    private static final Set<Activity> activities = Collections.newSetFromMap(new WeakHashMap<>());
    private static Application application;
    private static ContentObserver observer;
    private AppLanguage() { }

    public interface NativeScreen { void onAppLanguageChanged(); }

    public static boolean supported(String tag) {
        for (String supported : TAGS) if (supported.equals(tag)) return true;
        return false;
    }

    static Uri uri(Context context) { return Uri.parse("content://" + context.getPackageName() + ".language"); }

    public static String selected(Context context) {
        if (Build.VERSION.SDK_INT >= 33) return context.getSystemService(LocaleManager.class)
                .getApplicationLocales().toLanguageTags();
        Bundle result = context.getContentResolver().call(uri(context), "get", null, null);
        return result == null ? "" : result.getString("language", "");
    }

    public static void select(Context context, String tag) {
        if (!supported(tag)) throw new IllegalArgumentException("Unsupported app language");
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager.class).setApplicationLocales(LocaleList.forLanguageTags(tag));
        } else {
            context.getContentResolver().call(uri(context), "set", tag, null);
            synchronize();
        }
    }

    public static Locale systemLocale(Context context) {
        LocaleList locales = Build.VERSION.SDK_INT >= 33
                ? context.getSystemService(LocaleManager.class).getSystemLocales()
                : Resources.getSystem().getConfiguration().getLocales();
        return locales.isEmpty() ? Locale.ENGLISH : locales.get(0);
    }

    public static void initialize(Application app) {
        application = app;
        if (Build.VERSION.SDK_INT >= 33 && Application.getProcessName().equals(app.getPackageName())) {
            android.content.SharedPreferences preferences = app.getSharedPreferences("app-language", Context.MODE_PRIVATE);
            if (!preferences.getBoolean("platform-migrated", false)) {
                String previous = preferences.getString("language", "");
                LocaleManager manager = app.getSystemService(LocaleManager.class);
                if (!previous.isEmpty() && supported(previous) && manager.getApplicationLocales().isEmpty())
                    manager.setApplicationLocales(LocaleList.forLanguageTags(previous));
                preferences.edit().putBoolean("platform-migrated", true).apply();
            }
        }
        if (Build.VERSION.SDK_INT < 33) {
            observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
                @Override public void onChange(boolean selfChange) { synchronize(); }
            };
            app.getContentResolver().registerContentObserver(uri(app), false, observer);
            synchronize();
        }
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            public void onActivityCreated(Activity activity, Bundle state) { activities.add(activity); }
            public void onActivityResumed(Activity activity) {
                if (Build.VERSION.SDK_INT < 33) synchronize();
            }
            public void onActivityDestroyed(Activity activity) { activities.remove(activity); }
            public void onActivityStarted(Activity activity) { }
            public void onActivityPaused(Activity activity) { }
            public void onActivityStopped(Activity activity) { }
            public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
        });
    }

    public static void configurationChanged() {
        if (Build.VERSION.SDK_INT < 33) synchronize();
    }

    private static void synchronize() {
        if (application == null || Build.VERSION.SDK_INT >= 33) return;
        String tag = selected(application);
        LocaleListCompat requested = LocaleListCompat.forLanguageTags(tag);
        applyResources(application, tag);
        if (!requested.equals(AppCompatDelegate.getApplicationLocales()))
            AppCompatDelegate.setApplicationLocales(requested);
        // AppCompat 1.6 only updates Activity contexts on API <=32. Engine Activities
        // and app-owned background messages also need the same resource configuration.
        for (Activity activity : activities.toArray(new Activity[0])) {
            if (!(activity instanceof AppCompatActivity) && applyResources(activity, tag)
                    && activity instanceof NativeScreen) ((NativeScreen) activity).onAppLanguageChanged();
        }
    }

    @SuppressWarnings("deprecation")
    private static boolean applyResources(Context context, String tag) {
        Configuration current = context.getResources().getConfiguration();
        LocaleList locales = tag.isEmpty() ? Resources.getSystem().getConfiguration().getLocales()
                : LocaleList.forLanguageTags(tag);
        if (current.getLocales().equals(locales)) return false;
        Configuration changed = new Configuration(current);
        changed.setLocales(locales);
        context.getResources().updateConfiguration(changed, context.getResources().getDisplayMetrics());
        return true;
    }

    public static Context wrapNative(Context base) {
        if (Build.VERSION.SDK_INT >= 33) return base;
        String tag = selected(base);
        if (tag.isEmpty()) return base;
        Configuration override = new Configuration();
        override.setLocales(LocaleList.forLanguageTags(tag));
        return base.createConfigurationContext(override);
    }
}
