/* Copyright 2026 AbyssME contributors. SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.mophun;

import android.content.Context;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import ru.playsoftware.j2meloader.catalog.AdditionalGames;
import static org.junit.Assert.*;

public class MophunProviderTest {
    @Test public void manifestProviderLoadsInTheInstalledApp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertNotNull(AdditionalGames.get(context));
        assertEquals(MophunEngine.class, AdditionalGames.get(context).getClass());
    }

    @Test public void gameActivityIsPrivateAndPreviewIsAbsent() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertFalse(context.getPackageManager().getActivityInfo(
                new ComponentName(context, MophunGameActivity.class), 0).exported);
        try {
            context.getPackageManager().getActivityInfo(new ComponentName(context,
                    "ru.playsoftware.j2meloader.mophun.MophunPreviewActivity"), 0);
            fail("Diagnostic activity must not be shipped");
        } catch (PackageManager.NameNotFoundException expected) { }
    }
}
