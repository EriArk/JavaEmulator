// SPDX-License-Identifier: GPL-3.0-only
package ru.playsoftware.j2meloader.multiplayer;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class LobbyNoticeStateTest {
    @Test public void roomNoticesDoNotEnterPacketsAndRawFailuresAreNotGuessed() {
        List<JsonObject> packets=new ArrayList<>();
        LobbyRoom room=new LobbyRoom("1",()->1000,Collections.emptySet(),(peer,data)->packets.add(data.deepCopy()),()->{});
        assertEquals(SocialNotice.ROOM_CREATE_A_ROOM_OR_ACCEPT_AN_INVITATION,room.noticeCode());
        assertTrue(room.create("private"));room.invite("2");
        assertEquals(SocialNotice.ROOM_INVITATION_PENDING_WAITING_FOR_AN_ANSWER,room.noticeCode());
        assertEquals(room.noticeCode().english,room.notice());
        assertFalse(packets.isEmpty());
        for(JsonObject packet:packets){assertFalse(packet.toString().contains("ROOM_"));assertFalse(packet.has("notice"));}
        room.deliveryFailed("2",SocialNotice.ROOM_PLAYER_UNREACHABLE);
        assertEquals(SocialNotice.ROOM_PLAYER_UNREACHABLE,room.noticeCode());
        room.deliveryFailed("2",SocialNotice.ROOM_PLAYER_UNREACHABLE.english);
        assertEquals(SocialNotice.EXTERNAL,room.noticeCode());
        assertEquals(SocialNotice.ROOM_PLAYER_UNREACHABLE.english,room.notice());
        room.leave();assertEquals(SocialNotice.ROOM_YOU_LEFT_THE_ROOM,room.noticeCode());
    }
    @Test public void publicationRecoveryIsTypedWithoutSendingOrChangingStoredIntent() {
        LobbyPublisher publisher=new LobbyPublisher("1",new LobbyPublisher.Store(){
            public JsonObject read()throws Exception{throw new IOException("Private path 100%");}
            public void write(JsonObject data){fail("Must not replace unreadable history");}
        },()->1000,content->fail("Must not publish"),()->{});
        publisher.desired(UUID.randomUUID().toString(),"public");
        assertEquals(SocialNotice.ROOM_PUBLICATION_HISTORY_COULD_NOT_BE_READ_PUBLIC,publisher.statusCode());
        assertEquals(publisher.statusCode().english,publisher.status());
    }
    @Test public void mailboxClearsPreviousAccountNoticeAndRetainsTypedDeliveryReason() {
        SocialNotice[] failed={null};
        LobbyMailbox box=new LobbyMailbox((method,path,body,token,done)->done.accept(new FluxerAuth.Reply(500,new JsonObject(),0)),()->1000,
                new LobbyMailbox.Listener(){
                    public void message(String peer,String content){}public void changed(){}public void expired(){}
                    public void failed(String peer,String reason){fail("Typed delivery expected");}
                    public void failed(String peer,SocialNotice code){failed[0]=code;}
                });
        box.configure("fixture","1");box.active(true);box.tick();
        assertEquals(SocialNotice.ROOM_COULD_NOT_CHECK_CONTACTS_REFRESH_TO_RECONNECT,box.statusCode());
        box.configure("other","2");assertEquals(SocialNotice.NONE,box.statusCode());assertEquals("",box.status());
        box.send("invalid","fixture");assertEquals(SocialNotice.ROOM_LOBBY_MESSAGE_COULD_NOT_BE_QUEUED,failed[0]);
        JsonObject reply=new JsonObject();reply.addProperty("code","EMAIL_VERIFICATION_REQUIRED");
        assertEquals(SocialNotice.ROOM_VERIFY_YOUR_EMAIL_IN_FLUXER_BEFORE_MESSAGING,LobbyMailbox.deliveryCode(new FluxerAuth.Reply(403,reply,0)));
    }
    @Test public void oldUnknownAndInterruptedContactRecordsPreserveDecisions() {
        for(String code:new String[]{"","FUTURE_NOTICE"}) {
            JsonObject record=new JsonObject();record.addProperty("phase","uncertain");record.addProperty("host",true);
            record.addProperty("lease",UUID.randomUUID().toString());record.addProperty("room",UUID.randomUUID().toString());
            record.addProperty("error","Owner diagnostic 100%");if(!code.isEmpty())record.addProperty("errorCode",code);
            JsonObject contacts=new JsonObject();contacts.add("2",record);
            JsonObject saved=new JsonObject();saved.addProperty("v",1);saved.addProperty("self","1");saved.add("contacts",contacts);
            TemporaryContacts actor=contactActor(saved);
            JsonObject view=actor.state().getAsJsonObject("peers").getAsJsonObject("2");
            assertEquals("uncertain",view.get("phase").getAsString());assertEquals("Owner diagnostic 100%",view.get("error").getAsString());
            assertFalse(actor.keep("2"));actor.close();
            record.addProperty("phase","creating");
            actor=contactActor(saved);view=actor.state().getAsJsonObject("peers").getAsJsonObject("2");
            assertEquals("uncertain",view.get("phase").getAsString());
            assertEquals(SocialNotice.ROOM_CHECK_THIS_FRIENDSHIP_AFTER_THE_INTERRUPTED_REQUEST.name(),view.get("errorCode").getAsString());
            assertFalse(actor.keep("2"));actor.close();
        }
    }
    private TemporaryContacts contactActor(JsonObject saved) {
        return new TemporaryContacts("1",new TemporaryContacts.Journal(){
            public JsonObject load(){return saved.deepCopy();}public void save(JsonObject data){fail("Read-only recovery should not write");}
        },(method,path,body,done)->fail("No network during rendering/recovery"),(peer,packet)->fail("No signal during rendering/recovery"),1000);
    }
}
