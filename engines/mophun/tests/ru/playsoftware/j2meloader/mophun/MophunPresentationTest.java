package ru.playsoftware.j2meloader.mophun;

import android.graphics.Bitmap;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.PixelCopy;
import android.widget.FrameLayout;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import ru.playsoftware.j2meloader.config.ProfileModel;
import ru.playsoftware.j2meloader.settings.SettingsActivity;
import wolphun.Screen;
import static org.junit.Assert.*;

public class MophunPresentationTest {
    private static Object field(Object value, String name) throws Exception {
        Field f=value.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(value);
    }

    @Test public void completedSnapshotHasCoherentIdentityAndDoesNotCountHostRedraws() throws Exception {
        Screen screen=new Screen(16,12,1); screen.targetFps=0;
        int[] pixels=new int[192]; long[] stamp=new long[2];
        assertFalse(screen.copyPresented(pixels,stamp,0));
        Arrays.fill(screen.fb,0xFFFF0000); screen.present();
        assertTrue(screen.copyPresented(pixels,stamp,0));
        assertEquals(1,stamp[0]); long first=stamp[1];
        Arrays.fill(screen.fb,0xFF0000FF);
        assertFalse(screen.copyPresented(pixels,stamp,1));
        for(int color:pixels) assertEquals(0xFFFF0000,color);
        screen.present(); assertTrue(screen.copyPresented(pixels,stamp,1));
        assertEquals(2,stamp[0]); assertTrue(stamp[1]>=first); assertEquals(2,screen.flips);
        for(int color:pixels) assertEquals(0xFF0000FF,color);
    }

    @Test public void concurrentSnapshotsNeverMixPixelsAndSerial() throws Exception {
        Screen screen=new Screen(32,24,1); screen.targetFps=0;
        Thread writer=new Thread(() -> {
            for(int frame=1;frame<=300;frame++) {
                Arrays.fill(screen.fb,0xFF000000|frame); screen.present();
            }
        });
        int[] pixels=new int[32*24]; long[] stamp=new long[2]; writer.start();
        do {
            if(screen.copyPresented(pixels,stamp,stamp[0]))
                for(int color:pixels) assertEquals(0xFF000000|(int)stamp[0],color);
        } while(writer.isAlive());
        writer.join(); assertEquals(300,screen.presentationSerial());
    }

    private static final class Host implements AutoCloseable {
        final ActivityScenario<SettingsActivity> scenario=ActivityScenario.launch(SettingsActivity.class);
        final Screen screen=new Screen(16,12,1);
        final AtomicBoolean failed=new AtomicBoolean();
        ProfileModel profile;
        MophunGpuView view;
        final RectF area=new RectF(20,30,220,210);
        Host() {
            screen.targetFps=0;
            for(int y=0;y<12;y++) for(int x=0;x<16;x++)
                screen.fb[y*16+x]=y<6?(x<8?0xFFFF0000:0xFF00FF00):(x<8?0xFF0000FF:0xFFFFFFFF);
            screen.present();
            scenario.onActivity(activity -> {
                profile=new ProfileModel(activity.getCacheDir()); profile.fsrUpscaling=true;
                view=new MophunGpuView(activity, () -> {}, () -> failed.set(true));
                FrameLayout root=new FrameLayout(activity); root.addView(view,new FrameLayout.LayoutParams(260,240));
                activity.setContentView(root); view.active(true);
            });
        }
        void draw(int width,int height,int rotation,boolean paused) throws Exception {
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10); AtomicBoolean ready=new AtomicBoolean();
            do {
                scenario.onActivity(a -> ready.set(view.update(screen,area,width,height,rotation,profile,paused)));
                if(ready.get() && (long)field(view,"renderedSerial")==screen.presentationSerial()) {
                    Thread.sleep(30); return;
                }
                assertFalse("GPU fallback",failed.get()); Thread.sleep(20);
            } while(System.nanoTime()<deadline);
            fail("No presented GPU state");
        }
        Bitmap capture() throws Exception {
            Bitmap bitmap=Bitmap.createBitmap(260,240,Bitmap.Config.ARGB_8888);
            CountDownLatch done=new CountDownLatch(1); int[] result={-1};
            PixelCopy.request(view,bitmap,r -> {result[0]=r;done.countDown();},new Handler(Looper.getMainLooper()));
            assertTrue(done.await(5,TimeUnit.SECONDS)); assertEquals(PixelCopy.SUCCESS,result[0]); return bitmap;
        }
        public void close() { scenario.onActivity(a -> view.active(false)); scenario.close(); }
    }

    @Test public void gpuPixelsMatchCanvasOrientationAndKeepLetterboxAcrossRotations() throws Exception {
        try(Host host=new Host()) {
            int[][] expected={{0xFFFF0000,0xFF00FF00,0xFF0000FF,0xFFFFFFFF},
                    {0xFF0000FF,0xFFFF0000,0xFFFFFFFF,0xFF00FF00},
                    {0xFFFFFFFF,0xFF0000FF,0xFF00FF00,0xFFFF0000},
                    {0xFF00FF00,0xFFFFFFFF,0xFFFF0000,0xFF0000FF}};
            for(int rotation=0;rotation<360;rotation+=90) {
                host.draw(160,120,rotation,false);
                Bitmap image=host.capture();
                int[] actual={image.getPixel(95,95),image.getPixel(145,95),image.getPixel(95,145),image.getPixel(145,145)};
                assertArrayEquals("rotation "+rotation,expected[rotation/90],actual);
                assertEquals(0xFF101417,image.getPixel(5,5)); image.recycle();
            }
            host.draw(400,300,0,false);
            Bitmap clipped=host.capture();
            assertEquals(0xFF101417,clipped.getPixel(19,100));
            assertEquals(0xFFFF0000,clipped.getPixel(21,100));
            assertEquals(0xFF101417,clipped.getPixel(221,100)); clipped.recycle();
        }
    }

    @Test public void effectsPauseResumeAndMinificationKeepRealFramesAndSettings() throws Exception {
        try(Host host=new Host()) {
            host.profile.frameInterpolation=true;
            host.draw(160,120,0,false);
            for(int i=0;i<4;i++) {
                Arrays.fill(host.screen.fb,i%2==0?0xFF00FF00:0xFFFF0000); host.screen.present();
                host.draw(160,120,0,true);
                Bitmap image=host.capture(); assertEquals(host.screen.fb[0],image.getPixel(120,120)); image.recycle();
                host.scenario.onActivity(a -> host.view.active(false));
                host.scenario.onActivity(a -> host.view.active(true));
                host.draw(160,120,0,false);
                assertTrue(host.profile.fsrUpscaling); assertTrue(host.profile.frameInterpolation);
            }
            host.draw(8,6,0,true);
            Bitmap image=host.capture(); assertEquals(0xFFFF0000,image.getPixel(120,120)); image.recycle();
            assertEquals(5,host.screen.presentationSerial()); assertEquals(5,host.screen.flips);
        }
    }

    @Test public void optionalWorkUsesSharedLoadGuardAndRepeatedFramesKeepTheirSerial() throws Exception {
        try(Host host=new Host()) {
            host.profile.frameInterpolation=true; host.draw(160,120,0,false);
            CountDownLatch done=new CountDownLatch(1);
            host.view.queueEvent(() -> {
                try {
                    ru.playsoftware.j2meloader.runtime.InterpolationLoadGuard load=
                            (ru.playsoftware.j2meloader.runtime.InterpolationLoadGuard)field(host.view,"load");
                    long now=System.nanoTime(); for(int i=0;i<3;i++) load.submitted(now,20_000_000,16_666_666);
                } catch(Exception e) { throw new AssertionError(e); } finally { done.countDown(); }
            });
            assertTrue(done.await(5,TimeUnit.SECONDS));
            Arrays.fill(host.screen.fb,0xFF00FF00); host.screen.present();
            host.draw(160,120,0,false);
            // Wait for this serial, not merely the unchanged geometry.
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while((long)field(host.view,"renderedSerial")!=2 && System.nanoTime()<end) Thread.sleep(10);
            assertEquals(2L,field(host.view,"renderedSerial")); assertFalse((boolean)field(host.view,"animating"));
            Bitmap image=host.capture(); assertEquals(0xFF00FF00,image.getPixel(120,120)); image.recycle();
            for(int i=0;i<10;i++) host.draw(160,120,0,false);
            assertEquals(2,host.screen.flips); assertEquals(2L,field(host.view,"renderedSerial"));
        }
    }

    @Test public void rendererErrorRestoresHostFallbackWithoutChangingPreferences() throws Exception {
        try(Host host=new Host()) {
            host.draw(160,120,0,false);
            host.scenario.onActivity(a -> host.view.update(host.screen,host.area,4096,4096,0,host.profile,false));
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(!host.failed.get() && System.nanoTime()<end) Thread.sleep(20);
            assertTrue(host.failed.get()); assertTrue(host.profile.fsrUpscaling);
            host.scenario.onActivity(a -> assertFalse(host.view.update(host.screen,host.area,160,120,0,host.profile,false)));
            assertNull(field(host.view,"pipeline")); assertNull(field(host.view,"bitmap"));
        }
    }
}
