/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.system.OsConstants;
import java.io.*;
import java.util.ArrayDeque;

/** Socket IO never runs on the room/UI/Binder thread. Both directions are bounded. */
final class LocalPeerPipe implements PeerPacketPort {
    static final int MAX_PACKET = 16383, MAX_BYTES = 1048576, MAX_PACKETS = 256;
    private final ParcelFileDescriptor descriptor;
    private final DataInputStream input;
    private final DataOutputStream output;
    private final ArrayDeque<byte[]> incoming = new ArrayDeque<>(), outgoing = new ArrayDeque<>();
    private int inBytes, outBytes;
    private boolean closed;
    private final Thread reader, writer;

    LocalPeerPipe(ParcelFileDescriptor descriptor) throws IOException {
        this.descriptor = descriptor;
        ParcelFileDescriptor readFd = null;
        try {
            readFd = descriptor.dup();
            ParcelFileDescriptor writeFd = descriptor.dup();
            input = new DataInputStream(new ParcelFileDescriptor.AutoCloseInputStream(readFd));
            output = new DataOutputStream(new ParcelFileDescriptor.AutoCloseOutputStream(writeFd));
        } catch (IOException e) {
            if (readFd != null) readFd.close(); descriptor.close(); throw e;
        }
        reader = new Thread(this::read, "game-ipc-read"); writer = new Thread(this::write, "game-ipc-write");
        reader.setDaemon(true); writer.setDaemon(true); reader.start(); writer.start();
    }
    @Override public synchronized void receive(byte[] packet) throws IOException {
        check(); validate(packet);
        if (outgoing.size() >= MAX_PACKETS || outBytes + packet.length > MAX_BYTES) {
            close(); throw new IOException("Game IPC send queue exceeded");
        }
        outgoing.add(packet.clone()); outBytes += packet.length; notifyAll();
    }
    @Override public synchronized byte[] pollOutbound() throws IOException {
        check(); byte[] packet = incoming.poll(); if (packet != null) inBytes -= packet.length; return packet;
    }
    private void read() {
        try (DataInputStream input = this.input) {
            while (true) {
                int length = input.readUnsignedShort();
                if (length < 1 || length > MAX_PACKET) throw new IOException("Invalid game IPC packet");
                byte[] packet = new byte[length]; input.readFully(packet);
                synchronized (this) {
                    check();
                    if (incoming.size() >= MAX_PACKETS || inBytes + length > MAX_BYTES) throw new IOException("Game IPC receive queue exceeded");
                    incoming.add(packet); inBytes += length;
                }
            }
        } catch (IOException | RuntimeException e) { close(); }
    }
    private void write() {
        try (DataOutputStream output = this.output) {
            while (true) {
                byte[] packet;
                synchronized (this) {
                    while (!closed && outgoing.isEmpty()) wait();
                    check(); packet = outgoing.remove(); outBytes -= packet.length;
                }
                output.writeShort(packet.length); output.write(packet); output.flush();
            }
        } catch (IOException | RuntimeException | InterruptedException e) { close(); }
    }
    private void check() throws IOException { if (closed) throw new IOException("Game IPC disconnected"); }
    private static void validate(byte[] packet) throws IOException {
        if (packet == null || packet.length < 1 || packet.length > MAX_PACKET) throw new IOException("Invalid game IPC packet");
    }
    @Override public synchronized void close() {
        if (closed) return; closed = true; incoming.clear(); outgoing.clear(); inBytes = outBytes = 0; notifyAll();
        // Closing an fd alone need not interrupt another thread's blocking Linux socket read.
        try { Os.shutdown(descriptor.getFileDescriptor(), OsConstants.SHUT_RDWR); } catch (Exception ignored) { }
        try { descriptor.close(); } catch (IOException ignored) { }
    }
    boolean workersStopped(long millis) throws InterruptedException {
        reader.join(millis); writer.join(millis); return !reader.isAlive() && !writer.isAlive();
    }
}
