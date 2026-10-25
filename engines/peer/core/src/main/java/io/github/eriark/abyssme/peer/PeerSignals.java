/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.peer;

import java.io.*;
import java.util.*;

/** Bounded signaling envelopes, never game traffic. Authentication is supplied
 * by the provider's verified DM author, not by any field inside the envelope. */
public final class PeerSignals {
    public static final String PREFIX = "AbyssME peer v1\n";
    static final int DESCRIPTION = 1, CANDIDATE = 2, CHUNK = 960, MAX_BYTES = 24576, MAX_SEQUENCE = 128;
    static final long LIFETIME_MS = 120000;
    static final class Signal {
        final int kind;
        final byte[] data;
        Signal(int kind, byte[] data) { this.kind = kind; this.data = data; }
    }
    private static final class Parts {
        final int kind, count, length;
        final byte[][] data;
        int received;
        Parts(int kind, int count, int length) {
            this.kind = kind; this.count = count; this.length = length;
            data = new byte[count][];
        }
    }
    private final PeerBinding binding;
    private final String remote;
    private final long started;
    private final Map<Integer, Parts> pending = new HashMap<>();
    private final Set<Integer> completed = new HashSet<>();
    private int sequence, pendingBytes;
    private boolean timedOut;

    PeerSignals(PeerBinding binding, String self, long monotonicStart) {
        this.binding = binding; remote = binding.other(self); started = monotonicStart;
    }

    List<String> encode(int kind, byte[] payload, long now) throws IOException {
        if ((kind != DESCRIPTION && kind != CANDIDATE) || payload == null
                || payload.length < 1 || payload.length > MAX_BYTES || sequence >= MAX_SEQUENCE || expired(now))
            throw new IOException("Signaling limit exceeded");
        int serial = ++sequence, count = (payload.length + CHUNK - 1) / CHUNK;
        List<String> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            // A fresh room-authorized attempt is the nonce. Do not compare clocks
            // across phones: the receiver enforces its own monotonic attempt deadline.
            out.write(binding.digest()); out.writeLong(LIFETIME_MS);
            out.writeShort(serial); out.writeByte(kind); out.writeByte(i); out.writeByte(count);
            out.writeInt(payload.length);
            out.write(payload, i * CHUNK, Math.min(CHUNK, payload.length - i * CHUNK));
            result.add(PREFIX + Base64.getEncoder().encodeToString(bytes.toByteArray()));
        }
        return result;
    }

    Signal accept(String authenticatedAuthor, String content, long now) throws IOException {
        if (!remote.equals(authenticatedAuthor) || content == null || !content.startsWith(PREFIX)) return null;
        if (content.length() > 2000) throw new IOException("Oversized signaling");
        if (expired(now)) { pending.clear(); pendingBytes = 0; return null; }
        try {
            byte[] bytes = Base64.getDecoder().decode(content.substring(PREFIX.length()));
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
            byte[] identity = new byte[32]; in.readFully(identity);
            if (!binding.matches(identity)) return null;
            if (in.readLong() != LIFETIME_MS) throw new IOException("Invalid signaling lifetime");
            int serial = in.readUnsignedShort(), kind = in.readUnsignedByte();
            int index = in.readUnsignedByte(), count = in.readUnsignedByte(), length = in.readInt();
            if (serial < 1 || serial > MAX_SEQUENCE || (kind != DESCRIPTION && kind != CANDIDATE)
                    || length < 1 || length > MAX_BYTES || count != (length + CHUNK - 1) / CHUNK
                    || index >= count || in.available() != Math.min(CHUNK, length - index * CHUNK))
                throw new IOException("Invalid signaling fragment");
            if (completed.contains(serial)) return null;
            Parts p = pending.get(serial);
            if (p == null) {
                if (pending.size() >= 8 || pendingBytes + length > 65536) throw new IOException("Signaling queue exceeded");
                p = new Parts(kind, count, length); pending.put(serial, p); pendingBytes += length;
            }
            if (p.kind != kind || p.count != count || p.length != length)
                throw new IOException("Conflicting signaling fragment");
            byte[] part = new byte[in.available()]; in.readFully(part);
            if (p.data[index] != null) {
                if (!Arrays.equals(p.data[index], part)) throw new IOException("Conflicting repeated fragment");
                return null;
            }
            p.data[index] = part;
            if (++p.received != count) return null;
            byte[] whole = new byte[length];
            for (int i = 0; i < count; i++) System.arraycopy(p.data[i], 0, whole, i * CHUNK, p.data[i].length);
            pending.remove(serial); pendingBytes -= length; completed.add(serial);
            return new Signal(kind, whole);
        } catch (IllegalArgumentException bad) { throw new IOException("Invalid signaling encoding"); }
    }
    private boolean expired(long monotonicNow) {
        timedOut |= monotonicNow < started || monotonicNow - started >= LIFETIME_MS;
        return timedOut;
    }
}
