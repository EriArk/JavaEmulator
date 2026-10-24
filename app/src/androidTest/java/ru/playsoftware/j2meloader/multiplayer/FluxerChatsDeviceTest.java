/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import android.app.Activity;
import android.app.Application;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.EditText;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.gson.*;
import org.junit.Test;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import ru.playsoftware.j2meloader.R;
import static org.junit.Assert.*;

/** Real Android UI with an injected in-memory provider; never sends a real message. */
public class FluxerChatsDeviceTest {
    private static final String TOKEN="fixture-chat-session-one",OTHER="fixture-chat-session-two";
    private static final class Fixture implements AutoCloseable,FluxerAuth.Store,FluxerAuth.Transport,Application.ActivityLifecycleCallbacks {
        final Application app=(Application)InstrumentationRegistry.getInstrumentation().getTargetContext().getApplicationContext();
        volatile String token=TOKEN,lastContent="",lastNonce="";volatile boolean failSend;volatile int posts;
        final boolean friendsData;volatile int mutations;volatile boolean lostMutation;
        JsonArray relations;
        final JsonArray sent=new JsonArray();long messageId=1000;
        Fixture(){this(false);}
        Fixture(boolean friendsData){this.friendsData=friendsData;
            relations=new JsonArray();if(friendsData){relations.add(person("22",1,"Alex"));relations.add(person("44",3,"Sam"));relations.add(person("55",4,"Jamie"));relations.add(person("66",2,"Morgan"));}
            app.registerActivityLifecycleCallbacks(this);
        }
        private JsonObject person(String id,int type,String name){JsonObject r=new JsonObject();r.addProperty("id",id);r.addProperty("type",type);r.addProperty("since","2026-10-24T01:00:00Z");JsonObject user=new JsonObject();user.addProperty("id",id);user.addProperty("username",name.toLowerCase(java.util.Locale.ROOT));user.addProperty("global_name",name+" - UI fixture");user.addProperty("discriminator","0042");r.add("user",user);return r;}
        public String read(){return token;}public void save(String token){throw new AssertionError("No credential writes in chat fixture");}public void clear(){throw new AssertionError("No credential deletion in chat fixture");}
        public void request(String method,String path,JsonObject body,String session,java.util.function.Consumer<FluxerAuth.Reply> done){
            JsonElement reply;
            if(path.equals("/v1/users/@me"))reply=JsonParser.parseString("{\"id\":\""+(OTHER.equals(session)?"33":"11")+"\",\"username\":\"UI fixture\"}");
            else if(friendsData&&!method.equals("GET")&&path.startsWith("/v1/users/@me/relationships")){
                mutations++;String id=path.substring(path.lastIndexOf('/')+1);
                if(method.equals("POST")){relations.add(person("77",4,body.get("username").getAsString()));reply=relations.get(relations.size()-1);}
                else{JsonObject found=null;for(int i=relations.size()-1;i>=0;i--)if(relations.get(i).getAsJsonObject().get("id").getAsString().equals(id)){found=relations.remove(i).getAsJsonObject();break;}
                    if(found==null){done.accept(new FluxerAuth.Reply(404,null,0));return;}
                    if(method.equals("PUT")){found.addProperty("type",body.get("type").getAsInt());relations.add(found);}reply=found;
                }
                if(lostMutation){done.accept(new FluxerAuth.Reply(0,null,0));return;}
            }
            else if(path.endsWith("/relationships"))reply=OTHER.equals(session)?new JsonArray():relations.deepCopy();
            else if(path.equals("/v1/users/@me/channels"))reply=JsonParser.parseString(OTHER.equals(session)?"[]":"[{\"id\":\"101\",\"type\":1,\"last_message_id\":\"1000\",\"recipients\":[{\"id\":\"22\",\"global_name\":\"Alex - UI fixture\"}]},{\"id\":\"102\",\"type\":3,\"name\":\"Co-op night - UI fixture\",\"last_message_id\":\"900\"},{\"id\":\"103\",\"type\":1,\"recipients\":[{\"id\":\"44\",\"username\":\"Sam - UI fixture\"}]}]");
            else if(method.equals("POST")){
                posts++;lastContent=body.get("content").getAsString();lastNonce=body.get("nonce").getAsString();
                if(failSend){done.accept(new FluxerAuth.Reply(0,null,0));return;}
                JsonObject m=message(String.valueOf(++messageId),"11",lastContent);m.addProperty("nonce",lastNonce);sent.add(m);reply=m;
            }else if(path.contains("before="))reply=new JsonArray();
            else {
                JsonArray rows=new JsonArray();rows.add(message("904","22","This is an offline UI fixture. No real message was sent."));
                rows.add(message("903","11","Let's try a short co-op session."));rows.add(message("902","22","Ready when you are. Which game?"));
                rows.add(message("901","11","We can pick it together once lobbies are available."));for(JsonElement m:sent)rows.add(m);reply=rows;
            }
            done.accept(new FluxerAuth.Reply(200,reply,0));
        }
        private JsonObject message(String id,String author,String text){JsonObject m=new JsonObject();m.addProperty("id",id);m.addProperty("channel_id","101");m.addProperty("content",text);m.addProperty("timestamp","2026-10-24T01:00:00Z");JsonObject a=new JsonObject();a.addProperty("id",author);a.addProperty("username",author.equals("11")?"You":"Alex - UI fixture");m.add("author",a);return m;}
        public void onActivityPreCreated(Activity activity,Bundle saved){if(activity instanceof FluxerChatsActivity){
            new ViewModelProvider((FluxerChatsActivity)activity,new ViewModelProvider.Factory(){
                @Override public <T extends ViewModel>T create(Class<T> type){return type.cast(new FluxerChatsModel(app,Fixture.this,Fixture.this));}
            }).get(FluxerChatsModel.class);
        }}
        public void onActivityCreated(Activity a,Bundle b){}public void onActivityStarted(Activity a){}public void onActivityResumed(Activity a){}public void onActivityPaused(Activity a){}public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){}
        public void close(){app.unregisterActivityLifecycleCallbacks(this);}
    }
    @Test public void signedOutChatsOfferAccountWithoutNetwork() throws Exception {
        try(Fixture f=new Fixture()){f.token=null;try(ActivityScenario<FluxerChatsActivity> screen=ActivityScenario.launch(FluxerChatsActivity.class)){
            await(screen,s->!s.verified&&!s.loading);
            screen.onActivity(a->{assertTrue(a.findViewById(R.id.fluxer_chat_sign_in).isShown());assertFalse(a.findViewById(R.id.fluxer_chat_channels).isShown());});assertEquals(0,f.posts);
        }}
    }
    @Test public void conversationsFitPortraitAndLandscapeWithKeyboard() throws Exception {
        try(Fixture f=new Fixture();ActivityScenario<FluxerChatsActivity> screen=ActivityScenario.launch(FluxerChatsActivity.class)){
            await(screen,s->s.channels.size()==3);
            screen.onActivity(a->a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));Thread.sleep(700);
            capture("chats-list-portrait-fixture.png");select(screen);await(screen,s->s.messages.size()==4&&!s.historyBusy);Thread.sleep(300);
            assertVisibleComposer(screen);capture("chats-conversation-portrait-fixture.png");
            screen.onActivity(a->a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));Thread.sleep(700);
            assertVisibleComposer(screen);capture("chats-landscape-fixture.png");
            screen.onActivity(a->{EditText input=a.findViewById(R.id.fluxer_chat_input);input.requestFocus();((android.view.inputmethod.InputMethodManager)a.getSystemService(Activity.INPUT_METHOD_SERVICE)).showSoftInput(input,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);});
            Thread.sleep(900);assertVisibleComposer(screen);
            screen.onActivity(a->{
                androidx.core.view.WindowInsetsCompat insets=androidx.core.view.ViewCompat.getRootWindowInsets(a.findViewById(R.id.fluxer_chat_input));
                assertNotNull(insets);assertTrue(insets.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()));
                androidx.recyclerview.widget.RecyclerView rows=a.findViewById(R.id.fluxer_chat_messages);
                assertEquals(rows.getAdapter().getItemCount()-1,((androidx.recyclerview.widget.LinearLayoutManager)rows.getLayoutManager()).findLastVisibleItemPosition());
            });
            capture("chats-keyboard-fixture.png");
            screen.onActivity(a->{((android.view.inputmethod.InputMethodManager)a.getSystemService(Activity.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(a.findViewById(R.id.fluxer_chat_input).getWindowToken(),0);a.getDelegate().setLocalNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);});
            Thread.sleep(700);assertVisibleComposer(screen);capture("chats-light-fixture.png");assertEquals(0,f.posts);
        }
    }
    @Test public void unicodeDraftSurvivesRotationAndOneSendClearsIt() throws Exception {
        try(Fixture f=new Fixture();ActivityScenario<FluxerChatsActivity> screen=ActivityScenario.launch(FluxerChatsActivity.class)){
            await(screen,s->s.channels.size()==3);select(screen);await(screen,s->s.canSend);
            String draft="Hello \u041f\u0440\u0438\u0432\u0435\u0442 \u05e9\u05dc\u05d5\u05dd \ud83c\udfae";
            screen.onActivity(a->((EditText)a.findViewById(R.id.fluxer_chat_input)).setText(draft));await(screen,s->s.draft.equals(draft));screen.recreate();await(screen,s->s.draft.equals(draft));
            screen.onActivity(a->{assertEquals(draft,((EditText)a.findViewById(R.id.fluxer_chat_input)).getText().toString());View send=a.findViewById(R.id.fluxer_chat_send);send.requestFocusFromTouch();a.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP,android.view.KeyEvent.KEYCODE_BUTTON_A));});
            await(screen,s->s.draft.isEmpty()&&s.messages.size()==5);assertEquals(1,f.posts);assertEquals(draft,f.lastContent);
        }
    }
    @Test public void uncertainSendHasExplicitRetryAndNoDuplicateNonce() throws Exception {
        try(Fixture f=new Fixture();ActivityScenario<FluxerChatsActivity> screen=ActivityScenario.launch(FluxerChatsActivity.class)){
            await(screen,s->s.channels.size()==3);select(screen);await(screen,s->s.canSend);f.failSend=true;
            screen.onActivity(a->((EditText)a.findViewById(R.id.fluxer_chat_input)).setText("Retry fixture"));await(screen,s->s.draft.equals("Retry fixture"));
            screen.onActivity(a->a.findViewById(R.id.fluxer_chat_send).performClick());await(screen,s->s.messages.stream().anyMatch(m->m.delivery.equals("Delivery unknown")));
            String nonce=f.lastNonce;capture("chats-delivery-unknown-fixture.png");f.failSend=false;
            screen.onActivity(a->{
                androidx.recyclerview.widget.RecyclerView rows=a.findViewById(R.id.fluxer_chat_messages);
                androidx.recyclerview.widget.RecyclerView.ViewHolder pending=rows.findViewHolderForAdapterPosition(rows.getAdapter().getItemCount()-1);
                assertNotNull(pending);pending.itemView.performClick();
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            AccessibilityNodeInfo root=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();
            assertNotNull(root);java.util.List<AccessibilityNodeInfo> actions=root.findAccessibilityNodeInfosByText("Retry sending");
            assertEquals(1,actions.size());AccessibilityNodeInfo action=actions.get(0);
            while(!action.isClickable()&&action.getParent()!=null)action=action.getParent();
            assertTrue(action.performAction(AccessibilityNodeInfo.ACTION_CLICK));
            await(screen,s->s.messages.size()==5&&s.messages.stream().noneMatch(m->m.delivery.equals("Delivery unknown")));
            assertEquals(2,f.posts);assertEquals(nonce,f.lastNonce);
        }
    }
    @Test public void accountChangeDropsPreviousConversationAndDraft() throws Exception {
        try(Fixture f=new Fixture();ActivityScenario<FluxerChatsActivity> screen=ActivityScenario.launch(FluxerChatsActivity.class)){
            await(screen,s->s.channels.size()==3);select(screen);await(screen,s->s.canSend);
            screen.onActivity(a->((EditText)a.findViewById(R.id.fluxer_chat_input)).setText("Private old draft"));await(screen,s->!s.draft.isEmpty());
            screen.moveToState(Lifecycle.State.CREATED);f.token=OTHER;screen.moveToState(Lifecycle.State.RESUMED);
            await(screen,s->s.verified&&s.account.equals("33"));screen.onActivity(a->assertEquals("",((EditText)a.findViewById(R.id.fluxer_chat_input)).getText().toString()));
            assertEquals(0,f.posts);
        }
    }
    @Test public void authenticatedTransportRoutesStayNarrow(){
        assertTrue(FluxerHttpTransport.allowed("GET","/v1/users/@me/channels",TOKEN));
        assertTrue(FluxerHttpTransport.allowed("GET","/v1/channels/101/messages?limit=50&before=800",TOKEN));
        assertTrue(FluxerHttpTransport.allowed("POST","/v1/channels/101/messages",TOKEN));
        assertFalse(FluxerHttpTransport.allowed("POST","/v1/channels/101/messages",null));
        assertFalse(FluxerHttpTransport.allowed("DELETE","/v1/channels/101",TOKEN));
        assertFalse(FluxerHttpTransport.allowed("GET","/v1/channels/../messages?limit=50",TOKEN));
        assertTrue(FluxerHttpTransport.allowed("PUT","/v1/users/@me/relationships/22",TOKEN));
        assertTrue(FluxerHttpTransport.allowed("POST","/v1/users/@me/relationships/22",TOKEN));
        assertFalse(FluxerHttpTransport.allowed("POST","/v1/users/@me/relationships/22?redirect=elsewhere",TOKEN));
        assertTrue(FluxerHttpTransport.allowed("DELETE","/v1/users/@me/relationships/22",TOKEN));
        assertFalse(FluxerHttpTransport.allowed("DELETE","/v1/users/@me/relationships/22",null));
        assertFalse(FluxerHttpTransport.allowed("POST","/v1/users/@me/relationships/bulk-ignore",TOKEN));
        assertFalse(FluxerHttpTransport.allowed("PATCH","/v1/users/@me/relationships/22",TOKEN));
    }
    @Test public void friendsListsFitBothOrientationsAndKeepFilterOnRotation() throws Exception {
        try(Fixture f=new Fixture(true);ActivityScenario<FluxerChatsActivity> screen=ActivityScenario.launch(FluxerChatsActivity.class)){
            await(screen,s->s.friendsReady&&s.friends.size()==4);friends(screen);
            screen.onActivity(a->a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));Thread.sleep(600);capture("friends-portrait-fixture.png");
            screen.onActivity(a->((android.widget.Spinner)a.findViewById(R.id.fluxer_friends_filter)).setSelection(1));Thread.sleep(250);
            screen.recreate();await(screen,s->s.friendsReady);screen.onActivity(a->assertEquals(1,((android.widget.Spinner)a.findViewById(R.id.fluxer_friends_filter)).getSelectedItemPosition()));
            screen.onActivity(a->a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));Thread.sleep(650);capture("friends-requests-landscape-fixture.png");
            screen.onActivity(a->a.getDelegate().setLocalNightMode(androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO));Thread.sleep(650);capture("friends-requests-light-fixture.png");
            assertEquals(0,f.mutations);assertEquals(0,f.posts);
        }
    }
    @Test public void acceptsRequestThroughMenuAndConfirmsBeforeBlocking() throws Exception {
        try(Fixture f=new Fixture(true);ActivityScenario<FluxerChatsActivity> screen=ActivityScenario.launch(FluxerChatsActivity.class)){
            await(screen,s->s.friendsReady);friends(screen);screen.onActivity(a->((android.widget.Spinner)a.findViewById(R.id.fluxer_friends_filter)).setSelection(1));Thread.sleep(200);
            // Requests are alphabetic: Jamie (outgoing), then Sam (incoming).
            personRow(screen,1);clickText("Accept request");await(screen,s->s.friendsReady&&s.friends.stream().anyMatch(p->p.id.equals("44")&&p.type==1));assertEquals(1,f.mutations);
            screen.onActivity(a->((android.widget.Spinner)a.findViewById(R.id.fluxer_friends_filter)).setSelection(0));Thread.sleep(200);
            personRow(screen,0);clickText("Block");assertEquals(1,f.mutations);capture("friends-block-confirmation-fixture.png");clickText("Cancel");assertEquals(1,f.mutations);
            personRow(screen,0);clickText("Block");clickText("Block");await(screen,s->s.friendsReady&&s.friends.stream().anyMatch(p->p.id.equals("22")&&p.type==2));assertEquals(2,f.mutations);
            screen.onActivity(a->assertTrue(new ViewModelProvider(a).get(FluxerChatsModel.class).state.getValue().channels.stream().noneMatch(c->c.id.equals("101"))));
        }
    }
    @Test public void addFriendValidatesTagAndShowsOutgoingRequest() throws Exception {
        try(Fixture f=new Fixture(true);ActivityScenario<FluxerChatsActivity> screen=ActivityScenario.launch(FluxerChatsActivity.class)){
            await(screen,s->s.friendsReady);friends(screen);screen.onActivity(a->a.findViewById(R.id.fluxer_friends_add).performClick());
            clickText("Send request");assertEquals(0,f.mutations);
            android.app.UiAutomation automation=InstrumentationRegistry.getInstrumentation().getUiAutomation();
            android.accessibilityservice.AccessibilityServiceInfo info=automation.getServiceInfo();info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;automation.setServiceInfo(info);
            AccessibilityNodeInfo root=InstrumentationRegistry.getInstrumentation().getUiAutomation().getRootInActiveWindow();
            java.util.List<AccessibilityNodeInfo> tags=root.findAccessibilityNodeInfosByViewId("io.github.eriark.abyssme.debug:id/fluxer_friend_tag");
            assertEquals(1,tags.size());Bundle text=new Bundle();text.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"newplayer#0042");assertTrue(tags.get(0).performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,text));
            capture("friends-add-fixture.png");clickText("Send request");await(screen,s->s.friendsReady&&s.friends.size()==5);assertEquals(1,f.mutations);
        }
    }
    @Test public void openingFriendDmAndBackPreservesChatDraft() throws Exception {
        try(Fixture f=new Fixture(true);ActivityScenario<FluxerChatsActivity> screen=ActivityScenario.launch(FluxerChatsActivity.class)){
            await(screen,s->s.friendsReady);select(screen);await(screen,s->s.canSend);screen.onActivity(a->((EditText)a.findViewById(R.id.fluxer_chat_input)).setText("Keep this draft"));await(screen,s->s.draft.equals("Keep this draft"));
            friends(screen);personRow(screen,0);clickText("Message");await(screen,s->s.openedConversation==1&&s.canSend);
            screen.onActivity(a->{assertTrue(a.findViewById(R.id.fluxer_chat_input).isShown());assertEquals("Keep this draft",((EditText)a.findViewById(R.id.fluxer_chat_input)).getText().toString());a.onBackPressed();});
            screen.onActivity(a->assertTrue(a.findViewById(R.id.fluxer_friends_panel).isShown()));assertEquals(0,f.posts);assertEquals(0,f.mutations);
        }
    }
    @Test public void uncertainFriendMutationReconcilesWithoutReplay() throws Exception {
        try(Fixture f=new Fixture(true);ActivityScenario<FluxerChatsActivity> screen=ActivityScenario.launch(FluxerChatsActivity.class)){
            await(screen,s->s.friendsReady);friends(screen);f.lostMutation=true;personRow(screen,0);clickText("Remove friend");clickText("Remove friend");
            await(screen,s->s.friendsReady&&s.friends.size()==3&&s.friendsNotice.contains("Result unknown"));capture("friends-unknown-result-fixture.png");assertEquals(1,f.mutations);
            screen.onActivity(a->new ViewModelProvider(a).get(FluxerChatsModel.class).refresh());await(screen,s->s.friendsReady);assertEquals(1,f.mutations);
        }
    }
    private void friends(ActivityScenario<FluxerChatsActivity> screen)throws Exception{screen.onActivity(a->a.findViewById(R.id.fluxer_tab_friends).performClick());InstrumentationRegistry.getInstrumentation().waitForIdleSync();Thread.sleep(150);}
    private void personRow(ActivityScenario<FluxerChatsActivity> screen,int index)throws Exception{InstrumentationRegistry.getInstrumentation().waitForIdleSync();Thread.sleep(150);screen.onActivity(a->{androidx.recyclerview.widget.RecyclerView rows=a.findViewById(R.id.fluxer_friends_list);androidx.recyclerview.widget.RecyclerView.ViewHolder row=rows.findViewHolderForAdapterPosition(index);assertNotNull(row);row.itemView.performClick();});}
    private void clickText(String text)throws Exception{
        android.app.UiAutomation automation=InstrumentationRegistry.getInstrumentation().getUiAutomation();
        android.accessibilityservice.AccessibilityServiceInfo info=automation.getServiceInfo();
        info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;automation.setServiceInfo(info);
        long deadline=System.currentTimeMillis()+4000;
        do{
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();Thread.sleep(100);
            java.util.List<AccessibilityNodeInfo> roots=new java.util.ArrayList<>();
            AccessibilityNodeInfo active=automation.getRootInActiveWindow();if(active!=null)roots.add(active);
            for(android.view.accessibility.AccessibilityWindowInfo window:automation.getWindows())if(window.getRoot()!=null)roots.add(window.getRoot());
            for(AccessibilityNodeInfo root:roots)for(AccessibilityNodeInfo node:root.findAccessibilityNodeInfosByText(text))if(node.getText()!=null&&text.equalsIgnoreCase(node.getText().toString())&&node.isVisibleToUser()){
                android.graphics.Rect bounds=new android.graphics.Rect();node.getBoundsInScreen(bounds);
                if(node.isEnabled()&&!bounds.isEmpty()){
                    long now=android.os.SystemClock.uptimeMillis();
                    android.view.MotionEvent down=android.view.MotionEvent.obtain(now,now,android.view.MotionEvent.ACTION_DOWN,bounds.centerX(),bounds.centerY(),0);
                    android.view.MotionEvent up=android.view.MotionEvent.obtain(now,now+50,android.view.MotionEvent.ACTION_UP,bounds.centerX(),bounds.centerY(),0);
                    try{assertTrue(automation.injectInputEvent(down,true));assertTrue(automation.injectInputEvent(up,true));}finally{down.recycle();up.recycle();}return;
                }
            }
        }while(System.currentTimeMillis()<deadline);
        capture("missing-action-"+text.replace(' ','-')+".png");
        fail("Missing action: "+text);
    }
    private void select(ActivityScenario<FluxerChatsActivity> screen){screen.onActivity(a->new ViewModelProvider(a).get(FluxerChatsModel.class).select("101"));}
    private void await(ActivityScenario<FluxerChatsActivity> screen,Predicate<FluxerChats.State> condition)throws Exception{
        AtomicReference<FluxerChats.State> state=new AtomicReference<>();long until=System.currentTimeMillis()+8000;
        do{screen.onActivity(a->state.set(new ViewModelProvider(a).get(FluxerChatsModel.class).state.getValue()));if(state.get()!=null&&condition.test(state.get()))return;Thread.sleep(50);}while(System.currentTimeMillis()<until);
        fail("Chat fixture did not reach the expected state");
    }
    private void assertVisibleComposer(ActivityScenario<FluxerChatsActivity> screen){screen.onActivity(a->{
        View send=a.findViewById(R.id.fluxer_chat_send);assertTrue(send.isShown());assertTrue(send.hasWindowFocus());android.graphics.Rect rect=new android.graphics.Rect();assertTrue(send.getGlobalVisibleRect(rect));assertTrue(rect.height()>=send.getHeight()-1);
        androidx.recyclerview.widget.RecyclerView messages=a.findViewById(R.id.fluxer_chat_messages);assertTrue("History squeezed out",messages.getHeight()>a.getResources().getDisplayMetrics().density*60);
    });}
    private void capture(String name)throws Exception{
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();Thread.sleep(250);
        Bitmap bitmap=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(bitmap);
        File root=new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null),"fluxer-chats-checks");assertTrue(root.isDirectory()||root.mkdirs());
        try(FileOutputStream out=new FileOutputStream(new File(root,name))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}finally{bitmap.recycle();}
    }
}
