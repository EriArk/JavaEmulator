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
    static boolean retainTargets = true;
    static long draws, uploads, readbacks, textureUploads, emptyDraws, offsetDraws, multiplyDraws, spriteDraws;
    static String renderer = "not initialized", failure = "";
    private static GpuRasterizer instance;
    private static boolean failed;
    private static final java.util.Set<short[]> signedDepthChecks = java.util.Collections.newSetFromMap(
            new java.util.WeakHashMap<short[],Boolean>());
    private static final java.util.HashSet<Integer> reportedFallbacks=new java.util.HashSet<>();
    private static int diagnosticLines;
    private static void diagnostic(String message) {
        android.util.Log.i("M3GGpu",message);
        if (diagnosticLines>=128) return;
        android.app.Activity activity=javax.microedition.util.ContextHolder.getActivity();
        if(activity==null || activity.getExternalFilesDir(null)==null) return;
        java.io.File file=new java.io.File(activity.getExternalFilesDir(null),"m3g-gpu-session.txt");
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(file,diagnosticLines>0)) {
            out.write((System.currentTimeMillis()+" pid="+android.os.Process.myPid()+" "+message+"\n")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            diagnosticLines++;
        } catch(java.io.IOException error) { android.util.Log.w("M3GGpu","Diagnostic write failed",error); }
    }
    static void noteFallback(int flags,int blend,int format) {
        if (!ru.playsoftware.j2meloader.BuildConfig.M3G_GPU) return;
        int key=flags|(blend<<8)|(format<<16);
        if(reportedFallbacks.add(key)) diagnostic("CPU fallback flags="+flags+" blend="+blend+" format="+format+" failure="+failure);
    }
    static synchronized void cpuDepthWriteWithOffset(short[] depth) { signedDepthChecks.add(depth); }
    private EGLDisplay display = EGL_NO_DISPLAY;
    private EGLContext context = EGL_NO_CONTEXT;
    private EGLSurface surface = EGL_NO_SURFACE;
    private int program, packProgram, framebuffer, packFramebuffer, color, depth, packed, vbo, vao;
    private final int[] textureIds = new int[4];
    private final Image2D[][] uploadedImages = new Image2D[4][12];
    private final long[][] uploadedRevisions = new long[4][12];
    private final int[] modes=new int[4], sizes=new int[4], formats=new int[4], mips=new int[4], blendColors=new int[4];
    private int targetSizeLocation, alphaLocation, thresholdLocation, compositeLocation;
    private int fogModeLocation, parallelFogLocation, fogColorLocation, fogParamsLocation, packSizeLocation;
    private int spriteLocation, spriteLayoutLocation, spriteIndices, spriteIndexWidth;
    private ByteBuffer spriteIndexInput;
    private ByteBuffer colors, depths, depthInput, textureInput, geometry;
    private int width, height;
    private int[] residentPixels;
    private short[] residentDepth;
    private boolean depthDirty;

    private static final class SpriteState {
        Image2D image;
        float[] mapping;
        int[] flags;
        int ox, oy;
    }
    private static final SpriteState spriteState = new SpriteState();

    static synchronized boolean sprite(int[] pixels, short[] depth, int w, int h, Image2D image,
            float[] mapping, int[] flags, int ox, int oy, int blend, boolean resident) {
        if (!enabled || failed) return false;
        for(float value:mapping) if(!Float.isFinite(value)) return false;
        SpriteState s=spriteState;
        s.image=image;
        s.mapping=mapping; s.flags=flags; s.ox=ox; s.oy=oy;
        return draw(pixels,depth,w,h,null,2,ox,oy,ox+flags[0],oy+flags[2],ox+flags[1],oy+flags[3],
                false,0,false,(flags[12]&1)!=0,(flags[12]&2)!=0,flags[16],flags[14],flags[15],
                null,1,null,null,blend,0,0,null,false,null,resident,s);
    }

    // CPU access barriers commit consecutive draws together. Image targets flush immediately.
    static synchronized boolean draw(int[] pixels, short[] zbuffer, int w, int h,
            Triangle[] triangles, int count, int ox, int oy, int left, int top, int right, int bottom,
            boolean vertexColors, int defaultColor, boolean perspective, boolean depthTest,
            boolean depthWrite, int mask, int alphaFactor, int threshold, Texture2D[] textures, int units,
            Graphics3D.TextureFilter[] filters, Graphics3DPipelines.MipmapMode[] mipModes,
            int blend, float depthFactor, float depthUnits, Fog fog, boolean parallelFog, float[] fogDepth, boolean resident) {
        return draw(pixels,zbuffer,w,h,triangles,count,ox,oy,left,top,right,bottom,vertexColors,
                defaultColor,perspective,depthTest,depthWrite,mask,alphaFactor,threshold,textures,units,
                filters,mipModes,blend,depthFactor,depthUnits,fog,parallelFog,fogDepth,resident,null);
    }

    private static boolean draw(int[] pixels, short[] zbuffer, int w, int h,
            Triangle[] triangles, int count, int ox, int oy, int left, int top, int right, int bottom,
            boolean vertexColors, int defaultColor, boolean perspective, boolean depthTest,
            boolean depthWrite, int mask, int alphaFactor, int threshold, Texture2D[] textures, int units,
            Graphics3D.TextureFilter[] filters, Graphics3DPipelines.MipmapMode[] mipModes,
            int blend, float depthFactor, float depthUnits, Fog fog, boolean parallelFog, float[] fogDepth,
            boolean resident, SpriteState sprite) {
        if (!enabled || failed || count==0 || w<1 || h<1 || w>2048 || h>2048 || count>65536
                || pixels.length<w*h || zbuffer.length<w*h) return false;
        if (signedDepthChecks.contains(zbuffer)) {
            // CPU uses signed shorts; raw negative values cannot be uploaded as unsigned GL depth.
            for(int i=0;i<w*h;i++) if(zbuffer[i]<0) return false;
            signedDepthChecks.remove(zbuffer);
        }
        if (units<0 || units>4) return false;
        for (int unit=0;unit<units;unit++) {
            Image2D image=sprite==null?textures[unit].getImage():sprite.image;
            if (image.image==pixels || image.getWidth()>2048 || image.getHeight()>2048
                    || image.getFormat()<Image2D.ALPHA || image.getFormat()>Image2D.RGBA) return false;
        }
        if (blend<CompositingMode.ALPHA || blend>CompositingMode.REPLACE
                || !Float.isFinite(depthFactor) || !Float.isFinite(depthUnits)) return false;
        if (fog!=null && (!Float.isFinite(fog.getDensity()) || !Float.isFinite(fog.getNearDistance())
                || !Float.isFinite(fog.getFarDistance()) || (fog.getMode()==Fog.LINEAR
                && Math.abs(fog.getFarDistance()-fog.getNearDistance())<0.000001f))) return false;
        if(sprite!=null && (sprite.flags[13]<0 || sprite.flags[13]>32200 || left<0 || top<0
                || right>w || bottom>h || left>=right || top>=bottom)) return false;
        for (int i=0; sprite==null && i<count; i++) {
            Triangle t=triangles[i];
            if (parallelFog && (!Float.isFinite(fogDepth[i*3]) || !Float.isFinite(fogDepth[i*3+1])
                    || !Float.isFinite(fogDepth[i*3+2]))) return false;
            float offset=depthOffset(t,depthFactor,depthUnits);
            if (!valid(t.xA(),t.yA(),t.zA()+offset,t.iwA()) || !valid(t.xB(),t.yB(),t.zB()+offset,t.iwB())
                    || !valid(t.xC(),t.yC(),t.zC()+offset,t.iwC())) return false;
            for (int unit=0;unit<units;unit++) if (!(Float.isFinite(t.sA(unit)) && Float.isFinite(t.tA(unit))
                    && Float.isFinite(t.sB(unit)) && Float.isFinite(t.tB(unit))
                    && Float.isFinite(t.sC(unit)) && Float.isFinite(t.tC(unit)))) return false;
        }
        if (instance!=null && instance.residentPixels!=null
                && (instance.residentPixels!=pixels || instance.residentDepth!=zbuffer
                || instance.width!=w || instance.height!=h)) flush();
        EGLDisplay previousDisplay=eglGetCurrentDisplay(); EGLContext previousContext=eglGetCurrentContext();
        EGLSurface previousDraw=eglGetCurrentSurface(EGL_DRAW), previousRead=eglGetCurrentSurface(EGL_READ);
        GpuRasterizer gpu=instance;
        boolean success=false;
        try {
            if (gpu==null) { gpu=new GpuRasterizer(); instance=gpu; gpu.initialize(); }
            if (!eglMakeCurrent(gpu.display,gpu.surface,gpu.surface,gpu.context)) throw new IllegalStateException("eglMakeCurrent");
            gpu.render(pixels,zbuffer,w,h,triangles,count,ox,oy,left,top,right,bottom,vertexColors,
                    defaultColor,perspective,depthTest,depthWrite,mask,alphaFactor,threshold,textures,units,filters,mipModes,
                    blend,depthFactor,depthUnits,fog,parallelFog,fogDepth,sprite);
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
        if (!success) {
            boolean pending=gpu!=null && gpu.residentPixels!=null;
            destroy(); failed=true;
            // Never resume CPU rendering over stale buffers after loss of an in-flight frame.
            if (pending) throw new IllegalStateException("GPU frame lost: "+failure);
            return false;
        }
        draws++;
        if(sprite!=null) spriteDraws++;
        if(depthFactor!=0 || depthUnits!=0) offsetDraws++;
        if(blend==CompositingMode.MODULATE || blend==CompositingMode.MODULATE_X2) multiplyDraws++;
        if (ru.playsoftware.j2meloader.BuildConfig.M3G_GPU && (draws&1023)==1)
            diagnostic("draws="+draws+" units="+units+" textureUploads="+textureUploads
                    +" offsets="+offsetDraws+" multiply="+multiplyDraws+" sprites="+spriteDraws+" empty="+emptyDraws
                    +" uploads="+uploads+" readbacks="+readbacks+" renderer="+renderer);
        if (!resident || !retainTargets) flush();
        return true;
    }

    static synchronized void flush() {
        GpuRasterizer gpu=instance;
        if (gpu==null || gpu.residentPixels==null) return;
        EGLDisplay oldDisplay=eglGetCurrentDisplay(); EGLContext oldContext=eglGetCurrentContext();
        EGLSurface oldDraw=eglGetCurrentSurface(EGL_DRAW), oldRead=eglGetCurrentSurface(EGL_READ);
        RuntimeException error=null;
        try {
            if (!eglMakeCurrent(gpu.display,gpu.surface,gpu.surface,gpu.context))
                throw new IllegalStateException("GPU flush context unavailable");
            gpu.readback();
        } catch (RuntimeException e) { error=e; }
        finally {
            boolean restored=oldDisplay==EGL_NO_DISPLAY
                    ? eglMakeCurrent(gpu.display,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT)
                    : eglMakeCurrent(oldDisplay,oldDraw,oldRead,oldContext);
            if (!restored) error=new IllegalStateException("Unable to restore caller EGL context");
        }
        if (error!=null) {
            failure=error.toString(); destroy(); failed=true;
            throw new IllegalStateException("GPU frame readback failed; CPU buffers not committed",error);
        }
        gpu.colors.asIntBuffer().get(gpu.residentPixels,0,gpu.width*gpu.height);
        if (gpu.depthDirty) gpu.depths.asShortBuffer().get(gpu.residentDepth,0,gpu.width*gpu.height);
        gpu.residentPixels=null; gpu.residentDepth=null; gpu.depthDirty=false;
        readbacks++;
    }

    private static boolean valid(float x,float y,float z,float iw) {
        return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z) && z>=0 && z<=32200
                && Float.isFinite(iw) && iw>0;
    }

    private static float depthOffset(Triangle t,float factor,float units) {
        if(factor==0) return units*1e-7f;
        float dxB=t.xB()-t.xA(),dyB=t.yB()-t.yA(),dxC=t.xC()-t.xA(),dyC=t.yC()-t.yA();
        float det=dxB*dyC-dxC*dyB;
        if(Math.abs(det)<1e-6f) return 0;
        // Match the candidate's slope norm and depth units, not driver polygon-offset units.
        float inv=M3GMath.fastReciprocal(det),dzB=t.zB()-t.zA(),dzC=t.zC()-t.zA();
        float dx=(dzB*dyC-dzC*dyB)*inv,dy=(dxB*dzC-dxC*dzB)*inv;
        return factor*M3GMath.sqrt(dx*dx+dy*dy)+units*1e-7f;
    }

    static synchronized void close() {
        try { flush(); } finally { destroy(); }
        failed=false;
    }

    private static void destroy() {
        spriteState.image=null; spriteState.mapping=null; spriteState.flags=null;
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
        glUseProgram(program);
        for(int unit=0;unit<4;unit++) {
            modes[unit]=glGetUniformLocation(program,"mode["+unit+"]");
            sizes[unit]=glGetUniformLocation(program,"imageSize["+unit+"]");
            formats[unit]=glGetUniformLocation(program,"rgba["+unit+"]");
            mips[unit]=glGetUniformLocation(program,"mip["+unit+"]");
            blendColors[unit]=glGetUniformLocation(program,"blendColor["+unit+"]");
            glUniform1i(glGetUniformLocation(program,"image"+unit),unit);
        }
        targetSizeLocation=glGetUniformLocation(program,"targetSize");
        alphaLocation=glGetUniformLocation(program,"alphaFactor");
        thresholdLocation=glGetUniformLocation(program,"threshold");
        compositeLocation=glGetUniformLocation(program,"composite");
        fogModeLocation=glGetUniformLocation(program,"fogMode");
        parallelFogLocation=glGetUniformLocation(program,"parallelFog");
        fogColorLocation=glGetUniformLocation(program,"fogColor");
        fogParamsLocation=glGetUniformLocation(program,"fogParams");
        spriteLocation=glGetUniformLocation(program,"sprite");
        spriteLayoutLocation=glGetUniformLocation(program,"spriteLayout");
        glUniform1i(glGetUniformLocation(program,"spriteIndices"),4);
        packSizeLocation=glGetUniformLocation(packProgram,"size");
        int[] ids=new int[3]; glGenTextures(3,ids,0);
        color=ids[0]; depth=ids[1]; packed=ids[2]; glGenTextures(4,textureIds,0);
        glGenTextures(1,ids,0); spriteIndices=ids[0];
        glGenFramebuffers(2,ids,0); framebuffer=ids[0]; packFramebuffer=ids[1];
        glGenBuffers(1,ids,0); vbo=ids[0]; glGenVertexArrays(1,ids,0); vao=ids[0];
        check();
    }

    private void render(int[] pixels,short[] zbuffer,int w,int h,Triangle[] triangles,int count,int ox,int oy,
            int left,int top,int right,int bottom,boolean vertexColors,int defaultColor,boolean perspective,
            boolean depthTest,boolean depthWrite,int mask,int alphaFactor,int threshold,Texture2D[] textures,int units,
            Graphics3D.TextureFilter[] filters,Graphics3DPipelines.MipmapMode[] mipModes,
            int blend,float depthFactor,float depthUnits,Fog fog,boolean parallelFog,float[] fogDepth,SpriteState sprite) {
        int packWidth=(w+1)/2;
        glActiveTexture(GL_TEXTURE0); glPixelStorei(GL_UNPACK_ALIGNMENT,1); glPixelStorei(GL_PACK_ALIGNMENT,1);
        if (residentPixels==null) {
            colors=buffer(colors,w*h*4); depths=buffer(depths,packWidth*h*4); depthInput=buffer(depthInput,w*h*2);
            // Keep Android's BGRA byte order in the FBO; shaders swizzle without Java pixel loops.
            colors.asIntBuffer().put(pixels,0,w*h); depthInput.asShortBuffer().put(zbuffer,0,w*h);
            boolean resize=width!=w || height!=h; width=w; height=h;
            targetTexture(color,GL_RGBA8,GL_RGBA,GL_UNSIGNED_BYTE,w,h,colors,resize);
            targetTexture(depth,GL_DEPTH_COMPONENT16,GL_DEPTH_COMPONENT,GL_UNSIGNED_SHORT,w,h,depthInput,resize);
            targetTexture(packed,GL_RGBA8,GL_RGBA,GL_UNSIGNED_BYTE,packWidth,h,null,resize);
            check();
            residentPixels=pixels; residentDepth=zbuffer; uploads++;
        }
        glBindFramebuffer(GL_FRAMEBUFFER,framebuffer);
        glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,color,0);
        glFramebufferTexture2D(GL_FRAMEBUFFER,GL_DEPTH_ATTACHMENT,GL_TEXTURE_2D,depth,0);
        complete();
        geometry=buffer(geometry,count*3*20*4); FloatBuffer data=geometry.asFloatBuffer();
        if(sprite!=null) {
            int z=sprite.flags[13];
            vertex(data,left,top,z,0,null,0,0,1,1);
            vertex(data,right,top,z,0,null,0,0,1,1);
            vertex(data,left,bottom,z,0,null,0,0,1,1);
            vertex(data,right,top,z,0,null,0,0,1,1);
            vertex(data,right,bottom,z,0,null,0,0,1,1);
            vertex(data,left,bottom,z,0,null,0,0,1,1);
        }
        for (int i=0; sprite==null && i<count; i++) {
            Triangle t=triangles[i];
            float offset=depthOffset(t,depthFactor,depthUnits);
            vertex(data,t.xA()+ox,t.yA()+oy,t.zA()+offset,vertexColors?t.colorA():defaultColor,
                    t,0,units,perspective?t.iwA():1,
                    parallelFog?fogDepth[i*3]:t.iwA());
            vertex(data,t.xB()+ox,t.yB()+oy,t.zB()+offset,vertexColors?t.colorB():defaultColor,
                    t,1,units,perspective?t.iwB():1,
                    parallelFog?fogDepth[i*3+1]:t.iwB());
            vertex(data,t.xC()+ox,t.yC()+oy,t.zC()+offset,vertexColors?t.colorC():defaultColor,
                    t,2,units,perspective?t.iwC():1,
                    parallelFog?fogDepth[i*3+2]:t.iwC());
        }
        data.flip(); glBindVertexArray(vao); glBindBuffer(GL_ARRAY_BUFFER,vbo);
        glBufferData(GL_ARRAY_BUFFER,data.remaining()*4,data,GL_STREAM_DRAW);
        for (int i=0; i<7; i++) glEnableVertexAttribArray(i);
        glVertexAttribPointer(0,3,GL_FLOAT,false,80,0);
        glVertexAttribPointer(1,4,GL_FLOAT,false,80,12);
        for (int i=0;i<4;i++) glVertexAttribPointer(2+i,3,GL_FLOAT,false,80,28+i*12);
        glVertexAttribPointer(6,1,GL_FLOAT,false,80,76);
        glViewport(0,0,w,h); glDisable(GL_BLEND); glDisable(GL_CULL_FACE); glDisable(GL_DITHER);
        if (blend!=CompositingMode.REPLACE) {
            glEnable(GL_BLEND); glBlendEquation(GL_FUNC_ADD);
            if(blend==CompositingMode.MODULATE || blend==CompositingMode.MODULATE_X2)
                glBlendFuncSeparate(GL_DST_COLOR,blend==CompositingMode.MODULATE?GL_ZERO:GL_SRC_COLOR,
                        GL_DST_ALPHA,blend==CompositingMode.MODULATE?GL_ZERO:GL_SRC_ALPHA);
            else glBlendFuncSeparate(GL_SRC_ALPHA,blend==CompositingMode.ALPHA?GL_ONE_MINUS_SRC_ALPHA:GL_ONE,
                        GL_ONE,GL_ONE_MINUS_SRC_ALPHA);
        }
        glEnable(GL_SCISSOR_TEST); glScissor(left,top,Math.max(0,right-left),Math.max(0,bottom-top));
        if (depthTest) glEnable(GL_DEPTH_TEST); else glDisable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL); glDepthMask(depthWrite);
        glColorMask((mask&0xffffff)!=0,(mask&0xffffff)!=0,(mask&0xffffff)!=0,(mask&0xff000000)!=0);
        glUseProgram(program); glUniform2f(targetSizeLocation,w,h);
        glUniform1i(alphaLocation,alphaFactor);
        glUniform1i(thresholdLocation,threshold);
        glUniform1i(compositeLocation,blend==CompositingMode.ALPHA || blend==CompositingMode.ALPHA_ADD?1:0);
        glUniform1i(fogModeLocation,fog==null?0:fog.getMode()==Fog.LINEAR?1:2);
        glUniform1i(parallelFogLocation,parallelFog?1:0);
        glUniform1i(spriteLocation,sprite==null?0:1);
        if(sprite!=null) {
            int[] p=sprite.flags; float[] f=sprite.mapping;
            // Preserve CPU DDA rounding at texel boundaries with O(width + height) indices.
            // The GPU still performs all pixel sampling, fog, blending and depth operations.
            int size=Math.max(w,h);
            spriteIndexInput=buffer(spriteIndexInput,size*2*4);
            IntBuffer indices=spriteIndexInput.asIntBuffer();
            float u=f[0];
            for(int x=left;x<right;x++,u+=f[1]) indices.put(x,Math.max(p[8],Math.min(p[8]+p[10]-1,p[8]+(int)u)));
            for(int y=top;y<bottom;y++) {
                float v=(y-sprite.oy+.5f-f[2])*f[3];
                int ty=p[9]+(int)(((p[12]&4)!=0?1-v:v)*p[11]);
                indices.put(size+y,Math.max(p[9],Math.min(p[9]+p[11]-1,ty)));
            }
            glActiveTexture(GL_TEXTURE4);
            targetTexture(spriteIndices,GL_R32I,GL_RED_INTEGER,GL_INT,size,2,spriteIndexInput,spriteIndexWidth!=size);
            spriteIndexWidth=size;
            glUniform4i(spriteLayoutLocation,0,0,p[19],0);
            if((p[12]&8)!=0) {
                glUniform1i(fogModeLocation,3);
                glUniform3f(fogColorLocation,(p[18]>>16)&255,(p[18]>>8)&255,p[18]&255);
            }
        }
        if (fog!=null) {
            int c=fog.getColor();
            glUniform3f(fogColorLocation,(c>>16)&255,(c>>8)&255,c&255);
            glUniform3f(fogParamsLocation,fog.getNearDistance(),fog.getFarDistance(),fog.getDensity());
        }
        for (int unit=0;unit<4;unit++) {
            glUniform1i(modes[unit],unit>=units?0:sprite!=null?Texture2D.FUNC_REPLACE:textures[unit].getBlending());
            glActiveTexture(GL_TEXTURE0+unit);
            if (unit>=units) { glBindTexture(GL_TEXTURE_2D,0); continue; }
            Texture2D tex=sprite==null?textures[unit]:null;
            Image2D image=sprite==null?tex.getImage():sprite.image; int tw=image.getWidth(), th=image.getHeight();
            boolean linear=sprite==null && filters[unit]==Graphics3D.TextureFilters.BILINEAR;
            int mip=sprite!=null || mipModes[unit]==null?0:mipModes[unit]==Graphics3DPipelines.MipmapModes.LINEAR?2:1;
            int levels=mip==0?1:32-Integer.numberOfLeadingZeros(Math.max(tw,th));
            glBindTexture(GL_TEXTURE_2D,textureIds[unit]);
            for (int level=0;level<levels;level++) {
                Image2D source=sprite!=null?image:tex.getImageForLOD(level); int sw=source.getWidth(),sh=source.getHeight();
                if (uploadedImages[unit][level]==source && uploadedRevisions[unit][level]==source.revision) continue;
                textureInput=buffer(textureInput,sw*sh*4); textureInput.asIntBuffer().put(source.image,0,sw*sh);
                glTexImage2D(GL_TEXTURE_2D,level,GL_RGBA8,sw,sh,0,GL_RGBA,GL_UNSIGNED_BYTE,textureInput);
                uploadedImages[unit][level]=source; uploadedRevisions[unit][level]=source.revision; textureUploads++;
            }
            for(int level=levels;level<12;level++) uploadedImages[unit][level]=null;
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAX_LEVEL,levels-1);
            int min=mip==0?(linear?GL_LINEAR:GL_NEAREST):mip==1
                    ?(linear?GL_LINEAR_MIPMAP_NEAREST:GL_NEAREST_MIPMAP_NEAREST)
                    :(linear?GL_LINEAR_MIPMAP_LINEAR:GL_NEAREST_MIPMAP_LINEAR);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,min);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,linear?GL_LINEAR:GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,tex!=null && tex.getWrappingS()==Texture2D.WRAP_REPEAT?GL_REPEAT:GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,tex!=null && tex.getWrappingT()==Texture2D.WRAP_REPEAT?GL_REPEAT:GL_CLAMP_TO_EDGE);
            glUniform2f(sizes[unit],tw,th);
            glUniform1i(formats[unit],image.getFormat());
            glUniform1i(mips[unit],mip);
            int c=tex==null?0:tex.getBlendColor();
            glUniform3f(blendColors[unit],(c>>16)&255,(c>>8)&255,c&255);
        }
        glActiveTexture(GL_TEXTURE0);
        glDrawArrays(GL_TRIANGLES,0,count*3);
        depthDirty|=depthWrite && depthTest;
        check();
    }

    private void readback() {
        int w=width,h=height,packWidth=(w+1)/2;
        glActiveTexture(GL_TEXTURE0);
        glBindFramebuffer(GL_FRAMEBUFFER,framebuffer);
        glReadPixels(0,0,w,h,GL_RGBA,GL_UNSIGNED_BYTE,colors);
        if (depthDirty) {
            // ES3 has no portable depth readback. Pack two 16-bit samples into each RGBA pixel.
            glBindFramebuffer(GL_FRAMEBUFFER,packFramebuffer);
            glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,packed,0); complete();
            glDisable(GL_BLEND); glDisable(GL_DEPTH_TEST); glDisable(GL_SCISSOR_TEST); glColorMask(true,true,true,true);
            glUseProgram(packProgram); glBindTexture(GL_TEXTURE_2D,depth);
            glUniform2i(packSizeLocation,w,h);
            glViewport(0,0,packWidth,h);
            glDrawArrays(GL_TRIANGLES,0,3);
            glReadPixels(0,0,packWidth,h,GL_RGBA,GL_UNSIGNED_BYTE,depths);
        }
        check();
    }

    private static void vertex(FloatBuffer out,float x,float y,float z,int c,Triangle t,int corner,int units,float iw,float fog) {
        out.put(x).put(y).put(z).put((c>>16)&255).put((c>>8)&255).put(c&255).put(c>>>24);
        for(int unit=0;unit<4;unit++) {
            out.put(unit>=units?0:corner==0?t.sA(unit):corner==1?t.sB(unit):t.sC(unit));
            out.put(unit>=units?0:corner==0?t.tA(unit):corner==1?t.tB(unit):t.tC(unit));
            out.put(iw);
        }
        out.put(fog);
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
    private static String vertexShader() {
        StringBuilder s=new StringBuilder("#version 300 es\nlayout(location=0) in vec3 position; layout(location=1) in vec4 color;\n");
        for(int i=0;i<4;i++) s.append("layout(location=").append(i+2).append(") in vec3 uv").append(i).append(";\n");
        s.append("layout(location=6) in float fog; out highp float fogValue;\n")
                .append("uniform vec2 targetSize; out highp vec4 vertexColor; out highp vec3 texCoord[4]; out highp float depthValue;\n")
                .append("void main(){ gl_Position=vec4(position.xy/targetSize*2.0-1.0,0,1); vertexColor=color; depthValue=position.z; fogValue=fog;\n");
        for(int i=0;i<4;i++) s.append("texCoord[").append(i).append("]=uv").append(i).append(";\n");
        return s.append("}").toString();
    }

    private static String fragmentShader() {
        StringBuilder s=new StringBuilder("#version 300 es\nprecision highp float; precision highp int;\n")
                .append("in vec4 vertexColor; in vec3 texCoord[4]; in float depthValue; uniform vec2 imageSize[4];\n")
                .append("uniform int mode[4],rgba[4],mip[4],alphaFactor,threshold,composite,fogMode,parallelFog; out vec4 result;\n")
                .append("uniform vec3 blendColor[4]; in float fogValue; uniform vec3 fogColor,fogParams;\n")
                .append("uniform int sprite; uniform ivec4 spriteLayout; uniform highp isampler2D spriteIndices;\n");
        for(int i=0;i<4;i++) s.append("uniform sampler2D image").append(i).append(";\n");
        s.append("vec4 layer(vec4 c,vec4 t,int mode,int rgba,vec3 b){\n")
                .append("if(mode==226 && rgba<99)return c;\n")
                .append("if(rgba==96){ c.a=mode==228?t.a:floor((c.a*t.a+128.0)/256.0); return c; }\n")
                .append("rgba=(rgba==98 || rgba==100)?1:0;\n")
                .append("if(mode==228){ c.rgb=t.rgb; if(rgba!=0)c.a=t.a; }\n")
                .append("else if(mode==227){ c.rgb=floor((c.rgb*t.rgb+(rgba!=0?128.0:0.0))/256.0); if(rgba!=0)c.a=floor((c.a*t.a+c.a)/256.0); }\n")
                .append("else if(mode==226){ c.rgb=rgba!=0?c.rgb+floor((t.rgb-c.rgb)*t.a/256.0):t.rgb; }\n")
                .append("else { c.rgb=mode==224?min(c.rgb+t.rgb,255.0):c.rgb+floor(((b-c.rgb)*t.rgb+128.0)/256.0); if(rgba!=0)c.a=floor((c.a*t.a+128.0)/256.0); } return c; }\n")
                .append("void main(){ vec4 c=floor(clamp(vertexColor,0.0,255.0)+0.0001);\n")
                .append("if(sprite!=0){ ivec2 p=ivec2(texelFetch(spriteIndices,ivec2(int(gl_FragCoord.x),0),0).r,\n")
                .append("texelFetch(spriteIndices,ivec2(int(gl_FragCoord.y),1),0).r);\n")
                .append("c=floor(texelFetch(image0,p,0).bgra*255.0+0.5); } else {\n");
        // ES3 sampler selection is deliberately static, including on mobile drivers.
        for(int i=0;i<4;i++) s.append("{ int u=").append(i).append("; if(mode[u]!=0){\n")
                .append("vec3 p=texCoord[u]; vec2 uv=p.xy/p.z; float lod=0.0;\n")
                .append("if(mip[u]!=0){ vec2 dx=abs((dFdx(p.xy)-uv*dFdx(p.z))/p.z),dy=abs((dFdy(p.xy)-uv*dFdy(p.z))/p.z);\n")
                .append("lod=floor(0.5*log2(max(1.0,max(dot(dx,dx),dot(dy,dy))))*256.0)/256.0; if(mip[u]==1)lod=floor(lod+127.0/256.0); }\n")
                .append("vec4 t=floor(textureLod(image").append(i).append(",uv/imageSize[u],lod).bgra*255.0+0.5);\n")
                .append("c=layer(c,t,mode[u],rgba[u],blendColor[u]); }}\n");
        return s.append(
            "} float a=floor(c.a*float(alphaFactor)/256.0); if(a<float(threshold) || (sprite!=0 && a==0.0))discard; if(a==0.0)c.a=255.0;\n"
            +"if(fogMode!=0){ float z=parallelFog!=0?fogValue:1.0/fogValue;\n"
            +"float f=fogMode==1?(fogParams.y-z)/(fogParams.y-fogParams.x):exp(-fogParams.z*z);\n"
            +"float q=fogMode==3?float(spriteLayout.z):floor(clamp(f*256.0,0.0,255.0)); c.rgb=floor((c.rgb*q+fogColor*(256.0-q))/256.0); }\n"
            +"if(composite!=0)c.a=a; result=c.bgra/255.0; gl_FragDepth=floor(depthValue)/65535.0; }").toString();
    }
    private static final String VERTEX=vertexShader(), FRAGMENT=fragmentShader();
    private static final String PACK_VERTEX="#version 300 es\nvoid main(){\n"
            +"vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2); gl_Position=vec4(p*2.0-1.0,0,1); }";
    private static final String PACK_FRAGMENT="#version 300 es\nprecision highp float; precision highp int;\n"
            +"uniform highp sampler2D image; uniform ivec2 size; out vec4 result;\n"
            +"int sampleDepth(int i){ if(i>=size.x*size.y)return 0;\n"
            +"return int(floor(texelFetch(image,ivec2(i%size.x,i/size.x),0).r*65535.0+0.5)); }\n"
            +"void main(){ int i=2*(int(gl_FragCoord.x)+int(gl_FragCoord.y)*((size.x+1)/2));\n"
            +"int a=sampleDepth(i),b=sampleDepth(i+1); result=vec4(a&255,(a>>8)&255,b&255,(b>>8)&255)/255.0; }";
}
