/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.input;

import android.view.InputDevice;
import android.view.MotionEvent;

/** Shared Android motion decoding for gameplay and controller identification. */
public final class ControllerMotion {
    private ControllerMotion() { }

    public static final class Sticks {
        public final float leftX, leftY, rightX, rightY;
        private Sticks(float x, float y, float rx, float ry) {
            leftX = x; leftY = y; rightX = rx; rightY = ry;
        }
    }

    public static Sticks sticks(MotionEvent event) {
        InputDevice device = event.getDevice();
        RightStickAxes.Ranges ranges = axis -> {
            InputDevice.MotionRange range = device == null ? null : device.getMotionRange(axis, event.getSource());
            return range == null ? null : new RightStickAxes.Range(range.getMin(), range.getMax(), range.getFlat());
        };
        RightStickAxes axes = RightStickAxes.select(ranges);
        float rx = axes.x < 0 ? 0 : ranges.get(axes.x).value(event.getAxisValue(axes.x));
        float ry = axes.y < 0 ? 0 : ranges.get(axes.y).value(event.getAxisValue(axes.y));
        return new Sticks(centered(event, MotionEvent.AXIS_X), centered(event, MotionEvent.AXIS_Y), rx, ry);
    }

    private static float centered(MotionEvent event, int axis) {
        InputDevice device = event.getDevice();
        InputDevice.MotionRange range = device == null ? null : device.getMotionRange(axis, event.getSource());
        float value = event.getAxisValue(axis);
        return Float.isFinite(value) && Math.abs(value) > Math.max(range == null ? 0 : range.getFlat(), .15f) ? value : 0;
    }

    public static void update(ControllerInput input, MotionEvent event) {
        if (event.getActionMasked() != MotionEvent.ACTION_MOVE || !event.isFromSource(InputDevice.SOURCE_JOYSTICK)) return;
        Sticks s = sticks(event);
        input.axes(event.getDeviceId(), s.leftX, s.leftY,
                centered(event, MotionEvent.AXIS_HAT_X), centered(event, MotionEvent.AXIS_HAT_Y),
                Math.max(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE)),
                Math.max(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS)), s.rightX, s.rightY);
    }
}
