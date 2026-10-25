/* SPDX-License-Identifier: GPL-3.0-only */
package javax.bluetooth;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.microedition.io.Connection;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.StreamConnectionNotifier;

import ru.playsoftware.j2meloader.multiplayer.FramedL2capConnection;

/** Internal JSR-82 backend. Not an Internet transport or an admission mechanism.
 * The runtime may install this only for an authorized launch with an encrypted,
 * reliable peer transport. Production capabilities remain disabled until that exists.
 */
public final class VirtualBluetooth implements Closeable {
    /** One instance per launch attempt; close must interrupt ALL outstanding operations.
     * Return admitted peers only. SDP is metadata, never a source of peer authorization.
     * All results must belong to this instance, not a later/reconnected session.
     */
    public interface Backend extends Closeable {
        List<Peer> peers() throws IOException;
        default List<Peer> inquiryPeers() throws IOException { return peers(); }
        List<Service> services(String address) throws IOException;
        void discoverable(int mode) throws IOException;
        Listener listen(Service service) throws IOException;
        Channel connect(String address, int port, boolean packets, int receiveMtu,
                        int minimumTransmitMtu) throws IOException;
    }

    public interface Listener extends Closeable {
        int port();
        Channel accept() throws IOException;
        void update(ServiceRecord record) throws IOException;
    }

    public static final class Peer {
        public final String address, name;
        public Peer(String address, String name) {
            this.address = address(address);
            if (name == null || name.length() > 248) throw new IllegalArgumentException("Invalid peer name");
            this.name = name;
        }
    }

    public static final class Service {
        public final UUID uuid;
        public final String name;
        public final boolean packets;
        public final int port, receiveMtu, minimumTransmitMtu;
        private final Map<Integer, DataElement> attributes;
        public Service(UUID uuid, String name, boolean packets, int port, int receiveMtu,
                       int minimumTransmitMtu) {
            this(uuid, name, packets, port, receiveMtu, minimumTransmitMtu, null);
        }
        public Service(UUID uuid, String name, boolean packets, int port, int receiveMtu,
                       int minimumTransmitMtu, ServiceRecord record) {
            this.uuid = new UUID(Objects.requireNonNull(uuid).toString(), false);
            if (name == null || name.length() > 248 || port < 0 || port > 65535)
                throw new IllegalArgumentException("Invalid service");
            mtu(receiveMtu); mtu(minimumTransmitMtu);
            this.name = name; this.packets = packets; this.port = port;
            this.receiveMtu = receiveMtu; this.minimumTransmitMtu = minimumTransmitMtu;
            attributes = record == null ? new LinkedHashMap<>() : VirtualServiceAttributes.snapshot(record);
        }
        Map<Integer, DataElement> attributeSnapshot() {
            Map<Integer, DataElement> copy = new LinkedHashMap<>();
            for (Map.Entry<Integer, DataElement> e : attributes.entrySet()) copy.put(e.getKey(), VirtualServiceAttributes.copy(e.getValue()));
            return copy;
        }
    }

    /** Dedicated ordered stream; packet services use FramedL2capConnection on BOTH ends. */
    public static final class Channel implements Closeable {
        public final String peer;
        public final InputStream input;
        public final OutputStream output;
        public final int receiveMtu, transmitMtu;
        private final Closeable owner;
        public Channel(String peer, InputStream input, OutputStream output, Closeable owner,
                       int receiveMtu, int transmitMtu) {
            this.peer = address(peer); this.input = Objects.requireNonNull(input);
            this.output = Objects.requireNonNull(output); this.owner = Objects.requireNonNull(owner);
            mtu(receiveMtu); mtu(transmitMtu);
            this.receiveMtu = receiveMtu; this.transmitMtu = transmitMtu;
        }
        @Override public void close() throws IOException { owner.close(); }
    }

    private static volatile VirtualBluetooth installed;
    private final Backend backend;
    private final Peer local;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Virtual Bluetooth discovery"); t.setDaemon(true); return t;
    });
    private final Set<Connection> connections = new HashSet<>();
    private final Map<Integer, Search> searches = new LinkedHashMap<>();
    private final Map<String, RemoteDevice> cache = new LinkedHashMap<>();
    private volatile boolean closed;
    private int discoverable, nextSearch = 1;
    private Inquiry inquiry;
    private static final class Inquiry {
        final DiscoveryListener listener;
        boolean done;
        Inquiry(DiscoveryListener listener) { this.listener = listener; }
    }

    public VirtualBluetooth(Peer local, Backend backend) {
        this.local = Objects.requireNonNull(local); this.backend = Objects.requireNonNull(backend);
    }

    /** Called by runtime owner before MIDlet starts, never by directory discovery. */
    public static synchronized void install(VirtualBluetooth session) {
        Objects.requireNonNull(session);
        if (installed != null || session.closed) throw new IllegalStateException("Bluetooth session already bound or closed");
        installed = session;
    }

    /** Only after every old MIDlet thread has stopped; close alone deliberately fails closed. */
    public static synchronized void detach(VirtualBluetooth session) throws IOException {
        if (installed != session) throw new IllegalStateException("Different Bluetooth session");
        session.close(); installed = null;
    }

    public static VirtualBluetooth current() { return installed; }
    String localAddress() { return local.address; }
    String localName() { return local.name; }
    boolean powered() { return !closed; }
    synchronized int discoverable() { return discoverable; }
    boolean discoverable(int mode) throws BluetoothStateException {
        try { check(); backend.discoverable(mode); synchronized (this) { check(); discoverable = mode; } return true; }
        catch (IOException e) { throw state(e); }
    }

    RemoteDevice remote(String value) {
        return new RemoteDevice(this, address(value));
    }
    String peerName(String value) throws IOException {
        for (Peer peer : peers()) if (peer.address.equals(value)) return peer.name;
        throw new IOException("Peer is not in this Bluetooth session");
    }
    private List<Peer> peers() throws IOException {
        return peers(false);
    }
    private List<Peer> peers(boolean inquiry) throws IOException {
        check(); List<Peer> peers = inquiry ? backend.inquiryPeers() : backend.peers(); check();
        if (peers == null || peers.size() > 31) throw new IOException("Invalid peer list");
        Set<String> seen = new HashSet<>();
        for (Peer p : peers) if (p == null || p.address.equals(local.address) || !seen.add(p.address))
            throw new IOException("Invalid peer identity");
        return peers;
    }
    synchronized RemoteDevice[] cached(int option) {
        if (option != DiscoveryAgent.CACHED && option != DiscoveryAgent.PREKNOWN) throw new IllegalArgumentException();
        // Admission is not Bluetooth pairing. PREKNOWN must not fabricate bonded devices.
        return option == DiscoveryAgent.PREKNOWN || cache.isEmpty() ? null : cache.values().toArray(new RemoteDevice[0]);
    }
    synchronized boolean inquire(DiscoveryListener listener) throws BluetoothStateException {
        Objects.requireNonNull(listener);
        if (closed) throw new BluetoothStateException("Bluetooth session closed");
        if (inquiry != null) return false;
        Inquiry operation = new Inquiry(listener);
        inquiry = operation;
        worker.execute(() -> {
            int result = DiscoveryListener.INQUIRY_COMPLETED;
            try {
                for (Peer peer : peers(true)) {
                    synchronized (operation) {
                        if (closed || operation.done) return;
                        RemoteDevice device = remote(peer.address);
                        synchronized (this) { if (closed) return; cache.put(peer.address, device); }
                        listener.deviceDiscovered(device, new DeviceClass(0x200204));
                    }
                }
            } catch (IOException | RuntimeException e) { result = DiscoveryListener.INQUIRY_ERROR; }
            synchronized (operation) {
                if (closed || operation.done) return;
                operation.done = true;
                synchronized (this) { if (inquiry == operation) inquiry = null; }
                listener.inquiryCompleted(result);
            }
        });
        return true;
    }
    boolean cancel(DiscoveryListener listener) {
        Objects.requireNonNull(listener);
        Inquiry operation;
        synchronized (this) {
            operation = inquiry;
            if (operation == null || operation.listener != listener) return false;
            inquiry = null;
        }
        synchronized (operation) {
            if (closed || operation.done) return false;
            operation.done = true;
            listener.inquiryCompleted(DiscoveryListener.INQUIRY_TERMINATED); return true;
        }
    }

    private static final class Search {
        final int id; final DiscoveryListener listener;
        boolean done;
        Search(int id, DiscoveryListener listener) { this.id = id; this.listener = listener; }
    }
    synchronized int search(UUID[] uuids, RemoteDevice peer, DiscoveryListener listener) throws BluetoothStateException {
        if (closed) throw new BluetoothStateException("Bluetooth session closed");
        if (peer.virtual != this) throw new IllegalArgumentException("Peer belongs to another Bluetooth backend");
        if (!searches.isEmpty()) throw new BluetoothStateException("Service search already active");
        UUID[] requested = new UUID[uuids.length];
        for (int i = 0; i < uuids.length; i++) requested[i] = new UUID(uuids[i].toString(), false);
        Search search = new Search(nextSearch++, listener); searches.put(search.id, search);
        worker.execute(() -> {
            int result = DiscoveryListener.SERVICE_SEARCH_COMPLETED;
            ServiceRecord[] found = null;
            try { found = records(peer, requested); if (found.length == 0) result = DiscoveryListener.SERVICE_SEARCH_NO_RECORDS; }
            catch (IOException | RuntimeException e) { result = DiscoveryListener.SERVICE_SEARCH_ERROR; }
            synchronized (search) {
                if (closed || search.done) return;
                if (found != null && found.length > 0) {
                    try { listener.servicesDiscovered(search.id, found); }
                    catch (RuntimeException e) { result = DiscoveryListener.SERVICE_SEARCH_ERROR; }
                }
                if (!closed && !search.done) {
                    search.done = true;
                    synchronized (this) { searches.remove(search.id); }
                    listener.serviceSearchCompleted(search.id, result);
                }
            }
        });
        return search.id;
    }
    boolean cancel(int id) {
        Search s;
        synchronized (this) { s = searches.remove(id); }
        if (s == null) return false;
        synchronized (s) {
            if (closed || s.done) return false;
            s.done = true;
            s.listener.serviceSearchCompleted(id, DiscoveryListener.SERVICE_SEARCH_TERMINATED); return true;
        }
    }
    private ServiceRecord[] records(RemoteDevice peer, UUID[] wanted) throws IOException {
        peerName(peer.getBluetoothAddress());
        List<Service> services = backend.services(peer.getBluetoothAddress()); check();
        if (services == null || services.size() > 32) throw new IOException("Invalid service list");
        List<ServiceRecord> records = new ArrayList<>();
        for (Service s : services) {
            Record record = new Record(peer, s, s.port);
            boolean matches = true;
            for (UUID id : wanted) {
                boolean present = false;
                for (int attr : record.getAttributeIDs()) if (VirtualServiceAttributes.contains(record.getAttributeValue(attr), id)) present = true;
                if (!present) matches = false;
            }
            if (matches) records.add(record);
        }
        return records.toArray(new ServiceRecord[0]);
    }
    String select(UUID uuid, int security, boolean master) throws BluetoothStateException {
        Objects.requireNonNull(uuid); security(security);
        try {
            for (Peer p : peers()) {
                ServiceRecord[] records = records(remote(p.address), new UUID[]{uuid});
                if (records.length > 0) return records[0].getConnectionURL(security, master);
            }
            return null;
        } catch (IOException e) { throw state(e); }
    }

    public Connection open(String url, int mode) throws IOException {
        check();
        if (mode < Connector.READ || mode > Connector.READ_WRITE) throw new IllegalArgumentException("Invalid IO mode");
        Url parsed = new Url(url);
        if (parsed.server) {
            Service s = new Service(new UUID(parsed.target, false), parsed.name, parsed.packets,
                    0, parsed.rx, parsed.tx);
            Listener listener = backend.listen(s);
            try {
                Notifier n = parsed.packets ? new PacketNotifier(listener, s, mode) : new StreamNotifier(listener, s, mode);
                return track(n);
            } catch (IOException | RuntimeException e) { listener.close(); throw e; }
        }
        String peer = address(parsed.host); peerName(peer);
        int port;
        try { port = Integer.parseInt(parsed.target, parsed.packets ? 16 : 10); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid service port", e); }
        if (port < 1 || port > (parsed.packets ? 65535 : 30)) throw new IllegalArgumentException("Invalid service port");
        Channel channel = backend.connect(peer, port, parsed.packets, parsed.rx, parsed.tx);
        if (!peer.equals(channel.peer)) { channel.close(); throw new IOException("Wrong channel peer"); }
        return connected(channel, parsed.packets, mode, parsed.rx, parsed.tx);
    }
    private Connection connected(Channel c, boolean packets, int mode, int rx, int tx) throws IOException {
        try {
            check(); peerName(c.peer);
            if (packets && (c.receiveMtu > rx || c.transmitMtu < tx)) throw new IOException("Invalid MTU negotiation");
            return track(packets ? new Packet(c, mode) : new Stream(c, mode));
        } catch (IOException | RuntimeException e) { c.close(); throw e; }
    }
    private synchronized <T extends Connection> T track(T c) throws IOException {
        if (closed || connections.size() >= 32) { c.close(); throw new IOException("Bluetooth session closed or channel limit reached"); }
        connections.add(c); return c;
    }
    private synchronized void forget(Connection c) { connections.remove(c); }
    void check() throws IOException { if (closed) throw new IOException("Bluetooth session closed"); }
    synchronized boolean connected(String address) {
        if (closed) return false;
        for (Connection c : connections) if (c instanceof Connected && !((Connected)c).ended
                && ((Connected)c).channel.peer.equals(address)) return true;
        return false;
    }
    boolean owns(Connection connection, String address) throws IOException {
        check();
        if (!(connection instanceof Connected)) throw new IllegalArgumentException("Not a virtual Bluetooth channel");
        Connected c = (Connected) connection;
        if (c.session() != this || !address.equals(c.channel.peer)) throw new IllegalArgumentException("Different Bluetooth peer");
        if (c.ended) throw new IOException("Bluetooth channel closed");
        return true;
    }

    abstract class Notifier implements Connection {
        final Listener listener; final Record record; final Service service; final int mode;
        volatile boolean ended;
        Notifier(Listener listener, Service service, int mode) {
            this.listener = listener; this.service = service; this.mode = mode;
            record = new Record(null, service, listener.port());
        }
        Connection accept() throws IOException {
            check(); if (ended) throw new IOException("Service closed");
            try { listener.update(record.snapshot()); }
            catch (IllegalArgumentException e) { throw new ServiceRegistrationException("Invalid service attributes"); }
            Channel c = listener.accept();
            if (ended) { c.close(); throw new IOException("Service closed"); }
            return connected(c, service.packets, mode, service.receiveMtu, service.minimumTransmitMtu);
        }
        @Override public void close() throws IOException { ended = true; forget(this); listener.close(); }
    }
    final class StreamNotifier extends Notifier implements StreamConnectionNotifier {
        StreamNotifier(Listener l, Service s, int m) { super(l, s, m); }
        @Override public StreamConnection acceptAndOpen() throws IOException { return (StreamConnection) accept(); }
    }
    final class PacketNotifier extends Notifier implements L2CAPConnectionNotifier {
        PacketNotifier(Listener l, Service s, int m) { super(l, s, m); }
        @Override public L2CAPConnection acceptAndOpen() throws IOException { return (L2CAPConnection) accept(); }
    }
    abstract class Connected implements Connection {
        final Channel channel; final int mode; volatile boolean ended;
        Connected(Channel c, int mode) { channel = c; this.mode = mode; }
        VirtualBluetooth session() { return VirtualBluetooth.this; }
        void usable(boolean writing) throws IOException {
            check();
            if (ended || (writing ? mode == Connector.READ : mode == Connector.WRITE)) throw new IOException("Channel closed or wrong IO mode");
        }
        @Override public void close() throws IOException { ended = true; forget(this); channel.close(); }
        void abort() throws IOException { ended = true; forget(this); channel.close(); }
    }
    final class Stream extends Connected implements StreamConnection {
        private boolean inputOpened, outputOpened;
        private int streams;
        Stream(Channel c, int mode) { super(c, mode); }
        @Override public synchronized InputStream openInputStream() throws IOException {
            usable(false); if (inputOpened) throw new IOException("Input already opened"); inputOpened = true;
            streams++;
            return new FilterInputStream(channel.input) {
                volatile boolean streamClosed;
                void valid() throws IOException { check(); if (streamClosed) throw new IOException("Input closed"); }
                @Override public int read() throws IOException { valid(); return in.read(); }
                @Override public int read(byte[] b, int o, int n) throws IOException { valid(); return in.read(b, o, n); }
                @Override public int available() throws IOException { valid(); return in.available(); }
                @Override public long skip(long n) throws IOException { valid(); return in.skip(n); }
                @Override public synchronized void reset() throws IOException { valid(); in.reset(); }
                @Override public synchronized void close() throws IOException {
                    if (!streamClosed) { streamClosed = true; releaseStream(); }
                }
            };
        }
        @Override public synchronized OutputStream openOutputStream() throws IOException {
            usable(true); if (outputOpened) throw new IOException("Output already opened"); outputOpened = true;
            streams++;
            return new FilterOutputStream(channel.output) {
                volatile boolean streamClosed;
                void valid() throws IOException { check(); if (streamClosed) throw new IOException("Output closed"); }
                @Override public void write(int v) throws IOException { valid(); out.write(v); }
                @Override public void write(byte[] b, int o, int n) throws IOException { valid(); out.write(b, o, n); }
                @Override public void flush() throws IOException { valid(); out.flush(); }
                @Override public synchronized void close() throws IOException {
                    if (!streamClosed) {
                        try { flush(); } finally { streamClosed = true; releaseStream(); }
                    }
                }
            };
        }
        @Override public void close() throws IOException {
            boolean release;
            synchronized (this) { if (ended) return; ended = true; release = streams == 0; }
            if (release) abort();
        }
        private void releaseStream() throws IOException {
            boolean release;
            synchronized (this) { streams--; release = ended && streams == 0; }
            if (release) abort();
        }
        @Override public DataInputStream openDataInputStream() throws IOException { return new DataInputStream(openInputStream()); }
        @Override public DataOutputStream openDataOutputStream() throws IOException { return new DataOutputStream(openOutputStream()); }
    }
    final class Packet extends Connected implements L2CAPConnection {
        final FramedL2capConnection framed;
        Packet(Channel c, int mode) { super(c, mode); framed = new FramedL2capConnection(c.input, c.output, c, c.receiveMtu, c.transmitMtu); }
        @Override public int getReceiveMTU() throws IOException { check(); return framed.getReceiveMTU(); }
        @Override public int getTransmitMTU() throws IOException { check(); return framed.getTransmitMTU(); }
        @Override public void send(byte[] b) throws IOException { usable(true); framed.send(b); }
        @Override public int receive(byte[] b) throws IOException { usable(false); return framed.receive(b); }
        @Override public boolean ready() throws IOException { usable(false); return framed.ready(); }
        @Override public void close() throws IOException { ended = true; forget(this); framed.close(); }
    }

    ServiceRecord record(Connection c) {
        if (!(c instanceof VirtualBluetooth.Notifier)) throw new IllegalArgumentException("Not a virtual notifier");
        Notifier n = (Notifier) c;
        synchronized (this) {
            if (!connections.contains(n) || n.ended) throw new IllegalArgumentException("Service is not owned by this session");
        }
        return n.record;
    }
    void update(ServiceRecord r) throws ServiceRegistrationException {
        Objects.requireNonNull(r);
        Notifier found = null;
        synchronized (this) {
            for (Connection c : connections) if (c instanceof Notifier && ((Notifier)c).record == r) found = (Notifier)c;
        }
        if (found == null) throw new IllegalArgumentException("Service is not owned by this session");
        try { check(); found.listener.update(found.record.snapshot()); }
        catch (IOException | IllegalArgumentException e) { throw new ServiceRegistrationException(e.getMessage()); }
    }

    private static final class Record implements ServiceRecord {
        final boolean packets; final int port;
        final RemoteDevice peer;
        final Map<Integer, DataElement> attributes = new LinkedHashMap<>();
        Record(RemoteDevice peer, Service s, int port) {
            this.peer = peer; this.packets = s.packets; this.port = port;
            if (port < 1 || port > (packets ? 65535 : 30)) throw new IllegalArgumentException("Invalid service port");
            attributes.putAll(VirtualServiceAttributes.snapshot(new J2MEServiceRecord(peer, s.uuid, false, s.packets)));
            attributes.put(0x100, new DataElement(DataElement.STRING, s.name));
            for (Map.Entry<Integer, DataElement> entry : s.attributes.entrySet()) attributes.put(entry.getKey(), VirtualServiceAttributes.copy(entry.getValue()));
            DataElement list = new DataElement(DataElement.DATSEQ);
            DataElement l2 = new DataElement(DataElement.DATSEQ);
            l2.addElement(new DataElement(DataElement.UUID, new UUID(0x100)));
            if (packets) l2.addElement(new DataElement(DataElement.U_INT_2, port));
            list.addElement(l2);
            if (!packets) {
                DataElement rf = new DataElement(DataElement.DATSEQ);
                rf.addElement(new DataElement(DataElement.UUID, new UUID(3)));
                rf.addElement(new DataElement(DataElement.U_INT_1, port)); list.addElement(rf);
            }
            setAttributeValue(4, list);
        }
        @Override public RemoteDevice getHostDevice() { return peer; }
        @Override public synchronized int[] getAttributeIDs() {
            int[] ids = new int[attributes.size()]; int i = 0;
            for (Integer id : attributes.keySet()) ids[i++] = id;
            return ids;
        }
        @Override public synchronized DataElement getAttributeValue(int id) {
            validId(id); return attributes.get(id);
        }
        @Override public synchronized boolean setAttributeValue(int id, DataElement value) {
            validId(id); if (id == 0) throw new IllegalArgumentException("Immutable service handle");
            if (value == null) return attributes.remove(id) != null;
            if (!attributes.containsKey(id) && attributes.size() >= 256) throw new IllegalArgumentException("Too many attributes");
            DataElement previous = attributes.put(id, VirtualServiceAttributes.copy(value));
            try { VirtualServiceAttributes.snapshot(this); }
            catch (IllegalArgumentException e) { if (previous == null) attributes.remove(id); else attributes.put(id, previous); throw e; }
            return true;
        }
        @Override public boolean populateRecord(int[] ids) {
            Objects.requireNonNull(ids); if (ids.length == 0) throw new IllegalArgumentException("Empty attribute set");
            boolean found = false;
            for (int id : ids) { validId(id); found |= getAttributeValue(id) != null; }
            return found;
        }
        @Override public void setDeviceServiceClasses(int classes) {
            if ((classes & ~0xffe000) != 0) throw new IllegalArgumentException("Invalid service classes");
        }
        synchronized Record snapshot() {
            // Copy before handing metadata to asynchronous transport code.
            return new Record(this);
        }
        private Record(Record source) {
            peer = source.peer; packets = source.packets; port = source.port;
            attributes.putAll(VirtualServiceAttributes.snapshot(source));
        }
        private static void validId(int id) { if (id < 0 || id > 65535) throw new IllegalArgumentException("Invalid attribute id"); }
        @Override public String getConnectionURL(int security, boolean master) {
            security(security);
            RemoteDevice peer = getHostDevice();
            if (peer == null) return null;
            return (packets ? "btl2cap://" : "btspp://") + peer.getBluetoothAddress() + ":"
                    + (packets ? Integer.toHexString(port) : port) + ";authenticate=" + (security != 0)
                    + ";encrypt=" + (security == 2) + ";master=" + master;
        }
    }

    static final class Url {
        final boolean packets, server; final String host, target;
        String name = ""; int rx = 672, tx = 48;
        Url(String url) {
            Objects.requireNonNull(url);
            packets = url.startsWith("btl2cap://");
            if (!packets && !url.startsWith("btspp://")) throw new IllegalArgumentException("Not a Bluetooth URL");
            String[] parts = url.substring(packets ? 10 : 8).split(";", -1);
            int colon = parts[0].indexOf(':');
            if (colon <= 0 || colon == parts[0].length()-1) throw new IllegalArgumentException("Missing Bluetooth endpoint");
            host = parts[0].substring(0, colon); target = parts[0].substring(colon+1); server = host.equals("localhost");
            Set<String> keys = new HashSet<>(); boolean auth = false, encrypt = false, authorize = false;
            for (int i = 1; i < parts.length; i++) {
                int eq = parts[i].indexOf('=');
                if (eq < 1) throw new IllegalArgumentException("Invalid Bluetooth parameter");
                String key = parts[i].substring(0, eq).toLowerCase(Locale.ROOT), value = parts[i].substring(eq+1);
                if (!keys.add(key)) throw new IllegalArgumentException("Duplicate Bluetooth parameter");
                switch (key) {
                    case "name": if (!server || value.length() > 248) throw new IllegalArgumentException("Invalid service name"); name = value; break;
                    case "receivemtu": if (!packets) throw new IllegalArgumentException("RFCOMM MTU"); rx = number(value); mtu(rx); break;
                    case "transmitmtu": if (!packets) throw new IllegalArgumentException("RFCOMM MTU"); tx = number(value); mtu(tx); break;
                    case "authenticate": auth = bool(value); break;
                    case "encrypt": encrypt = bool(value); break;
                    case "authorize": if (!server) throw new IllegalArgumentException("Client authorization"); authorize = bool(value); break;
                    case "master": bool(value); break;
                    default: throw new IllegalArgumentException("Unsupported Bluetooth parameter");
                }
            }
            if ((encrypt || authorize) && !auth) throw new IllegalArgumentException("Authentication required");
        }
        private static int number(String value) {
            if (!value.matches("[0-9]{1,5}")) throw new IllegalArgumentException("Invalid MTU"); return Integer.parseInt(value);
        }
        private static boolean bool(String value) {
            if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("Invalid boolean"); return value.equals("true");
        }
    }
    private static String address(String value) {
        if (value == null || !value.matches("[0-9a-fA-F]{12}")) throw new IllegalArgumentException("Invalid Bluetooth address");
        return value.toUpperCase(Locale.ROOT);
    }
    private static void mtu(int value) { if (value < 48 || value > 672) throw new IllegalArgumentException("Unsupported MTU"); }
    private static void security(int value) { if (value < 0 || value > 2) throw new IllegalArgumentException("Invalid security mode"); }
    private static BluetoothStateException state(IOException e) { return new BluetoothStateException(e.getMessage()); }

    @Override public void close() throws IOException {
        List<Connection> pending;
        synchronized (this) {
            if (closed) return;
            closed = true; inquiry = null; searches.clear(); cache.clear();
            pending = new ArrayList<>(connections); connections.clear();
        }
        worker.shutdownNow();
        IOException failure = null;
        try { backend.close(); } catch (IOException e) { failure = e; }
        for (Connection c : pending) try {
            if (c instanceof Connected) ((Connected)c).abort(); else c.close();
        } catch (IOException e) { if (failure == null) failure = e; else failure.addSuppressed(e); }
        if (failure != null) throw failure;
    }
}
