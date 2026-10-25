/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import android.app.Application;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;
import static ru.playsoftware.j2meloader.multiplayer.LobbyRoom.*;

/** Isolated owner/reentrant room callbacks; never reads credentials or opens a real room. */
public class LobbyRuntimeLifecycleDeviceTest {
    @Test public void hostProcessEndRevokesVerificationBeforeNewGeneration() throws Exception { checkReturn(true); }
    @Test public void guestRemoteEndDoesNotAutomaticallyPrepareAnotherProcess() throws Exception { checkReturn(false); }

    private void checkReturn(boolean host) throws Exception {
        Application app=(Application)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        LobbyRoomModel model=new LobbyRoomModel(app,new FluxerAuth.Store(){
            public String read(){return null;}public void save(String t){throw new AssertionError();}public void clear(){throw new AssertionError();}
        },(m,p,b,t,c)->{throw new AssertionError("No network in lifecycle fixture");},Collections.singleton("java/bluetooth/1"),a->null);
        ScheduledThreadPoolExecutor owner=(ScheduledThreadPoolExecutor)field(model,"owner");
        try {
            owner.submit(()->{
                String h="1563512156446527490",g="1563519321118879744",self=host?h:g;
                Set<String> caps=Collections.singleton("java/bluetooth/1");
                Map<String,LobbyRoom> rooms=new HashMap<>();ArrayDeque<Runnable> mail=new ArrayDeque<>();
                boolean[] observing={false};
                Runnable changed=()->{if(observing[0])call(model,"roomChanged");};
                LobbyRoom a=new LobbyRoom(h,()->1000,caps,(to,p)->mail.add(()->rooms.get(to).receive(h,p)),host?changed:()->{});
                LobbyRoom b=new LobbyRoom(g,()->1000,caps,(to,p)->mail.add(()->rooms.get(to).receive(g,p)),host?()->{}:changed);
                rooms.put(h,a);rooms.put(g,b);a.create("public");b.join(h,a.room(),"");drain(mail);
                LobbySession.Game game=new LobbySession.Game("java",String.join("",Collections.nCopies(64,"a")),"bluetooth","1",2);
                a.select(game);drain(mail);LobbyRoom local=host?a:b;
                int[] created={0};
                LobbyPeerCoordinator.Events events=new LobbyPeerCoordinator.Events(){
                    public boolean signal(String p,List<String> f){return true;}
                    public void prepared(javax.bluetooth.VirtualBluetooth v){}public void closed(String r){}
                };
                LobbyPeerCoordinator c=new LobbyPeerCoordinator(self,local,(binding,id,e)->{
                    created[0]++;return new LobbyPeerCoordinator.Link(){public void receive(String p,String s){}public void tick(){}public void close(){}};
                },events);
                set(model,"room",local);set(model,"self",self);set(model,"gameNetwork",c);
                c.localContent(game);
                a.prepared(number(a.snapshot(),"generation"),game,true);
                b.prepared(number(b.snapshot(),"generation"),game,true);drain(mail);
                a.ready(true);b.ready(true);drain(mail);assertTrue("Fixture must launch",a.start());drain(mail);
                String attempt=local.launchAttempt();assertFalse(attempt.isEmpty());
                set(model,"runtimeAttempt",attempt);set(model,"launchTaken",true);observing[0]=true;
                // A consumed/missing game lease, or a remote terminal snapshot, must
                // revoke localContent before nested callbacks can create a new link.
                if(host)call(model,"runtime");else{a.finish(attempt);drain(mail);}
                assertEquals("",field(model,"runtimeAttempt"));
                try {
                    Method ended=LobbyRoomModel.class.getDeclaredMethod("networkClosed",String.class);
                    ended.setAccessible(true);ended.invoke(model,"Peer disconnected. Rejoin the room to retry.");
                } catch(Exception e) { throw new AssertionError(e); }
                assertEquals("Game connection ended. Prepare and press Ready again.",field(model,"authNotice"));
                for(int i=0;i<5;i++)c.tick();
                assertEquals("No implicit replacement transport",1,created[0]);
                assertFalse(bool(member(local.snapshot(),self),"ready"));
                a.select(null);a.select(game);drain(mail);c.tick();
                assertEquals(1,created[0]);
                c.localContent(game);assertEquals("Explicit preparation opens the next attempt",2,created[0]);
                observing[0]=false;c.close();
            }).get(10,TimeUnit.SECONDS);
        } finally { model.onCleared();assertTrue(owner.awaitTermination(10,TimeUnit.SECONDS)); }
    }
    private static void drain(ArrayDeque<Runnable> mail){for(int i=0;!mail.isEmpty();i++){assertTrue(i<100);mail.remove().run();}}
    private static Object field(Object o,String name){try{Field f=LobbyRoomModel.class.getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(Exception e){throw new AssertionError(e);}}
    private static void set(Object o,String name,Object value){try{Field f=LobbyRoomModel.class.getDeclaredField(name);f.setAccessible(true);f.set(o,value);}catch(Exception e){throw new AssertionError(e);}}
    private static void call(Object o,String name){try{Method m=LobbyRoomModel.class.getDeclaredMethod(name);m.setAccessible(true);m.invoke(o);}catch(Exception e){throw new AssertionError(e);}}
}
