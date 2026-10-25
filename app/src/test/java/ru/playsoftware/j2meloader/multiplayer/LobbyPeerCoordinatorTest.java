/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import com.google.gson.*;
import io.github.eriark.abyssme.peer.PeerBinding;
import org.junit.Test;
import javax.bluetooth.VirtualBluetooth;
import java.util.*;
import static org.junit.Assert.*;
import static ru.playsoftware.j2meloader.multiplayer.LobbyRoom.*;

public class LobbyPeerCoordinatorTest {
    static final String H="1563512156446527490",G="1563519321118879744";
    static LobbySession.Game game(char hash){return new LobbySession.Game("java",String.join("",Collections.nCopies(64,""+hash)),"bluetooth","1",2);}
    static final class World {
        long now=1000;ArrayDeque<Runnable> mail=new ArrayDeque<>();Map<String,LobbyRoom> rooms=new HashMap<>();
        LobbyRoom add(String self){LobbyRoom r=new LobbyRoom(self,()->now,Collections.singleton("java/bluetooth/1"),(to,p)->mail.add(()->rooms.get(to).receive(self,p)),()->{});rooms.put(self,r);return r;}
        void drain(){for(int i=0;!mail.isEmpty();i++){assertTrue(i<100);mail.remove().run();}}
        World(){LobbyRoom h=add(H),g=add(G);h.create("public");g.join(H,h.room(),"");drain();h.select(game('a'));drain();}
    }
    static final class Fake implements LobbyPeerCoordinator.Factory,LobbyPeerCoordinator.Events {
        int created,stopped,prepared,failures,received;List<LobbyPeerCoordinator.Events> callbacks=new ArrayList<>();
        public LobbyPeerCoordinator.Link create(PeerBinding b,String self,LobbyPeerCoordinator.Events events){
            created++;callbacks.add(events);return new LobbyPeerCoordinator.Link(){boolean closed;
                public void tick(){}public void receive(String a,String c){received++;}public void close(){if(!closed){closed=true;stopped++;}}
            };
        }
        public boolean signal(String p,List<String> f){return true;}
        public void prepared(VirtualBluetooth adapter){prepared++;}
        public void closed(String reason){failures++;}
    }
    @Test public void bothAdmittedPlayersDeriveSameAttemptButInvitationDoesNot(){
        World w=new World();LobbyRoom h=w.rooms.get(H),g=w.rooms.get(G);
        assertTrue(h.peerBinding().sameSession(g.peerBinding()));
        LobbyRoom other=w.add("1563519321118879745");assertNull(other.peerBinding());
        h.invite("1563519321118879745");w.drain();assertNull(other.peerBinding());
    }
    @Test public void preparationRequiresExactLocalGameAndActualTransportHandshake(){
        World w=new World();LobbyRoom h=w.rooms.get(H);Fake f=new Fake();LobbyPeerCoordinator c=new LobbyPeerCoordinator(H,h,f,f);
        c.tick();assertEquals(0,f.created);c.localContent(game('b'));assertEquals(0,f.created);
        c.localContent(game('a'));assertEquals(1,f.created);h.ready(true);assertFalse(bool(member(h.snapshot(),H),"ready"));
        f.callbacks.get(0).prepared(null);assertTrue(bool(member(h.snapshot(),H),"prepared"));assertFalse(bool(member(h.snapshot(),H),"ready"));
        h.ready(true);assertTrue(bool(member(h.snapshot(),H),"ready"));c.close();assertFalse(bool(member(h.snapshot(),H),"prepared"));
    }
    @Test public void changedGameClosesOldLinkAndRejectsLateCallbacks(){
        World w=new World();LobbyRoom h=w.rooms.get(H);Fake f=new Fake();LobbyPeerCoordinator c=new LobbyPeerCoordinator(H,h,f,f);
        c.localContent(game('a'));LobbyPeerCoordinator.Events old=f.callbacks.get(0);PeerBinding previous=h.peerBinding();
        h.select(game('b'));w.drain();c.tick();assertEquals(1,f.stopped);assertFalse(previous.sameSession(h.peerBinding()));
        old.prepared(null);assertEquals(0,f.prepared);assertFalse(old.signal(G,Collections.singletonList("stale")));
        c.localContent(game('b'));assertEquals(2,f.created);c.close();
    }
    @Test public void remoteLeaveAndLocalVerificationLossRevokeReady(){
        for(boolean leave:new boolean[]{false,true}){
            World w=new World();LobbyRoom h=w.rooms.get(H);Fake f=new Fake();LobbyPeerCoordinator c=new LobbyPeerCoordinator(H,h,f,f);
            c.localContent(game('a'));f.callbacks.get(0).prepared(null);h.ready(true);
            if(leave){w.rooms.get(G).leave();w.drain();c.tick();}else c.localContent(null);
            assertEquals(1,f.stopped);assertFalse(bool(member(h.snapshot(),H),"ready"));c.close();
        }
    }
    @Test public void disconnectRequiresFreshAuthorityAttemptNotAnAutomaticLoop(){
        World w=new World();LobbyRoom h=w.rooms.get(H);Fake f=new Fake();LobbyPeerCoordinator c=new LobbyPeerCoordinator(H,h,f,f);
        c.localContent(game('a'));f.callbacks.get(0).prepared(null);f.callbacks.get(0).closed("Lost");
        for(int i=0;i<20;i++)c.tick();assertEquals(1,f.created);assertEquals(1,f.failures);
        h.select(null);h.select(game('a'));c.tick();assertEquals(2,f.created);c.close();
    }
    @Test public void expiredGuestAuthorityCannotRetainPreparedNetwork(){
        World w=new World();LobbyRoom g=w.rooms.get(G);Fake f=new Fake();LobbyPeerCoordinator c=new LobbyPeerCoordinator(G,g,f,f);
        c.localContent(game('a'));w.now+=46;c.tick();assertEquals(1,f.stopped);assertNull(g.peerBinding());c.close();
    }
    @Test public void anotherRoomAndClosedAccountCannotReviveOldCallbacks(){
        World w=new World();LobbyRoom h=w.rooms.get(H);Fake f=new Fake();LobbyPeerCoordinator c=new LobbyPeerCoordinator(H,h,f,f);
        c.localContent(game('a'));LobbyPeerCoordinator.Events old=f.callbacks.get(0);c.close();
        h.leave();h.create("public");old.prepared(null);old.closed("Late");assertEquals(0,f.prepared);assertEquals(0,f.failures);
        assertFalse(old.signal(G,Collections.singletonList("late")));c.tick();assertEquals(1,f.created);
    }
    @Test public void admittedEarlySignalsWaitForLocalVerificationAndAreDeduplicated(){
        World w=new World();LobbyRoom h=w.rooms.get(H);Fake f=new Fake();LobbyPeerCoordinator c=new LobbyPeerCoordinator(H,h,f,f);
        String frame=io.github.eriark.abyssme.peer.PeerSignals.PREFIX+"fixture";
        c.receive("1563519321118879745",frame);c.receive(G,frame);c.receive(G,frame);assertEquals(0,f.created);
        c.localContent(game('a'));assertEquals(1,f.created);assertEquals(1,f.received);c.close();
    }
    @Test public void changedAttemptDropsBufferedSignalsAndBufferIsBounded(){
        World w=new World();LobbyRoom h=w.rooms.get(H);Fake f=new Fake();LobbyPeerCoordinator c=new LobbyPeerCoordinator(H,h,f,f);
        String prefix=io.github.eriark.abyssme.peer.PeerSignals.PREFIX;c.receive(G,prefix+"old");
        h.select(game('b'));c.localContent(game('b'));assertEquals(0,f.received);c.close();
        Fake next=new Fake();LobbyPeerCoordinator d=new LobbyPeerCoordinator(H,h,next,next);
        for(int i=0;i<129;i++)d.receive(G,prefix+i);d.localContent(game('b'));
        assertEquals(0,next.created);assertEquals(1,next.failures);d.close();
    }
}
