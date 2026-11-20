/* SPDX-License-Identifier: GPL-3.0-only */
package com.nokia.mid.ui;

import android.graphics.Rect;
import javax.microedition.lcdui.KeyboardViewport;
import javax.microedition.shell.MicroActivity;
import javax.microedition.util.ContextHolder;

public final class VirtualKeyboard {
    public static final int CUSTOM_KEYBOARD = 1;
    public static final int SYSTEM_KEYBOARD = 2;
    public static final int VKB_TYPE_ITUT = 1;
    public static final int VKB_TYPE_GAME = 3;
    public static final int VKB_MODE_DEFAULT = 0;
    public static final int VKB_MODE_NUMERIC = 1;
    public static final int VKB_MODE_ALPHA_LOWER_CASE = 2;
    public static final int VKB_MODE_ALPHA_UPPER_CASE = 3;
    public static final int VKB_MODE_ALPHA_UPPER_CASE_LOCKED = 4;

    private VirtualKeyboard() {}

    private static KeyboardViewport viewport() {
        MicroActivity activity = ContextHolder.getActivity();
        return activity == null ? null : activity.getKeyboardViewport();
    }

    public static boolean isVisible() {
        KeyboardViewport viewport = viewport();
        return viewport != null && viewport.isVisible();
    }

    private static Rect bounds() {
        KeyboardViewport viewport = viewport();
        return viewport == null ? new Rect() : viewport.getBounds();
    }

    public static int getXPosition() { return bounds().left; }
    public static int getYPosition() { return bounds().top; }
    public static int getWidth() { return bounds().width(); }
    public static int getHeight() { return bounds().height(); }

    public static void setVisibilityListener(KeyboardVisibilityListener listener) {
        KeyboardViewport viewport = viewport();
        if (viewport != null) viewport.setListener(listener == null ? null : visible -> {
            if (visible) listener.showNotify(SYSTEM_KEYBOARD);
            else listener.hideNotify(SYSTEM_KEYBOARD);
        });
    }

    public static void suppressSizeChanged(boolean suppress) {
        KeyboardViewport viewport = viewport();
        if (viewport != null) viewport.suppressSizeChanged(suppress);
    }

    public static void hideOpenKeypadCommand(boolean hide) {
        MicroActivity activity = ContextHolder.getActivity();
        if (activity != null) activity.hideNokiaKeypadCommand(hide);
    }
}
