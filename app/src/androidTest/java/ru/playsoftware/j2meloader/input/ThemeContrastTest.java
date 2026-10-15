package ru.playsoftware.j2meloader.input;

import android.content.res.Configuration;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import androidx.core.graphics.ColorUtils;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import ru.playsoftware.j2meloader.R;
import static org.junit.Assert.*;

public class ThemeContrastTest {
    @Test public void themeChoiceUpdatesActivityAndSurvivesRecreation() {
        android.app.Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        android.content.SharedPreferences preferences = androidx.preference.PreferenceManager
                .getDefaultSharedPreferences(instrumentation.getTargetContext());
        String previous = preferences.getString("pref_theme", null);
        try (androidx.test.core.app.ActivityScenario<ru.playsoftware.j2meloader.settings.SettingsActivity> scenario =
                     androidx.test.core.app.ActivityScenario.launch(ru.playsoftware.j2meloader.settings.SettingsActivity.class)) {
            for (String value : new String[]{"dark", "light", "system"}) {
                instrumentation.waitForIdleSync();
                scenario.onActivity(activity -> {
                    ru.playsoftware.j2meloader.settings.SettingsFragment fragment =
                            (ru.playsoftware.j2meloader.settings.SettingsFragment) activity.getSupportFragmentManager()
                                    .findFragmentById(R.id.settings_content);
                    assertNotNull(fragment);
                    androidx.preference.ListPreference theme = fragment.findPreference("pref_theme");
                    assertNotNull(theme);
                    assertArrayEquals(new CharSequence[]{"system", "light", "dark"}, theme.getEntryValues());
                    theme.setValue(value);
                });
                instrumentation.waitForIdleSync();
                scenario.recreate();
                scenario.onActivity(activity -> {
                    assertEquals(value, preferences.getString("pref_theme", null));
                    int expected = "dark".equals(value) ? Configuration.UI_MODE_NIGHT_YES
                            : "light".equals(value) ? Configuration.UI_MODE_NIGHT_NO
                            : android.content.res.Resources.getSystem().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                    assertEquals(expected, activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK);
                    android.content.Context engine = ru.playsoftware.j2meloader.util.AppearanceContext
                            .wrap(instrumentation.getTargetContext());
                    assertEquals(expected, engine.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK);
                    double luminance = ColorUtils.calculateLuminance(activity.getColor(R.color.background));
                    assertTrue(expected == Configuration.UI_MODE_NIGHT_YES ? luminance < .03 : luminance > .8);
                });
            }
        } finally {
            instrumentation.runOnMainSync(() -> {
                android.content.SharedPreferences.Editor editor = preferences.edit();
                if (previous == null) editor.remove("pref_theme"); else editor.putString("pref_theme", previous);
                editor.commit();
            });
        }
    }

    @Test public void customButtonsKeepContrastInBothAppearances() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            android.content.Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
            for (int mode : new int[]{Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES}) {
                Configuration config = new Configuration(app.getResources().getConfiguration());
                config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | mode;
                ContextThemeWrapper context = new ContextThemeWrapper(app.createConfigurationContext(config), R.style.AppTheme);
                androidx.appcompat.widget.AppCompatButton button = new androidx.appcompat.widget.AppCompatButton(context);
                for (int color : new int[]{R.color.btn_bg_normal, R.color.btn_bg_pressed})
                    assertTrue(ColorUtils.calculateContrast(button.getCurrentTextColor(), context.getColor(color)) >= 4.5);
            }
        });
    }

    @Test public void paletteHasRealLightSurfacesAndReadableStates() {
        for (int mode : new int[]{Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES}) {
            ContextThemeWrapper context = context(mode);
            double background = ColorUtils.calculateLuminance(context.getColor(R.color.background));
            assertTrue(mode == Configuration.UI_MODE_NIGHT_NO ? background > .8 : background < .03);
            for (int surface : new int[]{R.color.background, R.color.config_card, R.color.menu_surface,
                    R.color.phone_panel, R.color.phone_panel_alt, R.color.input_surface,
                    R.color.focus_surface, R.color.selected_surface, R.color.btn_bg_pressed, R.color.touch_button}) {
                for (int text : new int[]{R.color.text_primary, R.color.text_secondary, R.color.accent}) {
                    assertTrue("Contrast: " + text + " on " + surface + " mode " + mode,
                            ColorUtils.calculateContrast(context.getColor(text), context.getColor(surface)) >= 4.5);
                }
            }
            assertTrue(ColorUtils.calculateContrast(context.getColor(R.color.on_accent), context.getColor(R.color.accent)) >= 4.5);
            assertTrue(ColorUtils.calculateContrast(context.getColor(R.color.mapper_lcd_text), context.getColor(R.color.mapper_lcd)) >= 4.5);
            for (int badge : new int[]{R.color.control_a, R.color.control_b, R.color.control_x, R.color.control_y})
                assertTrue(ColorUtils.calculateContrast(context.getColor(badge), context.getColor(R.color.input_surface)) >= 4.5);
        }
    }

    @Test public void bothTouchDecksRenderThemedPanelsWithoutPaintingTheGame() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (int mode : new int[]{Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES}) {
                ContextThemeWrapper context = context(mode);
                for (int style : new int[]{TouchDeck.PHONE, TouchDeck.GAMEPAD}) {
                    ru.playsoftware.j2meloader.config.ProfileModel profile = new ru.playsoftware.j2meloader.config.ProfileModel();
                    profile.touchLayout = style;
                    TouchDeck deck = new TouchDeck(profile, 1, new TouchDeck.Sink() {
                        public void press(int code) { }
                        public void release(int code) { }
                        public void repeat(int code) { }
                        public void invalidate() { }
                        public void feedback() { }
                    });
                    deck.setTheme(context);
                    for (int[] size : new int[][]{{360, 640}, {640, 360}}) {
                        deck.layout(size[0], size[1]);
                        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(size[0], size[1], android.graphics.Bitmap.Config.ARGB_8888);
                        try {
                            bitmap.eraseColor(android.graphics.Color.MAGENTA);
                            deck.draw(new android.graphics.Canvas(bitmap));
                            assertEquals(context.getColor(R.color.touch_surface), bitmap.getPixel(0, size[1] - 1));
                            android.graphics.RectF game = deck.gameArea(size[0], size[1]);
                            assertEquals(android.graphics.Color.MAGENTA, bitmap.getPixel((int) game.centerX(), (int) game.centerY()));
                        } finally { bitmap.recycle(); }
                    }
                    deck.cancel();
                }
            }
        });
    }

    private static ContextThemeWrapper context(int mode) {
        android.content.Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Configuration config = new Configuration(app.getResources().getConfiguration());
        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | mode;
        return new ContextThemeWrapper(app.createConfigurationContext(config), R.style.AppTheme);
    }

    @Test public void nativeFieldsHaveContrastInBothSystemModes() {
        android.content.Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        for (int mode : new int[]{Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES}) {
            Configuration config = new Configuration(app.getResources().getConfiguration());
            config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | mode;
            for (int style : new int[]{R.style.AppTheme, R.style.SettingsTheme, R.style.FilePickerTheme, R.style.FilePickerAlertDialogTheme}) {
                ContextThemeWrapper context = new ContextThemeWrapper(app.createConfigurationContext(config), style);
                TypedValue text = new TypedValue(), background = new TypedValue();
                assertTrue(context.getTheme().resolveAttribute(android.R.attr.textColorPrimary, text, true));
                assertTrue(context.getTheme().resolveAttribute(android.R.attr.colorBackground, background, true));
                int foreground = text.resourceId != 0 ? context.getColorStateList(text.resourceId).getDefaultColor() : text.data;
                int surface = background.resourceId != 0 ? context.getColor(background.resourceId) : background.data;
                assertTrue("Theme " + style + " in mode " + mode, ColorUtils.calculateContrast(foreground, surface) >= 4.5);
            }
        }
    }
}
