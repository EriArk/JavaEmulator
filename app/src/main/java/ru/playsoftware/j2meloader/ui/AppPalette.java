package ru.playsoftware.j2meloader.ui;

import android.content.Context;
import android.content.res.Configuration;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import ru.playsoftware.j2meloader.R;

/** UI colors only. Guest rendering, imported art and per-game input are independent. */
public final class AppPalette {
    public static final String PREFERENCE = "pref_palette";
    public static final String[] NAMES = {"Classic", "Retro LCD", "High contrast"};
    public static final String[] VALUES = {"classic", "retro", "contrast"};
    private AppPalette() { }

    public static String selected(Context context) {
        return ru.playsoftware.j2meloader.util.AppearanceContext.palette(context);
    }
    public static int style(String value) {
        return "retro".equals(value) ? R.style.PaletteRetro
                : "contrast".equals(value) ? R.style.PaletteContrast : R.style.PaletteClassic;
    }
    public static void apply(Context context) {
        if (context instanceof android.app.Activity)
            ru.playsoftware.j2meloader.util.AppearanceContext.refresh(context);
        context.getTheme().applyStyle(style(selected(context)), true);
    }
    public static Context preview(Context context, String value, int nightMode) {
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | nightMode;
        ContextThemeWrapper wrapper = new ContextThemeWrapper(context.createConfigurationContext(config), R.style.AppTheme_NoActionBar);
        wrapper.getTheme().applyStyle(style(value), true);
        return wrapper;
    }
    public static int color(Context context, int resource) {
        int attr = 0;
        if (resource == R.color.primary) attr = R.attr.abyss_primary;
        if (resource == R.color.primary_dark) attr = R.attr.abyss_primary_dark;
        if (resource == R.color.accent) attr = R.attr.abyss_accent;
        if (resource == R.color.background) attr = R.attr.abyss_background;
        if (resource == R.color.text_primary) attr = R.attr.abyss_text_primary;
        if (resource == R.color.text_secondary) attr = R.attr.abyss_text_secondary;
        if (resource == R.color.btn_bg_normal) attr = R.attr.abyss_btn_bg_normal;
        if (resource == R.color.btn_bg_pressed) attr = R.attr.abyss_btn_bg_pressed;
        if (resource == R.color.config_card) attr = R.attr.abyss_config_card;
        if (resource == R.color.config_card_stroke) attr = R.attr.abyss_config_card_stroke;
        if (resource == R.color.phone_panel) attr = R.attr.abyss_phone_panel;
        if (resource == R.color.phone_panel_alt) attr = R.attr.abyss_phone_panel_alt;
        if (resource == R.color.phone_cyan) attr = R.attr.abyss_phone_cyan;
        if (resource == R.color.phone_green) attr = R.attr.abyss_phone_green;
        if (resource == R.color.phone_line) attr = R.attr.abyss_phone_line;
        if (resource == R.color.focus_surface) attr = R.attr.abyss_focus_surface;
        if (resource == R.color.selected_surface) attr = R.attr.abyss_selected_surface;
        if (resource == R.color.input_surface) attr = R.attr.abyss_input_surface;
        if (resource == R.color.menu_surface) attr = R.attr.abyss_menu_surface;
        if (resource == R.color.on_accent) attr = R.attr.abyss_on_accent;
        if (resource == R.color.mapper_lcd) attr = R.attr.abyss_mapper_lcd;
        if (resource == R.color.mapper_lcd_text) attr = R.attr.abyss_mapper_lcd_text;
        if (resource == R.color.mapper_lcd_line) attr = R.attr.abyss_mapper_lcd_line;
        if (resource == R.color.control_a) attr = R.attr.abyss_control_a;
        if (resource == R.color.control_b) attr = R.attr.abyss_control_b;
        if (resource == R.color.control_x) attr = R.attr.abyss_control_x;
        if (resource == R.color.control_y) attr = R.attr.abyss_control_y;
        if (resource == R.color.touch_surface) attr = R.attr.abyss_touch_surface;
        if (resource == R.color.touch_button) attr = R.attr.abyss_touch_button;
        if (resource == R.color.touch_line) attr = R.attr.abyss_touch_line;
        if (resource == R.color.touch_key_highlight) attr = R.attr.abyss_touch_key_highlight;
        if (resource == R.color.touch_key_shadow) attr = R.attr.abyss_touch_key_shadow;
        TypedValue value = new TypedValue();
        if (attr != 0 && context.getTheme().resolveAttribute(attr, value, true)) {
            if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT)
                return value.data;
            if (value.resourceId != 0) return context.getColor(value.resourceId);
        }
        return context.getColor(resource);
    }
}
