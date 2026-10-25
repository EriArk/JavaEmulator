/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import android.app.Application;
import android.os.SystemClock;
import androidx.lifecycle.*;
import com.google.gson.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import static ru.playsoftware.j2meloader.multiplayer.LobbyRoom.*;

/** App-process room owner; navigating to Chats does not create a second host authority. */
public final class LobbyRoomModel extends AndroidViewModel {
    static final ViewModelStore STORE=new ViewModelStore();
    static LobbyRoomModel get(Application app){return new ViewModelProvider(STORE,new ViewModelProvider.AndroidViewModelFactory(app)).get(LobbyRoomModel.class);}
    public static final class State {
        public String account="",status="Sign in to Fluxer to use rooms.",publication="",room="",host="",access="private";
        public boolean verified,hosting,joined,available;
        public JsonObject snapshot=new JsonObject();
        public JsonObject contacts=new JsonObject();
        public List<JsonObject> invitations=new ArrayList<>();
        public Map<String,String> requests=new LinkedHashMap<>(),friends=new LinkedHashMap<>(),names=new HashMap<>();
    }
    final MutableLiveData<State> state=new MutableLiveData<>();
    private final ScheduledThreadPoolExecutor owner=new ScheduledThreadPoolExecutor(1);
    private final FluxerAuth.Store credentials;
    private final FluxerHttpTransport nativeHttp;
    private final FluxerAuth.Transport http;
    private final java.util.function.Function<String,LobbyPublisher.Store> publicationStore;
    private final Set<String> capabilities;
    private final LobbyMailbox mailbox;
    private final java.util.function.Function<String,TemporaryContacts.Journal> journals;
    private TemporaryContacts contacts;
    private Object manualLease;
    private long contactRetryAt;
    private LobbyRoom room;
    private LobbyPeerCoordinator gameNetwork;
    private LobbyPublisher publisher;
    private String token,self="",authNotice="Sign in to Fluxer to use rooms.";
    private boolean verifying,verified,closed;
    private int viewers;
    private long epoch,nextCredentials;
    private final ScheduledFuture<?> timer;
    private ScheduledFuture<?> networkTimer;
    private final Application.ActivityLifecycleCallbacks activityObserver=new Application.ActivityLifecycleCallbacks(){
        private final Set<android.app.Activity> tracked=Collections.newSetFromMap(new IdentityHashMap<>());
        public void onActivityStarted(android.app.Activity a){if(!(a instanceof LobbyRoomActivity)&&tracked.add(a))resume();}
        public void onActivityStopped(android.app.Activity a){if(tracked.remove(a))pause();}
        public void onActivityCreated(android.app.Activity a,android.os.Bundle b){}public void onActivityResumed(android.app.Activity a){}
        public void onActivityPaused(android.app.Activity a){}public void onActivitySaveInstanceState(android.app.Activity a,android.os.Bundle b){}public void onActivityDestroyed(android.app.Activity a){}
    };
    public LobbyRoomModel(Application app){this(app,new FluxerCredentialStore(app),null,Collections.emptySet(),account->{
        try{return new LobbyPublicationStore(app,account);}catch(Exception e){return new LobbyPublisher.Store(){public JsonObject read()throws Exception{throw e;}public void write(JsonObject s)throws Exception{throw e;}};}
    });}
    LobbyRoomModel(Application app,FluxerAuth.Store credentials,FluxerAuth.Transport supplied,Set<String> capabilities,java.util.function.Function<String,LobbyPublisher.Store> store){
        this(app,credentials,supplied,capabilities,store,account->{
            if(supplied!=null)return new TemporaryContacts.Journal(){JsonObject saved;public JsonObject load(){return saved;}public void save(JsonObject s){saved=s.deepCopy();}};
            try{java.nio.file.Path root=app.getNoBackupFilesDir().toPath().resolve("lobby-contacts");java.nio.file.Files.createDirectories(root);return new FileContactJournal(root,account);}
            catch(Exception e){return new TemporaryContacts.Journal(){public JsonObject load()throws java.io.IOException{throw new java.io.IOException("Contact storage unavailable");}public void save(JsonObject s)throws java.io.IOException{throw new java.io.IOException("Contact storage unavailable");}};}
        });
    }
    LobbyRoomModel(Application app,FluxerAuth.Store credentials,FluxerAuth.Transport supplied,Set<String> capabilities,java.util.function.Function<String,LobbyPublisher.Store> store,java.util.function.Function<String,TemporaryContacts.Journal> journals){
        super(app);this.credentials=credentials;this.capabilities=new HashSet<>(capabilities);publicationStore=store;
        this.journals=journals;
        nativeHttp=supplied==null?new FluxerHttpTransport(owner):null;http=supplied==null?nativeHttp:supplied;
        mailbox=new LobbyMailbox(http,SystemClock::elapsedRealtime,new LobbyMailbox.Listener(){
            public void message(String peer,String content){
                if(publisher!=null)publisher.receive(peer,content);
                if(room!=null&&content.startsWith(PREFIX)&&content.endsWith("\n```"))try{room.receive(peer,JsonParser.parseString(content.substring(PREFIX.length(),content.length()-4)).getAsJsonObject());}catch(RuntimeException ignored){}
                if(contacts!=null&&content.startsWith(LobbyMailbox.CONTACT_PREFIX)&&content.endsWith("\n```"))try{contactContext();contacts.receive(peer,JsonParser.parseString(content.substring(LobbyMailbox.CONTACT_PREFIX.length(),content.length()-4)).getAsJsonObject());publish();}catch(RuntimeException ignored){}
                if(gameNetwork!=null&&content.startsWith(io.github.eriark.abyssme.peer.PeerSignals.PREFIX))gameNetwork.receive(peer,content);
            }
            public void changed(){publish();}
            public void failed(String peer,String reason){if(room!=null&&!peer.equals(LobbyDirectory.BOT))room.deliveryFailed(peer,reason);publish();}
            public void expired(){reset(null);authNotice="Sign-in expired. Open Account to reconnect.";publish();}
        });
        owner.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        timer=owner.scheduleWithFixedDelay(()->{
            if(viewers<=0||closed)return;
            if(SystemClock.elapsedRealtime()>=nextCredentials)load();
            if(verified){if(room!=null)room.tick();if(publisher!=null)publisher.tick();contactContext();if(contacts!=null)contacts.tick(System.currentTimeMillis()/1000);watch();mailbox.tick();publish();}
        },1,1,TimeUnit.SECONDS);app.registerActivityLifecycleCallbacks(activityObserver);publish();
    }
    void resume(){owner.execute(()->{viewers++;load();mailbox.active(true);if(room!=null)room.active(true);});}
    void pause(){owner.execute(()->{viewers=Math.max(0,viewers-1);if(viewers==0){mailbox.active(false);if(room!=null)room.active(false);}});}
    void refresh(){owner.execute(()->{nextCredentials=0;load();mailbox.refresh();if(publisher!=null)publisher.retry();});}
    private void load(){
        nextCredentials=SystemClock.elapsedRealtime()+10000;
        String saved;try{saved=credentials.read();}catch(Exception e){reset(null);authNotice="Could not unlock sign-in. Open Account to recover it.";publish();return;}
        if(Objects.equals(saved,token)&&(verified||verifying))return;
        if(!Objects.equals(saved,token))reset(saved);
        if(token==null){publish();return;}verifying=true;long generation=epoch;
        http.request("GET","/v1/users/@me",null,token,r->{
            if(generation!=epoch||closed)return;verifying=false;
            if(!r.ok()||!LobbyDirectory.id(text(r.body,"id"))){authNotice="Could not verify sign-in. Refresh or open Account.";if(r.status==429)nextCredentials=SystemClock.elapsedRealtime()+Math.max(1000,Math.min(300000,r.retryMillis));publish();return;}
            self=text(r.body,"id");verified=true;authNotice="";mailbox.configure(token,self);mailbox.active(viewers>0);
            room=new LobbyRoom(self,()->System.currentTimeMillis()/1000,capabilities,(peer,p)->mailbox.send(peer,PREFIX+p+"\n```",
                    text(p,"kind").equals("state")&&text(p,"reply").isEmpty()?text(p,"boot")+"/"+text(p,"lobby"):""),this::roomChanged);
            room.active(viewers>0);
            gameNetwork=new LobbyPeerCoordinator(self,room,LobbyPeerCoordinator.NATIVE,new LobbyPeerCoordinator.Events(){
                public boolean signal(String peer,List<String> fragments){return mailbox.sendPeerSignals(peer,fragments);}
                public void prepared(javax.bluetooth.VirtualBluetooth adapter){publish();}
                public void closed(String reason){authNotice=reason;publish();}
            });
            publisher=new LobbyPublisher(self,publicationStore.apply(self),()->System.currentTimeMillis()/1000,c->mailbox.send(LobbyDirectory.BOT,c),this::publish);
            contacts=new TemporaryContacts(self,journals.apply(self),(method,path,body,done)->{
                if(SystemClock.elapsedRealtime()<contactRetryAt){done.accept(new TemporaryContacts.Reply(429,null,(int)((contactRetryAt-SystemClock.elapsedRealtime())/1000+1)));return;}
                long requestEpoch=epoch;
                http.request(method,path,method.equals("GET")||method.equals("DELETE")?null:body,token,result->{
                    if(requestEpoch!=epoch||closed)return;
                    if(result.status==429)contactRetryAt=SystemClock.elapsedRealtime()+Math.max(15000,result.retryMillis);
                    done.accept(new TemporaryContacts.Reply(result.status,result.data,(int)Math.min(300,result.retryMillis/1000)));
                    if(result.status==401){reset(null);authNotice="Sign-in expired. Open Account to reconnect.";}
                    publish();
                });
            },(peer,p)->mailbox.send(peer,LobbyMailbox.CONTACT_PREFIX+p+"\n```"),System.currentTimeMillis()/1000);
            contactContext();watch();
            publish();mailbox.refresh();
        });
    }
    private void reset(String token){++epoch;if(networkTimer!=null)networkTimer.cancel(false);networkTimer=null;if(gameNetwork!=null)gameNetwork.close();gameNetwork=null;if(contacts!=null)contacts.close();contacts=null;manualLease=null;contactRetryAt=0;this.token=token;self="";verified=verifying=false;room=null;publisher=null;mailbox.configure(null,"");authNotice="Sign in to Fluxer to use rooms.";}
    private void roomChanged(){
        if(room==null)return;
        if(gameNetwork!=null)gameNetwork.refresh();
        contactContext();watch();
        if(publisher!=null)publisher.desired(room.hosting()?room.room():"",room.hosting()?room.access():"private");publish();
    }
    private Set<String> admitted(){
        Set<String> peers=new HashSet<>();if(room==null||!room.joined())return peers;
        JsonElement members=room.snapshot().get("members");if(members!=null&&members.isJsonArray())for(JsonElement e:members.getAsJsonArray())peers.add(text(e.getAsJsonObject(),"id"));return peers;
    }
    private void contactContext(){if(contacts!=null)contacts.context(room==null?"":room.room(),room==null?"":room.host(),admitted(),verified&&viewers>0,manualLease!=null||room!=null&&!room.room().isEmpty()&&!room.available());}
    private void watch(){
        if(room==null)return;
        Set<String> peers=new LinkedHashSet<>();JsonElement members=room.snapshot().get("members");
        if(members!=null&&members.isJsonArray())for(JsonElement e:members.getAsJsonArray())peers.add(text(e.getAsJsonObject(),"id"));
        if(contacts!=null){List<String> older=new ArrayList<>(contacts.trackedPeers());Collections.sort(older);if(!older.isEmpty()){int start=(int)((System.currentTimeMillis()/10000*8)%older.size());for(int i=0;i<Math.min(8,older.size());i++)peers.add(older.get((start+i)%older.size()));}}
        if(!room.host().isEmpty())peers.add(room.host());
        List<String> requests=new ArrayList<>(room.requests().keySet());Collections.sort(requests);
        if(!requests.isEmpty()){int start=(int)((System.currentTimeMillis()/10000*8)%requests.size());for(int i=0;i<Math.min(8,requests.size());i++)peers.add(requests.get((start+i)%requests.size()));}
        mailbox.watch(peers);
    }
    private void publish(){
        State s=new State();s.account=self;s.verified=verified;
        s.status=!verified?authNotice:!authNotice.isEmpty()?authNotice:!mailbox.status().isEmpty()?mailbox.status():room==null?"":room.notice();
        if(room!=null){s.room=room.room();s.host=room.host();s.hosting=room.hosting();s.joined=room.joined();s.available=room.available();s.access=room.access();s.snapshot=room.snapshot();s.invitations=room.invitations();
            for(String peer:room.requests().keySet())s.requests.put(peer,mailbox.name(peer));
            JsonElement members=s.snapshot.get("members");if(members!=null&&members.isJsonArray())for(JsonElement e:members.getAsJsonArray()){String id=text(e.getAsJsonObject(),"id");s.names.put(id,id.equals(self)?"You":mailbox.name(id));}
        }
        for(String friend:mailbox.friends())s.friends.put(friend,mailbox.name(friend));
        for(JsonObject invite:s.invitations){String id=text(invite,"host");s.names.put(id,mailbox.name(id));}
        if(contacts!=null){s.contacts=contacts.state();for(String id:object(s.contacts,"peers").keySet())s.names.put(id,mailbox.name(id));}
        s.publication=publisher==null?"":publisher.status();state.postValue(s);
    }
    void action(String account,Consumer<LobbyRoom> action){owner.execute(()->{if(!closed&&verified&&self.equals(account)&&room!=null){authNotice="";action.accept(room);}});}
    /** Internal runtime hook: the caller has verified the local content hash and installed adapter.
     * No UI/directory action calls this yet; production capabilities intentionally remain empty. */
    void prepareGameNetwork(String account,LobbySession.Game verifiedLocalGame){owner.execute(()->{
        if(!closed&&verified&&self.equals(account)&&gameNetwork!=null){
            if(verifiedLocalGame!=null&&!capabilities.contains(capability(verifiedLocalGame)))return;
            gameNetwork.localContent(verifiedLocalGame);
            if(networkTimer!=null)networkTimer.cancel(false);networkTimer=null;
            if(verifiedLocalGame!=null)networkTimer=owner.scheduleWithFixedDelay(()->{if(!closed&&gameNetwork!=null)gameNetwork.tick();},20,20,TimeUnit.MILLISECONDS);
        }
    });}
    void keep(String account,String peer){owner.execute(()->{if(!closed&&verified&&self.equals(account)&&contacts!=null){contacts.keep(peer);publish();}});}
    void manual(String account,String peer,java.util.function.BiConsumer<Runnable,String> done){owner.execute(()->{
        if(closed||!verified||!self.equals(account)||contacts==null){done.accept(null,"Checking lobby contacts. Refresh and try again.");return;}
        if(manualLease!=null||!contacts.manual(peer)){publish();done.accept(null,"Temporary contact is connecting or ending. Keep it in My room, then retry.");return;}
        Object lease=new Object();manualLease=lease;contactContext();publish();
        done.accept(()->{try{owner.execute(()->{if(manualLease==lease){manualLease=null;contactContext();publish();}});}catch(RejectedExecutionException stopped){}},"");
    });}
    @Override protected void onCleared(){getApplication().unregisterActivityLifecycleCallbacks(activityObserver);owner.execute(()->{closed=true;timer.cancel(false);reset(null);if(nativeHttp!=null)nativeHttp.closeWhenIdle(owner::shutdown);else owner.shutdown();});}
}
