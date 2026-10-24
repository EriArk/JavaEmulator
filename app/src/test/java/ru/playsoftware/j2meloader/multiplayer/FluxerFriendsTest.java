/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import com.google.gson.*;
import java.util.*;
import java.util.function.Consumer;
import org.junit.Test;
import static org.junit.Assert.*;

public class FluxerFriendsTest {
    private static final String TOKEN="fixture-friends-token",ROOT="/v1/users/@me/relationships",CHANNELS="/v1/users/@me/channels";
    private static final class Request {
        String method,path,token;JsonObject body;Consumer<FluxerAuth.Reply> done;
        void reply(int code,String json){done.accept(new FluxerAuth.Reply(code,json==null?null:JsonParser.parseString(json),0));}
    }
    private static final class Fixture {
        long now=1000;List<Request> requests=new ArrayList<>();
        FluxerChats chats=new FluxerChats((method,path,body,token,done)->{Request r=new Request();r.method=method;r.path=path;r.body=body;r.token=token;r.done=done;requests.add(r);},()->now,s->{});
        Request take(String method,String path){assertFalse(requests.isEmpty());Request r=requests.remove(0);assertEquals(method,r.method);assertEquals(path,r.path);return r;}
        void connect(int type){chats.session(TOKEN);take("GET","/v1/users/@me").reply(200,"{\"id\":\"11\"}");list(type);}
        void list(int type){take("GET",ROOT).reply(200,type==0?"[]":"["+relation(type,"2026-10-24T00:00:00Z")+"]");take("GET",CHANNELS).reply(200,"[]");}
        FluxerChats.Friend friend(){return chats.state().friends.get(0);}
        void action(FluxerChats.FriendAction action){FluxerChats.Friend f=friend();chats.friendAction("11",f,action);take("GET",ROOT).reply(200,"["+relation(f.type,f.since)+"]");}
    }
    private static String relation(int type,String since){return "{\"id\":\"22\",\"type\":"+type+",\"since\":\""+since+"\",\"user\":{\"id\":\"22\",\"username\":\"player\",\"discriminator\":\"0042\",\"global_name\":\"Player\"}}";}
    private static String channel(String peer){return "{\"id\":\"101\",\"type\":1,\"recipients\":[{\"id\":\""+peer+"\",\"username\":\"Player\"}]}";}
    @Test public void anonymousActionsNeverReachNetwork(){Fixture f=new Fixture();f.chats.addFriend("11","player#0042");assertTrue(f.requests.isEmpty());assertFalse(f.chats.state().friendsReady);}
    @Test public void exposesProviderContactsWithoutPresenceClaims(){Fixture f=new Fixture();f.connect(3);assertEquals("Player",f.friend().name);assertEquals("player#0042",f.friend().tag);assertEquals("Incoming request",f.friend().kind());assertTrue(f.chats.state().friendsReady);}
    @Test public void acceptUsesPutOnlyAfterFreshSnapshot(){Fixture f=new Fixture();f.connect(3);f.action(FluxerChats.FriendAction.ACCEPT);Request r=f.take("PUT",ROOT+"/22");assertEquals(1,r.body.get("type").getAsInt());r.reply(200,relation(1,"later"));f.list(1);assertEquals(1,f.friend().type);}
    @Test public void cancelAndUnblockUseDeleteWithoutBody(){for(int type:new int[]{2,4}){Fixture f=new Fixture();f.connect(type);f.action(type==2?FluxerChats.FriendAction.UNBLOCK:FluxerChats.FriendAction.CANCEL);assertNull(f.take("DELETE",ROOT+"/22").body);}}
    @Test public void cannotAcceptOutgoingRequestOrMessageBlockedUser(){Fixture f=new Fixture();f.connect(4);f.chats.friendAction("11",f.friend(),FluxerChats.FriendAction.ACCEPT);assertTrue(f.requests.isEmpty());Fixture b=new Fixture();b.connect(2);b.chats.friendAction("11",b.friend(),FluxerChats.FriendAction.MESSAGE);assertTrue(b.requests.isEmpty());}
    @Test public void changedTypePreventsDestructiveMutation(){Fixture f=new Fixture();f.connect(3);f.chats.friendAction("11",f.friend(),FluxerChats.FriendAction.DECLINE);f.take("GET",ROOT).reply(200,"["+relation(1,"later")+"]");assertTrue(f.requests.isEmpty());assertEquals(1,f.friend().type);assertTrue(f.chats.state().friendsNotice.contains("Contact changed"));}
    @Test public void sameTypeNewGenerationAlsoPreventsDeletion(){Fixture f=new Fixture();f.connect(1);f.chats.friendAction("11",f.friend(),FluxerChats.FriendAction.REMOVE);f.take("GET",ROOT).reply(200,"["+relation(1,"new friendship")+"]");assertTrue(f.requests.isEmpty());}
    @Test public void missingContactCannotBeDeleted(){Fixture f=new Fixture();f.connect(1);f.chats.friendAction("11",f.friend(),FluxerChats.FriendAction.REMOVE);f.take("GET",ROOT).reply(200,"[]");assertTrue(f.requests.isEmpty());}
    @Test public void staleConfirmationAfterAccountSwitchIsIgnored(){Fixture f=new Fixture();f.connect(1);FluxerChats.Friend old=f.friend();f.chats.session("fixture-other-account");f.take("GET","/v1/users/@me").reply(200,"{\"id\":\"33\"}");f.list(1);f.chats.friendAction("11",old,FluxerChats.FriendAction.REMOVE);assertTrue(f.requests.isEmpty());}
    @Test public void latePreflightAfterLogoutCannotMutate(){Fixture f=new Fixture();f.connect(1);f.chats.friendAction("11",f.friend(),FluxerChats.FriendAction.REMOVE);Request r=f.take("GET",ROOT);f.chats.session(null);r.reply(200,"["+relation(1,friendSince())+"]");assertTrue(f.requests.isEmpty());assertTrue(f.chats.state().friends.isEmpty());}
    private static String friendSince(){return "2026-10-24T00:00:00Z";}
    @Test public void busyActionsCannotDoubleSubmitOrRaceRefresh(){Fixture f=new Fixture();f.connect(3);FluxerChats.Friend peer=f.friend();f.chats.friendAction("11",peer,FluxerChats.FriendAction.ACCEPT);f.chats.friendAction("11",peer,FluxerChats.FriendAction.ACCEPT);f.chats.refresh();assertEquals(1,f.requests.size());}
    @Test public void failedPreflightMakesNoMutation(){Fixture f=new Fixture();f.connect(1);f.chats.friendAction("11",f.friend(),FluxerChats.FriendAction.BLOCK);f.take("GET",ROOT).reply(500,null);assertTrue(f.requests.isEmpty());assertFalse(f.chats.state().friendsReady);}
    @Test public void unknownDeleteRefreshesButNeverReplays(){Fixture f=new Fixture();f.connect(1);f.action(FluxerChats.FriendAction.REMOVE);f.take("DELETE",ROOT+"/22").reply(0,null);f.list(0);assertTrue(f.chats.state().friendsNotice.contains("Result unknown"));assertTrue(f.requests.isEmpty());}
    @Test public void rateLimitBlocksManualMutationUntilRefresh(){Fixture f=new Fixture();f.connect(1);f.action(FluxerChats.FriendAction.REMOVE);f.take("DELETE",ROOT+"/22").done.accept(new FluxerAuth.Reply(429,null,60000));f.chats.refresh();f.chats.addFriend("11","other#0001");assertTrue(f.requests.isEmpty());f.now+=60001;f.chats.refresh();f.list(1);assertTrue(f.chats.state().friendsReady);}
    @Test public void expiredMutationClearsPrivateState(){Fixture f=new Fixture();f.connect(1);f.action(FluxerChats.FriendAction.BLOCK);f.take("PUT",ROOT+"/22").reply(401,null);assertFalse(f.chats.state().verified);assertTrue(f.chats.state().friends.isEmpty());}
    @Test public void lateMutationAfterAccountChangeDoesNotRefreshNewAccount(){Fixture f=new Fixture();f.connect(1);f.action(FluxerChats.FriendAction.REMOVE);Request r=f.take("DELETE",ROOT+"/22");f.chats.session(null);r.reply(204,null);assertTrue(f.requests.isEmpty());}
    @Test public void invalidTagDoesNotSendOrLookup(){Fixture f=new Fixture();f.connect(0);for(String tag:new String[]{"", "player", "../users#1234", "player#12345", "player#12x4"})f.chats.addFriend("11",tag);assertTrue(f.requests.isEmpty());}
    @Test public void tagPreservesLeadingZeroDiscriminator(){Fixture f=new Fixture();f.connect(0);f.chats.addFriend("11"," new_player#0042 ");f.take("GET",ROOT).reply(200,"[]");Request r=f.take("POST",ROOT);assertEquals("0042",r.body.get("discriminator").getAsString());assertEquals("new_player",r.body.get("username").getAsString());}
    @Test public void numericDiscriminatorUsesCanonicalTag(){Fixture f=new Fixture();f.connect(0);f.chats.addFriend("11","player#0042");f.take("GET",ROOT).reply(200,"["+relation(3,friendSince()).replace("\"discriminator\":\"0042\"","\"discriminator\":42")+"]");assertEquals("player#0042",f.friend().tag);assertTrue(f.requests.isEmpty());}
    @Test public void mismatchedUserIdentityIsNotActionable(){Fixture f=new Fixture();f.connect(0);f.chats.refresh();String wrong=relation(1,friendSince()).replace("\"user\":{\"id\":\"22\"","\"user\":{\"id\":\"99\"");f.take("GET",ROOT).reply(200,"["+wrong+"]");assertFalse(f.chats.state().friendsReady);assertTrue(f.chats.state().friends.isEmpty());}
    @Test public void existingContactTagDoesNotOverwriteOrImplicitlyAccept(){for(int type=1;type<=4;type++){Fixture f=new Fixture();f.connect(type);f.chats.addFriend("11","player#0042");f.take("GET",ROOT).reply(200,"["+relation(type,friendSince())+"]");assertTrue(f.requests.isEmpty());}}
    @Test public void openFriendValidatesReturnedRecipientAndNeverSendsText(){Fixture f=new Fixture();f.connect(1);f.action(FluxerChats.FriendAction.MESSAGE);Request r=f.take("POST",CHANNELS);assertEquals("22",r.body.get("recipient_id").getAsString());r.reply(200,channel("22"));assertEquals("101",f.chats.state().channel);assertEquals(1,f.chats.state().openedConversation);f.take("GET","/v1/channels/101/messages?limit=50").reply(200,"[]");assertTrue(f.requests.isEmpty());}
    @Test public void wrongRecipientAndGroupAreNotOpened(){Fixture f=new Fixture();f.connect(1);f.action(FluxerChats.FriendAction.MESSAGE);f.take("POST",CHANNELS).reply(200,channel("99"));assertEquals("",f.chats.state().channel);assertEquals(0,f.chats.state().openedConversation);assertTrue(f.requests.isEmpty());}
    @Test public void existingDirectChannelIsReusedAfterPreflight(){Fixture f=new Fixture();f.chats.session(TOKEN);f.take("GET","/v1/users/@me").reply(200,"{\"id\":\"11\"}");f.take("GET",ROOT).reply(200,"["+relation(1,friendSince())+"]");f.take("GET",CHANNELS).reply(200,"["+channel("22")+"]");f.action(FluxerChats.FriendAction.MESSAGE);f.take("GET","/v1/channels/101/messages?limit=50");assertTrue(f.requests.isEmpty());}
    @Test public void malformedOrDuplicateRelationshipsDisableActions(){Fixture f=new Fixture();f.connect(1);f.chats.refresh();f.take("GET",ROOT).reply(200,"["+relation(1,friendSince())+","+relation(2,friendSince())+"]");assertFalse(f.chats.state().friendsReady);assertTrue(f.requests.isEmpty());}
    @Test public void closeFencesPendingFriendWrite(){Fixture f=new Fixture();f.connect(1);f.action(FluxerChats.FriendAction.REMOVE);Request r=f.take("DELETE",ROOT+"/22");f.chats.close();r.reply(204,null);assertTrue(f.requests.isEmpty());}
}
