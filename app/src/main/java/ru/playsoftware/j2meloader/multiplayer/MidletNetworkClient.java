/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import android.content.*;
import android.os.*;
import javax.bluetooth.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Created only by a network launch; installs before any game constructor can access Bluetooth. */
public final class MidletNetworkClient implements Closeable {
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Binder lifetime = new Binder();
    private final Consumer<String> failure;
    private boolean bound, closed;
    private LocalPeerPipe pipe;
    private VirtualBluetooth adapter;
    private final Runnable timeout = () -> fail("Game network preparation timed out. Return to the room.");
    private final ServiceConnection connection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder service) { connected(service); }
        public void onServiceDisconnected(ComponentName name) { fail("Game network connection ended. Return to the room."); }
        public void onBindingDied(ComponentName name) { onServiceDisconnected(name); }
        public void onNullBinding(ComponentName name) { onServiceDisconnected(name); }
    };
    private final String ticket, path;
    private final Runnable ready;

    public MidletNetworkClient(Context context, Intent launch, String path, Runnable ready, Consumer<String> failure) {
        this.context = context; this.ticket = launch.getStringExtra(GameNetworkService.EXTRA_TICKET);
        this.path = path; this.ready = ready; this.failure = failure;
    }
    public void start() {
        if (ticket == null || ticket.length() != 36) { fail("Invalid network launch. Return to the room."); return; }
        main.postDelayed(timeout, 30000);
        try {
            bound = context.bindService(new Intent(context, GameNetworkService.class), connection, Context.BIND_AUTO_CREATE);
            if (!bound) fail("Could not connect to the room.");
        } catch (RuntimeException e) { fail("Could not connect to the room."); }
    }
    private void connected(IBinder service) {
        synchronized (this) { if (closed) return;
        worker.execute(() -> {
            Parcel data = Parcel.obtain(), reply = Parcel.obtain();
            try {
                data.writeInterfaceToken(GameNetworkService.DESCRIPTOR);
                data.writeString(ticket); data.writeString(path); data.writeStrongBinder(lifetime);
                if (!service.transact(GameNetworkService.CLAIM, data, reply, 0)) throw new IOException("Unsupported network bridge");
                reply.readException(); boolean host = reply.readInt() != 0;
                ParcelFileDescriptor fd = ParcelFileDescriptor.CREATOR.createFromParcel(reply);
                PeerBluetoothBackend backend;
                synchronized (this) {
                    if (closed) { fd.close(); return; }
                    pipe = new LocalPeerPipe(fd);
                    backend = new PeerBluetoothBackend(new VirtualBluetooth.Peer(host ? "020000000002" : "020000000001", host ? "Player 2" : "Player 1"), host);
                    adapter = new VirtualBluetooth(new VirtualBluetooth.Peer(host ? "020000000001" : "020000000002", host ? "Player 1" : "Player 2"), backend);
                    VirtualBluetooth.install(adapter);
                }
                boolean released = false;
                long nextCheck = 0;
                while (!Thread.currentThread().isInterrupted()) {
                    synchronized (this) { if (closed) return; }
                    if (!released && SystemClock.elapsedRealtime() >= nextCheck) {
                        nextCheck = SystemClock.elapsedRealtime() + 100;
                        released = acknowledge(service);
                        if (released) main.post(() -> { synchronized (this) { if (closed) return; main.removeCallbacks(timeout); ready.run(); } });
                    }
                    for (int i = 0; i < 16; i++) { byte[] packet = pipe.pollOutbound(); if (packet == null) break; backend.receive(packet); }
                    for (int i = 0; i < 16; i++) { byte[] packet = backend.pollOutbound(); if (packet == null) break; pipe.receive(packet); }
                    Thread.sleep(10);
                }
            } catch (IOException | RuntimeException | RemoteException | InterruptedException e) {
                fail("Game network connection ended. Return to the room to retry.");
            } finally { reply.recycle(); data.recycle(); }
        });
        }
    }
    private boolean acknowledge(IBinder service) throws IOException, RemoteException {
        Parcel data = Parcel.obtain(), reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(GameNetworkService.DESCRIPTOR); data.writeString(ticket); data.writeStrongBinder(lifetime);
            if (!service.transact(GameNetworkService.READY, data, reply, 0)) throw new IOException("Unsupported launch barrier");
            reply.readException(); return reply.readInt() != 0;
        } finally { data.recycle(); reply.recycle(); }
    }
    private void fail(String reason) {
        synchronized (this) { if (closed) return; close(); }
        main.post(() -> failure.accept(reason));
    }
    @Override public synchronized void close() {
        if (closed) return; closed = true; main.removeCallbacks(timeout);
        // Leave the closed adapter installed until the MIDlet process exits: never fall back to the radio.
        if (adapter != null) try { adapter.close(); } catch (IOException ignored) { }
        if (pipe != null) pipe.close();
        worker.shutdownNow();
        main.post(() -> { if (bound) { bound = false; context.unbindService(connection); } });
    }
}
