/* SPDX-License-Identifier: GPL-3.0-only */
package com.nokia.mid.ui;

import javax.microedition.lcdui.Canvas;

public abstract class CanvasItem {
    Canvas parent;
    int x, y, width, height, z;
    boolean visible;

    abstract void changed();

    public synchronized void setParent(Object value) {
        if (value != null && (!(value instanceof Canvas) || parent != null && value != parent))
            throw new IllegalArgumentException("Canvas parent required");
        parent = (Canvas) value;
        changed();
    }

    public synchronized Object getParent() { return parent; }

    public synchronized void setSize(int width, int height) {
        if (width < 0 || height < 0) throw new IllegalArgumentException("Negative size");
        this.width = width;
        this.height = height;
        changed();
    }

    public synchronized void setSize(int x, int y, int width, int height) {
        setSize(width, height);
        setPosition(x, y);
    }

    public synchronized void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
        changed();
    }

    public synchronized void setZPosition(int value) { z = value; changed(); }
    public synchronized void setVisible(boolean value) { visible = value; changed(); }
    public synchronized boolean isVisible() { return visible; }
    public synchronized int getPositionX() { return x; }
    public synchronized int getPositionY() { return y; }
    public synchronized int getZPosition() { return z; }
    public synchronized int getWidth() { return width; }
    public synchronized int getHeight() { return height; }
}
