/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import com.google.gson.*;
import org.junit.Test;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.Assert.*;

public class LobbyMailboxTest {
    static class Fixture implements LobbyMailbox.Listener {
        long now=100000;int posts,opens,failures,expired,reads;boolean blocked,wrongRecipient;int postStatus=200,readStatus=200;
        List<String> messages=new ArrayList<>(),sent=new ArrayList<>();JsonArray incoming=new JsonArray();Consumer<FluxerAuth.Reply> held;boolean hold;
        LobbyMailbox m=new LobbyMailbox(this::http,()->now,this);
        void start(){m.configure("fixture-token","11");m.active(true);m.refresh();}
        JsonObject channel(){return JsonParser.parseString("{\"id\":\"100\",\"type\":1,\"last_message_id\":\"999\",\"recipients\":[{\"id\":\""+(wrongRecipient?"33":"22")+"\",\"username\":\"Test player\"}]}").getAsJsonObject();}
        void http(String method,String path,JsonObject body,String token,Consumer<FluxerAuth.Reply> done){
            if(hold){held=done;return;}
            if(path.endsWith("relationships")){done.accept(new FluxerAuth.Reply(200,JsonParser.parseString("[{\"id\":\"22\",\"type\":"+(blocked?2:1)+",\"user\":{\"id\":\"22\",\"username\":\"Test player\"}}]"),0));return;}
            if(path.equals("/v1/users/@me/channels")){if(method.equals("POST")){opens++;done.accept(new FluxerAuth.Reply(200,channel(),0));}else{JsonArray a=new JsonArray();if(!wrongRecipient)a.add(channel());done.accept(new FluxerAuth.Reply(200,a,0));}return;}
            if(method.equals("GET")){reads++;done.accept(new FluxerAuth.Reply(readStatus,incoming,10000));return;}
            posts++;sent.add(body.get("content").getAsString());assertEquals("POST",method);assertEquals(0,body.getAsJsonObject("allowed_mentions").getAsJsonArray("parse").size());JsonObject response=body.deepCopy();response.addProperty("id","1000");response.addProperty("channel_id","100");response.add("author",JsonParser.parseString("{\"id\":\"11\"}"));done.accept(new FluxerAuth.Reply(postStatus,response,0));
        }
        JsonObject incoming(String id,String author){JsonObject m=new JsonObject();m.addProperty("id",id);m.addProperty("channel_id","100");m.addProperty("content",LobbyRoom.PREFIX+"{}\n```");JsonObject a=new JsonObject();a.addProperty("id",author);m.add("author",a);return m;}
        public void message(String peer,String content){messages.add(peer+content);}public void changed(){}public void failed(String peer,String reason){failures++;}public void expired(){expired++;}
    }
    @Test public void deliversOnlyAuthenticatedPrivatePeerNotWebhookOrOtherAuthor(){Fixture f=new Fixture();f.incoming.add(f.incoming("901","22"));f.incoming.add(f.incoming("902","33"));JsonObject hook=f.incoming("903","22");hook.addProperty("webhook_id","44");f.incoming.add(hook);f.start();assertEquals(1,f.messages.size());}
    @Test public void repeatedPollingDoesNotReplayControlMessage(){Fixture f=new Fixture();f.incoming.add(f.incoming("901","22"));f.start();f.now+=11000;f.m.tick();assertEquals(1,f.messages.size());}
    @Test public void blockedPlayerIsNeitherReadNorSentTo(){Fixture f=new Fixture();f.blocked=true;f.start();f.m.send("22",LobbyRoom.PREFIX+"{}\n```");assertEquals(0,f.posts);assertEquals(0,f.reads);assertEquals(1,f.failures);}
    @Test public void ambiguousPostIsNotAutomaticallyRepeated(){Fixture f=new Fixture();f.postStatus=0;f.start();f.m.send("22",LobbyRoom.PREFIX+"{}\n```");assertEquals(1,f.posts);assertEquals(1,f.failures);f.now+=11000;f.m.tick();assertEquals(1,f.posts);}
    @Test public void mismatchedDmRecipientCannotReceivePacket(){Fixture f=new Fixture();f.wrongRecipient=true;f.start();f.m.send("22",LobbyRoom.PREFIX+"{}\n```");assertEquals(1,f.opens);assertEquals(0,f.posts);assertEquals(1,f.failures);}
    @Test public void pauseAndAccountChangeFenceLateResponses(){Fixture f=new Fixture();f.hold=true;f.start();Consumer<FluxerAuth.Reply> old=f.held;f.m.active(false);old.accept(new FluxerAuth.Reply(200,new JsonArray(),0));assertEquals(0,f.reads);f.m.configure("different-token","33");old.accept(new FluxerAuth.Reply(401,null,0));assertEquals(0,f.expired);}
    @Test public void rateLimitDefersAllRequests(){Fixture f=new Fixture();f.readStatus=429;f.start();int reads=f.reads;f.m.refresh();f.m.send("22",LobbyRoom.PREFIX+"{}\n```");assertEquals(reads,f.reads);assertEquals(0,f.posts);}
    @Test public void expiredSessionIsReportedWithoutFurtherRequests(){Fixture f=new Fixture();f.readStatus=401;f.start();assertEquals(1,f.expired);f.m.tick();assertEquals(1,f.reads);}
    @Test public void queuedSnapshotsCoalesceWithoutDroppingAdmissionOrClose(){Fixture f=new Fixture();f.m.configure("fixture-token","11");f.m.send("22","admission");for(int i=0;i<200;i++)f.m.send("22","state-"+i,"room-incarnation");f.m.send("22","close");assertEquals(0,f.failures);f.m.active(true);f.m.refresh();for(int i=0;i<4;i++){f.now+=1100;f.m.tick();}assertEquals(Arrays.asList("admission","state-199","close"),f.sent);}
}
