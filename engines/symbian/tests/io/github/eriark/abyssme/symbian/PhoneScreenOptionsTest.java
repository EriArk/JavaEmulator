/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.symbian;

import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.nio.file.Files;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.*;

public final class PhoneScreenOptionsTest {
    @Test public void cachedModesArePhoneBoundAndInvalidatedByConfigurationChanges() throws Exception {
        File files = Files.createTempDirectory(InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getCacheDir().toPath(), "screen-options-").toFile();
        try {
            SystemFileCatalog catalog = new SystemFileCatalog(new File(files, "symbian-systems"));
            File rom = new File(files, "test.rom"); Files.write(rom.toPath(), new byte[]{1});
            SystemFileCatalog.Entry phone = catalog.add(Collections.singletonList(rom), (runtime, r, p) -> {
                File wsini = new File(runtime, "data/drives/z/rm-320/system/data/WSINI.INI");
                Files.createDirectories(wsini.getParentFile().toPath());
                Files.write(wsini.toPath(), new byte[]{1});
                Files.write(new File(runtime, "data/devices.yml").toPath(), new byte[]{2});
                Files.write(new File(runtime, "config.yml").toPath(), new byte[]{3});
                return new String[]{"RM-320", "Fixture", "epoc93fp1"};
            });
            assertEquals(-1, PhoneScreenOptions.read(files, phone.id));
            PhoneScreenOptions.record(files, phone.id, 5);
            assertEquals(5, PhoneScreenOptions.read(files, phone.id));
            assertEquals(-1, PhoneScreenOptions.read(files, String.join("", Collections.nCopies(64, "0"))));
            File wsini = new File(catalog.runtime(phone), "data/drives/z/rm-320/system/data/WSINI.INI");
            long modified = wsini.lastModified();
            Files.write(wsini.toPath(), new byte[]{9}); wsini.setLastModified(modified);
            assertEquals("Same size/timestamp replacement must invalidate", -1, PhoneScreenOptions.read(files, phone.id));
            PhoneScreenOptions.record(files, phone.id, 3);
            assertEquals(3, PhoneScreenOptions.read(files, phone.id));
            Files.write(new File(catalog.runtime(phone), "config.yml").toPath(), new byte[]{4});
            assertEquals(-1, PhoneScreenOptions.read(files, phone.id));
            PhoneScreenOptions.record(files, phone.id, 7);
            File cache = new File(files, "symbian-screen-options/" + phone.id + ".json");
            String json = new String(Files.readAllBytes(cache.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            Files.write(cache.toPath(), json.replace("\"version\":1", "\"version\":99")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertEquals(-1, PhoneScreenOptions.read(files, phone.id));
            Files.write(cache.toPath(), new byte[]{0});
            assertEquals(-1, PhoneScreenOptions.read(files, phone.id));
            Files.write(cache.toPath(), json.replace("\"modes\":7", "\"modes\":6")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertEquals(-1, PhoneScreenOptions.read(files, phone.id));
            File registration = new File(catalog.runtime(phone), "data/devices.yml");
            PhoneScreenOptions.record(files, phone.id, 7);
            Files.write(registration.toPath(), new byte[]{8});
            assertEquals(-1, PhoneScreenOptions.read(files, phone.id));
            File metadata = new File(catalog.set(phone.id), "set.json");
            String contents = new String(Files.readAllBytes(metadata.toPath()), java.nio.charset.StandardCharsets.UTF_8);
            Files.write(metadata.toPath(), contents.replace("epoc93fp1", "epoc6")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            PhoneScreenOptions.record(files, phone.id, 7);
            assertEquals("Legacy phones cannot inherit modern capabilities", 1, PhoneScreenOptions.read(files, phone.id));
            assertEquals(1, PhoneScreenOptions.read(files, "legacy"));
            assertEquals(-1, PhoneScreenOptions.read(files, "../outside"));
        } finally { PhoneBackupTest.remove(files); }
    }
}
