/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import android.app.Service;
import android.content.*;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.microedition.shell.MicroActivity;
import static ru.playsoftware.j2meloader.util.Constants.KEY_MIDLET_NAME;

/** Not exported. Only an explicit, one-use launch can obtain the already admitted packet pipe. */
public final class GameNetworkService extends Service {
    static final String DESCRIPTOR = "io.github.eriark.abyssme.GameNetwork.v1";
    public static final String EXTRA_TICKET = "abyssme.networkLaunch";
    static final int CLAIM = IBinder.FIRST_CALL_TRANSACTION;
    private static final AtomicReference<GameNetworkLease> active = new AtomicReference<>();
    private volatile GameNetworkLease owned;

    public static Intent offer(Context context, String title, GameNetworkLease lease) throws IOException {
        if (!active.compareAndSet(null, lease)) throw new IOException("Another network launch is active");
        try { lease.offer(); }
        catch (IOException e) { remove(lease); throw e; }
        return new Intent(Intent.ACTION_DEFAULT, Uri.parse(lease.installed.directory.getPath()), context, MicroActivity.class)
                .putExtra(KEY_MIDLET_NAME, title).putExtra(EXTRA_TICKET, lease.ticket);
    }
    static void remove(GameNetworkLease lease) { active.compareAndSet(lease, null); }
    private final Binder binder = new Binder() {
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (code != CLAIM) return super.onTransact(code, data, reply, flags);
            if (Binder.getCallingUid() != android.os.Process.myUid()) throw new SecurityException("Private game connection");
            data.enforceInterface(DESCRIPTOR);
            String ticket = data.readString(), path = data.readString(); IBinder lifetime = data.readStrongBinder();
            if (data.dataAvail() != 0 || ticket == null || ticket.length() != 36 || path == null || path.length() > 4096)
                throw new IllegalArgumentException("Invalid game launch");
            GameNetworkLease lease = active.get();
            ParcelFileDescriptor descriptor = null;
            try {
                if (lease == null) throw new IOException("No prepared network launch");
                descriptor = lease.claim(ticket, path, lifetime);
                owned = lease;
                reply.writeNoException(); reply.writeInt(lease.host ? 1 : 0);
                descriptor.writeToParcel(reply, 0);
            } catch (IOException e) { reply.writeException(new IllegalStateException(e.getMessage())); }
            finally { if (descriptor != null) try { descriptor.close(); } catch (IOException ignored) { } }
            return true;
        }
    };
    @Override public IBinder onBind(Intent intent) { return binder; }
    @Override public void onDestroy() {
        GameNetworkLease lease = owned; if (lease != null) lease.close(); super.onDestroy();
    }
}
