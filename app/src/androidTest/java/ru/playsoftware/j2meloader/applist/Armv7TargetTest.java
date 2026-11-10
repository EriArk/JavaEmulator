/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.applist;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import androidx.preference.PreferenceManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Before;
import org.junit.Test;
import ru.playsoftware.j2meloader.BuildConfig;
import ru.playsoftware.j2meloader.MainActivity;
import ru.playsoftware.j2meloader.R;
import static org.junit.Assert.*;

/** Explicit ARMv7 smoke check; generated data only, no games or firmware. */
public class Armv7TargetTest {
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    @Before public void explicitTarget() {
        org.junit.Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("armv7")));
    }
    @Test public void processLoadsAllRequiredBackendsAs32BitWithoutSymbian() throws Exception {
        assertFalse(android.os.Process.is64Bit());
        assertFalse(BuildConfig.SYMBIAN_EXPERIMENT);
        for(String library:new String[]{"micro3d","abyssm3graster","midi","avutil","avcodec","avformat","abyssaudiobridge","abyss-peer"})
            System.loadLibrary(library);
        assertThrows(ClassNotFoundException.class,()->Class.forName("io.github.eriark.abyssme.symbian.SymbianActivity"));
    }
    @Test public void unavailableTabsAreSkippedWithoutErasingTheirStoredState() {
        try(LibraryPreferencesSnapshot snapshot=new LibraryPreferencesSnapshot(context)) {
            SharedPreferences prefs=PreferenceManager.getDefaultSharedPreferences(context);
            prefs.edit().putBoolean("libraryTabsMigrated",true).putInt("libraryPlatform",4)
                    .putString("libraryTab.4.query","preserve former phone query").commit();
            try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
                scenario.onActivity(activity->{
                    assertEquals(View.GONE,activity.findViewById(R.id.platform_symbian).getVisibility());
                    assertEquals(View.GONE,activity.findViewById(R.id.platform_ngage).getVisibility());
                    AppsListFragment fragment=(AppsListFragment)activity.getSupportFragmentManager().findFragmentById(R.id.container);
                    assertEquals(1,prefs.getInt("libraryPlatform",0));
                    fragment.stepPlatform(-1);assertEquals(2,prefs.getInt("libraryPlatform",0));
                    fragment.stepPlatform(1);assertEquals(1,prefs.getInt("libraryPlatform",0));
                    assertEquals("preserve former phone query",prefs.getString("libraryTab.4.query",null));
                });
            }
        }
    }
}
