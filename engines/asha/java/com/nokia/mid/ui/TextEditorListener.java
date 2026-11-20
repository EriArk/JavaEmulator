/* SPDX-License-Identifier: GPL-3.0-only */
package com.nokia.mid.ui;

public interface TextEditorListener {
    int ACTION_CONTENT_CHANGE = 1;
    int ACTION_OPTIONS_CHANGE = 2;
    int ACTION_CARET_MOVE = 4;
    int ACTION_TRAVERSE_PREVIOUS = 8;
    int ACTION_TRAVERSE_NEXT = 16;
    int ACTION_PAINT_REQUEST = 32;
    int ACTION_DIRECTION_CHANGE = 64;
    int ACTION_INPUT_MODE_CHANGE = 128;
    int ACTION_LANGUAGE_CHANGE = 256;
    int ACTION_TRAVERSE_OUT_SCROLL_UP = 512;
    int ACTION_TRAVERSE_OUT_SCROLL_DOWN = 1024;
    int ACTION_SCROLLBAR_CHANGED = 2048;
    void inputAction(TextEditor textEditor, int actions);
}
