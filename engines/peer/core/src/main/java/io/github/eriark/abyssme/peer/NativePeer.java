/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.peer;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

/** Internal ICE/DTLS/SCTP endpoint. Never feed it unauthenticated signaling. */
final class NativePeer implements Closeable {
    static final int DESCRIPTION = 1, CANDIDATE = 2, OPEN = 3, DATA = 4, CLOSED = 5, ERROR = 6, STATE = 7;
    static final int MAX_MESSAGE = 16384;
    static { System.loadLibrary("abyss-peer"); }
    static final class Event {
        final int kind;
        final String text, extra;
        final byte[] data;
        Event(int kind, String text, String extra, byte[] data) {
            this.kind = kind; this.text = text; this.extra = extra; this.data = data;
        }
    }
    private final AtomicLong handle;
    NativePeer(boolean host, String stunServer) throws IOException {
        handle = new AtomicLong(create(host, stunServer == null ? "" : stunServer));
    }
    void start() throws IOException { start(id()); }
    void description(String sdp, String type) throws IOException { description(id(), sdp, type); }
    void candidate(String candidate, String mid) throws IOException { candidate(id(), candidate, mid); }
    void send(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length < 1 || bytes.length > MAX_MESSAGE) throw new IOException("Invalid peer message size");
        send(id(), bytes);
    }
    Event poll(int timeoutMs) throws IOException { return poll(id(), Math.max(0, Math.min(250, timeoutMs))); }
    private long id() throws IOException {
        long id = handle.get();
        if (id == 0) throw new IOException("Peer closed");
        return id;
    }
    @Override public void close() {
        long id = handle.getAndSet(0);
        if (id != 0) destroy(id);
    }
    private static native long create(boolean host, String stun) throws IOException;
    private static native void start(long handle) throws IOException;
    private static native void description(long handle, String sdp, String type) throws IOException;
    private static native void candidate(long handle, String candidate, String mid) throws IOException;
    private static native void send(long handle, byte[] bytes) throws IOException;
    private static native Event poll(long handle, int timeoutMs) throws IOException;
    private static native void destroy(long handle);
}
