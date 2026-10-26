/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import com.google.gson.*;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
import static ru.playsoftware.j2meloader.multiplayer.LobbyRoom.*;

public class LobbyPublisherTest {
    static class Store implements LobbyPublisher.Store {JsonObject value=new JsonObject();boolean fail;public JsonObject read(){return value.deepCopy();}public void write(JsonObject o)throws Exception{if(fail)throw new Exception();value=o.deepCopy();}}
    static class Fixture {
        long now=1000;Store store=new Store();List<JsonObject> commands=new ArrayList<>();
        LobbyPublisher p=new LobbyPublisher("11",store,()->now,c->{JsonObject o=JsonParser.parseString(c.substring(LobbyDirectory.PREFIX.length(),c.length()-4)).getAsJsonObject();assertEquals(o,store.value);commands.add(o);},()->{});
        String room=uuid();
        String result(String code){JsonObject c=commands.get(commands.size()-1).deepCopy();c.addProperty("owner","11");c.addProperty("expires",now+90);c.addProperty("result",code);return LobbyPublisher.RESULT_PREFIX+c+"\n```";}
        void resultNow(String code){p.receive(LobbyDirectory.BOT,result(code));}
    }
    @Test public void privateRoomNeverPublishes(){Fixture f=new Fixture();f.p.desired(f.room,"private");assertTrue(f.commands.isEmpty());}
    @Test public void botClockSkewIsBoundedAndExpiredResultsStayRejected(){
        for(long offset:new long[]{-90,22,60,61}){
            Fixture f=new Fixture();f.p.desired(f.room,"public");
            String result=f.result("accepted").replace("\"expires\":1090","\"expires\":"+(1090+offset));
            f.p.receive(LobbyDirectory.BOT,result);
            assertEquals(offset>=0&&offset<=60,f.p.status().contains("accepted"));
        }
    }
    @Test public void waitsForCorrelatedBotResultNotMessageDelivery(){Fixture f=new Fixture();f.p.desired(f.room,"public");assertTrue(f.p.status().contains("Waiting"));f.p.receive("22",f.result("accepted"));assertTrue(f.p.status().contains("Waiting"));f.resultNow("accepted");assertTrue(f.p.status().contains("accepted"));}
    @Test public void missingReplyNeverClaimsSuccessOrBlindlyRetries(){Fixture f=new Fixture();f.p.desired(f.room,"public");f.now+=46;f.p.tick();assertTrue(f.p.status().contains("missing"));f.now+=300;f.p.tick();assertEquals(1,f.commands.size());f.p.retry();assertEquals(2,f.commands.size());assertEquals(2,number(f.commands.get(1),"revision"));}
    @Test public void foreignAccountAndRequestRepliesCannotConfirm(){Fixture f=new Fixture();f.p.desired(f.room,"public");f.p.receive(LobbyDirectory.BOT,f.result("accepted").replace("\"owner\":\"11\"","\"owner\":\"22\""));assertTrue(f.p.status().contains("Waiting"));f.p.receive(LobbyDirectory.BOT,f.result("accepted").replace(text(f.commands.get(0),"request"),uuid()));assertTrue(f.p.status().contains("Waiting"));}
    @Test public void abyssmeQuotaConflictDoesNotAskToCloseTrainerOS(){Fixture f=new Fixture();f.p.desired(f.room,"public");f.resultNow("already-listed");assertTrue(f.p.status().contains("AbyssME"));assertFalse(f.p.status().contains("TrainerOS"));f.now+=300;f.p.tick();assertEquals(1,f.commands.size());}
    @Test public void switchingPrivateWithdrawsWithHigherRevision(){Fixture f=new Fixture();f.p.desired(f.room,"public");f.resultNow("accepted");f.p.desired(f.room,"private");assertEquals("withdraw",text(f.commands.get(1),"op"));f.resultNow("withdrawn");assertTrue(f.p.status().contains("withdrawal"));}
    @Test public void recoveryClosesOldRoomBeforePublishingDifferentOne(){Fixture f=new Fixture();f.p.desired(f.room,"public");List<String> out=new ArrayList<>();LobbyPublisher restart=new LobbyPublisher("11",f.store,()->f.now,out::add,()->{});restart.desired(uuid(),"public");assertEquals(1,out.size());assertTrue(out.get(0).contains("\"op\":\"close\""));assertTrue(out.get(0).contains(f.room));}
    @Test public void writeFailureStopsNetworkMutation(){Fixture f=new Fixture();f.store.fail=true;f.p.desired(f.room,"public");assertTrue(f.commands.isEmpty());assertTrue(f.p.status().contains("Nothing"));}
    @Test public void acceptedHeartbeatUsesFreshRevision(){Fixture f=new Fixture();f.p.desired(f.room,"public");f.resultNow("accepted");f.now+=61;f.p.tick();assertEquals(2,f.commands.size());assertEquals(2,number(f.commands.get(1),"revision"));}
    @Test public void privacyChangeDuringPendingPublicationWithdrawsImmediatelyAfterAck(){Fixture f=new Fixture();f.p.desired(f.room,"public");f.p.desired(f.room,"private");f.resultNow("accepted");f.p.tick();assertEquals(2,f.commands.size());assertEquals("withdraw",text(f.commands.get(1),"op"));}

    private JsonObject selected(String hash,int members,Boolean running){
        JsonObject state=new JsonObject();state.add("game",game(new LobbySession.Game("java",String.join("",Collections.nCopies(64,hash)),"bluetooth","1",2)));
        JsonArray players=new JsonArray();for(int i=0;i<members;i++){JsonObject p=new JsonObject();p.addProperty("id",Integer.toString(11+i));p.addProperty("connected",false);players.add(p);}state.add("members",players);
        if(running!=null){JsonObject launch=new JsonObject();launch.addProperty("running",running);launch.addProperty("attempt",uuid());state.add("launch",launch);}return state;
    }
    @Test public void selectedGameProjectsOnlyPublicIdentityAndReservedSeats(){
        Fixture f=new Fixture();JsonObject snapshot=selected("a",1,null);snapshot.addProperty("path","/private/game");snapshot.addProperty("token","secret");
        f.p.desired(f.room,"public",snapshot,"Biplanes");JsonObject command=f.commands.get(0);
        assertEquals("gathering",text(command,"status"));assertEquals("other",text(command,"topic"));assertEquals(1,number(command,"available"));
        assertEquals("j2me",text(object(command,"game"),"platform"));assertEquals("Biplanes",text(object(command,"game"),"title"));assertEquals(5,object(command,"game").size());
        assertFalse(command.has("members"));assertFalse(command.has("launch"));assertFalse(command.toString().contains("secret"));assertFalse(command.has("path"));
        assertEquals(0,number(LobbyPublisher.projection(selected("a",2,null),"Biplanes"),"available"));
    }
    @Test public void projectionIsReadableByDirectoryWithExactGameIdentity(){
        Fixture f=new Fixture();f.p.desired(f.room,"approval",selected("a",1,null),"Biplanes");
        JsonObject ad=f.commands.get(0).deepCopy();ad.addProperty("owner","11");ad.addProperty("name","Host");ad.addProperty("expires",1180);
        JsonObject message=new JsonObject(),author=new JsonObject();author.addProperty("id",LobbyDirectory.BOT);message.add("author",author);
        message.addProperty("channel_id",LobbyDirectory.CHANNELS.get(4));message.addProperty("content",LobbyDirectory.PREFIX+ad+"\n```");
        LobbyDirectory.Room room=LobbyDirectory.parse(message,LobbyDirectory.CHANNELS.get(4),1000,0);assertNotNull(room);
        assertTrue(room.acceptsJavaGame(text(object(ad,"game"),"id")));assertFalse(room.acceptsJavaGame(String.join("",Collections.nCopies(64,"b"))));
        assertTrue(room.hasSpace());assertEquals("Waiting for players",room.phase());
    }
    @Test public void pendingUpdatesCoalesceToLatestGameWithoutConfirmingOldSelection(){
        Fixture f=new Fixture();f.p.desired(f.room,"public");
        f.p.desired(f.room,"public",selected("a",1,null),"First");f.p.desired(f.room,"public",selected("b",2,null),"Second");
        assertEquals(1,f.commands.size());f.resultNow("accepted");assertEquals("Updating public listing...",f.p.status());f.p.tick();
        assertEquals(2,f.commands.size());JsonObject next=f.commands.get(1);assertEquals("Second",text(object(next,"game"),"title"));assertEquals(0,number(next,"available"));
        f.resultNow("accepted");f.p.desired(f.room,"public",selected("b",2,null),"Second");assertEquals(2,f.commands.size());
    }
    @Test public void staleGameAckCannotResurrectSelectionAfterClear(){
        Fixture f=new Fixture();f.p.desired(f.room,"public",selected("a",1,null),"First");f.p.desired(f.room,"public");
        f.resultNow("accepted");f.p.tick();assertFalse(f.commands.get(1).has("game"));assertEquals("choosing",text(f.commands.get(1),"status"));
    }
    @Test public void launchAndReturnUpdateStatusWithoutAdvertisingOpenSeats(){
        Fixture f=new Fixture();f.p.desired(f.room,"public",selected("a",1,false),"Game");
        assertEquals("gathering",text(f.commands.get(0),"status"));assertEquals(0,number(f.commands.get(0),"available"));f.resultNow("accepted");
        f.p.desired(f.room,"public",selected("a",2,true),"Game");assertEquals("playing",text(f.commands.get(1),"status"));f.resultNow("accepted");
        f.p.desired(f.room,"public",selected("a",1,null),"Game");assertEquals("gathering",text(f.commands.get(2),"status"));assertEquals(1,number(f.commands.get(2),"available"));
    }
    @Test public void metadataDoesNotRepublishPrivateRoomAndCloseStillWins(){
        Fixture f=new Fixture();f.p.desired(f.room,"public",selected("a",1,null),"Game");f.resultNow("accepted");
        f.p.desired(f.room,"private",selected("a",1,null),"Game");f.resultNow("withdrawn");
        f.p.desired(f.room,"private",selected("b",2,null),"Other");assertEquals(2,f.commands.size());
        f.p.desired(f.room,"public",selected("b",2,null),"Other");f.p.desired("","private");f.resultNow("accepted");f.p.tick();
        assertEquals("close",text(f.commands.get(3),"op"));assertFalse(f.commands.get(3).has("game"));
    }
    @Test public void displayNameIsBoundedWithoutChangingContentIdentity(){
        String title=String.join("",Collections.nCopies(159,"a"))+"\uD83C\uDFAE";
        JsonObject result=LobbyPublisher.projection(selected("a",1,null),title);assertEquals(159,text(object(result,"game"),"title").length());
        assertEquals("Game  name",text(object(LobbyPublisher.projection(selected("a",1,null),"Game\n\tname"),"game"),"title"));
        assertEquals("Java Bluetooth game",text(object(LobbyPublisher.projection(selected("a",1,null),"\n"),"game"),"title"));
    }
    @Test public void unsupportedAdapterNeverClaimsDirectoryCompatibility(){
        JsonObject snapshot=selected("a",1,null);snapshot.getAsJsonObject("game").addProperty("adapter","2");
        assertFalse(LobbyPublisher.projection(snapshot,"Game").has("game"));
    }
    @Test public void privacyOrCloseSurvivesMissingUpsertAcknowledgement(){
        for(boolean close:new boolean[]{false,true}){
            Fixture f=new Fixture();f.p.desired(f.room,"public",selected("a",1,null),"Game");f.p.desired(close?"":f.room,"private");
            f.now+=46;f.p.tick();f.p.tick();assertEquals(2,f.commands.size());assertEquals(close?"close":"withdraw",text(f.commands.get(1),"op"));
            f.now+=46;f.p.tick();f.now+=100;f.p.tick();assertEquals("Unconfirmed removal requires explicit retry",2,f.commands.size());
        }
    }
    @Test public void rejectedUpsertDoesNotLosePrivacyAndRejectedRemovalDoesNotLoop(){
        Fixture f=new Fixture();f.p.desired(f.room,"public");f.p.desired(f.room,"private");f.resultNow("stale");f.p.tick();
        assertEquals("withdraw",text(f.commands.get(1),"op"));f.p.desired("","private");f.resultNow("wrong-product");
        f.now+=300;f.p.tick();assertEquals(2,f.commands.size());assertTrue(f.p.status().contains("wrong-product"));
    }
}
