/* SPDX-License-Identifier: GPL-3.0-only */
package com.nokia.mid.ui;

public interface KeyboardVisibilityListener {
    void showNotify(int keyboardCategory);
    void hideNotify(int keyboardCategory);
}
