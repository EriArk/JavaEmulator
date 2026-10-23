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

    @Test public void retroShadersCompileCropAndSwitchWithoutOtherEffects() {
        Bitmap padded=fixture(32,32,0), cropped=Bitmap.createBitmap(padded,0,0,32,24);
        try {
            for(int mode=1;mode<=2;mode++) {
                pipeline.setRetroShader(mode);
                pipeline.upload(padded,1,32,24,true,0);
                int texture=pipeline.renderForPresentation(96,72,true,100,1);
                assertFalse(pipeline.animating());
                assertFalse(pipeline.presentSharpened(vertices));
                try(Target a=new Target(96,72); Target b=new Target(96,72)) {
                    present(a,texture,false,0,0);
                    pipeline.upload(cropped,2,32,24,false,2);
                    texture=pipeline.renderForPresentation(96,72,false,0,3);
                    present(b,texture,false,0,0);
                    assertArrayEquals("Padding must never enter the shader",a.pixels(),b.pixels());
                    assertEquals(texture,pipeline.renderForPresentation(96,72,false,0,4));
                }
                // New geometry, rotation and new guest pixels must not reuse the cached output.
                pipeline.upload(padded,3,32,24,true,20_000_000);
                try(Target rotated=new Target(67,83)) {
                    texture=pipeline.renderForPresentation(83,67,true,100,21_000_000);
                    present(rotated,texture,false,90,0);
                    byte[] pixels=rotated.pixels();
                    int sum=0; for(int i=0;i<pixels.length;i+=4) sum+=pixels[i]&255;
                    assertTrue(sum>1000); assertFalse(pipeline.animating());
                }
            }
            pipeline.setRetroShader(0);
            pipeline.upload(cropped,3,32,24,false,0);
            pipeline.renderForPresentation(96,72,true,50,1);
            try(Target target=new Target(96,72)) {
                glBindFramebuffer(GL_FRAMEBUFFER,target.fbo); glViewport(0,0,96,72);
                assertTrue(pipeline.presentSharpened(vertices));
            }
            assertEquals(GL_NO_ERROR,glGetError());
        } finally { padded.recycle(); cropped.recycle(); }
    }

    @Test public void retroShadersBoundedGpuTiming() {
        Bitmap frame=fixture(128,160,0);
        try {
            for(int mode=1;mode<=2;mode++) {
                pipeline.setRetroShader(mode);
                long[] times=new long[16];
                for(int i=-4;i<times.length;i++) {
                    glFinish(); long start=System.nanoTime();
                    pipeline.upload(frame,i,128,160,false,start);
                    pipeline.renderForPresentation(576,720,false,0,start);
                    glFinish(); if(i>=0) times[i]=System.nanoTime()-start;
                }
                java.util.Arrays.sort(times);
                android.os.Bundle result=new android.os.Bundle();
                result.putString("stream","\nRetro mode="+mode+" 128x160 -> 576x720 upload+shader medianMs="
                        +times[8]/1e6+" maxMs="+times[15]/1e6+" (not whole-game FPS)\n");
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendStatus(0,result);
            }
        } finally { frame.recycle(); }
    }

    @Test public void retroReviewSourceExportsProductionOutput() throws Exception {
        org.junit.Assume.assumeTrue("true".equals(androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("retroShaderReview")));
        java.io.File root=new java.io.File(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getExternalFilesDir(null),"retro-review");
        Bitmap source=android.graphics.BitmapFactory.decodeFile(new java.io.File(root,"source.png").getPath());
        assertNotNull("Stage the private review source first",source);
        try {
            for(int mode=1;mode<=2;mode++) {
                pipeline.setRetroShader(mode);
                pipeline.upload(source,1,source.getWidth(),source.getHeight(),false,0);
                int texture=pipeline.renderForPresentation(384,480,false,0,1);
                try(Target target=new Target(384,480)) {
                    present(target,texture,false,0,0);
                    Bitmap image=Bitmap.createBitmap(384,480,Bitmap.Config.ARGB_8888);
                    image.copyPixelsFromBuffer(ByteBuffer.wrap(target.pixels()));
                    try(java.io.FileOutputStream output=new java.io.FileOutputStream(new java.io.File(root,"production-"+mode+".png"))) {
                        assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,output));
                    } finally { image.recycle(); }
                }
            }
        } finally { source.recycle(); }
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

    private Bitmap image(Bitmap source, int width, int height, int preset, boolean motion, long serial, long now) {
        return image(source,width,height,preset,motion,serial,now,50);
    }

    private Bitmap image(Bitmap source, int width, int height, int preset, boolean motion, long serial, long now, int sharpness) {
        ru.playsoftware.j2meloader.config.ProfileModel settings=new ru.playsoftware.j2meloader.config.ProfileModel();
        ru.playsoftware.j2meloader.config.ImagePresets.apply(settings,preset,true);
        settings.fsrSharpness=sharpness;
        pipeline.upload(source,serial,source.getWidth(),source.getHeight(),motion,serial*50_000_000);
        int texture=pipeline.renderForPresentation(width,height,settings.fsrUpscaling,settings.fsrSharpness,now);
        try(Target target=new Target(width,height)) {
            glBindFramebuffer(GL_FRAMEBUFFER,target.fbo); glViewport(0,0,width,height);
            vertices.clear(); vertices.put(new float[]{-1,-1,0,0,-1,1,0,1,1,-1,1,0,1,1,1,1}).rewind();
            pipeline.presentTexture(texture,vertices,settings.screenFilter && !settings.fsrUpscaling);
            Bitmap result=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
            result.copyPixelsFromBuffer(ByteBuffer.wrap(target.pixels())); return result;
        }
    }

    private void comparison(Bitmap source, String name, int width, int height) throws Exception {
        android.content.Context context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext();
        Bitmap sheet=Bitmap.createBitmap(width*3+32,height+64,Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas=new android.graphics.Canvas(sheet); canvas.drawColor(0xFF101417);
        android.graphics.Paint label=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        label.setColor(0xFFF5F5F5); label.setTextSize(20);
        String[] names=ru.playsoftware.j2meloader.config.ImagePresets.names(true);
        for(int mode=0;mode<3;mode++) {
            Bitmap result=image(source,width,height,mode,false,1,50_000_000);
            canvas.drawText(names[mode],8+mode*(width+8),28,label);
            canvas.drawBitmap(result,8+mode*(width+8),48,null); result.recycle();
        }
        try(java.io.FileOutputStream file=new java.io.FileOutputStream(new java.io.File(context.getExternalFilesDir(null),name+".png"))) {
            assertTrue(sheet.compress(Bitmap.CompressFormat.PNG,100,file));
        } finally { sheet.recycle(); }
    }

    @Test public void spatialPresetsSeparateExactPixelsFromSoftFiltersAndStaticMotionDoesNotBlur() throws Exception {
        Bitmap source=Bitmap.createBitmap(32,24,Bitmap.Config.ARGB_8888);
        for(int y=0;y<24;y++) for(int x=0;x<32;x++)
            source.setPixel(x,y,(x<16 ? (x+y)%2==0 : (x==20 || y==5 || y==11 || y==18))?0xFFFFFFFF:0xFF000000);
        try {
            int[] soft=new int[3];
            for(int mode=0;mode<3;mode++) {
                Bitmap result=image(source,128,96,mode,false,1,50_000_000);
                for(int y=0;y<96;y++) for(int x=0;x<128;x++) {
                    int color=result.getPixel(x,y);
                    if(mode==0) assertEquals(source.getPixel(x/4,y/4),color);
                    if(color!=0xFF000000 && color!=0xFFFFFFFF) soft[mode]++;
                }
                result.recycle();
            }
            assertEquals(0,soft[0]); assertTrue(soft[1]>0); assertTrue(soft[2]>0);
            Bitmap real=image(source,128,96,2,true,10,500_000_000);
            Bitmap intermediate=image(source,128,96,2,true,11,575_000_000);
            assertTrue("Unchanged frames must not acquire motion blur",real.sameAs(intermediate));
            real.recycle(); intermediate.recycle();
            android.os.Bundle report=new android.os.Bundle();
            report.putString("stream","\nResampled pixels / 12288: sharp="+soft[0]+" smooth="+soft[1]+" FSR="+soft[2]+"\n");
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().sendStatus(0,report);
            comparison(source,"image-presets-pixels",256,192);
        } finally { source.recycle(); }
    }

    @Test public void compareOptionalCapturedGameAtIdenticalScaleAndFrame() throws Exception {
        String name=androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("imageFixture");
        Assume.assumeNotNull(name);
        android.content.Context context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext();
        java.io.File root=context.getExternalFilesDir(null), file=new java.io.File(root,name);
        assertTrue(file.getCanonicalPath().startsWith(root.getCanonicalPath()+java.io.File.separator));
        Bitmap source=android.graphics.BitmapFactory.decodeFile(file.getPath()); assertNotNull(source);
        try {
            assertTrue(source.getWidth()<=512 && source.getHeight()<=512);
            comparison(source,"image-presets-game",source.getWidth()*3,source.getHeight()*3);
        } finally { source.recycle(); }
    }

    @Test public void exportOptionalFiveModeVisualReview() throws Exception {
        String name=androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("imageFixture");
        Assume.assumeNotNull(name);
        android.content.Context context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext();
        java.io.File root=context.getExternalFilesDir(null), file=new java.io.File(root,name);
        assertTrue(file.getCanonicalPath().startsWith(root.getCanonicalPath()+java.io.File.separator));
        Bitmap source=android.graphics.BitmapFactory.decodeFile(file.getPath()); assertNotNull(source);
        try {
            assertTrue(source.getWidth()<=512 && source.getHeight()<=512);
            int width=source.getWidth()*3, height=source.getHeight()*3;
            String[] titles={"A - Pixel sharp", "B - Smooth", "C - FSR / 0%", "D - FSR / 50%", "E - FSR / 100%"};
            String[] details={"Nearest / FSR off", "Linear / FSR off", "EASU + RCAS", "Enhanced preset", "EASU + RCAS"};
            android.graphics.Paint label=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            android.graphics.Paint pixels=new android.graphics.Paint(); pixels.setFilterBitmap(false);
            int tileWidth=Math.max(384,width), tileHeight=height+96;
            Bitmap sheet=Bitmap.createBitmap((tileWidth+16)*2+16,(tileHeight+16)*3+16,Bitmap.Config.ARGB_8888);
            android.graphics.Canvas canvas=new android.graphics.Canvas(sheet); canvas.drawColor(0xFF101417);
            Bitmap detail=Bitmap.createBitmap(816,3*304+16,Bitmap.Config.ARGB_8888);
            android.graphics.Canvas zoom=new android.graphics.Canvas(detail); zoom.drawColor(0xFF101417);
            for(int mode=0;mode<5;mode++) {
                Bitmap result=image(source,width,height,Math.min(mode,2),false,1,50_000_000,Math.max(0,(mode-2)*50));
                Bitmap tile=Bitmap.createBitmap(tileWidth,tileHeight,Bitmap.Config.ARGB_8888);
                android.graphics.Canvas panel=new android.graphics.Canvas(tile); panel.drawColor(0xFF101417);
                label.setColor(0xFFFFC766); label.setTextSize(26); panel.drawText(titles[mode],8,30,label);
                label.setColor(0xFFE5E9ED); label.setTextSize(18); panel.drawText(details[mode]+" / 3x",8,58,label);
                panel.drawBitmap(result,(tileWidth-width)/2f,80,pixels);
                canvas.drawBitmap(tile,16+(mode%2)*(tileWidth+16),16+(mode/2)*(tileHeight+16),pixels);
                writeReviewPng(root,"visual-review-"+(char)('a'+mode)+".png",tile);
                int dx=16+(mode%2)*400, dy=16+(mode/2)*304;
                label.setColor(0xFFFFC766); label.setTextSize(24); zoom.drawText(titles[mode],dx,dy+26,label);
                android.graphics.Rect crop=new android.graphics.Rect(width/4,height/2,3*width/4,3*height/4);
                float factor=Math.min(384f/crop.width(),240f/crop.height());
                zoom.drawBitmap(result,crop,new android.graphics.RectF(dx,dy+48,
                        dx+crop.width()*factor,dy+48+crop.height()*factor),pixels);
                result.recycle(); tile.recycle();
            }
            label.setColor(0xFFE5E9ED); label.setTextSize(22);
            int x=32+tileWidth,y=32+2*(tileHeight+16);
            for(String line:new String[]{"Same frozen source frame",source.getWidth()+" x "+source.getHeight()+" -> "+width+" x "+height,
                    "GPU: "+android.os.Build.MODEL, "LCD: off", "Interpolation: off", "Composed comparison", "Not five live screenshots"}) {
                y+=34; canvas.drawText(line,x,y,label);
            }
            writeReviewPng(root,"visual-review-sheet.png",sheet); sheet.recycle();
            label.setTextSize(20);
            zoom.drawText("Same crop / nearest magnification",416,670,label);
            zoom.drawText("Inspect edges, not smoothness",416,706,label);
            writeReviewPng(root,"visual-review-detail.png",detail); detail.recycle();
        } finally { source.recycle(); }
    }

    private static void writeReviewPng(java.io.File root,String name,Bitmap bitmap) throws Exception {
        try(java.io.FileOutputStream output=new java.io.FileOutputStream(new java.io.File(root,name))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,output));
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
