/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.bluetooth.L2CAPConnection;

/** L2CAP packet semantics over an already authorized, reliable ordered peer stream.
 * Not a Bluetooth wire protocol or an authentication/encryption implementation.
 * Never use this framing with the existing physical-Bluetooth implementation.
 */
public final class FramedL2capConnection implements L2CAPConnection {
    private final InputStream input;
    private final OutputStream output;
    private final Closeable transport;
    private final int receiveMtu, transmitMtu;
    private final Object readLock = new Object(), writeLock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final byte[] header = new byte[2];
    private final byte[] packet;
    private int headerRead, payloadRead, payloadLength = -1;

    /** MTUs must come from the session's agreed channel negotiation, not game packets.
     * Closing transport must unblock any pending read/write, e.g. Socket.close().
     */
    public FramedL2capConnection(InputStream input, OutputStream output, Closeable transport,
                                int receiveMtu, int transmitMtu) {
        if (receiveMtu < MINIMUM_MTU || receiveMtu > 65535 || transmitMtu < MINIMUM_MTU || transmitMtu > 65535) {
            throw new IllegalArgumentException("Invalid negotiated L2CAP MTU");
        }
        this.input = Objects.requireNonNull(input);
        this.output = Objects.requireNonNull(output);
        this.transport = Objects.requireNonNull(transport);
        this.receiveMtu = receiveMtu;
        this.transmitMtu = transmitMtu;
        packet = new byte[receiveMtu];
    }

    @Override public int getReceiveMTU() throws IOException { checkOpen(); return receiveMtu; }
    @Override public int getTransmitMTU() throws IOException { checkOpen(); return transmitMtu; }

    @Override public void send(byte[] data) throws IOException {
        Objects.requireNonNull(data);
        synchronized (writeLock) {
            checkOpen();
            int size = Math.min(data.length, transmitMtu);
            try {
                output.write(size >>> 8);
                output.write(size & 255);
                output.write(data, 0, size);
                output.flush();
            } catch (IOException failure) { throw fail(failure); }
        }
    }

    @Override public int receive(byte[] destination) throws IOException {
        Objects.requireNonNull(destination);
        synchronized (readLock) {
            checkOpen();
            readPacket(true);
            int count = Math.min(destination.length, payloadLength);
            System.arraycopy(packet, 0, destination, 0, count);
            headerRead = payloadRead = 0;
            payloadLength = -1;
            return count;
        }
    }

    @Override public boolean ready() throws IOException {
        synchronized (readLock) {
            checkOpen();
            return readPacket(false);
        }
    }

    // Keep at most one bounded packet. ready() must not report a partial header/payload.
    private boolean readPacket(boolean blocking) throws IOException {
        try {
            while (true) {
                checkOpen();
                if (headerRead == 2 && payloadLength < 0) {
                    payloadLength = ((header[0] & 255) << 8) | (header[1] & 255);
                    if (payloadLength > receiveMtu) throw new IOException("Peer packet exceeds negotiated MTU");
                }
                if (payloadLength >= 0 && payloadRead == payloadLength) return true;
                byte[] target = headerRead < 2 ? header : packet;
                int offset = headerRead < 2 ? headerRead : payloadRead;
                int wanted = headerRead < 2 ? 2 - headerRead : payloadLength - payloadRead;
                if (!blocking) {
                    int available = input.available();
                    if (available <= 0) return false;
                    wanted = Math.min(wanted, available);
                }
                int count = input.read(target, offset, wanted);
                if (count < 0) throw new EOFException("Peer closed L2CAP channel");
                if (count == 0) throw new IOException("Peer stream made no progress");
                if (headerRead < 2) headerRead += count;
                else payloadRead += count;
            }
        } catch (IOException failure) { throw fail(failure); }
    }

    private void checkOpen() throws IOException { if (closed.get()) throw new IOException("L2CAP channel closed"); }
    private IOException fail(IOException failure) {
        try { close(); } catch (IOException closeError) { failure.addSuppressed(closeError); }
        return failure;
    }

    @Override public void close() throws IOException {
        // Do not acquire readLock/writeLock: closing must interrupt blocked game IO.
        if (closed.compareAndSet(false, true)) transport.close();
    }
}
