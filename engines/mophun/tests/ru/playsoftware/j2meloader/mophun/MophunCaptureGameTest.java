package ru.playsoftware.j2meloader.mophun;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.Assume;
import org.junit.Test;
import ru.playsoftware.j2meloader.applist.AppItem;
import ru.playsoftware.j2meloader.applist.GameArtwork;
import ru.playsoftware.j2meloader.appsdb.AppDatabase;
import ru.playsoftware.j2meloader.config.ProfileModel;
import ru.playsoftware.j2meloader.config.ProfilesManager;
import static org.junit.Assert.*;

/** Opt-in installed game; screenshots/isolated catalog fixtures do not alter the user's library. */
public class MophunCaptureGameTest {
    private static Object get(Object object, String name) {
        try { Field f=object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object); }
        catch(Exception e) { throw new AssertionError(e); }
    }
    private static void set(Object object, String name, Object value) {
        try { Field f=object.getClass().getDeclaredField(name); f.setAccessible(true); f.set(object,value); }
        catch(Exception e) { throw new AssertionError(e); }
    }
    private static void invoke(Object object, String name) {
        try { Method m=object.getClass().getDeclaredMethod(name); m.setAccessible(true); m.invoke(object); }
        catch(Exception e) { throw new AssertionError(e); }
    }
    private static Bitmap frame(ActivityScenario<MophunGameActivity> scenario, boolean enhanced) throws Exception {
        AtomicReference<Bitmap> result=new AtomicReference<>(); CountDownLatch done=new CountDownLatch(1);
        scenario.onActivity(activity -> {
            try {
                Method m=activity.getClass().getDeclaredMethod("captureFrame",boolean.class,Consumer.class);
                m.setAccessible(true);
                assertEquals(true,m.invoke(activity,enhanced,(Consumer<Bitmap>) image -> {
                    result.set(image.copy(Bitmap.Config.ARGB_8888,false)); done.countDown();
                }));
            } catch(Exception e) { throw new AssertionError(e); }
        });
        assertTrue(done.await(5,TimeUnit.SECONDS)); assertNotNull(result.get()); return result.get();
    }
    private static Set<Uri> screenshots(Context context) {
        Set<Uri> result=new HashSet<>();
        try(android.database.Cursor cursor=context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[]{"_id"},"owner_package_name=? AND relative_path=? AND is_pending=0",
                new String[]{context.getPackageName(),"Pictures/AbyssME/"},null)) {
            assertNotNull(cursor);
            while(cursor.moveToNext()) result.add(android.content.ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,cursor.getLong(0)));
        }
        return result;
    }
    private static void save(Context context, String name, Bitmap bitmap) throws Exception {
        try(FileOutputStream stream=new FileOutputStream(new File(context.getExternalFilesDir(null),name+".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,stream));
        }
    }

    @Test public void installedGameExportsBothSourcesAndPausesWithSafeArtwork() throws Exception {
        String id=InstrumentationRegistry.getArguments().getString("mophunGame"); Assume.assumeNotNull(id);
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        File root=Files.createTempDirectory(context.getCacheDir().toPath(),"capture-catalog-").toFile();
        File directory=new File(root,"converted/capture-fixture"); assertTrue(directory.mkdirs());
        try(AppDatabase db=AppDatabase.open(context,root.getAbsolutePath())) {
            db.appItemDao().insert(new AppItem(directory.getName(),"Capture fixture","Test","1"));
        }
        Set<Uri> before=screenshots(context), created=new HashSet<>();
        Intent intent=new Intent(context,MophunGameActivity.class).putExtra("id",id);
        try(ActivityScenario<MophunGameActivity> scenario=ActivityScenario.launch(intent)) {
            Object[] original=new Object[3]; ExecutorService[] disk=new ExecutorService[1];
            try {
                Thread.sleep(1500);
                scenario.onActivity(activity -> {
                    assertFalse(activity.isFinishing());
                    original[0]=get(activity,"directory"); original[1]=get(activity,"libraryRoot"); original[2]=get(activity,"captured");
                    disk[0]=(ExecutorService)get(activity,"disk");
                    ProfileModel profile=(ProfileModel)get(activity,"profile"); ProfilesManager.beginPreview(profile);
                    profile.fsrUpscaling=true; profile.frameInterpolation=true; profile.mophunCaptureEnhanced=true;
                    profile.screenRotation=90; profile.screenScaleType=1; profile.screenScaleRatio=100;
                    profile.screenshotWithEffect=false; profile.controlMode=1; profile.touchVisibility=1;
                    invoke(activity,"applyPreview");
                });
                int[] dimensions=new int[4]; long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
                do {
                    scenario.onActivity(activity -> {
                        MophunGpuView view=(MophunGpuView)get(activity,"gpuView");
                        MophunGpuView.State state=(MophunGpuView.State)get(view,"presented");
                        if(state!=null && state.rotation==90 && state.screen.flips>=20) {
                            dimensions[0]=state.width; dimensions[1]=state.height;
                            dimensions[2]=state.screen.pw; dimensions[3]=state.screen.ph;
                        }
                    });
                    if(dimensions[0]>0) break; Thread.sleep(50);
                } while(System.nanoTime()<end);
                assertTrue("No GPU game frame",dimensions[0]>0);
                Bitmap enhanced=frame(scenario,true), source=frame(scenario,false);
                try {
                    assertEquals(dimensions[0],enhanced.getWidth()); assertEquals(dimensions[1],enhanced.getHeight());
                    assertEquals(dimensions[2],source.getWidth()); assertEquals(dimensions[3],source.getHeight());
                    assertTrue(enhanced.getWidth()>source.getWidth() || enhanced.getHeight()>source.getHeight());
                    save(context,"mophun-capture-enhanced",enhanced); save(context,"mophun-capture-source",source);
                } finally { enhanced.recycle(); source.recycle(); }
                scenario.onActivity(a -> invoke(a,"takeScreenshot"));
                end=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
                do {
                    created=screenshots(context); created.removeAll(before);
                    if(!created.isEmpty()) break; Thread.sleep(50);
                } while(System.nanoTime()<end);
                assertEquals(1,created.size());
                try(java.io.InputStream stream=context.getContentResolver().openInputStream(created.iterator().next())) {
                    Bitmap saved=BitmapFactory.decodeStream(stream); assertNotNull(saved);
                    assertEquals(dimensions[1],saved.getWidth()); assertEquals(dimensions[0],saved.getHeight());
                    save(context,"mophun-capture-gallery",saved); saved.recycle();
                }
                scenario.onActivity(activity -> {
                    set(activity,"directory",directory); set(activity,"libraryRoot",root); set(activity,"captured",false);
                });
                scenario.moveToState(Lifecycle.State.CREATED);
                disk[0].submit(() -> {}).get(5,TimeUnit.SECONDS);
                Bitmap cover=BitmapFactory.decodeFile(new File(directory,"cover.png").getPath()); assertNotNull(cover);
                assertEquals(dimensions[1],cover.getWidth()); assertEquals(dimensions[0],cover.getHeight());
                save(context,"mophun-capture-artwork",cover); cover.recycle();
                byte[] originalCover=Files.readAllBytes(new File(directory,"cover.png").toPath());
                Files.copy(new File(directory,"icon.png").toPath(),new File(directory,GameArtwork.USER_ICON).toPath());
                Files.delete(new File(directory,"cover.png").toPath());
                scenario.moveToState(Lifecycle.State.RESUMED);
                scenario.onActivity(activity -> {
                    set(activity,"captured",false);
                    ((ProfileModel)get(activity,"profile")).mophunCaptureEnhanced=false;
                    invoke(activity,"captureArtwork");
                });
                disk[0].submit(() -> {}).get(5,TimeUnit.SECONDS);
                try(AppDatabase db=AppDatabase.open(context,root.getAbsolutePath())) {
                    AppItem item=db.appItemDao().getByPath(directory.getName());
                    assertEquals(directory.getName()+"/"+GameArtwork.USER_ICON,item.getImagePath());
                    assertEquals(directory.getName()+"/cover.png",item.getCoverPath());
                }
                cover=BitmapFactory.decodeFile(new File(directory,"cover.png").getPath()); assertNotNull(cover);
                assertEquals(dimensions[3],cover.getWidth()); assertEquals(dimensions[2],cover.getHeight()); cover.recycle();
                Files.write(new File(directory,GameArtwork.USER_COVER).toPath(),originalCover);
                Files.delete(new File(directory,"cover.png").toPath());
                scenario.onActivity(activity -> { set(activity,"captured",false); invoke(activity,"captureArtwork"); });
                disk[0].submit(() -> {}).get(5,TimeUnit.SECONDS);
                assertFalse(new File(directory,"cover.png").exists());
                assertArrayEquals(originalCover,Files.readAllBytes(new File(directory,GameArtwork.USER_COVER).toPath()));
            } finally {
                scenario.moveToState(Lifecycle.State.RESUMED);
                scenario.onActivity(activity -> {
                    if(original[0]!=null) {
                        set(activity,"directory",original[0]); set(activity,"libraryRoot",original[1]); set(activity,"captured",original[2]);
                    }
                    ProfileModel profile=(ProfileModel)get(activity,"profile");
                    if(profile!=null && ProfilesManager.isPreview(profile)) invoke(activity,"undoPreview");
                });
            }
        } finally {
            for(Uri uri:created) context.getContentResolver().delete(uri,null,null);
            try(java.util.stream.Stream<java.nio.file.Path> paths=Files.walk(root.toPath())) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.delete(path); } catch(java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
                });
            }
        }
    }
}
