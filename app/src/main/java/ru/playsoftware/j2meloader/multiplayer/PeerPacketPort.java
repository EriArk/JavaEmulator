/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import java.io.Closeable;
import java.io.IOException;

/** Bounded, non-blocking packet boundary for the secure peer's owner thread. */
public interface PeerPacketPort extends Closeable {
    void receive(byte[] packet) throws IOException;
    byte[] pollOutbound() throws IOException;
}
