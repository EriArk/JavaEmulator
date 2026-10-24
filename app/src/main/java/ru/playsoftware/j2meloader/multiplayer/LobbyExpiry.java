/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

/** Bounded tolerance for independent device/server clocks, not an expiry grace period. */
final class LobbyExpiry {
    static final long FUTURE_SKEW_SECONDS = 60;

    private LobbyExpiry() {}

    static boolean plausible(long deadline, long now, long lifetime) {
        if (deadline <= 0 || now < 0 || lifetime < 0
                || lifetime > Long.MAX_VALUE - FUTURE_SKEW_SECONDS) return false;
        return deadline <= now || deadline - now <= lifetime + FUTURE_SKEW_SECONDS;
    }

    static boolean live(long deadline, long now, long lifetime) {
        return deadline > now && plausible(deadline, now, lifetime);
    }
}
