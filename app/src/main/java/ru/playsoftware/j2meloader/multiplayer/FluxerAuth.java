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
    public enum Notice {
        SIGNED_OUT("Not signed in"),
        UNLOCK_FAILED("Saved sign-in could not be unlocked. Remove it from this device to sign in again."),
        CONNECTING("Connecting to Fluxer..."),
        SERVICE_FAILED("Could not verify the Fluxer service. Try again."),
        START_FAILED("Could not start sign-in. Try again."),
        INVALID_EXPIRY("Invalid sign-in expiry. Try again."),
        EXPIRED("Sign-in code expired. Try again."),
        APPROVAL("Enter this code on Fluxer and approve sign-in."),
        BUSY("Fluxer is busy. Please wait before trying again."),
        INVALID_SESSION("Fluxer returned an invalid session. Try again."),
        DECLINED("Sign-in was declined."),
        WAITING("Waiting for Fluxer. Retrying automatically..."),
        COMPLETE_FAILED("Could not complete sign-in. Try again."),
        CHECKING("Checking your account..."),
        SESSION_EXPIRED("Your session expired. Sign in again."),
        VERIFY_FAILED("Could not verify your account. Your sign-in has not been removed."),
        SAVED("Signed in. Your session is saved securely on this device."),
        SAVE_FAILED("Signed in for now, but the session could not be saved. Retry saving before leaving."),
        CANCELLED("Sign-in cancelled."),
        SIGNING_OUT("Signing out..."),
        SIGNOUT_FAILED("Could not sign out of Fluxer. Retry, or remove the saved sign-in from this device."),
        REMOVE_FAILED("Saved sign-in could not be removed. Please try again.");
        public final String english;
        Notice(String english) { this.english = english; }
    }
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
        public final Notice notice;
        public final String message, code, name, username;
        public final boolean remembered, hasSession;
        public final long expiresAt, retryAt;
        private State(FluxerAuth a) {
            phase = a.phase; notice = a.notice; message = a.notice.english; code = a.code;
            name = a.name; username = a.username; remembered = a.remembered;
            hasSession = a.token != null; expiresAt = a.expiresAt; retryAt = a.retryAt;
        }
    }
    private final Transport transport;
    private final Store store;
    private final LongSupplier clock;
    private final Consumer<State> observer;
    private Phase phase = Phase.SIGNED_OUT;
    private Notice notice = Notice.SIGNED_OUT;
    private String code = "", secret, token, name = "", username = "";
    private boolean remembered, closed, polling, initialized;
    private long epoch, expiresAt, nextPoll, retryAt;

    public FluxerAuth(Transport transport, Store store, LongSupplier clock, Consumer<State> observer) {
        this.transport = transport; this.store = store; this.clock = clock; this.observer = observer;
    }
    public State state() { return new State(this); }
    private void show(Phase next, Notice text) { phase = next; notice = text; observer.accept(state()); }
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
        catch (Exception e) { show(Phase.ERROR, Notice.UNLOCK_FAILED); return; }
        if (token == null) show(Phase.SIGNED_OUT, Notice.SIGNED_OUT); else verify(++epoch);
    }

    public void begin() {
        if (closed || token != null || phase != Phase.SIGNED_OUT
                || clock.getAsLong() < retryAt) return;
        long generation = ++epoch;
        show(Phase.STARTING, Notice.CONNECTING);
        transport.request("GET", "/.well-known/fluxer", null, null, reply -> {
            if (!current(generation)) return;
            JsonObject endpoints = object(reply.body, "endpoints");
            if (!reply.ok() || !ORIGIN.equals(string(endpoints, "api_public"))
                    || !"wss://gateway.fluxer.app".equals(string(endpoints, "gateway"))) {
                failedStart(reply, Notice.SERVICE_FAILED); return;
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
                if (!initiated.ok() || !valid) { failedStart(initiated, Notice.START_FAILED); return; }
                long deadline;
                try { deadline = Instant.parse(string(initiated.body, "expires_at")).toEpochMilli(); }
                catch (RuntimeException e) { deleteHandoff(receivedCode, receivedSecret); failedStart(initiated, Notice.INVALID_EXPIRY); return; }
                if (deadline <= clock.getAsLong()) {
                    deleteHandoff(receivedCode, receivedSecret); failedStart(initiated, Notice.EXPIRED); return;
                }
                String normalized = receivedCode.replace("-", "");
                code = normalized.substring(0,6) + "-" + normalized.substring(6);
                secret = receivedSecret;
                expiresAt = Math.min(deadline, clock.getAsLong() + 300000);
                nextPoll = clock.getAsLong() + 2200;
                show(Phase.WAITING, Notice.APPROVAL);
            });
        });
    }

    private void failedStart(Reply reply, Notice text) {
        retryAt = clock.getAsLong() + (reply.status == 429 ? backoff(reply) : 0);
        show(Phase.SIGNED_OUT, reply.status == 429 ? Notice.BUSY : text);
    }

    public void tick() {
        if (closed || phase != Phase.WAITING) return;
        if (clock.getAsLong() >= expiresAt) { cancel(); show(Phase.SIGNED_OUT, Notice.EXPIRED); return; }
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
                cancel(); show(Phase.SIGNED_OUT, Notice.EXPIRED); return;
            }
            polling = false; nextPoll = clock.getAsLong() + (reply.status == 429 ? backoff(reply) : 2200);
            String status = string(reply.body, "status");
            if (reply.ok() && "completed".equals(status)) {
                String received = string(reply.body, "token");
                if (!validToken(received)) { cancel(); show(Phase.SIGNED_OUT, Notice.INVALID_SESSION); return; }
                token = received; clearHandoff(); verify(generation);
            } else if (reply.status == 404 || reply.status == 410 || "expired".equals(status) || "denied".equals(status)) {
                cancel(); show(Phase.SIGNED_OUT, "denied".equals(status) ? Notice.DECLINED : Notice.EXPIRED);
            } else if (reply.ok() && "pending".equals(status)) {
                show(Phase.WAITING, Notice.APPROVAL);
            } else if (reply.status == 0 || reply.status == 429 || reply.status >= 500) {
                show(Phase.WAITING, Notice.WAITING);
            } else { cancel(); show(Phase.SIGNED_OUT, Notice.COMPLETE_FAILED); }
        });
    }

    private void verify(long generation) {
        show(Phase.VERIFYING, Notice.CHECKING);
        transport.request("GET", "/v1/users/@me", null, token, reply -> {
            if (!current(generation)) return;
            if (reply.status == 401) { forget(); if (phase == Phase.SIGNED_OUT) show(Phase.SIGNED_OUT, Notice.SESSION_EXPIRED); return; }
            String id = string(reply.body, "id"), user = string(reply.body, "username");
            if (!reply.ok() || !id.matches("[0-9]{1,30}") || user.isEmpty() || user.length() > 100) {
                retryAt = clock.getAsLong() + (reply.status == 429 ? backoff(reply) : 0);
                show(Phase.OFFLINE, Notice.VERIFY_FAILED); return;
            }
            username = user;
            String display = string(reply.body, "global_name");
            name = display.isEmpty() || display.length() > 100 ? user : display;
            if (remembered) show(Phase.CONNECTED, Notice.SAVED);
            else remember();
        });
    }

    private void remember() {
        try { store.save(token); remembered = true; show(Phase.CONNECTED, Notice.SAVED); }
        catch (Exception e) { remembered = false; show(Phase.CONNECTED, Notice.SAVE_FAILED); }
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
        clearHandoff(); show(Phase.SIGNED_OUT, Notice.CANCELLED);
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
        show(Phase.SIGNING_OUT, Notice.SIGNING_OUT);
        transport.request("POST", "/v1/auth/logout", new JsonObject(), token, reply -> {
            if (!current(generation)) return;
            if (reply.ok() || reply.status == 401) forget();
            else {
                retryAt = clock.getAsLong() + (reply.status == 429 ? backoff(reply) : 0);
                show(Phase.OFFLINE, Notice.SIGNOUT_FAILED);
            }
        });
    }
    public void forget() {
        if (closed) return;
        ++epoch;
        if (secret != null) deleteHandoff(code, secret);
        clearHandoff();
        try { store.clear(); }
        catch (Exception e) { show(Phase.ERROR, Notice.REMOVE_FAILED); return; }
        token = null; remembered = false; name = username = ""; retryAt = 0;
        show(Phase.SIGNED_OUT, Notice.SIGNED_OUT);
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
