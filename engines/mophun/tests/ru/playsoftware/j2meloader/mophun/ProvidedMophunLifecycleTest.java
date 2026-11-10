// SPDX-License-Identifier: GPL-3.0-only
package ru.playsoftware.j2meloader.mophun;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.SystemClock;
import android.view.KeyEvent;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BooleanSupplier;
import org.junit.Test;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.util.FileUtils;
import static org.junit.Assert.*;
import static org.junit.Assume.*;

/** Opt-in permitted game, isolated library/profile/saves; no game data in the test APK. */
public class ProvidedMophunLifecycleTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context context = instrumentation.getTargetContext();

    @Test public void providedGamePausesReimportsAndRelaunches() throws Exception {
        String name = InstrumentationRegistry.getArguments().getString("mophunFile");
        assumeNotNull(name);
        assertTrue("Private fixture is debug-only", context.getPackageName().endsWith(".debug"));
        assertEquals(new File(name).getName(), name);
        File source = new File(context.getExternalFilesDir(null), "sweep/mophun/" + name);
        assertTrue(source.isFile());
        File root = Files.createTempDirectory(context.getCacheDir().toPath(), "mophun-lifecycle-").toFile();
        File output = new File(context.getExternalFilesDir(null), "sweep/mophun/" + name + "-captures");
        // Do not leave an earlier success report or frames behind after a failed rerun.
        if (output.exists()) FileUtils.deleteDirectory(output);
        assertTrue(output.mkdirs());
        String previous = Config.getEmulatorDir();
        Method init = Config.class.getDeclaredMethod("initDirs", String.class);
        init.setAccessible(true);
        java.util.List<MophunSession> sessions = new java.util.ArrayList<>();
        try {
            init.invoke(null, root.getAbsolutePath());
            String id = MophunStore.install(context, root, Uri.fromFile(source)).getPath();
            Intent intent = new Intent(context, MophunGameActivity.class).putExtra("id", id);
            try (ActivityScenario<MophunGameActivity> scenario = ActivityScenario.launch(intent)) {
                MophunSession session = session(scenario);
                sessions.add(session);
                await("Game must present a frame", () -> session.error() != null ||
                        session.screen() != null && session.screen().presentationSerial() > 0);
                healthy(session);
                SystemClock.sleep(2000); healthy(session); capture(output, "startup");
                int count = Integer.parseInt(InstrumentationRegistry.getArguments().getString("mophunConfirmCount", "3"));
                assertTrue(count >= 0 && count <= 10);
                for (int step = 1; step <= count; step++) {
                    key(scenario, KeyEvent.KEYCODE_BUTTON_A);
                    SystemClock.sleep(1500); healthy(session); capture(output, "step-" + step);
                }
                key(scenario, KeyEvent.KEYCODE_DPAD_RIGHT);
                SystemClock.sleep(500); capture(output, "input");
                scenario.moveToState(Lifecycle.State.CREATED);
                await("Background activity must park the VM", session::parked);
                healthy(session);
                long pausedSerial = session.screen().presentationSerial();
                SystemClock.sleep(350);
                assertEquals("No guest frames while paused", pausedSerial, session.screen().presentationSerial());
                scenario.moveToState(Lifecycle.State.RESUMED);
                await("Foreground activity must resume frames", () -> session.screen().presentationSerial() > pausedSerial);
                healthy(session); capture(output, "resumed");
            }
            // Activity destruction schedules shutdown; join before reading or deleting saves.
            sessions.get(0).close();
            File saves = new File(Config.getDataDir(), id);
            Map<String, byte[]> before = snapshot(saves);
            assertEquals(id, MophunStore.install(context, root, Uri.fromFile(source)).getPath());
            Map<String, byte[]> after = snapshot(saves);
            assertEquals("Reimport must preserve the save tree", before.keySet(), after.keySet());
            for (String path : before.keySet()) assertArrayEquals(path, before.get(path), after.get(path));
            try (ActivityScenario<MophunGameActivity> scenario = ActivityScenario.launch(intent)) {
                MophunSession session = session(scenario);
                sessions.add(session);
                await("Relaunch must present a frame", () -> session.error() != null ||
                        session.screen() != null && session.screen().presentationSerial() > 0);
                healthy(session); SystemClock.sleep(2000); healthy(session); capture(output, "relaunched");
            }
            sessions.get(1).close();
            String report = "Game-generated save files before reimport: " + before.size()
                    + "\nReimport preserved all captured bytes.\nPause/resume and cold activity relaunch passed."
                    + "\nScreenshots require review; this is not complete-game or audible-audio acceptance.\n";
            Files.write(new File(output, "result.txt").toPath(), report.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } finally {
            try {
                for (MophunSession session : sessions) session.close();
            } finally { init.invoke(null, previous); }
            FileUtils.deleteDirectory(root); // Retain the fixture if a worker cannot stop.
        }
    }

    private static MophunSession session(ActivityScenario<MophunGameActivity> scenario) {
        MophunSession[] value = {null};
        scenario.onActivity(activity -> {
            try {
                Field field = MophunGameActivity.class.getDeclaredField("session");
                field.setAccessible(true); value[0] = (MophunSession) field.get(activity);
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        });
        assertNotNull(value[0]); return value[0];
    }

    private static void healthy(MophunSession session) {
        assertNull("Unexpected guest error", session.error());
        assertFalse("Guest must still be running", session.finished());
    }

    private static void await(String message, BooleanSupplier ready) {
        long deadline = SystemClock.uptimeMillis() + 15000;
        while (!ready.getAsBoolean() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50);
        assertTrue(message, ready.getAsBoolean());
    }

    private static void key(ActivityScenario<MophunGameActivity> scenario, int key) {
        scenario.onActivity(activity -> activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, key)));
        SystemClock.sleep(150);
        scenario.onActivity(activity -> activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, key)));
    }

    private static Map<String, byte[]> snapshot(File directory) throws Exception {
        Map<String, byte[]> files = new TreeMap<>();
        assertTrue(directory.isDirectory());
        try (java.util.stream.Stream<java.nio.file.Path> paths = Files.walk(directory.toPath())) {
            for (java.nio.file.Path path : (Iterable<java.nio.file.Path>) paths.filter(Files::isRegularFile)::iterator)
                files.put(directory.toPath().relativize(path).toString(), Files.readAllBytes(path));
        }
        return files;
    }

    private void capture(File directory, String name) throws Exception {
        Bitmap image = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull(image);
        try (FileOutputStream out = new FileOutputStream(new File(directory, name + ".png"))) {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, out));
        } finally { image.recycle(); }
    }
}
