/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import android.app.Service;
import android.content.Intent;
import android.os.*;
import javax.bluetooth.*;
import javax.microedition.io.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Debug-only cross-process fixture. Never packaged in Release or given account credentials. */
public final class GameNetworkProbeService extends Service {
    private MidletNetworkClient client;
    private final ExecutorService game = Executors.newSingleThreadExecutor();
    private volatile String status = "waiting";
    private volatile boolean hold;
    private final Binder binder = new Binder() {
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            if (Binder.getCallingUid() != android.os.Process.myUid()) throw new SecurityException();
            if (code == 1) { reply.writeNoException(); reply.writeString(status); reply.writeInt(android.os.Process.myPid()); return true; }
            if (code == 2) { new Handler(Looper.getMainLooper()).postDelayed(() -> android.os.Process.killProcess(android.os.Process.myPid()), 100); reply.writeNoException(); return true; }
            if (code == 3) { reply.writeNoException(); reply.writeInt(VirtualBluetooth.current()!=null&&!LocalDevice.isPowerOn()?1:0); return true; }
            return super.onTransact(code, data, reply, flags);
        }
    };
    @Override public IBinder onBind(Intent intent) {
        hold = intent.getBooleanExtra("hold", false);
        client = new MidletNetworkClient(this, intent, intent.getData().toString(), () -> {
            status = "attached";
            if (!hold) game.submit(() -> { try { exercise(); status = "passed"; } catch (Throwable e) { status = "failed: " + e; } });
        }, reason -> status = "closed: " + reason);
        client.start(); return binder;
    }
    private void exercise() throws Exception {
        DiscoveryAgent discovery = LocalDevice.getLocalDevice().getDiscoveryAgent();
        long until = SystemClock.elapsedRealtime() + 15000; String url = null;
        while (url == null && SystemClock.elapsedRealtime() < until) {
            url = discovery.selectService(new javax.bluetooth.UUID("abcdef0001", false), 2, false);
            if (url == null) Thread.sleep(50);
        }
        if (url == null) throw new IOException("Missing service");
        StreamConnection stream = (StreamConnection) Connector.open(url);
        try (Closeable closer = stream::close; DataInputStream in = stream.openDataInputStream(); DataOutputStream out = stream.openDataOutputStream()) {
            for (int i = 0; i < 64; i++) {
                byte[] packet = new byte[4096]; new Random(i + 73).nextBytes(packet); out.write(packet);
                byte[] echoed = new byte[4096]; in.readFully(echoed);
                if (!Arrays.equals(packet, echoed)) throw new IOException("Changed stream data");
            }
        }
    }
    @Override public void onDestroy() {
        if (client != null) client.close(); game.shutdownNow();
        // Isolated fixture process must not retain a closed adapter for the next test.
        super.onDestroy();
        android.os.Process.killProcess(android.os.Process.myPid());
    }
}
