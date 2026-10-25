/* SPDX-License-Identifier: GPL-3.0-only */
package javax.bluetooth;

import org.junit.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.microedition.io.*;
import static org.junit.Assert.*;

/** Same wire/IO contracts run on the JVM and Android; pump is not a real network. */
public class PeerBluetoothBackendTest {
    static final String HOST="020000000001", GUEST="020000000002";
    static final UUID GAME=new UUID("abcdef123456", false);
    PeerBluetoothBackend a,b; VirtualBluetooth host,guest;
    ExecutorService io; ScheduledExecutorService wire; AtomicReference<Throwable> wireError;
    @Before public void setUp() {
        a=new PeerBluetoothBackend(new VirtualBluetooth.Peer(GUEST,"Guest"),true);
        b=new PeerBluetoothBackend(new VirtualBluetooth.Peer(HOST,"Host"),false);
        host=new VirtualBluetooth(new VirtualBluetooth.Peer(HOST,"Host"),a);
        guest=new VirtualBluetooth(new VirtualBluetooth.Peer(GUEST,"Guest"),b);
        io=Executors.newCachedThreadPool(); wire=Executors.newSingleThreadScheduledExecutor(); wireError=new AtomicReference<>();
        wire.scheduleWithFixedDelay(()->{
            try { transfer(a,b); transfer(b,a); }
            catch(Throwable e) { wireError.compareAndSet(null,e); a.close(); b.close(); }
        },0,1,TimeUnit.MILLISECONDS);
    }
    static void transfer(PeerBluetoothBackend from,PeerBluetoothBackend to)throws IOException {
        for(int i=0;i<16;i++){byte[] frame=from.pollOutbound();if(frame==null)return;to.receive(frame);}
    }
    @After public void tearDown()throws Exception {
        wire.shutdownNow(); a.close();b.close();host.close();guest.close();io.shutdownNow();
        assertTrue(wire.awaitTermination(5,TimeUnit.SECONDS));assertTrue(io.awaitTermination(5,TimeUnit.SECONDS));
    }
    StreamConnection[] streams()throws Exception {
        StreamConnectionNotifier server=(StreamConnectionNotifier)host.open("btspp://localhost:"+GAME+";name=Peer game",3);
        String url=new DiscoveryAgent(guest).selectService(GAME,0,false);assertNotNull(url);
        StreamConnection client=(StreamConnection)guest.open(url,3);
        return new StreamConnection[]{server.acceptAndOpen(),client};
    }
    @Test public void rfcommDiscoveryAndBinaryExchange()throws Exception {
        StreamConnection[] pair=streams();byte[] bytes=new byte[12000];new Random(7).nextBytes(bytes);
        pair[1].openOutputStream().write(bytes);byte[] result=new byte[bytes.length];pair[0].openDataInputStream().readFully(result);
        assertArrayEquals(bytes,result);assertNull(wireError.get());
    }
    @Test public void l2capNegotiatesMtuAndPreservesPacketsAfterTruncation()throws Exception {
        L2CAPConnectionNotifier server=(L2CAPConnectionNotifier)host.open("btl2cap://localhost:"+GAME+";ReceiveMTU=512;TransmitMTU=100",3);
        String url=new DiscoveryAgent(guest).selectService(GAME,0,false);
        L2CAPConnection client=(L2CAPConnection)guest.open(url+";ReceiveMTU=200;TransmitMTU=300",3);
        L2CAPConnection accepted=server.acceptAndOpen();assertEquals(512,client.getTransmitMTU());assertEquals(200,accepted.getTransmitMTU());
        client.send(new byte[500]);client.send(new byte[]{7,8});byte[] small=new byte[2];
        assertEquals(2,accepted.receive(small));assertEquals(2,accepted.receive(small));assertArrayEquals(new byte[]{7,8},small);
        accepted.send(new byte[0]);assertEquals(0,client.receive(new byte[4]));
    }
    @Test public void sdpUpdatesAreCopiedAndVisibleBeforeQueryReturns()throws Exception {
        StreamConnectionNotifier server=(StreamConnectionNotifier)host.open("btspp://localhost:"+GAME,3);
        ServiceRecord record=host.record(server);DataElement seq=new DataElement(DataElement.DATSEQ);
        seq.addElement(new DataElement(DataElement.STRING,"Custom game"));seq.addElement(new DataElement(DataElement.U_INT_16,new byte[16]));
        seq.addElement(new DataElement(true));seq.addElement(new DataElement(DataElement.INT_8,Long.MIN_VALUE));
        record.setAttributeValue(0x200,seq);host.update(record);seq.addElement(new DataElement(DataElement.NULL));
        Map<Integer,DataElement> copied=b.services(HOST).get(0).attributeSnapshot();
        assertEquals(4,copied.get(0x200).getSize());assertEquals("",b.services(HOST).get(0).name);
    }
    @Test public void discoverabilityDoesNotRevokeAdmittedOpenStreams()throws Exception {
        assertTrue(b.inquiryPeers().isEmpty());a.discoverable(DiscoveryAgent.GIAC);assertEquals(1,b.inquiryPeers().size());
        StreamConnection[] pair=streams();a.discoverable(0);assertTrue(b.inquiryPeers().isEmpty());assertEquals(1,b.peers().size());
        pair[0].openOutputStream().write(42);assertEquals(42,pair[1].openInputStream().read());
    }
    @Test public void creditBackpressureTransfersMoreThanWindowWithoutLoss()throws Exception {
        StreamConnection[] pair=streams();byte[] bytes=new byte[262144];new Random(42).nextBytes(bytes);
        OutputStream out=pair[1].openOutputStream();InputStream in=pair[0].openInputStream();
        Future<?> writing=io.submit(()->{out.write(bytes);return null;});Thread.sleep(100);assertFalse(writing.isDone());
        byte[] result=new byte[bytes.length];new DataInputStream(in).readFully(result);writing.get(5,TimeUnit.SECONDS);
        assertArrayEquals(bytes,result);assertNull(wireError.get());
    }
    @Test public void simultaneousOppositeDirectionStreamsStaySeparate()throws Exception {
        VirtualBluetooth.Listener one=a.listen(service(false,672,48)),two=b.listen(service(false,672,48));
        Future<VirtualBluetooth.Channel> first=io.submit(()->a.connect(GUEST,two.port(),false,672,48));
        Future<VirtualBluetooth.Channel> second=io.submit(()->b.connect(HOST,one.port(),false,672,48));
        VirtualBluetooth.Channel c=first.get(5,TimeUnit.SECONDS),d=second.get(5,TimeUnit.SECONDS),e=one.accept(),f=two.accept();
        c.output.write(11);d.output.write(22);assertEquals(22,e.input.read());assertEquals(11,f.input.read());
    }
    @Test public void closeUnblocksReadAndAccept()throws Exception {
        StreamConnection[] pair=streams();InputStream in=pair[0].openInputStream();
        VirtualBluetooth.Listener server=a.listen(service(true,672,48));
        Future<?> read=io.submit(()->{expectIo(in::read);return null;});
        Future<?> accept=io.submit(()->{expectIo(server::accept);return null;});
        Thread.sleep(30);a.close();read.get(5,TimeUnit.SECONDS);accept.get(5,TimeUnit.SECONDS);
    }
    @Test public void closeUnblocksCreditStalledWriter()throws Exception {
        StreamConnection[] pair=streams();OutputStream out=pair[1].openOutputStream();
        Future<?> writing=io.submit(()->{expectIo(()->out.write(new byte[WINDOW_PLUS]));return null;});
        Thread.sleep(100);assertFalse(writing.isDone());b.close();writing.get(5,TimeUnit.SECONDS);
    }
    static final int WINDOW_PLUS=PeerBluetoothBackend.WINDOW+8192;
    @Test public void serviceCloseRejectsNewConnectionsButKeepsAcceptedStream()throws Exception {
        VirtualBluetooth.Listener server=a.listen(service(false,672,48));
        VirtualBluetooth.Channel client=b.connect(HOST,server.port(),false,672,48),accepted=server.accept();server.close();
        assertTrue(b.services(HOST).isEmpty());expectIo(()->b.connect(HOST,server.port(),false,672,48));
        client.output.write(13);assertEquals(13,accepted.input.read());
    }
    @Test public void unsupportedMtuAndUnadmittedAddressDoNotOpenStreams()throws Exception {
        VirtualBluetooth.Listener server=a.listen(service(true,100,300));
        expectIo(()->b.connect(HOST,server.port(),true,200,48));
        expectIo(()->b.connect("020000000099",server.port(),true,672,48));assertNull(wireError.get());
    }
    @Test public void remoteCloseDrainsQueuedBytesThenEof()throws Exception {
        VirtualBluetooth.Listener server=a.listen(service(false,672,48));
        VirtualBluetooth.Channel client=b.connect(HOST,server.port(),false,672,48),accepted=server.accept();
        client.output.write(new byte[]{1,2,3});client.close();assertEquals(1,accepted.input.read());assertEquals(2,accepted.input.read());
        assertEquals(3,accepted.input.read());assertEquals(-1,accepted.input.read());expectIo(()->accepted.output.write(7));
        accepted.output.flush();accepted.close();
    }
    @Test public void notifierPreservesInitiallySuppliedSdp()throws Exception {
        Map<Integer,DataElement> attributes=new LinkedHashMap<>();attributes.put(0x200,new DataElement(DataElement.STRING,"Initial"));
        a.listen(new VirtualBluetooth.Service(GAME,"Initial service",false,0,672,48,VirtualServiceCodec.record(attributes)));
        assertEquals("Initial",b.services(HOST).get(0).attributeSnapshot().get(0x200).getValue());
    }
    @Test public void closeDuringConnectDoesNotLeaveWaitingThread()throws Exception {
        wire.shutdownNow();assertTrue(wire.awaitTermination(5,TimeUnit.SECONDS));
        Future<?> connecting=io.submit(()->{expectIo(()->b.connect(HOST,1,false,672,48));return null;});
        Thread.sleep(30);b.close();connecting.get(5,TimeUnit.SECONDS);
    }
    @Test public void fabricatedCreditAndTrailingFrameDataFailClosed()throws Exception {
        StreamConnection[] pair=streams();
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        out.writeShort(0xAB01);out.writeByte(10);out.writeInt(2);out.writeInt(1);
        expectIo(()->b.receive(bytes.toByteArray()));expectIo(()->pair[1].openOutputStream().write(1));
        PeerBluetoothBackend isolated=new PeerBluetoothBackend(new VirtualBluetooth.Peer(HOST,"Host"),false);
        expectIo(()->isolated.receive(new byte[]{(byte)0xAB,1,5,0,0,0,0,99}));expectIo(isolated::peers);
    }
    @Test public void malformedFrameClosesAndWakesWaiters()throws Exception {
        VirtualBluetooth.Listener server=a.listen(service(false,672,48));
        Future<?> accept=io.submit(()->{expectIo(server::accept);return null;});
        expectIo(()->a.receive(new byte[]{0,0,1,0,0,0,1}));accept.get(5,TimeUnit.SECONDS);expectIo(a::peers);
    }
    @Test public void excessiveNestedSdpFailsClosed()throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        out.writeShort(0xAB01);out.writeByte(3);out.writeInt(1);out.writeUTF("1");out.writeUTF("Bad");out.writeBoolean(false);
        out.writeShort(1);out.writeShort(672);out.writeShort(48);out.writeShort(1);out.writeShort(0x200);
        for(int i=0;i<10;i++){out.writeByte(DataElement.DATSEQ);out.writeShort(1);}out.writeByte(DataElement.NULL);
        expectIo(()->b.receive(bytes.toByteArray()));expectIo(b::peers);
    }
    @Test public void serviceLimitIsBoundedAndSlotsCanBeReused()throws Exception {
        List<VirtualBluetooth.Listener> all=new ArrayList<>();for(int i=0;i<16;i++)all.add(a.listen(service(false,672,48)));
        expectIo(()->a.listen(service(false,672,48)));all.get(0).close();assertEquals(1,a.listen(service(false,672,48)).port());
        assertEquals(16,b.services(HOST).size());
    }
    static VirtualBluetooth.Service service(boolean packets,int rx,int tx){return new VirtualBluetooth.Service(GAME,"Test game",packets,0,rx,tx);}
    interface Action {void run()throws Exception;}
    static void expectIo(Action action)throws Exception {try{action.run();fail("Expected IOException");}catch(IOException expected){}}
}
