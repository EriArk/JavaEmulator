package ru.playsoftware.j2meloader.util;

import android.content.Context;
import android.os.Bundle;
import ru.playsoftware.j2meloader.BuildConfig;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.input.GameMenuDialog;

/** Global shortcut preferences, owned by the main process rather than :midlet. */
public final class GameShortcuts {
    public static final String DISPLAY_PREFERENCE = "pref_display_button";
    private GameShortcuts() { }

    public static boolean isDisplayVisible(Context context) {
        return context.getContentResolver().call(Screenshots.settingsUri(), "get", DISPLAY_PREFERENCE, null)
                .getBoolean("visible");
    }

    public static void setDisplayVisible(Context context, boolean visible) {
        Bundle values = new Bundle();
        values.putBoolean("visible", visible);
        context.getContentResolver().call(Screenshots.settingsUri(), "set", DISPLAY_PREFERENCE, values);
    }

    public static void page(Context context, GameMenuDialog menu, boolean hasDisplay,
                            Runnable refresh, Runnable back) {
        menu.page(context.getString(R.string.on_screen_shortcuts));
        menu.setBackAction(back);
        if (hasDisplay && !BuildConfig.HANDHELD_MODE) {
            menu.toggle(context.getString(R.string.display_button), isDisplayVisible(context), value -> {
                setDisplayVisible(context, value); refresh.run();
            });
        }
        menu.toggle(context.getString(R.string.screenshot_button), Screenshots.isButtonVisible(context), value -> {
            Screenshots.setButtonVisible(context, value); refresh.run();
        });
    }
}
