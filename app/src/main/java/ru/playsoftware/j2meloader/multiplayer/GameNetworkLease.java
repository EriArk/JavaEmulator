/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import android.os.*;
import io.github.eriark.abyssme.peer.PeerBinding;
import java.io.*;
import java.util.*;

/** Single-use local launch authority. The room owner must retain it on every authority change. */
public final class GameNetworkLease implements PeerPacketPort {
    interface Verification { void recheck(String path) throws IOException; }
    final String ticket = UUID.randomUUID().toString();
    final InstalledNetworkGame installed;
    final boolean host;
    private final String account;
    private final PeerBinding binding;
    private final Verification verification;
    private final LocalPeerPipe pipe;
    private ParcelFileDescriptor pending;
    private IBinder lifetime;
    private final IBinder.DeathRecipient death = this::close;
    private boolean offered, claiming, claimed, closed;
    private long deadline = SystemClock.elapsedRealtime() + 120000;

    public GameNetworkLease(PeerBinding binding, String account, InstalledNetworkGame installed) throws IOException {
        this(binding, account, installed, installed::recheck);
    }
    GameNetworkLease(PeerBinding binding, String account, InstalledNetworkGame installed, Verification verification) throws IOException {
        this.binding = Objects.requireNonNull(binding); this.account = account; this.installed = installed;
        this.verification = Objects.requireNonNull(verification);
        LobbySession.Game game = installed.game;
        PeerBinding expected = new PeerBinding(binding.room, binding.generation, binding.attempt,
                game.engine, game.contentHash, game.mode, game.adapterVersion, binding.seats);
        if (!binding.sameSession(expected)) throw new IOException("Wrong game for peer binding");
        host = binding.host(account);
        ParcelFileDescriptor[] pair = ParcelFileDescriptor.createReliableSocketPair();
        pending = pair[1];
        try { pipe = new LocalPeerPipe(pair[0]); }
        catch (IOException e) { pending.close(); throw e; }
    }
    synchronized void offer() throws IOException {
        check(); if (offered) throw new IOException("Launch already offered");
        offered = true; deadline = SystemClock.elapsedRealtime() + 30000;
    }
    ParcelFileDescriptor claim(String ticket, String path, IBinder lifetime) throws IOException {
        synchronized (this) {
            check();
            if (!offered || claiming || claimed || !this.ticket.equals(ticket) || lifetime == null) throw new IOException("Launch is not available");
            claiming = true;
        }
        try {
            // Hashing may be slow. Do not stall the room's packet pump or prevent revocation.
            verification.recheck(path);
            synchronized (this) {
                check();
                try { lifetime.linkToDeath(death, 0); } catch (RemoteException e) { close(); throw new IOException("Game process ended", e); }
                this.lifetime = lifetime; claimed = true;
                ParcelFileDescriptor result = pending; pending = null; return result;
            }
        } finally { synchronized (this) { claiming = false; } }
    }
    public synchronized boolean retain(String account, PeerBinding binding, InstalledNetworkGame installed) {
        if (!this.account.equals(account) || binding == null || !this.binding.sameSession(binding) || this.installed != installed) close();
        return !closed;
    }
    public synchronized boolean attached() { return claimed && !closed; }
    @Override public synchronized void receive(byte[] packet) throws IOException { check(); pipe.receive(packet); }
    @Override public synchronized byte[] pollOutbound() throws IOException { check(); return pipe.pollOutbound(); }
    private void check() throws IOException {
        if (!claimed && SystemClock.elapsedRealtime() >= deadline) close();
        if (closed) throw new IOException("Game launch ended or expired");
    }
    @Override public synchronized void close() {
        if (closed) return; closed = true;
        if (lifetime != null) { lifetime.unlinkToDeath(death, 0); lifetime = null; }
        pipe.close();
        if (pending != null) { try { pending.close(); } catch (IOException ignored) { } pending = null; }
        GameNetworkService.remove(this);
    }
}
