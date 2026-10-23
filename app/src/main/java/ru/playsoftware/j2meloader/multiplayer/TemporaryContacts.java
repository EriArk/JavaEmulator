/* SPDX-License-Identifier: GPL-3.0-only
 * Adapted from TrainerOS TemporaryContacts (d69e50dc), Copyright 2026 EriArk.
 * Protocol/state-machine provenance and integration limits: docs/MULTIPLAYER.md.
 */
package ru.playsoftware.j2meloader.multiplayer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Account-scoped actor. Call it and deliver provider completions on its owner thread.
 * The caller supplies authenticated signaling; a JSON peer field is not authentication.
 */
public final class TemporaryContacts implements AutoCloseable {
    public interface Journal {
        JsonObject load() throws IOException;
        void save(JsonObject snapshot) throws IOException;
    }

    public interface Provider {
        void request(String method, String path, JsonObject body, Consumer<Reply> completion);
    }

    public interface Signaling {
        void send(String peer, JsonObject packet);
    }

    public static final class Reply {
        public final int status, retrySeconds;
        public final JsonElement body;

        public Reply(int status, JsonElement body, int retrySeconds) {
            this.status = status;
            this.body = body;
            this.retrySeconds = Math.max(0, Math.min(86400, retrySeconds));
        }
    }

    private static final Set<String> PHASES = new HashSet<>(Arrays.asList(
            "baseline", "offer", "agree", "create", "creating", "wait", "accept", "accepting",
            "accepted", "confirm", "friend", "keep", "kept", "release", "released", "cleanup",
            "deleting", "gone", "done", "exempt", "blocked", "error", "uncertain"));
    private final Thread owner = Thread.currentThread();
    private final String self;
    private final Journal journal;
    private final Provider provider;
    private final Signaling signaling;
    private final Map<String, Long> next = new HashMap<>();
    private JsonObject records = new JsonObject();
    private Set<String> members = Collections.emptySet(), held = Collections.emptySet();
    private String room = "", host = "", busy = "", error = "";
    private long now, nextCreate, deadline, flight;
    private boolean online, recovering, closed;

    public TemporaryContacts(String self, Journal journal, Provider provider,
                             Signaling signaling, long nowSeconds) {
        if (!validId(self) || nowSeconds < 0) throw new IllegalArgumentException("Invalid account or clock");
        this.self = self;
        this.journal = java.util.Objects.requireNonNull(journal);
        this.provider = java.util.Objects.requireNonNull(provider);
        this.signaling = java.util.Objects.requireNonNull(signaling);
        now = nextCreate = nowSeconds;
        try {
            JsonObject saved = journal.load();
            if (saved == null) return;
            if (number(saved, "v") != 1 || !self.equals(string(saved, "self"))
                    || !saved.has("contacts") || !saved.get("contacts").isJsonObject()) {
                throw new IOException("Wrong contact journal");
            }
            JsonObject loaded = saved.getAsJsonObject("contacts").deepCopy();
            if (loaded.size() > 256) throw new IOException("Too many contacts");
            for (Map.Entry<String, JsonElement> entry : loaded.entrySet()) {
                if (!validId(entry.getKey()) || self.equals(entry.getKey()) || !entry.getValue().isJsonObject()) {
                    throw new IOException("Invalid contact");
                }
                JsonObject r = entry.getValue().getAsJsonObject();
                String phase = string(r, "phase");
                if (!validUuid(string(r, "lease")) || !validUuid(string(r, "room")) || !PHASES.contains(phase)
                        || !r.has("host") || !r.get("host").isJsonPrimitive()
                        || !r.getAsJsonPrimitive("host").isBoolean()) throw new IOException("Invalid lease");
                if (phase.equals("creating") || phase.equals("accepting")) {
                    r.addProperty("phase", "uncertain");
                    r.addProperty("error", "Check this friendship after the interrupted request.");
                }
                if (phase.equals("deleting")) r.addProperty("phase", "cleanup");
                next.put(entry.getKey(), now + 15);
            }
            records = loaded;
        } catch (IOException | RuntimeException failure) {
            records = new JsonObject();
            error = "Temporary contact history needs recovery. Automatic changes stopped.";
        }
    }

    /** Call only with locally validated, admitted membership, never directory listings/invites. */
    public void context(String room, String host, Set<String> admitted, boolean online, boolean recovering) {
        checkThread();
        this.online = online;
        this.recovering = recovering;
        this.room = this.host = "";
        members = Collections.emptySet();
        if (!validUuid(room) || !validId(host) || admitted == null || admitted.size() > 32
                || !admitted.contains(self) || !admitted.contains(host)) return;
        for (String peer : admitted) if (!validId(peer)) return;
        this.room = room;
        this.host = host;
        members = new HashSet<>(admitted);
    }

    /** Retain contacts still used by an active match, even after leaving its lobby. */
    public void hold(Set<String> peers) {
        checkThread();
        held = new HashSet<>(peers);
    }

    public JsonObject state() {
        checkThread();
        JsonObject peers = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : records.entrySet()) {
            JsonObject r = entry.getValue().getAsJsonObject();
            String phase = string(r, "phase");
            if (oneOf(phase, "exempt", "kept", "gone", "done")) continue;
            JsonObject view = object("phase", phase);
            view.addProperty("canKeep", oneOf(phase, "friend", "accepted"));
            view.addProperty("pendingKeep", phase.equals("keep"));
            view.addProperty("error", string(r, "error"));
            peers.add(entry.getKey(), view);
        }
        JsonObject result = object("error", error);
        result.add("peers", peers);
        return result;
    }

    public boolean keep(String peer) {
        checkThread();
        JsonObject r = record(peer);
        String phase = string(r, "phase");
        if (oneOf(phase, "kept", "exempt")) return true;
        if (!oneOf(phase, "friend", "accepted", "keep")) return false;
        boolean host = flag(r, "host");
        r.addProperty("phase", host ? "kept" : "keep");
        r.remove("error");
        if (!put(peer, r)) return false;
        send(peer, host ? "kept" : "keep");
        return true;
    }

    public void receive(String authenticatedPeer, JsonObject packet) {
        checkThread();
        String peer = authenticatedPeer;
        if (closed || !online || !error.isEmpty() || !validId(peer) || peer.equals(self) || packet == null
                || packet.toString().length() > 4096 || !string(packet, "ns").equals("org.traineros.lobby")
                || number(packet, "v") != 1 || !string(packet, "kind").equals("contact")
                || number(packet, "expires") <= now || number(packet, "expires") > now + 90
                || !packet.has("data") || !packet.get("data").isJsonObject()) return;
        JsonObject data = packet.getAsJsonObject("data");
        String lease = string(data, "lease"), room = string(packet, "lobby"), op = string(data, "op");
        if (!validUuid(lease) || !validUuid(room)) return;
        JsonObject r = record(peer);
        String phase = string(r, "phase");
        boolean replace = phase.equals("done") || (!active(peer) && !busy.equals(peer) && !r.has("type")
                && !oneOf(phase, "uncertain", "creating", "accepting", "kept", "keep"));
        if ((r.size() == 0 || replace) && op.equals("offer") && peer.equals(host) && room.equals(this.room)
                && members.contains(self) && members.contains(peer) && records.size() < 256) {
            put(peer, lease(lease, room, false));
            return;
        }
        if (r.size() == 0 || !lease.equals(string(r, "lease")) || !room.equals(string(r, "room"))) return;
        boolean host = flag(r, "host");
        if (op.equals("keep") && host) {
            if (!oneOf(phase, "deleting", "gone", "done", "exempt", "uncertain", "blocked", "error") && r.has("type")) {
                r.addProperty("phase", "kept"); r.remove("error");
                if (put(peer, r)) send(peer, "kept");
            }
        } else if (op.equals("kept") && !host) {
            r.addProperty("phase", "kept"); r.remove("error");
            if (put(peer, r)) send(peer, "kept-ok");
        } else if (op.equals("kept-ok") && host && phase.equals("kept")) {
            r.addProperty("acknowledged", true); put(peer, r);
        } else if (op.equals("release") && !host && !oneOf(phase, "done", "gone", "uncertain", "error", "blocked", "exempt")) {
            release(peer);
        } else if (op.equals("remove-ok") && host && phase.equals("release")) {
            r.addProperty("phase", "cleanup"); if (put(peer, r)) next.put(peer, now);
        } else if (op.equals("gone") && !host && oneOf(phase, "released", "done")) {
            r.addProperty("phase", "done"); if (put(peer, r)) send(peer, "gone-ok");
        } else if (op.equals("gone-ok") && host && phase.equals("gone")) {
            put(peer, null);
        } else if (op.equals("blocked") && oneOf(phase, "offer", "baseline", "agree")) {
            r.addProperty("phase", "blocked"); r.addProperty("error", "Friendship permissions need attention."); put(peer, r);
        } else if (op.equals("agree") && host && phase.equals("offer") && active(peer)) {
            r.addProperty("phase", "create"); if (put(peer, r)) next.put(peer, now);
        } else if (op.equals("requested") && !host && phase.equals("agree") && active(peer)) {
            r.addProperty("phase", "accept"); if (put(peer, r)) next.put(peer, now);
        } else if (op.equals("accepted") && host && oneOf(phase, "wait", "friend") && active(peer)) {
            if (phase.equals("friend")) send(peer, "complete");
            else { r.addProperty("phase", "confirm"); if (put(peer, r)) next.put(peer, now); }
        } else if (op.equals("complete") && !host && phase.equals("accepted")) {
            r.addProperty("phase", "friend"); put(peer, r);
        } else if (op.equals("offer") && !host && phase.equals("agree")) send(peer, "agree");
    }

    public void tick(long seconds) {
        checkThread();
        now = Math.max(now, seconds);
        if (closed || !online || recovering || !error.isEmpty()) return;
        if (!busy.isEmpty()) {
            if (now < deadline) return;
            String peer = busy; busy = ""; ++flight;
            JsonObject r = record(peer);
            String phase = string(r, "phase");
            if (oneOf(phase, "creating", "accepting")) {
                r.addProperty("phase", "uncertain");
                r.addProperty("error", "Check this friendship after the interrupted request.");
                put(peer, r);
            } else if (phase.equals("deleting")) { r.addProperty("phase", "cleanup"); put(peer, r); }
            next.put(peer, now + 15);
        }
        if (host.equals(self)) for (String peer : members) if (!peer.equals(self) && records.size() < 256) {
            JsonObject r = record(peer);
            String phase = string(r, "phase");
            boolean replace = !room.equals(string(r, "room")) && (oneOf(phase, "gone", "done")
                    || (oneOf(phase, "release", "blocked", "error") && !r.has("type")));
            if (r.size() == 0 || replace) put(peer, lease(UUID.randomUUID().toString(), room, true));
        }
        for (String peer : new HashSet<>(records.keySet())) {
            if (now < next.getOrDefault(peer, 0L)) continue;
            JsonObject r = record(peer);
            String phase = string(r, "phase");
            boolean host = flag(r, "host");
            if (oneOf(phase, "done", "gone", "kept") && !active(peer) && number(r, "ended") > 0
                    && now - number(r, "ended") > 86400) { put(peer, null); continue; }
            if (phase.equals("done")) continue;
            if (phase.equals("gone")) { send(peer, "gone"); continue; }
            if (held.contains(peer) && !active(peer)) continue;
            if (!host && flag(r, "releaseRequested")) { release(peer); continue; }
            if (phase.equals("kept")) {
                if (!active(peer) && host && flag(r, "acknowledged")) put(peer, null);
                else if (host && !flag(r, "acknowledged")) send(peer, "kept");
                continue;
            }
            if (phase.equals("keep")) { send(peer, "keep"); continue; }
            if (phase.equals("exempt")) { if (!active(peer)) put(peer, null); continue; }
            if (oneOf(phase, "blocked", "error", "uncertain")) continue;
            if (host && !active(peer) && !oneOf(phase, "release", "cleanup")) {
                phase = "release"; r.addProperty("phase", phase); if (!put(peer, r)) return;
            }
            if (!host && !active(peer) && !phase.equals("released")) continue;
            if (oneOf(phase, "release", "offer", "agree", "accepted")) send(peer, phase);
            else if (phase.equals("released")) send(peer, "remove-ok");
            else if (phase.equals("wait")) send(peer, "requested");
            else if (oneOf(phase, "baseline", "create", "accept", "confirm", "cleanup")) { read(peer, phase); return; }
        }
    }

    private void read(String peer, String phase) {
        request(peer, phase, "GET", "/v1/users/@me/relationships", new JsonObject(), reply -> {
            if (!online || recovering) { next.put(peer, now + 15); return; }
            if (reply.status != 200 || reply.body == null || !reply.body.isJsonArray()) {
                next.put(peer, now + Math.max(15, reply.retrySeconds)); return;
            }
            JsonArray rows = reply.body.getAsJsonArray();
            JsonObject relation = new JsonObject();
            for (JsonElement value : rows) {
                if (!value.isJsonObject() || !validId(string(value.getAsJsonObject(), "id"))
                        || number(value.getAsJsonObject(), "type") < 1) {
                    fail(peer, "Couldn't read friendship state. Automatic changes stopped."); return;
                }
                if (peer.equals(string(value.getAsJsonObject(), "id"))) {
                    if (relation.size() > 0) { fail(peer, "Ambiguous friendship state. Automatic changes stopped."); return; }
                    relation = value.getAsJsonObject();
                }
            }
            JsonObject r = record(peer);
            if (flag(r, "releaseRequested")) return;
            int type = (int) number(relation, "type");
            if (phase.equals("baseline")) {
                if (type != 0) {
                    r.addProperty("phase", type == 1 ? "exempt" : "blocked");
                    if (type != 1) r.addProperty("error", "An existing request or block needs your decision.");
                    if (put(peer, r) && !flag(r, "host")) send(peer, "blocked");
                } else {
                    String op = flag(r, "host") ? "offer" : "agree";
                    r.addProperty("phase", op); if (put(peer, r)) send(peer, op);
                }
            } else if (phase.equals("create")) {
                if (!active(peer)) return;
                if (type != 0) { fail(peer, "Friendship changed; automatic request stopped."); return; }
                if (now < nextCreate) { next.put(peer, nextCreate); return; }
                nextCreate = now + 7; mutate(peer, "creating", "POST", new JsonObject(), 4);
            } else if (phase.equals("accept")) {
                if (!active(peer)) return;
                if (type == 0) { next.put(peer, now + 5); return; }
                if (!stamped(relation, 3)) { fail(peer, "Friendship changed; automatic acceptance stopped."); return; }
                JsonObject body = new JsonObject(); body.addProperty("type", 1);
                mutate(peer, "accepting", "PUT", body, 1);
            } else if (phase.equals("confirm")) {
                if (!stamped(relation, 1)) { fail(peer, "Couldn't verify the temporary friendship."); return; }
                r.addProperty("type", 1); r.addProperty("since", string(relation, "since")); r.addProperty("phase", "friend");
                if (put(peer, r)) send(peer, "complete");
            } else if (phase.equals("cleanup")) {
                if (held.contains(peer) || recovering || members.contains(peer)) { next.put(peer, now + 15); return; }
                if (type == 0) { r.addProperty("phase", "gone"); if (put(peer, r)) send(peer, "gone"); return; }
                if (!oneOf(Long.toString(number(r, "type")), "1", "4") || !stamped(relation, (int) number(r, "type"))
                        || !string(relation, "since").equals(string(r, "since"))) {
                    r.addProperty("phase", "uncertain"); r.addProperty("error", "Friendship changed; it was kept. Review it in the profile.");
                    if (put(peer, r)) send(peer, "kept"); return;
                }
                mutate(peer, "deleting", "DELETE", new JsonObject(), 0);
            }
        });
    }

    private void mutate(String peer, String phase, String method, JsonObject body, int expectedType) {
        JsonObject r = record(peer); r.addProperty("phase", phase);
        if (!put(peer, r)) return;
        request(peer, phase, method, "/v1/users/@me/relationships/" + peer, body, reply -> {
            JsonObject current = record(peer);
            if (phase.equals("deleting")) {
                if ((reply.status >= 200 && reply.status < 300) || reply.status == 404) {
                    current.addProperty("phase", "gone"); if (put(peer, current)) send(peer, "gone");
                } else {
                    current.addProperty("phase", "cleanup"); put(peer, current);
                    next.put(peer, now + Math.max(15, reply.retrySeconds));
                }
                return;
            }
            if (reply.status == 429) {
                current.addProperty("phase", phase.equals("creating") ? "create" : "accept"); put(peer, current);
                next.put(peer, now + Math.max(15, reply.retrySeconds)); return;
            }
            JsonObject relation = reply.body != null && reply.body.isJsonObject() ? reply.body.getAsJsonObject() : new JsonObject();
            if (reply.status < 200 || reply.status >= 300 || !peer.equals(string(relation, "id")) || !stamped(relation, expectedType)) {
                current.addProperty("phase", reply.status == 0 || reply.status >= 500 || (reply.status >= 200 && reply.status < 300) ? "uncertain" : "error");
                current.addProperty("error", "Couldn't confirm temporary friendship. Review permissions or open the profile.");
                if (put(peer, current)) send(peer, "blocked"); return;
            }
            current.addProperty("type", expectedType); current.addProperty("since", string(relation, "since"));
            current.addProperty("phase", phase.equals("creating") ? "wait" : "accepted");
            if (put(peer, current)) send(peer, phase.equals("creating") ? "requested" : "accepted");
        });
    }

    private void request(String peer, String phase, String method, String path, JsonObject body, Consumer<Reply> done) {
        if (closed || !online || !error.isEmpty()) return;
        busy = peer; deadline = now + 45;
        long serial = ++flight;
        String lease = string(record(peer), "lease");
        provider.request(method, path, body, reply -> {
            checkThread();
            if (closed || serial != flight) return;
            ++flight; busy = "";
            JsonObject current = record(peer);
            if (!lease.equals(string(current, "lease")) || !phase.equals(string(current, "phase")) || !error.isEmpty()) return;
            done.accept(reply == null ? new Reply(0, null, 0) : reply);
        });
    }

    private void release(String peer) {
        JsonObject r = record(peer);
        String phase = string(r, "phase");
        if (oneOf(phase, "keep", "kept")) { send(peer, flag(r, "host") ? "kept" : "keep"); return; }
        if (recovering || busy.equals(peer) || held.contains(peer)
                || (members.contains(peer) && !room.equals(string(r, "room")))) r.addProperty("releaseRequested", true);
        else { r.addProperty("phase", "released"); r.remove("releaseRequested"); }
        if (put(peer, r) && !flag(r, "releaseRequested")) send(peer, "remove-ok");
    }

    private void fail(String peer, String message) {
        JsonObject r = record(peer); r.addProperty("phase", "error"); r.addProperty("error", message);
        if (put(peer, r)) send(peer, "blocked");
    }

    private boolean put(String peer, JsonObject value) {
        if (closed || !error.isEmpty()) return false;
        JsonObject copy = records.deepCopy();
        if (value == null) copy.remove(peer);
        else {
            if (oneOf(string(value, "phase"), "kept", "gone", "done") && !value.has("ended")) value.addProperty("ended", now);
            copy.add(peer, value.deepCopy());
        }
        if (copy.equals(records)) return true;
        JsonObject snapshot = object("self", self); snapshot.addProperty("v", 1); snapshot.add("contacts", copy);
        try { journal.save(snapshot); }
        catch (IOException | RuntimeException failure) { error = "Couldn't save temporary contact history. Automatic changes stopped."; return false; }
        records = copy;
        return true;
    }

    private void send(String peer, String operation) {
        if (closed || !online || !error.isEmpty()) return;
        JsonObject r = record(peer);
        if (r.size() == 0) return;
        JsonObject packet = object("ns", "org.traineros.lobby");
        packet.addProperty("v", 1); packet.addProperty("kind", "contact");
        packet.addProperty("lobby", string(r, "room")); packet.addProperty("expires", now + 60);
        JsonObject data = object("op", operation); data.addProperty("lease", string(r, "lease")); packet.add("data", data);
        next.put(peer, now + 15);
        signaling.send(peer, packet);
    }

    private boolean active(String peer) { return room.equals(string(record(peer), "room")) && members.contains(self) && members.contains(peer); }
    private JsonObject record(String peer) { return records.has(peer) ? records.getAsJsonObject(peer).deepCopy() : new JsonObject(); }
    private void checkThread() { if (Thread.currentThread() != owner) throw new IllegalStateException("Use the contact actor's owner thread"); }
    @Override public void close() { checkThread(); closed = true; online = false; ++flight; busy = ""; }
    private static JsonObject lease(String lease, String room, boolean host) {
        JsonObject r = object("lease", lease); r.addProperty("room", room); r.addProperty("host", host); r.addProperty("phase", "baseline"); return r;
    }
    static JsonObject object(String key, String value) { JsonObject o = new JsonObject(); o.addProperty(key, value); return o; }
    static String string(JsonObject o, String key) {
        JsonElement v = o.get(key); return v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isString() ? v.getAsString() : "";
    }
    static long number(JsonObject o, String key) {
        try { JsonElement v = o.get(key); return v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber() ? v.getAsBigDecimal().longValueExact() : 0; }
        catch (RuntimeException ignored) { return 0; }
    }
    private static boolean flag(JsonObject o, String key) { JsonElement v = o.get(key); return v != null && v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean() && v.getAsBoolean(); }
    private static boolean oneOf(String value, String... options) { for (String option : options) if (value.equals(option)) return true; return false; }
    static boolean validId(String id) { return id != null && id.matches("[1-9][0-9]{0,19}"); }
    private static boolean validUuid(String id) { try { return id != null && UUID.fromString(id).toString().equalsIgnoreCase(id) && !UUID.fromString(id).equals(new UUID(0, 0)); } catch (IllegalArgumentException e) { return false; } }
    private static boolean stamped(JsonObject r, int type) {
        if (number(r, "type") != type) return false;
        try { OffsetDateTime.parse(string(r, "since")); return true; } catch (DateTimeParseException e) { return false; }
    }
}
