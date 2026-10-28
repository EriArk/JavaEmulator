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
    public interface FriendGate {
        void enter(String account, String peer, java.util.function.BiConsumer<Runnable,String> result);
    }
    public static final int TEXT_LIMIT = 2000;
    public enum FriendAction { ACCEPT, DECLINE, CANCEL, REMOVE, BLOCK, UNBLOCK, MESSAGE }
    public static final class Friend {
        public final String id, name, tag, since;
        public final int type;
        Friend(JsonObject relation) {
            id=str(relation,"id");type=number(relation,"type");since=str(relation,"since");
            JsonObject user=object(relation,"user");String nickname=str(relation,"nickname");
            name=bounded(nickname.isEmpty()?label(user):nickname,120);
            String username=str(user,"username"),discriminator="";
            JsonElement tagNumber=user.get("discriminator");
            if(tagNumber!=null&&tagNumber.isJsonPrimitive())discriminator=tagNumber.getAsString();
            discriminator=discriminator.matches("[0-9]{1,4}")?String.format(Locale.ROOT,"%04d",Integer.parseInt(discriminator)):"";
            tag=username.isEmpty()?id:bounded(username,32)+(discriminator.isEmpty()?"":"#"+discriminator);
        }
        boolean same(Friend other){return other!=null&&id.equals(other.id)&&type==other.type&&since.equals(other.since);}
        public String kind(){return type==1?"Friend":type==2?"Blocked":type==3?"Incoming request":"Request sent";}
    }
    private static final Comparator<String> IDS = (a,b) -> a.length() == b.length() ? a.compareTo(b) : Integer.compare(a.length(),b.length());
    public static final class Channel {
        public final String id, name, last;
        public final boolean group, fallbackName;
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
            fallbackName=title.isEmpty();
            name = bounded(fallbackName ? (group ? "Group conversation" : "Direct message") : title, 120);
        }
    }
    public static final class Message {
        public enum ContentKind { TEXT, SESSION, ATTACHMENT, STICKER, EVENT }
        public final String id, author, text, time, delivery;
        public final SocialNotice deliveryCode;
        public final ContentKind contentKind;
        public final boolean mine, retryable;
        Message(String id, String author, String text, String time, SocialNotice delivery, boolean mine, boolean retryable, ContentKind contentKind) {
            this.id=id; this.author=author; this.text=text; this.time=time; this.delivery=delivery.english; this.deliveryCode=delivery; this.mine=mine; this.retryable=retryable;
            this.contentKind=contentKind;
        }
    }
    public static final class State {
        public final String account, status, channel, title, draft;
        public final boolean verified, loading, historyBusy, canSend, more, past;
        public final long draftRevision, retryAt;
        public final List<Channel> channels;
        public final List<Message> messages;
        public final List<Friend> friends;
        public final boolean friendsBusy, friendsReady;
        public final String friendsNotice;
        public final SocialNotice statusCode, friendsCode;
        public final String friendFailureDetail;
        public final long openedConversation;
        public String friendsStatus() {
            if (!friendsNotice.isEmpty()) return friendsNotice;
            return friendsStatusCode().english;
        }
        public SocialNotice friendsStatusCode() {
            if (friendsCode != SocialNotice.NONE) return friendsCode;
            if (friendsReady) return SocialNotice.CONTACTS_CURRENT;
            return verified && friendsBusy ? SocialNotice.CONTACTS_REFRESHING : statusCode;
        }
        public boolean quiet(boolean friends) {
            SocialNotice notice=friends?friendsStatusCode():statusCode;
            return notice==SocialNotice.CURRENT || notice==SocialNotice.CONTACTS_CURRENT || notice==SocialNotice.CONTACTS_REFRESHED;
        }
        State(FluxerChats a) {
            account=a.self; statusCode=a.status; status=a.status.english; channel=a.selected; verified=a.verified; loading=a.loading;
            channels=Collections.unmodifiableList(new ArrayList<>(a.channels.values()));
            Channel c=a.channels.get(channel); title=c==null?"Chats":c.name;
            Window w=a.windows.get(channel);
            draft=w==null?"":w.draft; draftRevision=w==null?0:w.draftRevision;
            historyBusy=w!=null&&w.busy; more=w!=null&&w.more; past=w!=null&&w.past;
            retryAt=a.retryAt;
            List<Friend> people=new ArrayList<>(a.friends.values());
            people.sort(Comparator.comparing((Friend f)->f.name,String.CASE_INSENSITIVE_ORDER).thenComparing(f->f.id));
            friends=Collections.unmodifiableList(people);friendsBusy=a.friendBusy||a.loading;
            friendsReady=a.verified&&a.friendsReady&&!friendsBusy&&a.clock.getAsLong()>=retryAt;
            friendsCode=a.friendsNotice;friendFailureDetail=friendsCode==SocialNotice.EXTERNAL?a.friendFailureDetail:"";
            friendsNotice=friendsCode==SocialNotice.EXTERNAL?friendFailureDetail:friendsCode.english;openedConversation=a.openedConversation;
            canSend=a.verified&&w!=null&&w.loaded&&w.access&&!w.sending()&&a.clock.getAsLong()>=retryAt;
            List<Message> rows=new ArrayList<>();
            if(w!=null) {
                for(JsonObject m:w.remote.values()) {
                    JsonObject author=object(m,"author");
                    if(a.blocked.contains(str(author,"id"))) continue;
                    String content=str(m,"content");
                    Message.ContentKind kind=Message.ContentKind.TEXT;
                    if(control(content)) {content="Game session message";kind=Message.ContentKind.SESSION;}
                    else if(content.isEmpty()) {
                        kind=array(m,"attachments").size()>0?Message.ContentKind.ATTACHMENT:array(m,"stickers").size()>0?Message.ContentKind.STICKER:Message.ContentKind.EVENT;
                        content=kind==Message.ContentKind.ATTACHMENT?"[Attachment]":kind==Message.ContentKind.STICKER?"[Sticker]":"[Conversation event]";
                    }
                    rows.add(new Message(str(m,"id"),label(author),bounded(content,8000),str(m,"timestamp"),
                            str(m,"edited_timestamp").isEmpty()?SocialNotice.NONE:SocialNotice.EDITED,a.self.equals(str(author,"id")),false,kind));
                }
                for(Pending p:w.pending.values()) rows.add(new Message(p.nonce,"You",p.content,"",p.delivery,true,
                        w.access&&!p.sending&&(p.knownRejected||a.clock.getAsLong()-p.first<240000)&&a.clock.getAsLong()>=a.retryAt,Message.ContentKind.TEXT));
            }
            messages=Collections.unmodifiableList(rows);
        }
    }
    private static final class Pending {
        String nonce, content; SocialNotice delivery=SocialNotice.SENDING; long first, attempt; boolean sending=true, knownRejected, uncertain;
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
    private final Map<String,Friend> friends=new LinkedHashMap<>();
    private boolean friendBusy,friendsReady;
    private SocialNotice friendsNotice=SocialNotice.NONE;
    private String friendFailureDetail="";
    private long openedConversation;
    private String token, self="", selected="";
    private SocialNotice status=SocialNotice.CHATS_SIGNIN;
    private boolean verified, loading, active, closed;
    private long epoch, nextChannels, nextMessages, retryAt;
    private final FriendGate friendGate;

    public FluxerChats(FluxerAuth.Transport transport, LongSupplier clock, Consumer<State> observer) {
        this(transport,clock,observer,(account,peer,done)->done.accept(()->{},""));
    }
    public FluxerChats(FluxerAuth.Transport transport, LongSupplier clock, Consumer<State> observer, FriendGate gate) {
        this.transport=transport; this.clock=clock; this.observer=observer;
        friendGate=gate;
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
        loading=true; status=SocialNotice.CHECK_ACCOUNT; emit(); long generation=epoch;
        request("GET","/v1/users/@me",null,r -> {
            if(!current(generation))return;
            loading=false;
            if(!r.ok()||!id(str(r.body,"id"))) { error(r,SocialNotice.VERIFY_FAILED);return; }
            self=str(r.body,"id"); verified=true; refresh();
        });
    }
    public void storageError() { reset();token=null;status=SocialNotice.UNLOCK_FAILED;emit(); }
    private void reset() {
        ++epoch; verified=false; loading=false; self=selected="";channels.clear();windows.clear();blocked.clear();
        friends.clear();friendBusy=friendsReady=false;friendsNotice=SocialNotice.NONE;friendFailureDetail="";openedConversation=0;
        nextChannels=nextMessages=retryAt=0;status=SocialNotice.CHATS_SIGNIN;
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
        if(closed||!verified||loading||friendBusy||clock.getAsLong()<retryAt)return;
        loading=true;nextChannels=clock.getAsLong()+30000;long generation=epoch;
        request("GET","/v1/users/@me/relationships",null,relations -> {
            if(!current(generation))return;
            if(!relations.ok()||!relationships(relations.data)) {loading=false;friendsReady=false;error(relations,SocialNotice.CONTACTS_FAILED);return;}
            emit();
            request("GET","/v1/users/@me/channels",null,r -> {
                if(!current(generation))return;loading=false;
                if(!r.ok()||!r.data.isJsonArray()) {error(r,SocialNotice.CONVERSATIONS_FAILED);return;}
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
                status=list.size()>500?SocialNotice.CONVERSATIONS_LIMIT:channels.isEmpty()?SocialNotice.NO_CONVERSATIONS:SocialNotice.CURRENT;
                if(friendsNotice==SocialNotice.CONTACT_ACCEPTED)friendsNotice=SocialNotice.CONTACTS_REFRESHED;
                emit();
            });
        });
    }
    private boolean relationships(JsonElement data) {
        if(!data.isJsonArray()||data.getAsJsonArray().size()>2000)return false;
        Map<String,Friend> fresh=new LinkedHashMap<>();
        for(JsonElement e:data.getAsJsonArray()) {
            if(!e.isJsonObject())return false;
            Friend f=new Friend(e.getAsJsonObject());
            String userId=str(object(e.getAsJsonObject(),"user"),"id");
            if(!userId.isEmpty()&&!userId.equals(f.id))return false;
            if(!id(f.id)||f.type<1||f.type>4||fresh.containsKey(f.id))return false;
            if(!self.equals(f.id))fresh.put(f.id,f);
        }
        friends.clear();friends.putAll(fresh);friendsReady=true;
        blocked.clear();for(Friend f:fresh.values())if(f.type==2)blocked.add(f.id);
        // Apply new blocks even if the subsequent conversation refresh fails.
        channels.values().removeIf(c->!c.group&&!Collections.disjoint(c.recipients,blocked));
        windows.keySet().retainAll(channels.keySet());
        if(!channels.containsKey(selected))selected="";
        return true;
    }
    private boolean canChangeFriends(String account) {
        return !closed&&self.equals(account)&&state().friendsReady;
    }
    /** UI supplies the exact snapshot it confirmed, including its owning account. */
    public void friendAction(String account,Friend expected,FriendAction action) {
        if(!canChangeFriends(account)||expected==null||!expected.same(friends.get(expected.id))||!permitted(expected.type,action))return;
        friendBusy=true;friendsNotice=SocialNotice.CHECK_CONTACT;emit();long generation=epoch;
        java.util.function.Consumer<Runnable> read=release->{
        request("GET","/v1/users/@me/relationships",null,r->{
            if(!current(generation)){release.run();return;}
            if(!r.ok()||!relationships(r.data)){release.run();friendBusy=false;friendsReady=false;friendFailure(r,SocialNotice.CONTACT_VERIFY_FAILED);return;}
            if(!expected.same(friends.get(expected.id))){release.run();friendBusy=false;friendsNotice=SocialNotice.CONTACT_CHANGED;emit();return;}
            if(action==FriendAction.MESSAGE){release.run();openFriend(expected,generation);return;}
            JsonObject body=null;String method="DELETE";
            if(action==FriendAction.ACCEPT||action==FriendAction.BLOCK){method="PUT";body=new JsonObject();body.addProperty("type",action==FriendAction.BLOCK?2:1);}
            friendsNotice=SocialNotice.CONTACT_UPDATING;emit();
            request(method,"/v1/users/@me/relationships/"+expected.id,body,result->{
                release.run();
                if(!current(generation))return;
                friendResult(result);
            });
        });
        };
        if(action==FriendAction.MESSAGE)read.accept(()->{});
        else guard(account,expected.id,generation,read);
    }
    private void guard(String account,String peer,long generation,Consumer<Runnable> action){
        friendGate.enter(account,peer,(release,error)->{
            if(!current(generation)){if(release!=null)release.run();return;}
            if(release==null){friendBusy=false;friendsNotice=SocialNotice.EXTERNAL;friendFailureDetail=error;emit();return;}
            action.accept(release);
        });
    }
    private static boolean permitted(int type,FriendAction action) {
        switch(action){
            case ACCEPT:case DECLINE:return type==3;
            case CANCEL:return type==4;
            case REMOVE:case MESSAGE:return type==1;
            case UNBLOCK:return type==2;
            case BLOCK:return type==1||type==3||type==4;
            default:return false;
        }
    }
    public void addFriend(String account,String text) {
        if(!canChangeFriends(account))return;
        String tag=text==null?"":text.trim();
        if(!tag.matches("[A-Za-z0-9_]{1,32}#[0-9]{4}")){friendsNotice=SocialNotice.ENTER_TAG;emit();return;}
        friendBusy=true;friendsNotice=SocialNotice.CHECK_CONTACTS;emit();long generation=epoch;
        guard(account,"",generation,release->{
        request("GET","/v1/users/@me/relationships",null,r->{
            if(!current(generation)){release.run();return;}
            if(!r.ok()||!relationships(r.data)){release.run();friendBusy=false;friendsReady=false;friendFailure(r,SocialNotice.REQUEST_CHECK_FAILED);return;}
            for(Friend f:friends.values())if(f.tag.equalsIgnoreCase(tag)){
                release.run();friendBusy=false;friendsNotice=f.type==3?SocialNotice.REQUEST_EXISTS:SocialNotice.CONTACT_EXISTS;emit();return;
            }
            JsonObject body=new JsonObject();int split=tag.lastIndexOf('#');body.addProperty("username",tag.substring(0,split));body.addProperty("discriminator",tag.substring(split+1));
            friendsNotice=SocialNotice.SENDING_REQUEST;emit();
            request("POST","/v1/users/@me/relationships",body,result->{release.run();if(current(generation))friendResult(result);});
        });
        });
    }
    private void friendResult(FluxerAuth.Reply r) {
        friendBusy=false;friendsReady=false;
        if(r.ok())friendsNotice=SocialNotice.CONTACT_ACCEPTED;
        else if(r.status==0||r.status>=500)friendsNotice=SocialNotice.CONTACT_UNCERTAIN;
        else {friendFailure(r,SocialNotice.CONTACT_REJECTED);return;}
        emit();refresh();
    }
    private void friendFailure(FluxerAuth.Reply r,SocialNotice text) {
        friendsNotice=text;error(r,text);
        if(r.status==429)friendsNotice=SocialNotice.CONTACT_BUSY;
        emit();
    }
    private void openFriend(Friend f,long generation) {
        for(Channel c:channels.values())if(!c.group&&c.recipients.contains(f.id)){
            friendBusy=false;friendsNotice=SocialNotice.NONE;select(c.id);++openedConversation;emit();return;
        }
        JsonObject body=new JsonObject();body.addProperty("recipient_id",f.id);
        request("POST","/v1/users/@me/channels",body,r->{
            if(!current(generation))return;friendBusy=false;
            JsonObject c=r.body;
            Set<String> recipients=new HashSet<>();for(JsonElement e:array(c,"recipients"))if(e.isJsonObject())recipients.add(str(e.getAsJsonObject(),"id"));
            if(!r.ok()||!id(str(c,"id"))||number(c,"type")!=1||!str(c,"guild_id").isEmpty()||!recipients.contains(f.id)
                    ||recipients.stream().anyMatch(peer->!peer.equals(f.id)&&!peer.equals(self))){
                friendFailure(r,SocialNotice.OPEN_CONVERSATION_FAILED);return;
            }
            channels.put(str(c,"id"),new Channel(c,self));friendsNotice=SocialNotice.NONE;select(str(c,"id"));++openedConversation;emit();
        });
    }
    public void select(String channel) {
        if(closed||!verified||!channels.containsKey(channel))return;
        selected=channel;
        if(!windows.containsKey(channel)) {
            if(windows.size()>=24) {
                String remove=null;
                for(Map.Entry<String,Window> entry:windows.entrySet())if(entry.getValue().draft.isEmpty()&&entry.getValue().pending.isEmpty()){remove=entry.getKey();break;}
                if(remove==null){selected="";status=SocialNotice.PENDING_CONVERSATIONS;emit();return;}
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
                error(r,r.status==403||r.status==404?SocialNotice.CONVERSATION_UNAVAILABLE:SocialNotice.MESSAGES_FAILED);return;
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
            status=w.past?SocialNotice.EARLIER_MESSAGES:SocialNotice.CURRENT;emit();
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
        if(w.pending.size()>=20){status=SocialNotice.RESOLVE_PENDING;emit();return;}
        Pending p=new Pending();p.nonce=UUID.randomUUID().toString().replace("-","");p.content=content;p.first=clock.getAsLong();
        w.pending.put(p.nonce,p);w.draft="";post(channel,w,p);
    }
    public void retry(String channel,String nonce) {
        Window w=windows.get(channel);Pending p=w==null?null:w.pending.get(nonce);
        if(closed||!verified||w==null||!w.access||p==null||p.sending||clock.getAsLong()<retryAt)return;
        if(!p.knownRejected&&clock.getAsLong()-p.first>=240000){status=SocialNotice.DELIVERY_CHECK;emit();if(channel.equals(selected))latest();return;}
        if(p.knownRejected)p.first=clock.getAsLong();
        post(channel,w,p);
    }
    public void discard(String channel,String nonce) {
        Window w=windows.get(channel);Pending p=w==null?null:w.pending.get(nonce);
        if(p!=null&&!p.sending){w.pending.remove(nonce);emit();}
    }
    private void post(String channel,Window w,Pending p) {
        p.sending=true;p.delivery=SocialNotice.SENDING;long attempt=++p.attempt,generation=epoch;
        JsonObject body=new JsonObject();body.addProperty("content",p.content);body.addProperty("nonce",p.nonce);
        JsonObject mentions=new JsonObject();mentions.add("parse",new JsonArray());body.add("allowed_mentions",mentions);
        emit();request("POST","/v1/channels/"+channel+"/messages",body,r -> {
            if(!current(generation)||windows.get(channel)!=w||w.pending.get(p.nonce)!=p||attempt!=p.attempt)return;
            p.sending=false;
            if(r.ok()&&validMessage(r.body,channel)&&self.equals(str(object(r.body,"author"),"id"))) {
                w.pending.remove(p.nonce);++w.mutation;
                if(!w.past) {w.remote.put(str(r.body,"id"),clean(r.body));while(w.remote.size()>100)w.remote.pollFirstEntry();}
                status=SocialNotice.SENT;emit();return;
            }
            p.uncertain|=r.status==0||r.status>=500||r.ok();
            p.knownRejected=!p.uncertain&&r.status>=400&&r.status<500;
            p.delivery=p.knownRejected?SocialNotice.NOT_SENT:SocialNotice.DELIVERY_UNKNOWN;
            if(r.status==403||r.status==404)w.access=false;
            error(r,p.knownRejected?SocialNotice.SEND_FAILED:SocialNotice.SEND_UNCERTAIN);
        });
    }
    private void error(FluxerAuth.Reply r,SocialNotice text) {
        if(r.status==401){reset();token=null;status=SocialNotice.SESSION_EXPIRED;}
        else if(r.status==429){retryAt=clock.getAsLong()+Math.max(3000,Math.min(300000,r.retryMillis));status=SocialNotice.BUSY;}
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
    private static boolean control(String text){return text.startsWith(io.github.eriark.abyssme.peer.PeerSignals.PREFIX)||text.endsWith("\n```")&&(text.startsWith("TrainerOS lobby\n```json\n")||text.startsWith("TrainerOS lobby directory\n```json\n")||text.startsWith(LobbyMailbox.CONTACT_PREFIX)||text.startsWith(LobbyRoom.PREFIX)||text.startsWith(LobbyPublisher.RESULT_PREFIX));}
}
