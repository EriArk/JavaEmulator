/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import com.google.gson.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
import static ru.playsoftware.j2meloader.multiplayer.LobbyRoom.*;

public class LobbyRoomTest {
    static final class Mail {String from,to;JsonObject packet;Mail(String f,String t,JsonObject p){from=f;to=t;packet=p;}}
    static final class World {
        long now=1000;Map<String,LobbyRoom> clients=new HashMap<>();ArrayDeque<Mail> mail=new ArrayDeque<>();
        LobbyRoom add(String id){return add(id,0);}
        LobbyRoom add(String id,long skew){LobbyRoom r=new LobbyRoom(id,()->now+skew,Collections.singleton("j2me/link/1"),(peer,p)->mail.add(new Mail(id,peer,p)),()->{});clients.put(id,r);return r;}
        void deliver(){int bound=0;while(!mail.isEmpty()){assertTrue("Message loop",++bound<200);Mail m=mail.remove();LobbyRoom r=clients.get(m.to);if(r!=null)r.receive(m.from,m.packet);}}
        void join(LobbyRoom guest,LobbyRoom host){guest.join(host.host(),host.room(),"");deliver();}
    }
    static LobbySession.Game game(){return new LobbySession.Game("j2me",String.join("",Collections.nCopies(64,"a")),"link","1",4);}
    @Test public void independentDeviceClocksCanInviteJoinHeartbeatAndLeave(){
        for(long skew:new long[]{-45,-22,22,45}){
            World w=new World();LobbyRoom h=w.add("11",skew),g=w.add("22");
            h.create("private");h.invite("22");w.now+=2;w.deliver();
            assertEquals(1,g.invitations().size());g.answer(text(g.invitations().get(0),"key"),true);w.deliver();
            assertTrue(g.available());assertEquals(2,h.snapshot().getAsJsonArray("members").size());
            for(int i=0;i<4;i++){w.now+=20;h.tick();g.tick();w.deliver();assertTrue(g.available());assertTrue(bool(member(h.snapshot(),"22"),"connected"));}
            g.leave();w.deliver();assertEquals(1,h.snapshot().getAsJsonArray("members").size());
        }
    }
    @Test public void excessiveFutureEnvelopeAndOfferRemainRejected(){
        for(String field:new String[]{"expires","until"}){
            World w=new World();LobbyRoom h=w.add("11"),g=w.add("22");h.create("private");h.invite("22");Mail m=w.mail.remove();
            if(field.equals("expires"))m.packet.addProperty(field,w.now+151);
            else m.packet.getAsJsonObject("data").addProperty(field,w.now+361);
            g.receive("11",m.packet);assertTrue(g.invitations().isEmpty());
        }
    }
    @Test public void publicJoinNeedsAuthenticatedHostSnapshot(){World w=new World();LobbyRoom h=w.add("11"),g=w.add("22");h.create("public");g.join("11",h.room(),"");assertFalse(g.joined());w.deliver();assertTrue(g.joined());assertEquals(2,g.snapshot().getAsJsonArray("members").size());}
    @Test public void privateRoomRequiresRecipientBoundOffer(){World w=new World();LobbyRoom h=w.add("11"),g=w.add("22"),other=w.add("33");h.create("private");h.invite("22");w.deliver();assertEquals(1,g.invitations().size());String offer=text(g.invitations().get(0),"offer");other.join("11",h.room(),offer);w.deliver();assertFalse(other.joined());g.answer(text(g.invitations().get(0),"key"),true);w.deliver();assertTrue(g.joined());}
    @Test public void approvalDoesNotAdmitBeforeHostAccepts(){World w=new World();LobbyRoom h=w.add("11"),g=w.add("22");h.create("approval");w.join(g,h);assertFalse(g.joined());assertTrue(h.requests().containsKey("22"));h.approve("22",true);w.deliver();assertTrue(g.joined());}
    @Test public void approvalDeclineAndCancellationClearRequests(){World w=new World();LobbyRoom h=w.add("11"),g=w.add("22");h.create("approval");w.join(g,h);g.leave();w.deliver();assertTrue(h.requests().isEmpty());w.join(g,h);h.approve("22",false);w.deliver();assertEquals("",g.room());}
    @Test public void forgedHostCannotAdmitGuest(){World w=new World();LobbyRoom h=w.add("11"),g=w.add("22");h.create("public");g.join("11",h.room(),"");Mail join=w.mail.remove();h.receive(join.from,join.packet);Mail state=w.mail.remove();g.receive("33",state.packet);assertFalse(g.joined());g.receive("11",state.packet);assertTrue(g.joined());}
    @Test public void staleProbeCannotFinishJoin(){World w=new World();LobbyRoom h=w.add("11"),g=w.add("22");h.create("public");g.join("11",h.room(),"");Mail join=w.mail.remove();h.receive(join.from,join.packet);Mail state=w.mail.remove();state.packet.addProperty("reply",uuid());g.receive("11",state.packet);assertFalse(g.joined());}
    @Test public void duplicateLeaveCannotRemoveNewIncarnation(){World w=new World();LobbyRoom h=w.add("11"),g=w.add("22");h.create("public");w.join(g,h);g.leave();Mail leave=w.mail.remove();h.receive("22",leave.packet);w.deliver();w.join(g,h);assertTrue(g.joined());h.receive("22",leave.packet);assertEquals(2,h.snapshot().getAsJsonArray("members").size());}
    @Test public void expiryAndForeignScopeAreRejected(){World w=new World();LobbyRoom h=w.add("11"),g=w.add("22");h.create("private");h.invite("22");Mail m=w.mail.remove();m.packet.addProperty("ns","org.traineros.lobby");g.receive("11",m.packet);assertTrue(g.invitations().isEmpty());m.packet.addProperty("ns","org.abyssme.lobby");w.now+=100;g.receive("11",m.packet);assertTrue(g.invitations().isEmpty());}
    @Test public void inviteExpiresAndCloseRevokesIt(){World w=new World();LobbyRoom h=w.add("11"),g=w.add("22");h.create("private");h.invite("22");w.deliver();h.leave();w.deliver();assertTrue(g.invitations().isEmpty());h.create("private");h.invite("22");w.deliver();w.now+=301;g.tick();assertTrue(g.invitations().isEmpty());}
    @Test public void replayedAdmissionConsumesOldOfferWithoutRestoringMembership(){
        World w=new World();LobbyRoom h=w.add("11"),g=w.add("22");h.create("private");h.invite("22");
        Mail invitation=w.mail.peek();w.deliver();g.answer(text(g.invitations().get(0),"key"),true);
        Mail join=w.mail.remove();h.receive("22",join.packet);Mail admission=w.mail.remove();
        LobbyRoom fresh=w.add("22");fresh.receive("11",invitation.packet);assertEquals(1,fresh.invitations().size());
        fresh.receive("33",admission.packet);assertEquals(1,fresh.invitations().size());
        fresh.receive("11",admission.packet);assertTrue(fresh.invitations().isEmpty());assertFalse(fresh.joined());assertEquals("",fresh.room());
        fresh.receive("11",invitation.packet);assertTrue(fresh.invitations().isEmpty());
    }
    @Test public void terminalHistoryFencesOlderInvitesButNotNewHostOffers(){
        for(String kind:Arrays.asList("closed","removed")){
            World w=new World();LobbyRoom h=w.add("11"),g=w.add("22");h.create("private");h.invite("22");Mail invite=w.mail.remove();
            JsonObject terminal=invite.packet.deepCopy();terminal.addProperty("kind",kind);terminal.addProperty("serial",number(invite.packet,"serial")+1);
            g.receive("11",terminal);g.receive("11",invite.packet);assertTrue(g.invitations().isEmpty());
            LobbyRoom replacement=w.add("11");replacement.create("private");replacement.invite("22");w.deliver();assertEquals(1,g.invitations().size());
        }
    }
    @Test public void roomCloseReleasesMembersButNotApplicationAccount(){World w=new World();LobbyRoom h=w.add("11"),g=w.add("22");h.create("public");w.join(g,h);h.leave();w.deliver();assertEquals("",g.room());assertTrue(g.create("private"));}
    @Test public void removeIsHostOnlyAndGuestCanLeave(){World w=new World();LobbyRoom h=w.add("11"),g=w.add("22");h.create("public");w.join(g,h);g.remove("11");assertTrue(g.joined());h.remove("22");w.deliver();assertFalse(g.joined());}
    @Test public void readinessRequiresPreparedExactGameAndGeneration(){World w=new World();LobbyRoom h=w.add("11"),g=w.add("22");h.create("public");w.join(g,h);h.ready(true);assertFalse(bool(member(h.snapshot(),"11"),"ready"));assertTrue(h.select(game()));w.deliver();long generation=number(h.snapshot(),"generation");assertFalse(h.prepared(generation-1,game(),true));assertTrue(h.prepared(generation,game(),true));assertTrue(g.prepared(generation,game(),true));w.deliver();g.ready(true);h.ready(true);w.deliver();assertTrue(bool(member(h.snapshot(),"22"),"ready"));assertTrue(bool(member(g.snapshot(),"11"),"ready"));}
    @Test public void membershipChangeResetsAllReadiness(){World w=new World();LobbyRoom h=w.add("11"),g=w.add("22"),third=w.add("33");h.create("public");w.join(g,h);h.select(game());w.deliver();long gen=number(h.snapshot(),"generation");h.prepared(gen,game(),true);g.prepared(gen,game(),true);w.deliver();h.ready(true);g.ready(true);w.deliver();w.join(third,h);assertFalse(bool(member(h.snapshot(),"11"),"ready"));assertFalse(bool(member(h.snapshot(),"22"),"prepared"));assertFalse(h.prepared(gen,game(),true));}
    @Test public void noAdapterCannotSelectGame(){LobbyRoom r=new LobbyRoom("11",()->1000,Collections.emptySet(),(p,m)->{},()->{});r.create("public");assertFalse(r.select(game()));assertFalse(r.snapshot().has("game"));}
    @Test public void fullRoomAndCapabilityFitOneControlMessage(){
        String token=String.join("",Collections.nCopies(32,"a")),hash=String.join("",Collections.nCopies(64,"a"));
        LobbySession.Game supported=new LobbySession.Game(token,hash,token,token,16),oversize=new LobbySession.Game(token+"x",hash,token,token,16);
        World w=new World();String host="12345678901234567890";
        LobbyRoom h=new LobbyRoom(host,()->w.now,new HashSet<>(Arrays.asList(capability(supported),capability(oversize))),(peer,p)->{assertTrue(p.toString().length()<=1900);w.mail.add(new Mail(host,peer,p));},()->{});w.clients.put(host,h);h.create("public");
        assertFalse(h.select(oversize));assertTrue(h.select(supported));
        for(int i=1;i<=15;i++)w.join(w.add("223456789012345678"+String.format(java.util.Locale.ROOT,"%02d",i)),h);
        assertEquals(16,h.snapshot().getAsJsonArray("members").size());
    }
    @Test public void timeoutAndPauseInvalidateLiveState(){World w=new World();LobbyRoom h=w.add("11"),g=w.add("22");h.create("public");w.join(g,h);h.active(false);assertFalse(h.available());w.now+=46;g.tick();assertFalse(g.available());h.active(true);w.deliver();h.tick();w.deliver();assertTrue(g.available());}
    @Test public void joinTimeoutAllowsFreshAttempt(){World w=new World();LobbyRoom g=w.add("22");g.join("11",uuid(),"");w.now+=91;g.tick();assertEquals("",g.room());assertTrue(g.create("private"));}
    @Test public void staleDirectoryJoinGetsCorrelatedUnavailableWithoutLeakingCurrentRoom(){
        for(boolean replacement:new boolean[]{false,true}){
            World w=new World();LobbyRoom h=w.add("11"),g=w.add("22");h.create("public");String old=h.room();h.leave();w.deliver();
            if(replacement)h.create("private");String current=h.room();g.join("11",old,"");Mail join=w.mail.remove();
            h.receive(join.from,join.packet);Mail reply=w.mail.remove();assertEquals("unavailable",text(reply.packet,"kind"));
            assertEquals(old,text(reply.packet,"lobby"));assertEquals(0,object(reply.packet,"data").size());
            h.receive(join.from,join.packet);assertTrue("Do not amplify replays",w.mail.isEmpty());
            g.receive("11",reply.packet);assertEquals("",g.room());assertEquals("This room is no longer available. Refresh Lobbies.",g.notice());assertEquals(current,h.room());
            if(replacement){h.access("public");w.join(g,h);assertTrue(g.joined());}
        }
    }
    @Test public void unavailableCannotCancelAnotherProbeOrAdmittedMembership(){
        World w=new World();LobbyRoom h=w.add("11"),g=w.add("22");String old=uuid();g.join("11",old,"");
        Mail join=w.mail.remove();h.receive("22",join.packet);Mail unavailable=w.mail.remove();
        g.receive("33",unavailable.packet);assertEquals(old,g.room());
        g.leave();w.deliver();g.join("11",old,"");g.receive("11",unavailable.packet);assertEquals(old,g.room());assertFalse(g.joined());
        g.leave();w.deliver();h.create("public");w.join(g,h);assertTrue(g.joined());
        JsonObject forged=unavailable.packet.deepCopy();forged.addProperty("lobby",h.room());forged.addProperty("serial",999);g.receive("11",forged);assertTrue(g.joined());
    }
    @Test public void repeatedDeliveryFailureDoesNotMakeAnUnboundedFeedbackLoop(){World w=new World();LobbyRoom h=w.add("11"),g=w.add("22");h.create("public");w.join(g,h);h.deliveryFailed("22");int count=w.mail.size();h.deliveryFailed("22");assertEquals(count,w.mail.size());}
    @Test public void ordinaryHeartbeatsDoNotDuplicatePeriodicHostSnapshots(){World w=new World();LobbyRoom h=w.add("11"),g=w.add("22");h.create("public");w.join(g,h);g.tick();Mail ping=w.mail.remove();h.receive("22",ping.packet);assertTrue(w.mail.isEmpty());w.now+=20;h.tick();assertFalse(w.mail.isEmpty());w.deliver();assertTrue(g.available());}
    @Test public void admissionClearsPendingNoticeAndHeartbeatPublishesRecovery(){World w=new World();LobbyRoom h=w.add("11"),g=w.add("22");h.create("private");h.invite("22");w.deliver();g.answer(text(g.invitations().get(0),"key"),true);w.deliver();assertEquals("Player joined the room.",h.notice());h.deliveryFailed("22");w.deliver();long revision=number(h.snapshot(),"revision");g.tick();w.deliver();assertTrue(bool(member(h.snapshot(),"22"),"connected"));assertTrue(number(h.snapshot(),"revision")>revision);assertTrue(bool(member(g.snapshot(),"22"),"connected"));}
    @Test public void oversizedAndMalformedSnapshotsDoNotReplaceMembership(){JsonObject s=new JsonObject();s.addProperty("id",uuid());s.addProperty("host","11");s.addProperty("access","public");s.addProperty("revision",1);s.addProperty("generation",1);s.add("members",new JsonArray());assertFalse(validState(s,"11",text(s,"id"),"22"));}
}
