/* Copyright 2026. GPL-3.0-only. */
package javax.microedition.lcdui.graphics;

import android.graphics.Bitmap;
import android.opengl.GLUtils;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.function.LongSupplier;
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
    private final Target estimatedFlow = new Target(), flow = new Target(), sceneState = new Target(), interpolated = new Target(), upscaled = new Target(), sharpened = new Target();
    private Program easu, rcas, estimate, spread, resolve, scene, copy;
    private RetroShaderProgram xbrz, crt;
    private int retroShader;
    private long serial = Long.MIN_VALUE;
    private int current, tw, th, sw, sh;
    private boolean history, motionEnabled, animating, uploaded, motionPending;
    private int cachedTexture, cachedWidth, cachedHeight, cachedStrength;
    private boolean cachedFsr;
    private boolean cachedPresentation;
    private int presentationTexture, presentationWidth, presentationHeight, presentationTexWidth, presentationTexHeight;
    private int presentationStrength;

    public EnhancementPipeline() {
        quad.put(new float[]{-1,-1,0,0, -1,1,0,1, 1,-1,1,0, 1,1,1,1}).rewind();
        glGenTextures(2, inputs, 0);
        for (int texture : inputs) configure(texture);
    }

    public boolean animating() { return animating; }
    public void setRetroShader(int mode) {
        int next = mode == 1 || mode == 2 ? mode : 0;
        if (retroShader == next) return;
        retroShader = next; resetHistory();
    }
    public void skipInterpolation() {
        if (!motionPending && !animating) return;
        clock.finish(); motionPending=false; animating=false; cachedTexture=0;
        presentationStrength=0;
    }
    public void resetHistory() {
        history=false; clock.reset(); serial=Long.MIN_VALUE; animating=false; cachedTexture=0;
        uploaded=false; motionPending=false;
        presentationStrength=0;
    }

    /** Returns a texture containing only the guest viewport, ready for host rotation. */
    public int render(Bitmap frame, long frameSerial, int width, int height, int outW, int outH,
                      boolean fsr, boolean motion, int strength, long now) {
        validateOutput(outW,outH);
        upload(frame,frameSerial,width,height,motion,now);
        return renderUploaded(outW,outH,fsr,strength,now);
    }

    /** GL thread only. Protect pixels through upload; readyNanos is captured when those pixels are completed. */
    public void upload(Bitmap frame, long frameSerial, int width, int height, boolean motion, long readyNanos) {
        if (width<=0 || height<=0 || width>frame.getWidth() || height>frame.getHeight()
                || (long)frame.getWidth()*frame.getHeight()>1_048_576L)
            throw new IllegalArgumentException("Enhancement texture budget exceeded");
        if (tw!=frame.getWidth() || th!=frame.getHeight() || sw!=width || sh!=height || motionEnabled!=motion) {
            tw=frame.getWidth(); th=frame.getHeight(); sw=width; sh=height; motionEnabled=motion;
            for (int texture : inputs) {
                configure(texture);
                glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA,tw,th,0,GL_RGBA,GL_UNSIGNED_BYTE,null);
            }
            resetHistory();
        }
        if (!uploaded || serial!=frameSerial) {
            current=1-current;
            configure(inputs[current]); GLUtils.texSubImage2D(GL_TEXTURE_2D,0,0,0,frame);
            clock.frame(readyNanos); serial=frameSerial;
            uploaded=true; cachedTexture=0; motionPending=motion && history;
            presentationStrength=0;
        }
    }

    /** Uses GPU-owned pixels only. No reference to the guest Bitmap survives upload(). */
    public int renderUploaded(int outW, int outH, boolean fsr, int strength, long now) {
        return renderUploaded(outW,outH,fsr,strength,now,false);
    }

    /** Defer RCAS to the final draw, avoiding an output-sized target and copy pass. */
    public int renderForPresentation(int outW, int outH, boolean fsr, int strength, long now) {
        return renderUploaded(outW,outH,fsr,strength,now,true);
    }

    /** Live draw: include CPU-side pass preparation in the interpolation deadline. */
    public int renderForPresentation(int outW, int outH, boolean fsr, int strength) {
        return renderForPresentation(outW,outH,fsr,strength,System::nanoTime);
    }

    int renderForPresentation(int outW, int outH, boolean fsr, int strength, LongSupplier drawClock) {
        return renderUploaded(outW,outH,fsr,strength,drawClock.getAsLong(),true,drawClock);
    }

    private int renderUploaded(int outW, int outH, boolean fsr, int strength, long now, boolean presentation) {
        return renderUploaded(outW,outH,fsr,strength,now,presentation,null);
    }

    private int renderUploaded(int outW, int outH, boolean fsr, int strength, long now,
                               boolean presentation, LongSupplier drawClock) {
        validateOutput(outW,outH);
        if (!uploaded) throw new IllegalStateException("No uploaded enhancement frame");
        if (retroShader != 0) return renderRetro(outW,outH);
        if (!motionPending && !animating && cachedTexture!=0 && cachedWidth==outW && cachedHeight==outH
                && cachedFsr==fsr && cachedStrength==strength && cachedPresentation==presentation) return cachedTexture;
        presentationStrength=0;
        if (motionPending) {
            if (clock.phase(now)<1f) {
                if (estimate==null) estimate=new Program("motion-estimate.fsh");
                estimatedFlow.size(Math.max(1,(sw+7)/8),Math.max(1,(sh+7)/8));
                begin(estimate,estimatedFlow,inputs[current],sw,sh,tw,th);
                second(estimate,"previous",inputs[1-current],1); draw();
                if (scene==null) scene=new Program("motion-scene.fsh");
                sceneState.size(1,1);
                begin(scene,sceneState,estimatedFlow.texture,estimatedFlow.width,estimatedFlow.height,
                        estimatedFlow.width,estimatedFlow.height); draw();
                if (spread==null) spread=new Program("motion-spread.fsh");
                flow.size(estimatedFlow.width,estimatedFlow.height);
                begin(spread,flow,estimatedFlow.texture,flow.width,flow.height,flow.width,flow.height); draw();
            }
            motionPending=false;
        }
        int texture=inputs[current], textureW=tw, textureH=th;
        float phase=motionEnabled && history ? clock.phase(now) : 1f;
        animating=phase<1f;
        if (animating) {
            if (resolve==null) resolve=new Program("motion-resolve.fsh");
            interpolated.size(sw,sh);
            // Compilation/allocation/submission may consume the remaining window.
            // Do not resolve an old phase or schedule more animation after it expires.
            if (drawClock!=null) phase=clock.phase(drawClock.getAsLong());
            animating=phase<1f;
        }
        if (animating) {
            begin(resolve,interpolated,texture,sw,sh,tw,th);
            second(resolve,"previous",history?inputs[1-current]:texture,1);
            second(resolve,"motion",flow.texture!=0?flow.texture:texture,2);
            second(resolve,"sceneState",sceneState.texture,3);
            glUniform1f(resolve.uniform("phase"),phase); draw();
            texture=interpolated.texture; textureW=sw; textureH=sh;
        }
        history=true;
        // EASU is an upscaler, not a downsampling filter.
        boolean upscale=fsr && outW>=sw && outH>=sh && (outW>sw || outH>sh);
        if (upscale) {
            if (easu==null) easu=new Program("fsr-easu.fsh");
            upscaled.size(outW,outH);
            begin(easu,upscaled,texture,sw,sh,textureW,textureH); draw();
            texture=upscaled.texture; textureW=outW; textureH=outH;
        }
        if (fsr && strength>0 && outW>=sw && outH>=sh) {
            if (presentation) {
                presentationTexture=texture; presentationWidth=outW; presentationHeight=outH;
                presentationTexWidth=textureW; presentationTexHeight=textureH;
                presentationStrength=Math.min(100,strength);
            } else {
                if (rcas==null) rcas=new Program("fsr-rcas.fsh");
                sharpened.size(outW,outH);
                begin(rcas,sharpened,texture,outW,outH,textureW,textureH);
                glUniform1f(rcas.uniform("sharpness"),Math.min(100,strength)/100f);
                draw(); texture=sharpened.texture;
            }
        } else if (!upscale && (textureW!=sw || textureH!=sh)) {
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
        cachedPresentation=presentation;
        return texture;
    }

    /** Draw into the caller's framebuffer/viewport with normalized guest UVs, including host rotation.
     * Returns false when the caller should present the returned texture normally. */
    public boolean presentSharpened(FloatBuffer vertices) {
        if (presentationStrength==0) return false;
        if (rcas==null) rcas=new Program("fsr-rcas.fsh");
        source(rcas,presentationTexture,presentationWidth,presentationHeight,presentationTexWidth,presentationTexHeight);
        glUniform1f(rcas.uniform("sharpness"),presentationStrength/100f);
        vertices(vertices); draw();
        int error=glGetError();
        if(error!=GL_NO_ERROR) throw new IllegalStateException("Enhancement presentation GL error: "+error);
        return true;
    }

    private int renderRetro(int width,int height) {
        presentationStrength=0; motionPending=false; animating=false; history=false;
        if(cachedTexture!=0 && cachedWidth==width && cachedHeight==height) return cachedTexture;
        int texture=inputs[current];
        if(tw!=sw || th!=sh) {
            if(resolve==null) resolve=new Program("motion-resolve.fsh");
            interpolated.size(sw,sh);
            begin(resolve,interpolated,texture,sw,sh,tw,th);
            second(resolve,"previous",texture,1); second(resolve,"motion",texture,2);
            glUniform1f(resolve.uniform("phase"),1f); draw(); texture=interpolated.texture;
        }
        RetroShaderProgram program;
        if(retroShader==1) { if(xbrz==null) xbrz=new RetroShaderProgram(1); program=xbrz; }
        else { if(crt==null) crt=new RetroShaderProgram(2); program=crt; }
        upscaled.size(width,height); glBindFramebuffer(GL_FRAMEBUFFER,upscaled.fbo); glViewport(0,0,width,height);
        program.draw(texture,sw,sh,width,height,quad);
        glBindFramebuffer(GL_FRAMEBUFFER,0); glActiveTexture(GL_TEXTURE0);
        int error=glGetError(); if(error!=GL_NO_ERROR) throw new IllegalStateException("Retro shader GL error: "+error);
        cachedTexture=upscaled.texture; cachedWidth=width; cachedHeight=height;
        return cachedTexture;
    }

    /** Present the processed texture in caller-owned geometry without another output-sized target. */
    public void presentTexture(int texture, FloatBuffer vertices, boolean filter) {
        if (retroShader != 0) filter = false;
        if (presentSharpened(vertices)) return;
        if (copy == null) copy = new Program("simple.fsh");
        source(copy, texture, sw, sh, sw, sh);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter ? GL_LINEAR : GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter ? GL_LINEAR : GL_NEAREST);
        vertices(vertices); draw();
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        int error = glGetError();
        if (error != GL_NO_ERROR) throw new IllegalStateException("Enhancement presentation GL error: " + error);
    }

    private static void validateOutput(int width,int height) {
        if (width<=0 || height<=0 || (long)width*height>4_194_304L)
            throw new IllegalArgumentException("Enhancement texture budget exceeded");
    }

    private void begin(Program p, Target t, int texture, int w, int h, int texW, int texH) {
        glBindFramebuffer(GL_FRAMEBUFFER,t.fbo); glViewport(0,0,t.width,t.height);
        source(p,texture,w,h,texW,texH); vertices(quad);
    }
    private static void source(Program p, int texture, int w, int h, int texW, int texH) {
        glUseProgram(p.id); glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D,texture);
        glUniform1i(p.uniform("sampler0"),0);
        glUniform2f(p.uniform("sourceSize"),w,h); glUniform2f(p.uniform("textureSize"),texW,texH);
    }
    private static void vertices(FloatBuffer vertices) {
        vertices.position(0); glVertexAttribPointer(0,2,GL_FLOAT,false,16,vertices); glEnableVertexAttribArray(0);
        vertices.position(2); glVertexAttribPointer(1,2,GL_FLOAT,false,16,vertices); glEnableVertexAttribArray(1);
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

    static String easuSource(String code, String renderer) {
        // Software SIMD rasterizers can pay heavily for per-fragment control flow.
        String name=renderer==null?"":renderer.toLowerCase(java.util.Locale.ROOT);
        if (name.isEmpty() || name.contains("swiftshader") || name.contains("llvmpipe")
                || name.contains("softpipe") || name.contains("software rasterizer")) return code;
        return "#define EASU_FLAT_REGIONS\n"+code;
    }

    public void close() {
        if(xbrz!=null) xbrz.close(); if(crt!=null) crt.close();
        estimatedFlow.close(); flow.close(); sceneState.close(); interpolated.close(); upscaled.close(); sharpened.close();
        glDeleteTextures(2,inputs,0);
        for(Program p:new Program[]{easu,rcas,estimate,spread,resolve,scene,copy}) if(p!=null) glDeleteProgram(p.id);
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
        private final java.util.Map<String,Integer> uniforms=new java.util.HashMap<>();
        Program(String fragment) {
            int v=0,f=0,p=0;
            try {
                v=compile(GL_VERTEX_SHADER,VERTEX);
                String code=ContextHolder.getAssetAsString("shaders/"+fragment);
                if ("fsr-easu.fsh".equals(fragment)) code=easuSource(code,glGetString(GL_RENDERER));
                f=compile(GL_FRAGMENT_SHADER,code);
                p=glCreateProgram(); glAttachShader(p,v); glAttachShader(p,f);
                glBindAttribLocation(p,0,"a_position"); glBindAttribLocation(p,1,"a_texcoord0");
                glLinkProgram(p); int[] status=new int[1]; glGetProgramiv(p,GL_LINK_STATUS,status,0);
                if(status[0]==0) throw new IllegalStateException(glGetProgramInfoLog(p));
                id=p;
            } catch(RuntimeException e) { if(p!=0) glDeleteProgram(p); throw e; }
            finally { if(v!=0) glDeleteShader(v); if(f!=0) glDeleteShader(f); }
        }
        int uniform(String name) {
            Integer location=uniforms.get(name);
            if(location==null) { location=glGetUniformLocation(id,name); uniforms.put(name,location); }
            return location;
        }
        static int compile(int type,String text) {
            int shader=glCreateShader(type); glShaderSource(shader,text); glCompileShader(shader);
            int[] status=new int[1]; glGetShaderiv(shader,GL_COMPILE_STATUS,status,0);
            if(status[0]==0) { String error=glGetShaderInfoLog(shader); glDeleteShader(shader); throw new IllegalStateException(error); }
            return shader;
        }
    }
}
