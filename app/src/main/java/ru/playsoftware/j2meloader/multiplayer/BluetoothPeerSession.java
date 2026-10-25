/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import io.github.eriark.abyssme.peer.PeerBinding;
import io.github.eriark.abyssme.peer.SecurePeer;
import javax.bluetooth.PeerBluetoothBackend;
import javax.bluetooth.VirtualBluetooth;
import java.io.*;
import java.util.*;

/** Lives on the room owner thread, not the UI or a game's blocking IO thread.
 * The caller supplies an admitted binding, polls every 10-20ms, and closes before
 * changing that authority. Installation into a MIDlet is a separate runtime step.
 */
public final class BluetoothPeerSession implements Closeable {
    public interface Listener {
        boolean signal(String recipient, List<String> fragments);
        void prepared(VirtualBluetooth adapter);
        void closed(String reason);
    }
    private final Thread owner = Thread.currentThread();
    private final PeerBinding binding;
    private final String self;
    private final Listener listener;
    private final PeerBluetoothBackend backend;
    private final VirtualBluetooth adapter;
    private final SecurePeer peer;
    private boolean closed, prepared;

    public BluetoothPeerSession(PeerBinding binding, String self, String stun, Listener listener) throws IOException {
        this.binding = Objects.requireNonNull(binding); this.self = self; this.listener = Objects.requireNonNull(listener);
        boolean host = binding.host(self);
        // Session-local, locally administered Bluetooth addresses; never expose provider IDs to games.
        VirtualBluetooth.Peer local = new VirtualBluetooth.Peer(host ? "020000000001" : "020000000002", host ? "Player 1" : "Player 2");
        VirtualBluetooth.Peer remote = new VirtualBluetooth.Peer(host ? "020000000002" : "020000000001", host ? "Player 2" : "Player 1");
        backend = new PeerBluetoothBackend(remote, host); adapter = new VirtualBluetooth(local, backend);
        try {
            peer = new SecurePeer(binding, self, stun, new SecurePeer.Listener() {
                public boolean signal(String recipient, List<String> fragments) { return !closed && listener.signal(recipient, fragments); }
                public void connected() { if (!closed) { prepared = true; listener.prepared(adapter); } }
                public void message(byte[] payload) {
                    if (!closed) try { backend.receive(payload); } catch (IOException e) { fail("Game network data was invalid. Rejoin the room."); }
                }
                public void closed(String reason) { fail(reason); }
            });
        } catch (IOException | RuntimeException e) { adapter.close(); throw e; }
    }
    public void receive(String authenticatedAuthor, String content) { checkThread(); if (!closed) peer.receiveSignal(authenticatedAuthor, content); }
    public void tick() {
        checkThread(); if (closed) return;
        peer.poll(); if (closed || !prepared) return;
        try {
            for (int i = 0; i < 16; i++) {
                byte[] payload = backend.pollOutbound(); if (payload == null) break; peer.send(payload);
            }
        } catch (IOException e) { fail("Game network connection ended. Return to the room to retry."); }
    }
    /** Revalidate after every authority change, including account switch and local preparation failure. */
    public boolean retain(String account, PeerBinding current, boolean locallyPrepared) {
        checkThread();
        if (!self.equals(account) || !same(binding, current) || !locallyPrepared) close();
        return !closed;
    }
    public boolean isPrepared() { checkThread(); return prepared && !closed; }
    private static boolean same(PeerBinding a, PeerBinding b) {
        return b != null && a.sameSession(b);
    }
    private void fail(String reason) { if (!closed) { close(); listener.closed(reason); } }
    @Override public void close() {
        checkThread(); if (closed) return; closed = true; prepared = false;
        try { adapter.close(); } catch (IOException ignored) { }
        peer.close();
    }
    private void checkThread() { if (Thread.currentThread() != owner) throw new IllegalStateException("Use the room owner thread"); }
}
