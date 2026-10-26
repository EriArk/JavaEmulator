/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import android.app.Application;
import android.os.SystemClock;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class LobbyDirectoryModel extends AndroidViewModel {
    final MutableLiveData<LobbyDirectory.State> state=new MutableLiveData<>();
    private final ScheduledThreadPoolExecutor owner=new ScheduledThreadPoolExecutor(1);
    private final FluxerAuth.Store store;
    private final FluxerHttpTransport http;
    private final LobbyDirectory directory;
    private final ScheduledFuture<?> timer;

    public LobbyDirectoryModel(Application app){
        // Match the room owner's experimental adapter; production stays disabled.
        this(app,new FluxerCredentialStore(app),null,ru.playsoftware.j2meloader.BuildConfig.DEBUG
                ?Collections.singleton(LobbyDirectory.JAVA_BLUETOOTH):Collections.emptySet());
    }
    LobbyDirectoryModel(Application app,FluxerAuth.Store store,FluxerAuth.Transport supplied,Set<String> capabilities){
        this(app,store,supplied,capabilities,new LobbyMembershipStore(app));
    }
    LobbyDirectoryModel(Application app,FluxerAuth.Store store,FluxerAuth.Transport supplied,Set<String> capabilities,LobbyDirectory.Membership membership){
        super(app);this.store=store;owner.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        http=supplied==null?new FluxerHttpTransport(owner):null;
        directory=new LobbyDirectory(supplied==null?http:supplied,()->System.currentTimeMillis()/1000,SystemClock::elapsedRealtime,capabilities,state::postValue,membership);
        state.postValue(directory.state());timer=owner.scheduleWithFixedDelay(directory::tick,1,1,TimeUnit.SECONDS);
    }
    void resume(){owner.execute(()->{directory.active(true);load();});}
    void pause(){owner.execute(()->directory.active(false));}
    void refresh(){owner.execute(this::load);}
    void retryCommunity(String account){owner.execute(()->directory.retryCommunity(account));}
    private void load(){try{directory.session(store.read());}catch(Exception ignored){directory.storageError();}}
    @Override protected void onCleared(){owner.execute(()->{timer.cancel(false);directory.close();if(http!=null)http.closeWhenIdle(owner::shutdown);else owner.shutdown();});}
}
