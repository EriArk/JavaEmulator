/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import org.junit.Test;
import static org.junit.Assert.*;

public class LobbyExpiryTest {
    @Test public void boundedFutureSkewDoesNotReviveExpiredMessages() {
        for (long ttl : new long[]{90, 300}) {
            assertTrue(LobbyExpiry.live(1000 + ttl + 60, 1000, ttl));
            assertFalse(LobbyExpiry.live(1000 + ttl + 61, 1000, ttl));
            assertFalse(LobbyExpiry.live(1000, 1000, ttl));
            assertFalse(LobbyExpiry.live(999, 1000, ttl));
            assertTrue(LobbyExpiry.plausible(999, 1000, ttl));
        }
    }
    @Test public void malformedAndOverflowingDeadlinesAreRejected() {
        assertFalse(LobbyExpiry.live(Long.MAX_VALUE, 1000, 90));
        assertFalse(LobbyExpiry.live(1090, -1, 90));
        assertFalse(LobbyExpiry.live(1090, 1000, Long.MAX_VALUE));
        assertFalse(LobbyExpiry.plausible(0, 1000, 90));
        assertTrue(LobbyExpiry.live(Long.MAX_VALUE, Long.MAX_VALUE - 90, 90));
    }
}
