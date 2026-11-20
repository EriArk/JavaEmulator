/* SPDX-License-Identifier: GPL-3.0-only */
package com.nokia.mid.ui.gestures;

public interface GestureListener {
    void gestureAction(Object container, GestureInteractiveZone zone, GestureEvent event);
}
