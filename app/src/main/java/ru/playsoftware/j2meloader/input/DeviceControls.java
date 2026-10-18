package ru.playsoftware.j2meloader.input;

import android.content.Context;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;
import java.util.HashSet;
import java.util.Set;
import ru.playsoftware.j2meloader.config.ProfileModel;

/** Session-owned connection tracking. Reconnection never acknowledges a pause. */
public final class DeviceControls implements InputManager.InputDeviceListener, AutoCloseable {
    public static final int AUTO = 0, TOUCH = 1, CONTROLLER = 2;
    public static final int ALWAYS = 1, OFF = 2;
    private final InputManager manager;
    private final Runnable release, changed, disconnected;
    private final Set<Integer> controllers = new HashSet<>();
    private boolean resumeRequired, closed;

    public DeviceControls(Context context, Runnable release, Runnable changed, Runnable disconnected) {
        manager = context.getSystemService(InputManager.class);
        this.release = release; this.changed = changed; this.disconnected = disconnected;
        manager.registerInputDeviceListener(this, new Handler(Looper.getMainLooper()));
        for (int id : manager.getInputDeviceIds()) if (isController(manager.getInputDevice(id))) controllers.add(id);
    }
    public static boolean isController(InputDevice device) {
        return device != null && !device.isVirtual() && controllerSources(device.getSources());
    }
    public static boolean controllerSources(int sources) {
        return (sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                || (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
    }
    public static boolean connected(Context context) {
        InputManager manager = context.getSystemService(InputManager.class);
        if (manager == null) return false;
        for (int id : manager.getInputDeviceIds()) if (isController(manager.getInputDevice(id))) return true;
        return false;
    }
    public static boolean showTouch(ProfileModel profile, boolean connected) {
        int visibility = profile.getTouchVisibility();
        if (visibility == ALWAYS) return true;
        if (visibility == OFF) return false;
        return profile.controlMode == TOUCH || (profile.controlMode != CONTROLLER && !connected);
    }
    public boolean connected() { return !controllers.isEmpty(); }
    public boolean resumeRequired() { return resumeRequired; }
    public void resume() { resumeRequired = false; }
    @Override public void onInputDeviceAdded(int id) { update(id, isController(manager.getInputDevice(id))); }
    @Override public void onInputDeviceChanged(int id) {
        if (controllers.contains(id)) release.run();
        update(id, isController(manager.getInputDevice(id)));
    }
    @Override public void onInputDeviceRemoved(int id) { update(id, false); }
    // Kept package-visible for deterministic removal/reconnection tests without changing host devices.
    void update(int id, boolean present) {
        if (closed) return;
        boolean removed = !present && controllers.remove(id);
        boolean added = present && controllers.add(id);
        if (!removed && !added) return;
        release.run();
        if (removed) resumeRequired = true;
        changed.run();
        if (removed) disconnected.run();
    }
    @Override public void close() {
        if (closed) return;
        closed = true; manager.unregisterInputDeviceListener(this); controllers.clear(); release.run();
    }
}
