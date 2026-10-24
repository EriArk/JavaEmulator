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
    @Test public void globalQuotaConflictExplainsOtherClient(){Fixture f=new Fixture();f.p.desired(f.room,"public");f.resultNow("already-listed");assertTrue(f.p.status().contains("TrainerOS"));f.now+=300;f.p.tick();assertEquals(1,f.commands.size());}
    @Test public void switchingPrivateWithdrawsWithHigherRevision(){Fixture f=new Fixture();f.p.desired(f.room,"public");f.resultNow("accepted");f.p.desired(f.room,"private");assertEquals("withdraw",text(f.commands.get(1),"op"));f.resultNow("withdrawn");assertTrue(f.p.status().contains("withdrawal"));}
    @Test public void recoveryClosesOldRoomBeforePublishingDifferentOne(){Fixture f=new Fixture();f.p.desired(f.room,"public");List<String> out=new ArrayList<>();LobbyPublisher restart=new LobbyPublisher("11",f.store,()->f.now,out::add,()->{});restart.desired(uuid(),"public");assertEquals(1,out.size());assertTrue(out.get(0).contains("\"op\":\"close\""));assertTrue(out.get(0).contains(f.room));}
    @Test public void writeFailureStopsNetworkMutation(){Fixture f=new Fixture();f.store.fail=true;f.p.desired(f.room,"public");assertTrue(f.commands.isEmpty());assertTrue(f.p.status().contains("Nothing"));}
    @Test public void acceptedHeartbeatUsesFreshRevision(){Fixture f=new Fixture();f.p.desired(f.room,"public");f.resultNow("accepted");f.now+=61;f.p.tick();assertEquals(2,f.commands.size());assertEquals(2,number(f.commands.get(1),"revision"));}
    @Test public void privacyChangeDuringPendingPublicationWithdrawsImmediatelyAfterAck(){Fixture f=new Fixture();f.p.desired(f.room,"public");f.p.desired(f.room,"private");f.resultNow("accepted");f.p.tick();assertEquals(2,f.commands.size());assertEquals("withdraw",text(f.commands.get(1),"op"));}
}
