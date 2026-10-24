/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import android.app.*;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.lifecycle.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.gson.*;
import org.junit.Test;
import java.io.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.concurrent.atomic.AtomicReference;
import ru.playsoftware.j2meloader.R;
import static org.junit.Assert.*;

/** Real native UI; directory entries and capability are test-only, not real lobbies. */
public class LobbyDirectoryDeviceTest {
    private static final class Fixture implements FluxerAuth.Store,FluxerAuth.Transport,LobbyDirectory.Membership,Application.ActivityLifecycleCallbacks,AutoCloseable {
        final Application app=(Application)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        final long expires=System.currentTimeMillis()/1000+180;
        volatile String token="fixture-directory-session";volatile int code=200,requests,posts,joinCode=200;
        final Set<String> attempts=new HashSet<>();
        public boolean claim(String account){return attempts.add(account);}
        Fixture(){app.registerActivityLifecycleCallbacks(this);}
        public String read(){return token;}public void save(String t){throw new AssertionError("No credential writes");}public void clear(){throw new AssertionError("No credential removal");}
        public void request(String method,String path,JsonObject body,String token,java.util.function.Consumer<FluxerAuth.Reply> done){
            requests++;
            if(path.equals(LobbyDirectory.INVITE_PATH)){
                JsonObject invite=JsonParser.parseString("{\"type\":0,\"code\":\"kOYY3hzY\",\"guild\":{\"id\":\""+LobbyDirectory.GUILD+"\"}}").getAsJsonObject();
                if(method.equals("POST")){assertEquals(new JsonObject(),body);posts++;if(joinCode==200)code=200;done.accept(new FluxerAuth.Reply(joinCode,invite,0));}
                else {assertEquals("GET",method);assertNull(body);done.accept(new FluxerAuth.Reply(200,invite,0));}
                return;
            }
            assertEquals("GET",method);assertNull(body);
            if(path.equals("/v1/users/@me")){done.accept(new FluxerAuth.Reply(200,JsonParser.parseString("{\"id\":\"11\"}"),0));return;}
            if(code!=200){done.accept(new FluxerAuth.Reply(code,null,0));return;}
            JsonArray page=new JsonArray();if(path.contains(LobbyDirectory.CHANNELS.get(0))){page.add(entry(4,false,0));page.add(entry(3,true,0));page.add(entry(2,true,1));JsonObject legacy=entry(1,false,0);String content=legacy.get("content").getAsString();legacy.addProperty("content",content.replace("\"v\":2","\"v\":1"));page.add(legacy);}
            done.accept(new FluxerAuth.Reply(200,page,0));
        }
        JsonObject entry(int n,boolean game,int available){JsonObject v=new JsonObject();v.addProperty("v",2);v.addProperty("product","abyssme");v.addProperty("protocol",1);v.addProperty("lobby",String.format(Locale.ROOT,"00000000-0000-4000-8000-%012d",n));v.addProperty("owner",Integer.toString(20+n));v.addProperty("name",n==4?"Alex - UI fixture":n==3?"Sam - UI fixture":"Jamie - UI fixture");v.addProperty("revision",1);v.addProperty("status",game?"gathering":"choosing");v.addProperty("access",n==2?"approval":"public");v.addProperty("seats",game?2:0);v.addProperty("available",available);v.addProperty("streams",0);v.addProperty("expires",expires);
            if(game){JsonObject g=new JsonObject();g.addProperty("id",String.join("",Collections.nCopies(64,"a")));g.addProperty("title",n==3?"Full room - UI fixture":"Java link - UI fixture");g.addProperty("platform","j2me");g.addProperty("mode","jsr82-l2cap");g.addProperty("adapter","1");v.add("game",g);}
            JsonObject m=new JsonObject();m.addProperty("id",Integer.toString(100+n));m.addProperty("channel_id",LobbyDirectory.CHANNELS.get(0));m.addProperty("content",LobbyDirectory.PREFIX+v+"\n```");JsonObject author=new JsonObject();author.addProperty("id",LobbyDirectory.BOT);m.add("author",author);return m;
        }
        public void onActivityPreCreated(Activity a,Bundle saved){if(a instanceof LobbyDirectoryActivity)new ViewModelProvider((LobbyDirectoryActivity)a,new ViewModelProvider.Factory(){public <T extends ViewModel>T create(Class<T> type){return type.cast(new LobbyDirectoryModel(app,Fixture.this,Fixture.this,Collections.singleton("j2me/jsr82-l2cap/1"),Fixture.this));}}).get(LobbyDirectoryModel.class);}
        public void onActivityCreated(Activity a,Bundle b){}public void onActivityStarted(Activity a){}public void onActivityResumed(Activity a){}public void onActivityPaused(Activity a){}public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){}
        public void close(){app.unregisterActivityLifecycleCallbacks(this);}
    }
    @Test public void signedOutDoesNotBrowseOrJoin() throws Exception {
        try(Fixture f=new Fixture()){f.token=null;try(ActivityScenario<LobbyDirectoryActivity> s=ActivityScenario.launch(LobbyDirectoryActivity.class)){
            await(s,x->!x.busy&&!x.verified);s.onActivity(a->{assertTrue(a.findViewById(R.id.lobby_account).isShown());assertFalse(a.findViewById(R.id.lobby_community).isShown());});assertEquals(0,f.requests);
        }}
    }
    @Test public void scopedRoomsSearchAndSeatsFitBothOrientations() throws Exception {
        try(Fixture f=new Fixture();ActivityScenario<LobbyDirectoryActivity> s=ActivityScenario.launch(LobbyDirectoryActivity.class)){
            await(s,x->!x.busy&&x.rooms.size()==3);s.onActivity(a->a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));Thread.sleep(1400);count(s,3);keyboard(s,false);capture("lobbies-portrait-fixture.png");
            s.onActivity(a->((CheckBox)a.findViewById(R.id.lobby_free)).setChecked(true));Thread.sleep(200);count(s,2);
            s.onActivity(a->((EditText)a.findViewById(R.id.lobby_search)).setText("Java"));Thread.sleep(200);count(s,1);
            s.onActivity(a->{((EditText)a.findViewById(R.id.lobby_search)).setText("");a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);});Thread.sleep(1400);count(s,2);keyboard(s,false);capture("lobbies-landscape-fixture.png");
            s.onActivity(a->a.getDelegate().setLocalNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO));Thread.sleep(1400);count(s,2);capture("lobbies-light-fixture.png");
            s.onActivity(a->{EditText input=a.findViewById(R.id.lobby_search);input.requestFocus();((android.view.inputmethod.InputMethodManager)a.getSystemService(Activity.INPUT_METHOD_SERVICE)).showSoftInput(input,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);});Thread.sleep(800);keyboard(s,true);count(s,2);
            s.onActivity(a->{a.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_BUTTON_B));a.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP,android.view.KeyEvent.KEYCODE_BUTTON_B));});Thread.sleep(800);keyboard(s,false);
        }
    }
    @Test public void providerFailureRemovesOldListingsAndLogoutClearsAccount() throws Exception {
        try(Fixture f=new Fixture();ActivityScenario<LobbyDirectoryActivity> s=ActivityScenario.launch(LobbyDirectoryActivity.class)){
            await(s,x->!x.busy&&x.rooms.size()==3);f.code=500;s.onActivity(a->a.findViewById(R.id.lobby_refresh).performClick());await(s,x->!x.busy&&x.rooms.isEmpty());count(s,0);capture("lobbies-offline-fixture.png");
            f.token=null;s.onActivity(a->a.findViewById(R.id.lobby_refresh).performClick());await(s,x->!x.verified);count(s,0);
        }
    }
    @Test public void failedAutomaticJoinShowsRetryAndRetryRecovers() throws Exception {
        try(Fixture f=new Fixture()){f.code=403;f.joinCode=403;try(ActivityScenario<LobbyDirectoryActivity> s=ActivityScenario.launch(LobbyDirectoryActivity.class)){
            await(s,x->!x.busy&&x.needsCommunity);s.onActivity(a->assertTrue(a.findViewById(R.id.lobby_community).isShown()));count(s,0);capture("lobbies-community-fixture.png");assertEquals(1,f.posts);
            f.joinCode=200;s.onActivity(a->a.findViewById(R.id.lobby_community).performClick());await(s,x->!x.busy&&x.rooms.size()==3);assertEquals(2,f.posts);
        }}
    }
    @Test public void automaticJoinReadsCatalogWithoutExtraButton() throws Exception {
        try(Fixture f=new Fixture()){f.code=403;try(ActivityScenario<LobbyDirectoryActivity> s=ActivityScenario.launch(LobbyDirectoryActivity.class)){
            await(s,x->!x.busy&&x.rooms.size()==3);assertEquals(1,f.posts);s.recreate();await(s,x->!x.busy&&x.rooms.size()==3);assertEquals(1,f.posts);
        }}
    }
    @Test public void membershipAttemptSurvivesNewStoreAndRejectsPaths() throws Exception {
        File root=new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir(),"membership-test-"+UUID.randomUUID());
        try{assertTrue(new LobbyMembershipStore(root).claim("11"));assertFalse(new LobbyMembershipStore(root).claim("11"));assertTrue(new LobbyMembershipStore(root).claim("12"));
            try{new LobbyMembershipStore(root).claim("../13");fail("Path accepted");}catch(IOException expected){}
        }finally{for(String name:new String[]{"11","12"}){File marker=new File(root,name);if(marker.exists())assertTrue(marker.delete());}if(root.exists())assertTrue(root.delete());}
    }
    @Test public void transportAllowsOnlyKnownAuthenticatedCommunityInvite(){
        String token="fixture-directory-session";
        assertTrue(FluxerHttpTransport.allowed("GET",LobbyDirectory.INVITE_PATH,token));assertTrue(FluxerHttpTransport.allowed("POST",LobbyDirectory.INVITE_PATH,token));
        assertFalse(FluxerHttpTransport.allowed("POST",LobbyDirectory.INVITE_PATH,null));assertFalse(FluxerHttpTransport.allowed("POST","/v1/invites/another",token));
        assertFalse(FluxerHttpTransport.allowed("POST",LobbyDirectory.INVITE_PATH+"?redirect=evil",token));assertFalse(FluxerHttpTransport.allowed("DELETE",LobbyDirectory.INVITE_PATH,token));
    }
    private static void count(ActivityScenario<LobbyDirectoryActivity> s,int expected){s.onActivity(a->{androidx.recyclerview.widget.RecyclerView list=a.findViewById(R.id.lobby_list);assertEquals(expected,list.getAdapter().getItemCount());assertTrue(list.getBottom()<=((View)list.getParent()).getHeight());});}
    private static void keyboard(ActivityScenario<LobbyDirectoryActivity> s,boolean visible){s.onActivity(a->{androidx.core.view.WindowInsetsCompat insets=androidx.core.view.ViewCompat.getRootWindowInsets(a.findViewById(R.id.lobby_search));assertNotNull(insets);assertEquals(visible,insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()));});}
    private static void await(ActivityScenario<LobbyDirectoryActivity> s,Predicate<LobbyDirectory.State> ready) throws Exception {for(int i=0;i<100;i++){AtomicReference<LobbyDirectory.State> state=new AtomicReference<>();s.onActivity(a->state.set(new ViewModelProvider(a).get(LobbyDirectoryModel.class).state.getValue()));if(state.get()!=null&&ready.test(state.get())){Thread.sleep(150);return;}Thread.sleep(50);}fail("Directory state timeout");}
    private static void capture(String name) throws Exception {Thread.sleep(500);InstrumentationRegistry.getInstrumentation().waitForIdleSync();Bitmap image=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(image);File dir=new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null),"lobby-directory-checks");assertTrue(dir.isDirectory()||dir.mkdirs());try(FileOutputStream out=new FileOutputStream(new File(dir,name))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));}image.recycle();}
}
