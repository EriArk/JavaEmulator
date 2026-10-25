/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import com.google.gson.*;
import org.junit.Test;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.Assert.*;

public class FluxerChatsTest {
    @Test public void peerSignalingPayloadIsHiddenButOrdinaryChatRemains(){
        Fixture f=new Fixture();f.connect();f.chats.select("101");
        String signal=io.github.eriark.abyssme.peer.PeerSignals.PREFIX+"opaque-fragment";
        f.history("101").reply(200,"["+message("801","101","22",signal,"")+","+message("802","101","22","Ready to play?","")+"]");
        assertEquals(2,f.chats.state().messages.size());
        assertEquals("Game session message",f.chats.state().messages.get(0).text);
        assertEquals("Ready to play?",f.chats.state().messages.get(1).text);
    }
    private static final String TOKEN="fixture-account-session-one",OTHER="fixture-account-session-two";
    private static final class Request {
        String method,path,token;JsonObject body;Consumer<FluxerAuth.Reply> done;
        void reply(int code,String json){done.accept(new FluxerAuth.Reply(code,json==null?null:JsonParser.parseString(json),0));}
    }
    private static final class Fixture {
        long now=1000,revision;final List<Request> requests=new ArrayList<>();
        final FluxerChats chats=new FluxerChats((method,path,body,token,done)->{
            Request r=new Request();r.method=method;r.path=path;r.body=body;r.token=token;r.done=done;requests.add(r);
        },()->now,s->{});
        Request take(String path){assertFalse("No request for "+path,requests.isEmpty());Request r=requests.remove(0);assertEquals(path,r.path);return r;}
        void connect(){chats.session(TOKEN);take("/v1/users/@me").reply(200,"{\"id\":\"11\",\"username\":\"Me\"}");take("/v1/users/@me/relationships").reply(200,"[]");list();}
        void list(){take("/v1/users/@me/channels").reply(200,"[{\"id\":\"101\",\"type\":1,\"last_message_id\":\"900\",\"recipients\":[{\"id\":\"22\",\"username\":\"Player\"}]},{\"id\":\"102\",\"type\":3,\"name\":\"Test group\",\"last_message_id\":\"1000\"}]");}
        Request history(String channel){return take("/v1/channels/"+channel+"/messages?limit=50");}
        void open(){connect();chats.select("101");history("101").reply(200,"["+message("800","101","22","Hello","")+"]");}
        long draft(String text){chats.draft(chats.state().channel,text,++revision);return revision;}
        Request send(){draft("Test message");chats.send(chats.state().channel,revision);return take("/v1/channels/"+chats.state().channel+"/messages");}
    }
    private static String message(String id,String channel,String author,String content,String nonce){JsonObject m=new JsonObject();m.addProperty("id",id);m.addProperty("channel_id",channel);m.addProperty("content",content);m.addProperty("nonce",nonce);m.addProperty("timestamp","2026-10-24T01:00:00Z");JsonObject a=new JsonObject();a.addProperty("id",author);a.addProperty("username","Test user");m.add("author",a);return m.toString();}
    @Test public void signedOutNeverFetchesOrSends(){Fixture f=new Fixture();f.chats.session(null);f.chats.active(true);f.chats.tick();f.chats.send("101",0);assertTrue(f.requests.isEmpty());assertFalse(f.chats.state().verified);}
    @Test public void listIsVerifiedPrivateSortedAndNamed(){Fixture f=new Fixture();f.connect();assertTrue(f.chats.state().verified);assertEquals("102",f.chats.state().channels.get(0).id);assertEquals("Player",f.chats.state().channels.get(1).name);}
    @Test public void verificationFailureCannotReadConversations(){Fixture f=new Fixture();f.chats.session(TOKEN);f.take("/v1/users/@me").reply(401,null);assertFalse(f.chats.state().verified);assertTrue(f.requests.isEmpty());}
    @Test public void friendsStatusDoesNotDescribeAnEmptyChatList(){
        Fixture f=new Fixture();f.chats.session(TOKEN);
        f.take("/v1/users/@me").reply(200,"{\"id\":\"11\"}");
        assertEquals("Refreshing contacts...",f.chats.state().friendsStatus());
        f.take("/v1/users/@me/relationships").reply(200,"[]");
        f.take("/v1/users/@me/channels").reply(200,"[]");
        assertEquals("No conversations yet.",f.chats.state().status);
        assertEquals("Contacts up to date.",f.chats.state().friendsStatus());
        f.chats.refresh();f.take("/v1/users/@me/relationships").reply(500,null);
        assertEquals(f.chats.state().status,f.chats.state().friendsStatus());
        assertTrue(f.chats.state().friendsStatus().contains("Could not check contacts"));
        f.chats.session(null);
        assertEquals(f.chats.state().status,f.chats.state().friendsStatus());
    }
    @Test public void blockedAccountsAndGuildChannelsAreExcluded(){
        Fixture f=new Fixture();f.chats.session(TOKEN);f.take("/v1/users/@me").reply(200,"{\"id\":\"11\"}");
        f.take("/v1/users/@me/relationships").reply(200,"[{\"id\":\"22\",\"type\":2}]");
        f.take("/v1/users/@me/channels").reply(200,"[{\"id\":\"101\",\"type\":1,\"recipients\":[{\"id\":\"22\"}]},{\"id\":\"102\",\"type\":3},{\"id\":\"103\",\"type\":0,\"guild_id\":\"200\"}]");
        assertEquals(1,f.chats.state().channels.size());f.chats.select("102");f.history("102").reply(200,"["+message("800","102","22","Hidden","")+"]");assertTrue(f.chats.state().messages.isEmpty());
    }
    @Test public void blockedLookupFailureDoesNotOpenHistory(){Fixture f=new Fixture();f.chats.session(TOKEN);f.take("/v1/users/@me").reply(200,"{\"id\":\"11\"}");f.take("/v1/users/@me/relationships").reply(500,null);assertTrue(f.requests.isEmpty());assertTrue(f.chats.state().channels.isEmpty());}
    @Test public void newBlockRemovesCachedDmEvenWhenChannelRefreshFails(){
        Fixture f=new Fixture();f.open();f.draft("private draft");Request send=f.send();
        f.chats.refresh();f.take("/v1/users/@me/relationships").reply(200,"[{\"id\":\"22\",\"type\":2}]");
        assertEquals("",f.chats.state().channel);assertTrue(f.chats.state().messages.isEmpty());
        f.take("/v1/users/@me/channels").reply(500,null);send.reply(500,null);
        assertEquals(1,f.chats.state().channels.size());assertFalse(f.chats.state().canSend);
    }
    @Test public void deniedSendDoesNotOfferRetryUntilAccessReturns(){
        Fixture f=new Fixture();f.open();Request send=f.send();String nonce=send.body.get("nonce").getAsString();send.reply(403,null);
        assertFalse(f.chats.state().messages.get(1).retryable);f.chats.retry("101",nonce);assertTrue(f.requests.isEmpty());
        f.chats.history(false);f.history("101").reply(200,"[]");assertTrue(f.chats.state().messages.get(0).retryable);
    }
    @Test public void differentSessionFencesOldHistoryAndClearsDrafts(){
        Fixture f=new Fixture();f.open();f.draft("private draft");f.chats.history(false);Request old=f.history("101");f.chats.session(OTHER);
        old.reply(200,"["+message("801","101","22","old account text","")+"]");assertTrue(f.chats.state().messages.isEmpty());assertEquals("",f.chats.state().draft);assertFalse(f.chats.state().verified);
        assertEquals(OTHER,f.take("/v1/users/@me").token);
    }
    @Test public void switchingConversationsNeverMixesLateReplies(){Fixture f=new Fixture();f.connect();f.chats.select("101");Request old=f.history("101");f.chats.select("102");f.history("102").reply(200,"["+message("802","102","22","group","")+"]");old.reply(200,"["+message("801","101","22","dm","")+"]");assertEquals("group",f.chats.state().messages.get(0).text);}
    @Test public void draftRevisionsPreventStaleSendAndKeepPerChannelText(){Fixture f=new Fixture();f.open();long first=f.draft("first");f.draft("second");f.chats.send("101",first);assertTrue(f.requests.isEmpty());f.chats.select("102");f.history("102").reply(200,"[]");f.draft("group draft");f.chats.select("101");assertEquals("second",f.chats.state().draft);}
    @Test public void successfulSendHasOneNonceAndDisablesMentions(){Fixture f=new Fixture();f.open();Request send=f.send();String nonce=send.body.get("nonce").getAsString();assertEquals(32,nonce.length());assertEquals(0,send.body.getAsJsonObject("allowed_mentions").getAsJsonArray("parse").size());f.chats.send("101",f.revision);assertTrue(f.requests.isEmpty());send.reply(200,message("801","101","11","Test message",nonce));assertEquals(2,f.chats.state().messages.size());assertEquals("",f.chats.state().messages.get(1).delivery);assertEquals("",f.chats.state().draft);}
    @Test public void unknownSendOnlyRetriesExplicitlyWithSameNonce(){Fixture f=new Fixture();f.open();Request send=f.send();String nonce=send.body.get("nonce").getAsString();send.reply(0,null);assertEquals("Delivery unknown",f.chats.state().messages.get(1).delivery);f.chats.retry("101",nonce);Request retry=f.take("/v1/channels/101/messages");assertEquals(nonce,retry.body.get("nonce").getAsString());}
    @Test public void unknownRetryAfterWindowOnlyChecksHistory(){Fixture f=new Fixture();f.open();Request send=f.send();String nonce=send.body.get("nonce").getAsString();send.reply(0,null);f.now+=240001;f.chats.retry("101",nonce);assertEquals("GET",f.history("101").method);assertTrue(f.requests.isEmpty());}
    @Test public void rejectionAfterUnknownDoesNotRemoveUncertainty(){Fixture f=new Fixture();f.open();Request send=f.send();String nonce=send.body.get("nonce").getAsString();send.reply(0,null);f.chats.retry("101",nonce);f.take("/v1/channels/101/messages").reply(429,null);f.now+=240001;f.chats.retry("101",nonce);f.history("101");assertTrue(f.requests.isEmpty());}
    @Test public void knownRejectedSendCanRetryLater(){Fixture f=new Fixture();f.open();Request send=f.send();String nonce=send.body.get("nonce").getAsString();send.reply(400,null);f.now+=300000;f.chats.retry("101",nonce);assertEquals("POST",f.take("/v1/channels/101/messages").method);}
    @Test public void historyAcknowledgementWinsOverLateSendFailure(){Fixture f=new Fixture();f.open();Request send=f.send();String nonce=send.body.get("nonce").getAsString();f.chats.history(false);f.history("101").reply(200,"["+message("801","101","11","Test message",nonce)+"]");send.reply(500,null);assertEquals(1,f.chats.state().messages.size());assertEquals("",f.chats.state().messages.get(0).delivery);}
    @Test public void oldHistoryCannotEraseSuccessfulSend(){Fixture f=new Fixture();f.open();f.chats.history(false);Request history=f.history("101");Request send=f.send();send.reply(200,message("801","101","11","Test message",send.body.get("nonce").getAsString()));history.reply(200,"[]");assertEquals(2,f.chats.state().messages.size());}
    @Test public void accessDeniedClearsRemoteTextButKeepsDraft(){Fixture f=new Fixture();f.open();f.draft("draft");f.chats.history(false);f.history("101").reply(403,null);assertTrue(f.chats.state().messages.isEmpty());assertEquals("draft",f.chats.state().draft);assertFalse(f.chats.state().canSend);}
    @Test public void expiredSessionClearsAllPrivateStateAndLatePosts(){Fixture f=new Fixture();f.open();Request send=f.send();f.chats.history(false);f.history("101").reply(401,null);send.reply(200,message("801","101","11","Test message",send.body.get("nonce").getAsString()));assertTrue(f.chats.state().messages.isEmpty());assertTrue(f.chats.state().channels.isEmpty());assertEquals("",f.chats.state().account);}
    @Test public void removingChannelClearsItsWindowAndPendingCallback(){Fixture f=new Fixture();f.open();Request send=f.send();f.chats.refresh();f.take("/v1/users/@me/relationships").reply(200,"[]");f.take("/v1/users/@me/channels").reply(200,"[]");send.reply(200,message("801","101","11","Test message",send.body.get("nonce").getAsString()));assertTrue(f.chats.state().messages.isEmpty());assertEquals("",f.chats.state().channel);}
    @Test public void olderUsesServerCursorAndIsNotAutoReplaced(){Fixture f=new Fixture();f.open();f.chats.older();f.take("/v1/channels/101/messages?limit=50&before=800").reply(200,"["+message("700","101","22","Earlier","")+"]");assertTrue(f.chats.state().past);assertEquals("Earlier",f.chats.state().messages.get(0).text);f.now+=9000;f.chats.active(true);f.chats.tick();assertTrue(f.requests.isEmpty());f.chats.latest();f.history("101").reply(200,"[]");assertFalse(f.chats.state().past);assertFalse(f.chats.state().more);}
    @Test public void inactiveScreenDoesNotPollAndReconnectDoesNotResend(){Fixture f=new Fixture();f.open();Request send=f.send();send.reply(0,null);f.now+=10000;f.chats.tick();assertTrue(f.requests.isEmpty());f.chats.active(true);f.chats.tick();assertEquals("GET",f.history("101").method);assertTrue(f.requests.isEmpty());}
    @Test public void rateLimitStopsBothPollingAndSending(){Fixture f=new Fixture();f.open();f.chats.history(false);f.history("101").done.accept(new FluxerAuth.Reply(429,null,60000));f.chats.active(true);f.now+=20000;f.chats.tick();f.draft("wait");f.chats.send("101",f.revision);assertTrue(f.requests.isEmpty());assertFalse(f.chats.state().canSend);}
    @Test public void malformedHistoryIsNotSuccessAndWrongChannelRowsAreIgnored(){Fixture f=new Fixture();f.open();f.chats.history(false);f.history("101").reply(200,"{}");assertEquals(1,f.chats.state().messages.size());f.chats.history(false);f.history("101").reply(200,"["+message("801","102","22","wrong","")+"]");assertTrue(f.chats.state().messages.isEmpty());}
    @Test public void switchingWhileSendingKeepsOutcomeInOriginalChat(){Fixture f=new Fixture();f.open();Request send=f.send();String nonce=send.body.get("nonce").getAsString();f.chats.select("102");f.history("102").reply(200,"[]");send.reply(0,null);assertTrue(f.chats.state().messages.isEmpty());f.chats.select("101");assertEquals("Delivery unknown",f.chats.state().messages.get(1).delivery);f.chats.discard("101",nonce);assertEquals(1,f.chats.state().messages.size());}
    @Test public void mediaIsRepresentedWithoutPersistingCapabilityUrls(){Fixture f=new Fixture();f.open();JsonObject m=JsonParser.parseString(message("801","101","22","","")).getAsJsonObject();JsonArray files=new JsonArray();JsonObject file=new JsonObject();file.addProperty("url","https://private.invalid/capability");files.add(file);m.add("attachments",files);f.chats.history(false);f.history("101").reply(200,"["+m+"]");assertEquals("[Attachment]",f.chats.state().messages.get(0).text);}
    @Test public void closeFencesEveryLateRequest(){Fixture f=new Fixture();f.connect();f.chats.select("101");Request r=f.history("101");f.chats.close();r.reply(200,"["+message("801","101","22","late","")+"]");assertTrue(f.chats.state().messages.isEmpty());}
}
