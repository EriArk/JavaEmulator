/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Owner-thread account actor. Secrets never enter its UI snapshots. */
public final class FluxerAuth implements AutoCloseable {
    public static final String ORIGIN = "https://api.fluxer.app";
    public static final String LOGIN_URL = "https://web.fluxer.app/login?handoff=1";
    public enum Phase { SIGNED_OUT, STARTING, WAITING, VERIFYING, CONNECTED, OFFLINE, SIGNING_OUT, ERROR }
    public interface Transport {
        void request(String method, String path, JsonObject body, String token, Consumer<Reply> done);
    }
    public interface Store {
        String read() throws Exception;
        void save(String token) throws Exception;
        void clear() throws Exception;
    }
    public static final class Reply {
        public final int status;
        public final JsonObject body;
        public final JsonElement data;
        public final long retryMillis;
        public Reply(int status, JsonElement data, long retryMillis) {
            this.status = status; this.data = data == null ? new JsonObject() : data;
            this.body = this.data.isJsonObject() ? this.data.getAsJsonObject() : new JsonObject();
            this.retryMillis = retryMillis;
        }
        boolean ok() { return status >= 200 && status < 300; }
    }
    public static final class State {
        public final Phase phase;
        public final String message, code, name, username;
        public final boolean remembered, hasSession;
        public final long expiresAt, retryAt;
        private State(FluxerAuth a) {
            phase = a.phase; message = a.message; code = a.code;
            name = a.name; username = a.username; remembered = a.remembered;
            hasSession = a.token != null; expiresAt = a.expiresAt; retryAt = a.retryAt;
        }
    }
    private final Transport transport;
    private final Store store;
    private final LongSupplier clock;
    private final Consumer<State> observer;
    private Phase phase = Phase.SIGNED_OUT;
    private String message = "Not signed in", code = "", secret, token, name = "", username = "";
    private boolean remembered, closed, polling, initialized;
    private long epoch, expiresAt, nextPoll, retryAt;

    public FluxerAuth(Transport transport, Store store, LongSupplier clock, Consumer<State> observer) {
        this.transport = transport; this.store = store; this.clock = clock; this.observer = observer;
    }
    public State state() { return new State(this); }
    private void show(Phase next, String text) { phase = next; message = text; observer.accept(state()); }
    private boolean current(long generation) { return !closed && generation == epoch; }
    private long backoff(Reply r) { return Math.max(2200, Math.min(300000, r.retryMillis)); }

    public void restore() {
        if (closed || initialized) return;
        initialized = true;
        try {
            token = store.read();
            if (token != null && !validToken(token)) { token = null; throw new IllegalArgumentException("Invalid stored session"); }
            remembered = token != null;
        }
        catch (Exception e) { show(Phase.ERROR, "Saved sign-in could not be unlocked. Remove it from this device to sign in again."); return; }
        if (token == null) show(Phase.SIGNED_OUT, "Not signed in"); else verify(++epoch);
    }

    public void begin() {
        if (closed || token != null || phase != Phase.SIGNED_OUT
                || clock.getAsLong() < retryAt) return;
        long generation = ++epoch;
        show(Phase.STARTING, "Connecting to Fluxer...");
        transport.request("GET", "/.well-known/fluxer", null, null, reply -> {
            if (!current(generation)) return;
            JsonObject endpoints = object(reply.body, "endpoints");
            if (!reply.ok() || !ORIGIN.equals(string(endpoints, "api_public"))
                    || !"wss://gateway.fluxer.app".equals(string(endpoints, "gateway"))) {
                failedStart(reply, "Could not verify the Fluxer service. Try again."); return;
            }
            transport.request("POST", "/v1/auth/handoff/initiate", new JsonObject(), null, initiated -> {
                String receivedCode = string(initiated.body, "code");
                String receivedSecret = string(initiated.body, "poll_secret");
                boolean valid = receivedCode.matches("[A-Z2-9]{6}-?[A-Z2-9]{6}")
                        && receivedSecret.matches("[A-Za-z0-9_-]{20,256}");
                if (!current(generation)) {
                    if (valid) deleteHandoff(receivedCode, receivedSecret);
                    return;
                }
                if (!initiated.ok() || !valid) { failedStart(initiated, "Could not start sign-in. Try again."); return; }
                long deadline;
                try { deadline = Instant.parse(string(initiated.body, "expires_at")).toEpochMilli(); }
                catch (RuntimeException e) { deleteHandoff(receivedCode, receivedSecret); failedStart(initiated, "Invalid sign-in expiry. Try again."); return; }
                if (deadline <= clock.getAsLong()) {
                    deleteHandoff(receivedCode, receivedSecret); failedStart(initiated, "Sign-in code expired. Try again."); return;
                }
                String normalized = receivedCode.replace("-", "");
                code = normalized.substring(0,6) + "-" + normalized.substring(6);
                secret = receivedSecret;
                expiresAt = Math.min(deadline, clock.getAsLong() + 300000);
                nextPoll = clock.getAsLong() + 2200;
                show(Phase.WAITING, "Enter this code on Fluxer and approve sign-in.");
            });
        });
    }

    private void failedStart(Reply reply, String text) {
        retryAt = clock.getAsLong() + (reply.status == 429 ? backoff(reply) : 0);
        show(Phase.SIGNED_OUT, reply.status == 429 ? "Fluxer is busy. Please wait before trying again." : text);
    }

    public void tick() {
        if (closed || phase != Phase.WAITING) return;
        if (clock.getAsLong() >= expiresAt) { cancel(); show(Phase.SIGNED_OUT, "Sign-in code expired. Try again."); return; }
        if (polling || clock.getAsLong() < nextPoll) return;
        polling = true;
        long generation = epoch;
        JsonObject body = new JsonObject(); body.addProperty("poll_secret", secret);
        transport.request("POST", "/v1/auth/handoff/" + code + "/status", body, null, reply -> {
            if (!current(generation)) {
                String abandoned = string(reply.body, "token");
                if (validToken(abandoned)) revoke(abandoned);
                return;
            }
            if (clock.getAsLong() >= expiresAt) {
                String abandoned = string(reply.body, "token");
                if (validToken(abandoned)) revoke(abandoned);
                cancel(); show(Phase.SIGNED_OUT, "Sign-in code expired. Try again."); return;
            }
            polling = false; nextPoll = clock.getAsLong() + (reply.status == 429 ? backoff(reply) : 2200);
            String status = string(reply.body, "status");
            if (reply.ok() && "completed".equals(status)) {
                String received = string(reply.body, "token");
                if (!validToken(received)) { cancel(); show(Phase.SIGNED_OUT, "Fluxer returned an invalid session. Try again."); return; }
                token = received; clearHandoff(); verify(generation);
            } else if (reply.status == 404 || reply.status == 410 || "expired".equals(status) || "denied".equals(status)) {
                cancel(); show(Phase.SIGNED_OUT, "denied".equals(status) ? "Sign-in was declined." : "Sign-in code expired. Try again.");
            } else if (reply.ok() && "pending".equals(status)) {
                show(Phase.WAITING, "Enter this code on Fluxer and approve sign-in.");
            } else if (reply.status == 0 || reply.status == 429 || reply.status >= 500) {
                show(Phase.WAITING, "Waiting for Fluxer. Retrying automatically...");
            } else { cancel(); show(Phase.SIGNED_OUT, "Could not complete sign-in. Try again."); }
        });
    }

    private void verify(long generation) {
        show(Phase.VERIFYING, "Checking your account...");
        transport.request("GET", "/v1/users/@me", null, token, reply -> {
            if (!current(generation)) return;
            if (reply.status == 401) { forget(); if (phase == Phase.SIGNED_OUT) show(Phase.SIGNED_OUT, "Your session expired. Sign in again."); return; }
            String id = string(reply.body, "id"), user = string(reply.body, "username");
            if (!reply.ok() || !id.matches("[0-9]{1,30}") || user.isEmpty() || user.length() > 100) {
                retryAt = clock.getAsLong() + (reply.status == 429 ? backoff(reply) : 0);
                show(Phase.OFFLINE, "Could not verify your account. Your sign-in has not been removed."); return;
            }
            username = user;
            String display = string(reply.body, "global_name");
            name = display.isEmpty() || display.length() > 100 ? user : display;
            if (remembered) show(Phase.CONNECTED, "Signed in. Your session is saved securely on this device.");
            else remember();
        });
    }

    private void remember() {
        try { store.save(token); remembered = true; show(Phase.CONNECTED, "Signed in. Your session is saved securely on this device."); }
        catch (Exception e) { remembered = false; show(Phase.CONNECTED, "Signed in for now, but the session could not be saved. Retry saving before leaving."); }
    }
    public void retry() {
        if (closed || clock.getAsLong() < retryAt) return;
        if (phase == Phase.CONNECTED && !remembered) remember();
        else if (phase == Phase.OFFLINE && token != null) verify(++epoch);
    }
    public void revalidate() {
        if (!closed && phase == Phase.CONNECTED && remembered) verify(++epoch);
    }

    public void cancel() {
        if (closed || (phase != Phase.STARTING && phase != Phase.WAITING)) return;
        ++epoch;
        if (secret != null) deleteHandoff(code, secret);
        clearHandoff(); show(Phase.SIGNED_OUT, "Sign-in cancelled.");
    }
    private void clearHandoff() { code = ""; secret = null; expiresAt = 0; polling = false; }
    private void deleteHandoff(String code, String secret) {
        JsonObject body = new JsonObject(); body.addProperty("poll_secret", secret);
        transport.request("DELETE", "/v1/auth/handoff/" + code, body, null, ignored -> {});
    }
    private void revoke(String value) { transport.request("POST", "/v1/auth/logout", new JsonObject(), value, ignored -> {}); }

    public void signOut() {
        if (closed || token == null || (phase != Phase.CONNECTED && phase != Phase.OFFLINE) || clock.getAsLong() < retryAt) return;
        long generation = ++epoch;
        show(Phase.SIGNING_OUT, "Signing out...");
        transport.request("POST", "/v1/auth/logout", new JsonObject(), token, reply -> {
            if (!current(generation)) return;
            if (reply.ok() || reply.status == 401) forget();
            else {
                retryAt = clock.getAsLong() + (reply.status == 429 ? backoff(reply) : 0);
                show(Phase.OFFLINE, "Could not sign out of Fluxer. Retry, or remove the saved sign-in from this device.");
            }
        });
    }
    public void forget() {
        if (closed) return;
        ++epoch;
        if (secret != null) deleteHandoff(code, secret);
        clearHandoff();
        try { store.clear(); }
        catch (Exception e) { show(Phase.ERROR, "Saved sign-in could not be removed. Please try again."); return; }
        token = null; remembered = false; name = username = ""; retryAt = 0;
        show(Phase.SIGNED_OUT, "Not signed in");
    }
    @Override public void close() {
        if (closed) return;
        cancel(); ++epoch; closed = true;
        // An unpersisted session should not be left behind when its screen is discarded.
        if (token != null && !remembered) revoke(token);
        token = null; clearHandoff();
    }
    static boolean validToken(String value) { return value != null && value.matches("[\\x21-\\x7e]{16,8192}"); }
    static String string(JsonObject object, String key) {
        try { return object != null && object.has(key) && object.get(key).isJsonPrimitive()
                && object.getAsJsonPrimitive(key).isString() ? object.get(key).getAsString() : ""; }
        catch (RuntimeException ignored) { return ""; }
    }
    private static JsonObject object(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonObject() ? object.getAsJsonObject(key) : new JsonObject();
    }
}
