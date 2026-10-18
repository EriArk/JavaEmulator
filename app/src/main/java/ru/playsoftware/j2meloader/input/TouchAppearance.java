package ru.playsoftware.j2meloader.input;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.view.View;
import android.widget.ImageButton;
import androidx.core.view.ViewCompat;
import androidx.core.widget.ImageViewCompat;
import ru.playsoftware.j2meloader.BuildConfig;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.config.ProfileModel;

/** One isolated palette for the Phone touch deck and in-game shortcut buttons. */
public final class TouchAppearance {
    private TouchAppearance() { }

    public static int mode(Context context, ProfileModel profile) {
        if (profile.touchTheme == TouchDeck.THEME_DARK) return Configuration.UI_MODE_NIGHT_YES;
        if (profile.touchTheme == TouchDeck.THEME_LIGHT) return Configuration.UI_MODE_NIGHT_NO;
        return context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
    }

    public static Context palette(Context context, ProfileModel profile) {
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | mode(context, profile);
        return context.createConfigurationContext(config);
    }

    public static void shortcuts(View root, ProfileModel profile) {
        if (BuildConfig.HANDHELD_MODE || root == null || profile == null) return;
        Context palette = palette(root.getContext(), profile);
        for (int id : new int[]{R.id.game_display_button, R.id.game_screenshot_button, R.id.game_menu_button, R.id.game_controls_handle}) {
            ImageButton button = root.findViewById(id);
            if (button == null) continue;
            ViewCompat.setBackgroundTintList(button, null);
            android.graphics.drawable.Drawable background = palette.getDrawable(R.drawable.bg_touch_shortcut);
            if (id == R.id.game_controls_handle) background = new android.graphics.drawable.InsetDrawable(
                    background, Math.round(8 * root.getResources().getDisplayMetrics().density));
            button.setBackground(background);
            if (id == R.id.game_controls_handle) {
                int padding = Math.round(16 * root.getResources().getDisplayMetrics().density);
                button.setPadding(padding, padding, padding, padding);
            }
            ImageViewCompat.setImageTintList(button, ColorStateList.valueOf(palette.getColor(R.color.text_primary)));
        }
    }
}
