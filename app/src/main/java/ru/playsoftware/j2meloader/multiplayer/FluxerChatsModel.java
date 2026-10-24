/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import android.app.Application;
import android.os.SystemClock;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class FluxerChatsModel extends AndroidViewModel {
    final MutableLiveData<FluxerChats.State> state=new MutableLiveData<>();
    private final ScheduledThreadPoolExecutor owner=new ScheduledThreadPoolExecutor(1);
    private final AtomicLong edits=new AtomicLong();
    private final FluxerAuth.Store store;
    private final FluxerHttpTransport http;
    private final FluxerChats chats;
    private final ScheduledFuture<?> timer;

    public FluxerChatsModel(Application application) {this(application,new FluxerCredentialStore(application),null);}
    FluxerChatsModel(Application application,FluxerAuth.Store store,FluxerAuth.Transport supplied) {
        super(application);this.store=store;
        owner.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        http=supplied==null?new FluxerHttpTransport(owner):null;
        chats=new FluxerChats(supplied==null?http:supplied,SystemClock::elapsedRealtime,state::postValue);
        state.postValue(chats.state());
        timer=owner.scheduleWithFixedDelay(chats::tick,1,1,TimeUnit.SECONDS);
    }
    void resume(){owner.execute(()->{chats.active(true);loadSession();});}
    void pause(){owner.execute(()->chats.active(false));}
    void refresh(){owner.execute(()->{loadSession();chats.refresh();if(!chats.state().past)chats.history(false);});}
    private void loadSession(){try{chats.session(store.read());}catch(Exception ignored){chats.storageError();}}
    void select(String id){owner.execute(()->chats.select(id));}
    void list(){owner.execute(chats::list);}
    void older(){owner.execute(chats::older);}
    void latest(){owner.execute(chats::latest);}
    long draft(String id,String text){long revision=edits.incrementAndGet();owner.execute(()->chats.draft(id,text,revision));return revision;}
    void send(String id,long revision){owner.execute(()->chats.send(id,revision));}
    void retry(String channel,String id){owner.execute(()->chats.retry(channel,id));}
    void discard(String channel,String id){owner.execute(()->chats.discard(channel,id));}
    @Override protected void onCleared(){owner.execute(()->{timer.cancel(false);chats.close();if(http!=null)http.closeWhenIdle(owner::shutdown);else owner.shutdown();});}
}
