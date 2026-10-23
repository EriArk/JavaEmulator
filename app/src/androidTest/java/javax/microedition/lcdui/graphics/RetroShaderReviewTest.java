package javax.microedition.lcdui.graphics;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.opengl.*;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import org.junit.*;
import static android.opengl.GLES30.*;
import static org.junit.Assert.*;

/** Opt-in local shader audition. No upstream shaders or game data are bundled in the APK. */
public class RetroShaderReviewTest {
    private EGLDisplay display;
    private EGLContext context;
    private EGLSurface surface;
    private File root;
    private final Set<Integer> rgbOnly=new HashSet<>();
    private final FloatBuffer positions=buffer(-1,-1,-1,1,1,-1,1,1);
    private final FloatBuffer coordinates=buffer(0,0,0,1,1,0,1,1);
    private static FloatBuffer buffer(float... values) {
        FloatBuffer result=ByteBuffer.allocateDirect(values.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        result.put(values).rewind(); return result;
    }

    @Before public void setup() throws Exception {
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("retroShaderReview")));
        android.content.Context app=InstrumentationRegistry.getInstrumentation().getTargetContext();
        boolean privateFiles="true".equals(InstrumentationRegistry.getArguments().getString("reviewPrivate"));
        root=new File(privateFiles?app.getFilesDir():app.getExternalFilesDir(null),"retro-review");
        assertTrue(root.isDirectory());
        display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        int[] version=new int[2]; assertTrue(EGL14.eglInitialize(display,version,0,version,1));
        EGLConfig[] configs=new EGLConfig[1]; int[] count=new int[1];
        assertTrue(EGL14.eglChooseConfig(display,new int[]{EGL14.EGL_RENDERABLE_TYPE,0x40,
                EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,EGL14.EGL_RED_SIZE,8,
                EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_NONE},0,configs,0,1,count,0));
        assertEquals(1,count[0]);
        context=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,
                new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,3,EGL14.EGL_NONE},0);
        surface=EGL14.eglCreatePbufferSurface(display,configs[0],new int[]{EGL14.EGL_WIDTH,16,EGL14.EGL_HEIGHT,16,EGL14.EGL_NONE},0);
        assertTrue(EGL14.eglMakeCurrent(display,surface,surface,context));
        glDisable(GL_BLEND); glDisable(GL_DITHER);
        report("Renderer: "+glGetString(GL_RENDERER)+" / "+glGetString(GL_VERSION));
    }

    @After public void cleanup() {
        if(display==null) return;
        EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
        if(surface!=null) EGL14.eglDestroySurface(display,surface);
        if(context!=null) EGL14.eglDestroyContext(display,context);
        EGL14.eglTerminate(display);
    }

    private static final class Texture implements AutoCloseable {
        final int id,width,height,fbo;
        Texture(int width,int height,boolean floating,Bitmap bitmap) {
            this.width=width; this.height=height;
            int[] ids=new int[1]; glGenTextures(1,ids,0); id=ids[0]; glBindTexture(GL_TEXTURE_2D,id);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
            if(bitmap!=null) GLUtils.texImage2D(GL_TEXTURE_2D,0,bitmap,0);
            else glTexImage2D(GL_TEXTURE_2D,0,floating?GL_RGBA32F:GL_RGBA8,width,height,0,
                    GL_RGBA,floating?GL_FLOAT:GL_UNSIGNED_BYTE,null);
            glGenFramebuffers(1,ids,0); fbo=ids[0]; glBindFramebuffer(GL_FRAMEBUFFER,fbo);
            glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,id,0);
            assertEquals("Framebuffer format / "+floating,GL_FRAMEBUFFER_COMPLETE,glCheckFramebufferStatus(GL_FRAMEBUFFER));
        }
        Bitmap read() {
            glBindFramebuffer(GL_FRAMEBUFFER,fbo);
            ByteBuffer pixels=ByteBuffer.allocateDirect(width*height*4);
            glReadPixels(0,0,width,height,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
            assertEquals(GL_NO_ERROR,glGetError());
            Bitmap result=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
            result.copyPixelsFromBuffer(pixels); return result;
        }
        public void close() { glDeleteFramebuffers(1,new int[]{fbo},0); glDeleteTextures(1,new int[]{id},0); }
    }

    private String shader(String path,int hqScale) throws Exception {
        File file=new File(root,"shaders/"+path);
        assertTrue(file.getCanonicalPath().startsWith(new File(root,"shaders").getCanonicalPath()+File.separator));
        String code=new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8);
        // Only adapt the language version; retain upstream algorithms and default parameters.
        code=code.replaceAll("(?m)^\\s*#version[^\\r\\n]*","");
        if(hqScale>0 && path.endsWith("hqx-pass2.glsl")) {
            assertTrue(code.contains("#define SCALE 2."));
            code=code.replace("#define SCALE 2.","#define SCALE "+hqScale+".");
        }
        if(path.endsWith("crt-geom.glsl")) {
            // The default lum macro collides with a same-named local in the upstream shader.
            assertTrue(code.contains("float lum=length(textureColor)"));
            code=code.replace("float lum=length(textureColor)","float localLuminance=length(textureColor)")
                    .replace("if (lum<0.5)","if (localLuminance<0.5)");
        }
        return code;
    }

    private int compile(int type,String code,String name) {
        int shader=glCreateShader(type);
        glShaderSource(shader,"#version 300 es\nprecision highp float;\nprecision highp int;\n#define "
                +(type==GL_VERTEX_SHADER?"VERTEX":"FRAGMENT")+"\n"+code);
        glCompileShader(shader); int[] ok=new int[1]; glGetShaderiv(shader,GL_COMPILE_STATUS,ok,0);
        String log=glGetShaderInfoLog(shader);
        if(ok[0]==0) { glDeleteShader(shader); fail(name+" / "+type+": "+log); }
        return shader;
    }

    private int program(String path,int hqScale) throws Exception {
        String code=shader(path,hqScale);
        int vertex=compile(GL_VERTEX_SHADER,code,path), fragment=0, program=0;
        try {
            fragment=compile(GL_FRAGMENT_SHADER,code,path); program=glCreateProgram();
            glAttachShader(program,vertex); glAttachShader(program,fragment); glLinkProgram(program);
            int[] ok=new int[1]; glGetProgramiv(program,GL_LINK_STATUS,ok,0);
            assertTrue(path+": "+glGetProgramInfoLog(program),ok[0]!=0);
            if(path.endsWith("xbr-lv2-noblend.glsl") || path.endsWith("advcartoon.glsl")) rgbOnly.add(program);
            else rgbOnly.remove(program);
            return program;
        } catch(Exception|AssertionError failure) { if(program!=0) glDeleteProgram(program); throw failure; }
        finally { glDeleteShader(vertex); if(fragment!=0) glDeleteShader(fragment); }
    }

    private static void bind(int program,String name,Texture texture,int unit) {
        int location=glGetUniformLocation(program,name);
        if(location>=0) { glActiveTexture(GL_TEXTURE0+unit); glBindTexture(GL_TEXTURE_2D,texture.id); glUniform1i(location,unit); }
    }
    private static void size(int program,String name,Texture texture) {
        glUniform2f(glGetUniformLocation(program,name),texture.width,texture.height);
    }
    private void attribute(int program,String name,FloatBuffer data) {
        int location=glGetAttribLocation(program,name);
        if(location>=0) { data.rewind(); glVertexAttribPointer(location,2,GL_FLOAT,false,0,data); glEnableVertexAttribArray(location); }
    }

    private void draw(int program,List<Texture> chain,Texture target,Texture lut) {
        Texture current=chain.get(chain.size()-1), original=chain.get(0);
        glBindFramebuffer(GL_FRAMEBUFFER,target.fbo); glViewport(0,0,target.width,target.height);
        glUseProgram(program); glColorMask(true,true,true,true); glClearColor(1,0,1,1); glClear(GL_COLOR_BUFFER_BIT);
        // RetroArch's opaque final output does not use alpha left undefined by RGB-only shaders.
        glColorMask(true,true,true,!rgbOnly.contains(program));
        bind(program,"Texture",current,0); bind(program,"decal",current,0);
        bind(program,"OrigTexture",original,1);
        size(program,"TextureSize",current); size(program,"InputSize",current); size(program,"OutputSize",target);
        for(int previous=1;previous<=chain.size();previous++) {
            Texture texture=chain.get(chain.size()-previous);
            bind(program,"PassPrev"+previous+"Texture",texture,previous+1);
            size(program,"PassPrev"+previous+"TextureSize",texture);
            size(program,"PassPrev"+previous+"InputSize",texture);
        }
        if(lut!=null) bind(program,"LUT",lut,chain.size()+2);
        glUniform1i(glGetUniformLocation(program,"FrameDirection"),1);
        glUniform1i(glGetUniformLocation(program,"FrameCount"),0);
        glUniformMatrix4fv(glGetUniformLocation(program,"MVPMatrix"),1,false,
                new float[]{1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1},0);
        attribute(program,"VertexCoord",positions); attribute(program,"TexCoord",coordinates);
        int color=glGetAttribLocation(program,"COLOR");
        if(color>=0) { glDisableVertexAttribArray(color); glVertexAttrib4f(color,1,1,1,1); }
        glDrawArrays(GL_TRIANGLE_STRIP,0,4);
        glColorMask(true,true,true,true);
        for(String name:new String[]{"VertexCoord","TexCoord"}) {
            int location=glGetAttribLocation(program,name); if(location>=0) glDisableVertexAttribArray(location);
        }
        assertEquals("Draw error",GL_NO_ERROR,glGetError());
    }

    private Bitmap render(Bitmap input,String mode) throws Exception {
        List<Texture> chain=new ArrayList<>(); List<Integer> programs=new ArrayList<>();
        Texture lut=null;
        int w=input.getWidth(),h=input.getHeight(),hq=mode.startsWith("hq")?Integer.parseInt(mode.substring(2,3)):0;
        List<String> paths=new ArrayList<>(); List<Integer> factors=new ArrayList<>();
        switch(mode) {
            case "original": paths.add("stock.glsl"); factors.add(3); break;
            case "eagle": paths.add("eagle/shaders/supereagle.glsl"); factors.add(2); break;
            case "hq2": case "hq3": case "hq4":
                paths.add("hqx/shader-files/hqx-pass1.glsl"); factors.add(1);
                paths.add("hqx/shader-files/hqx-pass2.glsl"); factors.add(hq); break;
            case "xbr": paths.add("xbr/shaders/xbr-lv2-noblend.glsl"); factors.add(3); break;
            case "xbrz": paths.add("xbrz/shaders/xbrz-freescale.glsl"); factors.add(3); break;
            case "scalefx":
                for(int i=0;i<5;i++) { paths.add("scalefx/shaders/scalefx-pass"+i+".glsl"); factors.add(i==4?3:1); } break;
            case "cartoon": paths.add("cel/shaders/advcartoon.glsl"); factors.add(3); break;
            case "easymode": paths.add("crt/shaders/crt-easymode.glsl"); factors.add(3); break;
            case "geom": paths.add("crt/shaders/crt-geom.glsl"); factors.add(3); break;
            default: throw new AssertionError(mode);
        }
        try {
            chain.add(new Texture(w,h,false,input));
            if(hq>0) {
                // HQ lookup RGBA channels are weights, not opacity. Keep decode unpremultiplied.
                BitmapFactory.Options options=new BitmapFactory.Options(); options.inPremultiplied=false;
                Bitmap weights=BitmapFactory.decodeFile(new File(root,"shaders/hqx/resources/hq"+hq+"x.png").getPath(),options);
                assertNotNull(weights);
                lut=new Texture(weights.getWidth(),weights.getHeight(),false,weights); weights.recycle();
            }
            for(int i=0;i<paths.size();i++) {
                int program=program(paths.get(i),hq); programs.add(program);
                Texture output=new Texture(w*factors.get(i),h*factors.get(i),mode.equals("scalefx") && i<2,null);
                try { draw(program,chain,output,lut); } catch(AssertionError failure) { output.close(); throw failure; }
                chain.add(output);
            }
            Texture last=chain.get(chain.size()-1);
            if(last.width!=w*3 || last.height!=h*3) {
                int program=program("stock.glsl",0); programs.add(program);
                Texture output=new Texture(w*3,h*3,false,null);
                try { draw(program,chain,output,null); } catch(AssertionError failure) { output.close(); throw failure; }
                chain.add(output);
            }
            Bitmap result=chain.get(chain.size()-1).read();
            draw(programs.get(programs.size()-1),chain.subList(0,chain.size()-1),chain.get(chain.size()-1),lut);
            Bitmap repeat=chain.get(chain.size()-1).read(); assertTrue("Stable frozen frame: "+mode,result.sameAs(repeat)); repeat.recycle();
            return result;
        } finally {
            for(Texture texture:chain) texture.close();
            for(int program:programs) glDeleteProgram(program);
            if(lut!=null) lut.close();
        }
    }

    private static void report(String line) {
        android.os.Bundle status=new android.os.Bundle(); status.putString("stream","\n"+line+"\n");
        InstrumentationRegistry.getInstrumentation().sendStatus(0,status);
    }
    private static void png(File file,Bitmap image) throws Exception {
        try(FileOutputStream output=new FileOutputStream(file)) { assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,output)); }
    }

    @Test public void auditionSameFrameWithoutEnhancementPipeline() throws Exception {
        Bitmap source=BitmapFactory.decodeFile(new File(root,"source.png").getPath()); assertNotNull(source);
        assertEquals(128,source.getWidth()); assertEquals(160,source.getHeight());
        String[] modes={"original","eagle","hq2","hq3","hq4","xbr","xbrz","scalefx","cartoon","easymode","geom"};
        String[] names={"Original / nearest","SuperEagle","HQ2x","HQ3x","HQ4x","xBR / no blend","xBRZ / freescale",
                "ScaleFX","Advanced Cartoon","CRT EasyMode","CRT Geom"};
        String[] scaling={"3x","2x -> nearest 3x","2x -> nearest 3x","3x","4x -> nearest 3x","3x","3x","3x","3x","3x","3x"};
        List<String> failures=new ArrayList<>(); Bitmap baseline=null;
        try {
            for(int mode=0;mode<modes.length;mode++) {
                try {
                    Bitmap image=render(source,modes[mode]);
                    if(mode==0) {
                        baseline=image.copy(Bitmap.Config.ARGB_8888,false);
                        for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++)
                            assertEquals(source.getPixel(x/3,y/3),image.getPixel(x,y));
                    }
                    Set<Integer> colors=new HashSet<>(); int changed=0;
                    for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++) {
                        int c=image.getPixel(x,y); colors.add(c);
                        if(baseline!=null && c!=baseline.getPixel(x,y)) changed++;
                    }
                    assertTrue("Nonblank output: "+modes[mode],colors.size()>16);
                    if(mode>0) assertTrue("Filter must affect the fixture: "+modes[mode],changed>0);
                    String id=String.format(Locale.ROOT,"%02d",mode);
                    png(new File(root,id+"-raw.png"),image);
                    Bitmap panel=Bitmap.createBitmap(384,570,Bitmap.Config.ARGB_8888);
                    Canvas canvas=new Canvas(panel); canvas.drawColor(0xFF101417);
                    Paint label=new Paint(Paint.ANTI_ALIAS_FLAG); label.setColor(0xFFFFC766); label.setTextSize(23);
                    canvas.drawText(id+" - "+names[mode],8,29,label);
                    label.setColor(0xFFE5E9ED); label.setTextSize(17);
                    canvas.drawText(scaling[mode]+" / no FSR or interpolation",8,54,label);
                    canvas.drawBitmap(image,0,74,null); png(new File(root,id+"-"+modes[mode]+".png"),panel);
                    panel.recycle(); image.recycle();
                    report(id+" "+names[mode]+": colors="+colors.size()+", changed="+changed+" / 184320");
                } catch(Exception|AssertionError failure) {
                    failures.add(modes[mode]+": "+failure); report("FAILED "+modes[mode]+": "+failure);
                    while(glGetError()!=GL_NO_ERROR) { }
                }
            }
        } finally { source.recycle(); if(baseline!=null) baseline.recycle(); }
        assertTrue(failures.toString(),failures.isEmpty());
    }
}
