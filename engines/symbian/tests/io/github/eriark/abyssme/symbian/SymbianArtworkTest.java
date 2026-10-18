package io.github.eriark.abyssme.symbian;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import org.junit.Before;
import org.junit.Test;
import ru.playsoftware.j2meloader.applist.AppItem;
import ru.playsoftware.j2meloader.applist.GameArtwork;
import ru.playsoftware.j2meloader.catalog.ExternalLibrary;
import static org.junit.Assert.*;

public final class SymbianArtworkTest {
    private Context context;
    private SymbianLibraryIndex index;
    private SystemFileCatalog.Entry system(int n) {
        return new SystemFileCatalog.Entry(String.format(Locale.ROOT,"%064x",n),"Test phone","RM-320","epoc93fp1");
    }
    @Before public void setup() throws Exception {
        Context base=InstrumentationRegistry.getInstrumentation().getTargetContext();
        File files=Files.createTempDirectory(base.getCacheDir().toPath(),"symbian-art-").toFile();
        context=new ContextWrapper(base) {
            @Override public File getFilesDir() { return files; }
            @Override public SharedPreferences getSharedPreferences(String name,int mode) {
                return super.getSharedPreferences(files.getName()+name,mode);
            }
        };
        index=new SymbianLibraryIndex(context);
        index.publish(system(1),new String[]{"123","One","Symbian","124","Two","N-Gage","125","Three","N-Gage 2.0"},false);
        index.publish(system(2),new String[]{"123","Other phone","Symbian"},false);
    }
    static byte[] image(int color) {
        Bitmap bitmap=Bitmap.createBitmap(48,64,Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(color);
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out)); bitmap.recycle(); return out.toByteArray();
    }
    private List<AppItem> items() throws Exception { return new SymbianLauncherLibrary().read(context); }
    private File directory(AppItem item) { return ((ExternalLibrary.Item)item).getArtworkDirectory(); }
    @Test public void allPlatformsAndPhonesHaveIndependentCustomArt() throws Exception {
        Set<File> directories=new HashSet<>();
        for(AppItem item:items()) {
            assertTrue(item.getId() < -1); assertTrue(directories.add(directory(item)));
            assertNull(item.getImagePathExt()); assertNull(item.getCoverPathExt());
            GameArtwork.saveUserImage(directory(item),true,new ByteArrayInputStream(image(0xff224466)));
            GameArtwork.saveUserImage(directory(item),false,new ByteArrayInputStream(image(0xff66aa88)));
        }
        for(AppItem item:items()) {
            assertEquals(new File(directory(item),GameArtwork.USER_ICON).getPath(),item.getImagePathExt());
            assertEquals(new File(directory(item),GameArtwork.USER_COVER).getPath(),item.getCoverPathExt());
        }
    }
    @Test public void rescanRenameAndAutomaticReplacementKeepCustomImagesAndIdentity() throws Exception {
        AppItem before=items().get(0);
        byte[] custom=image(0xffaa3344);
        GameArtwork.saveUserImage(directory(before),false,new ByteArrayInputStream(custom));
        SymbianLibraryIndex.Entry entry=index.read().get(0);
        Files.write(entry.cover.toPath(),image(0xff000000));
        index.publish(system(1),new String[]{"123","Renamed","N-Gage"},false);
        Files.write(entry.cover.toPath(),image(0xffffffff));
        AppItem after=items().get(0);
        assertEquals(before.getId(),after.getId()); assertEquals(before.getPath(),after.getPath());
        assertEquals("Renamed",after.getTitle());
        assertArrayEquals(custom,Files.readAllBytes(new File(after.getCoverPathExt()).toPath()));
        assertNull(items().get(1).getCoverPathExt());
    }
    @Test public void resettingOneImageRestoresAutomaticWithoutResettingTheOther() throws Exception {
        SymbianLibraryIndex.Entry entry=index.read().get(0);
        byte[] automatic=image(0xff335577),custom=image(0xffbb8844);
        Files.write(entry.icon.toPath(),automatic); Files.write(entry.cover.toPath(),automatic);
        AppItem item=items().get(0);
        GameArtwork.saveUserImage(directory(item),true,new ByteArrayInputStream(custom));
        GameArtwork.saveUserImage(directory(item),false,new ByteArrayInputStream(custom));
        Files.delete(new File(directory(item),GameArtwork.USER_ICON).toPath());
        item=items().get(0);
        assertEquals(entry.icon.getPath(),item.getImagePathExt());
        assertArrayEquals(custom,Files.readAllBytes(new File(item.getCoverPathExt()).toPath()));
        Files.delete(new File(directory(item),GameArtwork.USER_COVER).toPath());
        assertEquals(entry.cover.getPath(),items().get(0).getCoverPathExt());
        assertArrayEquals(automatic,Files.readAllBytes(entry.icon.toPath()));
        assertArrayEquals(automatic,Files.readAllBytes(entry.cover.toPath()));
    }
    @Test public void invalidReplacementPreservesCustomImageAndSnapshot() throws Exception {
        AppItem item=items().get(0);
        byte[] original=image(0xff998855);
        File snapshot=new File(context.getFilesDir(),"symbian-library/"+system(1).id+".json");
        byte[] before=Files.readAllBytes(snapshot.toPath());
        GameArtwork.saveUserImage(directory(item),true,new ByteArrayInputStream(original));
        try { GameArtwork.saveUserImage(directory(item),true,new ByteArrayInputStream(new byte[]{1,2})); fail(); }
        catch(IOException expected) { }
        assertArrayEquals(original,Files.readAllBytes(new File(items().get(0).getImagePathExt()).toPath()));
        assertArrayEquals(before,Files.readAllBytes(snapshot.toPath()));
    }
    @Test public void resetWithoutAutomaticImageReturnsToFallback() throws Exception {
        AppItem item=items().get(0);
        GameArtwork.saveUserImage(directory(item),false,new ByteArrayInputStream(image(0xffabcdef)));
        Files.delete(new File(directory(item),GameArtwork.USER_COVER).toPath());
        assertNull(items().get(0).getCoverPathExt());
        assertNull(items().get(0).getImagePathExt());
    }
}
