/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import android.app.Application;
import android.os.Bundle;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.gson.*;
import org.junit.Assume;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import static org.junit.Assert.*;
import static ru.playsoftware.j2meloader.multiplayer.LobbyRoom.*;

/** Explicitly destructive test-account setup; production room/contact actors use real HTTP. */
public class FluxerLiveContactsDeviceTest {
    private final Bundle args=InstrumentationRegistry.getArguments();
    private Application app(){return (Application)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();}
    private String token()throws Exception{return new FluxerCredentialStore(app()).read();}
    private FluxerAuth.Reply request(String method,String path)throws Exception{
        FluxerAuth.Reply reply=FluxerHttpTransport.exchange(method,path,null,token());
        assertTrue("Provider status "+reply.status,reply.ok());return reply;
    }
    private JsonObject relationship(String peer)throws Exception{
        JsonElement data=request("GET","/v1/users/@me/relationships").data;assertTrue(data.isJsonArray());
        for(JsonElement value:data.getAsJsonArray())if(peer.equals(text(value.getAsJsonObject(),"id")))return value.getAsJsonObject();
        return new JsonObject();
    }
    private String self()throws Exception{
        JsonObject me=request("GET","/v1/users/@me").body;
        assertTrue("Only dedicated AbyssME test accounts",text(me,"username").startsWith("abyssmetest"));return text(me,"id");
    }
    @Test public void prepareAuthorizedTestPair()throws Exception{
        Assume.assumeTrue("Explicit removal of the named test friendship required","remove-test-friend".equals(args.getString("fluxerContactSetup")));
        String name=args.getString("fluxerContactPeerName","");assertTrue(name.startsWith("abyssmetest"));
        String account=self(),peer="";
        for(JsonElement value:request("GET","/v1/users/@me/relationships").data.getAsJsonArray()){
            JsonObject relation=value.getAsJsonObject();if(name.equals(text(object(relation,"user"),"username"))){
                assertTrue(peer.isEmpty());assertEquals(1,number(relation,"type"));peer=text(relation,"id");
            }
        }
        assertTrue("Named test friend must already exist",LobbyDirectory.id(peer));
        request("DELETE","/v1/users/@me/relationships/"+peer);
        assertEquals(0,relationship(peer).size());
        Bundle report=new Bundle();report.putString("test_account",account);report.putString("test_peer",peer);
        InstrumentationRegistry.getInstrumentation().sendStatus(2,report);
    }
    @Test public void admittedNonFriendsCanCleanUpOrKeep()throws Exception{
        String role=args.getString("fluxerContactRole",""),peer=args.getString("fluxerContactPeer",""),mode=args.getString("fluxerContactMode","");
        Assume.assumeTrue("Explicit non-friend test role, identity and mode required",
                (role.equals("host")||role.equals("guest"))&&LobbyDirectory.id(peer)&&(mode.equals("cleanup")||mode.equals("keep")));
        String account=self();assertNotEquals(account,peer);assertEquals("Start with no friendship/request/block",0,relationship(peer).size());
        // Ordinary community admission: never alter privacy settings to force non-friend DMs.
        try(ActivityScenario<LobbyDirectoryActivity> screen=ActivityScenario.launch(LobbyDirectoryActivity.class)){
            AtomicReference<LobbyDirectory.State> state=new AtomicReference<>();long until=System.currentTimeMillis()+45000;
            do{screen.onActivity(a->state.set(new ViewModelProvider(a).get(LobbyDirectoryModel.class).state.getValue()));
                if(state.get()!=null&&state.get().verified&&!state.get().busy&&!state.get().needsCommunity)break;Thread.sleep(300);
            }while(System.currentTimeMillis()<until);
            assertNotNull(state.get());assertTrue(state.get().verified);assertFalse(state.get().busy);assertFalse(state.get().needsCommunity);
            assertTrue("Directory must be readable: "+state.get().status,
                    state.get().status.equals("Up to date")||state.get().status.equals("No compatible lobbies right now."));
        }
        AtomicReference<LobbyRoomModel> ref=new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->ref.set(LobbyRoomModel.get(app())));
        LobbyRoomModel model=ref.get();String owned="";
        try(ActivityScenario<LobbyRoomActivity> screen=ActivityScenario.launch(LobbyRoomActivity.class)){
            LobbyRoomModel.State initial=await(model,s->s.verified,30000);assertTrue("Never replace an existing room",initial.room.isEmpty());
            if(role.equals("host")){
                model.action(account,r->r.create("private"));owned=await(model,s->s.hosting,10000).room;
                model.action(account,r->r.invite(peer));
            }else{
                LobbyRoomModel.State invited=await(model,s->s.invitations.stream().anyMatch(i->peer.equals(text(i,"host"))),120000);
                JsonObject offer=invited.invitations.stream().filter(i->peer.equals(text(i,"host"))).findFirst().get();
                owned=text(offer,"lobby");model.action(account,r->r.answer(text(offer,"key"),true));
            }
            String room=owned;
            await(model,s->s.room.equals(room)&&s.available&&"friend".equals(phase(s,peer)),180000);
            assertEquals(1,number(relationship(peer),"type"));screen.recreate();
            if(mode.equals("keep")){
                if(role.equals("guest"))model.keep(account,peer);
                await(model,s->!object(s.contacts,"peers").has(peer),60000);
            }
            if(role.equals("guest")){
                Thread.sleep(10000);model.action(account,LobbyRoom::leave);await(model,s->s.room.isEmpty(),10000);
            }else{
                await(model,s->s.snapshot.has("members")&&s.snapshot.getAsJsonArray("members").size()==1,120000);
                model.action(account,LobbyRoom::leave);await(model,s->s.room.isEmpty(),10000);
            }
            await(model,s->!object(s.contacts,"peers").has(peer),180000);
            assertEquals(mode.equals("keep")?1:-1,number(relationship(peer),"type"));
        }finally{
            String room=owned;if(!room.isEmpty())model.action(account,r->{if(room.equals(r.room()))r.leave();});
        }
    }
    @Test public void probeAuthorizedNonFriendConversation()throws Exception{
        Assume.assumeTrue("Explicit test-peer delivery probe required", "probe".equals(args.getString("fluxerContactSetup")));
        self();String name=args.getString("fluxerContactPeerName","");assertTrue(name.startsWith("abyssmetest"));
        String channel="";
        for(JsonElement value:request("GET","/v1/users/@me/channels").data.getAsJsonArray()){
            JsonObject c=value.getAsJsonObject();JsonElement recipients=c.get("recipients");
            if(number(c,"type")==1&&recipients!=null&&recipients.isJsonArray()&&recipients.getAsJsonArray().size()==1){
                JsonObject peer=recipients.getAsJsonArray().get(0).getAsJsonObject();
                if(name.equals(text(peer,"username"))){assertTrue(channel.isEmpty());channel=text(c,"id");}
            }
        }
        assertTrue("Only an existing named test conversation",LobbyDirectory.id(channel));
        JsonObject body=new JsonObject();body.addProperty("content","AbyssME non-friend delivery check (test accounts).");
        body.addProperty("nonce",uuid().replace("-",""));body.addProperty("flags",4096);
        JsonObject mentions=new JsonObject();mentions.add("parse",new JsonArray());body.add("allowed_mentions",mentions);
        FluxerAuth.Reply result=FluxerHttpTransport.exchange("POST","/v1/channels/"+channel+"/messages",body,token());
        Bundle report=new Bundle();report.putInt("delivery_status",result.status);
        report.putString("delivery_code",text(FluxerHttpTransport.errorCode(result.body),"code"));
        InstrumentationRegistry.getInstrumentation().sendStatus(2,report);
        assertTrue("HTTP response, not a transport failure",result.status>0);
    }
    private static String phase(LobbyRoomModel.State s,String peer){return text(object(object(s.contacts,"peers"),peer),"phase");}
    private static LobbyRoomModel.State await(LobbyRoomModel model,Predicate<LobbyRoomModel.State> condition,long timeout)throws Exception{
        long until=System.currentTimeMillis()+timeout;LobbyRoomModel.State last=null;
        do{last=model.state.getValue();if(last!=null&&condition.test(last))return last;Thread.sleep(300);}while(System.currentTimeMillis()<until);
        fail("Contact timeout: "+(last==null?"no state":last.status+"; "+last.contacts));return null;
    }
}
