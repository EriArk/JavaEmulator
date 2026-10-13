package ru.playsoftware.j2meloader.mophun;

import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.nio.file.Files;
import org.junit.Test;
import static org.junit.Assert.*;

public class MophunSaveRepairTest {
    private File folder() throws Exception {
        return Files.createTempDirectory(InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getCacheDir().toPath(), "save-repair-").toFile();
    }

    @Test public void invalidSelectionIsBackedUpWithoutLosingUnlockedProgress() throws Exception {
        File folder = folder(), file = new File(folder, "maxlevel.sav");
        byte[] original = new byte[22];
        original[0] = 4;
        Files.write(file.toPath(), original);
        MophunCompatibility.repairPrototypeLevelSelection(folder);
        byte[] expected = original.clone(); expected[21] = 1;
        assertArrayEquals(expected, Files.readAllBytes(file.toPath()));
        File backup = new File(folder, "maxlevel.sav.before-level-zero-fix");
        assertArrayEquals(original, Files.readAllBytes(backup.toPath()));
        MophunCompatibility.repairPrototypeLevelSelection(folder);
        assertArrayEquals(expected, Files.readAllBytes(file.toPath()));
        assertArrayEquals(original, Files.readAllBytes(backup.toPath()));
    }

    @Test public void emptyPrototypeSaveIsMovedAside() throws Exception {
        File folder = folder(), file = new File(folder, "maxlevel.sav");
        Files.write(file.toPath(), new byte[0]);
        MophunCompatibility.repairPrototypeLevelSelection(folder);
        assertFalse(file.exists());
        assertTrue(new File(folder, "maxlevel.sav.before-level-zero-fix").isFile());
    }

    @Test public void validCustomAndUnrecognizedSavesAreUntouched() throws Exception {
        for (int variant = 0; variant < 4; variant++) {
            File folder = folder(), file = new File(folder, "maxlevel.sav");
            byte[] original = new byte[variant == 3 ? 23 : 22];
            original[0] = (byte)(variant == 2 ? 0 : 4);
            if (variant == 0) original[21] = 3;
            if (variant == 1) original[1] = 'A';
            Files.write(file.toPath(), original);
            MophunCompatibility.repairPrototypeLevelSelection(folder);
            assertArrayEquals(original, Files.readAllBytes(file.toPath()));
            assertFalse(new File(folder, "maxlevel.sav.before-level-zero-fix").exists());
        }
    }
}
