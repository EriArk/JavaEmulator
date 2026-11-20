/* SPDX-License-Identifier: GPL-3.0-only */
package com.nokia.mid.ui.gestures;

final class PinchEvent extends StrokeEvent {
    private final int initialDistance, distance, distanceChange;
    private final int centerX, centerY, centerChangeX, centerChangeY;

    PinchEvent(int startX, int startY, int x1, int y1, int x2, int y2, PinchEvent previous) {
        super(GestureInteractiveZone.GESTURE_PINCH, startX, startY, 0, 0, 0, 0);
        distance = (int)Math.round(Math.hypot(x2 - x1, y2 - y1));
        centerX = (x1 + x2) / 2;
        centerY = (y1 + y2) / 2;
        initialDistance = previous == null ? distance : previous.initialDistance;
        distanceChange = previous == null ? 0 : distance - previous.distance;
        centerChangeX = previous == null ? 0 : centerX - previous.centerX;
        centerChangeY = previous == null ? 0 : centerY - previous.centerY;
    }

    public int getPinchDistanceStarting() { return initialDistance; }
    public int getPinchDistanceCurrent() { return distance; }
    public int getPinchDistanceChange() { return distanceChange; }
    public int getPinchCenterX() { return centerX; }
    public int getPinchCenterY() { return centerY; }
    public int getPinchCenterChangeX() { return centerChangeX; }
    public int getPinchCenterChangeY() { return centerChangeY; }
}
