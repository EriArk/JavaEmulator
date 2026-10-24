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
        final JsonArray sent=new JsonArray();long messageId=1000;
        Fixture(){app.registerActivityLifecycleCallbacks(this);}
        public String read(){return token;}public void save(String token){throw new AssertionError("No credential writes in chat fixture");}public void clear(){throw new AssertionError("No credential deletion in chat fixture");}
        public void request(String method,String path,JsonObject body,String session,java.util.function.Consumer<FluxerAuth.Reply> done){
            JsonElement reply;
            if(path.equals("/v1/users/@me"))reply=JsonParser.parseString("{\"id\":\""+(OTHER.equals(session)?"33":"11")+"\",\"username\":\"UI fixture\"}");
            else if(path.endsWith("/relationships"))reply=new JsonArray();
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
