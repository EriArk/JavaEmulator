/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import com.google.gson.*;
import io.github.eriark.abyssme.peer.PeerBinding;
import java.util.*;
import java.util.function.LongSupplier;

/** Android adaptation of TrainerOS host admission, offer and incarnation fencing.
 * Authenticated DM authorship is supplied by the mailbox, never by packet fields.
 * Single owner thread. No directory advertisement grants membership.
 */
public final class LobbyRoom {
    public static final String PREFIX="AbyssME lobby\n```json\n";
    public interface Sender { void send(String peer,JsonObject packet); }
    private final String self,boot=uuid();
    private final LongSupplier clock;
    private final Sender sender;
    private final Runnable changed;
    private final Set<String> capabilities;
    private LobbySession session;
    private String host="",room="",access="private",probe="",hostBoot="",notice="Create a room or accept an invitation.";
    private JsonObject remote=new JsonObject();
    private long serial,revision=1,lastHost,nextBeat,joiningAt;
    private long peerGeneration;
    private String peerAttempt="";
    private boolean active=true;
    private final Map<String,Long> replay=new LinkedHashMap<>(),seen=new HashMap<>();
    private final Map<String,String> peerBoots=new HashMap<>(),requests=new LinkedHashMap<>();
    private final Map<String,JsonObject> offers=new LinkedHashMap<>(),invitations=new LinkedHashMap<>();

    public LobbyRoom(String self,LongSupplier seconds,Set<String> capabilities,Sender sender,Runnable changed){
        if(!LobbyDirectory.id(self))throw new IllegalArgumentException("Account required");
        this.self=self;clock=seconds;this.capabilities=new HashSet<>(capabilities);this.sender=sender;this.changed=changed;
    }
    public boolean hosting(){return session!=null;}
    public boolean joined(){return hosting()||!remote.entrySet().isEmpty();}
    public String room(){return room;}
    public String host(){return host;}
    public String notice(){return notice;}
    public String access(){return hosting()?access:text(remote,"access");}
    public Map<String,String> requests(){return Collections.unmodifiableMap(new LinkedHashMap<>(requests));}
    public List<JsonObject> invitations(){List<JsonObject> out=new ArrayList<>();for(JsonObject i:invitations.values())out.add(i.deepCopy());return out;}
    public JsonObject snapshot(){return hosting()?hostState():remote.deepCopy();}
    /** Authenticated admitted room state only. Never construct authority from an ICE offer. */
    public PeerBinding peerBinding(){
        if(!available()||!joined())return null;
        JsonObject state=snapshot();LobbySession.Game game=game(object(state,"game"));
        if(!peerGame(game)||!capabilities.contains(capability(game))||!uuid(text(state,"peerAttempt")))return null;
        List<String> seats=new ArrayList<>();
        JsonArray members=state.getAsJsonArray("members");
        if(members==null||members.size()!=2)return null;
        for(JsonElement element:members){JsonObject m=element.getAsJsonObject();if(!bool(m,"connected"))return null;seats.add(text(m,"id"));}
        if(!seats.get(0).equals(host)||!seats.contains(self))return null;
        try{return new PeerBinding(UUID.fromString(room),number(state,"generation"),UUID.fromString(text(state,"peerAttempt")),game.engine,game.contentHash,game.mode,game.adapterVersion,seats);}
        catch(IllegalArgumentException invalid){return null;}
    }
    private static boolean peerGame(LobbySession.Game game){return game!=null&&game.players==2&&game.engine.equals("java")&&game.mode.equals("bluetooth")&&game.adapterVersion.equals("1");}
    public boolean available(){return active&&(hosting()||joined()&&clock.getAsLong()-lastHost<45);}
    private void emit(){changed.run();}
    private void info(String text){notice=text;emit();}
    public void active(boolean value){
        active=value;
        if(!value&&hosting())for(String peer:session.members())session.connection(peer,false);
        if(value&&hosting())session.connection(self,true);
        nextBeat=0;emit();
    }
    public boolean create(String policy){
        if(!active||!room.isEmpty()||!policy(policy))return false;
        room=uuid();host=self;access=policy;session=new LobbySession(UUID.fromString(room),self);revision=1;
        notice="Room created. Invite someone to join.";emit();return true;
    }
    public void access(String value){if(hosting()&&active&&policy(value)){access=value;broadcast();}}
    public void invite(String peer){
        if(!hosting()||!active||!LobbyDirectory.id(peer)||self.equals(peer)||offers.size()>=32||session.members().contains(peer))return;
        JsonObject offer=new JsonObject();offer.addProperty("offer",uuid());offer.addProperty("until",clock.getAsLong()+300);
        offers.put(peer,offer);send(peer,"invite",offer,room,"");info("Invitation pending. Waiting for an answer.");
    }
    public void answer(String key,boolean accept){
        JsonObject invitation=invitations.remove(key);if(invitation==null)return;
        if(accept&&number(invitation,"until")>clock.getAsLong())join(text(invitation,"host"),text(invitation,"lobby"),text(invitation,"offer"));
        else emit();
    }
    public void join(String peer,String id,String offer){
        if(!active||!room.isEmpty()||!LobbyDirectory.id(peer)||peer.equals(self)||!uuid(id))return;
        host=peer;room=id;probe=uuid();hostBoot="";joiningAt=lastHost=clock.getAsLong();remote=new JsonObject();
        JsonObject data=new JsonObject();data.addProperty("probe",probe);if(uuid(offer))data.addProperty("offer",offer);
        send(host,"join",data,room,"");info("Waiting for the host...");
    }
    public void approve(String peer,boolean yes){
        if(!hosting()||!active||!requests.containsKey(peer))return;
        String reply=requests.remove(peer);
        if(yes&&session.members().size()<16&&session.admit(self,peer)){
            notice="Player joined the room.";
            seen.put(peer,clock.getAsLong());send(peer,"state",hostState(),room,reply);broadcast();
        }else{send(peer,"rejected",new JsonObject(),room,reply);emit();}
    }
    public void remove(String peer){
        if(!hosting()||!active||self.equals(peer)||!session.members().contains(peer))return;
        send(peer,"removed",new JsonObject(),room,"");session.remove(self,peer);seen.remove(peer);broadcast();
    }
    public void leave(){
        if(hosting()){
            Set<String> targets=new HashSet<>(session.members());targets.addAll(offers.keySet());targets.addAll(requests.keySet());targets.remove(self);
            for(String peer:targets)send(peer,"closed",new JsonObject(),room,"");session.close(self);
        }else if(!room.isEmpty())send(host,"leave",new JsonObject(),room,"");
        clear();info("You left the room.");
    }
    private void clear(){session=null;room=host=hostBoot=probe=peerAttempt="";peerGeneration=0;remote=new JsonObject();offers.clear();requests.clear();seen.clear();peerBoots.clear();joiningAt=0;}
    public void deliveryFailed(String peer){
        deliveryFailed(peer,"Could not reach this player. Check Fluxer access and refresh.");
    }
    void deliveryFailed(String peer,String reason){
        if(hosting()&&session.isConnected(peer)){session.connection(peer,false);broadcast();}
        if(peer.equals(host)&&!hosting())lastHost=clock.getAsLong()-46;
        info(reason);
    }
    public boolean select(LobbySession.Game game){
        if(!hosting()||!active||game!=null&&(!capabilities.contains(capability(game))||!wireGame(game)))return false;
        boolean ok=session.select(self,game);if(ok)broadcast();return ok;
    }
    public boolean prepared(long generation,LobbySession.Game game,boolean success){
        if(!available()||game==null||!capabilities.contains(capability(game)))return false;
        if(hosting()){boolean ok=session.prepared(self,generation,game,success);broadcast();return ok;}
        if(number(remote,"generation")!=generation||!game.equals(game(object(remote,"game"))))return false;
        JsonObject data=new JsonObject();data.addProperty("generation",generation);data.addProperty("prepared",success);
        send(host,"prepared",data,room,"");return true;
    }
    public void ready(boolean ready){
        if(!available())return;
        if(hosting()){session.ready(self,session.generation(),ready);broadcast();return;}
        JsonObject mine=member(remote,self);if(ready&&!bool(mine,"prepared"))return;
        JsonObject data=new JsonObject();data.addProperty("generation",number(remote,"generation"));data.addProperty("ready",ready);send(host,"ready",data,room,"");
    }
    public void receive(String peer,JsonObject p){
        long now=clock.getAsLong(),seq=number(p,"serial"),expiry=number(p,"expires");
        String id=text(p,"lobby"),incarnation=text(p,"boot"),kind=text(p,"kind");
        if(!active||!LobbyDirectory.id(peer)||peer.equals(self)||!text(p,"ns").equals("org.abyssme.lobby")||number(p,"v")!=1
                ||!uuid(id)||!uuid(incarnation)||seq<1||!LobbyExpiry.live(expiry,now,90)||p.toString().length()>1900)return;
        String key=peer+"/"+incarnation;
        if(seq<=replay.getOrDefault(key,0L))return;
        if(!replay.containsKey(key)&&replay.size()>=512)return;
        JsonObject data=object(p,"data");
        // The directory can briefly retain a room after its host restarted or
        // closed it. Answer the exact join attempt without exposing another room.
        if(kind.equals("join")&&(!hosting()||!id.equals(room))&&uuid(text(data,"probe"))){
            replay.put(key,seq);send(peer,"unavailable",new JsonObject(),id,text(data,"probe"));return;
        }
        // A fresh process rereads DM history. A later host admission/terminal
        // packet consumes its earlier offer without restoring old membership.
        if(!id.equals(room)&&(kind.equals("closed")||kind.equals("removed")
                ||kind.equals("state")&&validState(data,peer,id,self))){
            replay.put(key,seq);
            JsonObject old=invitations.get(peer+"/"+id);
            if(old!=null&&text(old,"boot").equals(incarnation)){invitations.remove(peer+"/"+id);emit();}
            return;
        }
        if(kind.equals("invite")){
            if(!uuid(text(data,"offer"))||!LobbyExpiry.live(number(data,"until"),now,300)||invitations.size()>=32)return;
            replay.put(key,seq);JsonObject invitation=data.deepCopy();invitation.addProperty("host",peer);invitation.addProperty("lobby",id);invitation.addProperty("key",peer+"/"+id);invitation.addProperty("boot",incarnation);
            invitations.put(peer+"/"+id,invitation);emit();return;
        }
        if(kind.equals("closed")){
            JsonObject offer=invitations.get(peer+"/"+id);if(offer!=null&&text(offer,"boot").equals(incarnation)){invitations.remove(peer+"/"+id);emit();}
        }
        if(!id.equals(room))return;
        if(hosting()){
            if(kind.equals("leave")&&requests.containsKey(peer)&&incarnation.equals(peerBoots.get(peer))){replay.put(key,seq);requests.remove(peer);emit();return;}
            if(kind.equals("join")){
                String reply=text(data,"probe");if(!uuid(reply))return;
                String oldBoot=peerBoots.get(peer);
                // A replacement process must be re-invited; old messages cannot switch an admitted incarnation.
                if(oldBoot!=null&&!oldBoot.equals(incarnation)){send(peer,"rejected",new JsonObject(),room,reply);return;}
                replay.put(key,seq);peerBoots.put(peer,incarnation);
                JsonObject offer=offers.get(peer);boolean invited=offer!=null&&number(offer,"until")>now&&text(offer,"offer").equals(text(data,"offer"));
                if(session.members().contains(peer)||session.members().size()<16&&(invited||access.equals("public"))&&session.admit(self,peer)){
                    notice="Player joined the room.";
                    offers.remove(peer);requests.remove(peer);seen.put(peer,now);session.connection(peer,true);send(peer,"state",hostState(),room,reply);broadcast();
                }else if(access.equals("approval")&&requests.size()<32){requests.put(peer,reply);seen.put(peer,now);send(peer,"pending",new JsonObject(),room,reply);emit();}
                else send(peer,"rejected",new JsonObject(),room,reply);
                return;
            }
            if(!session.members().contains(peer)||!incarnation.equals(peerBoots.get(peer)))return;
            replay.put(key,seq);seen.put(peer,now);
            if(kind.equals("leave")){session.remove(self,peer);seen.remove(peer);broadcast();return;}
            if(kind.equals("ping")){boolean reconnected=!session.isConnected(peer);session.connection(peer,true);if(reconnected)broadcast();return;}
            if(kind.equals("prepared")&&data.has("prepared")&&data.get("prepared").isJsonPrimitive()&&data.get("prepared").getAsJsonPrimitive().isBoolean())session.prepared(peer,number(data,"generation"),session.game(),bool(data,"prepared"));
            if(kind.equals("ready")&&data.has("ready")&&data.get("ready").isJsonPrimitive()&&data.get("ready").getAsJsonPrimitive().isBoolean())session.ready(peer,number(data,"generation"),bool(data,"ready"));
            broadcast();return;
        }
        if(!peer.equals(host)||!hostBoot.isEmpty()&&!hostBoot.equals(incarnation))return;
        boolean correlated=!probe.isEmpty()&&text(p,"reply").equals(probe);
        if(hostBoot.isEmpty()&&!correlated)return;
        if(kind.equals("unavailable")&&!joined()&&correlated){
            replay.put(key,seq);clear();info("This room is no longer available. Refresh Lobbies.");return;
        }
        if(kind.equals("pending")){replay.put(key,seq);info("Waiting for host approval...");return;}
        if(Arrays.asList("closed","removed","rejected").contains(kind)){replay.put(key,seq);clear();info("The room is closed or your request was declined.");return;}
        if(!kind.equals("state")||!validState(data,host,id,self)||number(data,"revision")<number(remote,"revision"))return;
        replay.put(key,seq);hostBoot=incarnation;remote=data.deepCopy();lastHost=now;joiningAt=0;probe="";notice="Connected to room";emit();
    }
    public void tick(){
        long now=clock.getAsLong();boolean expired=invitations.values().removeIf(i->number(i,"until")<=now);offers.values().removeIf(i->number(i,"until")<=now);
        if(expired)emit();if(!active)return;
        if(joiningAt>0&&now-joiningAt>90){clear();info("The host did not answer. Try joining again.");}
        if(now<nextBeat)return;nextBeat=now+20;
        if(hosting()){
            requests.keySet().removeIf(p->now-seen.getOrDefault(p,0L)>90);
            for(String peer:session.members())if(!peer.equals(self)&&now-seen.getOrDefault(peer,0L)>45)session.connection(peer,false);
            broadcast();
        }else if(joined()){
            JsonObject data=new JsonObject();data.addProperty("probe",uuid());send(host,"ping",data,room,"");
            if(now-lastHost>45)info("Host unavailable. Waiting to reconnect...");
        }
    }
    private void broadcast(){revision++;JsonObject state=hostState();for(String peer:session.members())if(!peer.equals(self))send(peer,"state",state,room,"");emit();}
    private JsonObject hostState(){
        JsonObject s=new JsonObject();s.addProperty("id",room);s.addProperty("host",self);s.addProperty("access",access);s.addProperty("revision",revision);s.addProperty("generation",session.generation());
        JsonArray members=new JsonArray();for(String peer:session.members()){JsonObject m=new JsonObject();m.addProperty("id",peer);m.addProperty("prepared",session.isPrepared(peer));m.addProperty("ready",session.isReady(peer));m.addProperty("connected",session.isConnected(peer));members.add(m);}s.add("members",members);
        if(session.game()!=null)s.add("game",game(session.game()));
        if(peerGame(session.game())&&session.members().size()==2){
            if(peerGeneration!=session.generation()){peerGeneration=session.generation();peerAttempt=uuid();}
            s.addProperty("peerAttempt",peerAttempt);
        }
        return s;
    }
    private void send(String peer,String kind,JsonObject data,String id,String reply){
        JsonObject p=new JsonObject();p.addProperty("ns","org.abyssme.lobby");p.addProperty("v",1);p.addProperty("lobby",id);p.addProperty("boot",boot);p.addProperty("serial",++serial);p.addProperty("expires",clock.getAsLong()+90);p.addProperty("kind",kind);p.addProperty("reply",reply);p.add("data",data.deepCopy());sender.send(peer,p);
    }
    static boolean validState(JsonObject s,String host,String room,String self){
        if(!text(s,"id").equals(room)||!text(s,"host").equals(host)||!policy(text(s,"access"))||number(s,"revision")<1||number(s,"generation")<1)return false;
        JsonElement members=s.get("members");if(members==null||!members.isJsonArray()||members.getAsJsonArray().size()>16)return false;
        Set<String> ids=new HashSet<>();for(JsonElement e:members.getAsJsonArray()){
            if(!e.isJsonObject())return false;JsonObject m=e.getAsJsonObject();if(!LobbyDirectory.id(text(m,"id"))||!ids.add(text(m,"id")))return false;
            for(String f:new String[]{"prepared","ready","connected"})if(!m.has(f)||!m.get(f).isJsonPrimitive()||!m.getAsJsonPrimitive(f).isBoolean())return false;
            if(bool(m,"ready")&&(!bool(m,"prepared")||!bool(m,"connected")))return false;
        }
        return ids.contains(host)&&ids.contains(self)&&(!s.has("game")||wireGame(game(object(s,"game"))))
                &&(!s.has("peerAttempt")||uuid(text(s,"peerAttempt"))&&ids.size()==2&&peerGame(game(object(s,"game"))));
    }
    // Keep a full 16-player snapshot within one provider message, including its envelope.
    private static boolean wireGame(LobbySession.Game g){return g!=null&&g.players<=16&&g.engine.length()<=32&&g.mode.length()<=32&&g.adapterVersion.length()<=32;}
    static JsonObject member(JsonObject s,String id){JsonElement a=s.get("members");if(a!=null&&a.isJsonArray())for(JsonElement e:a.getAsJsonArray())if(e.isJsonObject()&&text(e.getAsJsonObject(),"id").equals(id))return e.getAsJsonObject();return new JsonObject();}
    static JsonObject game(LobbySession.Game g){JsonObject o=new JsonObject();o.addProperty("engine",g.engine);o.addProperty("hash",g.contentHash);o.addProperty("mode",g.mode);o.addProperty("adapter",g.adapterVersion);o.addProperty("players",g.players);return o;}
    static LobbySession.Game game(JsonObject o){try{return new LobbySession.Game(text(o,"engine"),text(o,"hash"),text(o,"mode"),text(o,"adapter"),(int)number(o,"players"));}catch(RuntimeException e){return null;}}
    static String capability(LobbySession.Game g){return g.engine+"/"+g.mode+"/"+g.adapterVersion;}
    static boolean policy(String s){return Arrays.asList("private","public","approval").contains(s);}
    static String uuid(){return UUID.randomUUID().toString();}
    static boolean uuid(String s){try{return !s.equals("00000000-0000-0000-0000-000000000000")&&UUID.fromString(s).toString().equals(s);}catch(RuntimeException e){return false;}}
    static String text(JsonObject o,String key){return LobbyDirectory.text(o,key);}
    static JsonObject object(JsonObject o,String key){JsonElement e=o.get(key);return e!=null&&e.isJsonObject()?e.getAsJsonObject():new JsonObject();}
    static long number(JsonObject o,String key){JsonElement e=o.get(key);try{return e!=null&&e.isJsonPrimitive()&&e.getAsJsonPrimitive().isNumber()&&e.toString().matches("[0-9]{1,15}")?e.getAsLong():-1;}catch(RuntimeException ex){return -1;}}
    static boolean bool(JsonObject o,String key){JsonElement e=o.get(key);return e!=null&&e.isJsonPrimitive()&&e.getAsJsonPrimitive().isBoolean()&&e.getAsBoolean();}
}
