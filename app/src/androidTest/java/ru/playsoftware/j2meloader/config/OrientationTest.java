package ru.playsoftware.j2meloader.config;

import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.io.File;
import java.nio.file.Files;
import ru.playsoftware.j2meloader.BuildConfig;
import static org.junit.Assert.*;

public class OrientationTest {
    @Test public void preparationPreservesPhoneOrientationAndProfileRoundTrips() throws Exception {
        android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File dir = Files.createTempDirectory(context.getCacheDir().toPath(), "orientation-").toFile();
        for (int orientation = 0; orientation < 4; orientation++) {
            ProfileModel profile = new ProfileModel(dir);
            profile.orientation = orientation;
            CompatibilityProfileTester.run(context, dir, profile, (progress, message) -> { });
            assertEquals(BuildConfig.HANDHELD_MODE ? 3 : orientation, profile.orientation);
            assertTrue(ProfilesManager.saveConfig(profile));
            assertEquals(profile.orientation, ProfilesManager.loadConfig(dir).orientation);
        }
    }
}
