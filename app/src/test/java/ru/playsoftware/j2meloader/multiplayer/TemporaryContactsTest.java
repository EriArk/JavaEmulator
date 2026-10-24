/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.Assert.*;

public class TemporaryContactsTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    private static final class MemoryJournal implements TemporaryContacts.Journal {
        JsonObject data;
        boolean fail;
        @Override public JsonObject load() { return data == null ? null : data.deepCopy(); }
        @Override public void save(JsonObject snapshot) throws IOException {
            if (fail) throw new IOException("Disk full");
            data = snapshot.deepCopy();
        }
    }

    private final class Pair {
        TemporaryContacts host, guest;
        TemporaryContacts.Journal hj = new MemoryJournal(), gj = new MemoryJournal();
        JsonObject hr = new JsonObject(), gr = new JsonObject();
        long now = 1792800000, guestSkew;
        String room = UUID.randomUUID().toString();
        int posts, accepts, deletes, postStatus = 200, deleteStatus = 204;
        boolean dropCreate, dropGone, dropKeep, delayRead;
        Consumer<TemporaryContacts.Reply> delayed;
        Pair() { this(0); }
        Pair(long skew) { guestSkew=skew; reload(); enter(); }
        void reload() {
            if (host != null) { host.close(); guest.close(); }
            host = new TemporaryContacts("1", hj, (m,p,b,c) -> request(true,m,p,b,c),
                    (peer,packet) -> { assertEquals("2", peer); if (!dropGone || !op(packet).equals("gone")) guest.receive("1",packet); }, now);
            guest = new TemporaryContacts("2", gj, (m,p,b,c) -> request(false,m,p,b,c),
                    (peer,packet) -> { assertEquals("1", peer); if (!dropKeep || !op(packet).equals("keep")) host.receive("2",packet); }, now+guestSkew);
        }
        void enter() { context(host, true, false); context(guest, true, false); }
        void context(TemporaryContacts c, boolean online, boolean recovering) {
            c.context(room,"1",new HashSet<>(Arrays.asList("1","2")),online,recovering);
        }
        void leave() {
            host.context("","",Collections.emptySet(),true,false);
            guest.context("","",Collections.emptySet(),true,false);
        }
        void run(int seconds) { for (int i=0; i<seconds; i++) { now++; host.tick(now); guest.tick(now+guestSkew); } }
        String phase(boolean owner) {
            JsonObject state = (owner ? host : guest).state().getAsJsonObject("peers");
            String id = owner ? "2" : "1";
            return state.has(id) ? state.getAsJsonObject(id).get("phase").getAsString() : "";
        }
        void request(boolean owner, String method, String path, JsonObject body, Consumer<TemporaryContacts.Reply> done) {
            assertTrue(path.startsWith("/v1/users/@me/relationships"));
            if (method.equals("GET")) {
                JsonArray rows = new JsonArray(); JsonObject r = owner ? hr : gr;
                if (r.size()>0) rows.add(r.deepCopy());
                if (delayRead) { delayRead=false; delayed=ignored -> done.accept(reply(200,rows)); }
                else done.accept(reply(200,rows));
            } else if (method.equals("POST")) {
                assertTrue(owner); posts++;
                assertEquals("creating", phase(true));
                if (postStatus != 200) { done.accept(reply(postStatus,null)); return; }
                hr=relation("2",4,now); gr=relation("1",3,now);
                if (!dropCreate) done.accept(reply(200,hr.deepCopy()));
            } else if (method.equals("PUT")) {
                assertFalse(owner); assertEquals(1,body.get("type").getAsInt()); accepts++;
                assertEquals("accepting", phase(false));
                hr=relation("2",1,now); gr=relation("1",1,now); done.accept(reply(200,gr.deepCopy()));
            } else {
                assertEquals("DELETE",method); assertTrue(owner); deletes++;
                if (deleteStatus==204) { hr=new JsonObject(); gr=new JsonObject(); }
                done.accept(reply(deleteStatus,null));
            }
        }
    }

    private static String op(JsonObject p) { return p.getAsJsonObject("data").get("op").getAsString(); }
    private static TemporaryContacts.Reply reply(int code, JsonElement body) { return new TemporaryContacts.Reply(code,body,30); }
    private static JsonObject relation(String id,int type,long time) {
        JsonObject r=TemporaryContacts.object("id",id); r.addProperty("type",type); r.addProperty("since",Instant.ofEpochSecond(time).toString()); return r;
    }

    @Test public void independentClocksCanNegotiateTemporaryContact() {
        for(long skew:new long[]{-45,45}){
            Pair p=new Pair(skew);p.run(80);
            assertEquals(1,p.posts);assertEquals(1,p.accepts);
            assertEquals("friend",p.phase(true));assertEquals("friend",p.phase(false));
        }
    }

    @Test public void admittedRoundTripAndCleanup() {
        Pair p=new Pair(); p.run(80);
        assertEquals(1,p.posts); assertEquals(1,p.accepts); assertEquals("friend",p.phase(true)); assertEquals("friend",p.phase(false));
        p.enter(); p.run(20); assertEquals(0,p.deletes);
        p.leave(); p.run(80); assertEquals(1,p.deletes); assertEquals(0,p.hr.size()); assertEquals(0,p.gr.size());
    }
    @Test public void invitationsAndJoiningDoNotGrantAdmission() {
        Pair p=new Pair(); p.leave(); p.run(100); assertEquals(0,p.posts); assertEquals(0,p.accepts);
        p.host.context(p.room,"1",Collections.singleton("1"),true,false); p.run(30); assertEquals(0,p.posts);
    }
    @Test public void preexistingRelationshipsNeverOwned() {
        for (int type=1;type<=4;type++) {
            Pair p=new Pair(); p.hr=relation("2",type,p.now); p.gr=relation("1",type==3?4:type==4?3:type,p.now);
            p.run(80); p.leave(); p.run(80); assertEquals(0,p.posts); assertEquals(0,p.accepts); assertEquals(0,p.deletes);
        }
    }
    @Test public void keepFromEitherSideSurvivesLeave() {
        for (boolean owner : new boolean[]{true,false}) {
            Pair p=new Pair(); p.run(80); assertTrue(owner ? p.host.keep("2") : p.guest.keep("1"));
            p.run(30); p.leave(); p.run(80); assertEquals(0,p.deletes); assertEquals(1,p.hr.get("type").getAsInt());
        }
    }
    @Test public void lostKeepRetriesBeforeCleanup() {
        Pair p=new Pair(); p.run(80); p.dropKeep=true; assertTrue(p.guest.keep("1"));
        p.leave(); p.run(50); assertEquals(0,p.deletes); assertEquals("keep",p.phase(false));
        p.dropKeep=false; p.run(50); assertEquals(0,p.deletes); assertEquals("",p.phase(false));
    }
    @Test public void diskJournalRoundTripRecovery() throws Exception {
        Pair p=new Pair(); p.hj=new FileContactJournal(folder.getRoot().toPath(),"1"); p.gj=new FileContactJournal(folder.getRoot().toPath(),"2");
        p.reload(); p.enter(); p.run(80); p.reload(); p.leave(); p.run(80);
        assertEquals(1,p.posts); assertEquals(1,p.accepts); assertEquals(1,p.deletes);
    }
    @Test public void lostCreationReplyNeverAdoptsRelationship() {
        Pair p=new Pair(); p.dropCreate=true; p.run(100); assertEquals("uncertain",p.phase(true));
        p.leave(); p.run(80); p.reload(); p.leave(); p.run(80);
        assertEquals(1,p.posts); assertEquals(0,p.accepts); assertEquals(0,p.deletes);
    }
    @Test public void reloadWhileMutationPendingIsUncertain() {
        Pair p=new Pair(); p.dropCreate=true; p.run(10); assertEquals(1,p.posts);
        p.reload(); p.leave(); p.run(80); assertEquals("uncertain",p.phase(true)); assertEquals(0,p.deletes);
    }
    @Test public void changedRelationshipOrBlockSurvivesCleanup() {
        for (int type : new int[]{1,2}) {
            Pair p=new Pair(); p.run(80); p.hr=relation("2",type,p.now); p.leave(); p.run(80);
            assertEquals(0,p.deletes); assertEquals("uncertain",p.phase(true));
        }
    }
    @Test public void offlinePeerAndLostFinalAckDelayCleanup() {
        Pair p=new Pair(); p.run(80); p.leave(); p.guest.context("","",Collections.emptySet(),false,false);
        p.run(40); assertEquals(0,p.deletes);
        p.guest.context("","",Collections.emptySet(),true,false); p.dropGone=true; p.run(40); assertEquals(1,p.deletes);
        p.dropGone=false; p.run(40); assertEquals(1,p.deletes); assertEquals("",p.phase(false));
    }
    @Test public void rateLimitsAndFailedDeleteRecheckOwnership() {
        Pair p=new Pair(); p.postStatus=429; p.run(20); assertEquals(1,p.posts);
        p.postStatus=200; p.run(80); assertEquals(2,p.posts);
        p.deleteStatus=503; p.leave(); p.run(20); assertEquals(1,p.deletes);
        p.hr=relation("2",2,p.now); p.deleteStatus=204; p.run(80); assertEquals(1,p.deletes);
    }
    @Test public void oldCallbackCannotMutateClosedAccount() {
        Pair p=new Pair(); p.run(80); p.leave(); p.delayRead=true; p.run(18); assertNotNull(p.delayed);
        assertFalse(p.host.keep("2")); p.host.close(); p.delayed.accept(null); p.run(80); assertEquals(0,p.deletes);
    }
    @Test public void unwritableJournalPreventsProviderMutation() {
        Pair p=new Pair(); ((MemoryJournal)p.hj).fail=true; p.run(100);
        assertEquals(0,p.posts); assertFalse(p.host.state().get("error").getAsString().isEmpty());
    }
    @Test public void wrongAccountJournalStopsAutomation() {
        Pair p=new Pair(); p.run(80); p.gj=p.hj; p.reload(); p.enter(); p.run(80);
        assertFalse(p.guest.state().get("error").getAsString().isEmpty()); assertEquals(1,p.posts);
    }
    @Test public void holdAndRecoveryDeferCleanup() {
        Pair p=new Pair(); p.run(80); p.guest.hold(Collections.singleton("1")); p.leave(); p.run(50); assertEquals(0,p.deletes);
        p.context(p.guest,true,true); p.guest.hold(Collections.emptySet()); p.run(50); assertEquals(0,p.deletes);
        p.leave(); p.run(80); assertEquals(1,p.deletes);
    }
    @Test public void forgedExpiredOrOldLeaseCannotRelease() {
        Pair p=new Pair(); p.run(80);
        JsonObject packet=TemporaryContacts.object("ns","org.traineros.lobby"); packet.addProperty("v",1); packet.addProperty("kind","contact");
        packet.addProperty("lobby",p.room); packet.addProperty("expires",p.now+60);
        JsonObject data=TemporaryContacts.object("op","remove-ok"); data.addProperty("lease",UUID.randomUUID().toString()); packet.add("data",data);
        p.host.receive("2",packet); p.host.receive("3",packet); p.run(20); assertEquals(0,p.deletes); assertEquals("friend",p.phase(true));
        packet.addProperty("expires",p.now-1); p.host.receive("2",packet); p.run(20); assertEquals(0,p.deletes);
    }
    @Test public void corruptDiskJournalIsNotOverwritten() throws Exception {
        java.nio.file.Path root=folder.getRoot().toPath(); FileContactJournal j=new FileContactJournal(root,"1");
        java.nio.file.Files.write(root.resolve("1/temporary-contacts.json"),new byte[]{'{'});
        TemporaryContacts c=new TemporaryContacts("1",j,(m,p,b,r)->fail("No network"),(p,m)->fail("No signal"),1);
        c.tick(2); assertFalse(c.state().get("error").getAsString().isEmpty());
        assertEquals(1,java.nio.file.Files.size(root.resolve("1/temporary-contacts.json")));
    }
    @Test public void delayedReadAfterGoingOfflineDoesNotStrandMutationIntent() {
        Pair p=new Pair(); p.delayRead=true; p.run(1); assertNotNull(p.delayed);
        p.context(p.host,false,false); p.delayed.accept(null); p.run(20); assertEquals(0,p.posts);
        p.enter(); p.run(80); assertEquals(1,p.posts); assertEquals("friend",p.phase(true));
    }
    @Test public void malformedRelationshipIsNotTreatedAsNoRelationship() {
        Pair p=new Pair(); p.hr.addProperty("id","2"); p.hr.addProperty("type","blocked");
        p.run(80); assertEquals(0,p.posts); assertEquals("error",p.phase(true));
    }
    @Test public void reusedPairInAnotherRoomKeepsContactUntilBothLeave() {
        Pair p=new Pair(); p.run(80); p.room=UUID.randomUUID().toString(); p.enter(); p.run(80);
        assertEquals(1,p.posts); assertEquals(0,p.deletes);
        p.leave(); p.run(80); assertEquals(1,p.deletes);
    }
    @Test public void manualHostActionPermanentlyRelinquishesCleanup() {
        Pair p=new Pair();p.run(80);assertTrue(p.host.manual("2"));
        p.leave();p.run(80);assertEquals(0,p.deletes);
        p.reload();p.leave();p.run(80);assertEquals(0,p.deletes);
    }
    @Test public void manualGuestWaitsForHostAcknowledgement() {
        Pair p=new Pair();p.run(80);p.dropKeep=true;
        assertFalse(p.guest.manual("1"));assertEquals("keep",p.phase(false));
        p.leave();p.run(40);assertEquals(0,p.deletes);
        p.dropKeep=false;p.run(40);assertTrue(p.guest.manual("1"));assertEquals(0,p.deletes);
    }
    @Test public void manualActionCannotRaceReadOrCleanup() {
        Pair p=new Pair();p.delayRead=true;p.run(1);assertNotNull(p.delayed);
        assertFalse(p.host.manual("2"));assertFalse(p.host.manual(""));
        p.delayed.accept(null);p.run(80);p.leave();p.delayRead=true;p.run(18);
        assertFalse(p.host.manual("2"));assertFalse(p.host.manual(""));
    }
    @Test public void failedKeepPersistenceCannotAuthorizeManualMutation() {
        Pair p=new Pair();p.run(80);((MemoryJournal)p.hj).fail=true;
        assertFalse(p.host.manual("2"));assertFalse(p.host.manual(""));
        assertFalse(p.host.state().get("error").getAsString().isEmpty());
    }
    @Test public void recoveryLeasePausesAutomaticMutations() {
        Pair p=new Pair();p.context(p.host,true,true);p.run(80);assertEquals(0,p.posts);
        p.enter();p.run(80);assertEquals(1,p.posts);
        assertTrue(p.host.trackedPeers().contains("2"));
        p.host.trackedPeers().clear();assertTrue(p.host.trackedPeers().contains("2"));
    }
    @Test public void manualRecoveryOfExistingRequestIsNotStrandedByBlockedPhase() {
        Pair p=new Pair();p.hr=relation("2",4,p.now);p.gr=relation("1",3,p.now);p.run(40);
        assertTrue(p.host.manual("2"));p.run(20);p.leave();p.run(40);assertEquals(0,p.deletes);
    }
    @Test public void keepCannotAcknowledgeWhileProviderCreationIsStillInFlight() {
        Pair p=new Pair();p.dropCreate=true;p.run(10);assertEquals("creating",p.phase(true));
        JsonObject packet=TemporaryContacts.object("ns","org.traineros.lobby");packet.addProperty("v",1);packet.addProperty("kind","contact");packet.addProperty("lobby",p.room);packet.addProperty("expires",p.now+60);
        JsonObject data=TemporaryContacts.object("op","keep");data.addProperty("lease",((MemoryJournal)p.hj).data.getAsJsonObject("contacts").getAsJsonObject("2").get("lease").getAsString());packet.add("data",data);
        p.host.receive("2",packet);assertEquals("creating",p.phase(true));assertFalse(p.guest.manual("1"));assertFalse(p.host.manual("2"));
    }
}
