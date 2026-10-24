/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Foreground-only private text chats. Owner-thread; snapshots never contain credentials. */
public final class FluxerChats implements AutoCloseable {
    public static final int TEXT_LIMIT = 2000;
    private static final Comparator<String> IDS = (a,b) -> a.length() == b.length() ? a.compareTo(b) : Integer.compare(a.length(),b.length());
    public static final class Channel {
        public final String id, name, last;
        public final boolean group;
        private final Set<String> recipients=new HashSet<>();
        Channel(JsonObject c, String self) {
            id = str(c,"id"); last = str(c,"last_message_id"); group = number(c,"type") == 3;
            for(JsonElement e:array(c,"recipients"))if(e.isJsonObject())recipients.add(str(e.getAsJsonObject(),"id"));
            String title = str(c,"name");
            if (title.isEmpty()) {
                List<String> names = new ArrayList<>();
                for (JsonElement e : array(c,"recipients")) if (e.isJsonObject() && !self.equals(str(e.getAsJsonObject(),"id"))) names.add(label(e.getAsJsonObject()));
                title = String.join(", ", names);
            }
            name = bounded(title.isEmpty() ? (group ? "Group conversation" : "Direct message") : title, 120);
        }
    }
    public static final class Message {
        public final String id, author, text, time, delivery;
        public final boolean mine, retryable;
        Message(String id, String author, String text, String time, String delivery, boolean mine, boolean retryable) {
            this.id=id; this.author=author; this.text=text; this.time=time; this.delivery=delivery; this.mine=mine; this.retryable=retryable;
        }
    }
    public static final class State {
        public final String account, status, channel, title, draft;
        public final boolean verified, loading, historyBusy, canSend, more, past;
        public final long draftRevision, retryAt;
        public final List<Channel> channels;
        public final List<Message> messages;
        State(FluxerChats a) {
            account=a.self; status=a.status; channel=a.selected; verified=a.verified; loading=a.loading;
            channels=Collections.unmodifiableList(new ArrayList<>(a.channels.values()));
            Channel c=a.channels.get(channel); title=c==null?"Chats":c.name;
            Window w=a.windows.get(channel);
            draft=w==null?"":w.draft; draftRevision=w==null?0:w.draftRevision;
            historyBusy=w!=null&&w.busy; more=w!=null&&w.more; past=w!=null&&w.past;
            retryAt=a.retryAt;
            canSend=a.verified&&w!=null&&w.loaded&&w.access&&!w.sending()&&a.clock.getAsLong()>=retryAt;
            List<Message> rows=new ArrayList<>();
            if(w!=null) {
                for(JsonObject m:w.remote.values()) {
                    JsonObject author=object(m,"author");
                    if(a.blocked.contains(str(author,"id"))) continue;
                    String content=str(m,"content");
                    if(control(content)) content="Game session message";
                    else if(content.isEmpty()) content=array(m,"attachments").size()>0?"[Attachment]":array(m,"stickers").size()>0?"[Sticker]":"[Conversation event]";
                    rows.add(new Message(str(m,"id"),label(author),bounded(content,8000),str(m,"timestamp"),
                            str(m,"edited_timestamp").isEmpty()?"":"Edited",a.self.equals(str(author,"id")),false));
                }
                for(Pending p:w.pending.values()) rows.add(new Message(p.nonce,"You",p.content,"",p.delivery,true,
                        w.access&&!p.sending&&(p.knownRejected||a.clock.getAsLong()-p.first<240000)&&a.clock.getAsLong()>=a.retryAt));
            }
            messages=Collections.unmodifiableList(rows);
        }
    }
    private static final class Pending {
        String nonce, content, delivery="Sending..."; long first, attempt; boolean sending=true, knownRejected, uncertain;
    }
    private static final class Window {
        final TreeMap<String,JsonObject> remote=new TreeMap<>(IDS);
        final LinkedHashMap<String,Pending> pending=new LinkedHashMap<>();
        String draft="", before=""; long draftRevision, ticket, mutation;
        boolean loaded, busy, more, past, access=true;
        boolean sending() { for(Pending p:pending.values()) if(p.sending)return true; return false; }
    }
    private final FluxerAuth.Transport transport;
    private final LongSupplier clock;
    private final Consumer<State> observer;
    private final LinkedHashMap<String,Channel> channels=new LinkedHashMap<>();
    private final LinkedHashMap<String,Window> windows=new LinkedHashMap<>(16,0.75f,true);
    private final Set<String> blocked=new HashSet<>();
    private String token, self="", selected="", status="Sign in to Fluxer to open your chats.";
    private boolean verified, loading, active, closed;
    private long epoch, nextChannels, nextMessages, retryAt;

    public FluxerChats(FluxerAuth.Transport transport, LongSupplier clock, Consumer<State> observer) {
        this.transport=transport; this.clock=clock; this.observer=observer;
    }
    public State state() { return new State(this); }
    private void emit() { observer.accept(state()); }
    public void active(boolean value) { active=value; }
    public void session(String value) {
        if(closed)return;
        if(Objects.equals(token,value)&&verified) { tick(); return; }
        if(Objects.equals(token,value)&&loading)return;
        if(clock.getAsLong()<retryAt&&Objects.equals(token,value))return;
        reset(); token=value;
        if(!FluxerAuth.validToken(value)) {token=null;emit();return;}
        loading=true; status="Checking your account..."; emit(); long generation=epoch;
        request("GET","/v1/users/@me",null,r -> {
            if(!current(generation))return;
            loading=false;
            if(!r.ok()||!id(str(r.body,"id"))) { error(r,"Could not verify your account. Retry or open Account.");return; }
            self=str(r.body,"id"); verified=true; refresh();
        });
    }
    public void storageError() { reset();token=null;status="Saved sign-in could not be unlocked. Open Account to recover it.";emit(); }
    private void reset() {
        ++epoch; verified=false; loading=false; self=selected="";channels.clear();windows.clear();blocked.clear();
        nextChannels=nextMessages=retryAt=0;status="Sign in to Fluxer to open your chats.";
    }
    private boolean current(long generation) {return !closed&&generation==epoch;}
    private void request(String method,String path,JsonObject body,Consumer<FluxerAuth.Reply> done) {transport.request(method,path,body,token,done);}
    public void tick() {
        if(closed||!active||!verified||clock.getAsLong()<retryAt)return;
        if(clock.getAsLong()>=nextChannels)refresh();
        Window w=windows.get(selected);
        if(w!=null&&!w.past&&!w.busy&&clock.getAsLong()>=nextMessages)history(false);
    }
    public void refresh() {
        if(closed||!verified||loading||clock.getAsLong()<retryAt)return;
        loading=true;nextChannels=clock.getAsLong()+30000;long generation=epoch;
        request("GET","/v1/users/@me/relationships",null,relations -> {
            if(!current(generation))return;
            if(!relations.ok()||!relations.data.isJsonArray()) {loading=false;error(relations,"Could not check blocked accounts. Retry to load chats.");return;}
            blocked.clear();for(JsonElement e:relations.data.getAsJsonArray())if(e.isJsonObject()&&number(e.getAsJsonObject(),"type")==2)blocked.add(str(e.getAsJsonObject(),"id"));
            // Apply new blocks even if the subsequent conversation refresh fails.
            channels.values().removeIf(c->!c.group&&!Collections.disjoint(c.recipients,blocked));
            windows.keySet().retainAll(channels.keySet());
            if(!channels.containsKey(selected))selected="";
            emit();
            request("GET","/v1/users/@me/channels",null,r -> {
                if(!current(generation))return;loading=false;
                if(!r.ok()||!r.data.isJsonArray()) {error(r,"Could not refresh conversations. Previously loaded chats are unchanged.");return;}
                List<Channel> list=new ArrayList<>();
                for(JsonElement e:r.data.getAsJsonArray())if(e.isJsonObject()) {
                    JsonObject c=e.getAsJsonObject();int type=number(c,"type");
                    if(!id(str(c,"id"))||!str(c,"guild_id").isEmpty()||(type!=1&&type!=3))continue;
                    boolean blockedDm=false;
                    if(type==1)for(JsonElement recipient:array(c,"recipients"))if(recipient.isJsonObject()&&blocked.contains(str(recipient.getAsJsonObject(),"id")))blockedDm=true;
                    if(!blockedDm)list.add(new Channel(c,self));
                }
                list.sort((a,b)->IDS.compare(b.last,a.last));channels.clear();
                for(Channel c:list) {if(channels.size()==500)break;channels.put(c.id,c);}
                windows.keySet().retainAll(channels.keySet());
                if(!channels.containsKey(selected))selected="";
                status=list.size()>500?"Showing 500 most recent conversations":channels.isEmpty()?"No conversations yet.":"Up to date";emit();
            });
        });
    }
    public void select(String channel) {
        if(closed||!verified||!channels.containsKey(channel))return;
        selected=channel;
        if(!windows.containsKey(channel)) {
            if(windows.size()>=24) {
                String remove=null;
                for(Map.Entry<String,Window> entry:windows.entrySet())if(entry.getValue().draft.isEmpty()&&entry.getValue().pending.isEmpty()){remove=entry.getKey();break;}
                if(remove==null){selected="";status="Too many unsent conversations. Finish or discard a pending message first.";emit();return;}
                windows.remove(remove);
            }
            windows.put(channel,new Window());
        }
        emit();history(false);
    }
    public void list() {selected="";emit();}
    public void latest() {Window w=windows.get(selected);if(w!=null&&!w.busy){w.past=false;history(false);}}
    public void older() {Window w=windows.get(selected);if(w!=null&&w.more)history(true);}
    public void history(boolean older) {
        Window w=windows.get(selected);
        if(closed||!verified||w==null||w.busy||clock.getAsLong()<retryAt||older&&w.before.isEmpty())return;
        String channel=selected;long generation=epoch,ticket=++w.ticket,mutation=w.mutation;
        w.busy=true;nextMessages=clock.getAsLong()+8000;emit();
        request("GET","/v1/channels/"+channel+"/messages?limit=50"+(older?"&before="+w.before:""),null,r -> {
            if(!current(generation)||windows.get(channel)!=w||ticket!=w.ticket)return;
            w.busy=false;
            if(!r.ok()||!r.data.isJsonArray()) {
                if(r.status==403||r.status==404){w.remote.clear();w.loaded=false;w.access=false;w.more=false;}
                error(r,r.status==403||r.status==404?"This conversation is no longer available.":"Could not refresh messages. Your draft is unchanged.");return;
            }
            // A GET started before a successful POST must not erase that acknowledgement.
            if(mutation!=w.mutation){nextMessages=0;emit();return;}
            TreeMap<String,JsonObject> page=new TreeMap<>(IDS);
            for(JsonElement e:r.data.getAsJsonArray())if(e.isJsonObject()) {
                JsonObject m=e.getAsJsonObject();if(validMessage(m,channel)) {
                    page.put(str(m,"id"),clean(m));
                    Pending p=w.pending.get(str(m,"nonce"));
                    if(p!=null&&self.equals(str(object(m,"author"),"id")))w.pending.remove(p.nonce);
                }
            }
            String previous=w.before;
            if(!older)w.remote.clear();
            w.remote.putAll(page);
            while(w.remote.size()>100) {if(older)w.remote.pollLastEntry();else w.remote.pollFirstEntry();}
            if(!page.isEmpty())w.before=page.firstKey();
            w.more=!page.isEmpty()&&(!older||!previous.equals(w.before));w.loaded=true;w.access=true;w.past=older;
            status=w.past?"Earlier messages":"Up to date";emit();
        });
    }
    public void draft(String channel,String text,long revision) {
        Window w=windows.get(channel);if(w==null||revision<w.draftRevision)return;
        w.draft=bounded(text,TEXT_LIMIT);w.draftRevision=revision;emit();
    }
    public void send(String channel,long revision) {
        Window w=windows.get(channel);
        if(!channel.equals(selected)||w==null||w.draftRevision!=revision||!state().canSend)return;
        String content=w.draft;if(content.trim().isEmpty()||content.length()>TEXT_LIMIT)return;
        if(w.pending.size()>=20){status="Resolve pending messages before sending more.";emit();return;}
        Pending p=new Pending();p.nonce=UUID.randomUUID().toString().replace("-","");p.content=content;p.first=clock.getAsLong();
        w.pending.put(p.nonce,p);w.draft="";post(channel,w,p);
    }
    public void retry(String channel,String nonce) {
        Window w=windows.get(channel);Pending p=w==null?null:w.pending.get(nonce);
        if(closed||!verified||w==null||!w.access||p==null||p.sending||clock.getAsLong()<retryAt)return;
        if(!p.knownRejected&&clock.getAsLong()-p.first>=240000){status="Delivery unknown. Check the conversation before sending again.";emit();if(channel.equals(selected))latest();return;}
        if(p.knownRejected)p.first=clock.getAsLong();
        post(channel,w,p);
    }
    public void discard(String channel,String nonce) {
        Window w=windows.get(channel);Pending p=w==null?null:w.pending.get(nonce);
        if(p!=null&&!p.sending){w.pending.remove(nonce);emit();}
    }
    private void post(String channel,Window w,Pending p) {
        p.sending=true;p.delivery="Sending...";long attempt=++p.attempt,generation=epoch;
        JsonObject body=new JsonObject();body.addProperty("content",p.content);body.addProperty("nonce",p.nonce);
        JsonObject mentions=new JsonObject();mentions.add("parse",new JsonArray());body.add("allowed_mentions",mentions);
        emit();request("POST","/v1/channels/"+channel+"/messages",body,r -> {
            if(!current(generation)||windows.get(channel)!=w||w.pending.get(p.nonce)!=p||attempt!=p.attempt)return;
            p.sending=false;
            if(r.ok()&&validMessage(r.body,channel)&&self.equals(str(object(r.body,"author"),"id"))) {
                w.pending.remove(p.nonce);++w.mutation;
                if(!w.past) {w.remote.put(str(r.body,"id"),clean(r.body));while(w.remote.size()>100)w.remote.pollFirstEntry();}
                status="Message sent";emit();return;
            }
            p.uncertain|=r.status==0||r.status>=500||r.ok();
            p.knownRejected=!p.uncertain&&r.status>=400&&r.status<500;
            p.delivery=p.knownRejected?"Not sent":"Delivery unknown";
            if(r.status==403||r.status==404)w.access=false;
            error(r,p.knownRejected?"Message was not sent. Check access or retry.":"Delivery unknown. Check messages before retrying.");
        });
    }
    private void error(FluxerAuth.Reply r,String text) {
        if(r.status==401){reset();token=null;status="Your session expired. Open Account to sign in again.";}
        else if(r.status==429){retryAt=clock.getAsLong()+Math.max(3000,Math.min(300000,r.retryMillis));status="Fluxer is busy. Please wait before retrying.";}
        else status=text;
        emit();
    }
    @Override public void close(){closed=true;reset();token=null;}
    private static boolean validMessage(JsonObject m,String channel){return id(str(m,"id"))&&channel.equals(str(m,"channel_id"))&&id(str(object(m,"author"),"id"));}
    private static JsonObject clean(JsonObject message) {
        JsonObject result=new JsonObject();
        for(String key:new String[]{"id","channel_id","nonce","timestamp","edited_timestamp"})result.addProperty(key,str(message,key));
        result.addProperty("content",bounded(str(message,"content"),8000));
        JsonObject author=new JsonObject();for(String key:new String[]{"id","username","global_name"})author.addProperty(key,bounded(str(object(message,"author"),key),120));
        result.add("author",author);
        for(String key:new String[]{"attachments","stickers"}){JsonArray present=new JsonArray();if(array(message,key).size()>0)present.add(new JsonObject());result.add(key,present);}
        return result;
    }
    static boolean id(String value){return value.matches("[1-9][0-9]{0,19}");}
    private static String str(JsonObject o,String k){return FluxerAuth.string(o,k);}
    private static String label(JsonObject o){String name=str(o,"global_name");if(name.isEmpty())name=str(o,"username");return bounded(name.isEmpty()?"Unknown author":name,120);}
    private static JsonArray array(JsonObject o,String k){return o.has(k)&&o.get(k).isJsonArray()?o.getAsJsonArray(k):new JsonArray();}
    private static JsonObject object(JsonObject o,String k){return o.has(k)&&o.get(k).isJsonObject()?o.getAsJsonObject(k):new JsonObject();}
    private static int number(JsonObject o,String k){try{return o.get(k).getAsInt();}catch(RuntimeException e){return -1;}}
    private static String bounded(String text,int max){if(text.length()<=max)return text;int end=Character.isHighSurrogate(text.charAt(max-1))?max-1:max;return text.substring(0,end);}
    private static boolean control(String text){return text.endsWith("\n```")&&(text.startsWith("TrainerOS lobby\n```json\n")||text.startsWith("TrainerOS lobby directory\n```json\n"));}
}
