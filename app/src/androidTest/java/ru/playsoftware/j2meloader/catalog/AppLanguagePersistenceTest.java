// SPDX-License-Identifier: GPL-3.0-only
package ru.playsoftware.j2meloader.catalog;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.settings.SettingsActivity;
import ru.playsoftware.j2meloader.util.AppLanguage;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Run set, force-stop/reinstall the debug APK, then verify in a new process. */
public class AppLanguagePersistenceTest {
    @Test public void coldStartAndUpgradeCheckpoint() {
        String stage=InstrumentationRegistry.getArguments().getString("languageStage", "");
        assumeTrue(stage.equals("set") || stage.equals("verify"));
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences checkpoint=context.getSharedPreferences("language-test-checkpoint", Context.MODE_PRIVATE);
        try(ActivityScenario<SettingsActivity> scenario=ActivityScenario.launch(SettingsActivity.class)) {
            if(stage.equals("set")) {
                String tag=InstrumentationRegistry.getArguments().getString("languageTag", "es");
                assertTrue("Use an advertised explicit language",!tag.isEmpty() && AppLanguage.supported(tag));
                assertFalse("Restore the previous checkpoint first",checkpoint.contains("original"));
                assertTrue(checkpoint.edit().putString("original",AppLanguage.selected(context))
                        .putString("target",tag)
                        .putString("guest",AppLanguage.systemLocale(context).toLanguageTag()).commit());
                scenario.onActivity(activity -> AppLanguage.select(activity,tag));
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                assertEquals(tag,AppLanguage.selected(context));
            } else {
                assertTrue("Run the set stage first",checkpoint.contains("original"));
                String original=checkpoint.getString("original", "");
                try {
                    String tag=checkpoint.getString("target", "es");
                    android.content.res.Configuration config=new android.content.res.Configuration();
                    config.setLocales(android.os.LocaleList.forLanguageTags(tag));
                    String title=context.createConfigurationContext(config).getString(R.string.settings_title);
                    assertEquals(tag,AppLanguage.selected(context));
                    assertEquals(tag,context.getResources().getConfiguration().getLocales().get(0).toLanguageTag());
                    assertEquals(title,context.getString(R.string.settings_title));
                    scenario.onActivity(activity -> assertEquals(title,activity.getString(R.string.settings_title)));
                    assertEquals(checkpoint.getString("guest", ""),AppLanguage.systemLocale(context).toLanguageTag());
                } finally {
                    scenario.onActivity(activity -> AppLanguage.select(activity,original));
                    assertTrue(checkpoint.edit().clear().commit());
                }
            }
        }
    }
}
