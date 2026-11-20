/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.cybiko;

import android.content.Context;
import android.net.Uri;
import android.graphics.BitmapFactory;
import androidx.test.platform.app.InstrumentationRegistry;
import io.github.eriark.abyssme.storage.StoragePaths;
import java.io.*;
import java.nio.file.*;
import org.junit.*;
import static org.junit.Assert.*;
import ru.playsoftware.j2meloader.catalog.ExternalLibrary;

public class CybikoImportTest {
    @Test public void installsClassicAndXtremeWithOriginalIconsAndFirmware() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("cybikoFixtures")));
        Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();
        File source=new File(c.getExternalFilesDir(null),"cybiko-check");
        CybikoLibrary library=new CybikoLibrary();
        for (boolean classic:new boolean[]{true,false}) {
            String model=classic?"classic":"xtreme";
            File game=new File(source,classic?"Homerun_C.app":"Homerun_X.zip");
            ExternalLibrary.Item card=library.install(c,Uri.fromFile(game));
            assertEquals("Homerun Derby",CybikoArtwork.description(new File(card.getArtworkDirectory(),"game.app"))[1]);
            assertEquals(12,card.platformId());
            assertEquals(card.getId(),library.install(c,Uri.fromFile(game)).getId());
            assertEquals(classic?47:48,BitmapFactory.decodeFile(card.getImagePathExt()).getWidth());
            File system=new File(StoragePaths.library(c),"cybiko/system/"+model);
            assertTrue(CybikoFirmware.install(c,Uri.fromFile(new File(source,model+".zip")),system,classic)>=2);
            assertTrue(CybikoFirmware.ready(system));
        }
        ExternalLibrary.Item snake=library.install(c,Uri.fromFile(new File(source,"Snake_Battle.app")));
        assertEquals("Snake Battle",snake.getTitle());
        assertEquals("1.2.32",CybikoArtwork.description(new File(snake.getArtworkDirectory(),"game.app"))[8]);
    }
}
