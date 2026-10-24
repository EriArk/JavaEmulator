/* SPDX-License-Identifier: GPL-3.0-only */
package javax.bluetooth;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.microedition.io.Connection;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.StreamConnectionNotifier;

import static org.junit.Assert.*;

/** Same contract suite on host JVM and Android. Loopback fixture, NOT encrypted P2P. */
public class VirtualBluetoothTest {
    protected static final String A = "020000000001", B = "020000000002";
    protected static final UUID GAME = new UUID("123456789abcdef", false);
    protected VirtualBluetooth host, guest;
    protected FakeBackend left, right;
    private final ExecutorService tasks = Executors.newCachedThreadPool();

    @Before public void setup() {
        left = new FakeBackend(A); right = new FakeBackend(B);
        left.other = right; right.other = left;
        host = new VirtualBluetooth(new VirtualBluetooth.Peer(A, "Player 1"), left);
        guest = new VirtualBluetooth(new VirtualBluetooth.Peer(B, "Player 2"), right);
    }
    @After public void cleanup() throws Exception {
        if (VirtualBluetooth.current() == host) VirtualBluetooth.detach(host);
        if (VirtualBluetooth.current() == guest) VirtualBluetooth.detach(guest);
        host.close(); guest.close(); tasks.shutdownNow();
    }

    @Test public void localDeviceNeedsNoAndroidRadioAndClosedBindingFailsClosed() throws Exception {
        java.lang.reflect.Field physicalCache = LocalDevice.class.getDeclaredField("dev");
        physicalCache.setAccessible(true); Object previousPhysical = physicalCache.get(null);
        VirtualBluetooth.install(host);
        LocalDevice local = LocalDevice.getLocalDevice();
        assertSame(previousPhysical, physicalCache.get(null));
        assertEquals(A, local.getBluetoothAddress()); assertEquals("Player 1", local.getFriendlyName());
        assertTrue(local.setDiscoverable(DiscoveryAgent.GIAC)); assertEquals(DiscoveryAgent.GIAC, local.getDiscoverable());
        assertTrue(LocalDevice.isPowerOn()); assertNull(local.getDiscoveryAgent().retrieveDevices(DiscoveryAgent.PREKNOWN));
        host.close(); assertFalse(LocalDevice.isPowerOn());
        assertThrows(BluetoothStateException.class, LocalDevice::getLocalDevice);
        assertThrows(IOException.class, () -> new org.microemu.cldc.btspp.Connection().openConnection("btspp://"+B+":1", 3, false));
        assertThrows(IllegalStateException.class, () -> VirtualBluetooth.install(guest));
        VirtualBluetooth.detach(host); VirtualBluetooth.install(guest);
        assertNotSame(local, LocalDevice.getLocalDevice());
        assertThrows(BluetoothStateException.class, () -> local.getDiscoveryAgent().startInquiry(DiscoveryAgent.GIAC, new Events()));
        assertSame(previousPhysical, physicalCache.get(null));
    }

    @Test public void inquiryCachesAdmittedPeerAndServiceSearchReturnsUsableUrl() throws Exception {
        StreamConnectionNotifier server = (StreamConnectionNotifier) host.open("btspp://localhost:"+GAME+";name=Test game", 3);
        DiscoveryAgent agent = new DiscoveryAgent(guest);
        Events events = new Events(); assertTrue(agent.startInquiry(DiscoveryAgent.GIAC, events)); events.inquiryDone();
        assertEquals(DiscoveryListener.INQUIRY_COMPLETED, events.inquiryResult);
        assertEquals(A, events.peer.getBluetoothAddress()); assertEquals("Peer", events.peer.getFriendlyName(false));
        assertEquals(events.peer, agent.retrieveDevices(DiscoveryAgent.CACHED)[0]);
        agent.searchServices(null, new UUID[]{GAME, new UUID(0x1101)}, events.peer, events); events.searchDone();
        assertEquals(DiscoveryListener.SERVICE_SEARCH_COMPLETED, events.searchResult);
        assertEquals(1, events.records.length);
        assertEquals("Test game", events.records[0].getAttributeValue(0x100).getValue());
        StreamConnection client = (StreamConnection) guest.open(events.records[0].getConnectionURL(2, false), 3);
        StreamConnection accepted = server.acceptAndOpen();
        client.openDataOutputStream().writeUTF("Hello from JSR-82");
        assertEquals("Hello from JSR-82", accepted.openDataInputStream().readUTF());
        accepted.openDataOutputStream().writeInt(12345);
        assertEquals(12345, client.openDataInputStream().readInt());
        assertEquals(A, RemoteDevice.getRemoteDevice(client).getBluetoothAddress());
        assertEquals(B, RemoteDevice.getRemoteDevice(accepted).getBluetoothAddress());
    }

    @Test public void packetsNegotiateMtuAndPreserveEmptyAndTruncatedBoundaries() throws Exception {
        L2CAPConnectionNotifier server = (L2CAPConnectionNotifier) host.open("btl2cap://localhost:"+GAME+";ReceiveMTU=100;TransmitMTU=48", 3);
        String url = new DiscoveryAgent(guest).selectService(GAME, 0, false);
        L2CAPConnection client = (L2CAPConnection) guest.open(url+";ReceiveMTU=200", 3);
        L2CAPConnection accepted = server.acceptAndOpen();
        assertEquals(100, client.getTransmitMTU()); assertEquals(200, accepted.getTransmitMTU());
        client.send(new byte[]{1, 2, 3, 4}); client.send(new byte[0]); client.send(new byte[]{9});
        byte[] shortPacket = new byte[2]; assertEquals(2, accepted.receive(shortPacket));
        assertArrayEquals(new byte[]{1, 2}, shortPacket);
        assertEquals(0, accepted.receive(shortPacket)); assertEquals(1, accepted.receive(shortPacket)); assertEquals(9, shortPacket[0]);
        accepted.send(new byte[300]); assertEquals(200, client.receive(new byte[672]));
    }

    @Test public void closingNotifierWithdrawsServiceButNotAcceptedConnection() throws Exception {
        StreamConnectionNotifier server = (StreamConnectionNotifier)host.open("btspp://localhost:"+GAME, 3);
        StreamConnection client = (StreamConnection)guest.open(new DiscoveryAgent(guest).selectService(GAME, 0, false), 3);
        StreamConnection accepted = server.acceptAndOpen(); server.close();
        assertNull(new DiscoveryAgent(guest).selectService(GAME, 0, false));
        client.openOutputStream().write(7); assertEquals(7, accepted.openInputStream().read());
        assertThrows(IOException.class, server::acceptAndOpen);
    }

    @Test public void closeUnblocksAcceptAndReadAndRejectsOldSessionObjects() throws Exception {
        StreamConnectionNotifier server = (StreamConnectionNotifier)host.open("btspp://localhost:"+GAME, 3);
        StreamConnection client = (StreamConnection)guest.open(new DiscoveryAgent(guest).selectService(GAME, 0, false), 3);
        StreamConnection accepted = server.acceptAndOpen();
        InputStream input = accepted.openInputStream();
        Future<Boolean> read = tasks.submit(() -> { try { input.read(); return false; } catch (IOException expected) { return true; } });
        Future<Boolean> accept = tasks.submit(() -> { try { server.acceptAndOpen(); return false; } catch (IOException expected) { return true; } });
        host.close(); assertTrue(read.get(3, TimeUnit.SECONDS)); assertTrue(accept.get(3, TimeUnit.SECONDS));
        assertThrows(IOException.class, () -> host.remote(B).getFriendlyName(false));
        assertThrows(IOException.class, () -> RemoteDevice.getRemoteDevice(accepted));
        assertThrows(BluetoothStateException.class, () -> new DiscoveryAgent(host).startInquiry(DiscoveryAgent.GIAC, new Events()));
        assertNotNull(client);
    }

    @Test public void ioModesAndSingleOpenAreEnforced() throws Exception {
        StreamConnectionNotifier server = (StreamConnectionNotifier)host.open("btspp://localhost:"+GAME, 1);
        StreamConnection client = (StreamConnection)guest.open(new DiscoveryAgent(guest).selectService(GAME, 0, false), 2);
        StreamConnection accepted = server.acceptAndOpen();
        assertThrows(IOException.class, client::openInputStream);
        assertThrows(IOException.class, accepted::openOutputStream);
        client.openOutputStream().write(3);
        assertThrows(IOException.class, client::openOutputStream);
        InputStream in = accepted.openInputStream(); assertEquals(3, in.read()); in.close();
        assertThrows(IOException.class, in::read);
    }

    @Test public void invalidUrlsAreRejectedBeforeBackendMutation() throws Exception {
        for (String tail : Arrays.asList(";encrypt=true", ";ReceiveMTU=47", ";ReceiveMTU=673",
                ";name=a;name=b", ";authenticate=maybe", ";unknown=true", ";ReceiveMTU=-1")) {
            assertThrows(IllegalArgumentException.class, () -> host.open("btl2cap://localhost:"+GAME+tail, 3));
        }
        assertThrows(IllegalArgumentException.class, () -> host.open("btspp://localhost:"+GAME+";ReceiveMTU=100", 3));
        assertThrows(IllegalArgumentException.class, () -> host.open("btspp://localhost:", 3));
        assertEquals(0, left.servers.size());
    }

    @Test public void wrongPeerAndForeignSessionAreRejected() throws Exception {
        assertThrows(IOException.class, () -> guest.open("btspp://020000000099:1", 3));
        assertThrows(IllegalArgumentException.class, () -> new DiscoveryAgent(guest).searchServices(null, new UUID[]{GAME}, host.remote(B), new Events()));
        assertNotEquals(host.remote(B), guest.remote(B));
        assertEquals(host.remote(B), host.remote(B));
        assertThrows(IllegalArgumentException.class, () -> host.record(guest.open("btspp://localhost:"+GAME, 3)));
    }

    @Test public void cancelInquiryDoesNotCompleteTwiceOrLeakIntoRestartWithSameListener() throws Exception {
        left.entered = new CountDownLatch(1); left.release = new CountDownLatch(1);
        DiscoveryAgent agent = new DiscoveryAgent(host); Events events = new Events();
        agent.startInquiry(DiscoveryAgent.GIAC, events); assertTrue(left.entered.await(3, TimeUnit.SECONDS));
        assertFalse(agent.cancelInquiry(new Events())); assertTrue(agent.cancelInquiry(events));
        assertFalse(agent.cancelInquiry(events)); assertTrue(agent.startInquiry(DiscoveryAgent.GIAC, events));
        left.release.countDown();
        assertTrue(events.twoInquiries.await(3, TimeUnit.SECONDS));
        assertEquals(2, events.inquiries.get()); assertEquals(1, events.devices.get());
    }

    @Test public void cancelSearchHasSingleTerminalCallbackAndNoLateRecords() throws Exception {
        host.open("btspp://localhost:"+GAME, 3);
        right.entered = new CountDownLatch(1); right.release = new CountDownLatch(1);
        DiscoveryAgent agent = new DiscoveryAgent(guest); Events events = new Events();
        int id = agent.searchServices(null, new UUID[]{GAME}, guest.remote(A), events);
        assertTrue(right.entered.await(3, TimeUnit.SECONDS)); assertTrue(agent.cancelServiceSearch(id));
        assertFalse(agent.cancelServiceSearch(id)); right.release.countDown(); events.searchDone();
        // A following inquiry fences completion of the previous queued task.
        Events next = new Events(); agent.startInquiry(DiscoveryAgent.GIAC, next); next.inquiryDone();
        assertEquals(1, events.searches.get()); assertNull(events.records);
        assertEquals(DiscoveryListener.SERVICE_SEARCH_TERMINATED, events.searchResult);
    }

    @Test public void blockingGameCallbackCannotPreventSessionClose() throws Exception {
        CountDownLatch inside = new CountDownLatch(1), release = new CountDownLatch(1);
        Events events = new Events() {
            @Override public void deviceDiscovered(RemoteDevice peer, DeviceClass type) {
                inside.countDown();
                try { release.await(3, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
            }
        };
        new DiscoveryAgent(host).startInquiry(DiscoveryAgent.GIAC, events);
        assertTrue(inside.await(3, TimeUnit.SECONDS));
        Future<?> close = tasks.submit(() -> { host.close(); return null; });
        try { close.get(1, TimeUnit.SECONDS); } finally { release.countDown(); }
        assertFalse(host.powered());
    }

    @Test public void searchRequiresEveryUuidAndReturnsNoRecordForUnrelatedService() throws Exception {
        host.open("btspp://localhost:"+GAME, 3);
        Events events = new Events();
        new DiscoveryAgent(guest).searchServices(null, new UUID[]{GAME, new UUID(0x9999)}, guest.remote(A), events);
        events.searchDone(); assertNull(events.records); assertEquals(DiscoveryListener.SERVICE_SEARCH_NO_RECORDS, events.searchResult);
    }

    @Test public void customServiceAttributesAreCopiedUpdatedAndSearched() throws Exception {
        Connection server = host.open("btspp://localhost:"+GAME, 3);
        ServiceRecord record = host.record(server);
        DataElement sequence = new DataElement(DataElement.DATSEQ);
        UUID extra = new UUID(0x7788);
        sequence.addElement(new DataElement(DataElement.UUID, extra));
        sequence.addElement(new DataElement(DataElement.STRING, "Game protocol 2"));
        record.setAttributeValue(0x200, sequence);
        record.setAttributeValue(0x100, new DataElement(DataElement.STRING, "Match name"));
        host.update(record);
        // The provider owns a snapshot, not a mutable reference into game code.
        ((DataElement)record.getAttributeValue(0x200)).addElement(new DataElement(true));
        Events events = new Events();
        new DiscoveryAgent(guest).searchServices(new int[]{0x200}, new UUID[]{GAME, extra}, guest.remote(A), events);
        events.searchDone(); assertEquals(1, events.records.length);
        assertEquals("Match name", events.records[0].getAttributeValue(0x100).getValue());
        assertEquals(2, events.records[0].getAttributeValue(0x200).getSize());
        assertTrue(record.setAttributeValue(0x200, null)); host.update(record);
        assertNull(new DiscoveryAgent(guest).selectService(extra, 0, false));
    }

    @Test public void cyclicOrOversizedMetadataCannotReachProvider() throws Exception {
        Connection server = host.open("btspp://localhost:"+GAME, 3);
        ServiceRecord record = host.record(server);
        DataElement cycle = new DataElement(DataElement.DATSEQ); cycle.addElement(cycle);
        assertThrows(IllegalArgumentException.class, () -> record.setAttributeValue(0x200, cycle));
        char[] big = new char[4097]; Arrays.fill(big, 'a');
        assertThrows(IllegalArgumentException.class, () -> record.setAttributeValue(0x200, new DataElement(DataElement.STRING, new String(big))));
        record.setAttributeValue(0x200, new DataElement(DataElement.DATSEQ));
        DataElement mutable = record.getAttributeValue(0x200); mutable.addElement(mutable);
        assertThrows(ServiceRegistrationException.class, () -> host.update(record));
        assertThrows(IllegalArgumentException.class, () -> record.setAttributeValue(-1, new DataElement(true)));
    }

    @Test public void mtuMismatchLeavesNotifierUsableForNextClient() throws Exception {
        L2CAPConnectionNotifier server = (L2CAPConnectionNotifier)host.open("btl2cap://localhost:"+GAME+";ReceiveMTU=64", 3);
        String url = new DiscoveryAgent(guest).selectService(GAME, 0, false);
        assertThrows(IOException.class, () -> guest.open(url+";TransmitMTU=100", 3));
        L2CAPConnection client = (L2CAPConnection)guest.open(url, 3);
        L2CAPConnection accepted = server.acceptAndOpen(); client.send(new byte[]{5});
        assertEquals(1, accepted.receive(new byte[1]));
    }

    @Test public void channelLimitAndClosedNotifierDoNotPoisonNextSession() throws Exception {
        List<Connection> servers = new ArrayList<>();
        for (int i = 0; i < 32; i++) servers.add(host.open("btl2cap://localhost:"+(i+1), 3));
        assertThrows(IOException.class, () -> host.open("btl2cap://localhost:999", 3));
        servers.get(0).close(); assertNotNull(host.open("btl2cap://localhost:999", 3));
        assertEquals(32, left.servers.size());
    }

    @Test public void connectionCloseKeepsExistingStreamsUntilTheirOwnClose() throws Exception {
        StreamConnectionNotifier server = (StreamConnectionNotifier)host.open("btspp://localhost:"+GAME, 3);
        StreamConnection client = (StreamConnection)guest.open(new DiscoveryAgent(guest).selectService(GAME, 0, false), 3);
        StreamConnection accepted = server.acceptAndOpen();
        InputStream input = accepted.openInputStream();
        java.io.OutputStream output = client.openOutputStream();
        client.close(); accepted.close();
        assertThrows(IOException.class, client::openInputStream);
        output.write(23); output.flush(); assertEquals(23, input.read());
        output.close(); assertEquals(-1, input.read()); input.close();
        assertThrows(IOException.class, () -> output.write(24));
    }

    @Test public void sessionCloseStillInterruptsStreamAfterConnectionWasClosed() throws Exception {
        StreamConnectionNotifier server = (StreamConnectionNotifier)host.open("btspp://localhost:"+GAME, 3);
        StreamConnection client = (StreamConnection)guest.open(new DiscoveryAgent(guest).selectService(GAME, 0, false), 3);
        StreamConnection accepted = server.acceptAndOpen();
        InputStream input = accepted.openInputStream(); accepted.close();
        Future<Boolean> waiting = tasks.submit(() -> { try { input.read(); return false; } catch (IOException expected) { return true; } });
        host.close(); assertTrue(waiting.get(3, TimeUnit.SECONDS)); assertNotNull(client);
    }

    protected static class Events implements DiscoveryListener {
        final CountDownLatch inquiry = new CountDownLatch(1), search = new CountDownLatch(1), twoInquiries = new CountDownLatch(2);
        final AtomicInteger inquiries = new AtomicInteger(), searches = new AtomicInteger(), devices = new AtomicInteger();
        volatile int inquiryResult, searchResult;
        volatile RemoteDevice peer; volatile ServiceRecord[] records;
        @Override public void deviceDiscovered(RemoteDevice p, DeviceClass type) { peer = p; devices.incrementAndGet(); }
        @Override public void inquiryCompleted(int result) { inquiryResult = result; inquiries.incrementAndGet(); inquiry.countDown(); twoInquiries.countDown(); }
        @Override public void servicesDiscovered(int id, ServiceRecord[] found) { records = found; }
        @Override public void serviceSearchCompleted(int id, int result) { searchResult = result; searches.incrementAndGet(); search.countDown(); }
        void inquiryDone() throws InterruptedException { assertTrue(inquiry.await(5, TimeUnit.SECONDS)); }
        void searchDone() throws InterruptedException { assertTrue(search.await(5, TimeUnit.SECONDS)); }
    }

    protected static final class FakeBackend implements VirtualBluetooth.Backend {
        final String address; FakeBackend other; boolean closed;
        final Map<Integer, Server> servers = new LinkedHashMap<>();
        final List<Closeable> sockets = new ArrayList<>();
        volatile CountDownLatch entered, release;
        FakeBackend(String address) { this.address = address; }
        @Override public List<VirtualBluetooth.Peer> peers() throws IOException {
            if (entered != null) {
                entered.countDown();
                try { if (!release.await(5, TimeUnit.SECONDS)) throw new IOException("Fixture gate timed out"); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
            }
            synchronized (this) { if (closed) throw new IOException("Closed"); }
            return Collections.singletonList(new VirtualBluetooth.Peer(other.address, "Peer"));
        }
        @Override public List<VirtualBluetooth.Service> services(String address) {
            List<VirtualBluetooth.Service> result = new ArrayList<>();
            synchronized (other) {
                for (Server server : other.servers.values()) {
                    VirtualBluetooth.Service s = server.service;
                    result.add(new VirtualBluetooth.Service(s.uuid, s.name, s.packets, server.port, s.receiveMtu, s.minimumTransmitMtu, server.record));
                }
            }
            return result;
        }
        @Override public void discoverable(int mode) { }
        @Override public synchronized VirtualBluetooth.Listener listen(VirtualBluetooth.Service service) throws IOException {
            if (closed) throw new IOException("Closed");
            int port = service.packets ? 0x1001 : 1;
            while (servers.containsKey(port)) port++;
            Server server = new Server(service, port); servers.put(port, server); return server;
        }
        @Override public VirtualBluetooth.Channel connect(String address, int port, boolean packets, int rx, int tx) throws IOException {
            Server server;
            synchronized (other) { server = other.servers.get(port); }
            if (server == null || server.service.packets != packets) throw new IOException("No service");
            if (tx > server.service.receiveMtu || server.service.minimumTransmitMtu > rx) throw new IOException("MTU mismatch");
            try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
                Socket client = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
                Socket accepted = listener.accept(); client.setSoTimeout(5000); accepted.setSoTimeout(5000);
                synchronized (this) { sockets.add(client); }
                synchronized (other) { other.sockets.add(accepted); }
                server.enqueue(new VirtualBluetooth.Channel(this.address, accepted.getInputStream(), accepted.getOutputStream(), accepted, server.service.receiveMtu, rx));
                return new VirtualBluetooth.Channel(address, client.getInputStream(), client.getOutputStream(), client, rx, server.service.receiveMtu);
            }
        }
        @Override public void close() throws IOException {
            List<Server> pending; List<Closeable> open;
            synchronized (this) { closed = true; pending = new ArrayList<>(servers.values()); open = new ArrayList<>(sockets); }
            if (release != null) release.countDown();
            for (Server s : pending) s.close(); for (Closeable c : open) c.close();
        }
        final class Server implements VirtualBluetooth.Listener {
            final VirtualBluetooth.Service service; final int port;
            volatile ServiceRecord record;
            final List<VirtualBluetooth.Channel> queue = new ArrayList<>(); boolean ended;
            Server(VirtualBluetooth.Service service, int port) { this.service = service; this.port = port; }
            @Override public int port() { return port; }
            @Override public void update(ServiceRecord record) { this.record = record; }
            synchronized void enqueue(VirtualBluetooth.Channel c) throws IOException {
                if (ended) { c.close(); throw new IOException("Closed"); } queue.add(c); notifyAll();
            }
            @Override public synchronized VirtualBluetooth.Channel accept() throws IOException {
                while (!ended && queue.isEmpty()) try { wait(); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
                if (ended) throw new IOException("Closed"); return queue.remove(0);
            }
            @Override public void close() throws IOException {
                synchronized (FakeBackend.this) { servers.remove(port); }
                synchronized (this) { ended = true; for (VirtualBluetooth.Channel c : queue) c.close(); queue.clear(); notifyAll(); }
            }
        }
    }
}
