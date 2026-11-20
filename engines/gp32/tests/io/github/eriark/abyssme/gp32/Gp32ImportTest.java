/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.gp32;

import android.content.Context;
import android.net.Uri;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Properties;
import org.junit.Test;
import static org.junit.Assert.*;
import ru.playsoftware.j2meloader.catalog.SourceIdentity;

public class Gp32ImportTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final Gp32Library library = new Gp32Library();

    @Test public void rejectsUnrecognizedHeaders() throws Exception {
        File file = File.createTempFile("gp32-", ".tmp", context.getCacheDir());
        try {
            Files.write(file.toPath(), new byte[1200]);
            for (String ext : new String[]{"bin", "fxe", "fpk", "smc"}) {
                try {
                    library.inspect(context, file, "bad." + ext, new Properties());
                    fail(ext);
                } catch (IOException expected) {
                    assertFalse(expected.getMessage().isEmpty());
                }
            }
        } finally { file.delete(); }
    }

    @Test public void preservesCardPathsAndTextAssetsInPackagedGames() throws Exception {
        File folder = Files.createTempDirectory(context.getCacheDir().toPath(), "gp32-zip").toFile();
        File archive = new File(folder, "bundle.zip");
        try {
            try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(archive.toPath()))) {
                for (String path : new String[]{"dist/GAME/Test.fxe", "dist/GAME/Data/config.txt"}) {
                    zip.putNextEntry(new java.util.zip.ZipEntry(path));
                    zip.write(new byte[]{1,2,3});
                    zip.closeEntry();
                }
            }
            assertEquals("Test.fpk", Gp32Archive.unpack(archive));
            String packageData = new String(Files.readAllBytes(archive.toPath()), java.nio.charset.StandardCharsets.ISO_8859_1);
            assertTrue(packageData.startsWith("GPKG"));
            assertTrue(packageData.contains("gp:\\GAME\\Data"));
            assertTrue(packageData.contains("config.txt"));
        } finally {
            archive.delete();
            folder.delete();
        }
    }

    @Test public void importsRealExecutableByContentAndPreservesItsTitle() throws Exception {
        String fixture = InstrumentationRegistry.getArguments().getString("gp32Game");
        org.junit.Assume.assumeNotNull(fixture);
        File file = new File(context.getExternalFilesDir(null), fixture);
        String hash = SourceIdentity.sha256(file);
        for (int attempt = 0; attempt < 2; attempt++) library.install(context, Uri.fromFile(file));
        assertEquals(hash, SourceIdentity.sha256(library.installed(context, hash)));
        assertEquals(1, library.read(context).stream().filter(card ->
                "Crazy Jack".equals(card.getTitle()) && card.getPath().endsWith(hash)).count());
    }
}
