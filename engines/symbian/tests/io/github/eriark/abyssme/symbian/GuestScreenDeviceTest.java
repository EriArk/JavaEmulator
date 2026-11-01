// SPDX-License-Identifier: GPL-3.0-only
package io.github.eriark.abyssme.symbian;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.os.SystemClock;
import android.view.*;
import androidx.test.platform.app.InstrumentationRegistry;
import com.github.eka2l1.emu.Emulator;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.*;
import ru.playsoftware.j2meloader.MainActivity;
import static org.junit.Assert.*;

/** Explicit opt-in: never launch against the owner's installed phone or saves. */
public final class GuestScreenDeviceTest {
    @Test public void copiedPhoneRendersAtGuestSizesAndRestoresOriginal() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("guestScreen")));
        Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        Context context=instrumentation.getTargetContext();
        String title=InstrumentationRegistry.getArguments().getString("gameTitle","Snakes");
        SymbianLibraryIndex.Entry game=new SymbianLibraryIndex(context).read().stream()
                .filter(entry -> entry.title.equals(title)).findFirst().orElse(null);
        assertNotNull("Supply the permitted game fixture: "+title,game);
        SystemFileCatalog catalog=new SystemFileCatalog(context);
        SystemFileCatalog.Entry phone=catalog.read(game.system);
        boolean supported=SymbianGameSettings.supportsGuestScreen(phone.os);
        File original=catalog.runtime(phone);
        Map<String,String> before=hashes(original);
        File copy=Files.createTempDirectory(context.getCacheDir().toPath(),"guest-screen-test-").toFile();
        Activity[] activity=new Activity[1];
        AtomicBoolean attached=new AtomicBoolean();
        try {
            Emulator.stopSession();
            String[] settings=Emulator.portablePhoneSettings(original.getAbsolutePath());
            try(java.util.stream.Stream<Path> paths=Files.walk(original.toPath())) {
                for(Path path:(Iterable<Path>)paths::iterator) {
                    Path target=copy.toPath().resolve(original.toPath().relativize(path));
                    if(Files.isDirectory(path))Files.createDirectories(target); else Files.copy(path,target);
                }
            }
            Files.write(new File(copy,"config.yml").toPath(),settings[0].getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Files.write(new File(copy,"data/devices.yml").toPath(),settings[1].getBytes(java.nio.charset.StandardCharsets.UTF_8));
            activity[0]=instrumentation.startActivitySync(new Intent(context,MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            int[] nativeSize=null;
            for(int preset:new int[]{0,1,2,0}) {
                if(!supported && preset!=0) {
                    try {Emulator.prepareGame(copy.getAbsolutePath(),preset);fail("Legacy screen override accepted");}
                    catch(IOException expected) { }
                    finally {Emulator.stopSession();}
                    continue;
                }
                Emulator.prepareGame(copy.getAbsolutePath(),preset);
                Emulator.getApps();
                assertTrue(Emulator.startSession());
                Emulator.lastGuestExit=null;
                instrumentation.runOnMainSync(() -> {
                    TextureView view=new TextureView(activity[0]);
                    view.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                        private Surface output;
                        public void onSurfaceTextureAvailable(SurfaceTexture texture,int w,int h) {
                            output=new Surface(texture);onSurfaceTextureSizeChanged(texture,w,h);
                        }
                        public void onSurfaceTextureSizeChanged(SurfaceTexture texture,int w,int h) {
                            Emulator.setScreenParams(Color.BLACK,100,1,2,"",0,true);
                            Emulator.surfaceChanged(output,w,h);attached.set(true);Emulator.pauseSession(false);
                        }
                        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
                            if(attached.getAndSet(false))Emulator.surfaceDestroyed();
                            if(output!=null)output.release();return true;
                        }
                        public void onSurfaceTextureUpdated(SurfaceTexture texture) { }
                    });
                    activity[0].setContentView(view);
                });
                long end=SystemClock.uptimeMillis()+10000;
                while(!attached.get() && SystemClock.uptimeMillis()<end)SystemClock.sleep(50);
                assertTrue(attached.get());
                assertTrue(Emulator.launchGame((int)game.uid));SystemClock.sleep(8000);
                int[] info=null;
                for(int tries=0;tries<100 && info==null;tries++) {info=Emulator.guestScreenInfo();SystemClock.sleep(20);}
                assertNotNull(info);
                if(nativeSize==null)nativeSize=info.clone();
                int width=preset==0?nativeSize[0]:preset==1?Math.min(nativeSize[0],nativeSize[1]):Math.max(nativeSize[0],nativeSize[1]);
                int height=preset==0?nativeSize[1]:preset==1?Math.max(nativeSize[0],nativeSize[1]):Math.min(nativeSize[0],nativeSize[1]);
                assertEquals(width,info[0]);assertEquals(height,info[1]);
                assertTrue("Active mode must use the requested pixels, possibly rotated",
                        (width==info[2] && height==info[3]) || (width==info[3] && height==info[2]));
                assertTrue(info[4]>=info[2]);
                android.os.Bundle report=new android.os.Bundle();
                report.putString("stream",title+" preset="+preset+" panel/mode/pitch/count="+Arrays.toString(info)+"\n");
                instrumentation.sendStatus(0,report);
                File frame=new File(context.getExternalFilesDir(null),"guest-screen-"+game.uid+"-"+preset+".png");
                assertTrue(Emulator.saveScreenshotTo(frame.getAbsolutePath()));
                Bitmap image=BitmapFactory.decodeFile(frame.getAbsolutePath());assertNotNull(image);
                try {
                    // GLES capture can be a host-resolution frame; geometry above is the guest evidence.
                    assertTrue(image.getWidth()>0 && image.getHeight()>0);
                    android.util.Log.i("GuestScreenTest","Capture "+image.getWidth()+"x"+image.getHeight());
                }
                finally {image.recycle();}
                android.util.Log.i("GuestScreenTest",title+" preset="+preset+" info="+Arrays.toString(info)+" exit="+Emulator.lastGuestExit);
                instrumentation.runOnMainSync(() -> {
                    if(attached.getAndSet(false))Emulator.surfaceDestroyed();
                    activity[0].setContentView(new android.widget.FrameLayout(activity[0]));
                });
                instrumentation.waitForIdleSync();Emulator.stopSession();
            }
            assertEquals(before,hashes(original));
        } finally {
            instrumentation.runOnMainSync(() -> {
                if(attached.getAndSet(false))Emulator.surfaceDestroyed();
                if(activity[0]!=null)activity[0].finish();
            });
            instrumentation.waitForIdleSync();Emulator.stopSession();PhoneBackupTest.remove(copy);
        }
    }
    private static Map<String,String> hashes(File root) throws IOException {
        Map<String,String> result=new TreeMap<>();
        try(java.util.stream.Stream<Path> paths=Files.walk(root.toPath())) {
            for(Path path:(Iterable<Path>)paths.filter(Files::isRegularFile)::iterator)
                result.put(root.toPath().relativize(path).toString(),SystemFileCatalog.hash(path.toFile()));
        }
        return result;
    }
}
