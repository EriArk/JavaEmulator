/* SPDX-License-Identifier: GPL-3.0-only */
package javax.bluetooth;

import java.io.*;
import java.util.*;

/** Logical Bluetooth services/streams over ONE authenticated reliable ordered peer.
 * Game calls may block; receive/pollOutbound never do. The transport owner must
 * close this instance on account, admission, game, generation or attempt changes.
 */
public final class PeerBluetoothBackend implements VirtualBluetooth.Backend {
    private static final int QUERY=1, BARRIER=2, SERVICE=3, REMOVE=4, VISIBLE=5,
            OPEN=6, ACCEPT=7, REJECT=8, DATA=9, CREDIT=10, CLOSE=11;
    static final int WINDOW=32768, CHUNK=8192, MAX_STREAMS=16, MAX_SERVICES=16;
    private static final int MAX_WIRE=16383, MAX_QUEUE=1048576;
    private final VirtualBluetooth.Peer remote;
    private final int parity;
    private final Map<Integer, Server> servers = new LinkedHashMap<>();
    private final Map<Integer, VirtualBluetooth.Service> services = new LinkedHashMap<>();
    private final Map<Integer, Pipe> pipes = new HashMap<>();
    private final ArrayDeque<byte[]> outbound = new ArrayDeque<>();
    private int queued, nextId, lastRemote, opens, query, barrier, visible;
    private boolean closed;
    private IOException failure;

    public PeerBluetoothBackend(VirtualBluetooth.Peer remote, boolean host) {
        this.remote = Objects.requireNonNull(remote); parity = host ? 1 : 0; nextId = host ? 1 : 2;
    }
    @Override public synchronized List<VirtualBluetooth.Peer> peers() throws IOException {
        check(); return Collections.singletonList(remote);
    }
    @Override public synchronized List<VirtualBluetooth.Peer> inquiryPeers() throws IOException {
        sync(); return visible == 0 ? Collections.emptyList() : peers();
    }
    @Override public synchronized List<VirtualBluetooth.Service> services(String address) throws IOException {
        peer(address); sync(); return new ArrayList<>(services.values());
    }
    @Override public synchronized void discoverable(int mode) throws IOException {
        if (mode != 0 && (mode < 0x9e8b00 || mode > 0x9e8b3f)) throw new IllegalArgumentException("Invalid discovery mode");
        emit(VISIBLE, mode, null);
    }
    @Override public synchronized VirtualBluetooth.Listener listen(VirtualBluetooth.Service service) throws IOException {
        check(); if (servers.size() >= MAX_SERVICES) throw new IOException("Too many Bluetooth services");
        int port = 1; while (servers.containsKey(key(port, service.packets))) port++;
        Server server = new Server(service, port);
        advertise(server, null); servers.put(key(port, service.packets), server); return server;
    }
    @Override public synchronized VirtualBluetooth.Channel connect(String address, int port, boolean packets,
                                                                  int rx, int tx) throws IOException {
        peer(address); mtu(rx); mtu(tx);
        if (port < 1 || port > (packets ? 65535 : 30)) throw new IOException("Invalid service port");
        if (pipes.size() >= MAX_STREAMS || ++opens > 1024) throw new IOException("Bluetooth stream limit");
        Pipe pipe = new Pipe(nextId, rx); pipe.minimumTx = tx; nextId += 2; pipes.put(pipe.id, pipe);
        try {
            emit(OPEN, pipe.id, out -> { out.writeShort(port); out.writeBoolean(packets); out.writeShort(rx); out.writeShort(tx); });
            long until = deadline();
            while (!pipe.accepted && !pipe.ended) await(until);
            pipe.writable(); return pipe.channel();
        } catch (IOException e) { pipe.end(false); throw e; }
    }
    /** Only the secure transport owner calls this, after its encrypted identity handshake. */
    public synchronized byte[] pollOutbound() throws IOException {
        check(); byte[] frame = outbound.poll(); if (frame != null) queued -= frame.length; return frame;
    }
    public synchronized void receive(byte[] frame) throws IOException {
        check();
        try {
            if (frame == null || frame.length < 7 || frame.length > MAX_WIRE) throw new IOException("Invalid Bluetooth frame");
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(frame));
            if (in.readUnsignedShort() != 0xAB01) throw new IOException("Wrong Bluetooth protocol");
            int kind = in.readUnsignedByte(), id = in.readInt();
            switch (kind) {
                case QUERY:
                    if (id < 1) throw new IOException("Invalid metadata barrier"); emit(BARRIER, id, null); break;
                case BARRIER:
                    if (id < barrier || id > query) throw new IOException("Invalid metadata response"); barrier = id; break;
                case VISIBLE:
                    if (id != 0 && (id < 0x9e8b00 || id > 0x9e8b3f)) throw new IOException("Invalid discovery mode"); visible = id; break;
                case SERVICE:
                    VirtualBluetooth.Service service = VirtualServiceCodec.read(in);
                    if (id != key(service.port, service.packets) || !services.containsKey(id) && services.size() >= MAX_SERVICES)
                        throw new IOException("Invalid remote service table");
                    services.put(id, service); break;
                case REMOVE:
                    if (services.remove(id) == null) throw new IOException("Unknown service removal"); break;
                case OPEN: incoming(id, in); break;
                case ACCEPT: case REJECT: case DATA: case CREDIT: case CLOSE:
                    stream(kind, id, in); break;
                default: throw new IOException("Unknown Bluetooth frame");
            }
            if (in.available() != 0) throw new IOException("Trailing Bluetooth frame data");
            notifyAll();
        } catch (IOException | RuntimeException e) {
            IOException bad = new IOException("Invalid peer Bluetooth traffic", e); abort(bad); throw bad;
        }
    }
    private void incoming(int id, DataInputStream in) throws IOException {
        if (id <= lastRemote || id < 1 || (id & 1) == parity) throw new IOException("Invalid remote stream identity");
        lastRemote = id;
        int port = in.readUnsignedShort(); boolean packets = VirtualServiceCodec.bool(in);
        int rx = in.readUnsignedShort(), tx = in.readUnsignedShort(); mtu(rx); mtu(tx);
        Server server = servers.get(key(port, packets));
        if (server != null) server.pending.removeIf(p -> p.ended);
        if (++opens > 1024) throw new IOException("Bluetooth stream attempt limit");
        if (server == null || server.ended || pipes.size() >= MAX_STREAMS || server.pending.size() >= 8
                || server.service.receiveMtu < tx || rx < server.service.minimumTransmitMtu) {
            emit(REJECT, id, null); return;
        }
        Pipe pipe = new Pipe(id, server.service.receiveMtu); pipe.tx = rx; pipe.accepted = true;
        pipes.put(id, pipe); server.pending.add(pipe);
        emit(ACCEPT, id, out -> out.writeShort(pipe.rx));
    }
    private void stream(int kind, int id, DataInputStream in) throws IOException {
        Pipe pipe = pipes.get(id);
        if (pipe == null) {
            boolean old = id > 0 && ((id & 1) == parity ? id < nextId : id <= lastRemote);
            if (!old) throw new IOException("Unknown Bluetooth stream");
            // Frames already in flight before local close cannot revive the channel.
            in.skipBytes(in.available()); return;
        }
        switch (kind) {
            case ACCEPT:
                if ((id & 1) != parity || pipe.accepted) throw new IOException("Unexpected stream acceptance");
                pipe.tx = in.readUnsignedShort(); mtu(pipe.tx);
                if (pipe.tx < pipe.minimumTx) throw new IOException("Peer rejected minimum transmit MTU");
                pipe.accepted = true; break;
            case REJECT:
                if (pipe.accepted || (id & 1) != parity) throw new IOException("Unexpected stream rejection");
                pipe.error = new IOException("Remote Bluetooth service unavailable"); pipe.end(true); break;
            case CLOSE: pipe.end(true); break;
            case DATA:
                int n = in.available();
                if (!pipe.accepted || n < 1 || n > CHUNK || n > WINDOW - pipe.size - pipe.consumed)
                    throw new IOException("Peer exceeded receive credit");
                for (int i = 0; i < n; i++) pipe.buffer[(pipe.head + pipe.size++) % WINDOW] = in.readByte();
                break;
            case CREDIT:
                int credit = in.readInt();
                if (!pipe.accepted || credit < 1 || credit > WINDOW - pipe.credit) throw new IOException("Invalid stream credit");
                pipe.credit += credit; break;
        }
    }
    private void sync() throws IOException {
        check(); if (query == Integer.MAX_VALUE) throw new IOException("Metadata request limit");
        int request = ++query; emit(QUERY, request, null); long until = deadline();
        while (barrier < request) await(until);
    }
    private void advertise(Server server, ServiceRecord record) throws IOException {
        VirtualBluetooth.Service s = server.service;
        VirtualBluetooth.Service updated = new VirtualBluetooth.Service(s.uuid, s.name, s.packets, server.port,
                s.receiveMtu, s.minimumTransmitMtu, record == null ? VirtualServiceCodec.record(s.attributeSnapshot()) : record);
        emit(SERVICE, key(server.port, s.packets), out -> VirtualServiceCodec.write(out, updated));
    }
    private interface Encoder { void write(DataOutputStream out) throws IOException; }
    private void emit(int kind, int id, Encoder encoder) throws IOException {
        check(); ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
        out.writeShort(0xAB01); out.writeByte(kind); out.writeInt(id); if (encoder != null) encoder.write(out);
        byte[] frame = bytes.toByteArray();
        if (frame.length > MAX_WIRE) throw new IOException("Bluetooth service metadata is too large");
        if (outbound.size() >= 256 || queued + frame.length > MAX_QUEUE) {
            IOException e = new IOException("Bluetooth transport queue is full"); abort(e); throw e;
        }
        outbound.add(frame); queued += frame.length;
    }
    private void peer(String address) throws IOException { check(); if (!remote.address.equalsIgnoreCase(address)) throw new IOException("Peer not admitted"); }
    private static int key(int port, boolean packets) { return port | (packets ? 65536 : 0); }
    private static void mtu(int n) throws IOException { if (n < 48 || n > 672) throw new IOException("Invalid Bluetooth MTU"); }
    private static long deadline() { return System.nanoTime() + 30000000000L; }
    private void await(long until) throws IOException {
        check(); long left = until == Long.MAX_VALUE ? 1000000000L : until - System.nanoTime();
        if (left <= 0) throw new IOException("Bluetooth operation timed out");
        try { wait(Math.max(1, Math.min(1000, left / 1000000))); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new InterruptedIOException("Bluetooth operation interrupted"); }
        check();
    }
    private void check() throws IOException { if (closed) throw new IOException("Bluetooth peer session closed", failure); }
    private void abort(IOException reason) {
        if (closed) return; closed = true; failure = reason;
        for (Pipe p : pipes.values()) { p.ended = true; p.error = reason; p.size = 0; }
        pipes.clear(); servers.clear(); services.clear(); outbound.clear(); queued = 0; notifyAll();
    }
    @Override public synchronized void close() { abort(new IOException("Bluetooth peer disconnected")); }

    private final class Server implements VirtualBluetooth.Listener {
        final VirtualBluetooth.Service service; final int port;
        final ArrayDeque<Pipe> pending = new ArrayDeque<>(); boolean ended;
        Server(VirtualBluetooth.Service s, int p) { service = s; port = p; }
        public int port() { return port; }
        public VirtualBluetooth.Channel accept() throws IOException {
            synchronized (PeerBluetoothBackend.this) {
                for (;;) {
                    check(); if (ended) throw new IOException("Bluetooth service closed");
                    Pipe p = pending.poll(); if (p != null) { if (!p.ended) return p.channel(); continue; }
                    await(Long.MAX_VALUE);
                }
            }
        }
        public void update(ServiceRecord record) throws IOException {
            synchronized (PeerBluetoothBackend.this) { check(); if (ended) throw new IOException("Bluetooth service closed"); advertise(this, record); }
        }
        public void close() throws IOException {
            synchronized (PeerBluetoothBackend.this) {
                if (ended) return; ended = true; servers.remove(key(port, service.packets));
                for (Pipe p : pending) p.end(false); pending.clear();
                if (!closed) emit(REMOVE, key(port, service.packets), null); PeerBluetoothBackend.this.notifyAll();
            }
        }
    }
    private final class Pipe {
        final int id, rx; int tx, minimumTx=48, credit=WINDOW, head, size, consumed;
        final byte[] buffer = new byte[WINDOW]; boolean accepted, ended; IOException error;
        Pipe(int id, int rx) { this.id = id; this.rx = rx; }
        void writable() throws IOException { check(); if (ended || !accepted) throw new IOException("Bluetooth stream closed", error); }
        void end(boolean fromRemote) throws IOException {
            if (ended) return; ended = true; pipes.remove(id);
            if (!fromRemote) { size = 0; if (!closed) emit(CLOSE, id, null); }
            PeerBluetoothBackend.this.notifyAll();
        }
        VirtualBluetooth.Channel channel() {
            return new VirtualBluetooth.Channel(remote.address, new InputStream() {
                public int read() throws IOException { byte[] one = new byte[1]; return read(one, 0, 1) < 0 ? -1 : one[0] & 255; }
                public int available() throws IOException { synchronized (PeerBluetoothBackend.this) { check(); return size; } }
                public int read(byte[] b, int off, int len) throws IOException {
                    Objects.requireNonNull(b); if (off < 0 || len < 0 || len > b.length - off) throw new IndexOutOfBoundsException();
                    synchronized (PeerBluetoothBackend.this) {
                        check(); if (len == 0) return 0;
                        while (size == 0 && !ended) await(Long.MAX_VALUE);
                        check(); if (error != null) throw error; if (size == 0) return -1;
                        int n = Math.min(len, size);
                        for (int i = 0; i < n; i++) b[off+i] = buffer[(head+i) % WINDOW];
                        head = (head+n) % WINDOW; size -= n; consumed += n;
                        if (!ended && (consumed >= WINDOW/4 || size == 0)) {
                            int grant = consumed; consumed = 0; emit(CREDIT, id, out -> out.writeInt(grant));
                        }
                        return n;
                    }
                }
                public void close() throws IOException { synchronized (PeerBluetoothBackend.this) { end(false); } }
            }, new OutputStream() {
                public void write(int b) throws IOException { write(new byte[]{(byte)b}, 0, 1); }
                public void write(byte[] b, int off, int len) throws IOException {
                    Objects.requireNonNull(b); if (off < 0 || len < 0 || len > b.length - off) throw new IndexOutOfBoundsException();
                    synchronized (PeerBluetoothBackend.this) {
                        try {
                            writable(); long until = deadline();
                            while (len > 0) {
                                while (credit == 0) { writable(); await(until); }
                                writable(); int n = Math.min(len, Math.min(CHUNK, credit)), start = off;
                                emit(DATA, id, out -> out.write(b, start, n)); credit -= n; off += n; len -= n; until = deadline();
                            }
                        } catch (IOException e) {
                            try { end(false); } catch (IOException ignored) { }
                            throw e;
                        }
                    }
                }
                // No buffered bytes live in this stream. A remote logical close is
                // not a failed flush; a broken peer session still is.
                public void flush() throws IOException { synchronized (PeerBluetoothBackend.this) { check(); } }
                public void close() throws IOException { synchronized (PeerBluetoothBackend.this) { end(false); } }
            }, () -> { synchronized (PeerBluetoothBackend.this) { end(false); } }, rx, tx);
        }
    }
}
