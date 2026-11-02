// SPDX-License-Identifier: GPL-3.0-only
package ru.playsoftware.j2meloader.config;

import android.app.ActivityManager;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import androidx.test.platform.app.InstrumentationRegistry;
import com.android.dx.command.dexer.Main;
import java.io.*;
import java.nio.file.Files;
import java.util.UUID;
import java.util.zip.ZipFile;
import javax.microedition.shell.MicroActivity;
import org.junit.Test;
import ru.playsoftware.j2meloader.catalog.SourceHints;
import ru.playsoftware.j2meloader.util.Constants;
import ru.playsoftware.j2meloader.util.FileUtils;
import static org.junit.Assert.*;
import static org.junit.Assume.*;

/** Permitted local game only; disposable conversion, profile and saves, one VM per invocation. */
public class ProvidedJavaSweepTest {
    @Test public void observeProvidedGame() throws Exception {
        Bundle args = InstrumentationRegistry.getArguments(); assumeTrue(args.containsKey("javaGame"));
        Instrumentation i = InstrumentationRegistry.getInstrumentation(); Context context = i.getTargetContext();
        assertEquals("Do not interrupt another Java game", 0, pid(context));
        String name = args.getString("javaGame"); assertEquals(new File(name).getName(), name);
        File source = new File(context.getExternalFilesDir(null), "sweep/java/" + name); assertTrue(source.isFile());
        String id = "sweep-" + UUID.randomUUID();
        File game = new File(Config.getAppDir(), id), profileDir = new File(Config.getConfigsDir(), id);
        File saves = new File(Config.getDataDir(), id);
        File output = new File(context.getExternalFilesDir(null), "sweep/java/" + name + "-captures");
        assertTrue(game.mkdirs()); assertTrue(profileDir.mkdirs()); assertTrue(output.isDirectory() || output.mkdirs());
        try {
            try (ZipFile zip = new ZipFile(source); InputStream manifest = zip.getInputStream(zip.getEntry("META-INF/MANIFEST.MF"))) {
                Files.copy(manifest, new File(game, Config.MIDLET_MANIFEST_FILE).toPath());
            }
            Files.copy(source.toPath(), new File(game, Config.MIDLET_RES_FILE).toPath());
            SourceHints.write(game, name);
            Main.main(new String[]{"--no-optimize", "--core-library", "--output=" + new File(game, Config.MIDLET_DEX_FILE), source.getPath()});
            assertTrue(new File(game, Config.MIDLET_DEX_FILE).isFile());
            ProfileModel profile = new ProfileModel(profileDir);
            CompatibilityProfileTester.run(context, game, profile, (p, message) -> {});
            if (args.containsKey("javaWidth")) {
                profile.screenWidth = Integer.parseInt(args.getString("javaWidth"));
                profile.screenHeight = Integer.parseInt(args.getString("javaHeight"));
            }
            profile.showKeyboard = false; profile.touchInput = true;
            assertTrue(ProfilesManager.saveConfig(profile));
            Bundle status = new Bundle(); status.putString("stream", name + " size=" + profile.screenWidth + "x" + profile.screenHeight
                    + " profile=" + profile.compatibilityProfile + " reasons=" + profile.compatibilityReasons + "\n"); i.sendStatus(0, status);
            context.startActivity(new Intent(context, MicroActivity.class).setData(Uri.parse(game.getPath()))
                    .putExtra(Constants.KEY_MIDLET_NAME, name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            SystemClock.sleep(10000); capture(i, output, "startup");
            int step = 0;
            for (String action : args.getString("javaActions", "").split(";")) {
                if (action.isEmpty()) continue;
                String[] parts = action.split(":"); long now = SystemClock.uptimeMillis();
                if (parts[0].equals("key")) {
                    int key = Integer.parseInt(parts[1]);
                    i.getUiAutomation().injectInputEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, key, 0), true);
                    SystemClock.sleep(150);
                    i.getUiAutomation().injectInputEvent(new KeyEvent(now, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, key, 0), true);
                } else if (parts[0].equals("tap")) {
                    float x = Float.parseFloat(parts[1]), y = Float.parseFloat(parts[2]);
                    MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
                    MotionEvent up = MotionEvent.obtain(now, now+100, MotionEvent.ACTION_UP, x, y, 0);
                    try { i.getUiAutomation().injectInputEvent(down, true); SystemClock.sleep(100); i.getUiAutomation().injectInputEvent(up, true); }
                    finally { down.recycle(); up.recycle(); }
                } else if (parts[0].equals("wait")) SystemClock.sleep(Math.min(15000, Integer.parseInt(parts[1])));
                else throw new IllegalArgumentException(action);
                SystemClock.sleep(1800); capture(i, output, "step-" + (++step));
            }
            SystemClock.sleep(2000); capture(i, output, "final");
        } finally {
            int vm = pid(context); if (vm > 0) { android.os.Process.killProcess(vm); SystemClock.sleep(500); }
            assertEquals("VM must stop before removing its private fixture", 0, pid(context));
            FileUtils.deleteDirectory(game); FileUtils.deleteDirectory(profileDir); FileUtils.deleteDirectory(saves);
        }
    }
    private static int pid(Context context) {
        java.util.List<ActivityManager.RunningAppProcessInfo> processes = ((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE)).getRunningAppProcesses();
        if (processes != null) for (ActivityManager.RunningAppProcessInfo p : processes)
            if (p.uid == android.os.Process.myUid() && p.processName.equals(context.getPackageName() + ":midlet")) return p.pid;
        return 0;
    }
    private static void capture(Instrumentation i, File output, String name) throws IOException {
        Bitmap bitmap = i.getUiAutomation().takeScreenshot(); assertNotNull(bitmap);
        try (FileOutputStream out = new FileOutputStream(new File(output, name + ".png"))) { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)); }
        finally { bitmap.recycle(); }
    }
}
