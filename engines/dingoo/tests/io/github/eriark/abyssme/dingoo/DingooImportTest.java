/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.dingoo;

import android.content.Context;
import android.net.Uri;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.file.Files;
import java.util.Properties;
import ru.playsoftware.j2meloader.applist.AppItem;
import ru.playsoftware.j2meloader.catalog.ExternalLibrary;

public final class DingooImportTest {
    @Test public void rejectUnrelatedFiles() throws Exception {
        Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File file = File.createTempFile("dingoo-test-", ".app", c.getCacheDir());
        try {
            Files.write(file.toPath(), new byte[128]);
            try { new DingooLibrary().install(c, Uri.fromFile(file)); fail("Unrelated file imported"); }
            catch (IOException expected) { assertTrue(expected.getMessage().contains("CCDL")); }
        } finally { file.delete(); }
    }

    @Test public void importNativeFixture() throws Exception {
        String name = InstrumentationRegistry.getArguments().getString("dingooFixture");
        Assume.assumeNotNull(name);
        Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File file = new File(c.getExternalFilesDir(null),name);
        DingooLibrary library = new DingooLibrary();
        int platform = name.endsWith(".app") ? 6 : 7;
        String hash = library.inspect(file,name,new Properties());
        try {
            library.inspect(file, platform == 6 ? "wrong.cc" : "wrong.app",new Properties());
            fail("Mismatched CPU accepted");
        } catch (IOException expected) { assertTrue(expected.getMessage().contains("extension")); }
        library.install(c, Uri.fromFile(file));
        library.install(c, Uri.fromFile(file));
        int matches = 0;
        for (AppItem app : library.read(c)) {
            if (!app.getPath().equals("dingoo/" + hash)) continue;
            matches++;
            assertEquals(platform, ((ExternalLibrary.Item)app).platformId());
            assertEquals(file.length(), library.installed(c,hash).length());
        }
        assertEquals(1,matches);
    }
}
