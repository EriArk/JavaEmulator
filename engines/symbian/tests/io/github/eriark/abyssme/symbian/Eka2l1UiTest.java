// SPDX-License-Identifier: GPL-3.0-only
package io.github.eriark.abyssme.symbian;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

public final class Eka2l1UiTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private final Context context=instrumentation.getTargetContext();
    private UiAutomation ui;
    /** Synthetic queue navigation: no firmware, installed game or copy operation. */
    @Test public void savedSelectionReviewCancelAndDiscardWithoutGames() throws Exception {
        ui=instrumentation.getUiAutomation();
        File root=new File(context.getFilesDir(),"symbian-eka-transfer");
        assertFalse("Use an isolated test install without a user's transfer queue",root.exists());
        PhoneTransferQueue queue=new PhoneTransferQueue(new File(root,"queue.bin"));
        PhoneTransferQueue.Item remaining=new PhoneTransferQueue.Item("fixture-pending","Pending fixture",1048576);
        PhoneTransferQueue.Item done=new PhoneTransferQueue.Item("fixture-complete","Completed fixture",1048576);
        done.state=PhoneTransferQueue.State.DONE; done.destination=String.format(Locale.ROOT,"%064x",1);
        queue.items.add(remaining); queue.items.add(done); queue.started=true; queue.save();
        try {
            openSavedQueue(); click("Continue queue"); waitText("Phone transfer results",15000);
            click("Pending fixture · 1 MB"); click("Review selected phones");
            waitText("Copy selected phones",10000); click("Cancel");
            PhoneTransferQueue saved=PhoneTransferQueue.load(queue.file);
            assertEquals(1,saved.selected()); assertEquals(1,saved.completed());
            click("Close");
            openSavedQueue(); click("Continue queue"); waitText("Phone transfer results",15000);
            // Selection survived a fresh Activity. Review remains possible without a new toggle.
            click("Review selected phones"); waitText("Copy selected phones",10000); click("Cancel");
            capture("eka-synthetic-queue.png");
            String discard=context.getString(ru.playsoftware.j2meloader.R.string.eka_batch_discard);
            click(discard); click(discard); waitText("Choose ZIP",15000);
            assertFalse(root.exists());
        } finally {
            context.startActivity(new Intent(context,ru.playsoftware.j2meloader.MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            instrumentation.waitForIdleSync(); SystemClock.sleep(500);
            Eka2l1Transfer.remove(root);
        }
    }
    private void openSavedQueue() {
        context.startActivity(new Intent(context,SymbianActivity.class).putExtra("symbian.ekaTransfer",true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        waitText(context.getString(ru.playsoftware.j2meloader.R.string.eka_batch_saved),15000);
    }
    @Test public void pickerPreviewCancelImportAndLibrary() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("ekaUi")));
        ui=instrumentation.getUiAutomation();
        File fixture=new File(context.getExternalFilesDir(null),"eka-transfer-device-fixture.zip");
        assertTrue("Run Eka2l1DeviceTest with ekaExportFixture first",fixture.isFile());
        SystemFileCatalog catalog=new SystemFileCatalog(context);
        SymbianLibraryIndex.Entry sourceGame=new SymbianLibraryIndex(context).read().stream().filter(e -> e.title.equals("Snakes")).findFirst().orElse(null);
        assertNotNull(sourceGame);
        SystemFileCatalog.Entry sourcePhone=catalog.read(sourceGame.system);
        String phoneLabel=sourcePhone.model+" / "+sourcePhone.firmware;
        Set<String> before=new HashSet<>(); for(SystemFileCatalog.Entry e:catalog.list())before.add(e.id);
        File active=new File(context.getFilesDir(),"symbian-systems/active.json");
        byte[] prior=active.isFile()?Files.readAllBytes(active.toPath()):null;
        ContentValues values=new ContentValues(); values.put(MediaStore.MediaColumns.DISPLAY_NAME,"AbyssME-EKA-test-"+System.currentTimeMillis()+".zip");
        values.put(MediaStore.MediaColumns.MIME_TYPE,"application/zip");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH,"Download");
        Uri document=context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);
        assertNotNull(document);
        try {
            try(OutputStream output=context.getContentResolver().openOutputStream(document)) { assertNotNull(output); Files.copy(fixture.toPath(),output); }
            open(); waitText("Choose ZIP",30000); capture("eka-source.png"); click("Choose ZIP");
            chooseDocument(values.getAsString(MediaStore.MediaColumns.DISPLAY_NAME));
            waitText("Choose virtual phones",60000); capture("eka-phones.png"); click(phoneLabel+" · "+phoneSize(fixture)+" MB"); click("Review selected phones");
            waitText("Copy selected phones",10000); capture("eka-preview.png"); click("Cancel");
            assertEquals(before.size(),catalog.list().size());
            click("Review selected phones"); click("Copy selected phones");
            waitText("Phone transfer results",90000); capture("eka-complete.png");
            List<SymbianLibraryIndex.Entry> games=new SymbianLibraryIndex(context).read();
            assertTrue(games.stream().anyMatch(e -> !before.contains(e.system) && e.title.equals("Snakes")));
            click("Library"); waitText("All games",15000); capture("eka-library.png");
        } finally {
            context.startActivity(new Intent(context,ru.playsoftware.j2meloader.MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK)); SystemClock.sleep(1500);
            if(prior==null)Files.deleteIfExists(active.toPath());else Files.write(active.toPath(),prior);
            for(SystemFileCatalog.Entry phone:catalog.list()) if(!before.contains(phone.id) && phone.restored) {
                PhoneBackupTest.remove(catalog.set(phone.id));
                File[] index=new File(context.getFilesDir(),"symbian-library").listFiles(f -> f.getName().startsWith(phone.id));
                if(index!=null)for(File file:index)Files.deleteIfExists(file.toPath());
            }
            context.getContentResolver().delete(document,null,null);
        }
    }
    private long phoneSize(File fixture) throws Exception {
        try(Eka2l1Transfer transfer=new Eka2l1Transfer(context.getCacheDir(),text -> {})) {
            transfer.readZip(fixture); return (transfer.size(transfer.phones.get(0))+1048575)/1048576;
        }
    }
    private void open() {
        context.startActivity(new Intent(context,ru.playsoftware.j2meloader.catalog.LibraryImportActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
        click("Import from EKA2L1");
    }
    private void chooseDocument(String name) {
        click("Show roots"); click("Downloads"); click("Search");
        AccessibilityNodeInfo input=findEditable(ui.getRootInActiveWindow()); assertNotNull("Search input",input);
        android.os.Bundle arguments=new android.os.Bundle(); arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,name);
        assertTrue(input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,arguments));
        ui.injectInputEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_ENTER),true);
        ui.injectInputEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_UP,android.view.KeyEvent.KEYCODE_ENTER),true);
        SystemClock.sleep(700); click(name);
    }
    private AccessibilityNodeInfo findEditable(AccessibilityNodeInfo node) {
        if(node==null)return null; if(node.isEditable())return node;
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo found=findEditable(node.getChild(i)); if(found!=null)return found;}
        return null;
    }
    private AccessibilityNodeInfo find(AccessibilityNodeInfo node,String text) {
        if(node==null)return null;
        if(!node.isEditable() && (text.contentEquals(node.getText()==null?"":node.getText()) || text.contentEquals(node.getContentDescription()==null?"":node.getContentDescription())))return node;
        for(int i=0;i<node.getChildCount();i++) { AccessibilityNodeInfo found=find(node.getChild(i),text); if(found!=null)return found; }
        return null;
    }
    private AccessibilityNodeInfo waitText(String text,long timeout) {
        long end=SystemClock.uptimeMillis()+timeout;
        do { AccessibilityNodeInfo found=find(ui.getRootInActiveWindow(),text); if(found!=null)return found; SystemClock.sleep(150); } while(SystemClock.uptimeMillis()<end);
        capture("eka-failure.png"); throw new AssertionError("Missing UI text: "+text);
    }
    private void click(String text) {
        AccessibilityNodeInfo node=waitText(text,15000);
        // Dialog headings can have the same text as their confirmation action.
        AccessibilityNodeInfo clickable=findClickable(ui.getRootInActiveWindow(),text);
        if(clickable!=null)node=clickable;
        node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SHOW_ON_SCREEN.getId()); SystemClock.sleep(200); node.refresh();
        while(!node.isClickable() && node.getParent()!=null)node=node.getParent();
        if(!node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            android.graphics.Rect bounds=new android.graphics.Rect(); node.getBoundsInScreen(bounds);
            assertFalse("Visible target "+text,bounds.isEmpty());
            long now=SystemClock.uptimeMillis();
            for(int action:new int[]{android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP}) {
                android.view.MotionEvent event=android.view.MotionEvent.obtain(now,SystemClock.uptimeMillis(),action,bounds.exactCenterX(),bounds.exactCenterY(),0);
                event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
                assertTrue(ui.injectInputEvent(event,true)); event.recycle();
            }
        }
        SystemClock.sleep(700);
    }
    private AccessibilityNodeInfo findClickable(AccessibilityNodeInfo node,String text) {
        if(node==null)return null;
        if(node.isClickable() && node.isEnabled() && (text.contentEquals(node.getText()==null?"":node.getText())
                || text.contentEquals(node.getContentDescription()==null?"":node.getContentDescription())))return node;
        for(int i=0;i<node.getChildCount();i++) {
            AccessibilityNodeInfo found=findClickable(node.getChild(i),text); if(found!=null)return found;
        }
        return null;
    }
    private void capture(String name) {
        SystemClock.sleep(600);
        try(FileOutputStream output=new FileOutputStream(new File(context.getExternalFilesDir(null),name))) {
            android.graphics.Bitmap image=ui.takeScreenshot(); assertNotNull(image);
            image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output); image.recycle();
        } catch(IOException error) { throw new AssertionError(error); }
    }
}
