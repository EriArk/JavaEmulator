package io.github.eriark.abyssme.symbian;

import android.app.*;
import android.content.*;
import android.graphics.*;
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

/** Opt-in native pointer smoke test on a private COPY of the supplied RM-356 fixture. */
public final class SymbianNativeTouchTest {
    @Test public void nativeCalculatorRespondsToTouchOnCopiedPhone() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("nativeTouch")));
        Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        Context context=instrumentation.getTargetContext();
        SystemFileCatalog catalog=new SystemFileCatalog(context);
        SystemFileCatalog.Entry phone=catalog.list().stream().filter(e->e.firmware.equals("RM-356")).findFirst().orElse(null);
        assertNotNull("Supply the permitted RM-356 fixture",phone);
        File source=catalog.runtime(phone);
        File copy=Files.createTempDirectory(context.getCacheDir().toPath(),"native-touch-").toFile();
        String seed=UUID.randomUUID().toString().replace("-",""); String identity=seed+seed;
        SymbianSessionUi[] session=new SymbianSessionUi[1]; Activity[] activity=new Activity[1];
        AtomicBoolean attached=new AtomicBoolean(); TextureView[] surface=new TextureView[1];
        try {
            Emulator.stopSession();
            String[] settings=Emulator.portablePhoneSettings(source.getAbsolutePath());
            try(java.util.stream.Stream<java.nio.file.Path> paths=Files.walk(source.toPath())) {
                for(java.nio.file.Path path:(Iterable<java.nio.file.Path>)paths::iterator) {
                    java.nio.file.Path target=copy.toPath().resolve(source.toPath().relativize(path));
                    if(Files.isDirectory(path))Files.createDirectories(target);else Files.copy(path,target);
                }
            }
            Files.write(new File(copy,"config.yml").toPath(),(settings[0]+"\nhide-system-apps: false\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Files.write(new File(copy,"data/devices.yml").toPath(),settings[1].getBytes(java.nio.charset.StandardCharsets.UTF_8));
            Emulator.prepare(copy.getAbsolutePath());
            String[] apps=Emulator.getApps(); long uid=0;
            for(int i=0;i+1<apps.length;i+=2)if(apps[i+1].toLowerCase(Locale.ROOT).contains("calc")) uid=Long.parseLong(apps[i]);
            assertTrue("Calculator registered: "+Arrays.toString(apps),uid!=0);
            assertTrue(Emulator.startSession());
            activity[0]=instrumentation.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            long calculator=uid;
            instrumentation.runOnMainSync(() -> {
                surface[0]=new TextureView(activity[0]);
                surface[0].setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                    private Surface output;
                    public void onSurfaceTextureAvailable(SurfaceTexture texture,int w,int h) {
                        output=new Surface(texture);onSurfaceTextureSizeChanged(texture,w,h);
                    }
                    public void onSurfaceTextureSizeChanged(SurfaceTexture texture,int w,int h) {
                        Emulator.setScreenParams(Color.BLACK,100,session[0].scale(),2,"",0,true);
                        Emulator.surfaceChanged(output,w,h); attached.set(true); Emulator.pauseSession(false);
                    }
                    public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
                        session[0].clear();if(attached.getAndSet(false))Emulator.surfaceDestroyed();
                        output.release();output=null;return true;
                    }
                    public void onSurfaceTextureUpdated(SurfaceTexture texture){}
                });
                try {
                    session[0]=new SymbianSessionUi(activity[0],surface[0],identity,calculator,"Calculator",new SymbianSessionUi.Host() {
                        public RectF touchBounds(){int[] b=Emulator.touchBounds();return b==null?null:new RectF(b[0],b[1],b[2],b[3]);}
                        public void touch(int x,int y,int z,int a,int p){if(attached.get())Emulator.touchScreen(x,y,z,a,p);}
                        public void key(int key,boolean down){if(attached.get())Emulator.pressKey(key,down?0:1);}
                        public void pause(boolean value){if(attached.get())Emulator.pauseSession(value);}
                        public void display(int scale){if(attached.get())Emulator.setScreenParams(Color.BLACK,100,scale,2,"",0,true);}
                        public void frameRate(int fps){Emulator.setFrameRateLimit(fps);}
                        public void leave(){}
                        public void restart(){}
                        public Bitmap capture(){return null;}
                    });
                    activity[0].setContentView(session[0].root);
                } catch(IOException e){throw new AssertionError(e);}
            });
            long end=SystemClock.uptimeMillis()+10000;
            while(!attached.get() && SystemClock.uptimeMillis()<end)SystemClock.sleep(50);
            assertTrue(attached.get());
            assertTrue(Emulator.launchGame((int)calculator)); SystemClock.sleep(10000);
            capture(instrumentation,context,"symbian-touch-native-before.png");
            for(int rotation=0;rotation<4;rotation++) {
                int turn=rotation;
                instrumentation.runOnMainSync(() -> {session[0].profile.screenRotation=turn*90;session[0].apply();});
                instrumentation.waitForIdleSync(); SystemClock.sleep(700);
                Bitmap before=readResult(copy);
                int[] bounds=Emulator.touchBounds();assertNotNull(bounds);
                float[] point={bounds[0]+(bounds[2]-bounds[0])*.125f,bounds[1]+(bounds[3]-bounds[1])*.575f};
                instrumentation.runOnMainSync(() -> {
                    surface[0].getMatrix().mapPoints(point);
                    int[] location=new int[2]; session[0].root.getLocationOnScreen(location);
                    point[0]+=surface[0].getLeft()+location[0];point[1]+=surface[0].getTop()+location[1];
                });
                long now=SystemClock.uptimeMillis();
                MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,point[0],point[1],0);
                MotionEvent up=MotionEvent.obtain(now,now+80,MotionEvent.ACTION_UP,point[0],point[1],0);
                try {instrumentation.getUiAutomation().injectInputEvent(down,true);SystemClock.sleep(80);instrumentation.getUiAutomation().injectInputEvent(up,true);}
                finally {down.recycle();up.recycle();}
                SystemClock.sleep(700);capture(instrumentation,context,"symbian-touch-native-"+rotation+".png");
                Bitmap after=readResult(copy);
                try {assertFalse("Guest result changes after touch at rotation "+rotation,before.sameAs(after));}
                finally {before.recycle();after.recycle();}
            }
        } finally {
            instrumentation.runOnMainSync(() -> {
                if(session[0]!=null)session[0].close();
                if(attached.getAndSet(false))Emulator.surfaceDestroyed();
                if(activity[0]!=null)activity[0].finish();
            });
            instrumentation.waitForIdleSync(); Emulator.stopSession();
            PhoneBackupTest.remove(copy);
            PhoneBackupTest.remove(new File(context.getFilesDir(),"symbian-profiles/"+identity));
        }
    }
    private static Bitmap readResult(File directory) {
        File file=new File(directory,"touch-result.png");assertTrue(Emulator.saveScreenshotTo(file.getAbsolutePath()));
        Bitmap frame=BitmapFactory.decodeFile(file.getAbsolutePath());assertNotNull(frame);
        try {return Bitmap.createBitmap(frame,frame.getWidth()/2,frame.getHeight()*35/100,frame.getWidth()/2,frame.getHeight()*8/100);}
        finally {frame.recycle();}
    }
    private static void capture(Instrumentation instrumentation,Context context,String name) throws IOException {
        Bitmap bitmap=instrumentation.getUiAutomation().takeScreenshot();assertNotNull(bitmap);
        try(FileOutputStream out=new FileOutputStream(new File(context.getExternalFilesDir(null),name))) {assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));}
        finally {bitmap.recycle();}
    }
}
