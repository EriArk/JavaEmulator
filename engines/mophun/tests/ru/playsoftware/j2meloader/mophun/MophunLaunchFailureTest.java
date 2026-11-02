package ru.playsoftware.j2meloader.mophun;

import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.nio.file.Files;
import java.lang.reflect.Field;
import org.json.JSONObject;
import org.junit.Test;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.catalog.AdditionalGames;
import ru.playsoftware.j2meloader.config.*;
import ru.playsoftware.j2meloader.input.GameMenuDialog;
import ru.playsoftware.j2meloader.util.FileUtils;
import static org.junit.Assert.*;

public class MophunLaunchFailureTest {
    @Test public void failedWorkerCanRetryWithoutKeepingDraftOrTouchingSaves() throws Exception {
        android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String hash = java.util.UUID.randomUUID().toString().replace("-", ""); hash += hash;
        String id = "mophun-" + hash;
        File directory = MophunStore.directory(new File(Config.getEmulatorDir()), id);
        File configs = new File(Config.getConfigsDir(), id);
        File saves = new File(Config.getDataDir(), id);
        assertTrue(directory.mkdirs()); assertTrue(configs.mkdirs()); assertTrue(saves.mkdirs());
        try {
            Files.write(new File(directory, AdditionalGames.MANIFEST).toPath(),
                    new JSONObject().put("engine", "mophun").put("version", 1).put("sha256", hash)
                            .put("model", 2).put("title", "Launch recovery fixture").toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Files.write(new File(directory, MophunStore.EXECUTABLE).toPath(), new byte[]{1});
            File sentinel = new File(saves, "save-sentinel"); Files.write(sentinel.toPath(), new byte[]{4, 2});
            ProfileModel profile = new ProfileModel(configs); profile.nativeFpsLimit = 30;
            assertTrue(ProfilesManager.saveConfig(profile));
            profile.nativeFpsLimit = 45; SettingsTrial.stage(profile);
            Intent intent = new Intent(context, MophunGameActivity.class).putExtra("id", id).putExtra("externalLaunch", true);
            try (ActivityScenario<MophunGameActivity> scenario = ActivityScenario.launch(intent)) {
                awaitFailure(scenario);
                MophunSession[] old = {null};
                scenario.onActivity(activity -> {
                    assertEquals(45, ((ProfileModel) field(activity, "profile")).getNativeFpsLimit());
                    old[0] = (MophunSession) field(activity, "session");
                    find(menu(activity), activity.getString(R.string.compatibility_test_retry)).performClick();
                });
                long deadline = android.os.SystemClock.uptimeMillis() + 10000;
                boolean[] replaced = {false};
                do {
                    scenario.onActivity(activity -> replaced[0] = field(activity, "session") != old[0]);
                    if (!replaced[0]) Thread.sleep(100);
                } while (!replaced[0] && android.os.SystemClock.uptimeMillis() < deadline);
                assertTrue("Retry must create a new session", replaced[0]);
                assertTrue("Old worker must finish before replacement", old[0].finished());
                awaitFailure(scenario);
                scenario.onActivity(activity -> assertEquals(30, ((ProfileModel) field(activity, "profile")).getNativeFpsLimit()));
                assertEquals(30, ProfilesManager.loadConfig(configs).getNativeFpsLimit());
                assertArrayEquals(new byte[]{4, 2}, Files.readAllBytes(sentinel.toPath()));
                android.graphics.Bitmap image = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
                assertNotNull(image);
                try (java.io.FileOutputStream out = new java.io.FileOutputStream(new File(context.getExternalFilesDir(null), "launch-failure-mophun.png"))) {
                    assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out));
                } finally { image.recycle(); }
                scenario.onActivity(activity -> ((GameMenuDialog) field(activity, "menu")).onBackPressed());
            }
        } finally {
            FileUtils.deleteDirectory(directory); FileUtils.deleteDirectory(configs); FileUtils.deleteDirectory(saves);
        }
    }
    private static void awaitFailure(ActivityScenario<MophunGameActivity> scenario) throws Exception {
        long deadline = android.os.SystemClock.uptimeMillis() + 10000;
        boolean[] shown = {false};
        do {
            scenario.onActivity(activity -> {
                GameMenuDialog dialog = (GameMenuDialog) field(activity, "menu");
                shown[0] = dialog != null && dialog.isShowing()
                        && find(menu(activity), activity.getString(R.string.game_failure_title)) != null;
            });
            if (shown[0]) { Thread.sleep(350); return; }
            Thread.sleep(100);
        } while (android.os.SystemClock.uptimeMillis() < deadline);
        fail("Missing launch failure screen");
    }
    private static Object field(Object target, String name) {
        try { Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target); }
        catch (Exception error) { throw new AssertionError(error); }
    }
    private static View menu(MophunGameActivity activity) {
        return ((GameMenuDialog) field(activity, "menu")).getWindow().getDecorView();
    }
    private static View find(View view, String label) {
        if (view instanceof TextView && label.contentEquals(((TextView) view).getText())) return view;
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
            View match = find(((ViewGroup) view).getChildAt(i), label); if (match != null) return match;
        }
        return null;
    }
}
