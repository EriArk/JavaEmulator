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
    @Test public void customButtonsKeepLightTextOnDarkBackgrounds() {
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
