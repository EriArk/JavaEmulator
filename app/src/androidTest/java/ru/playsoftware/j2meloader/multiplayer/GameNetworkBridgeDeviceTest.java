/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import android.content.*;
import android.os.*;
import androidx.test.platform.app.InstrumentationRegistry;
import io.github.eriark.abyssme.peer.PeerBinding;
import javax.bluetooth.VirtualBluetooth;
import javax.microedition.io.*;
import org.junit.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class GameNetworkBridgeDeviceTest {
    private static final String A="1563512156446527490", B="1563519321118879744";
    private Context context;
    private File root;
    private InstalledNetworkGame installed;
    private PeerBinding binding;
    @Before public void setup() throws Exception {
        context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        root=Files.createTempDirectory(context.getCacheDir().toPath(),"network-game-").toFile();
        File directory=new File(root,"game");assertTrue(directory.mkdir());
        for(String file:new String[]{"res.jar","converted.dex","converted.dex.conf"})Files.write(new File(directory,file).toPath(),new byte[]{97,98,99});
        LobbySession.Game game=new LobbySession.Game("java","ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad","bluetooth","1",2);
        installed=InstalledNetworkGame.verify(root,directory,game);
        binding=new PeerBinding(UUID.randomUUID(),1,UUID.randomUUID(),game.engine,game.contentHash,game.mode,game.adapterVersion,Arrays.asList(A,B));
    }
    @After public void cleanup() throws Exception {
        try(java.util.stream.Stream<Path> files=Files.walk(root.toPath())) {
            for(Path path:(Iterable<Path>)files.sorted(Comparator.reverseOrder())::iterator)Files.deleteIfExists(path);
        }
    }
    @Test public void launchCannotBeReusedOrClaimedByAnotherGame() throws Exception {
        try(GameNetworkLease lease=new GameNetworkLease(binding,B,installed)){
            GameNetworkService.offer(context,"Fixture",lease);
            assertThrows(IOException.class,()->lease.claim(UUID.randomUUID().toString(),installed.directory.getPath(),new Binder()));
            assertThrows(IOException.class,()->lease.claim(lease.ticket,root.getPath(),new Binder()));
            try(ParcelFileDescriptor fd=lease.claim(lease.ticket,installed.directory.getPath(),new Binder())){
                assertTrue(lease.attached());assertThrows(IOException.class,()->lease.claim(lease.ticket,installed.directory.getPath(),new Binder()));
            }
        }
    }
    @Test public void gameMutationAndAuthorityRevocationFailClosed() throws Exception {
        try(GameNetworkLease lease=new GameNetworkLease(binding,B,installed)){
            GameNetworkService.offer(context,"Fixture",lease);
            Files.write(new File(installed.directory,"converted.dex").toPath(),new byte[]{1});
            assertThrows(IOException.class,()->lease.claim(lease.ticket,installed.directory.getPath(),new Binder()));
            assertFalse(lease.retain(A,binding,installed));assertThrows(IOException.class,lease::pollOutbound);
        }
    }
    @Test public void slowVerificationDoesNotBlockPacketsOrRevocation() throws Exception {
        CountDownLatch checking=new CountDownLatch(1),finish=new CountDownLatch(1);
        ExecutorService workers=Executors.newFixedThreadPool(2);
        try(GameNetworkLease lease=new GameNetworkLease(binding,B,installed,path->{
            checking.countDown();
            try{if(!finish.await(5,TimeUnit.SECONDS))throw new IOException("Fixture timed out");}
            catch(InterruptedException e){throw new IOException(e);}
            installed.recheck(path);
        })){
            GameNetworkService.offer(context,"Fixture",lease);
            Future<ParcelFileDescriptor> claim=workers.submit(()->lease.claim(lease.ticket,installed.directory.getPath(),new Binder()));
            assertTrue(checking.await(2,TimeUnit.SECONDS));
            assertNull(workers.submit(lease::pollOutbound).get(1,TimeUnit.SECONDS));
            workers.submit(lease::close).get(1,TimeUnit.SECONDS);finish.countDown();
            ExecutionException ended=assertThrows(ExecutionException.class,()->claim.get(2,TimeUnit.SECONDS));
            assertTrue(ended.getCause() instanceof IOException);assertFalse(lease.attached());
        }finally{finish.countDown();workers.shutdownNow();assertTrue(workers.awaitTermination(5,TimeUnit.SECONDS));}
    }
    @Test public void socketClosureStopsBothWorkers() throws Exception {
        ParcelFileDescriptor[] pair=ParcelFileDescriptor.createReliableSocketPair();
        LocalPeerPipe a=new LocalPeerPipe(pair[0]),b=new LocalPeerPipe(pair[1]);
        try {
            a.receive(new byte[]{1,2,3});byte[] received=null;long until=SystemClock.elapsedRealtime()+3000;
            while(received==null&&SystemClock.elapsedRealtime()<until){received=b.pollOutbound();Thread.sleep(5);}
            assertArrayEquals(new byte[]{1,2,3},received);a.close();
            assertTrue(a.workersStopped(3000));assertTrue(b.workersStopped(3000));assertThrows(IOException.class,b::pollOutbound);
        }finally{a.close();b.close();}
    }
    @Test public void oversizedAndMalformedPacketsAreRejected() throws Exception {
        ParcelFileDescriptor[] pair=ParcelFileDescriptor.createReliableSocketPair();LocalPeerPipe pipe=new LocalPeerPipe(pair[0]);
        try(ParcelFileDescriptor fd=pair[1];DataOutputStream out=new DataOutputStream(new FileOutputStream(fd.getFileDescriptor()))){
            assertThrows(IOException.class,()->pipe.receive(new byte[16384]));out.writeShort(0);out.flush();
            assertTrue(pipe.workersStopped(3000));assertThrows(IOException.class,pipe::pollOutbound);
        }finally{pipe.close();}
    }
    @Test public void queueOverflowCannotGrowWithoutBound() throws Exception {
        ParcelFileDescriptor[] pair=ParcelFileDescriptor.createReliableSocketPair();LocalPeerPipe pipe=new LocalPeerPipe(pair[0]);
        try(ParcelFileDescriptor unread=pair[1]){
            assertThrows(IOException.class,()->{for(int i=0;i<1024;i++)pipe.receive(new byte[16383]);});
            assertTrue(pipe.workersStopped(3000));assertThrows(IOException.class,pipe::pollOutbound);
        }finally{pipe.close();}
    }
    @Test public void revocationKeepsClosedAdapterInsteadOfPhysicalBluetooth() throws Exception {
        Probe probe=new Probe();
        try(GameNetworkLease lease=new GameNetworkLease(binding,B,installed)){
            Intent launch=GameNetworkService.offer(context,"Fixture",lease).putExtra("hold",true);
            probe.bind(launch);probe.await("attached",5000);lease.close();probe.awaitPrefix("closed:",5000);
            Parcel data=Parcel.obtain(),reply=Parcel.obtain();
            try{assertTrue(probe.binder.transact(3,data,reply,0));reply.readException();assertEquals(1,reply.readInt());}finally{data.recycle();reply.recycle();}
            probe.kill();Thread.sleep(250);
        }finally{probe.close();}
    }
    @Test public void obsoleteLeaseCannotClearNewLaunch() throws Exception {
        GameNetworkLease old=new GameNetworkLease(binding,B,installed);GameNetworkService.offer(context,"Old",old);old.close();
        try(GameNetworkLease current=new GameNetworkLease(binding,B,installed)){
            GameNetworkService.offer(context,"Current",current);old.close();
            try(ParcelFileDescriptor fd=current.claim(current.ticket,installed.directory.getPath(),new Binder())){assertTrue(current.attached());}
        }
    }
    @Test public void twoProcessesUseEncryptedBluetoothAndProcessDeathRevokesLaunch() throws Exception {
        ScheduledExecutorService owner=Executors.newSingleThreadScheduledExecutor();ExecutorService game=Executors.newSingleThreadExecutor();
        BluetoothPeerSession[] sessions=new BluetoothPeerSession[2];
        CountDownLatch ready=new CountDownLatch(2);AtomicReference<Throwable> failure=new AtomicReference<>();
        VirtualBluetooth[] adapter={null};GameNetworkLease lease=new GameNetworkLease(binding,B,installed);
        List<Runnable> signals=new ArrayList<>();Probe probe=new Probe();boolean localInstalled=false;
        try {
            owner.submit(()->{
                try {
                    for(int i=0;i<2;i++){
                        final int side=i;
                        sessions[i]=new BluetoothPeerSession(binding,i==0?A:B,null,i==0?null:lease,new BluetoothPeerSession.Listener(){
                            public boolean signal(String recipient,List<String> fragments){signals.add(()->{for(String fragment:fragments)sessions[1-side].receive(side==0?A:B,fragment);});return true;}
                            public void prepared(VirtualBluetooth value){if(side==0)adapter[0]=value;ready.countDown();}
                            public void closed(String reason){ready.countDown();}
                        });
                    }
                }catch(Throwable e){failure.set(e);while(ready.getCount()>0)ready.countDown();}
            }).get(10,TimeUnit.SECONDS);
            owner.scheduleWithFixedDelay(()->{
                try {
                    List<Runnable> pending=new ArrayList<>(signals);signals.clear();for(Runnable signal:pending)signal.run();
                    for(BluetoothPeerSession session:sessions)if(session!=null)session.tick();
                }catch(Throwable e){failure.compareAndSet(null,e);while(ready.getCount()>0)ready.countDown();}
            },0,10,TimeUnit.MILLISECONDS);
            assertTrue("Native preparation timed out",ready.await(30,TimeUnit.SECONDS));
            if(failure.get()!=null)throw new AssertionError(failure.get());assertNotNull(adapter[0]);
            VirtualBluetooth.install(adapter[0]);localInstalled=true;
            CountDownLatch listening=new CountDownLatch(1);
            Future<?> echo=game.submit(()->{
                StreamConnectionNotifier server=(StreamConnectionNotifier)Connector.open("btspp://localhost:abcdef0001;name=IPC fixture");
                try(Closeable closeServer=server::close){
                    javax.bluetooth.LocalDevice.getLocalDevice().setDiscoverable(javax.bluetooth.DiscoveryAgent.GIAC);listening.countDown();
                    StreamConnection stream=server.acceptAndOpen();
                    try(Closeable closeStream=stream::close;DataInputStream in=stream.openDataInputStream();DataOutputStream out=stream.openDataOutputStream()){
                        for(int i=0;i<64;i++){byte[] packet=new byte[4096];in.readFully(packet);out.write(packet);}
                        assertEquals(-1,in.read());
                    }
                }return null;
            });
            assertTrue(listening.await(5,TimeUnit.SECONDS));
            Intent launch=GameNetworkService.offer(context,"Fixture",lease);
            probe.bind(launch);probe.await("passed",30000);echo.get(5,TimeUnit.SECONDS);
            assertNotEquals("Fixture must not share app memory",android.os.Process.myPid(),probe.pid);
            assertTrue(lease.attached());probe.kill();
            long until=SystemClock.elapsedRealtime()+5000;while(lease.attached()&&SystemClock.elapsedRealtime()<until)Thread.sleep(10);
            assertFalse("Binder/process death did not revoke the launch",lease.attached());
            assertThrows(IOException.class,lease::pollOutbound);
            assertNull(failure.get());
        }finally{
            probe.close();lease.close();owner.submit(()->{for(BluetoothPeerSession session:sessions)if(session!=null)session.close();}).get(5,TimeUnit.SECONDS);
            owner.shutdownNow();game.shutdownNow();assertTrue(game.awaitTermination(5,TimeUnit.SECONDS));
            if(localInstalled)VirtualBluetooth.detach(adapter[0]);assertTrue(owner.awaitTermination(5,TimeUnit.SECONDS));
        }
    }
    private final class Probe implements Closeable,ServiceConnection {
        private final CountDownLatch connected=new CountDownLatch(1);
        private IBinder binder;private boolean bound;int pid;
        void bind(Intent launch)throws Exception {
            launch.setClassName(context,"ru.playsoftware.j2meloader.multiplayer.GameNetworkProbeService");
            bound=context.bindService(launch,this,Context.BIND_AUTO_CREATE);assertTrue(bound);assertTrue(connected.await(5,TimeUnit.SECONDS));
        }
        public void onServiceConnected(ComponentName name,IBinder service){binder=service;connected.countDown();}
        public void onServiceDisconnected(ComponentName name){}
        void await(String expected,long millis)throws Exception {
            awaitValue(expected,millis,false);
        }
        void awaitPrefix(String expected,long millis)throws Exception {awaitValue(expected,millis,true);}
        void awaitValue(String expected,long millis,boolean prefix)throws Exception {
            long until=SystemClock.elapsedRealtime()+millis;String status="";
            while(SystemClock.elapsedRealtime()<until){
                Parcel data=Parcel.obtain(),reply=Parcel.obtain();try{assertTrue(binder.transact(1,data,reply,0));reply.readException();status=reply.readString();pid=reply.readInt();}finally{data.recycle();reply.recycle();}
                if(prefix?status.startsWith(expected):expected.equals(status))return;if(status.startsWith("failed")||status.startsWith("closed"))fail(status);Thread.sleep(25);
            }fail("Fixture timeout: "+status);
        }
        void kill()throws Exception {Parcel data=Parcel.obtain(),reply=Parcel.obtain();try{binder.transact(2,data,reply,0);reply.readException();}finally{data.recycle();reply.recycle();}}
        public void close(){if(bound){context.unbindService(this);bound=false;}}
    }
}
