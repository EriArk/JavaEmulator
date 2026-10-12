/* SPDX-License-Identifier: GPL-3.0-or-later
 * Experimental GLES3 backend for the adapted FreeJ2ME-Plus triangle pipeline.
 * No legacy M3G implementation is used. See gpu/README.md for scope and limits.
 */
package javax.microedition.m3g;

import android.opengl.*;
import java.nio.*;
import static android.opengl.EGL14.*;
import static android.opengl.GLES30.*;

final class GpuRasterizer {
    static boolean enabled = ru.playsoftware.j2meloader.BuildConfig.M3G_GPU;
    static long draws;
    static String renderer = "not initialized", failure = "";
    private static GpuRasterizer instance;
    private static boolean failed;
    private EGLDisplay display = EGL_NO_DISPLAY;
    private EGLContext context = EGL_NO_CONTEXT;
    private EGLSurface surface = EGL_NO_SURFACE;
    private int program, packProgram, framebuffer, packFramebuffer, color, depth, packed, texture, vbo, vao;
    private ByteBuffer colors, depths, depthInput, textureInput, geometry;
    private int width, height;

    // One submesh is a transaction: CPU buffers change only after both readbacks succeed.
    static synchronized boolean draw(int[] pixels, short[] zbuffer, int w, int h,
            Triangle[] triangles, int count, int ox, int oy, int left, int top, int right, int bottom,
            boolean vertexColors, int defaultColor, boolean perspective, boolean depthTest,
            boolean depthWrite, int mask, int alphaFactor, int threshold, Texture2D tex, boolean linear) {
        if (!enabled || failed || count==0 || w<1 || h<1 || w>2048 || h>2048 || count>65536
                || pixels.length<w*h || zbuffer.length<w*h) return false;
        if (tex!=null && (tex.getImage().image==pixels || tex.getLevelFilter()!=Texture2D.FILTER_BASE_LEVEL
                || (tex.getBlending()!=Texture2D.FUNC_REPLACE && tex.getBlending()!=Texture2D.FUNC_MODULATE)
                || (tex.getImage().getFormat()!=Image2D.RGB && tex.getImage().getFormat()!=Image2D.RGBA))) return false;
        for (int i=0; i<count; i++) {
            Triangle t=triangles[i];
            if (!valid(t.xA(),t.yA(),t.zA(),t.iwA()) || !valid(t.xB(),t.yB(),t.zB(),t.iwB())
                    || !valid(t.xC(),t.yC(),t.zC(),t.iwC())) return false;
            if (tex!=null && !(Float.isFinite(t.sA(0)) && Float.isFinite(t.tA(0))
                    && Float.isFinite(t.sB(0)) && Float.isFinite(t.tB(0))
                    && Float.isFinite(t.sC(0)) && Float.isFinite(t.tC(0)))) return false;
        }
        EGLDisplay previousDisplay=eglGetCurrentDisplay(); EGLContext previousContext=eglGetCurrentContext();
        EGLSurface previousDraw=eglGetCurrentSurface(EGL_DRAW), previousRead=eglGetCurrentSurface(EGL_READ);
        GpuRasterizer gpu=instance;
        boolean success=false;
        try {
            if (gpu==null) { gpu=new GpuRasterizer(); instance=gpu; gpu.initialize(); }
            if (!eglMakeCurrent(gpu.display,gpu.surface,gpu.surface,gpu.context)) throw new IllegalStateException("eglMakeCurrent");
            gpu.render(pixels,zbuffer,w,h,triangles,count,ox,oy,left,top,right,bottom,vertexColors,
                    defaultColor,perspective,depthTest,depthWrite,mask,alphaFactor,threshold,tex,linear);
            success=true;
        } catch (RuntimeException error) {
            failure=error.toString();
        } finally {
            if (gpu!=null && gpu.display!=EGL_NO_DISPLAY) {
                boolean restored=previousDisplay==EGL_NO_DISPLAY
                        ? eglMakeCurrent(gpu.display,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT)
                        : eglMakeCurrent(previousDisplay,previousDraw,previousRead,previousContext);
                if (!restored) { success=false; failure="Unable to restore caller EGL context"; }
            }
        }
        if (!success) { close(); failed=true; return false; }
        gpu.colors.asIntBuffer().get(pixels,0,w*h);
        if (depthWrite) gpu.depths.asShortBuffer().get(zbuffer,0,w*h);
        draws++;
        return true;
    }

    private static boolean valid(float x,float y,float z,float iw) {
        return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z) && z>=0 && z<=32200
                && Float.isFinite(iw) && iw>0;
    }

    static synchronized void close() {
        failed=false;
        if (instance==null) return;
        // All owned objects die with this detached context; never terminate Android's shared display.
        if (instance.surface!=EGL_NO_SURFACE) eglDestroySurface(instance.display,instance.surface);
        if (instance.context!=EGL_NO_CONTEXT) eglDestroyContext(instance.display,instance.context);
        instance=null;
    }

    private void initialize() {
        display=eglGetDisplay(EGL_DEFAULT_DISPLAY);
        int[] version=new int[2];
        if (!eglInitialize(display,version,0,version,1)) throw new IllegalStateException("eglInitialize");
        EGLConfig[] configs=new EGLConfig[1]; int[] count=new int[1];
        if (!eglChooseConfig(display,new int[]{EGL_RENDERABLE_TYPE,0x40,EGL_SURFACE_TYPE,EGL_PBUFFER_BIT,
                EGL_RED_SIZE,8,EGL_GREEN_SIZE,8,EGL_BLUE_SIZE,8,EGL_ALPHA_SIZE,8,EGL_NONE},0,configs,0,1,count,0)
                || count[0]==0) throw new IllegalStateException("No GLES3 pbuffer config");
        context=eglCreateContext(display,configs[0],EGL_NO_CONTEXT,new int[]{EGL_CONTEXT_CLIENT_VERSION,3,EGL_NONE},0);
        surface=eglCreatePbufferSurface(display,configs[0],new int[]{EGL_WIDTH,1,EGL_HEIGHT,1,EGL_NONE},0);
        if (context==EGL_NO_CONTEXT || surface==EGL_NO_SURFACE
                || !eglMakeCurrent(display,surface,surface,context)) throw new IllegalStateException("Create GPU context");
        renderer=glGetString(GL_RENDERER);
        program=program(VERTEX,FRAGMENT); packProgram=program(PACK_VERTEX,PACK_FRAGMENT);
        int[] ids=new int[4]; glGenTextures(4,ids,0);
        color=ids[0]; depth=ids[1]; packed=ids[2]; texture=ids[3];
        glGenFramebuffers(2,ids,0); framebuffer=ids[0]; packFramebuffer=ids[1];
        glGenBuffers(1,ids,0); vbo=ids[0]; glGenVertexArrays(1,ids,0); vao=ids[0];
        check();
    }

    private void render(int[] pixels,short[] zbuffer,int w,int h,Triangle[] triangles,int count,int ox,int oy,
            int left,int top,int right,int bottom,boolean vertexColors,int defaultColor,boolean perspective,
            boolean depthTest,boolean depthWrite,int mask,int alphaFactor,int threshold,Texture2D tex,boolean linear) {
        int packWidth=(w+1)/2;
        colors=buffer(colors,w*h*4); depths=buffer(depths,packWidth*h*4); depthInput=buffer(depthInput,w*h*2);
        // Android ARGB ints arrive as BGRA bytes on the supported little-endian ABIs.
        // Keep that channel order in the FBO and swizzle in shaders, not per pixel in Java.
        colors.asIntBuffer().put(pixels,0,w*h); depthInput.asShortBuffer().put(zbuffer,0,w*h);
        glActiveTexture(GL_TEXTURE0); glPixelStorei(GL_UNPACK_ALIGNMENT,1); glPixelStorei(GL_PACK_ALIGNMENT,1);
        boolean resize=width!=w || height!=h; width=w; height=h;
        targetTexture(color,GL_RGBA8,GL_RGBA,GL_UNSIGNED_BYTE,w,h,colors,resize);
        targetTexture(depth,GL_DEPTH_COMPONENT16,GL_DEPTH_COMPONENT,GL_UNSIGNED_SHORT,w,h,depthInput,resize);
        targetTexture(packed,GL_RGBA8,GL_RGBA,GL_UNSIGNED_BYTE,packWidth,h,null,resize);
        glBindFramebuffer(GL_FRAMEBUFFER,framebuffer);
        glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,color,0);
        glFramebufferTexture2D(GL_FRAMEBUFFER,GL_DEPTH_ATTACHMENT,GL_TEXTURE_2D,depth,0);
        complete();
        geometry=buffer(geometry,count*3*10*4); FloatBuffer data=geometry.asFloatBuffer();
        for (int i=0; i<count; i++) {
            Triangle t=triangles[i];
            vertex(data,t.xA()+ox,t.yA()+oy,t.zA(),vertexColors?t.colorA():defaultColor,
                    tex==null?0:t.sA(0),tex==null?0:t.tA(0),perspective?t.iwA():1);
            vertex(data,t.xB()+ox,t.yB()+oy,t.zB(),vertexColors?t.colorB():defaultColor,
                    tex==null?0:t.sB(0),tex==null?0:t.tB(0),perspective?t.iwB():1);
            vertex(data,t.xC()+ox,t.yC()+oy,t.zC(),vertexColors?t.colorC():defaultColor,
                    tex==null?0:t.sC(0),tex==null?0:t.tC(0),perspective?t.iwC():1);
        }
        data.flip(); glBindVertexArray(vao); glBindBuffer(GL_ARRAY_BUFFER,vbo);
        glBufferData(GL_ARRAY_BUFFER,data.remaining()*4,data,GL_STREAM_DRAW);
        for (int i=0; i<3; i++) glEnableVertexAttribArray(i);
        glVertexAttribPointer(0,3,GL_FLOAT,false,40,0);
        glVertexAttribPointer(1,4,GL_FLOAT,false,40,12);
        glVertexAttribPointer(2,3,GL_FLOAT,false,40,28);
        glViewport(0,0,w,h); glDisable(GL_BLEND); glDisable(GL_CULL_FACE); glDisable(GL_DITHER);
        glEnable(GL_SCISSOR_TEST); glScissor(left,top,Math.max(0,right-left),Math.max(0,bottom-top));
        if (depthTest) glEnable(GL_DEPTH_TEST); else glDisable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL); glDepthMask(depthWrite);
        glColorMask((mask&0xffffff)!=0,(mask&0xffffff)!=0,(mask&0xffffff)!=0,(mask&0xff000000)!=0);
        glUseProgram(program); glUniform2f(glGetUniformLocation(program,"targetSize"),w,h);
        glUniform1i(glGetUniformLocation(program,"mode"),tex==null?0:tex.getBlending()==Texture2D.FUNC_REPLACE?1:2);
        glUniform1i(glGetUniformLocation(program,"rgba"),tex!=null && tex.getImage().getFormat()==Image2D.RGBA?1:0);
        glUniform1i(glGetUniformLocation(program,"alphaFactor"),alphaFactor);
        glUniform1i(glGetUniformLocation(program,"threshold"),threshold);
        if (tex!=null) {
            Image2D image=tex.getImage(); int tw=image.getWidth(), th=image.getHeight();
            textureInput=buffer(textureInput,tw*th*4); textureInput.asIntBuffer().put(image.image,0,tw*th);
            glBindTexture(GL_TEXTURE_2D,texture);
            glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,tw,th,0,GL_RGBA,GL_UNSIGNED_BYTE,textureInput);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,linear?GL_LINEAR:GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,linear?GL_LINEAR:GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,tex.getWrappingS()==Texture2D.WRAP_REPEAT?GL_REPEAT:GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,tex.getWrappingT()==Texture2D.WRAP_REPEAT?GL_REPEAT:GL_CLAMP_TO_EDGE);
            glUniform2f(glGetUniformLocation(program,"imageSize"),tw,th);
        } else glBindTexture(GL_TEXTURE_2D,0);
        glDrawArrays(GL_TRIANGLES,0,count*3);
        glReadPixels(0,0,w,h,GL_RGBA,GL_UNSIGNED_BYTE,colors);
        if (depthWrite) {
            // ES3 has no portable depth readback. Pack two 16-bit samples into each RGBA pixel.
            glBindFramebuffer(GL_FRAMEBUFFER,packFramebuffer);
            glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,packed,0); complete();
            glDisable(GL_DEPTH_TEST); glDisable(GL_SCISSOR_TEST); glColorMask(true,true,true,true);
            glUseProgram(packProgram); glBindTexture(GL_TEXTURE_2D,depth);
            glUniform2i(glGetUniformLocation(packProgram,"size"),w,h);
            glViewport(0,0,packWidth,h);
            glDrawArrays(GL_TRIANGLES,0,3);
            glReadPixels(0,0,packWidth,h,GL_RGBA,GL_UNSIGNED_BYTE,depths);
        }
        check();
    }

    private static void vertex(FloatBuffer out,float x,float y,float z,int c,float s,float t,float iw) {
        out.put(x).put(y).put(z).put((c>>16)&255).put((c>>8)&255).put(c&255).put(c>>>24).put(s).put(t).put(iw);
    }
    private static ByteBuffer buffer(ByteBuffer old,int size) {
        ByteBuffer result=old!=null && old.capacity()>=size?old:ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
        result.clear(); result.limit(size); return result;
    }
    private static void targetTexture(int id,int internal,int format,int type,int w,int h,Buffer data,boolean resize) {
        glBindTexture(GL_TEXTURE_2D,id);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST); glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE); glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
        if (resize) glTexImage2D(GL_TEXTURE_2D,0,internal,w,h,0,format,type,data);
        else if (data!=null) glTexSubImage2D(GL_TEXTURE_2D,0,0,0,w,h,format,type,data);
    }
    private static void complete() {
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("Incomplete GPU target");
    }
    private static void check() {
        int error=glGetError(); if (error!=GL_NO_ERROR) throw new IllegalStateException("GLES error "+error);
    }
    private static int shader(int kind,String source) {
        int id=glCreateShader(kind); glShaderSource(id,source); glCompileShader(id);
        int[] ok=new int[1]; glGetShaderiv(id,GL_COMPILE_STATUS,ok,0);
        if (ok[0]==0) throw new IllegalStateException(glGetShaderInfoLog(id)); return id;
    }
    private static int program(String vertex,String fragment) {
        int id=glCreateProgram(), vs=shader(GL_VERTEX_SHADER,vertex), fs=shader(GL_FRAGMENT_SHADER,fragment);
        glAttachShader(id,vs); glAttachShader(id,fs); glLinkProgram(id); glDeleteShader(vs); glDeleteShader(fs);
        int[] ok=new int[1]; glGetProgramiv(id,GL_LINK_STATUS,ok,0);
        if (ok[0]==0) throw new IllegalStateException(glGetProgramInfoLog(id)); return id;
    }
    private static final String VERTEX="#version 300 es\n"
            +"layout(location=0) in vec3 position; layout(location=1) in vec4 color; layout(location=2) in vec3 uv;\n"
            +"uniform vec2 targetSize; out highp vec4 vertexColor; out highp vec3 texCoord; out highp float depthValue;\n"
            +"void main(){ gl_Position=vec4(position.xy/targetSize*2.0-1.0,0,1); vertexColor=color; texCoord=uv; depthValue=position.z; }";
    private static final String FRAGMENT="#version 300 es\nprecision highp float; precision highp int;\n"
            +"in vec4 vertexColor; in vec3 texCoord; in float depthValue; uniform sampler2D image; uniform vec2 imageSize;\n"
            +"uniform int mode,rgba,alphaFactor,threshold; out vec4 result;\n"
            +"void main(){ vec4 c=floor(clamp(vertexColor,0.0,255.0)+0.0001);\n"
            +"if(mode!=0){ vec4 t=floor(texture(image,texCoord.xy/texCoord.z/imageSize).bgra*255.0+0.5);\n"
            +"if(mode==1){ c.rgb=t.rgb; if(rgba!=0)c.a=t.a; }\n"
            +"else { c.rgb=floor((c.rgb*t.rgb+(rgba!=0?128.0:0.0))/256.0); if(rgba!=0)c.a=floor((c.a*t.a+c.a)/256.0); }}\n"
            +"float a=floor(c.a*float(alphaFactor)/256.0); if(a<float(threshold))discard; if(a==0.0)c.a=255.0;\n"
            +"result=c.bgra/255.0; gl_FragDepth=floor(depthValue)/65535.0; }";
    private static final String PACK_VERTEX="#version 300 es\nvoid main(){\n"
            +"vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2); gl_Position=vec4(p*2.0-1.0,0,1); }";
    private static final String PACK_FRAGMENT="#version 300 es\nprecision highp float; precision highp int;\n"
            +"uniform highp sampler2D image; uniform ivec2 size; out vec4 result;\n"
            +"int sampleDepth(int i){ if(i>=size.x*size.y)return 0;\n"
            +"return int(floor(texelFetch(image,ivec2(i%size.x,i/size.x),0).r*65535.0+0.5)); }\n"
            +"void main(){ int i=2*(int(gl_FragCoord.x)+int(gl_FragCoord.y)*((size.x+1)/2));\n"
            +"int a=sampleDepth(i),b=sampleDepth(i+1); result=vec4(a&255,(a>>8)&255,b&255,(b>>8)&255)/255.0; }";
}
