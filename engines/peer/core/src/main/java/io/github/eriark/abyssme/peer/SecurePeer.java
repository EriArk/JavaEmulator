/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.peer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.LongSupplier;

/** Single-owner, two-player encrypted transport. Call poll() regularly off the UI
 * thread. Only close() is cross-thread. Room/account/generation changes must close
 * this instance; there is deliberately no implicit reconnection or renegotiation. */
public final class SecurePeer implements Closeable {
    public interface Listener {
        /** Enqueue every fragment through the authenticated private mailbox, without coalescing. */
        boolean signal(String recipient, List<String> fragments);
        void connected();
        void message(byte[] payload);
        void closed(String reason);
        /** Fixed local phase labels only; never SDP, addresses or provider content. */
        default void state(String phase) {}
    }
    private final Thread owner = Thread.currentThread();
    private final PeerBinding binding;
    private final String self, remote;
    private final Listener listener;
    private final NativePeer nativePeer;
    private final PeerSignals signals;
    private final LongSupplier monotonic;
    private final long deadline;
    private final List<String[]> earlyCandidates = new ArrayList<>();
    private boolean remoteDescription, helloSent, ready;
    private volatile boolean closed;

    public SecurePeer(PeerBinding binding, String self, String stunServer, Listener listener) throws IOException {
        this(binding, self, stunServer, listener, () -> System.nanoTime() / 1000000);
    }
    SecurePeer(PeerBinding binding, String self, String stunServer, Listener listener,
               LongSupplier monotonic) throws IOException {
        this.binding = Objects.requireNonNull(binding); this.self = self; remote = binding.other(self);
        this.listener = Objects.requireNonNull(listener); this.monotonic = monotonic;
        deadline = monotonic.getAsLong() + PeerSignals.LIFETIME_MS;
        signals = new PeerSignals(binding, self, monotonic.getAsLong());
        nativePeer = new NativePeer(binding.host(self), stunServer);
        try { nativePeer.start(); } catch (IOException failure) { nativePeer.close(); throw failure; }
    }
    public synchronized boolean isConnected() { checkThread(); return ready && !closed; }

    /** The caller must pass the provider-authenticated author, never a JSON sender field. */
    public synchronized void receiveSignal(String authenticatedAuthor, String content) {
        checkThread();
        if (closed) return;
        try {
            checkDeadline();
            PeerSignals.Signal signal = signals.accept(authenticatedAuthor, content, monotonic.getAsLong());
            if (signal == null) return;
            String text = ascii(signal.data);
            if (signal.kind == PeerSignals.DESCRIPTION) {
                if (remoteDescription) throw new IOException("Unexpected peer renegotiation");
                nativePeer.description(text, binding.host(self) ? "answer" : "offer");
                remoteDescription = true;
                listener.state("Remote description accepted");
                for (String[] candidate : earlyCandidates) nativePeer.candidate(candidate[1], candidate[0]);
                earlyCandidates.clear();
            } else {
                int split = text.indexOf('\n');
                if (split < 1 || split > 32 || text.length() - split - 1 > 512) throw new IOException("Invalid ICE candidate");
                String[] candidate = {text.substring(0, split), text.substring(split + 1)};
                if (remoteDescription) nativePeer.candidate(candidate[1], candidate[0]);
                else {
                    if (earlyCandidates.size() >= 64) throw new IOException("Too many early candidates");
                    earlyCandidates.add(candidate);
                }
            }
        } catch (IOException failure) { fail("Peer negotiation failed. Rejoin the room to retry."); }
    }
    public synchronized void poll() {
        checkThread();
        if (closed) return;
        try {
            checkDeadline();
            // Bound work per actor tick so one peer cannot starve the room owner.
            for (int i = 0; i < 32 && !closed; i++) {
                NativePeer.Event event = nativePeer.poll(0);
                if (event == null) break;
                switch (event.kind) {
                    case NativePeer.DESCRIPTION:
                        emit(PeerSignals.DESCRIPTION, event.text); break;
                    case NativePeer.CANDIDATE:
                        emit(PeerSignals.CANDIDATE, event.extra + "\n" + event.text); break;
                    case NativePeer.OPEN:
                        if (helloSent) throw new IOException("Repeated channel open");
                        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                        DataOutputStream out = new DataOutputStream(bytes);
                        out.writeByte(0); out.write(binding.digest()); out.writeUTF(self);
                        nativePeer.send(bytes.toByteArray()); helloSent = true; break;
                    case NativePeer.DATA:
                        receiveData(event.data); break;
                    case NativePeer.STATE:
                        listener.state(event.text); break;
                    case NativePeer.CLOSED:
                    case NativePeer.ERROR:
                        fail("Peer disconnected. Rejoin the room to retry."); break;
                    default: throw new IOException("Unknown peer event");
                }
            }
        } catch (IOException failure) { fail("Peer connection failed. Rejoin the room to retry."); }
    }
    public synchronized void send(byte[] payload) throws IOException {
        checkThread();
        if (!isConnected()) throw new IOException("Peer is not ready");
        if (payload == null || payload.length < 1 || payload.length >= NativePeer.MAX_MESSAGE)
            throw new IOException("Invalid peer payload size");
        byte[] frame = new byte[payload.length + 1]; frame[0] = 1;
        System.arraycopy(payload, 0, frame, 1, payload.length);
        try { nativePeer.send(frame); }
        catch (IOException failure) { fail("Peer send failed. Rejoin the room to retry."); throw failure; }
    }
    private void receiveData(byte[] frame) throws IOException {
        if (frame.length < 1 || !helloSent) throw new IOException("Peer handshake missing");
        if (frame[0] == 0) {
            if (ready) throw new IOException("Repeated peer identity");
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(frame, 1, frame.length - 1));
            byte[] identity = new byte[32]; in.readFully(identity);
            if (!binding.matches(identity) || !remote.equals(in.readUTF()) || in.available() != 0)
                throw new IOException("Wrong encrypted peer identity");
            ready = true; listener.connected();
        } else if (frame[0] == 1 && ready && frame.length > 1) {
            listener.message(Arrays.copyOfRange(frame, 1, frame.length));
        } else throw new IOException("Invalid peer frame");
    }
    private void emit(int kind, String text) throws IOException {
        if (!listener.signal(remote, signals.encode(kind, text.getBytes(StandardCharsets.US_ASCII), monotonic.getAsLong())))
            throw new IOException("Signaling could not be queued");
    }
    private static String ascii(byte[] bytes) throws IOException {
        for (byte value : bytes) if (value <= 0) throw new IOException("Invalid signaling characters");
        return new String(bytes, StandardCharsets.US_ASCII);
    }
    private void checkDeadline() throws IOException {
        if (!ready && monotonic.getAsLong() >= deadline) throw new IOException("Peer handshake expired");
    }
    private void fail(String reason) {
        if (closed) return;
        close(); listener.closed(reason);
    }
    @Override public synchronized void close() { closed = true; nativePeer.close(); }
    private void checkThread() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("Use the peer owner thread");
    }
}
