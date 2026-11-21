/* SPDX-License-Identifier: GPL-3.0-only */
package com.nokia.mid.ui;

public interface CustomKeyboardControl {
    int KEYBOARD_LANDSCAPE = 1, KEYBOARD_PORTRAIT = 2;
    int KEYBOARD_LANDSCAPE_180 = 4, KEYBOARD_PORTRAIT_180 = 8;

    void launch();
    void launch(int type);
    void launch(int type, int mode);
    void dismiss();
    void setKeyboardType(int type);
    void setKeyboardMode(int mode);
    int getKeyboardType();
    int getKeyboardMode();
    int getSupportedOrientations(int type);
}
