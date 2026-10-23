package javax.microedition.lcdui.graphics;

import android.graphics.Bitmap;
import android.opengl.*;
import java.nio.ByteBuffer;
import org.junit.*;
import static org.junit.Assert.*;
import static android.opengl.GLES20.*;

public class EnhancementPipelineTest {
    private EGLDisplay display;
    private EGLContext context;
    private EGLSurface surface;
    private EnhancementPipeline pipeline;
    @Before public void setup() {
        display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        int[] version=new int[2]; assertTrue(EGL14.eglInitialize(display,version,0,version,1));
        EGLConfig[] config=new EGLConfig[1]; int[] n=new int[1];
        assertTrue(EGL14.eglChooseConfig(display,new int[]{EGL14.EGL_RENDERABLE_TYPE,4,
                EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,EGL14.EGL_RED_SIZE,8,
                EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_NONE},0,config,0,1,n,0));
        context=EGL14.eglCreateContext(display,config[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
        surface=EGL14.eglCreatePbufferSurface(display,config[0],new int[]{EGL14.EGL_WIDTH,16,EGL14.EGL_HEIGHT,16,EGL14.EGL_NONE},0);
        assertTrue(EGL14.eglMakeCurrent(display,surface,surface,context)); pipeline=new EnhancementPipeline();
    }
    @After public void cleanup() {
        if(pipeline!=null) pipeline.close();
        EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
        EGL14.eglDestroySurface(display,surface); EGL14.eglDestroyContext(display,context); EGL14.eglTerminate(display);
    }
    private Bitmap solid(int w,int h,int color) { Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888); b.eraseColor(color); return b; }
    private int pixel(int texture,int x,int y) {
        int[] f=new int[1]; glGenFramebuffers(1,f,0); glBindFramebuffer(GL_FRAMEBUFFER,f[0]);
        glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,texture,0);
        assertEquals(GL_FRAMEBUFFER_COMPLETE,glCheckFramebufferStatus(GL_FRAMEBUFFER));
        ByteBuffer p=ByteBuffer.allocateDirect(4); glReadPixels(x,y,1,1,GL_RGBA,GL_UNSIGNED_BYTE,p);
        glBindFramebuffer(GL_FRAMEBUFFER,0); glDeleteFramebuffers(1,f,0); assertEquals(GL_NO_ERROR,glGetError());
        return 0xFF000000|((p.get(0)&255)<<16)|((p.get(1)&255)<<8)|(p.get(2)&255);
    }
    @Test public void fsrPreservesSolidColorsIncludingBlackAndWhite() {
        for(int c:new int[]{0xFF000000,0xFFFFFFFF,0xFF123456,0xFF00FF00}) {
            Bitmap b=solid(32,24,c);
            int t=pipeline.render(b,c,32,24,96,72,true,false,50,0);
            assertEquals(c,pixel(t,48,36)); assertEquals(c,pixel(t,0,0)); b.recycle();
        }
    }
    @Test public void viewportExcludesPaddingAndRetainsCornerOrientation() {
        Bitmap b=solid(32,32,0xFFFF00FF);
        for(int y=0;y<24;y++) for(int x=0;x<32;x++) b.setPixel(x,y,y<12?(x<16?0xFFFF0000:0xFF00FF00):(x<16?0xFF0000FF:0xFFFFFFFF));
        int t=pipeline.render(b,1,32,24,96,72,true,false,100,0);
        assertEquals(0xFFFF0000,pixel(t,3,3)); assertEquals(0xFF00FF00,pixel(t,92,3));
        assertEquals(0xFF0000FF,pixel(t,3,68)); assertEquals(0xFFFFFFFF,pixel(t,92,68)); b.recycle();
    }
    @Test public void motionGeneratesMovedEdgeAndStopsAtCurrentFrame() {
        Bitmap a=solid(32,24,0xFF000000), b=solid(32,24,0xFF000000);
        for(int y=0;y<24;y++) for(int x=10;x<14;x++) { a.setPixel(x,y,0xFFFFFFFF); b.setPixel(x+2,y,0xFFFFFFFF); }
        pipeline.render(a,1,32,24,32,24,false,true,50,0);
        pipeline.render(b,2,32,24,32,24,false,true,50,50_000_000);
        int t=pipeline.render(b,2,32,24,32,24,false,true,50,66_666_666);
        assertTrue(pipeline.animating());
        assertEquals(0xFFFFFFFF,pixel(t,11,12)); assertEquals(0xFF000000,pixel(t,15,12));
        t=pipeline.render(b,2,32,24,32,24,false,true,50,100_000_000);
        assertFalse(pipeline.animating()); assertEquals(0xFF000000,pixel(t,11,12)); assertEquals(0xFFFFFFFF,pixel(t,15,12));
        a.recycle(); b.recycle();
    }
    @Test public void sceneCutAndResetDoNotBlendOldScene() {
        Bitmap a=solid(32,24,0xFF000000), b=solid(32,24,0xFFFFFFFF);
        pipeline.render(a,1,32,24,32,24,false,true,50,0);
        int t=pipeline.render(b,2,32,24,32,24,false,true,50,50_000_000);
        assertEquals(0xFFFFFFFF,pixel(t,12,12));
        pipeline.resetHistory(); t=pipeline.render(a,3,32,24,96,72,true,true,50,60_000_000);
        assertFalse(pipeline.animating()); assertEquals(0xFF000000,pixel(t,48,36)); a.recycle(); b.recycle();
    }

    // Isolate resolve safeguards from the estimator: a neighboring moving block can vote for this vector.
    private void replaceMotionField(int dx,int dy) throws Exception {
        java.lang.reflect.Field f=EnhancementPipeline.class.getDeclaredField("flow"); f.setAccessible(true);
        Object target=f.get(pipeline);
        java.lang.reflect.Field texture=target.getClass().getDeclaredField("texture"); texture.setAccessible(true);
        Bitmap field=solid(4,3,0xFF0000FF|(Math.round((dx+4)*255f/8)<<16)|(Math.round((dy+4)*255f/8)<<8));
        glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D,texture.getInt(target));
        GLUtils.texSubImage2D(GL_TEXTURE_2D,0,0,0,field); field.recycle();
    }

    @Test public void movingBackgroundDoesNotEraseAnUnchangedHudPixel() throws Exception {
        Bitmap a=solid(32,24,0xFF000000), b=solid(32,24,0xFF000000);
        for(int y=0;y<24;y++) for(int x=10;x<14;x++) {
            a.setPixel(x,y,0xFFFFFFFF); b.setPixel(x+2,y,0xFFFFFFFF);
        }
        a.setPixel(13,8,0xFFFF0000); b.setPixel(13,8,0xFFFF0000);
        pipeline.render(a,1,32,24,32,24,false,true,50,0);
        pipeline.render(b,2,32,24,32,24,false,true,50,50_000_000);
        replaceMotionField(-2,0);
        int t=pipeline.render(b,2,32,24,32,24,false,true,50,66_000_000);
        assertEquals("A static HUD detail must not be warped with its block",0xFFFF0000,pixel(t,13,8));
        a.recycle(); b.recycle();
    }

    @Test public void outOfBoundsMotionKeepsCurrentEdgeInsteadOfSmearingBorder() throws Exception {
        for(int[] edge:new int[][]{{0,12,-4,0},{31,12,4,0},{16,0,0,-4},{16,23,0,4}}) {
            Bitmap a=solid(32,24,0xFF0000FF), b=solid(32,24,0xFF0000FF);
            b.setPixel(edge[0],edge[1],0xFFFF0000); pipeline.resetHistory();
            pipeline.render(a,1,32,24,32,24,false,true,50,0);
            pipeline.render(b,2,32,24,32,24,false,true,50,50_000_000);
            replaceMotionField(edge[2],edge[3]);
            int t=pipeline.render(b,2,32,24,32,24,false,true,50,66_000_000);
            assertEquals(0xFFFF0000,pixel(t,edge[0],edge[1])); a.recycle(); b.recycle();
        }
    }

    @Test public void disagreeingColorChannelsDoNotCreateAGhostBlend() throws Exception {
        Bitmap a=solid(32,24,0xFF000000), b=solid(32,24,0xFF000000);
        a.setPixel(12,8,0xFF220000); b.setPixel(13,8,0xFFFF0000);
        pipeline.render(a,1,32,24,32,24,false,true,50,0);
        pipeline.render(b,2,32,24,32,24,false,true,50,50_000_000);
        replaceMotionField(-2,0);
        int t=pipeline.render(b,2,32,24,32,24,false,true,50,66_666_666);
        assertEquals(0xFFFF0000,pixel(t,13,8)); a.recycle(); b.recycle();
    }

    @Test public void motionWorksInBothAxesAndDirections() {
        for(boolean vertical:new boolean[]{false,true}) for(int shift:new int[]{-2,2}) {
            Bitmap a=solid(32,24,0xFF000000), b=solid(32,24,0xFF000000);
            for(int y=0;y<24;y++) for(int x=0;x<32;x++) {
                int p=vertical?y:x;
                if(p>=10 && p<14) a.setPixel(x,y,0xFFFFFFFF);
                if(p>=10+shift && p<14+shift) b.setPixel(x,y,0xFFFFFFFF);
            }
            pipeline.resetHistory();
            pipeline.render(a,1,32,24,32,24,false,true,50,0);
            pipeline.render(b,2,32,24,32,24,false,true,50,50_000_000);
            int t=pipeline.render(b,2,32,24,32,24,false,true,50,66_666_666);
            int white=shift>0?11:12, black=shift>0?15:8;
            String direction="vertical="+vertical+", shift="+shift;
            assertEquals(direction,0xFFFFFFFF,pixel(t,vertical?12:white,vertical?white:12));
            assertEquals(direction,0xFF000000,pixel(t,vertical?12:black,vertical?black:12));
            a.recycle(); b.recycle();
        }
    }

    @Test public void nativeSharpeningHandlesPaddedInputWithoutUpscaling() throws Exception {
        Bitmap b=solid(32,32,0xFFFF00FF);
        for(int y=0;y<24;y++) for(int x=0;x<32;x++) b.setPixel(x,y,0xFF123456);
        int t=pipeline.render(b,1,32,24,32,24,true,false,100,0);
        assertEquals(0xFF123456,pixel(t,31,23)); assertEquals(0xFF123456,pixel(t,0,0));
        java.lang.reflect.Field easu=EnhancementPipeline.class.getDeclaredField("easu"); easu.setAccessible(true);
        assertNull(easu.get(pipeline)); b.recycle();
    }

    @Test public void fastStreamsDoNotAllocateMotionSearchResources() throws Exception {
        Bitmap b=solid(32,24,0xFF123456);
        pipeline.render(b,1,32,24,96,72,true,true,50,0);
        pipeline.render(b,2,32,24,96,72,true,true,50,16_000_000);
        java.lang.reflect.Field estimator=EnhancementPipeline.class.getDeclaredField("estimate");
        estimator.setAccessible(true);
        assertNull("No motion search for a frame that will not be interpolated",estimator.get(pipeline));
        assertFalse(pipeline.animating()); b.recycle();
    }

    @Test public void zeroSharpnessAndNativeSizeSkipUnnecessaryFsrPasses() throws Exception {
        Bitmap b=solid(32,32,0xFFFF00FF);
        for(int y=0;y<24;y++) for(int x=0;x<32;x++) b.setPixel(x,y,((x+y)%2==0)?0xFFFF0000:0xFF00FF00);
        int t=pipeline.render(b,1,32,24,32,24,true,false,0,0);
        for(int y=0;y<24;y++) for(int x=0;x<32;x++) assertEquals(b.getPixel(x,y),pixel(t,x,y));
        for(String name:new String[]{"easu","rcas"}) {
            java.lang.reflect.Field pass=EnhancementPipeline.class.getDeclaredField(name); pass.setAccessible(true);
            assertNull("Identity output needs no "+name,pass.get(pipeline));
        }
        t=pipeline.render(b,1,32,24,96,72,true,false,0,0);
        java.lang.reflect.Field rcas=EnhancementPipeline.class.getDeclaredField("rcas"); rcas.setAccessible(true);
        assertNull("Zero sharpness needs no RCAS pass",rcas.get(pipeline));
        assertNotEquals(0xFFFF00FF,pixel(t,95,71)); b.recycle();
    }
    @Test public void resizingAndTogglesRebuildResources() {
        for(int size:new int[]{32,64,16,48}) {
            Bitmap b=solid(size,size,0xFF00FF00);
            for(boolean motion:new boolean[]{true,false}) {
                int t=pipeline.render(b,size,size,size,size*2,size*2,true,motion,50,0);
                assertEquals(0xFF00FF00,pixel(t,size,size));
            }
            b.recycle();
        }
    }
    @Test public void oversizedTargetsFailBeforeAllocation() {
        Bitmap b=solid(32,32,0xFF000000);
        try { pipeline.render(b,1,32,32,8192,8192,true,false,50,0); fail(); }
        catch(IllegalArgumentException expected) { assertEquals(GL_NO_ERROR,glGetError()); }
        finally { b.recycle(); }
    }

    @Test public void unchangedFramesCacheAndMinificationStillCropsPadding() {
        Bitmap b=solid(32,32,0xFFFF00FF);
        for(int y=0;y<24;y++) for(int x=0;x<32;x++) b.setPixel(x,y,0xFF00FF00);
        int t=pipeline.render(b,1,32,24,16,12,true,false,0,0);
        assertEquals(0xFF00FF00,pixel(t,31,23));
        assertEquals(t,pipeline.render(b,1,32,24,16,12,true,false,0,100_000_000));
        t=pipeline.render(b,1,32,24,79,61,true,false,100,200_000_000);
        assertEquals(0xFF00FF00,pixel(t,78,60)); b.recycle();
    }

    @Test public void reportBoundedGpuCostAtHandheldResolution() {
        Bitmap a=solid(240,320,0xFF123456), b=solid(240,320,0xFF123456);
        for(int y=0;y<320;y++) for(int x=0;x<240;x++) {
            int c=0xFF000000|(((x/5+y/7)%2==0)?0xCC8040:0x2050C0);
            a.setPixel(x,y,c); b.setPixel((x+2)%240,y,c);
        }
        long serial=0;
        for(int mode=0;mode<4;mode++) {
            pipeline.resetHistory(); long[] costs=new long[30];
            for(int i=0;i<35;i++) {
                glFinish(); long start=System.nanoTime();
                pipeline.render(i%2==0?a:b,++serial,240,320,720,960,(mode&1)!=0,(mode&2)!=0,50,serial*50_000_000L);
                glFinish(); if(i>=5) costs[i-5]=System.nanoTime()-start;
            }
            java.util.Arrays.sort(costs);
            android.os.Bundle report=new android.os.Bundle();
            report.putString("stream", "\nGPU mode="+mode+" medianMs="+costs[15]/1e6+" p95Ms="+costs[28]/1e6+"\n");
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendStatus(0,report);
        }
        a.recycle(); b.recycle();
    }

    @Test public void reportStaticFastAndIntermediateFrameCosts() {
        Bitmap a=solid(240,320,0xFF123456), b=solid(240,320,0xFF654321);
        long serial=0;
        for(int workload=0;workload<4;workload++) {
            pipeline.resetHistory(); long[] costs=new long[30];
            for(int i=0;i<35;i++) {
                long interval=workload==1?16_000_000L:50_000_000L;
                long now=++serial*interval;
                if(workload==2) pipeline.render(i%2==0?a:b,serial,240,320,720,960,true,true,50,now);
                glFinish(); long start=System.nanoTime();
                pipeline.render(workload==0?a:(i%2==0?a:b),serial,240,320,720,960,
                        true,true,workload==3?0:50,now+(workload==2?16_000_000:0));
                glFinish(); if(i>=5) costs[i-5]=System.nanoTime()-start;
            }
            java.util.Arrays.sort(costs);
            android.os.Bundle report=new android.os.Bundle();
            report.putString("stream","\nGPU workload="+new String[]{"static","60Hz","intermediate","zero-sharpness"}[workload]
                    +" medianMs="+costs[15]/1e6+" p95Ms="+costs[28]/1e6+"\n");
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendStatus(0,report);
        }
        a.recycle(); b.recycle();
    }
}
