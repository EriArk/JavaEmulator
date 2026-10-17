package io.github.eriark.abyssme.symbian;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.util.*;
import org.junit.*;
import ru.playsoftware.j2meloader.BuildConfig;
import ru.playsoftware.j2meloader.MainActivity;
import ru.playsoftware.j2meloader.config.ProfilesManager;
import static org.junit.Assert.*;

public final class SymbianSessionUiTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context context = instrumentation.getTargetContext();
    private UiAutomation ui;
    @Test public void javaGameUsesCommonMenuWhenFixtureProvided() throws Exception {
        commonEngineMenu("javaMenuUri", "java-common-menu.png");
    }
    @Test public void mophunGameUsesCommonMenuWhenFixtureProvided() throws Exception {
        commonEngineMenu("mophunMenuUri", "mophun-common-menu.png");
    }
    private void commonEngineMenu(String argument, String screenshot) throws Exception {
        String uri = InstrumentationRegistry.getArguments().getString(argument);
        Assume.assumeNotNull(uri);
        ui = instrumentation.getUiAutomation();
        context.startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri), context, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        boolean controllerMenu = BuildConfig.HANDHELD_MODE;
        if (controllerMenu) { SystemClock.sleep(5000); key(KeyEvent.KEYCODE_BACK, 80); }
        else { waitNode("Game menu", 20000); SystemClock.sleep(2500); click("Game menu"); }
        waitNode("Resume", 5000);
        for (String label : new String[]{"Display", "Controls", "Screenshot", "More", "Exit game"})
            assertFalse(label, ui.getRootInActiveWindow().findAccessibilityNodeInfosByText(label).isEmpty());
        capture(screenshot);
        click("More"); click("Back"); waitNode("Resume", 5000);
        click("Exit game"); waitNode("Leave without saving", 5000); click("Resume");
        if (controllerMenu) key(KeyEvent.KEYCODE_BACK, 80); else click("Game menu");
        click("Exit game"); click("Leave without saving");
        SystemClock.sleep(1000);
    }
    @Test public void settingsArePerPhoneAndGameAndSelectDoesNotLeakIntoMapping() throws Exception {
        ui = instrumentation.getUiAutomation();
        MainActivity activity=(MainActivity)instrumentation.startActivitySync(new Intent(context,MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        String seed=UUID.randomUUID().toString().replace("-",""); String system=seed+seed;
        String other=new StringBuilder(system).reverse().toString();
        List<String> events=new ArrayList<>();
        java.util.concurrent.atomic.AtomicInteger pauses=new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger pausesAtRestart=new java.util.concurrent.atomic.AtomicInteger();
        SymbianSessionUi[] current=new SymbianSessionUi[1];
        SymbianSessionUi.Host host=new SymbianSessionUi.Host() {
            public android.graphics.RectF touchBounds(){return null;}
            public void touch(int x,int y,int pressure,int action,int pointer){}
            public void key(int scan,boolean down){events.add(scan+":"+down);}
            public void pause(boolean paused){pauses.incrementAndGet();}
            public void display(int scale){}
            public void leave(){}
            public void restart(){events.add("restart");current[0].close();pausesAtRestart.set(pauses.get());}
            public Bitmap capture(){return null;}
        };
        try {
            instrumentation.runOnMainSync(() -> {
                try {
                    current[0]=new SymbianSessionUi(activity,new TextureView(activity),system,1,"UI fixture",host);
                    activity.setContentView(current[0].root);
                    current[0].profile.screenRotation=180;
                    assertTrue(ProfilesManager.saveConfig(current[0].profile));
                    current[0].key(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_SELECT));
                    current[0].key(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_SELECT));
                } catch(IOException error){throw new AssertionError(error);}
            });
            SystemClock.sleep(150);
            instrumentation.runOnMainSync(() -> {
                assertEquals(Arrays.asList("164:true","164:false"),events); events.clear();
                current[0].key(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BUTTON_SELECT));
            });
            SystemClock.sleep(600); waitNode("Use mapping",5000); click("Cancel");
            instrumentation.runOnMainSync(() -> {
                current[0].key(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BUTTON_SELECT));
                assertTrue("Held Select is menu-only",events.isEmpty());
                assertFalse("Cancel must not start preview",ProfilesManager.isPreview(current[0].profile));
                current[0].close();
                try {
                    current[0]=new SymbianSessionUi(activity,new TextureView(activity),system,2,"Second game",host);
                    assertEquals(0,current[0].profile.screenRotation); current[0].close();
                    current[0]=new SymbianSessionUi(activity,new TextureView(activity),other,1,"Other phone",host);
                    assertEquals(0,current[0].profile.screenRotation); current[0].close();
                    current[0]=new SymbianSessionUi(activity,new TextureView(activity),system,1,"Saved game",host);
                    assertEquals(180,current[0].profile.screenRotation);
                } catch(IOException error){throw new AssertionError(error);}
            });
            instrumentation.runOnMainSync(() -> {
                activity.setContentView(current[0].root);
                current[0].showMenu();
            });
            click("Restart game"); click("Cancel");
            assertFalse(events.contains("restart"));
            instrumentation.runOnMainSync(() -> {
                ProfilesManager.beginPreview(current[0].profile);
                current[0].profile.screenRotation=90;
            });
            click("Restart game"); click("Restart");
            instrumentation.runOnMainSync(() -> {
                assertTrue(events.contains("restart"));
                assertEquals(180,current[0].profile.screenRotation);
                assertFalse(ProfilesManager.isPreview(current[0].profile));
            });
            instrumentation.waitForIdleSync(); SystemClock.sleep(200);
            assertEquals("Closed menu must not pause a replacement session",pausesAtRestart.get(),pauses.get());
        } finally {
            instrumentation.runOnMainSync(() -> {if(current[0]!=null)current[0].close();activity.finish();});
            for(String id:new String[]{system,other}) {
                java.nio.file.Path root=new File(context.getFilesDir(),"symbian-profiles/"+id).toPath();
                if(java.nio.file.Files.exists(root)) try(java.util.stream.Stream<java.nio.file.Path> paths=java.nio.file.Files.walk(root)) {
                    for(java.nio.file.Path file:(Iterable<java.nio.file.Path>)paths.sorted(Comparator.reverseOrder())::iterator) java.nio.file.Files.delete(file);
                }
            }
        }
    }
    @Test public void nativeGameUsesSharedDisplayMapperAndPhoneControls() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("sessionUi")));
        SymbianLibraryIndex.Entry game = new SymbianLibraryIndex(context).read().stream()
                .filter(e -> e.title.equals("Snakes")).findFirst().orElse(null);
        Assume.assumeNotNull(game);
        ui = instrumentation.getUiAutomation();
        context.startActivity(new Intent(context,SymbianActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(SymbianLibraryIndex.SYSTEM_EXTRA,game.system).putExtra(SymbianLibraryIndex.UID_EXTRA,game.uid));
        waitNode("Game menu",20000); SystemClock.sleep(8000);
        capture("symbian-common-game.png");
        key(KeyEvent.KEYCODE_BUTTON_SELECT,600);
        waitNode("Use mapping",5000); capture("symbian-common-mapper.png"); click("Cancel");
        click("Game menu"); waitNode("Resume",5000);
        capture("symbian-common-menu.png");
        click("Display"); waitNode("Picture size",5000);
        assertTrue(ui.getRootInActiveWindow().findAccessibilityNodeInfosByText("Smoothing").isEmpty());
        click("Image rotation"); click("180\u00b0");
        if (!BuildConfig.HANDHELD_MODE) { click("Orientation"); click("Portrait"); }
        capture("symbian-common-display.png"); click("Try changes");
        waitNode("Undo",5000); SystemClock.sleep(1200); capture("symbian-common-trial.png"); click("Undo");
        if (!BuildConfig.HANDHELD_MODE) {
            click("Game menu"); click("Display"); click("Orientation"); click("Portrait"); click("Try changes");
            waitNode("Undo",5000); SystemClock.sleep(1200); capture("symbian-common-phone-portrait.png");
        }
        click("Game menu"); click("Controls");
        capture("symbian-common-controls.png");
        if (!BuildConfig.HANDHELD_MODE) {
            click("Touch layout"); click("Gamepad"); click("Try changes");
            waitNode("Undo",5000); SystemClock.sleep(1000); capture("symbian-common-gamepad-portrait.png");
            click("Game menu"); click("Display"); click("Orientation"); click("Landscape"); click("Try changes");
            waitNode("Undo",5000); SystemClock.sleep(1000); capture("symbian-common-gamepad-landscape.png"); click("Undo");
        } else {
            click("Back"); waitNode("Resume",5000);
            click("Resume");
        }
        Set<Long> before=screenshots();
        click("Game menu"); waitNode("Resume",5000);
        AccessibilityNodeInfo captureAction=null;
        for(AccessibilityNodeInfo node:ui.getRootInActiveWindow().findAccessibilityNodeInfosByText("Screenshot"))
            if(node.getText()!=null && "Screenshot".contentEquals(node.getText()))captureAction=node;
        assertNotNull("Screenshot command in the pause dialog",captureAction); clickNode(captureAction);
        long deadline=SystemClock.uptimeMillis()+5000; Set<Long> added;
        do {added=screenshots();added.removeAll(before);if(added.isEmpty())SystemClock.sleep(100);} while(added.isEmpty() && SystemClock.uptimeMillis()<deadline);
        if(added.isEmpty())capture("symbian-failed-capture.png");
        assertEquals("One published game screenshot",1,added.size());
        android.net.Uri picture=android.content.ContentUris.withAppendedId(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,added.iterator().next());
        try(InputStream stream=context.getContentResolver().openInputStream(picture)) {
            Bitmap bitmap=android.graphics.BitmapFactory.decodeStream(stream); assertNotNull(bitmap);
            try {
                Set<Integer> colors=new HashSet<>();
                for(int y=0;y<bitmap.getHeight();y+=8)for(int x=0;x<bitmap.getWidth();x+=8)colors.add(bitmap.getPixel(x,y));
                assertTrue("Game screenshot must contain the rendered picture",colors.size()>8);
            } finally {bitmap.recycle();}
        }
        click("Game menu"); click("Exit game"); click("Leave without saving");
        SystemClock.sleep(1000);
    }
    @Test public void provisionPrivatePhoneCardFixtureWhenExplicitlyRequested() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("phoneCardFixture")));
        File inputs=new File(context.getExternalFilesDir(null),"symbian-input");
        SystemFileCatalog catalog=new SystemFileCatalog(context);
        try {
            File phoneArchive=new File(inputs,"ngage.zip");
            SystemFileCatalog.ImportResult imported=catalog.addArchive(context,phoneArchive,t -> {});
            assertEquals(imported.summary(),1,imported.entries.size());
            SystemFileCatalog.Entry phone=imported.entries.get(0);
            File runtime=catalog.runtime(phone);
            com.github.eka2l1.emu.Emulator.prepare(runtime.getAbsolutePath());
            try(GameImport card=GameImport.inspect(new File(inputs,"snakes.zip"))) {
                assertEquals(0,com.github.eka2l1.emu.Emulator.installNGageGame(card.file.getAbsolutePath()));
                com.github.eka2l1.emu.Emulator.prepare(runtime.getAbsolutePath());
                catalog.activate(phone); catalog.bind(card.hash,phone);
                new SymbianLibraryIndex(context).publish(phone,com.github.eka2l1.emu.Emulator.getLibraryApps(),true);
            }
        } finally {com.github.eka2l1.emu.Emulator.stopSession();}
    }
    @Test public void setupShowsConcreteFilesAndAllowsAddingPhoneBeforeInstaller() throws Exception {
        ui = instrumentation.getUiAutomation();
        context.startActivity(new Intent(context,SymbianActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        waitNode("Add system files",20000);
        long deadline=SystemClock.uptimeMillis()+20000;
        while (SystemClock.uptimeMillis()<deadline) {
            AccessibilityNodeInfo root=ui.getRootInActiveWindow();
            if(root!=null && (hasText(root,"phones available") || hasText(root,"1 phone available") || hasText(root,"No phone files added"))) break;
            SystemClock.sleep(100);
        }
        capture("symbian-common-system-files.png");
        clickPrefix("N-Gage 2.0\n");
        waitNode("Add N-Gage 2.0 installer",5000);
        AccessibilityNodeInfo root=ui.getRootInActiveWindow();
        assertTrue(hasText(root,"not a .n-gage game"));
        capture("symbian-common-required-files.png"); click("About phone dumps");
        assertTrue(hasText(ui.getRootInActiveWindow(),".rom"));
        assertTrue(hasText(ui.getRootInActiveWindow(),"same phone dump"));
        capture("symbian-common-file-formats.png"); click("Back"); click("Close"); click("Library");
    }
    private boolean hasText(AccessibilityNodeInfo node,String text) { return !node.findAccessibilityNodeInfosByText(text).isEmpty(); }
    private Set<Long> screenshots() {
        Set<Long> ids=new HashSet<>();
        try(android.database.Cursor cursor=context.getContentResolver().query(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[]{"_id"},"owner_package_name=? AND relative_path=? AND is_pending=0",
                new String[]{context.getPackageName(),"Pictures/AbyssME/"},null)) {
            assertNotNull(cursor); while(cursor.moveToNext())ids.add(cursor.getLong(0));
        }
        return ids;
    }
    private AccessibilityNodeInfo find(AccessibilityNodeInfo node,String text,boolean prefix) {
        if(node==null)return null;
        String value=node.getText()==null ? "" : node.getText().toString();
        if((prefix ? value.startsWith(text) : value.equalsIgnoreCase(text)) || text.contentEquals(node.getContentDescription()==null ? "" : node.getContentDescription())) return node;
        for(int i=0;i<node.getChildCount();i++) { AccessibilityNodeInfo match=find(node.getChild(i),text,prefix); if(match!=null)return match; }
        return null;
    }
    private AccessibilityNodeInfo waitNode(String text,long timeout) {
        long end=SystemClock.uptimeMillis()+timeout;
        do { AccessibilityNodeInfo found=find(ui.getRootInActiveWindow(),text,false); if(found!=null)return found; SystemClock.sleep(100); } while(SystemClock.uptimeMillis()<end);
        throw new AssertionError("Missing: "+text);
    }
    private AccessibilityNodeInfo description(AccessibilityNodeInfo node,String text) {
        if(node==null)return null;
        if(text.contentEquals(node.getContentDescription()==null ? "" : node.getContentDescription()))return node;
        for(int i=0;i<node.getChildCount();i++) { AccessibilityNodeInfo found=description(node.getChild(i),text); if(found!=null)return found; }
        return null;
    }
    private void click(String text) {
        AccessibilityNodeInfo node=description(ui.getRootInActiveWindow(),text);
        clickNode(node!=null ? node : waitNode(text,10000));
    }
    private void clickPrefix(String text) { AccessibilityNodeInfo node=find(ui.getRootInActiveWindow(),text,true); assertNotNull(text,node); clickNode(node); }
    private void clickNode(AccessibilityNodeInfo node) {
        node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId());
        SystemClock.sleep(150); node.refresh();
        if (!node.isClickable() || !node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            android.graphics.Rect bounds=new android.graphics.Rect(); node.getBoundsInScreen(bounds);
            assertTrue("Visible target",node.isVisibleToUser() && !bounds.isEmpty());
            long now=SystemClock.uptimeMillis();
            MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,bounds.centerX(),bounds.centerY(),0);
            MotionEvent up=MotionEvent.obtain(now,now+50,MotionEvent.ACTION_UP,bounds.centerX(),bounds.centerY(),0);
            down.setSource(InputDevice.SOURCE_TOUCHSCREEN); up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            try {assertTrue(ui.injectInputEvent(down,true));assertTrue(ui.injectInputEvent(up,true));} finally {down.recycle();up.recycle();}
        }
        SystemClock.sleep(200);
    }
    private void key(int code,long duration) {
        long now=SystemClock.uptimeMillis(); ui.injectInputEvent(new KeyEvent(now,now,KeyEvent.ACTION_DOWN,code,0),true);
        SystemClock.sleep(duration); ui.injectInputEvent(new KeyEvent(now,SystemClock.uptimeMillis(),KeyEvent.ACTION_UP,code,0),true);
    }
    private void capture(String name) throws Exception {
        ui.waitForIdle(500,5000); Bitmap image=ui.takeScreenshot(); assertNotNull(image);
        try(OutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),name))) { assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out)); }
        finally {image.recycle();}
    }
}
