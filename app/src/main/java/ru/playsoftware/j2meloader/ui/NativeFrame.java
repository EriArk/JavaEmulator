/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.ui;

/** Latest guest pixels for the shared GPU presenter. Copy pixels and timestamps atomically. */
public interface NativeFrame {
    int frameWidth();
    int frameHeight();
    long presentationSerial();
    boolean copyPresented(int[] pixels, long[] stamp, long afterSerial);
}
