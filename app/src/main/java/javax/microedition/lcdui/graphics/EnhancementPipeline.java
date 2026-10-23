/* Copyright 2026. GPL-3.0-only. */
package javax.microedition.lcdui.graphics;

import android.graphics.Bitmap;
import android.opengl.GLUtils;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import javax.microedition.util.ContextHolder;
import ru.playsoftware.j2meloader.runtime.FrameInterpolationClock;
import static android.opengl.GLES20.*;

/** GL-thread-owned, bounded presentation resources. No guest state or GPU readbacks. */
public final class EnhancementPipeline {
    private static final String VERTEX = "attribute vec2 a_position; attribute vec2 a_texcoord0;"
            + "varying vec2 v_texcoord0; void main(){gl_Position=vec4(a_position,0,1);v_texcoord0=a_texcoord0;}";
    private final FloatBuffer quad = ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder()).asFloatBuffer();
    private final FrameInterpolationClock clock = new FrameInterpolationClock();
    private final int[] inputs = new int[2];
    private final Target flow = new Target(), interpolated = new Target(), upscaled = new Target(), sharpened = new Target();
    private Program easu, rcas, estimate, resolve;
    private long serial = Long.MIN_VALUE;
    private int current, tw, th, sw, sh;
    private boolean history, motionEnabled, animating;
    private int cachedTexture, cachedWidth, cachedHeight, cachedStrength;
    private boolean cachedFsr;

    public EnhancementPipeline() {
        quad.put(new float[]{-1,-1,0,0, -1,1,0,1, 1,-1,1,0, 1,1,1,1}).rewind();
        glGenTextures(2, inputs, 0);
        for (int texture : inputs) configure(texture);
    }

    public boolean animating() { return animating; }
    public void resetHistory() {
        history=false; clock.reset(); serial=Long.MIN_VALUE; animating=false; cachedTexture=0;
    }

    /** Returns a texture containing only the guest viewport, ready for host rotation. */
    public int render(Bitmap frame, long frameSerial, int width, int height, int outW, int outH,
                      boolean fsr, boolean motion, int strength, long now) {
        if (width<=0 || height<=0 || outW<=0 || outH<=0
                || (long)outW*outH>4_194_304L || (long)frame.getWidth()*frame.getHeight()>1_048_576L)
            throw new IllegalArgumentException("Enhancement texture budget exceeded");
        if (tw!=frame.getWidth() || th!=frame.getHeight() || sw!=width || sh!=height || motionEnabled!=motion) {
            tw=frame.getWidth(); th=frame.getHeight(); sw=width; sh=height; motionEnabled=motion;
            for (int texture : inputs) {
                configure(texture);
                glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,tw,th,0,GL_RGBA,GL_UNSIGNED_BYTE,null);
            }
            resetHistory();
        }
        boolean fresh=serial!=frameSerial;
        if (!fresh && !animating && cachedTexture!=0 && cachedWidth==outW && cachedHeight==outH
                && cachedFsr==fsr && cachedStrength==strength) return cachedTexture;
        if (fresh) {
            current=1-current;
            configure(inputs[current]); GLUtils.texSubImage2D(GL_TEXTURE_2D,0,0,0,frame);
            clock.frame(now); serial=frameSerial;
            if (motion && history) {
                if (estimate==null) estimate=new Program("motion-estimate.fsh");
                flow.size(Math.max(1,(sw+7)/8),Math.max(1,(sh+7)/8));
                begin(estimate,flow,inputs[current],sw,sh,tw,th);
                second(estimate,"previous",inputs[1-current],1); draw();
            }
        }
        int texture=inputs[current], textureW=tw, textureH=th;
        float phase=motion && history ? clock.phase(now) : 1f;
        animating=phase<1f;
        if (motion) {
            if (resolve==null) resolve=new Program("motion-resolve.fsh");
            interpolated.size(sw,sh);
            begin(resolve,interpolated,texture,sw,sh,tw,th);
            second(resolve,"previous",history?inputs[1-current]:texture,1);
            second(resolve,"motion",flow.texture!=0?flow.texture:texture,2);
            glUniform1f(resolve.uniform("phase"),phase); draw();
            texture=interpolated.texture; textureW=sw; textureH=sh;
        }
        history=true;
        // EASU is an upscaler, not a downsampling filter.
        if (fsr && outW>=sw && outH>=sh) {
            if (easu==null) easu=new Program("fsr-easu.fsh");
            if (rcas==null) rcas=new Program("fsr-rcas.fsh");
            upscaled.size(outW,outH); sharpened.size(outW,outH);
            begin(easu,upscaled,texture,sw,sh,textureW,textureH); draw();
            begin(rcas,sharpened,upscaled.texture,outW,outH,outW,outH);
            glUniform1f(rcas.uniform("sharpness"),Math.max(0,Math.min(100,strength))/100f);
            draw(); texture=sharpened.texture;
        } else if (!motion) {
            // Crop padded Canvas textures even when EASU is bypassed during minification.
            if (resolve==null) resolve=new Program("motion-resolve.fsh");
            interpolated.size(sw,sh);
            begin(resolve,interpolated,texture,sw,sh,tw,th);
            second(resolve,"previous",texture,1); second(resolve,"motion",texture,2);
            glUniform1f(resolve.uniform("phase"),1f); draw(); texture=interpolated.texture;
        }
        glBindFramebuffer(GL_FRAMEBUFFER,0); glActiveTexture(GL_TEXTURE0);
        int error=glGetError();
        if(error!=GL_NO_ERROR) throw new IllegalStateException("Enhancement GL error: "+error);
        cachedTexture=texture; cachedWidth=outW; cachedHeight=outH; cachedFsr=fsr; cachedStrength=strength;
        return texture;
    }

    private void begin(Program p, Target t, int texture, int w, int h, int texW, int texH) {
        glBindFramebuffer(GL_FRAMEBUFFER,t.fbo); glViewport(0,0,t.width,t.height);
        glUseProgram(p.id); glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D,texture);
        glUniform1i(p.uniform("sampler0"),0);
        glUniform2f(p.uniform("sourceSize"),w,h); glUniform2f(p.uniform("textureSize"),texW,texH);
        quad.position(0); glVertexAttribPointer(0,2,GL_FLOAT,false,16,quad); glEnableVertexAttribArray(0);
        quad.position(2); glVertexAttribPointer(1,2,GL_FLOAT,false,16,quad); glEnableVertexAttribArray(1);
    }
    private static void second(Program p,String name,int texture,int unit) {
        glActiveTexture(GL_TEXTURE0+unit); glBindTexture(GL_TEXTURE_2D,texture); glUniform1i(p.uniform(name),unit);
    }
    private static void draw() { glDrawArrays(GL_TRIANGLE_STRIP,0,4); }
    private static void configure(int texture) {
        glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D,texture);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
    }
    public void close() {
        flow.close(); interpolated.close(); upscaled.close(); sharpened.close();
        glDeleteTextures(2,inputs,0);
        for(Program p:new Program[]{easu,rcas,estimate,resolve}) if(p!=null) glDeleteProgram(p.id);
    }
    private static final class Target {
        int texture,fbo,width,height;
        void size(int w,int h) {
            if(width==w && height==h) return;
            close(); int[] id=new int[1]; glGenTextures(1,id,0); texture=id[0]; configure(texture);
            glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,w,h,0,GL_RGBA,GL_UNSIGNED_BYTE,null);
            glGenFramebuffers(1,id,0); fbo=id[0]; glBindFramebuffer(GL_FRAMEBUFFER,fbo);
            glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,texture,0);
            if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)
                throw new IllegalStateException("Enhancement framebuffer unavailable");
            width=w; height=h;
        }
        void close() {
            if(texture!=0) glDeleteTextures(1,new int[]{texture},0);
            if(fbo!=0) glDeleteFramebuffers(1,new int[]{fbo},0);
            texture=fbo=width=height=0;
        }
    }
    private static final class Program {
        final int id;
        Program(String fragment) {
            int v=0,f=0,p=0;
            try {
                v=compile(GL_VERTEX_SHADER,VERTEX);
                f=compile(GL_FRAGMENT_SHADER,ContextHolder.getAssetAsString("shaders/"+fragment));
                p=glCreateProgram(); glAttachShader(p,v); glAttachShader(p,f);
                glBindAttribLocation(p,0,"a_position"); glBindAttribLocation(p,1,"a_texcoord0");
                glLinkProgram(p); int[] status=new int[1]; glGetProgramiv(p,GL_LINK_STATUS,status,0);
                if(status[0]==0) throw new IllegalStateException(glGetProgramInfoLog(p));
                id=p;
            } catch(RuntimeException e) { if(p!=0) glDeleteProgram(p); throw e; }
            finally { if(v!=0) glDeleteShader(v); if(f!=0) glDeleteShader(f); }
        }
        int uniform(String name) { return glGetUniformLocation(id,name); }
        static int compile(int type,String text) {
            int shader=glCreateShader(type); glShaderSource(shader,text); glCompileShader(shader);
            int[] status=new int[1]; glGetShaderiv(shader,GL_COMPILE_STATUS,status,0);
            if(status[0]==0) { String error=glGetShaderInfoLog(shader); glDeleteShader(shader); throw new IllegalStateException(error); }
            return shader;
        }
    }
}
