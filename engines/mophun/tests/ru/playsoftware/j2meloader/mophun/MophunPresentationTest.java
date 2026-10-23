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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import ru.playsoftware.j2meloader.config.ProfileModel;
import ru.playsoftware.j2meloader.settings.SettingsActivity;
import wolphun.Screen;
import static org.junit.Assert.*;

public class MophunPresentationTest {
    @Test public void integerVerticesUseWholePixelEdgesAcrossOddSizesAndRotations() {
        Screen screen=new Screen(7,9,1);
        java.nio.FloatBuffer vertices=java.nio.FloatBuffer.allocate(16);
        for(int rotation:new int[]{0,90,180,270}) for(int factor:new int[]{1,3,4}) {
            MophunGpuView.State state=new MophunGpuView.State(screen,new RectF(11,13,212,214),
                    7*factor,9*factor,rotation,false,false,50,false,16666667,true);
            state.vertices(vertices,240,240);
            for(int i=0;i<4;i++) {
                float x=(vertices.get(i*4)+1)*120,y=(1-vertices.get(i*4+1))*120;
                assertEquals(Math.round(x),x,.0001); assertEquals(Math.round(y),y,.0001);
                assertTrue(x>=11 && x<=212 && y>=13 && y<=214);
            }
        }
    }
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
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
            boolean success=false;
            try {
                do {
                    CountDownLatch done=new CountDownLatch(1); int[] result={PixelCopy.ERROR_SOURCE_INVALID};
                    // Serialize validation/request with the UI surface callbacks, not a sleep after draw().
                    scenario.onActivity(a -> {
                        if(!view.getHolder().getSurface().isValid()) { done.countDown(); return; }
                        PixelCopy.request(view,bitmap,r -> {result[0]=r;done.countDown();},new Handler(Looper.getMainLooper()));
                    });
                    assertTrue(done.await(5,TimeUnit.SECONDS));
                    if(result[0]==PixelCopy.SUCCESS) { success=true; return bitmap; }
                    assertTrue("Unexpected copy failure: "+result[0],result[0]==PixelCopy.ERROR_SOURCE_INVALID
                            || result[0]==PixelCopy.ERROR_SOURCE_NO_DATA);
                    assertFalse("GPU fallback",failed.get()); view.requestRender(); Thread.sleep(20);
                } while(System.nanoTime()<deadline);
                throw new AssertionError("No valid presented surface for capture");
            } finally { if(!success) bitmap.recycle(); }
        }
        Bitmap export() throws Exception {
            AtomicReference<Bitmap> result = new AtomicReference<>(); CountDownLatch done = new CountDownLatch(1);
            scenario.onActivity(a -> assertTrue(view.capture(image -> { result.set(image); done.countDown(); })));
            assertTrue(done.await(5, TimeUnit.SECONDS)); assertNotNull(result.get()); return result.get();
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

    @Test public void integerGpuMatchesNearestCanvasAtOddViewportOrigins() throws Exception {
        try(Host host=new Host()) {
            host.area.set(21,31,222,212);
            host.profile.screenScaleType=3; host.profile.fsrUpscaling=false;
            host.profile.frameInterpolation=true; host.profile.screenFilter=false;
            Bitmap source=Bitmap.createBitmap(16,12,Bitmap.Config.ARGB_8888);
            for(int y=0;y<12;y++) for(int x=0;x<16;x++) {
                int color=(x+y)%2==0?0xFFFF0000:0xFF0000FF;
                source.setPixel(x,y,color); host.screen.fb[y*16+x]=color;
            }
            host.screen.present();
            try {
                for(int rotation:new int[]{0,90,180,270}) {
                    host.draw(64,48,rotation,true);
                    Bitmap actual=host.capture(),expected=Bitmap.createBitmap(260,240,Bitmap.Config.ARGB_8888);
                    expected.eraseColor(0xFF101417);
                    android.graphics.Canvas canvas=new android.graphics.Canvas(expected); canvas.clipRect(host.area);
                    boolean quarter=rotation%180!=0;
                    canvas.translate(ru.playsoftware.j2meloader.ui.PictureScale.center(host.area.centerX(),quarter?48:64,true),
                            ru.playsoftware.j2meloader.ui.PictureScale.center(host.area.centerY(),quarter?64:48,true));
                    canvas.rotate(rotation);
                    android.graphics.Paint paint=new android.graphics.Paint(); paint.setFilterBitmap(false);
                    canvas.drawBitmap(source,null,new RectF(-32,-24,32,24),paint);
                    int[] a=new int[260*240],b=new int[260*240];
                    actual.getPixels(a,0,260,0,0,260,240); expected.getPixels(b,0,260,0,0,260,240);
                    assertArrayEquals("Integer GPU/Canvas pixels at rotation "+rotation,b,a);
                    actual.recycle(); expected.recycle();
                }
            } finally { source.recycle(); }
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

    @Test public void detachedSurfaceCannotReportReadyOrExportStalePixels() throws Exception {
        try(Host host=new Host()) {
            host.draw(160,120,0,false);
            AtomicReference<android.view.ViewGroup> parent=new AtomicReference<>();
            host.scenario.onActivity(a -> {
                parent.set((android.view.ViewGroup)host.view.getParent()); parent.get().removeView(host.view);
                assertFalse(host.view.getHolder().getSurface().isValid());
                assertFalse("A destroyed surface is not a presented image",
                        host.view.update(host.screen,host.area,160,120,0,host.profile,false));
                assertFalse("No stale GPU capture after detach",host.view.capture(image -> {
                    if(image!=null) image.recycle();
                }));
                parent.get().addView(host.view,new FrameLayout.LayoutParams(260,240));
            });
            host.draw(160,120,0,false);
            Bitmap image=host.capture(); assertEquals(0xFFFF0000,image.getPixel(95,95)); image.recycle();
        }
    }

    @Test public void exportMatchesPresentedPixelsWithoutAdvancingHistoryOrRotatingTwice() throws Exception {
        try (Host host = new Host()) {
            host.profile.frameInterpolation = true;
            for (int rotation = 0; rotation < 360; rotation += 90) {
                host.draw(160, 120, rotation, false);
                Bitmap exported = host.export(), visible = host.capture();
                assertEquals(160, exported.getWidth()); assertEquals(120, exported.getHeight());
                android.graphics.Matrix matrix = new android.graphics.Matrix(); matrix.postRotate(rotation);
                Bitmap rotated = Bitmap.createBitmap(exported, 0, 0, 160, 120, matrix, false);
                int left = 120 - rotated.getWidth()/2, top = 120 - rotated.getHeight()/2;
                for (int y = 0; y < rotated.getHeight(); y++) for (int x = 0; x < rotated.getWidth(); x++) {
                    int expected = visible.getPixel(left+x, top+y), actual = rotated.getPixel(x,y);
                    for (int shift = 0; shift < 32; shift += 8)
                        assertTrue("capture mismatch at " + x + "," + y + " rotation " + rotation,
                                Math.abs(((expected >>> shift)&255)-((actual >>> shift)&255)) <= 2);
                }
                if (rotated != exported) rotated.recycle(); exported.recycle(); visible.recycle();
                assertEquals(1, host.screen.flips);
                Object pipeline = field(host.view, "pipeline");
                assertEquals(1L, field(pipeline, "serial")); assertEquals(true, field(pipeline, "history"));
                assertFalse(host.failed.get());
            }
        }
    }

    @Test public void captureCompletesBeforePauseAndInactiveViewRejectsNewWork() throws Exception {
        try (Host host = new Host()) {
            host.draw(160,120,0,false);
            AtomicReference<Bitmap> result = new AtomicReference<>(); AtomicInteger calls = new AtomicInteger();
            host.scenario.onActivity(a -> {
                assertTrue(host.view.capture(image -> { result.set(image); calls.incrementAndGet(); }));
                host.view.active(false);
                assertEquals(1, calls.get());
                assertFalse(host.view.capture(image -> fail("Inactive view accepted capture")));
            });
            if (result.get() != null) result.get().recycle();
            Thread.sleep(2100); assertEquals(1, calls.get());
            host.scenario.onActivity(a -> host.view.active(true)); host.draw(160,120,0,false);
            Bitmap image = host.export(); assertEquals(0xFFFF0000,image.getPixel(20,20)); image.recycle();
        }
    }

    @Test public void exportingAnIntermediateFrameDoesNotAdvanceItsClock() throws Exception {
        try (Host host = new Host()) {
            host.profile.frameInterpolation = true; host.draw(160,120,0,false);
            java.util.concurrent.FutureTask<Void> prepare = new java.util.concurrent.FutureTask<>(() -> {
                javax.microedition.lcdui.graphics.EnhancementPipeline pipeline =
                        (javax.microedition.lcdui.graphics.EnhancementPipeline)field(host.view,"pipeline");
                Bitmap a = Bitmap.createBitmap(16,12,Bitmap.Config.ARGB_8888);
                Bitmap b = Bitmap.createBitmap(16,12,Bitmap.Config.ARGB_8888);
                try {
                    for (int y=0;y<12;y++) for (int x=0;x<16;x++) {
                        int color = 0xFF000000 | x*13<<16 | y*17<<8 | ((x+y)%2)*255;
                        a.setPixel(x,y,color); b.setPixel((x+2)%16,y,color);
                    }
                    pipeline.resetHistory();
                    pipeline.upload(a,1,16,12,true,0); pipeline.renderForPresentation(160,120,true,50,0);
                    pipeline.upload(b,2,16,12,true,50_000_000);
                    int texture = pipeline.renderForPresentation(160,120,true,50,75_000_000);
                    Field field = host.view.getClass().getDeclaredField("drawnTexture"); field.setAccessible(true);
                    field.setInt(host.view,texture); assertTrue(pipeline.animating());
                } finally { a.recycle(); b.recycle(); }
                return null;
            });
            host.view.queueEvent(prepare); prepare.get(5,TimeUnit.SECONDS);
            Bitmap first=host.export(), second=host.export();
            assertTrue(first.sameAs(second)); first.recycle(); second.recycle();
            Object pipeline=field(host.view,"pipeline");
            assertEquals(2L,field(pipeline,"serial")); assertEquals(true,field(pipeline,"history"));
            assertEquals(true,field(pipeline,"animating"));
            assertEquals(25_000_000L,field(field(pipeline,"clock"),"presented"));
            assertEquals(1,host.screen.flips);
        }
    }

    @Test public void stalledCaptureTimesOutOnceAndLateWorkCannotResetRenderer() throws Exception {
        try (Host host = new Host()) {
            host.draw(160,120,0,false);
            CountDownLatch blocked = new CountDownLatch(1), resume = new CountDownLatch(1), completed = new CountDownLatch(1);
            AtomicInteger calls = new AtomicInteger();
            host.view.queueEvent(() -> {
                blocked.countDown();
                try { resume.await(5,TimeUnit.SECONDS); } catch(InterruptedException e) { Thread.currentThread().interrupt(); }
            });
            try {
                assertTrue(blocked.await(2,TimeUnit.SECONDS));
                host.scenario.onActivity(a -> {
                    assertTrue(host.view.capture(image -> { assertNull(image); calls.incrementAndGet(); completed.countDown(); }));
                    assertFalse(host.view.capture(image -> fail("Duplicate capture accepted")));
                });
                assertTrue(completed.await(3,TimeUnit.SECONDS)); assertEquals(1,calls.get());
            } finally { resume.countDown(); }
            Bitmap image = host.export(); image.recycle(); assertEquals(1,calls.get()); assertFalse(host.failed.get());
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
