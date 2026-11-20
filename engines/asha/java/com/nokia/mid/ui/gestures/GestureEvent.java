/* SPDX-License-Identifier: GPL-3.0-only */
package com.nokia.mid.ui.gestures;

public interface GestureEvent {
    int getType();
    int getStartX();
    int getStartY();
    int getDragDistanceX();
    int getDragDistanceY();
    float getFlickDirection();
    int getFlickSpeed();
    int getFlickSpeedX();
    int getFlickSpeedY();
    int getPinchDistanceStarting();
    int getPinchDistanceCurrent();
    int getPinchDistanceChange();
    int getPinchCenterX();
    int getPinchCenterY();
    int getPinchCenterChangeX();
    int getPinchCenterChangeY();
}
