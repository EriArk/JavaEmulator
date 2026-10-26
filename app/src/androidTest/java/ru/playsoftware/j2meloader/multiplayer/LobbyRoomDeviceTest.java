/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import android.app.Application;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.lifecycle.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.gson.*;
import org.junit.Test;
import java.io.*;
import java.util.*;
import java.util.function.Predicate;
import static org.junit.Assert.*;
import static ru.playsoftware.j2meloader.multiplayer.LobbyRoom.*;

/** Native UI + real room/mailbox actors. Only the HTTP provider and partner are fixtures. */
public class LobbyRoomDeviceTest {
    static final class Fixture implements AutoCloseable,FluxerAuth.Store,FluxerAuth.Transport {
        final Application app=(Application)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        LobbyRoomModel model;LobbyRoom guest;volatile String token="room-fixture-token";volatile int posts;String botResult="accepted";
        long message=1000;JsonArray inbox=new JsonArray(),botInbox=new JsonArray();boolean autoAccept=true;
        final boolean temporary;TemporaryContacts guestContacts;int relationshipType,contactPosts,contactDeletes;String since="";
        Fixture()throws Exception{this(false);}
        Fixture(boolean temporary)throws Exception{
            this.temporary=temporary;
            org.junit.Assume.assumeTrue("Do not disturb an authenticated development room",new FluxerCredentialStore(app).read()==null);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                LobbyRoomModel.STORE.clear();model=new ViewModelProvider(LobbyRoomModel.STORE,new ViewModelProvider.Factory(){public <T extends ViewModel>T create(Class<T> type){return type.cast(new LobbyRoomModel(app,Fixture.this,Fixture.this,Collections.emptySet(),account->new LobbyPublisher.Store(){JsonObject record=new JsonObject();public JsonObject read(){return record.deepCopy();}public void write(JsonObject v){record=v.deepCopy();}}));}}).get(LobbyRoomModel.class);
            });
        }
        public String read(){return token;}public void save(String t){throw new AssertionError();}public void clear(){throw new AssertionError();}
        JsonObject channel(String peer){return JsonParser.parseString("{\"id\":\""+(peer.equals("22")?"101":"102")+"\",\"type\":1,\"last_message_id\":\"9999\",\"recipients\":[{\"id\":\""+peer+"\",\"username\":\""+(peer.equals("22")?"Alex - room fixture":"Directory fixture")+"\"}]}").getAsJsonObject();}
        JsonObject message(String peer,String content){JsonObject m=new JsonObject();m.addProperty("id",Long.toString(++message));m.addProperty("channel_id",peer.equals(LobbyDirectory.BOT)?"102":"101");m.addProperty("content",content);JsonObject author=new JsonObject();author.addProperty("id",peer);m.add("author",author);return m;}
        public void request(String method,String path,JsonObject body,String token,java.util.function.Consumer<FluxerAuth.Reply> done){
            if(guest==null)guest=new LobbyRoom("22",()->System.currentTimeMillis()/1000,Collections.emptySet(),(peer,p)->inbox.add(message("22",PREFIX+p+"\n```")),()->{});
            if(temporary&&guestContacts==null)guestContacts=new TemporaryContacts("22",new TemporaryContacts.Journal(){JsonObject saved;public JsonObject load(){return saved;}public void save(JsonObject s){saved=s.deepCopy();}},(m,p,b,c)->{
                JsonArray relationships=new JsonArray();if(relationshipType>0)relationships.add(relation("11",relationshipType==4?3:relationshipType));
                if(m.equals("GET"))c.accept(new TemporaryContacts.Reply(200,relationships,0));
                else{assertEquals("PUT",m);relationshipType=1;since=java.time.Instant.now().toString();c.accept(new TemporaryContacts.Reply(200,relation("11",1),0));}
            },(peer,p)->inbox.add(message("22",LobbyMailbox.CONTACT_PREFIX+p+"\n```")),System.currentTimeMillis()/1000);
            if(temporary){Set<String> members=new HashSet<>();if(guest.joined())for(JsonElement e:guest.snapshot().getAsJsonArray("members"))members.add(text(e.getAsJsonObject(),"id"));guestContacts.context(guest.room(),guest.host(),members,true,false);guestContacts.tick(System.currentTimeMillis()/1000);}
            JsonElement response;
            if(path.equals("/v1/users/@me"))response=JsonParser.parseString("{\"id\":\"11\"}");
            else if(path.endsWith("relationships")){JsonArray a=new JsonArray();if(!temporary||relationshipType>0)a.add(relation("22",temporary?relationshipType:1));response=a;}
            else if(path.endsWith("relationships/22")){assertTrue(temporary);if(method.equals("POST")){contactPosts++;relationshipType=4;since=java.time.Instant.now().toString();response=relation("22",4);}else{assertEquals("DELETE",method);contactDeletes++;relationshipType=0;done.accept(new FluxerAuth.Reply(204,null,0));return;}}
            else if(path.equals("/v1/users/@me/channels")){if(method.equals("POST"))response=channel(text(body,"recipient_id"));else{JsonArray a=new JsonArray();a.add(channel("22"));a.add(channel(LobbyDirectory.BOT));response=a;}}
            else if(method.equals("GET")){guest.tick();response=(path.contains("/102/")?botInbox:inbox).deepCopy();}
            else{
                posts++;String content=text(body,"content");response=message("11",content);response.getAsJsonObject().addProperty("channel_id",path.contains("/102/")?"102":"101");
                if(content.startsWith(PREFIX)){
                    JsonObject packet=JsonParser.parseString(content.substring(PREFIX.length(),content.length()-4)).getAsJsonObject();guest.receive("11",packet);
                    if(autoAccept&&text(packet,"kind").equals("invite")&&!guest.invitations().isEmpty())guest.answer(text(guest.invitations().get(0),"key"),true);
                }else if(content.startsWith(LobbyMailbox.CONTACT_PREFIX)&&temporary){guestContacts.receive("11",JsonParser.parseString(content.substring(LobbyMailbox.CONTACT_PREFIX.length(),content.length()-4)).getAsJsonObject());
                }else if(content.startsWith(LobbyDirectory.PREFIX)){
                    JsonObject result=JsonParser.parseString(content.substring(LobbyDirectory.PREFIX.length(),content.length()-4)).getAsJsonObject();result.addProperty("owner","11");result.addProperty("expires",System.currentTimeMillis()/1000+90);result.addProperty("result",text(result,"op").equals("close")?"closed":text(result,"op").equals("withdraw")?"withdrawn":botResult);botInbox.add(message(LobbyDirectory.BOT,LobbyPublisher.RESULT_PREFIX+result+"\n```"));
                }
            }
            done.accept(new FluxerAuth.Reply(200,response,0));
        }
        JsonObject relation(String id,int type){JsonObject r=new JsonObject();r.addProperty("id",id);r.addProperty("type",type);r.addProperty("since",since);JsonObject u=new JsonObject();u.addProperty("id",id);u.addProperty("username","Alex - room fixture");r.add("user",u);return r;}
        public void close(){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->LobbyRoomModel.STORE.clear());}
    }
    @Test public void nativeCreateInviteAdmissionRotationAndClose()throws Exception{
        try(Fixture f=new Fixture();ActivityScenario<LobbyRoomActivity> s=ActivityScenario.launch(LobbyRoomActivity.class)){
            await(f,x->x.verified&&!x.friends.isEmpty());click(s,"create");clickText("Invite only");await(f,x->x.hosting);
            click(s,"invite");clickText("Alex - room fixture");await(f,x->x.snapshot.has("members")&&x.snapshot.getAsJsonArray("members").size()==2);
            s.onActivity(a->a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));Thread.sleep(1400);await(f,x->x.available&&bool(member(x.snapshot,"22"),"connected"));capture("room-portrait-fixture.png");
            s.recreate();await(f,x->x.hosting&&x.snapshot.getAsJsonArray("members").size()==2);
            s.onActivity(a->{View ready=a.getWindow().getDecorView().findViewWithTag("ready");assertNotNull(ready);assertFalse(ready.isEnabled());a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);});Thread.sleep(1400);await(f,x->x.available&&bool(member(x.snapshot,"22"),"connected"));capture("room-landscape-fixture.png");
            click(s,"leave");clickText("Confirm");await(f,x->x.room.isEmpty());assertTrue(f.posts>0);
        }
    }
    @Test public void hostApprovalAndPublicDirectoryAcknowledgement()throws Exception{
        try(Fixture f=new Fixture();ActivityScenario<LobbyRoomActivity> s=ActivityScenario.launch(LobbyRoomActivity.class)){
            await(f,x->x.verified&&!x.friends.isEmpty());click(s,"create");clickText("Host approval");await(f,x->x.hosting);
            f.model.action("11",r->f.guest.join("11",r.room(),""));f.model.refresh();await(f,x->x.requests.containsKey("22"));capture("room-approval-fixture.png");
            click(s,"approve:22");await(f,x->x.snapshot.getAsJsonArray("members").size()==2);f.model.refresh();await(f,x->x.publication.contains("accepted"));
            s.onActivity(a->a.getDelegate().setLocalNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO));Thread.sleep(1400);capture("room-light-fixture.png");
            f.token=null;f.model.refresh();await(f,x->!x.verified);assertTrue(f.model.state.getValue().room.isEmpty());
        }
    }
    @Test public void openingSharedLinkDoesNotJoinWithoutConfirmation()throws Exception{
        try(Fixture f=new Fixture()){
            Intent intent=new Intent(f.app,LobbyRoomActivity.class).setData(Uri.parse(LobbyLink.create("22",uuid())));
            try(ActivityScenario<LobbyRoomActivity> s=ActivityScenario.launch(intent)){await(f,x->x.verified);assertEquals(0,f.posts);click(s,"join-link");Thread.sleep(200);assertEquals(0,f.posts);capture("room-link-confirmation-fixture.png");clickText("Cancel");assertTrue(f.model.state.getValue().room.isEmpty());}
        }
    }
    @Test public void signedOutRoomDoesNotReadOrSend()throws Exception{
        try(Fixture f=new Fixture()){f.token=null;try(ActivityScenario<LobbyRoomActivity> s=ActivityScenario.launch(LobbyRoomActivity.class)){Thread.sleep(500);assertFalse(f.model.state.getValue().verified);assertEquals(0,f.posts);s.onActivity(a->assertTrue(a.getWindow().getDecorView().findViewWithTag("account").isShown()));}}
    }
    @Test public void activeRoomShortcutAndDuplicateCreatePreserveAuthority()throws Exception{
        try(Fixture f=new Fixture();ActivityScenario<LobbyRoomActivity> s=ActivityScenario.launch(LobbyRoomActivity.class)){
            await(f,x->x.verified);assertFalse(RoomStatusProvider.active(f.app));
            click(s,"create");clickText("Invite only");await(f,x->x.hosting);
            String room=f.model.state.getValue().room;assertTrue(RoomStatusProvider.active(f.app));
            f.model.createForGame("11","public",null);Thread.sleep(300);
            assertEquals(room,f.model.state.getValue().room);assertEquals("private",f.model.state.getValue().access);
            s.onActivity(a->{
                ru.playsoftware.j2meloader.input.GameMenuDialog menu=new ru.playsoftware.j2meloader.input.GameMenuDialog(a,"Social");
                menu.useEdgePanel();menu.show();
                ru.playsoftware.j2meloader.input.SocialMenuPages.show(menu,a,null,intent->{
                    assertEquals(LobbyRoomActivity.class.getName(),intent.getComponent().getClassName());
                    assertEquals("Preserve the original room beneath the game",0,intent.getFlags());
                });
                View action=findText(menu.getWindow().getDecorView(),"Return to active room");
                assertNotNull(action);action.performClick();menu.dismiss();
                View empty=a.getWindow().getDecorView().findViewWithTag("empty-seat");assertNotNull(empty);assertTrue(empty.isEnabled());
            });
            f.model.action("11",LobbyRoom::leave);await(f,x->x.room.isEmpty());assertFalse(RoomStatusProvider.active(f.app));
        }
    }
    @Test public void admittedContactKeepAndManualLeaseOnNativeOwner()throws Exception{
        try(Fixture f=new Fixture(true);ActivityScenario<LobbyRoomActivity> s=ActivityScenario.launch(LobbyRoomActivity.class)){
            await(f,x->x.verified);f.model.action("11",r->{r.create("private");r.invite("22");});
            await(f,x->bool(object(object(x.contacts,"peers"),"22"),"canKeep"),160000);
            assertEquals(1,f.contactPosts);
            s.onActivity(a->{View keep=a.getWindow().getDecorView().findViewWithTag("keep:22");assertNotNull(keep);keep.requestRectangleOnScreen(new android.graphics.Rect(0,0,keep.getWidth(),keep.getHeight()),true);});
            capture("room-temporary-contact-fixture.png");
            click(s,"keep:22");await(f,x->!object(x.contacts,"peers").has("22"));
            java.util.concurrent.CountDownLatch granted=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.atomic.AtomicReference<Runnable> lease=new java.util.concurrent.atomic.AtomicReference<>();
            f.model.manual("11","22",(release,error)->{lease.set(release);granted.countDown();});assertTrue(granted.await(5,java.util.concurrent.TimeUnit.SECONDS));assertNotNull(lease.get());
            java.util.concurrent.CountDownLatch denied=new java.util.concurrent.CountDownLatch(1);
            f.model.manual("11","",(release,error)->{assertNull(release);denied.countDown();});assertTrue(denied.await(5,java.util.concurrent.TimeUnit.SECONDS));lease.get().run();
            f.model.action("11",LobbyRoom::leave);await(f,x->x.room.isEmpty());Thread.sleep(18000);assertEquals(0,f.contactDeletes);
        }
    }
    @Test public void admittedContactCleanupAfterLeave()throws Exception{
        try(Fixture f=new Fixture(true);ActivityScenario<LobbyRoomActivity> s=ActivityScenario.launch(LobbyRoomActivity.class)){
            await(f,x->x.verified);f.model.action("11",r->{r.create("private");r.invite("22");});
            await(f,x->text(object(object(x.contacts,"peers"),"22"),"phase").equals("friend"),160000);
            f.model.action("11",LobbyRoom::leave);await(f,x->x.room.isEmpty());
            await(f,x->Arrays.asList("release","released","cleanup","deleting").contains(text(object(object(x.contacts,"peers"),"22"),"phase")));
            capture("room-contact-cleanup-fixture.png");
            await(f,x->f.contactDeletes==1,100000);assertEquals(0,f.relationshipType);
        }
    }
    private static View findText(View view,String text){
        if(view instanceof android.widget.TextView&&text.contentEquals(((android.widget.TextView)view).getText()))return view;
        if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++){View found=findText(group.getChildAt(i),text);if(found!=null)return found;}}
        return null;
    }
    private static void click(ActivityScenario<LobbyRoomActivity> s,String tag){s.onActivity(a->{View v=a.getWindow().getDecorView().findViewWithTag(tag);assertNotNull(tag,v);assertTrue(tag,v.isEnabled());v.performClick();});}
    private static void clickText(String text)throws Exception{for(int i=0;i<40;i++){AccessibilityNodeInfo root=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();if(root!=null)for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(text)){if(text.contentEquals(node.getText()==null?"":node.getText())){AccessibilityNodeInfo target=node;while(target!=null&&!target.isClickable())target=target.getParent();if(target!=null&&target.performAction(AccessibilityNodeInfo.ACTION_CLICK)){Thread.sleep(150);return;}}}Thread.sleep(100);}fail("Missing action: "+text);}
    private static void await(Fixture f,Predicate<LobbyRoomModel.State> predicate)throws Exception{await(f,predicate,30000);}
    private static void await(Fixture f,Predicate<LobbyRoomModel.State> predicate,long timeout)throws Exception{long end=android.os.SystemClock.elapsedRealtime()+timeout;while(android.os.SystemClock.elapsedRealtime()<end){LobbyRoomModel.State s=f.model.state.getValue();if(s!=null&&predicate.test(s)){Thread.sleep(200);return;}Thread.sleep(75);}fail("Room state timeout: "+(f.model.state.getValue()==null?"null":f.model.state.getValue().status+" / "+f.model.state.getValue().contacts));}
    private static void capture(String name)throws Exception{Thread.sleep(500);InstrumentationRegistry.getInstrumentation().waitForIdleSync();Bitmap image=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(image);File dir=new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null),"lobby-room-checks");assertTrue(dir.isDirectory()||dir.mkdirs());try(FileOutputStream out=new FileOutputStream(new File(dir,name))){assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out));}image.recycle();}
}
