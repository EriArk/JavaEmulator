/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.peer;

import org.junit.Test;
import java.io.IOException;
import java.util.*;
import static org.junit.Assert.*;

public class PeerSignalsTest {
    static final String HOST = "1563512156446527490", GUEST = "1563519321118879744";
    private static final UUID ROOM = UUID.fromString("01234567-1234-4321-8888-111111111111");
    private static final UUID ATTEMPT = UUID.fromString("01234567-1234-4321-8888-222222222222");
    static PeerBinding binding() { return binding(ROOM, 3, ATTEMPT, "java", hash('a'), "bt", "1", Arrays.asList(HOST, GUEST)); }
    private static PeerBinding binding(UUID room, long generation, UUID attempt, String engine, String hash, String mode, String adapter, List<String> seats) {
        return new PeerBinding(room, generation, attempt, engine, hash, mode, adapter, seats);
    }
    private static PeerSignals signals(PeerBinding b, String self) { return new PeerSignals(b, self, 1000); }
    private static String hash(char c) { char[] chars = new char[64]; Arrays.fill(chars, c); return new String(chars); }
    private static byte[] data(int size) { byte[] result = new byte[size]; new Random(size).nextBytes(result); return result; }

    @Test public void fragmentsRoundTripOutOfOrderUnderMailboxLimit() throws Exception {
        PeerSignals sender = signals(binding(), HOST), receiver = signals(binding(), GUEST);
        byte[] bytes = data(PeerSignals.MAX_BYTES);
        List<String> frames = sender.encode(1, bytes, 1000);
        Collections.reverse(frames);
        PeerSignals.Signal found = null;
        for (String frame : frames) {
            assertTrue(frame.length() <= 2000);
            PeerSignals.Signal next = receiver.accept(HOST, frame, 2000);
            if (next != null) { assertNull(found); found = next; }
        }
        assertNotNull(found); assertArrayEquals(bytes, found.data); assertEquals(1, found.kind);
        for (String frame : frames) assertNull(receiver.accept(HOST, frame, 2000));
    }
    @Test public void unauthenticatedAuthorsAndOtherNamespacesAreIgnored() throws Exception {
        String frame = signals(binding(), HOST).encode(1, data(5), 1000).get(0);
        PeerSignals receiver = signals(binding(), GUEST);
        assertNull(receiver.accept(GUEST, frame, 1000));
        assertNull(receiver.accept("9999999999999999999", frame, 1000));
        assertNull(receiver.accept(HOST, "TrainerOS peer\nwhatever", 1000));
        assertNotNull(receiver.accept(HOST, frame, 1000));
    }
    @Test public void everyBindingFieldIsFenced() throws Exception {
        String frame = signals(binding(), HOST).encode(1, data(5), 1000).get(0);
        List<PeerBinding> others = Arrays.asList(
            binding(UUID.randomUUID(), 3, ATTEMPT, "java", hash('a'), "bt", "1", Arrays.asList(HOST, GUEST)),
            binding(ROOM, 4, ATTEMPT, "java", hash('a'), "bt", "1", Arrays.asList(HOST, GUEST)),
            binding(ROOM, 3, UUID.randomUUID(), "java", hash('a'), "bt", "1", Arrays.asList(HOST, GUEST)),
            binding(ROOM, 3, ATTEMPT, "mophun", hash('a'), "bt", "1", Arrays.asList(HOST, GUEST)),
            binding(ROOM, 3, ATTEMPT, "java", hash('b'), "bt", "1", Arrays.asList(HOST, GUEST)),
            binding(ROOM, 3, ATTEMPT, "java", hash('a'), "other", "1", Arrays.asList(HOST, GUEST)),
            binding(ROOM, 3, ATTEMPT, "java", hash('a'), "bt", "2", Arrays.asList(HOST, GUEST)),
            binding(ROOM, 3, ATTEMPT, "java", hash('a'), "bt", "1", Arrays.asList(GUEST, HOST)));
        for (PeerBinding b : others) assertNull(signals(b, GUEST).accept(HOST, frame, 1000));
    }
    @Test public void expiryUsesLocalMonotonicAttemptAndCannotBeUndone() throws Exception {
        PeerSignals sender = signals(binding(), HOST);
        String frame = sender.encode(1, data(5), 1000).get(0);
        PeerSignals receiver = signals(binding(), GUEST);
        assertNull(receiver.accept(HOST, frame, 121000));
        assertNull(receiver.accept(HOST, frame, 1001));
        assertThrows(IOException.class, () -> sender.encode(1, data(5), 121000));
    }
    @Test public void differentDeviceClocksDoNotRejectAuthenticSignals() throws Exception {
        PeerSignals sender = new PeerSignals(binding(), HOST, 1000);
        PeerSignals receiver = new PeerSignals(binding(), GUEST, 900000000);
        String frame = sender.encode(1, data(5), 1001).get(0);
        assertArrayEquals(data(5), receiver.accept(HOST, frame, 900000010).data);
        String answer = receiver.encode(1, data(10), 900000100).get(0);
        assertArrayEquals(data(10), sender.accept(GUEST, answer, 1100).data);
    }
    @Test public void identicalDuplicatePartsDoNotCompleteEarly() throws Exception {
        List<String> frames = signals(binding(), HOST).encode(1, data(1000), 1000);
        PeerSignals receiver = signals(binding(), GUEST);
        for (int i = 0; i < 20; i++) assertNull(receiver.accept(HOST, frames.get(0), 1000));
        assertArrayEquals(data(1000), receiver.accept(HOST, frames.get(1), 1000).data);
    }
    @Test public void conflictingDuplicateFails() throws Exception {
        List<String> frames = signals(binding(), HOST).encode(1, data(1000), 1000);
        PeerSignals receiver = signals(binding(), GUEST);
        receiver.accept(HOST, frames.get(0), 1000);
        byte[] bytes = Base64.getDecoder().decode(frames.get(0).substring(PeerSignals.PREFIX.length()));
        bytes[bytes.length - 1] ^= 1;
        assertThrows(IOException.class, () -> receiver.accept(HOST, PeerSignals.PREFIX + Base64.getEncoder().encodeToString(bytes), 1000));
    }
    @Test public void malformedLengthsAndEncodingsFailClosed() throws Exception {
        PeerSignals receiver = signals(binding(), GUEST);
        assertThrows(IOException.class, () -> receiver.accept(HOST, PeerSignals.PREFIX + "!", 1000));
        assertThrows(IOException.class, () -> receiver.accept(HOST, PeerSignals.PREFIX, 1000));
        String frame = signals(binding(), HOST).encode(1, data(10), 1000).get(0);
        byte[] bytes = Base64.getDecoder().decode(frame.substring(PeerSignals.PREFIX.length()));
        bytes[43] = (byte) 255;
        assertThrows(IOException.class, () -> receiver.accept(HOST, PeerSignals.PREFIX + Base64.getEncoder().encodeToString(bytes), 1000));
    }
    @Test public void incompleteMessageBudgetIsBoundedAndExpires() throws Exception {
        PeerSignals sender = signals(binding(), HOST), receiver = signals(binding(), GUEST);
        for (int i = 0; i < 8; i++) assertNull(receiver.accept(HOST, sender.encode(1, data(1000), 1000).get(0), 1000));
        assertThrows(IOException.class, () -> receiver.accept(HOST, sender.encode(1, data(1000), 1000).get(0), 1000));
        assertNull(receiver.accept(HOST, sender.encode(1, data(10), 1000).get(0), 200000));
    }
    @Test public void incompleteByteBudgetIsBounded() throws Exception {
        PeerSignals sender = signals(binding(), HOST), receiver = signals(binding(), GUEST);
        for (int i = 0; i < 2; i++) receiver.accept(HOST, sender.encode(1, data(24576), 1000).get(0), 1000);
        assertThrows(IOException.class, () -> receiver.accept(HOST, sender.encode(1, data(24576), 1000).get(0), 1000));
    }
    @Test public void senderCannotAllocateArbitrarySizesOrSequences() throws Exception {
        PeerSignals sender = signals(binding(), HOST);
        assertThrows(IOException.class, () -> sender.encode(1, data(24577), 1000));
        assertThrows(IOException.class, () -> sender.encode(1, new byte[0], 1000));
        assertThrows(IOException.class, () -> sender.encode(3, data(1), 1000));
        for (int i = 0; i < 128; i++) sender.encode(2, data(1), 1000);
        assertThrows(IOException.class, () -> sender.encode(2, data(1), 1000));
    }
    @Test public void admissionSnapshotIsImmutableAndTwoPlayerOnly() {
        List<String> seats = new ArrayList<>(Arrays.asList(HOST, GUEST));
        PeerBinding b = binding(ROOM, 3, ATTEMPT, "java", hash('a'), "bt", "1", seats);
        seats.clear(); assertEquals(2, b.seats.size());
        assertThrows(UnsupportedOperationException.class, () -> b.seats.clear());
        byte[] digest = b.digest(); digest[0] ^= 1; assertFalse(b.matches(digest));
        assertThrows(IllegalArgumentException.class, () -> b.other("9999999999999999999"));
        assertThrows(IllegalArgumentException.class, () -> binding(ROOM, 3, ATTEMPT, "java", hash('a'), "bt", "1", Arrays.asList(HOST, HOST)));
        assertThrows(IllegalArgumentException.class, () -> binding(ROOM, 3, ATTEMPT, "java", hash('a'), "bt", "1", Arrays.asList(HOST, GUEST, "9999999999999999999")));
    }
}
