package ru.playsoftware.j2meloader.catalog;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.Test;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.util.AppUtils;
import static org.junit.Assert.*;

public class AdditionalGamesTest {
    @Test public void catalogDoesNotDeleteUnknownOrDamagedAdditionalGame() throws Exception {
        File directory = new File(Config.getAppDir(), "engine-preservation-" + UUID.randomUUID());
        assertTrue(directory.mkdirs());
        File marker = new File(directory, AdditionalGames.MANIFEST);
        File executable = new File(directory, "game.mpn");
        try {
            Files.write(marker.toPath(), new byte[]{'{', '}'});
            Files.write(executable.toPath(), new byte[]{1, 2, 3});
            Method scan = AppUtils.class.getDeclaredMethod("getAppsList", List.class);
            scan.setAccessible(true);
            scan.invoke(null, Collections.singletonList(directory.getName()));
            assertTrue(marker.isFile());
            assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(executable.toPath()));
        } finally {
            Files.deleteIfExists(marker.toPath());
            Files.deleteIfExists(executable.toPath());
            Files.deleteIfExists(directory.toPath());
        }
    }
}
