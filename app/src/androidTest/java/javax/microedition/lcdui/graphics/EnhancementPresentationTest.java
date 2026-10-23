package javax.microedition.lcdui.graphics;

import android.graphics.Bitmap;
import android.opengl.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import org.junit.*;
import static org.junit.Assert.*;
import static android.opengl.GLES20.*;

/** Compare the fused final draw to the retained texture-based RCAS path on the same GPU. */
public class EnhancementPresentationTest {
    private EGLDisplay display;
    private EGLContext context;
    private EGLSurface surface;
    private EnhancementPipeline pipeline;
    private ShaderProgram copy;
    private final FloatBuffer vertices=ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder()).asFloatBuffer();

    @Before public void setup() {
        display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        int[] version=new int[2]; assertTrue(EGL14.eglInitialize(display,version,0,version,1));
        EGLConfig[] config=new EGLConfig[1]; int[] count=new int[1];
        assertTrue(EGL14.eglChooseConfig(display,new int[]{EGL14.EGL_RENDERABLE_TYPE,4,
                EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,
                EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_NONE},0,config,0,1,count,0));
        context=EGL14.eglCreateContext(display,config[0],EGL14.EGL_NO_CONTEXT,
                new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
        surface=EGL14.eglCreatePbufferSurface(display,config[0],new int[]{EGL14.EGL_WIDTH,16,
                EGL14.EGL_HEIGHT,16,EGL14.EGL_NONE},0);
        assertTrue(EGL14.eglMakeCurrent(display,surface,surface,context));
        pipeline=new EnhancementPipeline(); copy=new ShaderProgram(null);
    }
    @After public void cleanup() {
        if(pipeline!=null) pipeline.close();
        EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
        EGL14.eglDestroySurface(display,surface); EGL14.eglDestroyContext(display,context); EGL14.eglTerminate(display);
    }

    private static final class Target implements AutoCloseable {
        final int texture,fbo,width,height;
        Target(int width,int height) {
            this.width=width; this.height=height; int[] id=new int[1];
            glGenTextures(1,id,0); texture=id[0]; glBindTexture(GL_TEXTURE_2D,texture);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
            glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,width,height,0,GL_RGBA,GL_UNSIGNED_BYTE,null);
            glGenFramebuffers(1,id,0); fbo=id[0]; glBindFramebuffer(GL_FRAMEBUFFER,fbo);
            glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,texture,0);
            assertEquals(GL_FRAMEBUFFER_COMPLETE,glCheckFramebufferStatus(GL_FRAMEBUFFER));
        }
        byte[] pixels() {
            glBindFramebuffer(GL_FRAMEBUFFER,fbo);
            ByteBuffer data=ByteBuffer.allocateDirect(width*height*4);
            glReadPixels(0,0,width,height,GL_RGBA,GL_UNSIGNED_BYTE,data);
            byte[] pixels=new byte[data.capacity()]; data.get(pixels); return pixels;
        }
        public void close() { glDeleteFramebuffers(1,new int[]{fbo},0); glDeleteTextures(1,new int[]{texture},0); }
    }

    private Bitmap fixture(int width,int height,int shift) {
        Bitmap image=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        for(int y=0;y<height;y++) for(int x=0;x<width;x++) {
            int xx=x+shift;
            int color=y>=24?0xFFFF00FF:0xFF000000|((xx*37+y*3)&255)<<16|((xx*7+y*19)&255)<<8|((xx^y)&1)*255;
            image.setPixel(x,y,color);
        }
        return image;
    }

    private void present(Target target,int texture,boolean fused,int angle,int pad) {
        glBindFramebuffer(GL_FRAMEBUFFER,target.fbo); glViewport(0,0,target.width,target.height);
        glClearColor(0.25f,0,0.25f,1); glClear(GL_COLOR_BUFFER_BIT);
        vertices.clear();
        float left=-1+2f*pad/target.width, right=1-2f*pad/target.width;
        float bottom=-1+2f*pad/target.height, top=1-2f*pad/target.height;
        for(int i=0;i<4;i++) {
            float x=i<2?0:1, y=i%2;
            vertices.put(x==0?left:right).put(y==0?bottom:top)
                    .put(ScreenRotation.sourceX(x,y,angle)).put(ScreenRotation.sourceY(x,y,angle));
        }
        copy.use(); glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D,texture);
        glUniform1i(copy.uTextureUnit,0); copy.loadVbo(vertices,target.width,target.height);
        if(!fused || !pipeline.presentSharpened(vertices)) glDrawArrays(GL_TRIANGLE_STRIP,0,4);
        assertEquals(GL_NO_ERROR,glGetError());
    }

    private void compare(int width,int height,int strength,int rotation,boolean motion) {
        int pad=3;
        try(Target reference=new Target((rotation%180==0?width:height)+2*pad,(rotation%180==0?height:width)+2*pad);
            Target fused=new Target(reference.width,reference.height)) {
            int texture=pipeline.renderUploaded(width,height,true,strength,66_666_666);
            present(reference,texture,false,rotation,pad);
            texture=pipeline.renderForPresentation(width,height,true,strength,66_666_666);
            present(fused,texture,true,rotation,pad);
            byte[] a=reference.pixels(), b=fused.pixels(); int maximum=0;
            for(int i=0;i<a.length;i++) maximum=Math.max(maximum,Math.abs((a[i]&255)-(b[i]&255)));
            assertTrue("Max channel difference="+maximum+" at "+width+"x"+height+", sharpness="+strength
                    +", rotation="+rotation+", motion="+motion,maximum<=1);
            texture=pipeline.renderForPresentation(width,height,true,strength,66_666_666);
            present(fused,texture,true,rotation,pad);
            assertArrayEquals("Repeat/cached presentation must be stable",b,fused.pixels());
        }
    }

    @Test public void fusedOutputMatchesReferenceAcrossScalesRotationsAndStrengths() {
        Bitmap frame=fixture(32,32,0);
        pipeline.upload(frame,1,32,24,false,0);
        for(int[] size:new int[][]{{32,24},{79,61},{96,72},{16,12}})
            for(int strength:new int[]{0,50,100}) for(int rotation:new int[]{0,90,180,270})
                compare(size[0],size[1],strength,rotation,false);
        frame.recycle();
    }

    @Test public void intermediateFramesAndRepeatedPresentationMatchReference() {
        Bitmap a=fixture(32,32,0), b=fixture(32,32,2);
        pipeline.upload(a,1,32,24,true,0); pipeline.renderForPresentation(79,61,true,50,0);
        pipeline.upload(b,2,32,24,true,50_000_000);
        for(int rotation:new int[]{0,90,180,270}) compare(79,61,50,rotation,true);
        a.recycle(); b.recycle();
    }

    @Test public void fusedPathNeverAllocatesSharpenedTargetAndResetDisarmsIt() throws Exception {
        Bitmap frame=fixture(32,32,0);
        pipeline.upload(frame,1,32,24,false,0);
        for(int size:new int[]{96,79,32}) {
            int texture=pipeline.renderForPresentation(size,72,true,50,0);
            try(Target output=new Target(size,72)) { present(output,texture,true,0,0); }
        }
        java.lang.reflect.Field field=EnhancementPipeline.class.getDeclaredField("sharpened"); field.setAccessible(true);
        Object target=field.get(pipeline); java.lang.reflect.Field texture=target.getClass().getDeclaredField("texture");
        texture.setAccessible(true); assertEquals(0,texture.getInt(target));
        pipeline.resetHistory(); assertFalse(pipeline.presentSharpened(vertices));
        frame.recycle();
    }

    @Test public void reportFullPresentationCost() {
        Bitmap a=Bitmap.createBitmap(240,320,Bitmap.Config.ARGB_8888), b=Bitmap.createBitmap(240,320,Bitmap.Config.ARGB_8888);
        for(int y=0;y<320;y++) for(int x=0;x<240;x++) {
            int color=0xFF000000|((x/5+y/7)%2==0?0xCC8040:0x2050C0);
            a.setPixel(x,y,color); b.setPixel((x+2)%240,y,color);
        }
        try(Target output=new Target(720,960)) {
            EnhancementPipeline primary=pipeline;
            for(boolean motion:new boolean[]{false,true}) {
                EnhancementPipeline[] paths={new EnhancementPipeline(),new EnhancementPipeline()};
                long[][] samples=new long[2][40];
                try {
                    // Alternate AB/BA order so both paths see the same scene and similar clock/thermal conditions.
                    for(int i=0;i<50;i++) for(int order=0;order<2;order++) {
                        int path=(i+order)%2; boolean fused=path==1; pipeline=paths[path];
                        glFinish(); long start=System.nanoTime(), now=i*50_000_000L;
                        pipeline.upload(i%2==0?a:b,i,240,320,motion,now);
                        int texture=fused?pipeline.renderForPresentation(720,960,true,50,now+10_000_000)
                                :pipeline.renderUploaded(720,960,true,50,now+10_000_000);
                        present(output,texture,fused,0,0); glFinish();
                        if(i>=10) samples[path][i-10]=System.nanoTime()-start;
                    }
                    long[] differences=new long[40]; int faster=0;
                    for(int i=0;i<40;i++) { differences[i]=samples[0][i]-samples[1][i]; if(differences[i]>0) faster++; }
                    java.util.Arrays.sort(differences);
                    for(int path=0;path<2;path++) java.util.Arrays.sort(samples[path]);
                    android.os.Bundle result=new android.os.Bundle();
                    result.putString("stream","\nPaired presentation motion="+motion
                            +" reference medianMs="+samples[0][20]/1e6+" p95Ms="+samples[0][38]/1e6
                            +" fused medianMs="+samples[1][20]/1e6+" p95Ms="+samples[1][38]/1e6
                            +" saved medianMs="+differences[20]/1e6+" fasterPairs="+faster+"/40\n");
                    androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendStatus(0,result);
                } finally { pipeline=primary; for(EnhancementPipeline path:paths) path.close(); }
            }
        }
        a.recycle(); b.recycle();
    }

    @Test public void disabledEffectsAndNewUploadDisarmDeferredSharpening() {
        Bitmap frame=fixture(32,32,0);
        pipeline.upload(frame,1,32,24,false,0);
        pipeline.renderForPresentation(96,72,true,50,0);
        pipeline.renderForPresentation(96,72,false,50,0);
        assertFalse(pipeline.presentSharpened(vertices));
        pipeline.renderForPresentation(96,72,true,50,0);
        pipeline.renderForPresentation(96,72,true,0,0);
        assertFalse(pipeline.presentSharpened(vertices));
        pipeline.renderForPresentation(96,72,true,50,0);
        pipeline.upload(frame,2,32,24,false,50_000_000);
        assertFalse(pipeline.presentSharpened(vertices));
        frame.recycle();
    }
}
