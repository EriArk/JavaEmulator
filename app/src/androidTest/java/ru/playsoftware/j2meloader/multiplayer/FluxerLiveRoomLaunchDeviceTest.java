/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import android.app.*;
import android.graphics.Bitmap;
import android.os.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.gson.*;
import org.junit.*;
import java.io.*;
import java.util.function.Predicate;
import static org.junit.Assert.*;
import static ru.playsoftware.j2meloader.multiplayer.LobbyRoom.*;

/** Opt-in live private room -> real MIDlet process -> return check. Not a game match.
 * Uses two existing dedicated test friends; never exports credentials or game files. */
public class FluxerLiveRoomLaunchDeviceTest {
    @Test public void privateRoomLaunchAndProcessDeathReturn() throws Exception {
        Bundle args=InstrumentationRegistry.getArguments();String role=args.getString("peerRole",""),peer=args.getString("peerAccount",""),title=args.getString("launchGame","");
        Assume.assumeTrue("Explicit paired-device run required",(role.equals("host")||role.equals("guest"))&&LobbyDirectory.id(peer)&&!title.isEmpty());
        boolean host=role.equals("host");
        boolean normalExit="true".equals(args.getString("normalExit"));
        int rounds=Integer.parseInt(args.getString("launchRounds","1"));
        assertTrue(rounds>=1&&rounds<=3);
        int gameplaySeconds=Integer.parseInt(args.getString("gameplaySeconds","300"));
        assertTrue(gameplaySeconds>=30&&gameplaySeconds<=900);
        Application app=(Application)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        String token=new FluxerCredentialStore(app).read();assertNotNull(token);
        FluxerAuth.Reply me=FluxerHttpTransport.exchange("GET","/v1/users/@me",null,token);
        assertTrue(me.ok());assertTrue(text(me.body,"username").startsWith("abyssmetest"));String self=text(me.body,"id");
        FluxerAuth.Reply relationships=FluxerHttpTransport.exchange("GET","/v1/users/@me/relationships",null,token);assertTrue(relationships.ok());
        boolean friend=false;for(JsonElement e:relationships.data.getAsJsonArray()){JsonObject r=e.getAsJsonObject();if(text(r,"id").equals(peer))friend=number(r,"type")==1&&text(object(r,"user"),"username").startsWith("abyssmetest");}
        assertTrue("Existing dedicated test friend required",friend);
        NetworkGameCatalog.Entry selected=null;
        for(NetworkGameCatalog.Entry e:NetworkGameCatalog.list(new File(ru.playsoftware.j2meloader.config.Config.getAppDir())))if(e.title.equals(title)){assertNull("Ambiguous local title",selected);selected=e;}
        assertNotNull("Install the nominated local fixture first",selected);
        LobbyRoomModel[] holder={null};InstrumentationRegistry.getInstrumentation().runOnMainSync(()->holder[0]=LobbyRoomModel.get(app));LobbyRoomModel model=holder[0];
        boolean owned=false;
        try(ActivityScenario<LobbyRoomActivity> activity=ActivityScenario.launch(LobbyRoomActivity.class)){
            await(model,s->s.verified&&s.account.equals(self),30000);assertTrue("Do not replace an existing room",model.state.getValue().room.isEmpty());
            if(host){model.action(self,r->{r.create("private");r.invite(peer);});owned=true;}
            else{
                await(model,s->s.invitations.stream().anyMatch(i->text(i,"host").equals(peer)),90000);
                String key=model.state.getValue().invitations.stream().filter(i->text(i,"host").equals(peer)).map(i->text(i,"key")).findFirst().get();
                model.action(self,r->r.answer(key,true));owned=true;
            }
            await(model,s->s.joined&&s.snapshot.getAsJsonArray("members").size()==2,90000);
            String room=model.state.getValue().room;report(role+": admitted");
            for(int round=1;round<=rounds;round++){
            long previousGeneration=number(model.state.getValue().snapshot,"generation");
            boolean repeat=round>1;
            if(!host)await(model,s->s.snapshot.has("game")&&(!repeat||number(s.snapshot,"generation")>previousGeneration),90000);
            model.chooseGame(self,selected);
            await(model,s->bool(member(s.snapshot,self),"prepared"),120000);report(role+": verified local game and encrypted peer prepared");
            model.action(self,r->r.ready(true));
            if(host){await(model,s->bool(member(s.snapshot,self),"ready")&&bool(member(s.snapshot,peer),"ready"),90000);capture(app,role+"-ready.png");model.action(self,LobbyRoom::start);}
            await(model,s->bool(object(s.snapshot,"launch"),"running"),45000);
            awaitProcess(app,15000);Thread.sleep(4000);capture(app,role+"-midlet-"+round+".png");report(role+": round "+round+" both processes attached; MIDlet visible");
            if(!normalExit&&host){Thread.sleep(6000);killGame(app);}
            // Optional manual gameplay window: external input drives the unmodified game.
            // Returning to the room is lifecycle evidence, not an automatic match assertion.
            await(model,s->s.room.equals(room)&&!s.snapshot.has("launch"),normalExit?gameplaySeconds*1000L:45000);
            if(!normalExit&&!host)killGame(app);
            long exitDeadline=SystemClock.elapsedRealtime()+15000;
            while(process(app)!=0&&SystemClock.elapsedRealtime()<exitDeadline)Thread.sleep(100);
            assertEquals("Game process must exit",0,process(app));
            await(model,s->!bool(member(s.snapshot,self),"ready")&&!bool(member(s.snapshot,peer),"ready"),10000);
            assertFalse("Returning players must not be told to rejoin an intact room",model.state.getValue().status.contains("Rejoin"));
            Thread.sleep(1500);capture(app,role+"-returned-"+round+".png");report(role+": round "+round+" returned both seats with fresh readiness");
            if(round<rounds)Thread.sleep(host?12000:1000);
            }
            if(host)await(model,s->s.snapshot.has("members")&&s.snapshot.getAsJsonArray("members").size()==1,45000);
            model.action(self,LobbyRoom::leave);await(model,s->s.room.isEmpty(),5000);Thread.sleep(3000);owned=false;
        }finally{
            if(owned){killGame(app);model.action(self,LobbyRoom::leave);Thread.sleep(2000);}
        }
    }
    private static void await(LobbyRoomModel model,Predicate<LobbyRoomModel.State> predicate,long timeout)throws Exception{
        long end=SystemClock.elapsedRealtime()+timeout,nextDiagnostic=SystemClock.elapsedRealtime()+15000;
        while(SystemClock.elapsedRealtime()<end){
            LobbyRoomModel.State s=model.state.getValue();if(s!=null&&predicate.test(s))return;
            if(SystemClock.elapsedRealtime()>=nextDiagnostic){reportDiagnostic(model);nextDiagnostic=SystemClock.elapsedRealtime()+15000;}
            Thread.sleep(100);
        }
        LobbyRoomModel.State s=model.state.getValue();fail("Room timeout: "+(s==null?"no state":s.status+" / "+s.snapshot));
    }
    private static Object field(Object object,String name)throws Exception{
        java.lang.reflect.Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);
    }
    private static void reportDiagnostic(LobbyRoomModel model)throws Exception{
        java.util.concurrent.ScheduledThreadPoolExecutor owner=(java.util.concurrent.ScheduledThreadPoolExecutor)field(model,"owner");
        String diagnostic=owner.submit(()->{
            java.util.concurrent.ScheduledFuture<?> timer=(java.util.concurrent.ScheduledFuture<?>)field(model,"networkTimer");
            if(timer!=null&&timer.isDone()&&!timer.isCancelled())timer.get();
            Object coordinator=field(model,"gameNetwork");
            Object link=coordinator==null?null:field(coordinator,"link");
            String transport="";
            if(link!=null)for(java.lang.reflect.Field f:link.getClass().getDeclaredFields()){
                if(f.getType()!=BluetoothPeerSession.class)continue;
                f.setAccessible(true);Object peer=field(f.get(link),"peer");
                transport=", description="+field(peer,"remoteDescription")+", hello="+field(peer,"helloSent")+", peerClosed="+field(peer,"closed");
            }
            Object mailbox=field(model,"mailbox");
            return "waiting: networkTimer="+(timer==null?"none":timer.isCancelled()?"cancelled":"active")
                    +", local="+(coordinator!=null&&field(coordinator,"local")!=null)
                    +", link="+(coordinator!=null&&field(coordinator,"link")!=null)
                    +", failed="+(coordinator!=null&&field(coordinator,"failed")!=null)
                    +", queued="+((java.util.Collection<?>)field(mailbox,"queue")).size()
                    +", mailbox="+field(mailbox,"active")+"/"+field(mailbox,"polling")+"/"+field(mailbox,"sending")+transport;
        }).get(5,java.util.concurrent.TimeUnit.SECONDS);
        report(diagnostic);
    }
    private static int process(Application app){
        for(ActivityManager.RunningAppProcessInfo p:app.getSystemService(ActivityManager.class).getRunningAppProcesses())if(p.uid==android.os.Process.myUid()&&p.processName.equals(app.getPackageName()+":midlet"))return p.pid;
        return 0;
    }
    private static void awaitProcess(Application app,long timeout)throws Exception{long end=SystemClock.elapsedRealtime()+timeout;while(process(app)==0&&SystemClock.elapsedRealtime()<end)Thread.sleep(100);assertTrue("Separate MIDlet process missing",process(app)>0);}
    private static void killGame(Application app){int pid=process(app);if(pid>0)android.os.Process.killProcess(pid);}
    private static void report(String phase){Bundle status=new Bundle();status.putString("room_launch",phase);InstrumentationRegistry.getInstrumentation().sendStatus(2,status);}
    private static void capture(Application app,String name)throws Exception{
        Bitmap image=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(image);
        File dir=new File(app.getExternalFilesDir(null),"room-launch-checks");assertTrue(dir.isDirectory()||dir.mkdirs());
        try(FileOutputStream out=new FileOutputStream(new File(dir,name))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));}finally{image.recycle();}
    }
}
