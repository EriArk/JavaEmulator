// SPDX-License-Identifier: GPL-3.0-only
package io.github.eriark.abyssme.symbian;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Surface;
import android.view.TextureView;
import androidx.test.platform.app.InstrumentationRegistry;
import com.github.eka2l1.emu.Emulator;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import ru.playsoftware.j2meloader.MainActivity;
import static org.junit.Assert.*;
import static org.junit.Assume.*;

/** One opt-in supplied SIS per process. Screenshots are observations, not gameplay assertions. */
public final class ProvidedGameSweepTest {
    private final Instrumentation i = InstrumentationRegistry.getInstrumentation();
    private final Context context = i.getTargetContext();
    private void report(String text) {
        Bundle status = new Bundle(); status.putString("stream", text + "\n"); i.sendStatus(0, status);
    }
    @Test public void installAndObserveOnPrivatePhone() throws Exception {
        Bundle args = InstrumentationRegistry.getArguments();
        assumeTrue(args.containsKey("sweepGame"));
        String name = args.getString("sweepGame");
        assertTrue(name.matches("[a-zA-Z0-9_-]+\\.sisx?"));
        File inputs = new File(context.getExternalFilesDir(null), "sweep");
        File game = new File(inputs, name); assertTrue(game.isFile());
        File output = new File(inputs, name + "-captures"); assertTrue(output.isDirectory() || output.mkdirs());
        File root = Files.createTempDirectory(context.getCacheDir().toPath(), "provided-sweep-").toFile();
        File runtime = new File(root, "runtime");
        Activity[] activity = new Activity[1]; AtomicBoolean attached = new AtomicBoolean();
        try {
            Emulator.stopSession();
            if ("5800".equals(args.getString("sweepPhone"))) {
                SystemFileCatalog catalog = new SystemFileCatalog(new File(root, "systems"));
                SystemFileCatalog.ImportResult result = catalog.addArchive(context, new File(inputs, "5800.zip"), t -> {});
                assertTrue(result.failures.toString(), result.failures.isEmpty()); assertEquals(1, result.entries.size());
                runtime = catalog.runtime(result.entries.get(0));
            } else {
                SystemFileCatalog catalog = new SystemFileCatalog(context);
                SystemFileCatalog.Entry phone = catalog.list().stream()
                        .filter(e -> e.firmware.equals("RM-320")).findFirst().orElse(null);
                assertNotNull("Supply N95 fixture", phone);
                File original = catalog.runtime(phone);
                String[] portable = Emulator.portablePhoneSettings(original.getAbsolutePath());
                try (java.util.stream.Stream<Path> paths = Files.walk(original.toPath())) {
                    for (Path path : (Iterable<Path>) paths::iterator) {
                        Path target = runtime.toPath().resolve(original.toPath().relativize(path));
                        if (Files.isDirectory(path)) Files.createDirectories(target); else Files.copy(path, target);
                    }
                }
                Files.write(new File(runtime, "config.yml").toPath(), portable[0].getBytes(StandardCharsets.UTF_8));
                Files.write(new File(runtime, "data/devices.yml").toPath(), portable[1].getBytes(StandardCharsets.UTF_8));
            }
            report("phone-ready " + name);
            Emulator.prepare(runtime.getAbsolutePath());
            String[] before = Emulator.getCatalogApps();
            Set<String> old = new HashSet<>(Arrays.asList(before));
            int installed = Emulator.installApp(game.getAbsolutePath());
            report("install-result=" + installed); assertEquals(0, installed);
            Emulator.prepareGame(runtime.getAbsolutePath(), Integer.parseInt(args.getString("sweepPreset", "0")));
            String[] apps = Emulator.getCatalogApps(); report("catalog=" + Arrays.toString(apps));
            long uid = 0;
            for (int n = 0; !args.containsKey("sweepUid") && n + 1 < apps.length; n += 2) if (!old.contains(apps[n])) {
                assertEquals("One new app required; pass sweepUid if ambiguous", 0, uid);
                uid = Long.parseLong(apps[n]);
            }
            if (args.containsKey("sweepUid")) uid = Long.parseLong(args.getString("sweepUid"));
            assertNotEquals("Installer must register a launchable app", 0, uid);
            assertTrue(Emulator.startSession()); Emulator.lastGuestExit = null;
            activity[0] = i.startActivitySync(new Intent(context, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            i.runOnMainSync(() -> {
                TextureView view = new TextureView(activity[0]);
                view.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                    private Surface surface;
                    public void onSurfaceTextureAvailable(SurfaceTexture t, int w, int h) {
                        surface = new Surface(t); onSurfaceTextureSizeChanged(t, w, h);
                    }
                    public void onSurfaceTextureSizeChanged(SurfaceTexture t, int w, int h) {
                        Emulator.setScreenParams(Color.BLACK, 100, 1, 2, "", 0, true);
                        Emulator.surfaceChanged(surface, w, h); attached.set(true); Emulator.pauseSession(false);
                    }
                    public boolean onSurfaceTextureDestroyed(SurfaceTexture t) {
                        if (attached.getAndSet(false)) Emulator.surfaceDestroyed();
                        if (surface != null) surface.release(); return true;
                    }
                    public void onSurfaceTextureUpdated(SurfaceTexture t) { }
                }); activity[0].setContentView(view);
            });
            long end = SystemClock.uptimeMillis() + 10000;
            while (!attached.get() && SystemClock.uptimeMillis() < end) SystemClock.sleep(50);
            assertTrue(attached.get()); assertTrue(Emulator.launchGame((int) uid));
            SystemClock.sleep(8000); capture(output, "startup");
            String actions = args.getString("sweepActions", "");
            int step = 0;
            for (String action : actions.split(";")) {
                if (action.isEmpty()) continue;
                String[] parts = action.split(":");
                if (parts[0].equals("key")) {
                    int key = Integer.parseInt(parts[1]); Emulator.pressKey(key, 0);
                    SystemClock.sleep(150); Emulator.pressKey(key, 1);
                } else if (parts[0].equals("tap")) {
                    int[] b = Emulator.touchBounds(); assertNotNull(b);
                    int x = b[0] + (b[2]-b[0]) * Integer.parseInt(parts[1])/100;
                    int y = b[1] + (b[3]-b[1]) * Integer.parseInt(parts[2])/100;
                    Emulator.touchScreen(x, y, 100, 0, 0); SystemClock.sleep(100); Emulator.touchScreen(x, y, 0, 2, 0);
                } else if (parts[0].equals("wait")) SystemClock.sleep(Math.min(15000, Integer.parseInt(parts[1])));
                else throw new IllegalArgumentException(action);
                SystemClock.sleep(1500); capture(output, "step-" + (++step));
            }
            report("screen=" + Arrays.toString(Emulator.guestScreenInfo()) + " exit=" + Emulator.lastGuestExit);
            SystemClock.sleep(3000); capture(output, "final");
            android.graphics.Bitmap hostFrame = i.getUiAutomation().takeScreenshot();
            assertNotNull(hostFrame);
            try (java.io.FileOutputStream file = new java.io.FileOutputStream(new File(output, "host-final.png"))) {
                assertTrue(hostFrame.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, file));
            } finally { hostFrame.recycle(); }
        } finally {
            i.runOnMainSync(() -> {
                if (attached.getAndSet(false)) Emulator.surfaceDestroyed();
                if (activity[0] != null) activity[0].finish();
            });
            i.waitForIdleSync(); Emulator.stopSession(); PhoneBackupTest.remove(root);
        }
    }
    private void capture(File folder, String name) {
        assertTrue(Emulator.saveScreenshotTo(new File(folder, name + ".png").getAbsolutePath()));
        report("capture=" + name + " exit=" + Emulator.lastGuestExit);
    }
}
