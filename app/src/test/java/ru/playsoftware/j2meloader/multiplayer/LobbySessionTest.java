/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import org.junit.Test;
import java.util.Arrays;
import java.util.UUID;
import static org.junit.Assert.*;

public class LobbySessionTest {
    private static LobbySession.Game game(char hash) {
        char[] chars = new char[64]; Arrays.fill(chars, hash);
        return new LobbySession.Game("j2me",new String(chars),"jsr82-l2cap","1",2);
    }
    private static LobbySession room() {
        LobbySession room=new LobbySession(UUID.randomUUID(),"1");
        assertTrue(room.admit("1","2")); assertTrue(room.select("1",game('a'))); return room;
    }
    private static void prepare(LobbySession room) {
        for(String p : room.members()) assertTrue(room.prepared(p,room.generation(),game('a'),true));
    }
    @Test public void gameFreeRoomRequiresPreparationAndExplicitReady() {
        LobbySession room=new LobbySession(UUID.randomUUID(),"1"); room.admit("1","2");
        assertNull(room.takeLaunch()); assertFalse(room.ready("1",room.generation(),true));
        room.select("1",game('a')); prepare(room); assertNull(room.takeLaunch());
        room.ready("1",room.generation(),true); assertNull(room.takeLaunch());
        room.ready("2",room.generation(),true); LobbySession.Launch launch=room.takeLaunch(); assertNotNull(launch);
        assertEquals(Arrays.asList("1","2"),launch.seats); assertEquals(room.room(),launch.room); assertNull(room.takeLaunch());
        assertFalse(room.ready("2",room.generation(),true));
    }
    @Test public void changedGameRejectsOldCallbacksAndWrongEdition() {
        LobbySession room=room(); long old=room.generation(); prepare(room); room.ready("1",old,true);
        room.select("1",game('b'));
        assertFalse(room.prepared("2",old,game('a'),true)); assertFalse(room.ready("1",old,true));
        assertFalse(room.prepared("2",room.generation(),game('a'),true)); assertFalse(room.ready("2",room.generation(),true));
        assertNull(room.takeLaunch());
    }
    @Test public void strangerCannotSelectAdmitKickOrReady() {
        LobbySession room=room(); prepare(room);
        assertFalse(room.admit("3","4")); assertFalse(room.select("2",game('b')));
        assertFalse(room.remove("3","2")); assertFalse(room.close("2"));
        assertFalse(room.ready("3",room.generation(),true)); assertEquals(2,room.members().size());
    }
    @Test public void leavingOrReconnectingResetsReadyGeneration() {
        LobbySession room=room(); prepare(room); long previous=room.generation();
        room.ready("1",previous,true); room.ready("2",previous,true); room.connection("2",false);
        assertNull(room.takeLaunch()); assertFalse(room.ready("2",previous,true));
        room.connection("2",true); assertFalse(room.ready("2",room.generation(),true));
        room.remove("2","2"); assertNull(room.takeLaunch()); assertEquals(1,room.members().size());
    }
    @Test public void closingLobbyDoesNotPretendToStopRunningGame() {
        LobbySession room=room(); prepare(room);
        room.ready("1",room.generation(),true); room.ready("2",room.generation(),true);
        LobbySession.Launch launch=room.takeLaunch(); assertTrue(room.close("1"));
        assertFalse(room.finish(UUID.randomUUID())); assertTrue(room.finish(launch.attempt)); assertNull(room.takeLaunch());
    }
    @Test public void returningToRoomRequiresFreshReadyButKeepsMembersAndGame() {
        LobbySession room=room(); prepare(room);
        room.ready("1",room.generation(),true); room.ready("2",room.generation(),true);
        LobbySession.Launch first=room.takeLaunch(); assertTrue(room.finish(first.attempt)); assertNull(room.takeLaunch());
        prepare(room); room.ready("1",room.generation(),true); room.ready("2",room.generation(),true);
        LobbySession.Launch next=room.takeLaunch(); assertNotNull(next); assertNotEquals(first.attempt,next.attempt);
        assertTrue(next.generation>first.generation); assertEquals(first.seats,next.seats);
    }
    @Test public void fullRoomOrIncompatibleAdapterCannotLaunch() {
        LobbySession room=room(); assertFalse(room.admit("1","3"));
        LobbySession.Game wrong=new LobbySession.Game("j2me",game('a').contentHash,"jsr82-l2cap","2",2);
        assertFalse(room.prepared("2",room.generation(),wrong,true)); assertNull(room.takeLaunch());
    }
}
