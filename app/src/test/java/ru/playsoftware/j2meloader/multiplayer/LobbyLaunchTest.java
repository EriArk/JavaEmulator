/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import com.google.gson.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
import static ru.playsoftware.j2meloader.multiplayer.LobbyRoom.*;

public class LobbyLaunchTest {
    static final String A="1563512156446527490",B="1563519321118879744",C="1563519321118879745";
    static final LobbySession.Game GAME=new LobbySession.Game("java",String.join("",Collections.nCopies(64,"a")),"bluetooth","1",2);
    static final class World {
        long wall=1000,mono=10;
        final ArrayDeque<LobbyRoomTest.Mail> mail=new ArrayDeque<>();
        final LobbyRoom host=client(A),guest=client(B);
        LobbyRoom client(String id){return new LobbyRoom(id,()->wall,()->mono,Collections.singleton("java/bluetooth/1"),(p,m)->mail.add(new LobbyRoomTest.Mail(id,p,m)),()->{});}
        World(){host.create("public");guest.join(A,host.room(),"");deliver();host.select(GAME);deliver();}
        void deliver(){int count=0;while(!mail.isEmpty()){assertTrue(++count<200);LobbyRoomTest.Mail m=mail.remove();(m.to.equals(A)?host:guest).receive(m.from,m.packet);}}
        void ready(){long gen=number(host.snapshot(),"generation");host.prepared(gen,GAME,true);guest.prepared(gen,GAME,true);deliver();host.ready(true);guest.ready(true);deliver();}
        String start(){ready();assertTrue(host.start());deliver();return host.launchAttempt();}
    }
    @Test public void explicitHostStartRequiresBothReadyAndUsesTransportAttempt(){
        World w=new World();assertFalse(w.host.start());w.ready();assertFalse(w.guest.start());
        String transport=w.host.peerBinding().attempt.toString();assertTrue(w.host.start());w.deliver();
        assertEquals(transport,w.guest.launchAttempt());assertFalse(w.host.start());assertFalse(w.host.running());
    }
    @Test public void neitherProcessStartsUntilBothAcknowledge(){
        World w=new World();String attempt=w.start();w.host.attached(attempt);w.deliver();assertFalse(w.guest.running());
        w.guest.attached(attempt);w.deliver();assertTrue(w.host.running());assertTrue(w.guest.running());
        long revision=number(w.host.snapshot(),"revision");w.guest.attached(attempt);w.deliver();assertEquals(revision,number(w.host.snapshot(),"revision"));
    }
    @Test public void forgedAndStaleAttachmentCannotReleaseBarrier(){
        World w=new World();String attempt=w.start();w.host.attached(attempt);w.deliver();w.guest.attached(attempt);
        LobbyRoomTest.Mail m=w.mail.remove();w.host.receive(C,m.packet);assertFalse(w.host.running());
        JsonObject stale=m.packet.deepCopy();object(stale,"data").addProperty("attempt",uuid());w.host.receive(B,stale);assertFalse(w.host.running());
        w.guest.attached(attempt);w.deliver();assertTrue(w.host.running());
    }
    @Test public void cancellationReturnsSameRoomButRequiresFreshPreparationAndReady(){
        World w=new World();String attempt=w.start(),room=w.host.room();long gen=number(w.host.snapshot(),"generation");
        w.guest.finish(attempt);assertEquals("",w.guest.launchAttempt());assertNull(w.guest.peerBinding());w.deliver();
        assertEquals(room,w.guest.room());assertTrue(number(w.host.snapshot(),"generation")>gen);assertFalse(w.host.start());
        assertFalse(bool(member(w.host.snapshot(),A),"prepared"));String next=w.start();assertNotEquals(attempt,next);
        w.host.finish(attempt);assertEquals(next,w.host.launchAttempt());
    }
    @Test public void monotonicTimeoutWorksEvenWhenWallClockMovesBackwards(){
        World w=new World();w.start();w.wall-=500;w.mono+=31;w.host.tick();assertEquals("",w.host.launchAttempt());assertFalse(w.host.start());
    }
    @Test public void guestTimeoutRevokesLocallyWithoutWaitingForHost(){
        World w=new World();w.start();w.mono+=31;w.guest.tick();assertEquals("",w.guest.launchAttempt());assertNull(w.guest.peerBinding());
    }
    @Test public void unavailableHostCanBeCancelledWithoutResumingOldLaunchOnRecovery(){
        World w=new World();String old=w.start();w.wall+=46;assertFalse(w.guest.available());
        w.guest.finish(old);assertEquals("",w.guest.launchAttempt());
        // Delay the terminal control while a new heartbeat still carries the old launch.
        w.mail.clear();w.host.access("private");w.deliver();assertTrue(w.guest.available());
        assertEquals("",w.guest.launchAttempt());assertNull(w.guest.peerBinding());
    }
    @Test public void disconnectCancelsLaunchAndLaterReconnectionCannotResumeIt(){
        World w=new World();String old=w.start();w.host.deliveryFailed(B);w.deliver();assertEquals("",w.host.launchAttempt());
        w.guest.tick();w.deliver();assertEquals("",w.guest.launchAttempt());w.host.attached(old);assertFalse(w.host.running());
    }
    @Test public void leavingOrBackgroundingCancelsPendingLaunch(){
        for(boolean leave:new boolean[]{false,true}){World w=new World();w.start();if(leave)w.guest.leave();else w.guest.active(false);w.deliver();assertEquals("",w.host.launchAttempt());}
    }
    @Test public void malformedBarrierAndHostSeatOrderAreRejected(){
        World w=new World();w.start();JsonObject valid=w.host.snapshot();assertTrue(validState(valid,A,w.host.room(),B));
        for(int kind=0;kind<4;kind++){
            JsonObject s=valid.deepCopy(),l=object(s,"launch");
            if(kind==0)l.addProperty("running",true);
            if(kind==1)l.addProperty("attempt",uuid());
            if(kind==2){JsonArray a=new JsonArray();a.add(C);l.add("attached",a);}
            if(kind==3){JsonArray a=s.getAsJsonArray("members");JsonElement first=a.remove(0);a.add(first);}
            assertFalse(validState(s,A,w.host.room(),B));
        }
    }
    @Test public void runningMatchHasNoLaunchTimeoutAndFinishResetsBothSeats(){
        World w=new World();String attempt=w.start();w.host.attached(attempt);w.guest.attached(attempt);w.deliver();
        w.mono+=1000;w.host.tick();w.deliver();assertTrue(w.host.running());w.host.finish(attempt);w.deliver();
        assertEquals("",w.guest.launchAttempt());assertFalse(bool(member(w.guest.snapshot(),B),"ready"));
    }
}
