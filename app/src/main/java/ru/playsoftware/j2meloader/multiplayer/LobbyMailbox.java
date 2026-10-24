/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import com.google.gson.*;
import java.util.*;
import java.util.function.LongSupplier;
import static ru.playsoftware.j2meloader.multiplayer.LobbyRoom.*;

/** Bounded foreground DM signaling using the same provider routes as Chats/TrainerOS.
 * Never adds friends, joins groups, retries ambiguous writes or reads guild conversations.
 */
final class LobbyMailbox {
    static final String CONTACT_PREFIX="AbyssME contacts\n```json\n";
    interface Listener {void message(String peer,String content);void changed();void failed(String peer,String reason);void expired();}
    private static final class Delivery {String peer,content,replaceKey;long expires;Delivery(String p,String c,long e,String k){peer=p;content=c;expires=e;replaceKey=k;}}
    private final FluxerAuth.Transport http;
    private final LongSupplier clock;
    private final Listener listener;
    private final ArrayDeque<Delivery> queue=new ArrayDeque<>();
    private final Map<String,String> channels=new LinkedHashMap<>(),names=new HashMap<>();
    private final Set<String> blocked=new HashSet<>(),friends=new LinkedHashSet<>(),wanted=new LinkedHashSet<>();
    private final LinkedHashMap<String,Boolean> consumed=new LinkedHashMap<>();
    private String token,self="",status="";
    private long epoch,nextPoll,nextSend,retryAt;
    private boolean active,polling,sending,contactsKnown;
    LobbyMailbox(FluxerAuth.Transport http,LongSupplier milliseconds,Listener listener){this.http=http;clock=milliseconds;this.listener=listener;}
    void configure(String token,String self){++epoch;this.token=token;this.self=self;queue.clear();channels.clear();names.clear();blocked.clear();friends.clear();wanted.clear();consumed.clear();polling=sending=contactsKnown=false;nextPoll=nextSend=retryAt=0;}
    void active(boolean value){active=value;if(value)nextPoll=0;else{++epoch;polling=sending=false;}}
    String status(){return status;}
    String name(String peer){return names.getOrDefault(peer,peer);}
    List<String> friends(){return new ArrayList<>(friends);}
    void watch(Collection<String> peers){wanted.clear();for(String peer:peers)if(wanted.size()<64&&LobbyDirectory.id(peer)&&!peer.equals(self))wanted.add(peer);}
    void refresh(){nextPoll=0;tick();}
    void send(String peer,String content){
        send(peer,content,"");
    }
    void send(String peer,String content,String replaceKey){
        // Only unsolicited room snapshots may replace an unsent older snapshot.
        // Invitations, correlated admissions and terminal controls must retain their order.
        if(!replaceKey.isEmpty())queue.removeIf(d->d.peer.equals(peer)&&d.replaceKey.equals(replaceKey));
        if(!LobbyDirectory.id(peer)||peer.equals(self)||content.length()>2000||queue.size()>=128){listener.failed(peer,"Lobby message could not be queued.");return;}
        queue.add(new Delivery(peer,content,clock.getAsLong()+85000,replaceKey));tick();
    }
    void tick(){
        if(!active||token==null||clock.getAsLong()<retryAt)return;
        if(!polling&&!sending&&clock.getAsLong()>=nextPoll){poll();return;}
        if(!polling&&!sending&&contactsKnown&&!queue.isEmpty()&&clock.getAsLong()>=nextSend)pump();
    }
    private void request(String method,String path,JsonObject body,java.util.function.Consumer<FluxerAuth.Reply> done){
        long generation=epoch;http.request(method,path,body,token,r->{if(generation!=epoch)return;if(r.status==401){configure(null,"");listener.expired();return;}if(r.status==429)retryAt=clock.getAsLong()+Math.max(1000,Math.min(300000,r.retryMillis>0?r.retryMillis:30000));done.accept(r);});
    }
    private void poll(){
        polling=true;nextPoll=clock.getAsLong()+10000;
        request("GET","/v1/users/@me/relationships",null,r->{
            if(!r.ok()||!r.data.isJsonArray()||r.data.getAsJsonArray().size()>5000){contactsKnown=false;end("Could not check contacts. Refresh to reconnect.");return;}
            Set<String> nextBlocked=new HashSet<>(),nextFriends=new LinkedHashSet<>();
            for(JsonElement e:r.data.getAsJsonArray()){
                if(!e.isJsonObject()){contactsKnown=false;end("Invalid contacts response.");return;}JsonObject relation=e.getAsJsonObject();String id=text(relation,"id");
                if(!LobbyDirectory.id(id)||!text(object(relation,"user"),"id").equals(id)){contactsKnown=false;end("Invalid contacts response.");return;}
                if(number(relation,"type")==2)nextBlocked.add(id);if(number(relation,"type")==1)nextFriends.add(id);label(id,object(relation,"user"));
            }
            blocked.clear();blocked.addAll(nextBlocked);friends.clear();friends.addAll(nextFriends);contactsKnown=true;
            request("GET","/v1/users/@me/channels",null,c->{
                if(!c.ok()||!c.data.isJsonArray()||c.data.getAsJsonArray().size()>5000){end("Could not refresh invitations.");return;}
                List<JsonObject> list=new ArrayList<>();for(JsonElement e:c.data.getAsJsonArray())if(e.isJsonObject()&&!peer(e.getAsJsonObject()).isEmpty())list.add(e.getAsJsonObject());
                list.sort((a,b)->compare(text(b,"last_message_id"),text(a,"last_message_id")));
                channels.clear();List<String> selected=new ArrayList<>();
                for(JsonObject channel:list){String p=peer(channel);if(blocked.contains(p)||channels.containsKey(p))continue;if(channels.size()>=500&&!wanted.contains(p)&&!p.equals(LobbyDirectory.BOT))continue;channels.put(p,text(channel,"id"));label(p,channel.getAsJsonArray("recipients").get(0).getAsJsonObject());}
                if(channels.containsKey(LobbyDirectory.BOT))selected.add(LobbyDirectory.BOT);
                for(String p:wanted)if(channels.containsKey(p)&&!selected.contains(p)&&selected.size()<32)selected.add(p);
                int recent=0;for(String p:channels.keySet())if(!selected.contains(p)&&selected.size()<32&&recent++<8)selected.add(p);
                read(selected,0);
            });
        });
    }
    private String peer(JsonObject c){
        if(number(c,"type")!=1||!LobbyDirectory.id(text(c,"id"))||!text(c,"guild_id").isEmpty())return "";
        JsonElement a=c.get("recipients");if(a==null||!a.isJsonArray()||a.getAsJsonArray().size()!=1||!a.getAsJsonArray().get(0).isJsonObject())return "";
        String peer=text(a.getAsJsonArray().get(0).getAsJsonObject(),"id");return LobbyDirectory.id(peer)&&!peer.equals(self)?peer:"";
    }
    private void label(String id,JsonObject user){String label=text(user,"global_name");if(label.isEmpty())label=text(user,"username");if(!label.isEmpty())names.put(id,label.substring(0,Math.min(64,label.length())));}
    private void read(List<String> peers,int index){
        if(index==peers.size()){end("");return;}String peer=peers.get(index),channel=channels.get(peer);
        request("GET","/v1/channels/"+channel+"/messages?limit=50",null,r->{
            if(!r.ok()||!r.data.isJsonArray()||r.data.getAsJsonArray().size()>50){end("Could not refresh lobby messages.");return;}
            List<JsonObject> messages=new ArrayList<>();for(JsonElement e:r.data.getAsJsonArray())if(e.isJsonObject())messages.add(e.getAsJsonObject());
            messages.sort((a,b)->compare(text(a,"id"),text(b,"id")));
            for(JsonObject m:messages){String id=text(m,"id"),author=text(object(m,"author"),"id"),content=text(m,"content");
                if(!LobbyDirectory.id(id)||!channel.equals(text(m,"channel_id"))||!author.equals(peer)||!text(m,"webhook_id").isEmpty()||!text(m,"guild_id").isEmpty()||blocked.contains(author)||content.length()>2000)continue;
                if(!(content.startsWith(PREFIX)||content.startsWith(CONTACT_PREFIX)||content.startsWith(LobbyPublisher.RESULT_PREFIX)))continue;
                if(consumed.put(channel+"/"+id,true)==null)listener.message(author,content);
                while(consumed.size()>4096)consumed.remove(consumed.keySet().iterator().next());
            }
            read(peers,index+1);
        });
    }
    private void end(String notice){polling=false;status=notice;listener.changed();tick();}
    private void pump(){
        Delivery d=queue.remove();if(blocked.contains(d.peer)||clock.getAsLong()>=d.expires){listener.failed(d.peer,"Player blocked or lobby message expired.");nextSend=clock.getAsLong()+1000;return;}
        sending=true;nextSend=clock.getAsLong()+1000;String channel=channels.get(d.peer);
        if(channel!=null){post(d,channel);return;}
        JsonObject body=new JsonObject();body.addProperty("recipient_id",d.peer);
        request("POST","/v1/users/@me/channels",body,r->{
            if(!r.ok()||!d.peer.equals(peer(r.body))){sending=false;listener.failed(d.peer,"Could not open a private conversation. Check Fluxer permissions.");return;}
            String id=text(r.body,"id");channels.put(d.peer,id);post(d,id);
        });
    }
    private void post(Delivery d,String channel){
        JsonObject body=new JsonObject();body.addProperty("content",d.content);body.addProperty("nonce",uuid().replace("-",""));body.addProperty("flags",4096);
        JsonObject mentions=new JsonObject();mentions.add("parse",new JsonArray());body.add("allowed_mentions",mentions);
        request("POST","/v1/channels/"+channel+"/messages",body,r->{
            sending=false;
            if(!r.ok()||!channel.equals(text(r.body,"channel_id"))||!self.equals(text(object(r.body,"author"),"id"))||!d.content.equals(text(r.body,"content"))){
                listener.failed(d.peer,"Message delivery was not confirmed. Refresh before trying again.");
            }
            nextPoll=Math.min(nextPoll,clock.getAsLong()+1500);listener.changed();
        });
    }
    static int compare(String a,String b){return a.length()==b.length()?a.compareTo(b):Integer.compare(a.length(),b.length());}
}
