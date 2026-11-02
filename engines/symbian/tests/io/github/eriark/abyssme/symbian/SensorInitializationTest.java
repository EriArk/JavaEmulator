package io.github.eriark.abyssme.symbian;

import androidx.test.platform.app.InstrumentationRegistry;
import com.github.eka2l1.emu.Emulator;
import java.io.File;
import java.nio.file.Files;
import org.junit.Test;
import static org.junit.Assert.*;

public class SensorInitializationTest {
    @Test public void backgroundPreparationBindsMainSensorLoopAndCanRepeat() throws Exception {
        assertNotEquals(android.os.Looper.getMainLooper(), android.os.Looper.myLooper());
        File root = Files.createTempDirectory(InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getCacheDir().toPath(), "sensor-init-").toFile();
        try {
            for (int run = 0; run < 2; run++) {
                Emulator.prepare(root.getAbsolutePath());
                assertTrue(Emulator.environmentReport().contains("Sensor event loop: ready"));
                Emulator.stopSession();
            }
        } finally { Emulator.stopSession(); PhoneBackupTest.remove(root); }
    }
}
