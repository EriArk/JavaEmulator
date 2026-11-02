package javax.microedition.shell;

import android.app.ActivityManager;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Test;
import ru.playsoftware.j2meloader.config.*;
import ru.playsoftware.j2meloader.util.Constants;
import ru.playsoftware.j2meloader.util.FileUtils;
import static org.junit.Assert.*;

/** Real separate MIDlet/restart processes, using only a disposable malformed fixture. */
public class LaunchRetryUiTest {
    @Test public void retryWaitsForNewVmAndDoesNotSaveFailedDraft() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        UiAutomation ui = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        assertEquals("Do not interrupt another Java session", 0, pid(context));
        String id = "launch-failure-" + java.util.UUID.randomUUID();
        File game = new File(Config.getEmulatorDir(), "converted/" + id);
        File configs = new File(Config.getConfigsDir(), id);
        File saves = new File(Config.getDataDir(), id);
        assertTrue(game.mkdirs()); assertTrue(configs.mkdirs()); assertTrue(saves.mkdirs());
        try {
            Files.write(new File(game, Config.MIDLET_MANIFEST_FILE).toPath(),
                    ("MIDlet-Name: Launch recovery fixture\nMIDlet-Vendor: AbyssME tests\nMIDlet-Version: 1.0\n"
                            + "MIDlet-1: Launch recovery fixture,,fixture.Missing\n").getBytes(StandardCharsets.UTF_8));
            ProfileModel profile = new ProfileModel(configs); profile.fpsLimit = 30;
            assertTrue(ProfilesManager.saveConfig(profile));
            byte[] confirmed = Files.readAllBytes(new File(configs, Config.MIDLET_CONFIG_FILE).toPath());
            Files.write(new File(saves, "sentinel").toPath(), new byte[]{6, 2});
            profile.fpsLimit = 45; SettingsTrial.stage(profile);
            context.startActivity(new Intent(context, MicroActivity.class).setData(Uri.parse(game.getPath()))
                    .putExtra(Constants.KEY_MIDLET_NAME, "Launch recovery fixture")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await(ui, "Game could not run");
            int old = pid(context); assertTrue(old > 0);
            assertFalse(new File(configs, "settings-trial.json").exists());
            assertTrue(await(ui, "Retry").performAction(AccessibilityNodeInfo.ACTION_CLICK));
            long deadline = SystemClock.uptimeMillis() + 15000;
            int next;
            do { SystemClock.sleep(100); next = pid(context); }
            while ((next == 0 || next == old) && SystemClock.uptimeMillis() < deadline);
            assertTrue("Retry must replace the broken VM", next > 0 && next != old);
            await(ui, "Game could not run");
            assertArrayEquals(confirmed, Files.readAllBytes(new File(configs, Config.MIDLET_CONFIG_FILE).toPath()));
            assertArrayEquals(new byte[]{6, 2}, Files.readAllBytes(new File(saves, "sentinel").toPath()));
            SystemClock.sleep(400);
            android.graphics.Bitmap bitmap = ui.takeScreenshot(); assertNotNull(bitmap);
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(new File(context.getExternalFilesDir(null), "launch-failure-java.png"))) {
                assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out));
            } finally { bitmap.recycle(); }
            // Exiting kills the VM before accessibility can acknowledge the action.
            await(ui, "Exit game").performAction(AccessibilityNodeInfo.ACTION_CLICK);
            deadline = SystemClock.uptimeMillis() + 5000;
            while (pid(context) != 0 && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100);
            assertEquals(0, pid(context));
        } finally {
            int remaining = pid(context);
            if (remaining > 0) { android.os.Process.killProcess(remaining); SystemClock.sleep(300); }
            FileUtils.deleteDirectory(game); FileUtils.deleteDirectory(configs); FileUtils.deleteDirectory(saves);
        }
    }
    private static int pid(Context context) {
        java.util.List<ActivityManager.RunningAppProcessInfo> processes =
                ((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE)).getRunningAppProcesses();
        if (processes != null) for (ActivityManager.RunningAppProcessInfo process : processes)
            if (process.uid == android.os.Process.myUid() && process.processName.equals(context.getPackageName() + ":midlet"))
                return process.pid;
        return 0;
    }
    private static AccessibilityNodeInfo await(UiAutomation ui, String label) {
        long deadline = SystemClock.uptimeMillis() + 15000;
        do {
            AccessibilityNodeInfo result = find(ui.getRootInActiveWindow(), label);
            if (result != null) return result;
            SystemClock.sleep(100);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("Missing launch UI: " + label);
    }
    private static AccessibilityNodeInfo find(AccessibilityNodeInfo node, String label) {
        if (node == null) return null;
        if (node.isVisibleToUser() && label.contentEquals(node.getText() == null ? "" : node.getText())) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo match = find(node.getChild(i), label); if (match != null) return match;
        }
        return null;
    }
}
