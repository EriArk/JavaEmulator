/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import static org.junit.Assert.*;
import static ru.playsoftware.j2meloader.multiplayer.FluxerAuth.Phase.*;

public class FluxerAuthTest {
    private static final String TOKEN = "test-session-token-not-a-real-secret";
    private static class Vault implements FluxerAuth.Store {
        String token; boolean failRead, failSave, failClear;
        public String read() throws Exception { if (failRead) throw new Exception(); return token; }
        public void save(String value) throws Exception { if (failSave) throw new Exception(); token = value; }
        public void clear() throws Exception { if (failClear) throw new Exception(); token = null; }
    }
    private static class Request {
        String method, path, token; JsonObject body; Consumer<FluxerAuth.Reply> done;
        void reply(int status, String json) { done.accept(new FluxerAuth.Reply(status, json == null ? null : JsonParser.parseString(json).getAsJsonObject(), 0)); }
    }
    private static class Fixture {
        final Vault vault = new Vault();
        final List<Request> requests = new ArrayList<>();
        long now = 1792800000000L;
        final FluxerAuth actor = new FluxerAuth((method,path,body,token,done) -> {
            Request r = new Request(); r.method=method; r.path=path; r.body=body; r.token=token; r.done=done; requests.add(r);
        }, vault, () -> now, ignored -> {});
        Request take(String path) { assertFalse(requests.isEmpty()); Request r=requests.remove(0); assertEquals(path,r.path); return r; }
        void start() {
            actor.restore(); actor.begin(); discovery();
        }
        void discovery() {
            take("/.well-known/fluxer").reply(200, "{\"endpoints\":{\"api_public\":\"https://api.fluxer.app\",\"gateway\":\"wss://gateway.fluxer.app\"}}");
        }
        String initiation() { return "{\"code\":\"ABCDEF-GHJKMN\",\"poll_secret\":\"dummy_poll_secret_for_fixture_only\",\"expires_at\":\"" + Instant.ofEpochMilli(now+300000) + "\"}"; }
        void waiting() { start(); take("/v1/auth/handoff/initiate").reply(200, initiation()); assertEquals(WAITING, actor.state().phase); }
        Request poll() { now+=2200; actor.tick(); return take("/v1/auth/handoff/ABCDEF-GHJKMN/status"); }
        void completed() { waiting(); poll().reply(200,"{\"status\":\"completed\",\"token\":\""+TOKEN+"\"}"); }
        void account() { take("/v1/users/@me").reply(200, "{\"id\":\"12345\",\"username\":\"test-user\",\"global_name\":\"Test Player\"}"); }
        void connected() { completed(); account(); }
    }
    @Test public void offlineLibraryDoesNotStartAnyRequest() {
        Fixture f=new Fixture(); f.actor.restore(); f.actor.tick(); assertTrue(f.requests.isEmpty()); assertEquals(SIGNED_OUT,f.actor.state().phase);
    }
    @Test public void storesOnlyAfterAccountVerificationAndSnapshotHasNoToken() {
        Fixture f=new Fixture(); f.completed(); assertNull(f.vault.token); assertEquals(VERIFYING,f.actor.state().phase);
        assertEquals(TOKEN,f.requests.get(0).token); f.account();
        assertEquals(TOKEN,f.vault.token); assertEquals(CONNECTED,f.actor.state().phase); assertTrue(f.actor.state().remembered);
        assertEquals("Test Player",f.actor.state().name); assertEquals("",f.actor.state().code);
        for (java.lang.reflect.Field field : FluxerAuth.State.class.getFields()) assertFalse(field.getName().matches("token|secret|pollSecret"));
    }
    @Test public void restoredAccountIsVerifiedBeforeConnected() {
        Fixture f=new Fixture(); f.vault.token=TOKEN; f.actor.restore(); assertEquals(VERIFYING,f.actor.state().phase); f.account(); assertEquals(CONNECTED,f.actor.state().phase);
    }
    @Test public void expiredStoredSessionIsRemoved() {
        Fixture f=new Fixture(); f.vault.token=TOKEN; f.actor.restore(); f.take("/v1/users/@me").reply(401,null);
        assertEquals(SIGNED_OUT,f.actor.state().phase); assertNull(f.vault.token);
    }
    @Test public void restoreNetworkFailureKeepsSavedSessionAndRetryWorks() {
        Fixture f=new Fixture(); f.vault.token=TOKEN; f.actor.restore(); f.take("/v1/users/@me").reply(0,null);
        assertEquals(OFFLINE,f.actor.state().phase); assertEquals(TOKEN,f.vault.token); f.actor.retry(); f.account(); assertEquals(CONNECTED,f.actor.state().phase);
    }
    @Test public void untrustedDiscoveryNeverReceivesCredentialsOrInitiates() {
        Fixture f=new Fixture(); f.actor.restore(); f.actor.begin();
        f.take("/.well-known/fluxer").reply(200,"{\"endpoints\":{\"api_public\":\"https://evil.invalid\"}}");
        assertEquals(SIGNED_OUT,f.actor.state().phase); assertTrue(f.requests.isEmpty());
    }
    @Test public void cancelledInitiationDeletesItsLateCode() {
        Fixture f=new Fixture(); f.start(); Request r=f.take("/v1/auth/handoff/initiate"); f.actor.cancel(); r.reply(200,f.initiation());
        assertEquals("DELETE",f.take("/v1/auth/handoff/ABCDEF-GHJKMN").method); assertEquals(SIGNED_OUT,f.actor.state().phase);
    }
    @Test public void cancelledPollCannotSignInAndRevokesLateToken() {
        Fixture f=new Fixture(); f.waiting(); Request poll=f.poll(); f.actor.cancel();
        f.take("/v1/auth/handoff/ABCDEF-GHJKMN"); poll.reply(200,"{\"status\":\"completed\",\"token\":\""+TOKEN+"\"}");
        assertEquals(TOKEN,f.take("/v1/auth/logout").token); assertEquals(SIGNED_OUT,f.actor.state().phase); assertNull(f.vault.token);
    }
    @Test public void closeDuringInitiationCancelsLateResponse() {
        Fixture f=new Fixture(); f.start(); Request r=f.take("/v1/auth/handoff/initiate"); f.actor.close(); r.reply(200,f.initiation());
        f.take("/v1/auth/handoff/ABCDEF-GHJKMN"); assertNull(f.vault.token);
    }
    @Test public void onePollAtATimeAndPendingReschedules() {
        Fixture f=new Fixture(); f.waiting(); Request first=f.poll(); f.now+=10000; f.actor.tick(); assertTrue(f.requests.isEmpty());
        first.reply(200,"{\"status\":\"pending\"}"); f.actor.tick(); assertTrue(f.requests.isEmpty()); f.poll();
    }
    @Test public void rateLimitDefersPollButNeverExtendsExpiry() {
        Fixture f=new Fixture(); f.waiting(); f.poll().done.accept(new FluxerAuth.Reply(429,null,60000));
        f.now+=30000; f.actor.tick(); assertTrue(f.requests.isEmpty());
        f.now+=30000; f.actor.tick(); f.take("/v1/auth/handoff/ABCDEF-GHJKMN/status");
        f.now+=300000; f.actor.tick(); assertEquals(SIGNED_OUT,f.actor.state().phase); f.take("/v1/auth/handoff/ABCDEF-GHJKMN");
    }
    @Test public void rateLimitDefersInitiationAndRetry() {
        Fixture f=new Fixture(); f.actor.restore(); f.actor.begin(); f.take("/.well-known/fluxer").done.accept(new FluxerAuth.Reply(429,null,60000));
        f.actor.begin(); assertTrue(f.requests.isEmpty()); f.now+=60000; f.actor.begin(); f.discovery();
    }
    @Test public void deniedHandoffReturnsToSignIn() {
        Fixture f=new Fixture(); f.waiting(); f.poll().reply(200,"{\"status\":\"denied\"}"); assertEquals(SIGNED_OUT,f.actor.state().phase); assertEquals("",f.actor.state().code);
    }
    @Test public void completionAfterExpiryIsNotAccepted() {
        Fixture f=new Fixture(); f.waiting(); Request r=f.poll(); f.now+=300000;
        r.reply(200,"{\"status\":\"completed\",\"token\":\""+TOKEN+"\"}");
        f.take("/v1/auth/logout"); f.take("/v1/auth/handoff/ABCDEF-GHJKMN"); assertEquals(SIGNED_OUT,f.actor.state().phase);
    }
    @Test public void invalidExpiryDeletesCodeAndInvalidAccountIsNotSaved() {
        Fixture f=new Fixture(); f.start(); f.take("/v1/auth/handoff/initiate").reply(200,f.initiation().replace(Instant.ofEpochMilli(f.now+300000).toString(),"bad"));
        f.take("/v1/auth/handoff/ABCDEF-GHJKMN"); assertEquals(SIGNED_OUT,f.actor.state().phase);
        f=new Fixture(); f.completed(); f.take("/v1/users/@me").reply(200,"{\"id\":{},\"username\":\"x\"}"); assertEquals(OFFLINE,f.actor.state().phase); assertNull(f.vault.token);
    }
    @Test public void failedStorageIsExplicitAndCanBeRetried() {
        Fixture f=new Fixture(); f.vault.failSave=true; f.connected(); assertEquals(CONNECTED,f.actor.state().phase); assertFalse(f.actor.state().remembered);
        f.vault.failSave=false; f.actor.retry(); assertTrue(f.actor.state().remembered); assertEquals(TOKEN,f.vault.token);
    }
    @Test public void unreadableStoreRequiresExplicitRemovalNotSilentOverwrite() {
        Fixture f=new Fixture(); f.vault.failRead=true; f.actor.restore(); assertEquals(ERROR,f.actor.state().phase);
        f.actor.begin(); assertTrue(f.requests.isEmpty()); f.actor.forget(); assertEquals(SIGNED_OUT,f.actor.state().phase);
    }
    @Test public void failedLogoutDoesNotPretendToSignOut() {
        Fixture f=new Fixture(); f.connected(); f.actor.signOut(); f.take("/v1/auth/logout").reply(500,null);
        assertEquals(OFFLINE,f.actor.state().phase); assertEquals(TOKEN,f.vault.token);
        f.actor.signOut(); f.take("/v1/auth/logout").reply(204,null); assertEquals(SIGNED_OUT,f.actor.state().phase); assertNull(f.vault.token);
    }
    @Test public void explicitLocalRemovalFencesVerification() {
        Fixture f=new Fixture(); f.vault.token=TOKEN; f.actor.restore(); Request verify=f.take("/v1/users/@me");
        f.actor.forget(); verify.reply(200,"{\"id\":\"1\",\"username\":\"old\"}"); assertNull(f.vault.token); assertEquals(SIGNED_OUT,f.actor.state().phase);
    }
    @Test public void failedDeletionIsVisibleAndDoesNotReportSignedOut() {
        Fixture f=new Fixture(); f.connected(); f.vault.failClear=true; f.actor.signOut(); f.take("/v1/auth/logout").reply(204,null);
        assertEquals(ERROR,f.actor.state().phase); f.vault.failClear=false; f.actor.forget(); assertEquals(SIGNED_OUT,f.actor.state().phase);
    }
    @Test public void closingAnUnsavedSessionRevokesItButSavedAccountSurvives() {
        Fixture f=new Fixture(); f.vault.failSave=true; f.connected(); f.actor.close(); f.take("/v1/auth/logout");
        f=new Fixture(); f.connected(); f.actor.close(); assertTrue(f.requests.isEmpty()); assertEquals(TOKEN,f.vault.token);
    }
    @Test public void injectionLikeTokenIsRejected() {
        assertFalse(FluxerAuth.validToken("this-is-a-token\r\nCookie: no")); assertFalse(FluxerAuth.validToken("short")); assertTrue(FluxerAuth.validToken(TOKEN));
    }
}
