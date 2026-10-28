/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import android.app.Application;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.io.*;
import androidx.lifecycle.*;
import com.google.gson.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import static ru.playsoftware.j2meloader.multiplayer.LobbyRoom.*;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.config.ProfilesManager;
import javax.microedition.shell.MicroLoader;

/** App-process room owner; navigating to Chats does not create a second host authority. */
public final class LobbyRoomModel extends AndroidViewModel {
    static final ViewModelStore STORE=new ViewModelStore();
    static LobbyRoomModel get(Application app){return new ViewModelProvider(STORE,new ViewModelProvider.AndroidViewModelFactory(app)).get(LobbyRoomModel.class);}
    public static final class State {
        public SocialNotice statusCode=SocialNotice.ROOM_SIGN_IN_TO_FLUXER_TO_USE_ROOMS,publicationCode=SocialNotice.NONE;
        public String publicationDetail="";
        public String account="",status=SocialNotice.ROOM_SIGN_IN_TO_FLUXER_TO_USE_ROOMS.english,publication="",room="",host="",access="private";
        public boolean verified,hosting,joined,available;
        public boolean gameSelection,checkingGame,launchOffered;
        public String gameTitle="",attempt="",selectedGamePath="";
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
    private String token,self="",authNotice=SocialNotice.ROOM_SIGN_IN_TO_FLUXER_TO_USE_ROOMS.english;
    private SocialNotice authCode=SocialNotice.ROOM_SIGN_IN_TO_FLUXER_TO_USE_ROOMS;
    private void auth(SocialNotice code){authCode=code;authNotice=code.english;}
    private void auth(String raw){authCode=raw.isEmpty()?SocialNotice.NONE:SocialNotice.EXTERNAL;authNotice=raw;}
    private boolean verifying,verified,closed;
    private int viewers;
    private long epoch,nextCredentials;
    private final ScheduledFuture<?> timer;
    private ScheduledFuture<?> networkTimer;
    private final ExecutorService files=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private InstalledNetworkGame installed;
    private String gameTitle="",runtimeAttempt="";
    private GameNetworkLease gameLease;
    private Intent launchIntent;
    private boolean launchTaken,attachmentSent,checkingGame,runtimeUpdating;
    private long selectionEpoch;
    private long returningUntil;
    private final Application.ActivityLifecycleCallbacks activityObserver=new Application.ActivityLifecycleCallbacks(){
        private final Set<android.app.Activity> tracked=Collections.newSetFromMap(new IdentityHashMap<>());
        public void onActivityStarted(android.app.Activity a){if(!(a instanceof LobbyRoomActivity)&&tracked.add(a))resume();}
        public void onActivityStopped(android.app.Activity a){if(tracked.remove(a))pause();}
        public void onActivityCreated(android.app.Activity a,android.os.Bundle b){}public void onActivityResumed(android.app.Activity a){}
        public void onActivityPaused(android.app.Activity a){}public void onActivitySaveInstanceState(android.app.Activity a,android.os.Bundle b){}public void onActivityDestroyed(android.app.Activity a){}
    };
    public LobbyRoomModel(Application app){this(app,new FluxerCredentialStore(app),null,
            ru.playsoftware.j2meloader.BuildConfig.EXPERIMENTAL_MULTIPLAYER?Collections.singleton("java/bluetooth/1"):Collections.emptySet(),account->{
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
            public void failed(String peer,SocialNotice reason){if(room!=null&&!peer.equals(LobbyDirectory.BOT))room.deliveryFailed(peer,reason);publish();}
            public void failed(String peer,String reason){if(room!=null&&!peer.equals(LobbyDirectory.BOT))room.deliveryFailed(peer,reason);publish();}
            public void expired(){reset(null);auth(SocialNotice.ROOM_SIGN_IN_EXPIRED_OPEN_ACCOUNT_TO_RECONNECT);publish();}
        });
        owner.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        timer=owner.scheduleWithFixedDelay(()->{
            if(returningUntil!=0&&SystemClock.elapsedRealtime()>=returningUntil){returningUntil=0;visibility();}
            if(!activeOwner()||closed)return;
            if(SystemClock.elapsedRealtime()>=nextCredentials)load();
            if(verified){if(room!=null)room.tick();if(publisher!=null)publisher.tick();contactContext();if(contacts!=null)contacts.tick(System.currentTimeMillis()/1000);watch();mailbox.tick();publish();}
        },1,1,TimeUnit.SECONDS);app.registerActivityLifecycleCallbacks(activityObserver);publish();
    }
    private boolean activeOwner(){return viewers>0||!runtimeAttempt.isEmpty()||SystemClock.elapsedRealtime()<returningUntil;}
    private void visibility(){boolean active=activeOwner();mailbox.active(active);if(room!=null&&room.isActive()!=active)room.active(active);}
    void resume(){owner.execute(()->{viewers++;returningUntil=0;load();visibility();});}
    void pause(){owner.execute(()->{viewers=Math.max(0,viewers-1);visibility();});}
    void refresh(){owner.execute(()->{nextCredentials=0;load();mailbox.refresh();if(publisher!=null)publisher.retry();});}
    private void load(){
        nextCredentials=SystemClock.elapsedRealtime()+10000;
        String saved;try{saved=credentials.read();}catch(Exception e){reset(null);auth(SocialNotice.ROOM_COULD_NOT_UNLOCK_SIGN_IN_OPEN_ACCOUNT);publish();return;}
        if(Objects.equals(saved,token)&&(verified||verifying))return;
        if(!Objects.equals(saved,token))reset(saved);
        if(token==null){publish();return;}verifying=true;long generation=epoch;
        http.request("GET","/v1/users/@me",null,token,r->{
            if(generation!=epoch||closed)return;verifying=false;
            if(!r.ok()||!LobbyDirectory.id(text(r.body,"id"))){auth(SocialNotice.ROOM_COULD_NOT_VERIFY_SIGN_IN_REFRESH_OR);if(r.status==429)nextCredentials=SystemClock.elapsedRealtime()+Math.max(1000,Math.min(300000,r.retryMillis));publish();return;}
            self=text(r.body,"id");verified=true;auth(SocialNotice.NONE);mailbox.configure(token,self);mailbox.active(activeOwner());
            room=new LobbyRoom(self,()->System.currentTimeMillis()/1000,capabilities,(peer,p)->mailbox.send(peer,PREFIX+p+"\n```",
                    text(p,"kind").equals("state")&&text(p,"reply").isEmpty()?text(p,"boot")+"/"+text(p,"lobby"):""),this::roomChanged);
            room.active(activeOwner());
            gameNetwork=new LobbyPeerCoordinator(self,room,this::processLink,new LobbyPeerCoordinator.Events(){
                public boolean signal(String peer,List<String> fragments){return mailbox.sendPeerSignals(peer,fragments);}
                public void prepared(javax.bluetooth.VirtualBluetooth adapter){auth(SocialNotice.NONE);publish();}
                public void closed(String reason){networkClosed(reason);}
            });
            publisher=new LobbyPublisher(self,publicationStore.apply(self),()->System.currentTimeMillis()/1000,c->mailbox.send(LobbyDirectory.BOT,c),this::publish);
            contacts=new TemporaryContacts(self,journals.apply(self),(method,path,body,done)->{
                if(SystemClock.elapsedRealtime()<contactRetryAt){done.accept(new TemporaryContacts.Reply(429,null,(int)((contactRetryAt-SystemClock.elapsedRealtime())/1000+1)));return;}
                long requestEpoch=epoch;
                http.request(method,path,method.equals("GET")||method.equals("DELETE")?null:body,token,result->{
                    if(requestEpoch!=epoch||closed)return;
                    if(result.status==429)contactRetryAt=SystemClock.elapsedRealtime()+Math.max(15000,result.retryMillis);
                    done.accept(new TemporaryContacts.Reply(result.status,result.data,(int)Math.min(300,result.retryMillis/1000)));
                    if(result.status==401){reset(null);auth(SocialNotice.ROOM_SIGN_IN_EXPIRED_OPEN_ACCOUNT_TO_RECONNECT);}
                    publish();
                });
            },(peer,p)->mailbox.send(peer,LobbyMailbox.CONTACT_PREFIX+p+"\n```"),System.currentTimeMillis()/1000);
            contactContext();watch();
            publish();mailbox.refresh();
        });
    }
    private void reset(String token){++epoch;++selectionEpoch;checkingGame=false;clearRuntime();returningUntil=0;installed=null;gameTitle="";if(networkTimer!=null)networkTimer.cancel(false);networkTimer=null;if(gameNetwork!=null)gameNetwork.close();gameNetwork=null;if(contacts!=null)contacts.close();contacts=null;manualLease=null;contactRetryAt=0;this.token=token;self="";verified=verifying=false;room=null;publisher=null;mailbox.configure(null,"");auth(SocialNotice.ROOM_SIGN_IN_TO_FLUXER_TO_USE_ROOMS);}
    private void roomChanged(){
        if(room==null)return;
        runtime();
        if(!room.joined()){
            if(networkTimer!=null)networkTimer.cancel(false);networkTimer=null;
            ++selectionEpoch;checkingGame=false;installed=null;gameTitle="";
            if(gameNetwork!=null)gameNetwork.localContent(null);
        }
        if(gameNetwork!=null)gameNetwork.refresh();
        runtime();
        contactContext();watch();
        if(publisher!=null)publisher.desired(room.hosting()?room.room():"",room.hosting()?room.access():"private",room.snapshot(),gameTitle);publish();
    }
    private Set<String> admitted(){
        Set<String> peers=new HashSet<>();if(room==null||!room.joined())return peers;
        JsonElement members=room.snapshot().get("members");if(members!=null&&members.isJsonArray())for(JsonElement e:members.getAsJsonArray())peers.add(text(e.getAsJsonObject(),"id"));return peers;
    }
    private void contactContext(){if(contacts!=null)contacts.context(room==null?"":room.room(),room==null?"":room.host(),admitted(),verified&&activeOwner(),manualLease!=null||room!=null&&!room.room().isEmpty()&&!room.available());}
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
        s.gameSelection=capabilities.contains("java/bluetooth/1");s.checkingGame=checkingGame;s.gameTitle=gameTitle;
        if(room==null||installed==null||!installed.game.equals(game(object(room.snapshot(),"game"))))s.gameTitle="";
        else s.selectedGamePath=installed.directory.getPath();
        s.attempt=runtimeAttempt;s.launchOffered=launchIntent!=null&&!launchTaken;
        s.statusCode=!verified?authCode:!authNotice.isEmpty()?authCode:!mailbox.status().isEmpty()?mailbox.statusCode():room==null?SocialNotice.NONE:room.noticeCode();
        s.status=!verified?authNotice:!authNotice.isEmpty()?authNotice:!mailbox.status().isEmpty()?mailbox.status():room==null?"":room.notice();
        if(room!=null){s.room=room.room();s.host=room.host();s.hosting=room.hosting();s.joined=room.joined();s.available=room.available();s.access=room.access();s.snapshot=room.snapshot();s.invitations=room.invitations();
            for(String peer:room.requests().keySet())s.requests.put(peer,mailbox.name(peer));
            JsonElement members=s.snapshot.get("members");if(members!=null&&members.isJsonArray())for(JsonElement e:members.getAsJsonArray()){String id=text(e.getAsJsonObject(),"id");s.names.put(id,id.equals(self)?"You":mailbox.name(id));}
        }
        for(String friend:mailbox.friends())s.friends.put(friend,mailbox.name(friend));
        for(JsonObject invite:s.invitations){String id=text(invite,"host");s.names.put(id,mailbox.name(id));}
        if(contacts!=null){s.contacts=contacts.state();for(String id:object(s.contacts,"peers").keySet())s.names.put(id,mailbox.name(id));}
        s.publication=publisher==null?"":publisher.status();
        s.publicationCode=publisher==null?SocialNotice.NONE:publisher.statusCode();s.publicationDetail=publisher==null?"":publisher.rejectionCode();
        RoomStatusProvider.update(!closed&&s.verified&&!s.room.isEmpty());state.postValue(s);
    }
    void action(String account,Consumer<LobbyRoom> action){owner.execute(()->{if(!closed&&verified&&self.equals(account)&&room!=null){auth(SocialNotice.NONE);action.accept(room);}});}
    void games(Consumer<List<NetworkGameCatalog.Entry>> success,Consumer<SocialNotice> failure){files.execute(()->{
        try{List<NetworkGameCatalog.Entry> entries=NetworkGameCatalog.list(new File(Config.getAppDir()));main.post(()->success.accept(entries));}
        catch(IOException|RuntimeException e){
            android.util.Log.w("LobbyRoomModel","Game lookup failed",e);
            main.post(()->failure.accept(NetworkGameException.notice(e)));
        }
    });}
    void createForGame(String account,String access,NetworkGameCatalog.Entry entry){owner.execute(()->{
        if(closed||!verified||!self.equals(account)||room==null||!room.room().isEmpty())return;
        room.create(access);
        if(entry!=null)prepareGame(account,entry);
    });}
    void chooseGame(String account,NetworkGameCatalog.Entry entry){owner.execute(()->prepareGame(account,entry));}
    private void prepareGame(String account,NetworkGameCatalog.Entry entry){
        if(closed||checkingGame||!verified||!self.equals(account)||room==null||!room.joined()||!room.launchAttempt().isEmpty()||!capabilities.contains("java/bluetooth/1"))return;
        if(room.hosting()&&room.snapshot().getAsJsonArray("members").size()>2){auth(SocialNotice.ROOM_THIS_GAME_NEEDS_TWO_PLAYERS_REMOVE_EXTRA);publish();return;}
        long selection=++selectionEpoch;String selectedRoom=room.room();long generation=number(room.snapshot(),"generation");
        checkingGame=true;returningUntil=0;auth(SocialNotice.ROOM_CHECKING_INSTALLED_GAME);gameNetwork.localContent(null);publish();
        files.execute(()->{
            InstalledNetworkGame result=null;SocialNotice error=SocialNotice.NONE;
            try{
                File config=new File(Config.getConfigsDir(),entry.directory.getName());
                if(ProfilesManager.loadConfig(config)==null)throw new NetworkGameException(SocialNotice.GAME_SETUP_REQUIRED);
                result=NetworkGameCatalog.verify(new File(Config.getAppDir()),entry);
                MicroLoader.prepareNetworkCode(result.directory,getApplication().getCodeCacheDir());
                result.recheck(result.directory.getPath());
            }catch(IOException|RuntimeException e){
                android.util.Log.w("LobbyRoomModel","Game preparation failed",e);
                error=NetworkGameException.notice(e);
            }
            InstalledNetworkGame checked=result;SocialNotice problem=error;
            owner.execute(()->{
                if(closed||selection!=selectionEpoch)return;checkingGame=false;
                if(!self.equals(account)||room==null||!room.room().equals(selectedRoom)||number(room.snapshot(),"generation")!=generation){auth(SocialNotice.ROOM_ROOM_CHANGED_SELECT_THE_GAME_AGAIN);publish();return;}
                if(problem!=SocialNotice.NONE){auth(problem);publish();return;}
                if(!room.hosting()&&!checked.game.equals(game(object(room.snapshot(),"game")))){auth(SocialNotice.ROOM_THIS_IS_A_DIFFERENT_GAME_EDITION_SELECT);publish();return;}
                if(room.hosting()&&room.snapshot().getAsJsonArray("members").size()>checked.game.players){auth(SocialNotice.ROOM_THIS_GAME_NEEDS_TWO_PLAYERS_REMOVE_EXTRA);publish();return;}
                installed=checked;gameTitle=entry.title;auth(SocialNotice.NONE);
                if(room.hosting()){room.select(null);room.select(checked.game);}
                gameNetwork.localContent(checked.game);startNetworkTimer();publish();
            });
        });
    }
    private LobbyPeerCoordinator.Link processLink(io.github.eriark.abyssme.peer.PeerBinding binding,String account,LobbyPeerCoordinator.Events events)throws IOException{
        if(installed==null)throw new IOException("Select an installed game first");
        GameNetworkLease lease=new GameNetworkLease(binding,account,installed,true);gameLease=lease;
        BluetoothPeerSession session=new BluetoothPeerSession(binding,account,null,lease,new BluetoothPeerSession.Listener(){
            public boolean signal(String peer,List<String> fragments){return events.signal(peer,fragments);}
            public void prepared(javax.bluetooth.VirtualBluetooth adapter){events.prepared(adapter);}
            public void closed(String reason){events.closed(reason);}
        });
        return new LobbyPeerCoordinator.Link(){
            public void receive(String author,String content){session.receive(author,content);}
            public void tick(){session.tick();}
            public void close(){session.close();if(gameLease==lease)gameLease=null;}
        };
    }
    private void startNetworkTimer(){
        if(networkTimer!=null)networkTimer.cancel(false);
        networkTimer=owner.scheduleWithFixedDelay(()->{if(!closed&&gameNetwork!=null){gameNetwork.tick();runtime();}},20,20,TimeUnit.MILLISECONDS);
    }
    private void networkClosed(String reason){
        // Coordinator teardown can synchronously finish the room before this callback.
        boolean returning=!runtimeAttempt.isEmpty()||returningUntil>SystemClock.elapsedRealtime();
        runtime();
        if(returning)auth(SocialNotice.ROOM_GAME_CONNECTION_ENDED_PREPARE_AND_PRESS_READY);else auth(reason);
        publish();
    }
    private void clearRuntime(){
        // The remote process exits asynchronously. Keep terminal controls alive until the room resumes.
        if(launchTaken)returningUntil=SystemClock.elapsedRealtime()+15000;
        if(gameLease!=null)gameLease.close();gameLease=null;runtimeAttempt="";launchIntent=null;launchTaken=attachmentSent=false;
        // A finished process consumed its verification/transport attempt. Revoke it
        // before finish() broadcasts a new generation and re-enters roomChanged().
        if(gameNetwork!=null)gameNetwork.localContent(null);
    }
    private void runtime(){
        if(runtimeUpdating)return;runtimeUpdating=true;
        try{
            String attempt=room==null?"":room.launchAttempt();
            if(!runtimeAttempt.isEmpty()&&!runtimeAttempt.equals(attempt)){String ended=runtimeAttempt;clearRuntime();if(room!=null)room.finish(ended);visibility();publish();}
            if(attempt.isEmpty())return;
            if(gameLease==null||!gameLease.retain(self,room.peerBinding(),installed)){clearRuntime();room.finish(attempt);visibility();publish();return;}
            if(runtimeAttempt.isEmpty()){
                try{launchIntent=GameNetworkService.offer(getApplication(),gameTitle,gameLease);runtimeAttempt=attempt;visibility();publish();}
                catch(IOException e){clearRuntime();room.finish(attempt);auth(SocialNotice.ROOM_COULD_NOT_START_THIS_GAME_PREPARE_IT);visibility();publish();return;}
            }
            if(gameLease.adapterReady()&&!attachmentSent){attachmentSent=true;room.attached(attempt);}
            if(room.running()&&gameLease.adapterReady())gameLease.release();
        }catch(IOException e){String ended=runtimeAttempt;clearRuntime();if(room!=null)room.finish(ended);visibility();publish();}
        finally{runtimeUpdating=false;}
    }
    void launch(String account,String attempt,Consumer<Intent> start){owner.execute(()->{
        if(!closed&&self.equals(account)&&runtimeAttempt.equals(attempt)&&!launchTaken&&launchIntent!=null){
            launchTaken=true;Intent intent=new Intent(launchIntent);publish();
            main.post(()->start.accept(intent));
        }
    });}
    void keep(String account,String peer){owner.execute(()->{if(!closed&&verified&&self.equals(account)&&contacts!=null){contacts.keep(peer);publish();}});}
    void manual(String account,String peer,java.util.function.BiConsumer<Runnable,String> done){owner.execute(()->{
        if(closed||!verified||!self.equals(account)||contacts==null){done.accept(null,"Checking lobby contacts. Refresh and try again.");return;}
        if(manualLease!=null||!contacts.manual(peer)){publish();done.accept(null,"Temporary contact is connecting or ending. Keep it in My room, then retry.");return;}
        Object lease=new Object();manualLease=lease;contactContext();publish();
        done.accept(()->{try{owner.execute(()->{if(manualLease==lease){manualLease=null;contactContext();publish();}});}catch(RejectedExecutionException stopped){}},"");
    });}
    @Override protected void onCleared(){RoomStatusProvider.update(false);getApplication().unregisterActivityLifecycleCallbacks(activityObserver);files.shutdownNow();owner.execute(()->{closed=true;RoomStatusProvider.update(false);timer.cancel(false);reset(null);if(nativeHttp!=null)nativeHttp.closeWhenIdle(owner::shutdown);else owner.shutdown();});}
}
