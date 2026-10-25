/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import android.os.Bundle;
import android.os.SystemClock;
import androidx.test.platform.app.InstrumentationRegistry;
import io.github.eriark.abyssme.peer.PeerBinding;
import javax.bluetooth.*;
import javax.microedition.io.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Opt-in, dedicated accounts only, invoked after the caller checks both identities. */
final class LiveBluetoothScenario {
    static final javax.bluetooth.UUID STREAM=new javax.bluetooth.UUID("abcdef0001",false),PACKET=new javax.bluetooth.UUID("abcdef0002",false);
    static void run(PeerBinding binding,String self,String remote,String credential,boolean host)throws Exception {
        ScheduledThreadPoolExecutor owner=new ScheduledThreadPoolExecutor(1);owner.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        FluxerHttpTransport http=new FluxerHttpTransport(owner);BluetoothPeerSession[] session={null};LobbyMailbox[] mailbox={null};
        AtomicReference<Throwable> error=new AtomicReference<>();AtomicReference<VirtualBluetooth> adapter=new AtomicReference<>();
        CountDownLatch ready=new CountDownLatch(1);boolean installed=false;
        ExecutorService game=Executors.newSingleThreadExecutor();
        try{
            owner.submit(()->{
                mailbox[0]=new LobbyMailbox(http,SystemClock::elapsedRealtime,new LobbyMailbox.Listener(){
                    public void message(String author,String content){if(session[0]!=null)session[0].receive(author,content);}
                    public void changed(){}
                    public void failed(String peer,String reason){error.compareAndSet(null,new AssertionError(reason));ready.countDown();}
                    public void expired(){error.compareAndSet(null,new AssertionError("Sign-in expired"));ready.countDown();}
                });
                mailbox[0].configure(credential,self);mailbox[0].watch(Collections.singletonList(remote));mailbox[0].active(true);
                try{
                    session[0]=new BluetoothPeerSession(binding,self,null,new BluetoothPeerSession.Listener(){
                        public boolean signal(String peer,List<String> fragments){return mailbox[0].sendPeerSignals(peer,fragments);}
                        public void prepared(VirtualBluetooth value){adapter.set(value);ready.countDown();}
                        public void closed(String reason){error.compareAndSet(null,new AssertionError(reason));ready.countDown();}
                    });
                }catch(Exception e){error.set(e);ready.countDown();}
            }).get(10,TimeUnit.SECONDS);
            owner.scheduleWithFixedDelay(()->{
                try{mailbox[0].tick();if(session[0]!=null)session[0].tick();}
                catch(Throwable e){error.compareAndSet(null,e);if(session[0]!=null)session[0].close();ready.countDown();}
            },0,10,TimeUnit.MILLISECONDS);
            assertTrue("Peer preparation timed out",ready.await(140,TimeUnit.SECONDS));
            if(error.get()!=null)throw new AssertionError("Peer preparation failed",error.get());
            VirtualBluetooth.install(adapter.get());installed=true;
            game.submit(()->{exercise(host);return null;}).get(80,TimeUnit.SECONDS);
            Bundle report=new Bundle();report.putString("bluetooth_peer_result",(host?"host":"guest")+": Connector RFCOMM 256 KiB echo, discovery, custom SDP, L2CAP packets and truncation passed");
            InstrumentationRegistry.getInstrumentation().sendStatus(2,report);
        }finally{
            owner.submit(()->{mailbox[0].active(false);if(session[0]!=null)session[0].close();http.closeWhenIdle(owner::shutdown);}).get(10,TimeUnit.SECONDS);
            game.shutdownNow();assertTrue("Game fixture did not stop",game.awaitTermination(5,TimeUnit.SECONDS));
            if(installed)VirtualBluetooth.detach(adapter.get());
            if(!owner.awaitTermination(40,TimeUnit.SECONDS)){owner.shutdownNow();fail("HTTP owner did not drain");}
        }
    }
    private static void exercise(boolean host)throws Exception {
        LocalDevice local=LocalDevice.getLocalDevice();assertTrue(local.setDiscoverable(DiscoveryAgent.GIAC));
        if(host){
            StreamConnectionNotifier server=(StreamConnectionNotifier)Connector.open("btspp://localhost:"+STREAM+";name=AbyssME test");
            L2CAPConnectionNotifier packets=(L2CAPConnectionNotifier)Connector.open("btl2cap://localhost:"+PACKET+";ReceiveMTU=512");
            try(Closeable closeServer=server::close;Closeable closePackets=packets::close){
                ServiceRecord record=local.getRecord(server);record.setAttributeValue(0x201,new DataElement(DataElement.STRING,"Encrypted Bluetooth fixture"));local.updateRecord(record);
                StreamConnection stream=server.acceptAndOpen();
                try(Closeable closeStream=stream::close;DataInputStream in=stream.openDataInputStream();DataOutputStream out=stream.openDataOutputStream()){
                    for(int i=0;i<64;i++){byte[] bytes=new byte[4096];in.readFully(bytes);assertArrayEquals(block(i),bytes);out.write(bytes);}
                    L2CAPConnection channel=packets.acceptAndOpen();
                    try(Closeable closeChannel=channel::close){
                        byte[] small=new byte[3];assertEquals(3,channel.receive(small));assertArrayEquals(new byte[]{7,7,7},small);
                        assertEquals(2,channel.receive(small));assertEquals(8,small[0]);assertEquals(9,small[1]);
                        channel.send(new byte[]{42});assertEquals(0,channel.receive(small));channel.send(new byte[]{43});
                        assertEquals(0x13572468,in.readInt());out.writeInt(0x24681357);out.flush();
                        assertEquals(0x55aa,in.readInt());
                        assertEquals("Guest closes its logical stream before the host tears down the peer",-1,in.read());
                    }
                }
            }
        }else{
            DiscoveryAgent discovery=local.getDiscoveryAgent();
            long until=SystemClock.elapsedRealtime()+30000;String streamUrl=null,packetUrl=null;
            while(SystemClock.elapsedRealtime()<until&&(streamUrl==null||packetUrl==null)){
                streamUrl=discovery.selectService(STREAM,2,false);packetUrl=discovery.selectService(PACKET,2,false);
                if(streamUrl==null||packetUrl==null)Thread.sleep(100);
            }
            assertNotNull("RFCOMM service not discovered",streamUrl);assertNotNull("L2CAP service not discovered",packetUrl);
            CountDownLatch found=new CountDownLatch(1);List<ServiceRecord> records=new CopyOnWriteArrayList<>();
            RemoteDevice[] peers=discovery.retrieveDevices(DiscoveryAgent.CACHED);
            if(peers==null){CountDownLatch inquiry=new CountDownLatch(1);List<RemoteDevice> seen=new CopyOnWriteArrayList<>();
                discovery.startInquiry(DiscoveryAgent.GIAC,new DiscoveryListener(){
                    public void deviceDiscovered(RemoteDevice d,DeviceClass c){seen.add(d);}public void inquiryCompleted(int t){inquiry.countDown();}
                    public void servicesDiscovered(int i,ServiceRecord[] r){}public void serviceSearchCompleted(int i,int c){}
                });assertTrue(inquiry.await(10,TimeUnit.SECONDS));assertEquals(1,seen.size());peers=seen.toArray(new RemoteDevice[0]);}
            discovery.searchServices(new int[]{0x201},new javax.bluetooth.UUID[]{STREAM},peers[0],new DiscoveryListener(){
                public void deviceDiscovered(RemoteDevice d,DeviceClass c){}public void inquiryCompleted(int t){}
                public void servicesDiscovered(int i,ServiceRecord[] r){records.addAll(Arrays.asList(r));}public void serviceSearchCompleted(int i,int c){found.countDown();}
            });assertTrue(found.await(10,TimeUnit.SECONDS));assertEquals(1,records.size());assertEquals("Encrypted Bluetooth fixture",records.get(0).getAttributeValue(0x201).getValue());
            StreamConnection stream=(StreamConnection)Connector.open(streamUrl);
            try(Closeable closeStream=stream::close;DataInputStream in=stream.openDataInputStream();DataOutputStream out=stream.openDataOutputStream()){
                for(int i=0;i<64;i++){byte[] bytes=block(i);out.write(bytes);byte[] echoed=new byte[4096];in.readFully(echoed);assertArrayEquals(bytes,echoed);}
                L2CAPConnection channel=(L2CAPConnection)Connector.open(packetUrl+";ReceiveMTU=200;TransmitMTU=300");
                try(Closeable closeChannel=channel::close){
                    assertEquals(512,channel.getTransmitMTU());byte[] large=new byte[500];Arrays.fill(large,(byte)7);channel.send(large);channel.send(new byte[]{8,9});
                    byte[] one=new byte[1];assertEquals(1,channel.receive(one));assertEquals(42,one[0]);channel.send(new byte[0]);
                    assertEquals(1,channel.receive(one));assertEquals(43,one[0]);out.writeInt(0x13572468);out.flush();assertEquals(0x24681357,in.readInt());out.writeInt(0x55aa);out.flush();
                }
            }
            // The host is now waiting for logical EOF, not a peer disconnect. Give
            // its actor time to consume the queued close before disposing our peer.
            Thread.sleep(500);
        }
    }
    private static byte[] block(int i){byte[] bytes=new byte[4096];new Random(i+73).nextBytes(bytes);return bytes;}
}
