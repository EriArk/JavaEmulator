/* SPDX-License-Identifier: GPL-3.0-only */
package com.nokia.mid.ui.gestures;

class StrokeEvent implements GestureEvent {
    private final int type, x, y, dx, dy, vx, vy;

    StrokeEvent(int type, int x, int y, int dx, int dy, int vx, int vy) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.dx = dx;
        this.dy = dy;
        this.vx = vx;
        this.vy = vy;
    }
    public int getType() { return type; }
    public int getStartX() { return x; }
    public int getStartY() { return y; }
    public int getDragDistanceX() { return dx; }
    public int getDragDistanceY() { return dy; }
    public int getFlickSpeedX() { return vx; }
    public int getFlickSpeedY() { return vy; }
    public int getFlickSpeed() { return (int)Math.round(Math.hypot(vx, vy)); }
    public float getFlickDirection() { return (float)Math.atan2(vy, vx); }
    public int getPinchDistanceStarting() { return 0; }
    public int getPinchDistanceCurrent() { return 0; }
    public int getPinchDistanceChange() { return 0; }
    public int getPinchCenterX() { return 0; }
    public int getPinchCenterY() { return 0; }
    public int getPinchCenterChangeX() { return 0; }
    public int getPinchCenterChangeY() { return 0; }
}
