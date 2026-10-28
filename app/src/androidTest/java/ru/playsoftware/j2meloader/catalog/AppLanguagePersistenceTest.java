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
                assertFalse("Restore the previous checkpoint first",checkpoint.contains("original"));
                assertTrue(checkpoint.edit().putString("original",AppLanguage.selected(context))
                        .putString("guest",AppLanguage.systemLocale(context).toLanguageTag()).commit());
                scenario.onActivity(activity -> AppLanguage.select(activity,"es"));
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                assertEquals("es",AppLanguage.selected(context));
            } else {
                assertTrue("Run the set stage first",checkpoint.contains("original"));
                String original=checkpoint.getString("original", "");
                try {
                    assertEquals("es",AppLanguage.selected(context));
                    assertEquals("Ajustes",context.getString(R.string.settings_title));
                    scenario.onActivity(activity -> assertEquals("Ajustes",activity.getString(R.string.settings_title)));
                    assertEquals(checkpoint.getString("guest", ""),AppLanguage.systemLocale(context).toLanguageTag());
                } finally {
                    scenario.onActivity(activity -> AppLanguage.select(activity,original));
                    assertTrue(checkpoint.edit().clear().commit());
                }
            }
        }
    }
}
