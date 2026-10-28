// SPDX-License-Identifier: GPL-3.0-only
package ru.playsoftware.j2meloader.localization;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.widget.TextView;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.util.AppLanguage;

/** Separate-process fixture; never starts an emulator or opens user files. */
public final class LanguageProbeActivity extends Activity implements AppLanguage.NativeScreen {
    static volatile LanguageProbeActivity active;
    static volatile int starts;
    private TextView label;
    volatile String displayed;
    @Override protected void attachBaseContext(Context base) { super.attachBaseContext(AppLanguage.wrapNative(base)); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); starts++; active = this;
        label = new TextView(this); setContentView(label); onAppLanguageChanged();
    }
    @Override public void onAppLanguageChanged() {
        if (label != null) { label.setText(R.string.settings_title); displayed = label.getText().toString(); }
    }
    @Override public void onConfigurationChanged(Configuration config) { super.onConfigurationChanged(config); onAppLanguageChanged(); }
    @Override protected void onDestroy() { if (active == this) active = null; super.onDestroy(); }
}
