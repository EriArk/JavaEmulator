/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import com.google.gson.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Directory reader with bounded community admission. Advertisements never grant game admission. */
public final class LobbyDirectory implements AutoCloseable {
    // Directory protocol uses "j2me"; the local runtime calls this engine "java".
    public static final String JAVA_BLUETOOTH = "j2me/bluetooth/1";
    // Public service identities from TrainerOS LobbyDirectory.h, not credentials.
    public static final String BOT="1559529748697190400", GUILD="1559529715193098242";
    public static final String INVITE_PATH="/v1/invites/kOYY3hzY";
    public interface Membership { boolean claim(String account) throws Exception; }
    public static final List<String> CHANNELS=Collections.unmodifiableList(Arrays.asList(
            "1559529986233212933","1559529993002819588","1559530000271540224","1559530002591002624","1559530004931416064"));
    public static final String PREFIX="TrainerOS lobby directory\n```json\n";
    public static final class Room {
        public final String id,owner,name,status,access,title,platform,mode,adapter,hash;
        public final long revision,expires;
        public final int seats,available;
        private final JsonObject canonical;
        private long deadline;
        private Room(JsonObject value,long wall,long elapsed) {
            id=text(value,"lobby");owner=text(value,"owner");name=text(value,"name");
            status=text(value,"status");access=text(value,"access");revision=integer(value,"revision");expires=integer(value,"expires");
            seats=(int)integer(value,"seats");available=(int)integer(value,"available");
            JsonObject game=object(value,"game");title=status.equals("choosing")?"Choosing a game":text(game,"title");
            platform=text(game,"platform");mode=text(game,"mode");adapter=text(game,"adapter");hash=text(game,"id");
            deadline=elapsed+Math.max(0,expires-wall)*1000;
            canonical=value.deepCopy();
        }
        public String capability(){return platform+"/"+mode+"/"+adapter;}
        public boolean acceptsJavaGame(String selectedHash){
            return status.equals("choosing") || !selectedHash.isEmpty() && selectedHash.equals(hash)
                    && capability().equals(JAVA_BLUETOOTH);
        }
        public boolean live(long wall,long elapsed){return expires>wall&&deadline>elapsed;}
    }
    public static final class State {
        public final boolean verified,busy,needsCommunity;
        public final String account,status;
        public final List<Room> rooms;
        public final long retryAt;
        State(LobbyDirectory d){verified=d.verified;busy=d.busy;needsCommunity=d.needsCommunity;account=d.self;
            status=d.status;rooms=Collections.unmodifiableList(new ArrayList<>(d.rows.values()));retryAt=d.retryAt;}
    }
    private final FluxerAuth.Transport http;
    private final LongSupplier wall,elapsed;
    private final Consumer<State> listener;
    private final Set<String> capabilities;
    private final Membership membership;
    private final Map<String,Room> rows=new LinkedHashMap<>(),pending=new LinkedHashMap<>(),seen=new HashMap<>();
    private final Set<String> conflicts=new HashSet<>();
    private String token,self="",status="Sign in to Fluxer to browse lobbies.";
    private boolean verified,busy,needsCommunity,active,closed;
    private long epoch,nextRefresh,retryAt;
    private int pages;

    public LobbyDirectory(FluxerAuth.Transport http,LongSupplier wallSeconds,LongSupplier elapsedMillis,
                          Set<String> capabilities,Consumer<State> listener){
        this(http,wallSeconds,elapsedMillis,capabilities,listener,new Membership(){
            private final Set<String> attempted=new HashSet<>();
            public boolean claim(String account){return attempted.add(account);}
        });
    }
    public LobbyDirectory(FluxerAuth.Transport http,LongSupplier wallSeconds,LongSupplier elapsedMillis,
                          Set<String> capabilities,Consumer<State> listener,Membership membership){
        this.http=http;wall=wallSeconds;elapsed=elapsedMillis;this.capabilities=new HashSet<>(capabilities);this.listener=listener;
        this.membership=membership;
    }
    public State state(){return new State(this);}
    private void emit(){listener.accept(state());}
    public void active(boolean value){active=value;if(!value){++epoch;busy=false;pending.clear();}}
    public void session(String value){
        if(closed)return;
        if(Objects.equals(token,value)&&elapsed.getAsLong()<retryAt)return;
        if(Objects.equals(token,value)&&verified){refresh();return;}
        ++epoch;token=value;self="";verified=busy=needsCommunity=false;rows.clear();pending.clear();seen.clear();conflicts.clear();retryAt=nextRefresh=0;
        status="Sign in to Fluxer to browse lobbies.";
        if(value==null){emit();return;}
        busy=true;status="Checking Fluxer account...";emit();long generation=epoch;
        http.request("GET","/v1/users/@me",null,token,r->{
            if(!current(generation))return;busy=false;
            if(!r.ok()||!id(text(r.body,"id"))){failure(r,"Could not verify sign-in. Open Account or refresh.");return;}
            self=text(r.body,"id");verified=true;refresh();
        });
    }
    public void storageError(){session(null);status="Saved sign-in could not be unlocked. Open Account to recover it.";emit();}
    public void refresh(){
        if(closed||!verified||busy||elapsed.getAsLong()<retryAt)return;
        busy=true;needsCommunity=false;pending.clear();pages=0;nextRefresh=elapsed.getAsLong()+30000;
        status="Refreshing lobbies...";emit();load(0,"",epoch);
    }
    private void load(int channel,String before,long generation){
        if(channel==CHANNELS.size()){
            rows.clear();long now=wall.getAsLong(),mono=elapsed.getAsLong();
            for(Room room:pending.values())if(!conflicts.contains(room.id)&&room.live(now,mono)
                    &&(room.status.equals("choosing")||capabilities.contains(room.capability())))rows.put(room.id,room);
            pending.clear();busy=false;status=rows.isEmpty()?"No compatible lobbies right now.":"Up to date";emit();return;
        }
        if(++pages>50){failure(new FluxerAuth.Reply(0,null,0),"Directory is too large to load. Try again later.");return;}
        String channelId=CHANNELS.get(channel),path="/v1/channels/"+channelId+"/messages?limit=50"+(before.isEmpty()?"":"&before="+before);
        http.request("GET",path,null,token,r->{
            if(!current(generation))return;
            if(r.status==403||r.status==404){
                busy=false;pending.clear();rows.clear();needsCommunity=true;
                try{if(membership.claim(self)){joinCommunity(generation);return;}}
                catch(Exception ignored){status="Could not save community access state. Retry to continue.";emit();return;}
                status="Community access is unavailable. Retry to join or check your Fluxer account.";emit();return;
            }
            if(!r.ok()||!r.data.isJsonArray()||r.data.getAsJsonArray().size()>50){failure(r,"Could not refresh lobbies. Try again.");return;}
            JsonArray messages=r.data.getAsJsonArray();String last=before;
            for(JsonElement item:messages){
                if(!item.isJsonObject()){failure(new FluxerAuth.Reply(0,null,0),"Invalid directory response. Refresh to try again.");return;}
                JsonObject message=item.getAsJsonObject();String messageId=text(message,"id");
                if(!id(messageId)||(!last.isEmpty()&&compareIds(messageId,last)>=0)||!channelId.equals(text(message,"channel_id"))){
                    failure(new FluxerAuth.Reply(0,null,0),"Invalid directory page. Refresh to try again.");return;
                }
                last=messageId;
                Room room=parse(message,channelId,wall.getAsLong(),elapsed.getAsLong());if(room==null)continue;
                Room old=seen.get(room.id);
                if(old!=null){
                    if(!old.owner.equals(room.owner)){conflicts.add(room.id);continue;}
                    if(room.revision<old.revision)continue;
                    if(room.revision==old.revision){
                        if(!room.canonical.equals(old.canonical)){conflicts.add(room.id);continue;}
                        room.deadline=Math.min(room.deadline,old.deadline);
                    }
                }
                if(seen.size()>=5000&&!seen.containsKey(room.id)){failure(new FluxerAuth.Reply(0,null,0),"Directory limit reached. Reopen Lobbies to refresh.");return;}
                seen.put(room.id,room);pending.put(room.id,room);
            }
            if(messages.size()==50)load(channel,last,generation);else load(channel+1,"",generation);
        });
    }
    private void failure(FluxerAuth.Reply r,String message){
        busy=false;pending.clear();rows.clear();needsCommunity=r.status==403||r.status==404;
        if(r.status==401){++epoch;verified=false;self="";token=null;seen.clear();conflicts.clear();message="Sign-in expired. Open Account to sign in again.";}
        else if(needsCommunity)message="Join the TrainerOS community to browse public lobbies.";
        else if(r.status==429){retryAt=elapsed.getAsLong()+Math.max(1000,Math.min(300000,r.retryMillis>0?r.retryMillis:30000));message="Fluxer is busy. Please wait before refreshing.";}
        status=message;nextRefresh=elapsed.getAsLong()+30000;emit();
    }
    public void retryCommunity(String account){
        if(closed||busy||!verified||!self.equals(account)||!needsCommunity||elapsed.getAsLong()<retryAt)return;
        try{membership.claim(self);}catch(Exception ignored){status="Could not save community access state. Retry to continue.";emit();return;}
        joinCommunity(epoch);
    }
    private void joinCommunity(long generation){
        busy=true;needsCommunity=false;status="Connecting to the lobby community...";emit();
        http.request("GET",INVITE_PATH,null,token,r->{
            if(!current(generation))return;
            if(!r.ok()||!communityInvite(r.body)){joinFailure(r,"Could not verify the lobby community invite. Nothing was joined.");return;}
            http.request("POST",INVITE_PATH,new JsonObject(),token,result->{
                if(!current(generation))return;busy=false;
                if(result.ok()&&communityInvite(result.body)){refresh();return;}
                if(result.status==0||result.status>=500){
                    // The POST may have succeeded. Read access, never replay the membership write.
                    status="Community join result unknown. Checking access...";emit();refresh();return;
                }
                joinFailure(result,"Could not confirm community membership. Retry or check your Fluxer account.");
            });
        });
    }
    private boolean communityInvite(JsonObject invite){return integer(invite,"type")==0&&GUILD.equals(text(object(invite,"guild"),"id"))&&"kOYY3hzY".equals(text(invite,"code"));}
    private void joinFailure(FluxerAuth.Reply r,String message){failure(r,message);if(r.status!=401){needsCommunity=true;if(r.status!=429)status=message;emit();}}
    public void tick(){
        if(closed||!active)return;
        if(retryAt>0&&elapsed.getAsLong()>=retryAt){retryAt=0;emit();}
        if(rows.values().removeIf(r->!r.live(wall.getAsLong(),elapsed.getAsLong()))){if(rows.isEmpty())status="No compatible lobbies right now.";emit();}
        if(verified&&!busy&&!needsCommunity&&elapsed.getAsLong()>=nextRefresh)refresh();
    }
    private boolean current(long generation){return !closed&&generation==epoch;}
    @Override public void close(){closed=true;++epoch;rows.clear();pending.clear();seen.clear();conflicts.clear();token=null;}

    static Room parse(JsonObject message,String channel,long now,long mono){
        try {
            if(!CHANNELS.contains(channel)||!channel.equals(text(message,"channel_id"))||!BOT.equals(text(object(message,"author"),"id"))
                    ||!text(message,"webhook_id").isEmpty()||(!text(message,"guild_id").isEmpty()&&!GUILD.equals(text(message,"guild_id"))))return null;
            String content=text(message,"content");if(content.length()>2000||!content.startsWith(PREFIX)||!content.endsWith("\n```"))return null;
            JsonObject v=JsonParser.parseString(content.substring(PREFIX.length(),content.length()-4)).getAsJsonObject();
            if(integer(v,"v")!=2||!text(v,"product").equals("abyssme")||integer(v,"protocol")!=1)return null;
            String room=text(v,"lobby");if(!UUID.fromString(room).toString().equals(room)||room.equals("00000000-0000-0000-0000-000000000000")
                    ||!id(text(v,"owner"))||!label(v,"name",100))return null;
            long revision=integer(v,"revision"),expires=integer(v,"expires"),seats=integer(v,"seats"),available=integer(v,"available");
            if(revision<1||revision>9007199254740991L||!LobbyExpiry.plausible(expires,now,300)||seats<0||seats>16||available<0||available>seats)return null;
            if(!Arrays.asList("public","approval").contains(text(v,"access"))||integer(v,"streams")!=0)return null;
            String status=text(v,"status");if(status.equals("choosing")){if(seats!=0||available!=0||v.has("game"))return null;}
            else {
                if(!status.equals("gathering")&&!status.equals("playing"))return null;
                JsonObject game=object(v,"game");if(seats<2||!text(game,"id").matches("[a-f0-9]{64}")||!label(game,"title",160)
                        ||!Arrays.asList("j2me","mophun","symbian").contains(text(game,"platform"))
                        ||!text(game,"mode").matches("[a-z0-9._-]{1,64}")||!text(game,"adapter").matches("[A-Za-z0-9._-]{1,64}"))return null;
            }
            return new Room(v,now,mono);
        }catch(RuntimeException ignored){return null;}
    }
    static boolean id(String v){return v.matches("[1-9][0-9]{0,19}");}
    private static int compareIds(String a,String b){return a.length()==b.length()?a.compareTo(b):Integer.compare(a.length(),b.length());}
    static String text(JsonObject o,String key){JsonElement e=o.get(key);return e!=null&&e.isJsonPrimitive()&&e.getAsJsonPrimitive().isString()?e.getAsString():"";}
    private static JsonObject object(JsonObject o,String key){JsonElement e=o.get(key);return e!=null&&e.isJsonObject()?e.getAsJsonObject():new JsonObject();}
    private static long integer(JsonObject o,String key){JsonElement e=o.get(key);if(e==null||!e.isJsonPrimitive()||!e.getAsJsonPrimitive().isNumber()||!e.toString().matches("[0-9]{1,16}"))return -1;try{return e.getAsLong();}catch(RuntimeException ignored){return -1;}}
    private static boolean label(JsonObject o,String key,int limit){String s=text(o,key);return !s.trim().isEmpty()&&s.length()<=limit&&s.chars().noneMatch(c->c<32||c==127);}
}
