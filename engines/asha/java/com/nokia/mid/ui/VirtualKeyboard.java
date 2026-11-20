/* SPDX-License-Identifier: GPL-3.0-only */
package com.nokia.mid.ui;

import javax.microedition.shell.MicroActivity;
import javax.microedition.util.ContextHolder;

public final class VirtualKeyboard {
    private VirtualKeyboard() {}

    public static void hideOpenKeypadCommand(boolean hide) {
        MicroActivity activity = ContextHolder.getActivity();
        if (activity != null) activity.hideNokiaKeypadCommand(hide);
    }
}
