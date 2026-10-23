package javax.microedition.lcdui.graphics;

import android.graphics.Bitmap;
import android.opengl.*;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Random;
import javax.microedition.util.ContextHolder;
import org.junit.*;
import static org.junit.Assert.*;
import static android.opengl.GLES20.*;

/** Same-GPU comparison against a frozen pre-optimization shader, not another optimized pipeline. */
public class EasuOptimizationTest {
    private static final String VERTEX="attribute vec2 a_position; attribute vec2 a_texcoord0;"
            +"varying vec2 v_texcoord0; void main(){gl_Position=vec4(a_position,0,1);v_texcoord0=a_texcoord0;}";
    private EGLDisplay display;
    private EGLContext context;
    private EGLSurface surface;
    private int reference, optimized, flat, input, readFbo;
    private EnhancementPipeline pipeline;
    private final FloatBuffer quad=ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder()).asFloatBuffer();

    @Before public void setup() throws Exception {
        display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        assertTrue(EGL14.eglInitialize(display,new int[2],0,new int[2],0));
        EGLConfig[] config=new EGLConfig[1];
        assertTrue(EGL14.eglChooseConfig(display,new int[]{EGL14.EGL_RENDERABLE_TYPE,4,
                EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,
                EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_NONE},0,config,0,1,new int[1],0));
        context=EGL14.eglCreateContext(display,config[0],EGL14.EGL_NO_CONTEXT,
                new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
        surface=EGL14.eglCreatePbufferSurface(display,config[0],new int[]{EGL14.EGL_WIDTH,16,
                EGL14.EGL_HEIGHT,16,EGL14.EGL_NONE},0);
        assertTrue(EGL14.eglMakeCurrent(display,surface,surface,context));
        try(InputStream stream=InstrumentationRegistry.getInstrumentation().getContext().getAssets()
                .open("shaders/fsr-easu-reference.fsh")) {
            reference=program(new java.util.Scanner(stream,"UTF-8").useDelimiter("\\A").next());
        }
        String code=ContextHolder.getAssetAsString("shaders/fsr-easu.fsh");
        optimized=program(EnhancementPipeline.easuSource(code,glGetString(GL_RENDERER)));
        flat=program("#define EASU_FLAT_REGIONS\n"+code);
        pipeline=new EnhancementPipeline();
        quad.put(new float[]{-1,-1,0,0, -1,1,0,1, 1,-1,1,0, 1,1,1,1}).rewind();
        int[] id=new int[1]; glGenTextures(1,id,0); input=id[0];
        glGenFramebuffers(1,id,0); readFbo=id[0];
    }

    @After public void cleanup() {
        if(pipeline!=null) pipeline.close();
        glDeleteProgram(reference); glDeleteProgram(optimized); glDeleteProgram(flat);
        glDeleteTextures(1,new int[]{input},0); glDeleteFramebuffers(1,new int[]{readFbo},0);
        EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
        EGL14.eglDestroySurface(display,surface); EGL14.eglDestroyContext(display,context); EGL14.eglTerminate(display);
    }

    private static int shader(int type,String source) {
        int id=glCreateShader(type); glShaderSource(id,source); glCompileShader(id);
        int[] status=new int[1]; glGetShaderiv(id,GL_COMPILE_STATUS,status,0);
        assertEquals(glGetShaderInfoLog(id),1,status[0]); return id;
    }

    private static int program(String source) {
        int v=shader(GL_VERTEX_SHADER,VERTEX), f=shader(GL_FRAGMENT_SHADER,source), p=glCreateProgram();
        glAttachShader(p,v); glAttachShader(p,f);
        glBindAttribLocation(p,0,"a_position"); glBindAttribLocation(p,1,"a_texcoord0"); glLinkProgram(p);
        int[] status=new int[1]; glGetProgramiv(p,GL_LINK_STATUS,status,0);
        glDeleteShader(v); glDeleteShader(f); assertEquals(glGetProgramInfoLog(p),1,status[0]); return p;
    }

    private static void texture(int id) {
        glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D,id);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
    }

    private static final class Target implements AutoCloseable {
        final int texture,fbo,width,height;
        Target(int width,int height) {
            this.width=width; this.height=height;
            int[] id=new int[1]; glGenTextures(1,id,0); texture=id[0]; texture(texture);
            glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,width,height,0,GL_RGBA,GL_UNSIGNED_BYTE,null);
            glGenFramebuffers(1,id,0); fbo=id[0]; glBindFramebuffer(GL_FRAMEBUFFER,fbo);
            glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,texture,0);
            assertEquals(GL_FRAMEBUFFER_COMPLETE,glCheckFramebufferStatus(GL_FRAMEBUFFER));
        }
        public void close() { glDeleteTextures(1,new int[]{texture},0); glDeleteFramebuffers(1,new int[]{fbo},0); }
    }

    private void draw(int program,Target out,int width,int height,int tw,int th) {
        glBindFramebuffer(GL_FRAMEBUFFER,out.fbo); glViewport(0,0,out.width,out.height);
        glUseProgram(program); glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D,input);
        glUniform1i(glGetUniformLocation(program,"sampler0"),0);
        glUniform2f(glGetUniformLocation(program,"sourceSize"),width,height);
        glUniform2f(glGetUniformLocation(program,"textureSize"),tw,th);
        quad.position(0); glVertexAttribPointer(0,2,GL_FLOAT,false,16,quad); glEnableVertexAttribArray(0);
        quad.position(2); glVertexAttribPointer(1,2,GL_FLOAT,false,16,quad); glEnableVertexAttribArray(1);
        glDrawArrays(GL_TRIANGLE_STRIP,0,4);
    }

    private byte[] pixels(int texture,int w,int h) {
        glBindFramebuffer(GL_FRAMEBUFFER,readFbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,texture,0);
        assertEquals(GL_FRAMEBUFFER_COMPLETE,glCheckFramebufferStatus(GL_FRAMEBUFFER));
        ByteBuffer data=ByteBuffer.allocateDirect(w*h*4);
        glReadPixels(0,0,w,h,GL_RGBA,GL_UNSIGNED_BYTE,data);
        assertEquals(GL_NO_ERROR,glGetError()); byte[] bytes=new byte[data.capacity()]; data.get(bytes); return bytes;
    }

    private void compare(Bitmap frame,int width,int height,int outW,int outH) {
        texture(input); GLUtils.texImage2D(GL_TEXTURE_2D,0,frame,0);
        try(Target out=new Target(outW,outH)) {
            draw(reference,out,width,height,frame.getWidth(),frame.getHeight());
            byte[] expected=pixels(out.texture,outW,outH);
            draw(flat,out,width,height,frame.getWidth(),frame.getHeight());
            equivalent(expected,pixels(out.texture,outW,outH),"Forced flat shortcut");
            pipeline.resetHistory();
            int actual=pipeline.render(frame,1,width,height,outW,outH,true,false,0,0);
            equivalent(expected,pixels(actual,outW,outH),width+"x"+height+" -> "+outW+"x"+outH);
        }
    }

    private void equivalent(byte[] expected,byte[] result,String label) {
        int maximum=0;
        for(int i=0;i<result.length;i++) maximum=Math.max(maximum,Math.abs((result[i]&255)-(expected[i]&255)));
        assertTrue(label+": EASU channel error="+maximum,maximum<=1);
    }

    @Test public void softwareRenderersRetainBranchlessEvaluation() {
        for(String renderer:new String[]{null,"","Google SwiftShader","ANGLE (Google, Vulkan SwiftShader)",
                "llvmpipe (LLVM 15.0)","softpipe","Software Rasterizer"})
            assertEquals("shader",EnhancementPipeline.easuSource("shader",renderer));
        for(String renderer:new String[]{"Adreno (TM) 620","Mali-G78","ANGLE (Qualcomm Adreno)"})
            assertTrue(EnhancementPipeline.easuSource("shader",renderer).startsWith("#define EASU_FLAT_REGIONS\n"));
    }

    private Bitmap pattern(int width,int height,String name) {
        Bitmap frame=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        Random random=new Random(41023);
        for(int y=0;y<height;y++) for(int x=0;x<width;x++) {
            int color;
            switch(name) {
                case "solid": color=0x193B75; break;
                case "tiles": color=(x/16+y/16)%2==0?0xCC8040:0x2050C0; break;
                case "text": color=(y%20<7 && x%10<3)?0xFFFFFF:0x102030; break;
                case "gradient": color=(x*255/Math.max(1,width-1))<<16|(y*255/Math.max(1,height-1))<<8|0x80; break;
                case "one-bit": color=0x808080+((x+y)%2); break;
                case "equal-luma": color=(x+y)%2==0?0xFF0000:0x0000FF; break;
                case "mixed": color=x<width/2?((x/16+y/16)%2==0?0xCC8040:0x2050C0)
                        :(x*255/width)<<16|(y*255/height)<<8|0x80; break;
                default: color=random.nextInt(1<<24); break;
            }
            frame.setPixel(x,y,0xFF000000|color);
        }
        return frame;
    }

    @Test public void structuredAndDetailedImagesMatchFrozenReference() {
        for(String name:new String[]{"solid","tiles","text","gradient","noise","one-bit","equal-luma"}) {
            Bitmap frame=pattern(37,29,name);
            for(int[] size:new int[][]{{38,30},{79,61},{111,87},{37,87},{111,29}})
                compare(frame,37,29,size[0],size[1]);
            frame.recycle();
        }
    }

    @Test public void flatCentersWithContrastingOuterTapsAndPaddingMatchReference() {
        Bitmap frame=pattern(32,32,"noise");
        for(int y=0;y<23;y+=4) for(int x=0;x<27;x+=4)
            for(int dy=0;dy<2;dy++) for(int dx=0;dx<2;dx++) frame.setPixel(x+dx,y+dy,0xFF123456);
        compare(frame,29,25,87,75); compare(frame,29,25,71,63);
        frame.recycle();
    }

    @Test public void narrowViewportsAndSolidExtremesMatchReference() {
        for(int[] size:new int[][]{{1,1},{1,17},{19,1},{2,2}}) {
            Bitmap frame=pattern(32,32,"noise");
            compare(frame,size[0],size[1],size[0]*3+1,size[1]*3+1); frame.recycle();
        }
        for(int color:new int[]{0xFF000000,0xFFFFFFFF,0xFF010101,0xFFFEFEFE,0xFFFF0000,0xFF00FF00,0xFF0000FF}) {
            Bitmap frame=Bitmap.createBitmap(17,13,Bitmap.Config.ARGB_8888); frame.eraseColor(color);
            compare(frame,17,13,53,41); frame.recycle();
        }
    }

    @Test public void phoneResolutionsMatchReference() {
        for(int[] size:new int[][]{{176,208},{240,320},{320,240},{360,640},{480,800}}) {
            Bitmap frame=pattern(size[0],size[1],"mixed");
            compare(frame,size[0],size[1],720,960); frame.recycle();
        }
    }

    @Test public void reportPairedEasuCostsIncludingDetailedWorstCase() {
        try(Target output=new Target(720,960)) {
            for(String name:new String[]{"solid","tiles","text","gradient","noise"}) {
                Bitmap frame=pattern(240,320,name);
                texture(input); GLUtils.texImage2D(GL_TEXTURE_2D,0,frame,0); frame.recycle();
                long[][] samples=new long[2][40];
                for(int i=0;i<50;i++) for(int order=0;order<2;order++) {
                    int path=(i+order)%2; glFinish(); long start=System.nanoTime();
                    draw(path==0?reference:optimized,output,240,320,240,320); glFinish();
                    if(i>=10) samples[path][i-10]=System.nanoTime()-start;
                }
                long[] differences=new long[40]; int faster=0;
                for(int i=0;i<40;i++) { differences[i]=samples[0][i]-samples[1][i]; if(differences[i]>0) faster++; }
                Arrays.sort(differences); Arrays.sort(samples[0]); Arrays.sort(samples[1]);
                android.os.Bundle result=new android.os.Bundle();
                result.putString("stream","\nEASU "+name+" reference medianMs="+samples[0][20]/1e6
                        +" p95Ms="+samples[0][38]/1e6+" optimized medianMs="+samples[1][20]/1e6
                        +" p95Ms="+samples[1][38]/1e6+" saved medianMs="+differences[20]/1e6+" fasterPairs="+faster+"/40\n");
                InstrumentationRegistry.getInstrumentation().sendStatus(0,result);
                assertEquals(GL_NO_ERROR,glGetError());
            }
        }
    }
}
