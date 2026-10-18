package ru.playsoftware.j2meloader.input;

import android.content.Context;
import android.content.ComponentName;
import android.content.pm.ActivityInfo;
import android.graphics.RectF;
import android.view.InputDevice;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.gson.Gson;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import org.junit.Test;
import ru.playsoftware.j2meloader.MainActivity;
import ru.playsoftware.j2meloader.config.ProfileModel;
import ru.playsoftware.j2meloader.config.ProfilesManager;
import static org.junit.Assert.*;

public class DeviceControlsTest {
    private Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
    private void main(Runnable action) { InstrumentationRegistry.getInstrumentation().runOnMainSync(action); }

    @Test public void controllerDetectionExcludesMouseKeyboardAndDpadOnlyDevices() {
        assertTrue(DeviceControls.controllerSources(InputDevice.SOURCE_GAMEPAD));
        assertTrue(DeviceControls.controllerSources(InputDevice.SOURCE_JOYSTICK | InputDevice.SOURCE_KEYBOARD));
        assertFalse(DeviceControls.controllerSources(InputDevice.SOURCE_MOUSE));
        assertFalse(DeviceControls.controllerSources(InputDevice.SOURCE_KEYBOARD | InputDevice.SOURCE_DPAD));
        assertFalse(DeviceControls.isController(null));
    }

    @Test public void automaticAndExplicitModesHaveStablePrecedence() {
        ProfileModel p = new ProfileModel(); p.showKeyboard = true;
        for (int mode = 0; mode < 3; mode++) for (int visibility = 0; visibility < 3; visibility++) {
            p.controlMode = mode; p.touchVisibility = visibility;
            for (boolean connected : new boolean[]{false,true}) {
                boolean expected = visibility == 1 || (visibility == 0 && (mode == 1 || (mode == 0 && !connected)));
                assertEquals(expected, DeviceControls.showTouch(p, connected));
            }
        }
    }

    @Test public void oldProfilesKeepOrientationMappingsAndExplicitHiddenPhoneKeypad() {
        Gson gson = new Gson();
        ProfileModel handheld = gson.fromJson("{\"Orientation\":3,\"ShowKeyboard\":false,\"TouchInput\":false}", ProfileModel.class);
        assertEquals(0, handheld.controlMode); assertEquals(0, handheld.getTouchVisibility());
        assertTrue(DeviceControls.showTouch(handheld, false));
        assertFalse(DeviceControls.showTouch(handheld, true)); assertEquals(3, handheld.orientation);
        ProfileModel phone = new ProfileModel(); phone.showKeyboard = false; phone.touchInput = true;
        assertEquals(2, phone.getTouchVisibility());
        phone.touchVisibility = 1; assertTrue(DeviceControls.showTouch(phone, true));
    }

    @Test public void disconnectReleasesDirectionsDiagonalsTriggersAndWaitsForUser() {
        main(() -> {
            ArrayList<String> events = new ArrayList<>();
            ControllerInput input = new ControllerInput(k -> k, new ControllerInput.Sink() {
                public void press(int k) { events.add("+" + k); }
                public void release(int k) { events.add("-" + k); }
                public void repeat(int k) { events.add("r" + k); }
            });
            int[] pauses = {0}, updates = {0};
            DeviceControls devices = new DeviceControls(context(), input::clear, () -> updates[0]++, () -> pauses[0]++);
            try {
                devices.update(90001, true);
                input.key(90001, 96, true, false);
                input.axes(90001, -1, -1, 0, 0, 1, 1);
                int presses = (int) events.stream().filter(s -> s.startsWith("+")).count();
                devices.update(90001, false);
                assertEquals(presses, events.stream().filter(s -> s.startsWith("-")).count());
                assertEquals(1, pauses[0]); assertTrue(devices.resumeRequired());
                devices.update(90001, true);
                assertTrue("Reconnection must not resume", devices.resumeRequired());
                assertEquals(1, pauses[0]); assertEquals(3, updates[0]);
                devices.resume(); assertFalse(devices.resumeRequired());
                int before = events.size(); input.key(90001, 96, true, false);
                assertEquals(before + 1, events.size()); assertEquals("+96", events.get(before));
            } finally { devices.close(); }
        });
    }

    @Test public void unrelatedRemovalIsIgnoredAndClosingUnregistersCallbacks() {
        main(() -> {
            int[] events = {0};
            DeviceControls devices = new DeviceControls(context(), () -> events[0]++, () -> events[0]++, () -> events[0]++);
            devices.update(90002, false); assertEquals(0, events[0]);
            devices.update(90002, true); devices.update(90003, true);
            devices.update(90002, false);
            assertTrue("Other controller remains attached", devices.connected());
            assertTrue(devices.resumeRequired());
            devices.close(); int closed = events[0]; devices.close(); devices.update(90003, false);
            assertEquals(closed, events[0]);
        });
    }

    @Test public void hidingTouchDeckReleasesKeysAndReturnsWholeViewport() {
        main(() -> {
            ProfileModel p = new ProfileModel(); p.touchLayout = 0;
            ArrayList<Integer> releases = new ArrayList<>();
            TouchDeck deck = new TouchDeck(p, 1, new TouchDeck.Sink() {
                public void press(int k) {} public void release(int k) { releases.add(k); }
                public void repeat(int k) {} public void feedback() {} public void invalidate() {}
            });
            for (int[] size : new int[][]{{360,640},{640,360},{240,240}}) {
                deck.setEnabled(true); deck.layout(size[0],size[1]);
                TouchDeck.Key key = deck.getKeys().get(0);
                deck.down(0, key.bounds.centerX(), key.bounds.centerY()); releases.clear();
                deck.setEnabled(false);
                assertFalse(releases.isEmpty()); assertTrue(deck.getKeys().isEmpty());
                assertEquals(new RectF(0,0,size[0],size[1]), deck.gameArea(size[0],size[1]));
                assertFalse(deck.down(0, key.bounds.centerX(), key.bounds.centerY()));
                deck.setEnabled(true); assertFalse(deck.getKeys().isEmpty()); deck.cancel();
            }
        });
    }

    @Test public void newControlSettingsParticipateInPreviewAndPersistence() {
        main(() -> {
            File dir = new File(context().getCacheDir(), "unified-profile-" + System.nanoTime());
            assertTrue(dir.mkdirs());
            ProfileModel p = new ProfileModel(dir);
            assertTrue(ProfilesManager.saveConfig(p));
            ProfilesManager.beginPreview(p); p.controlMode = 2; p.touchVisibility = 1;
            ProfilesManager.undoPreview(p); assertEquals(0, p.controlMode); assertEquals(0, p.getTouchVisibility());
            ProfilesManager.beginPreview(p); p.controlMode = 1; p.touchVisibility = 2;
            assertTrue(ProfilesManager.keepPreview(p));
            ProfileModel saved = ProfilesManager.loadConfig(dir);
            assertEquals(1, saved.controlMode); assertEquals(2, saved.getTouchVisibility());
            for (File file : dir.listFiles()) assertTrue(file.delete()); assertTrue(dir.delete());
        });
    }

    @Test public void primaryApkSupportsPortraitAndLandscapeWithoutPhoneSuffix() throws Exception {
        ActivityInfo info = context().getPackageManager().getActivityInfo(new ComponentName(context(), MainActivity.class), 0);
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, info.screenOrientation);
        if (!ru.playsoftware.j2meloader.BuildConfig.FLAVOR.equals("phone")) assertFalse(context().getPackageName().contains(".phone"));
        assertFalse(ru.playsoftware.j2meloader.BuildConfig.HANDHELD_MODE);
    }
}
