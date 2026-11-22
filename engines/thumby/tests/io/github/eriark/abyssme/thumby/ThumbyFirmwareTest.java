/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.thumby;

import androidx.test.platform.app.InstrumentationRegistry;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ThumbyFirmwareTest {
    @Test public void recognizesBootRomAndRejectsPartialFirmware() throws Exception {
        Path dir = InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir().toPath();
        Path path = Files.createTempFile(dir, "firmware-test", ".bin");
        try {
            byte[] rom = new byte[16384];
            rom[16] = 'M'; rom[17] = 'u'; rom[18] = 1;
            Files.write(path, rom);
            assertEquals(ThumbyFirmware.BOOT, ThumbyFirmware.identify(path));

            ByteBuffer block = ByteBuffer.allocate(512).order(ByteOrder.LITTLE_ENDIAN);
            block.putInt(0, 0x0a324655).putInt(4, 0x9e5d5157).putInt(12, 0x10000000)
                    .putInt(16, 256).putInt(24, 1).putInt(508, 0x0ab16f30);
            Files.write(path, block.array());
            assertEquals(ThumbyFirmware.FLASH, ThumbyFirmware.identify(path));
            block.putInt(24, 2);
            Files.write(path, block.array());
            try { ThumbyFirmware.identify(path); fail("Accepted a partial UF2"); }
            catch (IOException expected) { assertTrue(expected.getMessage().contains("Incomplete")); }
        } finally { Files.deleteIfExists(path); }
    }
}
