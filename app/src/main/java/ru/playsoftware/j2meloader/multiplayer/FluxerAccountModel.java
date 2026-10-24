/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import android.app.Application;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Survives rotation and the external browser; all IO/state work stays off the UI thread. */
public final class FluxerAccountModel extends AndroidViewModel {
    final MutableLiveData<FluxerAuth.State> state = new MutableLiveData<>();
    private final ScheduledThreadPoolExecutor owner = new ScheduledThreadPoolExecutor(1);
    private final FluxerHttpTransport transport = new FluxerHttpTransport(owner);
    private final FluxerAuth auth;
    private final java.util.concurrent.ScheduledFuture<?> poll;
    public FluxerAccountModel(Application application) {
        super(application);
        owner.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        auth = new FluxerAuth(transport, new FluxerCredentialStore(application), System::currentTimeMillis, state::postValue);
        owner.execute(auth::restore);
        poll = owner.scheduleWithFixedDelay(auth::tick, 500, 500, TimeUnit.MILLISECONDS);
    }
    void begin() { owner.execute(auth::begin); }
    void cancel() { owner.execute(auth::cancel); }
    void retry() { owner.execute(auth::retry); }
    void signOut() { owner.execute(auth::signOut); }
    void forget() { owner.execute(auth::forget); }
    @Override protected void onCleared() {
        owner.execute(() -> {
            poll.cancel(false);
            auth.close();
            // Allow in-flight initiation/poll callbacks to cancel or revoke abandoned handoffs.
            transport.closeWhenIdle(owner::shutdown);
        });
    }
}
