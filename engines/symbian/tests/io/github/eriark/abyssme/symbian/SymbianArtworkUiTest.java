package io.github.eriark.abyssme.symbian;

import android.app.UiAutomation;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.SystemClock;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.Files;
import org.junit.Test;
import org.junit.Assume;
import ru.playsoftware.j2meloader.MainActivity;
import ru.playsoftware.j2meloader.applist.*;
import ru.playsoftware.j2meloader.catalog.ExternalLibrary;
import static org.junit.Assert.*;

/** Opt-in: existing debug-library entry; original overrides are restored in finally. */
public final class SymbianArtworkUiTest {
    private final Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
    private UiAutomation ui;
    private AppsListFragment fragment(MainActivity activity) {
        for(androidx.fragment.app.Fragment f:activity.getSupportFragmentManager().getFragments())
            if(f instanceof AppsListFragment) return (AppsListFragment)f;
        throw new AssertionError("Library fragment missing");
    }
    private void call(AppsListFragment f,String name,Class<?>[] types,Object...args) {
        try { Method method=AppsListFragment.class.getDeclaredMethod(name,types); method.setAccessible(true); method.invoke(f,args); }
        catch(ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private void pending(AppsListFragment f,int id,boolean icon) {
        try {
            Field target=AppsListFragment.class.getDeclaredField("artworkTargetId"); target.setAccessible(true); target.setInt(f,id);
            Field type=AppsListFragment.class.getDeclaredField("pickingIcon"); type.setAccessible(true); type.setBoolean(f,icon);
        } catch(ReflectiveOperationException e) { throw new AssertionError(e); }
    }
    private AccessibilityNodeInfo find(AccessibilityNodeInfo node,String text) {
        if(node==null) return null;
        if(node.isVisibleToUser() && (text.contentEquals(node.getText()==null?"":node.getText())
                || text.contentEquals(node.getContentDescription()==null?"":node.getContentDescription()))) return node;
        for(int i=0;i<node.getChildCount();i++) { AccessibilityNodeInfo match=find(node.getChild(i),text); if(match!=null) return match; }
        return null;
    }
    private void click(String text) {
        long end=SystemClock.uptimeMillis()+5000;
        do {
            AccessibilityNodeInfo node=find(ui.getRootInActiveWindow(),text);
            if(node!=null && node.isClickable() && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { SystemClock.sleep(200); return; }
            SystemClock.sleep(100);
        } while(SystemClock.uptimeMillis()<end);
        throw new AssertionError("Missing action: "+text);
    }
    private void waitImage(File target,byte[] bytes) throws Exception {
        long end=SystemClock.uptimeMillis()+8000;
        do {
            if(bytes==null ? !target.exists() : target.isFile() && java.util.Arrays.equals(bytes,Files.readAllBytes(target.toPath()))) return;
            SystemClock.sleep(100);
        } while(SystemClock.uptimeMillis()<end);
        fail("Artwork did not update: "+target);
    }
    private void screenshot(String name) throws Exception {
        SystemClock.sleep(500); Bitmap image=ui.takeScreenshot(); assertNotNull(image);
        File dir=new File(context.getExternalFilesDir(null),"symbian-artwork-captures"); assertTrue(dir.isDirectory()||dir.mkdirs());
        try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))) { assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,out)); }
        finally { image.recycle(); }
    }
    @Test public void nativeCardArtworkMenuAndResultSurviveActivityRecreation() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("artworkUi")));
        ExternalLibrary.Item item=(ExternalLibrary.Item)new SymbianLauncherLibrary().read(context).stream()
                .filter(e->e.getTitle().equals("Snakes")).findFirst().orElseThrow(()->new AssertionError("Install Snakes in the debug library"));
        File directory=item.getArtworkDirectory(),icon=new File(directory,GameArtwork.USER_ICON),cover=new File(directory,GameArtwork.USER_COVER);
        byte[] oldIcon=icon.isFile()?Files.readAllBytes(icon.toPath()):null,oldCover=cover.isFile()?Files.readAllBytes(cover.toPath()):null;
        byte[] chosen=SymbianArtworkTest.image(0xff4488aa);
        File image=File.createTempFile("picked-art-",".png",context.getCacheDir()); Files.write(image.toPath(),chosen);
        ui=InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a->call(fragment(a),"showActions",new Class[]{View.class,AppItem.class},a.getWindow().getDecorView(),item));
            click("Edit card"); click("Artwork"); screenshot("snakes-artwork-menu"); click("Cover");
            assertNotNull(find(ui.getRootInActiveWindow(),"Choose image"));
            assertNotNull(find(ui.getRootInActiveWindow(),"Use automatic"));
            click("Back"); click("Close");
            // Deliver the picker callback after recreation; no dependency on a vendor picker UI.
            scenario.onActivity(a->pending(fragment(a),item.getId(),false));
            scenario.recreate();
            scenario.onActivity(a->call(fragment(a),"onArtworkPicked",new Class[]{Uri.class},Uri.fromFile(image)));
            waitImage(cover,chosen);
            scenario.recreate();
            AppItem reloaded=new SymbianLauncherLibrary().read(context).stream().filter(e->e.getId()==item.getId()).findFirst().get();
            assertEquals(cover.getAbsolutePath(),reloaded.getCoverPathExt());
            scenario.onActivity(a->{pending(fragment(a),item.getId(),true); call(fragment(a),"onArtworkPicked",new Class[]{Uri.class},Uri.fromFile(image));});
            waitImage(icon,chosen);
            scenario.onActivity(a->call(fragment(a),"showActions",new Class[]{View.class,AppItem.class},a.getWindow().getDecorView(),item));
            click("Edit card"); click("Artwork"); click("Cover"); click("Use automatic"); waitImage(cover,null);
            assertArrayEquals(chosen,Files.readAllBytes(icon.toPath()));
            scenario.onActivity(a->call(fragment(a),"showActions",new Class[]{View.class,AppItem.class},a.getWindow().getDecorView(),item));
            click("Edit card"); click("Artwork"); click("Icon"); click("Use automatic"); waitImage(icon,null);
            scenario.onActivity(a->{pending(fragment(a),item.getId(),false); call(fragment(a),"onArtworkPicked",new Class[]{Uri.class},(Object)null);});
            assertFalse(cover.exists());
        } finally {
            if(oldIcon==null) Files.deleteIfExists(icon.toPath()); else GameArtwork.saveUserImage(directory,true,new ByteArrayInputStream(oldIcon));
            if(oldCover==null) Files.deleteIfExists(cover.toPath()); else GameArtwork.saveUserImage(directory,false,new ByteArrayInputStream(oldCover));
            Files.deleteIfExists(image.toPath());
        }
    }
}
