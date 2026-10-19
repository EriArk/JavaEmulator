package io.github.eriark.abyssme.symbian;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.util.*;
import org.junit.*;
import ru.playsoftware.j2meloader.BuildConfig;
import ru.playsoftware.j2meloader.MainActivity;
import ru.playsoftware.j2meloader.config.ProfilesManager;
import static org.junit.Assert.*;

public final class SymbianSessionUiTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context context = instrumentation.getTargetContext();
    private UiAutomation ui;
    @Test public void fastForwardUsesSharedInputAndRestoresNativeHostOnFocusLoss() throws Exception {
        MainActivity activity = (MainActivity) instrumentation.startActivitySync(new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        String seed = UUID.randomUUID().toString().replace("-", ""), system = seed + seed;
        SymbianSessionUi[] current = {null}; int[] speed = {0}, frames = {0}; List<Boolean> keys = new ArrayList<>();
        try {
            instrumentation.runOnMainSync(() -> {
                try {
                    current[0] = new SymbianSessionUi(activity,new TextureView(activity),system,1,"Fast-forward",new SymbianSessionUi.Host() {
                        public android.graphics.RectF touchBounds() { return null; }
                        public void touch(int x,int y,int pressure,int action,int pointer) { }
                        public void key(int scan,boolean down) { keys.add(down); }
                        public void pause(boolean value) { }
                        public void display(int scale) { }
                        public void speed(int value) { speed[0] = value; }
                        public void frameRate(int value) { frames[0] = value; }
                        public void leave() { } public void restart() { } public Bitmap capture() { return null; }
                    });
                    activity.setContentView(current[0].root);
                    current[0].profile.emulationSpeed = 75;
                    current[0].profile.fastForwardSpeed = 400;
                    current[0].profile.fastForwardButton = KeyEvent.KEYCODE_BUTTON_R1;
                    current[0].profile.nativeFpsLimit = 30;
                    current[0].apply();
                    current[0].key(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_R1));
                    assertEquals(400,speed[0]); assertEquals(120,frames[0]); assertTrue(keys.isEmpty());
                    current[0].key(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_R1));
                    assertEquals(75,speed[0]); assertEquals(30,frames[0]);
                    current[0].profile.fastForwardToggle = true; current[0].apply();
                    current[0].key(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_R1));
                    current[0].key(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_R1));
                    assertEquals(400,speed[0]); current[0].focused(false);
                    assertEquals(75,speed[0]); assertEquals(30,frames[0]);
                    assertEquals(75,current[0].profile.emulationSpeed);
                } catch (Exception error) { throw new AssertionError(error); }
            });
        } finally {
            instrumentation.runOnMainSync(() -> { if (current[0] != null) current[0].close(); activity.finish(); });
            ru.playsoftware.j2meloader.util.FileUtils.deleteDirectory(PhoneBackup.profiles(context.getFilesDir(),system));
        }
    }
    @Test public void javaGameUsesCommonMenuWhenFixtureProvided() throws Exception {
        commonEngineMenu("javaMenuUri", "java-common-menu.png");
    }
    @Test public void mophunGameUsesCommonMenuWhenFixtureProvided() throws Exception {
        commonEngineMenu("mophunMenuUri", "mophun-common-menu.png");
    }
    private void commonEngineMenu(String argument, String screenshot) throws Exception {
        String uri = InstrumentationRegistry.getArguments().getString(argument);
        Assume.assumeNotNull(uri);
        ui = instrumentation.getUiAutomation();
        context.startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri), context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        boolean controllerMenu = BuildConfig.HANDHELD_MODE;
        if (controllerMenu) { SystemClock.sleep(5000); key(KeyEvent.KEYCODE_BACK, 80); }
        else { waitNode("Game menu", 20000); SystemClock.sleep(2500); click("Game menu"); }
        waitNode("Resume", 5000);
        for (String label : new String[]{"Display", "Controls", "Screenshot", "More", "Exit game"})
            assertFalse(label, ui.getRootInActiveWindow().findAccessibilityNodeInfosByText(label).isEmpty());
        capture(screenshot);
        click("More"); click("Back"); waitNode("Resume", 5000);
        if ("true".equals(InstrumentationRegistry.getArguments().getString("fastForwardTrials"))) fastForwardTrials(screenshot);
        if ("true".equals(InstrumentationRegistry.getArguments().getString("unifiedControlsTrials"))) {
            for (String visibility : new String[]{"Always", "Off", "Auto"}) {
                click("Controls"); capture(screenshot + "-controls.png");
                click("On-screen buttons"); click(visibility);
                click("Try changes"); waitNode("Undo", 5000); SystemClock.sleep(1000);
                assertPublishedGamePicture(1);
                capture(screenshot + "-buttons-" + visibility + ".png");
                click("Undo"); click("Game menu"); waitNode("Resume", 5000);
            }
        }
        if ("true".equals(InstrumentationRegistry.getArguments().getString("speedTrials"))) speedTrials(screenshot, controllerMenu);
        if ("true".equals(InstrumentationRegistry.getArguments().getString("presentationTrials")))
            presentationTrials(screenshot, controllerMenu);
        if ("javaMenuUri".equals(argument)
                && "true".equals(InstrumentationRegistry.getArguments().getString("resizeReturnTrials")))
            resizeReturnTrials(controllerMenu);
        if ("mophunMenuUri".equals(argument)
                && "true".equals(InstrumentationRegistry.getArguments().getString("frameRateTrials"))) {
            for (int fps : new int[]{15, 30, 0, 60}) {
                click("More"); clickPrefix("Frame rate limit:"); click("Frame rate limit");
                click(fps == 0 ? "Unlimited" : fps + " FPS");
                capture("mophun-frame-rate-" + fps + "-menu.png");
                click("Try changes"); waitNode("Undo", 5000);
                final ru.playsoftware.j2meloader.mophun.MophunSession[] session = new ru.playsoftware.j2meloader.mophun.MophunSession[1];
                instrumentation.runOnMainSync(() -> {
                    for (Activity activity : androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                            .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)) {
                        if (!(activity instanceof ru.playsoftware.j2meloader.mophun.MophunGameActivity)) continue;
                        try {
                            java.lang.reflect.Field field = activity.getClass().getDeclaredField("session");
                            field.setAccessible(true); session[0] = (ru.playsoftware.j2meloader.mophun.MophunSession)field.get(activity);
                        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
                    }
                });
                assertNotNull(session[0]); assertEquals(fps, session[0].getFrameRateLimit());
                SystemClock.sleep(200);
                int before = session[0].screen().flips; long start = SystemClock.elapsedRealtime();
                SystemClock.sleep(1500);
                int frames = session[0].screen().flips - before;
                long elapsed = SystemClock.elapsedRealtime() - start;
                android.util.Log.i("AbyssFpsTest", "Mophun limit=" + fps + " frames=" + frames + " elapsedMs=" + elapsed);
                assertNull(session[0].error()); assertTrue("Game must keep presenting", frames > 0);
                if (fps > 0) assertTrue("Actual guest cap", frames <= fps * elapsed / 1000 + 3);
                capture("mophun-frame-rate-" + fps + "-game.png");
                click("Undo");
                if (controllerMenu) key(KeyEvent.KEYCODE_BACK,80); else click("Game menu");
                waitNode("Resume",5000);
            }
        }
        if ("javaMenuUri".equals(argument)
                && "true".equals(InstrumentationRegistry.getArguments().getString("javaDisplayTrials"))) {
            for (String size : new String[]{"128 x 160", "480 x 800", "240 x 320"}) {
                click("More"); click("Screen presets"); click("Phone resolution"); clickScrollingChoice(size);
                click("Try changes"); waitNode("Undo", 5000); SystemClock.sleep(1000);
                capture("java-trial-" + size.replace(" ", "") + ".png"); click("Undo");
                if (controllerMenu) key(KeyEvent.KEYCODE_BACK, 80); else click("Game menu");
                waitNode("Resume", 5000);
            }
            click("Restart game"); click("Restart"); SystemClock.sleep(8000);
            capture("java-trial-restarted.png");
            if (controllerMenu) key(KeyEvent.KEYCODE_BACK, 80); else click("Game menu");
            waitNode("Resume", 5000);
        }
        click("Exit game"); waitNode("Leave without saving", 5000); click("Resume");
        if (controllerMenu) key(KeyEvent.KEYCODE_BACK, 80); else click("Game menu");
        click("Exit game"); click("Leave without saving");
        SystemClock.sleep(1000);
    }
    private void resizeReturnTrials(boolean controllerMenu) throws Exception {
        ActivityManager.AppTask gameTask = null;
        for (ActivityManager.AppTask task : context.getSystemService(ActivityManager.class).getAppTasks()) {
            ComponentName top = task.getTaskInfo().topActivity;
            if (top != null && top.getClassName().equals("javax.microedition.shell.MicroActivity")) gameTask = task;
        }
        assertNotNull("Existing Java game task", gameTask);
        for (String size : new String[]{"128 x 160", "480 x 800", "320 x 240", "240 x 320"}) {
            click("More"); click("Screen presets"); click("Phone resolution"); clickScrollingChoice(size);
            click("Try changes"); waitNode("Undo", 5000); SystemClock.sleep(1000);
            capture("java-resize-trial-" + size.replace(" ", "") + ".png");
            assertPublishedGamePicture(1);
            key(KeyEvent.KEYCODE_HOME, 80); SystemClock.sleep(1000);
            gameTask.moveToFront(); waitNode("Game menu", 10000); SystemClock.sleep(1000);
            assertPublishedGamePicture(1);
            capture("java-resize-return-" + size.replace(" ", "") + ".png");
            click("Undo"); SystemClock.sleep(1000); assertPublishedGamePicture(1);
            if (controllerMenu) key(KeyEvent.KEYCODE_BACK, 80); else click("Game menu");
            waitNode("Resume", 5000);
        }
    }

    private void fastForwardTrials(String engine) throws Exception {
        click("More"); click("Fast-forward settings");
        click("Target speed"); click("400% (4x)");
        click("Controller button"); clickScrollingChoice("R1");
        click("Controller mode"); click("Hold");
        capture(engine + "-fast-forward-settings.png");
        click("Try changes"); waitNode("Undo",5000); SystemClock.sleep(500);
        long now = SystemClock.uptimeMillis();
        KeyEvent down = new KeyEvent(now,now,KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_R1,0,0,0,0,0,InputDevice.SOURCE_GAMEPAD);
        KeyEvent up = new KeyEvent(now,now+200,KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_R1,0,0,0,0,0,InputDevice.SOURCE_GAMEPAD);
        try {
            assertTrue(ui.injectInputEvent(down,true)); waitNode("Stop fast-forward",3000);
            capture(engine + "-fast-forward-held.png");
        } finally { ui.injectInputEvent(up,true); }
        SystemClock.sleep(200);
        assertNull(description(ui.getRootInActiveWindow(),"Stop fast-forward"));
        click("Game menu"); click("Fast-forward 400%"); waitNode("Stop fast-forward",3000);
        SystemClock.sleep(5500); waitNode("Stop fast-forward",1000);
        capture(engine + "-fast-forward-active.png");
        click("Stop fast-forward");
        click("Undo"); click("Game menu"); waitNode("Resume",5000);
    }
    @Test public void nativeFastForwardWhenFixtureProvided() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("fastForwardTrials")));
        SymbianLibraryIndex.Entry game = new SymbianLibraryIndex(context).read().stream()
                .filter(e -> e.title.equals("Snakes")).findFirst().orElse(null);
        Assume.assumeNotNull(game); ui = instrumentation.getUiAutomation();
        context.startActivity(new Intent(context,SymbianActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(SymbianLibraryIndex.SYSTEM_EXTRA,game.system).putExtra(SymbianLibraryIndex.UID_EXTRA,game.uid));
        waitNode("Game menu",20000); SystemClock.sleep(4000); click("Game menu");
        try { fastForwardTrials("snakes"); }
        catch (Throwable failure) { capture("snakes-fast-forward-failure.png"); throw failure; }
        finally {
            instrumentation.runOnMainSync(() -> {
                for (Activity activity : new ArrayList<>(androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)))
                    if (activity instanceof SymbianActivity) activity.finish();
            });
        }
    }
    private void presentationTrials(String engine, boolean controllerMenu) throws Exception {
        for (int rotation : new int[]{90, 180, 270, 0}) {
            click("Display"); click("Picture size"); click(rotation == 180 ? "Stretch" : "Fit");
            click("Image rotation"); click(rotation + "\u00b0");
            click("Smoothing");
            if (!BuildConfig.HANDHELD_MODE) {
                click("Orientation"); click(rotation == 90 || rotation == 270 ? "Landscape" : "Portrait");
            }
            click("Try changes"); waitNode("Undo", 5000); SystemClock.sleep(1500);
            capture(engine + "-presentation-" + rotation + ".png");
            assertPublishedGamePicture(1);
            click("Undo"); SystemClock.sleep(500);
            assertPublishedGamePicture(1);
            if (controllerMenu) key(KeyEvent.KEYCODE_BACK, 80); else click("Game menu");
            waitNode("Resume", 5000);
        }
    }
    @Test public void disconnectWhileHiddenStaysPausedAfterReturnAndReconnection() throws Exception {
        ui = instrumentation.getUiAutomation();
        MainActivity activity = (MainActivity) instrumentation.startActivitySync(new Intent(context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        String seed = UUID.randomUUID().toString().replace("-", ""), system = seed + seed;
        SymbianSessionUi[] current = new SymbianSessionUi[1];
        boolean[] paused = {false};
        List<Boolean> keys = new ArrayList<>();
        SymbianSessionUi.Host host = new SymbianSessionUi.Host() {
            public android.graphics.RectF touchBounds() { return null; }
            public void touch(int x,int y,int pressure,int action,int pointer) {}
            public void key(int scan,boolean down) { keys.add(down); }
            public void pause(boolean value) { paused[0] = value; }
            public void display(int scale) {} public void frameRate(int fps) {}
            public void leave() {} public void restart() {} public Bitmap capture() { return null; }
        };
        try {
            instrumentation.runOnMainSync(() -> {
                try {
                    current[0] = new SymbianSessionUi(activity, new TextureView(activity), system, 1, "Controller test", host);
                    activity.setContentView(current[0].root);
                    java.lang.reflect.Field field = SymbianSessionUi.class.getDeclaredField("devices"); field.setAccessible(true);
                    ru.playsoftware.j2meloader.input.DeviceControls devices =
                            (ru.playsoftware.j2meloader.input.DeviceControls) field.get(current[0]);
                    java.lang.reflect.Method update = devices.getClass().getDeclaredMethod("update", int.class, boolean.class);
                    update.setAccessible(true); update.invoke(devices, 90001, true);
                    current[0].key(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A));
                    assertEquals(Collections.singletonList(true), keys);
                    current[0].visible(false);
                    assertEquals(Arrays.asList(true, false), keys);
                    update.invoke(devices, 90001, false); update.invoke(devices, 90001, true);
                    current[0].visible(true); current[0].focused(true);
                    assertTrue(paused[0]); assertTrue(devices.resumeRequired());
                } catch (Exception error) { throw new AssertionError(error); }
            });
            waitNode("Resume", 5000); capture("unified-controller-reconnect.png"); click("Resume");
            instrumentation.runOnMainSync(() -> {
                assertFalse(paused[0]);
                current[0].key(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BUTTON_A));
                current[0].key(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_A));
                assertEquals(Arrays.asList(true, false, true, false), keys);
            });
        } finally {
            instrumentation.runOnMainSync(() -> { if (current[0] != null) current[0].close(); activity.finish(); });
            File directory = PhoneBackup.profiles(context.getFilesDir(), system);
            if (directory.isDirectory()) try (java.util.stream.Stream<java.nio.file.Path> paths = java.nio.file.Files.walk(directory.toPath())) {
                for (java.nio.file.Path file : (Iterable<java.nio.file.Path>) paths.sorted(Comparator.reverseOrder())::iterator)
                    java.nio.file.Files.delete(file);
            }
        }
    }

    @Test public void settingsArePerPhoneAndGameAndSelectDoesNotLeakIntoMapping() throws Exception {
        ui = instrumentation.getUiAutomation();
        MainActivity activity=(MainActivity)instrumentation.startActivitySync(new Intent(context,MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        String seed=UUID.randomUUID().toString().replace("-",""); String system=seed+seed;
        String other=new StringBuilder(system).reverse().toString();
        List<String> events=new ArrayList<>();
        java.util.concurrent.atomic.AtomicInteger pauses=new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger pausesAtRestart=new java.util.concurrent.atomic.AtomicInteger();
        SymbianSessionUi[] current=new SymbianSessionUi[1];
        SymbianSessionUi.Host host=new SymbianSessionUi.Host() {
            public android.graphics.RectF touchBounds(){return null;}
            public void touch(int x,int y,int pressure,int action,int pointer){}
            public void key(int scan,boolean down){events.add(scan+":"+down);}
            public void pause(boolean paused){pauses.incrementAndGet();}
            public void display(int scale){}
            public void frameRate(int fps){com.github.eka2l1.emu.Emulator.setFrameRateLimit(fps);}
            public void speed(int percent){com.github.eka2l1.emu.Emulator.setEmulationSpeed(percent);}
            public void leave(){}
            public void restart(){events.add("restart");current[0].close();pausesAtRestart.set(pauses.get());}
            public Bitmap capture(){return null;}
        };
        try {
            instrumentation.runOnMainSync(() -> {
                try {
                    current[0]=new SymbianSessionUi(activity,new TextureView(activity),system,1,"UI fixture",host);
                    activity.setContentView(current[0].root);
                    current[0].profile.screenRotation=180;
                    current[0].profile.nativeFpsLimit=15;
                    current[0].profile.emulationSpeed=75;
                    current[0].apply();
                    assertEquals(75,com.github.eka2l1.emu.Emulator.getEmulationSpeed());
                    assertTrue(ProfilesManager.saveConfig(current[0].profile));
                    current[0].key(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_SELECT));
                    current[0].key(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_SELECT));
                } catch(IOException error){throw new AssertionError(error);}
            });
            SystemClock.sleep(150);
            instrumentation.runOnMainSync(() -> {
                assertEquals(Arrays.asList("164:true","164:false"),events); events.clear();
                current[0].key(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_SELECT));
            });
            SystemClock.sleep(600); waitNode("Use mapping",5000); click("Cancel");
            instrumentation.runOnMainSync(() -> {
                current[0].key(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_SELECT));
                assertTrue("Held Select is menu-only",events.isEmpty());
                assertFalse("Cancel must not start preview",ProfilesManager.isPreview(current[0].profile));
                current[0].close();
                try {
                    current[0]=new SymbianSessionUi(activity,new TextureView(activity),system,2,"Second game",host);
                    assertEquals(60,com.github.eka2l1.emu.Emulator.getFrameRateLimit());
                    assertEquals(100,com.github.eka2l1.emu.Emulator.getEmulationSpeed());
                    assertEquals(0,current[0].profile.screenRotation); current[0].close();
                    current[0]=new SymbianSessionUi(activity,new TextureView(activity),other,1,"Other phone",host);
                    assertEquals(60,com.github.eka2l1.emu.Emulator.getFrameRateLimit());
                    assertEquals(0,current[0].profile.screenRotation); current[0].close();
                    current[0]=new SymbianSessionUi(activity,new TextureView(activity),system,1,"Saved game",host);
                    assertEquals(15,com.github.eka2l1.emu.Emulator.getFrameRateLimit());
                    assertEquals(180,current[0].profile.screenRotation);
                } catch(IOException error){throw new AssertionError(error);}
            });
            instrumentation.runOnMainSync(() -> {
                activity.setContentView(current[0].root);
                current[0].showMenu();
            });
            click("More"); clickPrefix("Frame rate limit:"); click("Frame rate limit"); click("30 FPS");
            capture("symbian-frame-rate-menu.png"); click("Try changes"); waitNode("Undo",5000);
            assertEquals(30,com.github.eka2l1.emu.Emulator.getFrameRateLimit());
            click("Undo"); assertEquals(15,com.github.eka2l1.emu.Emulator.getFrameRateLimit());
            instrumentation.runOnMainSync(() -> current[0].showMenu());
            click("Display"); click("Frame rate limit"); click("Unlimited"); click("Try changes");
            waitNode("Keep",5000); assertEquals(0,com.github.eka2l1.emu.Emulator.getFrameRateLimit());
            click("Keep");
            assertEquals(0,ProfilesManager.loadConfig(current[0].profile.dir).getNativeFpsLimit());
            instrumentation.runOnMainSync(() -> current[0].showMenu());
            click("Restart game"); click("Cancel");
            assertFalse(events.contains("restart"));
            instrumentation.runOnMainSync(() -> {
                ProfilesManager.beginPreview(current[0].profile);
                current[0].profile.screenRotation=90;
            });
            click("Restart game"); click("Restart");
            instrumentation.runOnMainSync(() -> {
                assertTrue(events.contains("restart"));
                assertEquals(180,current[0].profile.screenRotation);
                assertFalse(ProfilesManager.isPreview(current[0].profile));
            });
            instrumentation.waitForIdleSync(); SystemClock.sleep(200);
            assertEquals("Closed menu must not pause a replacement session",pausesAtRestart.get(),pauses.get());
        } finally {
            instrumentation.runOnMainSync(() -> {if(current[0]!=null)current[0].close();activity.finish();});
            for(String id:new String[]{system,other}) {
                java.nio.file.Path root=new File(context.getFilesDir(),"symbian-profiles/"+id).toPath();
                if(java.nio.file.Files.exists(root)) try(java.util.stream.Stream<java.nio.file.Path> paths=java.nio.file.Files.walk(root)) {
                    for(java.nio.file.Path file:(Iterable<java.nio.file.Path>)paths.sorted(Comparator.reverseOrder())::iterator) java.nio.file.Files.delete(file);
                }
            }
        }
    }
    @Test public void nativeGameUsesSharedDisplayMapperAndPhoneControls() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("sessionUi")));
        SymbianLibraryIndex.Entry game = new SymbianLibraryIndex(context).read().stream()
                .filter(e -> e.title.equals("Snakes")).findFirst().orElse(null);
        Assume.assumeNotNull(game);
        ui = instrumentation.getUiAutomation();
        context.startActivity(new Intent(context,SymbianActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(SymbianLibraryIndex.SYSTEM_EXTRA,game.system).putExtra(SymbianLibraryIndex.UID_EXTRA,game.uid));
        waitNode("Game menu",20000); SystemClock.sleep(8000);
        capture("symbian-common-game.png");
        key(KeyEvent.KEYCODE_BUTTON_SELECT,600);
        waitNode("Use mapping",5000); capture("symbian-common-mapper.png"); click("Cancel");
        click("Game menu"); waitNode("Resume",5000);
        capture("symbian-common-menu.png");
        speedTrials("symbian", false);
        for (int fps : new int[]{15, 30, 0, 60}) {
            click("More"); clickPrefix("Frame rate limit:"); click("Frame rate limit");
            click(fps == 0 ? "Unlimited" : fps + " FPS");
            capture("symbian-frame-rate-" + fps + "-menu.png");
            click("Try changes"); waitNode("Undo",5000);
            // SymbianActivity runs in :symbian; a JNI getter here would read a
            // different process. The in-process host test verifies JNI readback.
            SystemClock.sleep(1500); capture("symbian-frame-rate-" + fps + "-game.png");
            click("Game menu"); click("More");
            assertNotNull(find(ui.getRootInActiveWindow(), "Frame rate limit: "
                    + (fps == 0 ? "Unlimited" : fps + " FPS"), false));
            click("Back"); click("Resume"); click("Undo");
            click("Game menu"); waitNode("Resume",5000);
        }
        click("Display"); waitNode("Picture size",5000);
        assertTrue(ui.getRootInActiveWindow().findAccessibilityNodeInfosByText("Smoothing").isEmpty());
        click("Image rotation"); click("180\u00b0");
        if (!BuildConfig.HANDHELD_MODE) { click("Orientation"); click("Portrait"); }
        capture("symbian-common-display.png"); click("Try changes");
        waitNode("Undo",5000); SystemClock.sleep(1200); capture("symbian-common-trial.png"); click("Undo");
        if (!BuildConfig.HANDHELD_MODE) {
            click("Game menu"); click("Display"); click("Orientation"); click("Portrait"); click("Try changes");
            waitNode("Undo",5000); SystemClock.sleep(1200); capture("symbian-common-phone-portrait.png");
        }
        click("Game menu"); click("Controls");
        capture("symbian-common-controls.png");
        if (!BuildConfig.HANDHELD_MODE) {
            click("On-screen buttons"); click("Always");
            click("Touch layout"); click("Gamepad"); click("Try changes");
            waitNode("Undo",5000); SystemClock.sleep(1000); capture("symbian-common-gamepad-portrait.png");
            click("Game menu"); click("Display"); click("Orientation"); click("Landscape"); click("Try changes");
            waitNode("Undo",5000); SystemClock.sleep(1000); capture("symbian-common-gamepad-landscape.png"); click("Undo");
        } else {
            click("Back"); waitNode("Resume",5000);
            click("Resume");
        }
        assertPublishedGamePicture();
        click("Game menu"); click("Exit game"); click("Leave without saving");
        SystemClock.sleep(1000);
    }
    private void assertPublishedGamePicture() throws Exception {
        assertPublishedGamePicture(8);
    }

    private void speedTrials(String engine, boolean controllerMenu) throws Exception {
        for (int speed : new int[]{50,200,100}) {
            click("More"); clickPrefix("Emulation speed:"); click("Emulation speed");
            click(speed == 100 ? "100% (Normal)" : speed + "%");
            capture(engine + "-speed-" + speed + "-menu.png");
            click("Try changes"); waitNode("Undo",5000);
            SystemClock.sleep(2000); capture(engine + "-speed-" + speed + "-game.png");
            if (controllerMenu) key(KeyEvent.KEYCODE_BACK,80); else click("Game menu");
            click("More"); assertNotNull(find(ui.getRootInActiveWindow(), "Emulation speed: " + speed + "%", false));
            click("Back"); click("Resume"); click("Undo");
            if (controllerMenu) key(KeyEvent.KEYCODE_BACK,80); else click("Game menu");
            waitNode("Resume",5000);
        }
    }
    private void assertPublishedGamePicture(int minimumColors) throws Exception {
        Set<Long> before=screenshots();
        click("Game menu"); waitNode("Resume",5000);
        AccessibilityNodeInfo captureAction=null;
        for(AccessibilityNodeInfo node:ui.getRootInActiveWindow().findAccessibilityNodeInfosByText("Screenshot"))
            if(node.getText()!=null && "Screenshot".contentEquals(node.getText()))captureAction=node;
        assertNotNull("Screenshot command in the pause dialog",captureAction); clickNode(captureAction);
        long deadline=SystemClock.uptimeMillis()+5000; Set<Long> added;
        do {added=screenshots();added.removeAll(before);if(added.isEmpty())SystemClock.sleep(100);} while(added.isEmpty() && SystemClock.uptimeMillis()<deadline);
        if(added.isEmpty())capture("symbian-failed-capture.png");
        assertEquals("One published game screenshot",1,added.size());
        android.net.Uri picture=android.content.ContentUris.withAppendedId(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,added.iterator().next());
        try(InputStream stream=context.getContentResolver().openInputStream(picture)) {
            Bitmap bitmap=android.graphics.BitmapFactory.decodeStream(stream); assertNotNull(bitmap);
            try {
                Set<Integer> colors=new HashSet<>();
                for(int y=0;y<bitmap.getHeight();y+=8)for(int x=0;x<bitmap.getWidth();x+=8)colors.add(bitmap.getPixel(x,y));
                assertTrue("Game screenshot must contain the rendered picture",colors.size()>minimumColors);
            } finally {bitmap.recycle();}
        }
    }
    @Test public void nativeGameSurvivesRepeatedRestartAndBackgroundReturn() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("sessionUi")));
        SymbianLibraryIndex.Entry game = new SymbianLibraryIndex(context).read().stream()
                .filter(e -> e.title.equals("Snakes")).findFirst().orElse(null);
        Assume.assumeNotNull(game);
        ui = instrumentation.getUiAutomation();
        Intent launch = new Intent(context, SymbianActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(SymbianLibraryIndex.SYSTEM_EXTRA, game.system)
                .putExtra(SymbianLibraryIndex.UID_EXTRA, game.uid);
        context.startActivity(launch);
        waitNode("Game menu", 20000);
        SystemClock.sleep(8000);
        try {
            // Startup logos can be monochrome; count guest pixels, not Android chrome.
            assertPublishedGamePicture(1);
            for (int attempt = 0; attempt < 3; attempt++) {
                click("Game menu"); click("Restart game"); click("Restart");
                waitNode("Game menu", 20000);
                SystemClock.sleep(8000);
                assertPublishedGamePicture(1);
                capture("symbian-restart-" + attempt + ".png");
                key(KeyEvent.KEYCODE_HOME, 80);
                SystemClock.sleep(1000);
                context.startActivity(new Intent(context, SymbianActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
                waitNode("Game menu", 10000);
                SystemClock.sleep(1000);
                assertPublishedGamePicture(1);
                click("Game menu"); waitNode("Resume", 5000); click("Resume");
            }
            capture("symbian-restart-resumed.png");
        } finally {
            click("Game menu"); click("Exit game"); click("Leave without saving");
            SystemClock.sleep(1000);
        }
    }
    @Test public void provisionPrivatePhoneCardFixtureWhenExplicitlyRequested() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("phoneCardFixture")));
        File inputs=new File(context.getExternalFilesDir(null),"symbian-input");
        SystemFileCatalog catalog=new SystemFileCatalog(context);
        try {
            File phoneArchive=new File(inputs,"ngage.zip");
            SystemFileCatalog.ImportResult imported=catalog.addArchive(context,phoneArchive,t -> {});
            assertEquals(imported.summary(),1,imported.entries.size());
            SystemFileCatalog.Entry phone=imported.entries.get(0);
            File runtime=catalog.runtime(phone);
            com.github.eka2l1.emu.Emulator.prepare(runtime.getAbsolutePath());
            try(GameImport card=GameImport.inspect(new File(inputs,"snakes.zip"))) {
                assertEquals(0,com.github.eka2l1.emu.Emulator.installNGageGame(card.file.getAbsolutePath()));
                com.github.eka2l1.emu.Emulator.prepare(runtime.getAbsolutePath());
                catalog.activate(phone); catalog.bind(card.hash,phone);
                new SymbianLibraryIndex(context).publish(phone,com.github.eka2l1.emu.Emulator.getLibraryApps(),true);
            }
        } finally {com.github.eka2l1.emu.Emulator.stopSession();}
    }
    @Test public void setupShowsConcreteFilesAndAllowsAddingPhoneBeforeInstaller() throws Exception {
        ui = instrumentation.getUiAutomation();
        context.startActivity(new Intent(context,SymbianActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        waitNode("Add system files",20000);
        long deadline=SystemClock.uptimeMillis()+20000;
        while (SystemClock.uptimeMillis()<deadline) {
            AccessibilityNodeInfo root=ui.getRootInActiveWindow();
            if(root!=null && (hasText(root,"phones available") || hasText(root,"1 phone available") || hasText(root,"No phone files added"))) break;
            SystemClock.sleep(100);
        }
        capture("symbian-common-system-files.png");
        clickPrefix("N-Gage 2.0\n");
        waitNode("Add N-Gage 2.0 installer",5000);
        AccessibilityNodeInfo root=ui.getRootInActiveWindow();
        assertTrue(hasText(root,"not a .n-gage game"));
        capture("symbian-common-required-files.png"); click("About phone dumps");
        assertTrue(hasText(ui.getRootInActiveWindow(),".rom"));
        assertTrue(hasText(ui.getRootInActiveWindow(),"same phone dump"));
        capture("symbian-common-file-formats.png"); click("Back"); click("Close"); click("Library");
    }
    private boolean hasText(AccessibilityNodeInfo node,String text) { return !node.findAccessibilityNodeInfosByText(text).isEmpty(); }
    private Set<Long> screenshots() {
        Set<Long> ids=new HashSet<>();
        try(android.database.Cursor cursor=context.getContentResolver().query(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[]{"_id"},"owner_package_name=? AND relative_path=? AND is_pending=0",
                new String[]{context.getPackageName(),"Pictures/AbyssME/"},null)) {
            assertNotNull(cursor); while(cursor.moveToNext())ids.add(cursor.getLong(0));
        }
        return ids;
    }
    private AccessibilityNodeInfo find(AccessibilityNodeInfo node,String text,boolean prefix) {
        if(node==null)return null;
        String value=node.getText()==null ? "" : node.getText().toString();
        if((prefix ? value.startsWith(text) : value.equalsIgnoreCase(text)) || text.contentEquals(node.getContentDescription()==null ? "" : node.getContentDescription())) return node;
        for(int i=0;i<node.getChildCount();i++) { AccessibilityNodeInfo match=find(node.getChild(i),text,prefix); if(match!=null)return match; }
        return null;
    }
    private AccessibilityNodeInfo waitNode(String text,long timeout) {
        long end=SystemClock.uptimeMillis()+timeout;
        do {
            AccessibilityNodeInfo root = ui.getRootInActiveWindow();
            AccessibilityNodeInfo found = find(root, text, false);
            if (found != null) return found;
            if ("Game menu".equals(text) && !BuildConfig.HANDHELD_MODE) {
                AccessibilityNodeInfo reveal = find(root, "Show game controls", false);
                if (reveal != null) clickNode(reveal);
            }
            SystemClock.sleep(100);
        } while(SystemClock.uptimeMillis()<end);
        throw new AssertionError("Missing: "+text);
    }
    private AccessibilityNodeInfo description(AccessibilityNodeInfo node,String text) {
        if(node==null)return null;
        if(text.contentEquals(node.getContentDescription()==null ? "" : node.getContentDescription()))return node;
        for(int i=0;i<node.getChildCount();i++) { AccessibilityNodeInfo found=description(node.getChild(i),text); if(found!=null)return found; }
        return null;
    }
    private void click(String text) {
        AccessibilityNodeInfo node=description(ui.getRootInActiveWindow(),text);
        clickNode(node!=null ? node : waitNode(text,10000));
    }
    private void clickPrefix(String text) { AccessibilityNodeInfo node=find(ui.getRootInActiveWindow(),text,true); assertNotNull(text,node); clickNode(node); }
    private void clickScrollingChoice(String text) {
        for (int action : new int[]{AccessibilityNodeInfo.ACTION_SCROLL_FORWARD, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD}) {
            for (int attempt = 0; attempt < 12; attempt++) {
                AccessibilityNodeInfo root = ui.getRootInActiveWindow();
                AccessibilityNodeInfo match = find(root, text, false);
                if (match != null) { clickNode(match); return; }
                if (!scroll(root, action)) break;
                SystemClock.sleep(200);
            }
        }
        throw new AssertionError("Missing scrolling choice: " + text);
    }
    private boolean scroll(AccessibilityNodeInfo node, int action) {
        if (node == null) return false;
        if (node.isScrollable() && node.performAction(action)) return true;
        for (int i = 0; i < node.getChildCount(); i++) if (scroll(node.getChild(i), action)) return true;
        return false;
    }
    private void clickNode(AccessibilityNodeInfo node) {
        node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());
        SystemClock.sleep(150); node.refresh();
        if (!node.isClickable() || !node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            android.graphics.Rect bounds=new android.graphics.Rect(); node.getBoundsInScreen(bounds);
            assertTrue("Visible target",node.isVisibleToUser() && !bounds.isEmpty());
            long now=SystemClock.uptimeMillis();
            MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,bounds.centerX(),bounds.centerY(),0);
            MotionEvent up=MotionEvent.obtain(now,now+50,MotionEvent.ACTION_UP,bounds.centerX(),bounds.centerY(),0);
            down.setSource(InputDevice.SOURCE_TOUCHSCREEN); up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            try {assertTrue(ui.injectInputEvent(down,true));assertTrue(ui.injectInputEvent(up,true));} finally {down.recycle();up.recycle();}
        }
        SystemClock.sleep(200);
    }
    private void key(int code,long duration) {
        long now=SystemClock.uptimeMillis(); ui.injectInputEvent(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,code,0),true);
        SystemClock.sleep(duration); ui.injectInputEvent(new KeyEvent(now,SystemClock.uptimeMillis(),KeyEvent.ACTION_UP,code,0),true);
    }
    private void capture(String name) throws Exception {
        ui.waitForIdle(500,5000); Bitmap image=ui.takeScreenshot(); assertNotNull(image);
        try(OutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),name))) { assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out)); }
        finally {image.recycle();}
    }
}
