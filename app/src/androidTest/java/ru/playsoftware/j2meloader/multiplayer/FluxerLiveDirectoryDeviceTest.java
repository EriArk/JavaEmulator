/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import android.app.Application;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.gson.*;
import org.junit.Assume;
import org.junit.Test;
import java.io.File;
import java.io.FileOutputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import static org.junit.Assert.*;
import static ru.playsoftware.j2meloader.multiplayer.LobbyRoom.*;

/** Real shared service and normal app actors. Opt-in dedicated accounts only; no token export. */
public class FluxerLiveDirectoryDeviceTest {
    private final Bundle args=InstrumentationRegistry.getArguments();
    private String credential, botChannel;
    private Application app(){return (Application)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();}
    private FluxerAuth.Reply request(String method,String path,JsonObject body)throws Exception{
        FluxerAuth.Reply result=FluxerHttpTransport.exchange(method,path,body,credential);
        assertTrue("Provider HTTP "+result.status+" "+text(FluxerHttpTransport.errorCode(result.body),"code"),result.ok());return result;
    }
    private JsonObject command(int version,String id,long revision,String op){
        JsonObject c=new JsonObject();c.addProperty("v",version);c.addProperty("lobby",id);
        c.addProperty("revision",revision);c.addProperty("op",op);
        if(version==2){c.addProperty("product","abyssme");c.addProperty("protocol",1);c.addProperty("request",uuid());}
        if(op.equals("upsert")){
            c.addProperty("topic","choosing");c.addProperty("status","choosing");c.addProperty("access","public");
            c.addProperty("seats",0);c.addProperty("available",0);c.addProperty("streams",0);
            if(version==1){c.addProperty("title","AbyssME coexistence check (temporary)");c.addProperty("members",1);}
        }
        return c;
    }
    private void send(JsonObject command)throws Exception{
        JsonObject body=new JsonObject();body.addProperty("content",LobbyDirectory.PREFIX+command+"\n```");
        body.addProperty("nonce",uuid().replace("-",""));body.addProperty("flags",4096);
        JsonObject mentions=new JsonObject();mentions.add("parse",new JsonArray());body.add("allowed_mentions",mentions);
        request("POST","/v1/channels/"+botChannel+"/messages",body);
    }
    private void result(JsonObject command,String expected)throws Exception{
        send(command);long until=SystemClock.elapsedRealtime()+60000;
        do{
            Thread.sleep(4000);
            for(JsonElement e:request("GET","/v1/channels/"+botChannel+"/messages?limit=50",null).data.getAsJsonArray()){
                JsonObject m=e.getAsJsonObject();String content=text(m,"content");
                if(!LobbyDirectory.BOT.equals(text(object(m,"author"),"id"))||!content.startsWith(LobbyPublisher.RESULT_PREFIX)||!content.endsWith("\n```"))continue;
                JsonObject r=JsonParser.parseString(content.substring(LobbyPublisher.RESULT_PREFIX.length(),content.length()-4)).getAsJsonObject();
                if(text(command,"request").equals(text(r,"request"))){assertEquals(expected,text(r,"result"));return;}
            }
        }while(SystemClock.elapsedRealtime()<until);
        fail("Missing scoped service result: "+expected);
    }
    private Map<String,JsonObject> listings()throws Exception{
        Map<String,JsonObject> out=new HashMap<>();String channel=LobbyDirectory.CHANNELS.get(0);
        for(JsonElement e:request("GET","/v1/channels/"+channel+"/messages?limit=50",null).data.getAsJsonArray()){
            JsonObject message=e.getAsJsonObject();String content=text(message,"content");
            if(!LobbyDirectory.BOT.equals(text(object(message,"author"),"id"))||!content.startsWith(LobbyDirectory.PREFIX)||!content.endsWith("\n```"))continue;
            JsonObject value=JsonParser.parseString(content.substring(LobbyDirectory.PREFIX.length(),content.length()-4)).getAsJsonObject();
            out.put(value.has("listing")?text(value,"listing"):text(value,"lobby"),message);
        }
        return out;
    }
    private JsonObject visible(String id,boolean expected)throws Exception{
        long until=SystemClock.elapsedRealtime()+45000;
        do{JsonObject m=listings().get(id);if((m!=null)==expected)return m;Thread.sleep(4000);}while(SystemClock.elapsedRealtime()<until);
        fail("Listing visibility did not become "+expected);return null;
    }
    @Test public void publicRoomCoexistsWithLegacyListings()throws Exception{
        String role=args.getString("fluxerDirectoryRole",""),peer=args.getString("fluxerDirectoryPeer","");
        Assume.assumeTrue("Explicit paired test role and account required",(role.equals("host")||role.equals("guest"))&&LobbyDirectory.id(peer));
        credential=new FluxerCredentialStore(app()).read();assertNotNull(credential);
        JsonObject me=request("GET","/v1/users/@me",null).body;
        assertTrue("Dedicated account only",text(me,"username").startsWith("abyssmetest"));String account=text(me,"id");
        boolean friend=false;
        for(JsonElement e:request("GET","/v1/users/@me/relationships",null).data.getAsJsonArray()){
            JsonObject r=e.getAsJsonObject();if(peer.equals(text(r,"id")))friend=number(r,"type")==1&&text(object(r,"user"),"username").startsWith("abyssmetest");
        }
        assertTrue("Use only the named existing test friend",friend);
        if(role.equals("host")){
            JsonObject recipient=new JsonObject();recipient.addProperty("recipient_id",LobbyDirectory.BOT);
            botChannel=text(request("POST","/v1/users/@me/channels",recipient).body,"id");assertTrue(LobbyDirectory.id(botChannel));
        }
        AtomicReference<LobbyRoomModel> ref=new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->ref.set(LobbyRoomModel.get(app())));
        LobbyRoomModel model=ref.get();String owned="";List<String> legacy=new ArrayList<>();
        // ActivityScenario starts a new task. Finish the directory scenario before
        // launching the room, so the join happens in an actually resumed app.
        String selected=role.equals("guest")?findPublicRoom(peer,args.getString("fluxerDirectoryRoom","")):"";
        try(ActivityScenario<LobbyRoomActivity> screen=ActivityScenario.launch(LobbyRoomActivity.class)){
            assertTrue("Never replace a current room",await(model,s->s.verified,30000).room.isEmpty());
            try{
                if(role.equals("host")){
                    // Same authenticated owner can retain multiple TrainerOS meetings and one AbyssME room.
                    for(int i=0;i<2;i++){String id=uuid();legacy.add(id);JsonObject c=command(1,id,1,"upsert");c.addProperty("room",uuid());send(c);}
                    for(String id:legacy){JsonObject m=visible(id,true);
                        assertNull("AbyssME must ignore actual v1 announcements",LobbyDirectory.parse(m,LobbyDirectory.CHANNELS.get(0),System.currentTimeMillis()/1000,SystemClock.elapsedRealtime()));}
                    model.action(account,r->r.create("public"));owned=await(model,s->s.hosting,10000).room;
                    Bundle report=new Bundle();report.putString("public_test_room",owned);InstrumentationRegistry.getInstrumentation().sendStatus(2,report);
                    await(model,s->s.publication.equals("Public listing accepted by the directory"),90000);visible(owned,true);
                    await(model,s->members(s)==2&&s.available,180000);screen.recreate();
                    await(model,s->members(s)==2&&s.available,30000);capture("host-public-room.png");
                    await(model,s->members(s)==1,120000);
                    result(command(2,uuid(),1,"upsert"),"already-listed");
                    result(command(2,legacy.get(0),2,"withdraw"),"wrong-product");
                    for(String id:legacy)visible(id,true);
                    model.action(account,r->r.access("private"));
                    await(model,s->s.publication.equals("Listing withdrawal accepted"),90000);visible(owned,false);
                    for(String id:legacy)visible(id,true);
                    model.action(account,r->r.access("public"));
                    await(model,s->s.publication.equals("Public listing accepted by the directory"),90000);visible(owned,true);
                    model.action(account,LobbyRoom::leave);await(model,s->s.room.isEmpty(),10000);visible(owned,false);
                    send(command(1,legacy.get(0),2,"withdraw"));visible(legacy.get(0),false);
                    send(command(1,legacy.get(0),3,"upsert"));visible(legacy.get(0),true);
                }else{
                    owned=selected;
                    String id=owned;model.action(account,r->r.join(peer,id,""));
                    await(model,s->s.room.equals(id)&&s.available&&members(s)==2,90000);
                    capture("guest-public-room.png");
                    // Browsing/joining must not require a bot DM. The guest account can
                    // be subject to provider restrictions on initiating new conversations.
                    visible(id,true);
                    Thread.sleep(15000);model.action(account,LobbyRoom::leave);await(model,s->s.room.isEmpty(),10000);
                }
            }finally{
                String id=owned;if(!id.isEmpty())model.action(account,r->{if(id.equals(r.room()))r.leave();});
                for(String listing:legacy)send(command(1,listing,10000,"close"));
                for(String listing:legacy)visible(listing,false);
            }
        }
    }
    private String findPublicRoom(String peer,String expected)throws Exception{
        try(ActivityScenario<LobbyDirectoryActivity> directory=ActivityScenario.launch(LobbyDirectoryActivity.class)){
            AtomicReference<LobbyDirectory.State> state=new AtomicReference<>();long until=SystemClock.elapsedRealtime()+150000;
            do{directory.onActivity(a->state.set(new ViewModelProvider(a).get(LobbyDirectoryModel.class).state.getValue()));
                if(state.get()!=null&&!state.get().busy&&state.get().rooms.stream().anyMatch(r->r.owner.equals(peer)&&(expected.isEmpty()||r.id.equals(expected))))break;
                Thread.sleep(500);
            }while(SystemClock.elapsedRealtime()<until);
            assertNotNull(state.get());assertTrue("Directory must finish successfully: "+state.get().status,state.get().status.equals("Up to date"));
            List<LobbyDirectory.Room> matches=new ArrayList<>();for(LobbyDirectory.Room r:state.get().rooms)if(r.owner.equals(peer))matches.add(r);
            assertEquals("Two legacy meetings must not leak into AbyssME",1,matches.size());
            if(!expected.isEmpty())assertEquals(expected,matches.get(0).id);
            capture("guest-public-directory.png");return matches.get(0).id;
        }
    }
    private static int members(LobbyRoomModel.State s){return s.snapshot.has("members")?s.snapshot.getAsJsonArray("members").size():0;}
    private static LobbyRoomModel.State await(LobbyRoomModel model,Predicate<LobbyRoomModel.State> predicate,long timeout)throws Exception{
        long until=SystemClock.elapsedRealtime()+timeout;LobbyRoomModel.State last=null;
        do{last=model.state.getValue();if(last!=null&&predicate.test(last))return last;Thread.sleep(300);}while(SystemClock.elapsedRealtime()<until);
        fail("Public room timeout: "+(last==null?"no state":last.status+"; "+last.publication+"; members="+members(last)));return null;
    }
    private void capture(String name)throws Exception{
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();Bitmap image=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(image);
        File dir=new File(app().getExternalFilesDir(null),"fluxer-directory-checks");assertTrue(dir.isDirectory()||dir.mkdirs());
        try(FileOutputStream output=new FileOutputStream(new File(dir,name))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,output));}finally{image.recycle();}
    }
}
