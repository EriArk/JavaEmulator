/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.flash;

import android.content.Context;
import android.net.Uri;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.zip.*;
import org.junit.*;
import static org.junit.Assert.*;
import ru.playsoftware.j2meloader.catalog.ExternalLibrary;

public class FlashImportTest {
    @Test public void rejectsTruncatedMovie() throws Exception {
        try { SwfMovie.read(new ByteArrayInputStream(new byte[]{'F','W','S',7,32,0,0,0,0x30})); fail(); }
        catch (EOFException expected) {}
    }
    @Test public void importsPhoneGamesWithCompanionMovies() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("flashFixtures")));
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        File source=new File(c.getExternalFilesDir(null),"flash-check");
        FlashLibrary lib=new FlashLibrary();
        for (String name:Arrays.asList("MicroInvaders.swf","Balloonatroid.zip")) {
            ExternalLibrary.Item card=lib.install(c,Uri.fromFile(new File(source,name)));
            assertEquals(13,card.platformId());
            assertEquals(card.getId(),lib.install(c,Uri.fromFile(new File(source,name))).getId());
            FlashLibrary.Installed game=lib.installed(c,card.getArtworkDirectory().getName());
            if (name.endsWith(".zip")) {
                assertEquals("mobile/Balloonatroid.swf",game.info.getProperty("entry"));
                try (InputStream in=game.open("mobile/Level13.swf")) { assertEquals(7,SwfMovie.read(in).version); }
            } else {
                assertEquals("176",game.info.getProperty("width"));
                File wrapped = File.createTempFile("wrapped-", ".zip", c.getCacheDir());
                try {
                    try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(wrapped))) {
                        zip.putNextEntry(new ZipEntry("nested/MicroInvaders.swf"));
                        Files.copy(new File(source,name).toPath(),zip);
                    }
                    assertEquals(card.getId(),lib.install(c,Uri.fromFile(wrapped)).getId());
                    lib.install(c,Uri.fromFile(new File(source,name)));
                } finally { wrapped.delete(); }
            }
            try { game.open("../other.swf"); fail(); } catch (FileNotFoundException expected) {}
        }
    }
}
