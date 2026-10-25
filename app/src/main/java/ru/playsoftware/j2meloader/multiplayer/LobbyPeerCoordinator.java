/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import io.github.eriark.abyssme.peer.PeerBinding;
import io.github.eriark.abyssme.peer.PeerSignals;
import javax.bluetooth.VirtualBluetooth;
import java.io.*;
import java.util.*;
import static ru.playsoftware.j2meloader.multiplayer.LobbyRoom.*;

/** Room-owner bridge. Local content verification is explicit and never inferred
 * from a peer claim, title, directory entry or Ready click. No implicit retry. */
final class LobbyPeerCoordinator implements Closeable {
    interface Link extends Closeable {
        void receive(String author,String content);
        void tick();
        void close();
    }
    interface Events {
        boolean signal(String recipient,List<String> fragments);
        void prepared(VirtualBluetooth adapter);
        void closed(String reason);
    }
    interface Factory { Link create(PeerBinding binding,String self,Events events)throws IOException; }
    static final Factory NATIVE=(binding,self,events)->{
        BluetoothPeerSession session=new BluetoothPeerSession(binding,self,null,new BluetoothPeerSession.Listener(){
            public boolean signal(String peer,List<String> frames){return events.signal(peer,frames);}
            public void prepared(VirtualBluetooth adapter){events.prepared(adapter);}
            public void closed(String reason){events.closed(reason);}
        });
        return new Link(){public void receive(String a,String c){session.receive(a,c);}public void tick(){session.tick();}public void close(){session.close();}};
    };
    private final String self;
    private final LobbyRoom room;
    private final Factory factory;
    private final Events events;
    private LobbySession.Game local,boundGame;
    private PeerBinding binding,failed;
    private PeerBinding waitingBinding;
    private final LinkedHashSet<String> waitingSignals=new LinkedHashSet<>();
    private long waitingUntil;
    private Link link;
    private Object incarnation;
    private boolean closed,refreshing;
    LobbyPeerCoordinator(String self,LobbyRoom room,Factory factory,Events events){this.self=self;this.room=room;this.factory=factory;this.events=events;}
    void localContent(LobbySession.Game verified){local=verified;refresh();}
    void refresh(){
        if(closed||refreshing)return;
        refreshing=true;
        try{refreshCurrent();}finally{refreshing=false;}
    }
    private void refreshCurrent(){
        PeerBinding current=room.peerBinding();
        if(waitingBinding!=null&&!same(waitingBinding,current)){waitingSignals.clear();waitingBinding=null;}
        LobbySession.Game selected=game(object(room.snapshot(),"game"));
        if(local==null||!local.equals(selected))current=null;
        if(same(binding,current))return;
        stop();
        if(current==null||same(failed,current))return;
        binding=current;boundGame=local;Object lease=new Object();incarnation=lease;PeerBinding attempt=current;
        try{
            Link made=factory.create(attempt,self,new Events(){
                boolean valid(){return !closed&&incarnation==lease&&same(binding,room.peerBinding())&&local!=null&&local.equals(game(object(room.snapshot(),"game")));}
                public boolean signal(String peer,List<String> fragments){return valid()&&events.signal(peer,fragments);}
                public void prepared(VirtualBluetooth adapter){
                    if(!valid())return;
                    room.prepared(attempt.generation,local,true);
                    if(valid())events.prepared(adapter);
                }
                public void closed(String reason){if(valid()){failed=attempt;stop();room.prepared(attempt.generation,local,false);events.closed(reason);}}
            });
            if(incarnation==lease&&!closed){
                link=made;
                if(same(waitingBinding,attempt)){
                    if(System.nanoTime()>=waitingUntil){failed=attempt;stop();events.closed("Game network preparation expired. Select the game again.");}
                    else for(String content:new ArrayList<>(waitingSignals)){if(link!=made)break;made.receive(attempt.other(self),content);}
                }
                waitingSignals.clear();waitingBinding=null;
            }else made.close();
        }catch(IOException|RuntimeException e){failed=attempt;stop();room.prepared(attempt.generation,local,false);events.closed("Could not prepare the game network. Select the game again to retry.");}
    }
    void receive(String author,String content){
        refresh();if(link!=null){link.receive(author,content);return;}
        PeerBinding authority=room.peerBinding();
        if(closed||authority==null||same(failed,authority)||!authority.other(self).equals(author)
                ||content==null||content.length()>2000||!content.startsWith(PeerSignals.PREFIX))return;
        if(!same(waitingBinding,authority)){waitingSignals.clear();waitingBinding=authority;waitingUntil=System.nanoTime()+120000000000L;}
        if(System.nanoTime()>=waitingUntil||!waitingSignals.contains(content)&&waitingSignals.size()>=128){
            failed=authority;waitingSignals.clear();events.closed("Game network preparation expired or exceeded its limit. Select the game again.");return;
        }
        waitingSignals.add(content);
    }
    void tick(){refresh();if(link!=null)link.tick();}
    private void stop(){
        Link old=link;PeerBinding previous=binding;LobbySession.Game game=boundGame;
        incarnation=null;link=null;binding=null;boundGame=null;if(old!=null)old.close();
        if(previous!=null&&game!=null&&room.available()&&number(room.snapshot(),"generation")==previous.generation
                &&bool(member(room.snapshot(),self),"prepared"))room.prepared(previous.generation,game,false);
    }
    private static boolean same(PeerBinding a,PeerBinding b){return a==null?b==null:a.sameSession(b);}
    @Override public void close(){if(closed)return;closed=true;stop();waitingSignals.clear();waitingBinding=null;}
}
