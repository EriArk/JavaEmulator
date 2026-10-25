/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.peer;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/** Immutable two-player admission snapshot. The room authority, not a DM sender,
 * supplies these values. A new transport attempt always needs a fresh UUID. */
public final class PeerBinding {
    public final UUID room, attempt;
    public final long generation;
    public final List<String> seats;
    private final byte[] digest;

    public PeerBinding(UUID room, long generation, UUID attempt, String engine,
                       String gameHash, String mode, String adapterVersion, List<String> seats) {
        if (room == null || attempt == null || room.equals(new UUID(0, 0))
                || attempt.equals(new UUID(0, 0)) || generation < 1
                || !token(engine) || !token(mode) || !token(adapterVersion)
                || gameHash == null || !gameHash.matches("[a-f0-9]{64}")
                || seats == null || seats.size() != 2 || !id(seats.get(0))
                || !id(seats.get(1)) || seats.get(0).equals(seats.get(1))) {
            throw new IllegalArgumentException("Invalid admitted peer binding");
        }
        this.room = room; this.generation = generation; this.attempt = attempt;
        this.seats = Collections.unmodifiableList(new ArrayList<>(seats));
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("org.abyssme.peer.binding.v1");
            out.writeUTF(room.toString()); out.writeLong(generation); out.writeUTF(attempt.toString());
            out.writeUTF(engine); out.writeUTF(gameHash); out.writeUTF(mode); out.writeUTF(adapterVersion);
            out.writeUTF(seats.get(0)); out.writeUTF(seats.get(1)); out.flush();
            digest = MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray());
        } catch (IOException | NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    public String other(String self) {
        if (!seats.contains(self)) throw new IllegalArgumentException("Account not admitted");
        return seats.get(self.equals(seats.get(0)) ? 1 : 0);
    }
    public boolean host(String self) { other(self); return seats.get(0).equals(self); }
    public boolean sameSession(PeerBinding other) { return other != null && MessageDigest.isEqual(digest, other.digest); }
    byte[] digest() { return digest.clone(); }
    boolean matches(byte[] value) { return MessageDigest.isEqual(digest, value); }
    private static boolean token(String s) { return s != null && s.matches("[A-Za-z0-9._-]{1,64}"); }
    private static boolean id(String s) { return s != null && s.matches("[1-9][0-9]{15,19}"); }
}
