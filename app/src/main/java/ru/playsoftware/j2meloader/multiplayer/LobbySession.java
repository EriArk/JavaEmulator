/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Host-side room authority, independent of Fluxer chat and any emulator process.
 * Call from one session thread, using authenticated sender identities supplied by transport.
 * A directory advertisement or invitation alone must never call admit().
 */
public final class LobbySession {
    public static final class Game {
        public final String engine, contentHash, mode, adapterVersion;
        public final int players;

        /** Construct only for a capability actually supplied by an installed engine adapter. */
        public Game(String engine, String contentHash, String mode, String adapterVersion, int players) {
            if (!token(engine) || contentHash == null || !contentHash.matches("[a-f0-9]{64}")
                    || !token(mode) || !token(adapterVersion) || players < 2 || players > 32) {
                throw new IllegalArgumentException("Invalid game capability");
            }
            this.engine = engine; this.contentHash = contentHash; this.mode = mode;
            this.adapterVersion = adapterVersion; this.players = players;
        }
        @Override public boolean equals(Object other) {
            if (!(other instanceof Game)) return false;
            Game g = (Game) other;
            return players == g.players && engine.equals(g.engine) && contentHash.equals(g.contentHash)
                    && mode.equals(g.mode) && adapterVersion.equals(g.adapterVersion);
        }
        @Override public int hashCode() { return Objects.hash(engine, contentHash, mode, adapterVersion, players); }
        private static boolean token(String value) { return value != null && value.matches("[A-Za-z0-9._-]{1,64}"); }
    }

    public static final class Launch {
        public final UUID room, attempt;
        public final long generation;
        public final Game game;
        /** Order assigns P1 to the host, P2 onwards to admitted players. */
        public final List<String> seats;
        private Launch(UUID room, long generation, Game game, List<String> seats) {
            this.room = room; this.generation = generation; this.game = game;
            this.seats = Collections.unmodifiableList(new ArrayList<>(seats));
            attempt = UUID.randomUUID();
        }
    }

    private static final class Member {
        boolean prepared, ready, connected = true;
    }
    private final Thread ownerThread = Thread.currentThread();
    private final UUID room;
    private final String host;
    private final Map<String, Member> members = new LinkedHashMap<>();
    private long generation = 1;
    private Game game;
    private Launch running;
    private boolean closed;

    public LobbySession(UUID room, String host) {
        if (room == null || room.equals(new UUID(0, 0)) || !TemporaryContacts.validId(host)) {
            throw new IllegalArgumentException("Invalid room identity");
        }
        this.room = room; this.host = host; members.put(host, new Member());
    }
    public UUID room() { checkThread(); return room; }
    public long generation() { checkThread(); return generation; }
    public List<String> members() { checkThread(); return Collections.unmodifiableList(new ArrayList<>(members.keySet())); }

    /** The admission/invitation layer has already verified the request and host approval. */
    public boolean admit(String authenticatedHost, String peer) {
        checkThread();
        if (!hostAction(authenticatedHost) || running != null || !TemporaryContacts.validId(peer)
                || members.containsKey(peer) || members.size() >= (game == null ? 32 : game.players)) return false;
        members.put(peer, new Member()); invalidate(); return true;
    }
    public boolean remove(String authenticatedActor, String peer) {
        checkThread();
        if (closed || !members.containsKey(peer) || (!host.equals(authenticatedActor) && !peer.equals(authenticatedActor))) return false;
        if (peer.equals(host)) { close(authenticatedActor); return true; }
        members.remove(peer); invalidate(); return true;
    }
    public boolean select(String authenticatedHost, Game selected) {
        checkThread();
        if (!hostAction(authenticatedHost) || running != null || (selected != null && members.size() > selected.players)) return false;
        if (!Objects.equals(game, selected)) { game = selected; invalidate(); }
        return true;
    }
    /** A success means local content, engine/API and current peer transport are all prepared. */
    public boolean prepared(String authenticatedPeer, long expectedGeneration, Game actual, boolean success) {
        checkThread();
        Member member = current(authenticatedPeer, expectedGeneration);
        if (member == null || running != null || game == null) return false;
        member.prepared = success && game.equals(actual);
        if (!member.prepared) member.ready = false;
        return member.prepared;
    }
    public boolean ready(String authenticatedPeer, long expectedGeneration, boolean value) {
        checkThread();
        Member member = current(authenticatedPeer, expectedGeneration);
        if (member == null || running != null || !member.connected || (value && !member.prepared)) return false;
        member.ready = value; return true;
    }
    /** Invoke after each state change. The returned launch is consumed once, not a UI flag. */
    public Launch takeLaunch() {
        checkThread();
        if (closed || running != null || game == null || members.size() < 2) return null;
        for (Member member : members.values()) if (!member.connected || !member.prepared || !member.ready) return null;
        running = new Launch(room, generation, game, new ArrayList<>(members.keySet()));
        return running;
    }
    public boolean connection(String authenticatedPeer, boolean connected) {
        checkThread();
        Member member = members.get(authenticatedPeer);
        if (closed || member == null) return false;
        if (member.connected != connected) { member.connected = connected; invalidate(); }
        return true;
    }
    /** Match termination is reported by the runtime, not inferred from leaving a chat/room. */
    public boolean finish(UUID attempt) {
        checkThread();
        if (running == null || !running.attempt.equals(attempt)) return false;
        running = null; invalidate(); return true;
    }
    public boolean close(String authenticatedHost) {
        checkThread();
        if (!hostAction(authenticatedHost)) return false;
        closed = true; invalidate(); return true;
    }
    private Member current(String peer, long expectedGeneration) {
        return !closed && generation == expectedGeneration ? members.get(peer) : null;
    }
    private boolean hostAction(String actor) { return !closed && host.equals(actor); }
    private void invalidate() {
        generation++;
        for (Member member : members.values()) { member.ready = member.prepared = false; }
    }
    private void checkThread() {
        if (Thread.currentThread() != ownerThread) throw new IllegalStateException("Use the lobby's owner thread");
    }
}
