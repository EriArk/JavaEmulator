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
    static boolean batchSubmission = true;
    static boolean textureCacheEnabled = true;
    static int textureCacheBytes = GpuTextureCache.UNIT_BYTES;
    static long draws, uploads, readbacks, textureUploads, emptyDraws, offsetDraws, multiplyDraws, spriteDraws;
    static long recoveries, replayedDraws, journalBarriers;
    static long submissions, submittedDraws, geometryUploads;
    static final GpuFrame frame = new GpuFrame();
    // Research-only deterministic loss points: before draw, after draw, between readbacks.
    static int lossPoint, lossesRemaining;
    static String renderer = "not initialized", failure = "";
    private static GpuRasterizer instance;
    private static boolean failed;
    private static boolean frameLost;
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
    private final GpuTextureCache textures = new GpuTextureCache(textureCacheBytes);
    static synchronized int cachedTextureBytes() { return instance==null?0:instance.textures.bytes(); }
    private final int[] modes=new int[4], sizes=new int[4], formats=new int[4], mips=new int[4], blendColors=new int[4];
    private int targetSizeLocation, alphaLocation, thresholdLocation, compositeLocation;
    private int fogModeLocation, parallelFogLocation, fogColorLocation, fogParamsLocation, packSizeLocation;
    private int spriteLocation, spriteLayoutLocation, spriteIndices, spriteIndexWidth;
    private ByteBuffer colors, depths, depthInput, textureInput, batchGeometry;
    private int submitted;
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
    private static float[] vertexStaging = new float[0];

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
        GpuFrame.Draw command=new GpuFrame.Draw(); command.count=count; command.sprite=sprite!=null;
        command.textures=new GpuFrame.Texture[units];
        for(int unit=0;unit<units;unit++) command.textures[unit]=sprite!=null
                ?new GpuFrame.Texture(null,sprite.image,null,null)
                :new GpuFrame.Texture(textures[unit],null,filters[unit],mipModes[unit]);
        long required=frame.required(command,w,h);
        if(frame.bytes+required>frame.byteLimit || frame.draws.size()>=GpuFrame.MAX_DRAWS) {
            if(!frame.draws.isEmpty()) { flush(); journalBarriers++; }
            if(frame.required(command,w,h)>frame.byteLimit) return false;
        }
        prepare(command,w,h,triangles,count,ox,oy,left,top,right,bottom,vertexColors,defaultColor,
                perspective,depthTest,depthWrite,mask,alphaFactor,threshold,units,blend,depthFactor,
                depthUnits,fog,parallelFog,fogDepth,sprite);
        frame.capture(command,pixels,zbuffer,w,h);
        // Submit the first draw immediately to preserve safe startup fallback. Later
        // draws share one EGL scope and vertex upload at the next CPU access barrier.
        if((frame.draws.size()==1 || !batchSubmission) && !execute(false)) return false;
        draws++;
        if(sprite!=null) spriteDraws++;
        if(depthFactor!=0 || depthUnits!=0) offsetDraws++;
        if(blend==CompositingMode.MODULATE || blend==CompositingMode.MODULATE_X2) multiplyDraws++;
        if (ru.playsoftware.j2meloader.BuildConfig.M3G_GPU && (draws&1023)==1)
            diagnostic("draws="+draws+" units="+units+" textureUploads="+textureUploads
                    +" textureHits="+GpuTextureCache.hits+" textureEvictions="+GpuTextureCache.evictions
                    +" textureCacheBytes="+cachedTextureBytes()
                    +" offsets="+offsetDraws+" multiply="+multiplyDraws+" sprites="+spriteDraws+" empty="+emptyDraws
                    +" uploads="+uploads+" readbacks="+readbacks+" recoveries="+recoveries+" renderer="+renderer);
        if (!resident || !retainTargets) flush();
        return true;
    }

    static synchronized void flush() {
        if(frameLost) throw new IllegalStateException("GPU frame unavailable; reset required: "+failure);
        if(frame.draws.isEmpty()) return;
        execute(true);
        GpuRasterizer gpu=instance;
        gpu.colors.asIntBuffer().get(frame.pixels,0,frame.width*frame.height);
        if (gpu.depthDirty) gpu.depths.asShortBuffer().get(frame.depth,0,frame.width*frame.height);
        gpu.residentPixels=null; gpu.residentDepth=null; gpu.depthDirty=false; gpu.submitted=0;
        frame.clear(); readbacks++;
    }

    private static final class ContextLost extends IllegalStateException {
        ContextLost(String message) { super(message); }
    }

    private static void current(GpuRasterizer gpu) {
        if(eglMakeCurrent(gpu.display,gpu.surface,gpu.surface,gpu.context)) return;
        int error=eglGetError();
        if(error==EGL_CONTEXT_LOST || error==EGL_BAD_CONTEXT || error==EGL_BAD_SURFACE)
            throw new ContextLost("Owned EGL context unavailable: "+error);
        throw new IllegalStateException("eglMakeCurrent: "+error);
    }

    private static void injectLoss(int point) {
        if(lossPoint!=point || lossesRemaining<=0) return;
        lossesRemaining--;
        // Destroy real owned GL objects, rather than merely pretending a draw failed.
        destroyGpu();
        throw new ContextLost("Injected context loss at "+point);
    }

    private static boolean execute(boolean read) {
        EGLDisplay oldDisplay=eglGetCurrentDisplay(); EGLContext oldContext=eglGetCurrentContext();
        EGLSurface oldDraw=eglGetCurrentSurface(EGL_DRAW), oldRead=eglGetCurrentSurface(EGL_READ);
        RuntimeException error=null;
        boolean restoreFailed=false;
        boolean hadWork=read || frame.draws.size()>1;
        EGLDisplay ownedDisplay=instance==null?EGL_NO_DISPLAY:instance.display;
        try {
            for(int attempt=0;attempt<2;attempt++) {
                try {
                    if(instance==null) { instance=new GpuRasterizer(); instance.initialize(); }
                    ownedDisplay=instance.display; current(instance);
                    submissions++;
                    int start=instance.submitted;
                    instance.uploadGeometry(start);
                    int firstVertex=0;
                    GpuFrame.Draw previous=null;
                    for(int i=start;i<frame.draws.size();i++) {
                        GpuFrame.Draw saved=frame.draws.get(i);
                        injectLoss(1); instance.render(saved,firstVertex,previous); injectLoss(2);
                        previous=saved;
                        firstVertex+=saved.count*3;
                        instance.submitted=i+1; submittedDraws++;
                        if(attempt!=0) replayedDraws++;
                    }
                    if(read) instance.readback();
                    if(attempt!=0) recoveries++;
                    break;
                } catch(ContextLost lost) {
                    destroyGpu();
                    if(attempt!=0) throw lost;
                }
            }
        } catch (RuntimeException e) { error=e; }
        finally {
            if(instance!=null) ownedDisplay=instance.display;
            boolean restored=oldDisplay!=EGL_NO_DISPLAY
                    ? eglMakeCurrent(oldDisplay,oldDraw,oldRead,oldContext)
                    : ownedDisplay==EGL_NO_DISPLAY || eglMakeCurrent(ownedDisplay,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT);
            if (!restored) {
                restoreFailed=true;
                error=new IllegalStateException("Unable to restore caller EGL context");
            }
        }
        if (error!=null) {
            failure=error.toString();
            boolean pending=restoreFailed || hadWork || instance!=null && instance.residentPixels!=null;
            destroyGpu(); frame.close(); failed=true; frameLost=pending;
            if(pending) throw new IllegalStateException("GPU recovery failed; CPU buffers not committed",error);
            return false;
        }
        return true;
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
        try { if(!frameLost) flush(); }
        finally {
            destroyGpu(); frame.close(); failed=false; frameLost=false;
            spriteState.image=null; spriteState.mapping=null; spriteState.flags=null;
            vertexStaging=new float[0];
        }
    }

    private static void destroyGpu() {
        if (instance==null) return;
        if(eglGetCurrentContext().equals(instance.context) && instance.display!=EGL_NO_DISPLAY)
            eglMakeCurrent(instance.display,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT);
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
        if (context==EGL_NO_CONTEXT || surface==EGL_NO_SURFACE)
            throw new IllegalStateException("Create GPU context");
        current(this);
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
        color=ids[0]; depth=ids[1]; packed=ids[2];
        glGenTextures(1,ids,0); spriteIndices=ids[0];
        glGenFramebuffers(2,ids,0); framebuffer=ids[0]; packFramebuffer=ids[1];
        glGenBuffers(1,ids,0); vbo=ids[0]; glGenVertexArrays(1,ids,0); vao=ids[0];
        glBindVertexArray(vao); glBindBuffer(GL_ARRAY_BUFFER,vbo);
        for(int i=0;i<7;i++) glEnableVertexAttribArray(i);
        glVertexAttribPointer(0,3,GL_FLOAT,false,80,0);
        glVertexAttribPointer(1,4,GL_FLOAT,false,80,12);
        for(int i=0;i<4;i++) glVertexAttribPointer(2+i,3,GL_FLOAT,false,80,28+i*12);
        glVertexAttribPointer(6,1,GL_FLOAT,false,80,76);
        glDisable(GL_CULL_FACE); glDisable(GL_DITHER); glDepthFunc(GL_LEQUAL);
        check();
    }

    private static void prepare(GpuFrame.Draw command,int w,int h,Triangle[] triangles,int count,int ox,int oy,
            int left,int top,int right,int bottom,boolean vertexColors,int defaultColor,boolean perspective,
            boolean depthTest,boolean depthWrite,int mask,int alphaFactor,int threshold,int units,
            int blend,float depthFactor,float depthUnits,Fog fog,boolean parallelFog,float[] fogDepth,SpriteState sprite) {
        command.left=left; command.top=top; command.right=right; command.bottom=bottom;
        command.depthTest=depthTest; command.depthWrite=depthWrite; command.mask=mask;
        command.alpha=alphaFactor; command.threshold=threshold; command.blend=blend;
        command.parallelFog=parallelFog;
        command.fogMode=fog==null?0:fog.getMode()==Fog.LINEAR?1:2;
        if(fog!=null) {
            command.fogColor=fog.getColor(); command.fogNear=fog.getNearDistance();
            command.fogFar=fog.getFarDistance(); command.fogDensity=fog.getDensity();
        }
        command.geometry=frame.acquire(count*240);
        int floats=count*60;
        if(vertexStaging.length<floats) vertexStaging=new float[floats];
        float[] data=vertexStaging;
        if(sprite!=null) {
            int z=sprite.flags[13];
            vertex(data,0,left,top,z,0,null,0,0,1,1);
            vertex(data,20,right,top,z,0,null,0,0,1,1);
            vertex(data,40,left,bottom,z,0,null,0,0,1,1);
            vertex(data,60,right,top,z,0,null,0,0,1,1);
            vertex(data,80,right,bottom,z,0,null,0,0,1,1);
            vertex(data,100,left,bottom,z,0,null,0,0,1,1);
        }
        for (int i=0; sprite==null && i<count; i++) {
            Triangle t=triangles[i];
            float offset=depthOffset(t,depthFactor,depthUnits);
            vertex(data,i*60,t.xA()+ox,t.yA()+oy,t.zA()+offset,vertexColors?t.colorA():defaultColor,
                    t,0,units,perspective?t.iwA():1,
                    parallelFog?fogDepth[i*3]:t.iwA());
            vertex(data,i*60+20,t.xB()+ox,t.yB()+oy,t.zB()+offset,vertexColors?t.colorB():defaultColor,
                    t,1,units,perspective?t.iwB():1,
                    parallelFog?fogDepth[i*3+1]:t.iwB());
            vertex(data,i*60+40,t.xC()+ox,t.yC()+oy,t.zC()+offset,vertexColors?t.colorC():defaultColor,
                    t,2,units,perspective?t.iwC():1,
                    parallelFog?fogDepth[i*3+2]:t.iwC());
        }
        // One bulk transfer instead of a checked direct-buffer write per component.
        // Each queued command still owns its snapshot for delayed submission/replay.
        command.geometry.asFloatBuffer().put(data,0,floats);
        if(sprite!=null) {
            int[] p=sprite.flags; float[] f=sprite.mapping;
            int size=Math.max(w,h);
            command.indices=frame.acquire(size*8); IntBuffer indices=command.indices.asIntBuffer();
            float u=f[0];
            for(int x=left;x<right;x++,u+=f[1]) indices.put(x,Math.max(p[8],Math.min(p[8]+p[10]-1,p[8]+(int)u)));
            for(int y=top;y<bottom;y++) {
                float v=(y-sprite.oy+.5f-f[2])*f[3];
                int ty=p[9]+(int)(((p[12]&4)!=0?1-v:v)*p[11]);
                indices.put(size+y,Math.max(p[9],Math.min(p[9]+p[11]-1,ty)));
            }
            command.spriteFog=p[19];
            if((p[12]&8)!=0) { command.fogMode=3; command.fogColor=p[18]; }
        }
    }

    private void uploadGeometry(int start) {
        int end=frame.draws.size();
        if(start==end) return;
        ByteBuffer data;
        if(end-start==1) data=frame.draws.get(start).geometry;
        else {
            int bytes=0;
            for(int i=start;i<end;i++) bytes+=frame.draws.get(i).geometry.remaining();
            batchGeometry=buffer(batchGeometry,bytes);
            for(int i=start;i<end;i++) {
                ByteBuffer source=frame.draws.get(i).geometry;
                batchGeometry.put(source); source.rewind();
            }
            batchGeometry.flip(); data=batchGeometry;
        }
        glBindVertexArray(vao); glBindBuffer(GL_ARRAY_BUFFER,vbo);
        glBufferData(GL_ARRAY_BUFFER,data.remaining(),data,GL_STREAM_DRAW);
        check(); geometryUploads++;
    }

    private void render(GpuFrame.Draw command,int firstVertex,GpuFrame.Draw previous) {
        int w=frame.width,h=frame.height,packWidth=(w+1)/2;
        int count=command.count,blend=command.blend,mask=command.mask;
        if(previous==null) {
            glActiveTexture(GL_TEXTURE0); glPixelStorei(GL_UNPACK_ALIGNMENT,1); glPixelStorei(GL_PACK_ALIGNMENT,1);
        }
        if (residentPixels==null) {
            colors=buffer(colors,w*h*4); depths=buffer(depths,packWidth*h*4); depthInput=buffer(depthInput,w*h*2);
            colors.asIntBuffer().put(frame.pixels,0,w*h); depthInput.asShortBuffer().put(frame.depth,0,w*h);
            boolean resize=width!=w || height!=h; width=w; height=h;
            targetTexture(color,GL_RGBA8,GL_RGBA,GL_UNSIGNED_BYTE,w,h,colors,resize);
            targetTexture(depth,GL_DEPTH_COMPONENT16,GL_DEPTH_COMPONENT,GL_UNSIGNED_SHORT,w,h,depthInput,resize);
            targetTexture(packed,GL_RGBA8,GL_RGBA,GL_UNSIGNED_BYTE,packWidth,h,null,resize);
            if(resize) {
                glBindFramebuffer(GL_FRAMEBUFFER,framebuffer);
                glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,color,0);
                glFramebufferTexture2D(GL_FRAMEBUFFER,GL_DEPTH_ATTACHMENT,GL_TEXTURE_2D,depth,0); complete();
                glBindFramebuffer(GL_FRAMEBUFFER,packFramebuffer);
                glFramebufferTexture2D(GL_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,packed,0); complete();
            }
            check(); residentPixels=frame.pixels; residentDepth=frame.depth; uploads++;
        }
        // Reuse state only inside this uninterrupted submission. A new EGL scope,
        // readback or context replay always starts with a complete setup.
        if(previous==null) {
            glBindFramebuffer(GL_FRAMEBUFFER,framebuffer);
            glBindVertexArray(vao); glBindBuffer(GL_ARRAY_BUFFER,vbo);
            glViewport(0,0,w,h); glEnable(GL_SCISSOR_TEST);
            glUseProgram(program); glUniform2f(targetSizeLocation,w,h);
        }
        if(previous==null || previous.blend!=blend) {
            if(blend==CompositingMode.REPLACE) glDisable(GL_BLEND);
            else {
                glEnable(GL_BLEND); glBlendEquation(GL_FUNC_ADD);
                if(blend==CompositingMode.MODULATE || blend==CompositingMode.MODULATE_X2)
                    glBlendFuncSeparate(GL_DST_COLOR,blend==CompositingMode.MODULATE?GL_ZERO:GL_SRC_COLOR,
                            GL_DST_ALPHA,blend==CompositingMode.MODULATE?GL_ZERO:GL_SRC_ALPHA);
                else glBlendFuncSeparate(GL_SRC_ALPHA,blend==CompositingMode.ALPHA?GL_ONE_MINUS_SRC_ALPHA:GL_ONE,
                            GL_ONE,GL_ONE_MINUS_SRC_ALPHA);
            }
            glUniform1i(compositeLocation,blend==CompositingMode.ALPHA || blend==CompositingMode.ALPHA_ADD?1:0);
        }
        if(previous==null || previous.left!=command.left || previous.top!=command.top
                || previous.right!=command.right || previous.bottom!=command.bottom)
            glScissor(command.left,command.top,Math.max(0,command.right-command.left),Math.max(0,command.bottom-command.top));
        if(previous==null || previous.depthTest!=command.depthTest) {
            if(command.depthTest) glEnable(GL_DEPTH_TEST); else glDisable(GL_DEPTH_TEST);
        }
        if(previous==null || previous.depthWrite!=command.depthWrite) glDepthMask(command.depthWrite);
        // Reassert even unchanged masks: skipping this changes mixed-material
        // output on the Retroid GPU (covered by GpuBatchTest's prefix checks).
        glColorMask((mask&0xffffff)!=0,(mask&0xffffff)!=0,(mask&0xffffff)!=0,(mask&0xff000000)!=0);
        if(previous==null || previous.alpha!=command.alpha) glUniform1i(alphaLocation,command.alpha);
        if(previous==null || previous.threshold!=command.threshold) glUniform1i(thresholdLocation,command.threshold);
        if(previous==null || previous.fogMode!=command.fogMode) glUniform1i(fogModeLocation,command.fogMode);
        if(previous==null || previous.parallelFog!=command.parallelFog) glUniform1i(parallelFogLocation,command.parallelFog?1:0);
        if(previous==null || previous.sprite!=command.sprite) glUniform1i(spriteLocation,command.sprite?1:0);
        if(command.sprite) {
            int size=Math.max(w,h);
            glActiveTexture(GL_TEXTURE4);
            targetTexture(spriteIndices,GL_R32I,GL_RED_INTEGER,GL_INT,size,2,command.indices,spriteIndexWidth!=size);
            spriteIndexWidth=size;
            glUniform4i(spriteLayoutLocation,0,0,command.spriteFog,0);
        }
        if (command.fogMode!=0 && (previous==null || previous.fogMode==0 || previous.fogColor!=command.fogColor
                || previous.fogNear!=command.fogNear || previous.fogFar!=command.fogFar || previous.fogDensity!=command.fogDensity)) {
            int c=command.fogColor;
            glUniform3f(fogColorLocation,(c>>16)&255,(c>>8)&255,c&255);
            glUniform3f(fogParamsLocation,command.fogNear,command.fogFar,command.fogDensity);
        }
        for (int unit=0;unit<4;unit++) {
            GpuFrame.Texture tex=unit<command.textures.length?command.textures[unit]:null;
            GpuFrame.Texture old=previous!=null && unit<previous.textures.length?previous.textures[unit]:null;
            if(previous==null || (old==null?0:old.mode)!=(tex==null?0:tex.mode)) glUniform1i(modes[unit],tex==null?0:tex.mode);
            glActiveTexture(GL_TEXTURE0+unit);
            if(tex==null) { glBindTexture(GL_TEXTURE_2D,0); continue; }
            GpuFrame.Image image=tex.levels[0]; int tw=image.width,th=image.height;
            boolean linear=tex.linear; int mip=tex.mip,levels=tex.levels.length;
            GpuTextureCache.Entry cached=textures.select(unit,tex,textureCacheEnabled);
            glBindTexture(GL_TEXTURE_2D,cached.id);
            for (int level=0;level<levels;level++) {
                GpuFrame.Image source=tex.levels[level]; int sw=source.width,sh=source.height;
                if (cached.images[level]==source.source && cached.revisions[level]==source.revision) continue;
                textureInput=buffer(textureInput,sw*sh*4); textureInput.asIntBuffer().put(source.pixels,0,sw*sh);
                glTexImage2D(GL_TEXTURE_2D,level,GL_RGBA8,sw,sh,0,GL_RGBA,GL_UNSIGNED_BYTE,textureInput);
                cached.images[level]=source.source; cached.revisions[level]=source.revision; textureUploads++;
            }
            int min=mip==0?(linear?GL_LINEAR:GL_NEAREST):mip==1
                    ?(linear?GL_LINEAR_MIPMAP_NEAREST:GL_NEAREST_MIPMAP_NEAREST)
                    :(linear?GL_LINEAR_MIPMAP_LINEAR:GL_NEAREST_MIPMAP_LINEAR);
            int key=levels | (linear?32:0) | (mip<<6) | (tex.repeatS?256:0) | (tex.repeatT?512:0);
            if(cached.samplerKey!=key) {
                glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAX_LEVEL,levels-1);
                glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,min);
                glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,linear?GL_LINEAR:GL_NEAREST);
                glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,tex.repeatS?GL_REPEAT:GL_CLAMP_TO_EDGE);
                glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,tex.repeatT?GL_REPEAT:GL_CLAMP_TO_EDGE);
                cached.samplerKey=key;
            }
            if(old==null || old.levels[0].width!=tw || old.levels[0].height!=th) glUniform2f(sizes[unit],tw,th);
            if(old==null || old.levels[0].format!=image.format) glUniform1i(formats[unit],image.format);
            if(old==null || old.mip!=mip) glUniform1i(mips[unit],mip);
            int c=tex.blendColor;
            if(old==null || old.blendColor!=c) glUniform3f(blendColors[unit],(c>>16)&255,(c>>8)&255,c&255);
        }
        glActiveTexture(GL_TEXTURE0);
        glDrawArrays(GL_TRIANGLES,firstVertex,count*3);
        depthDirty|=command.depthWrite && command.depthTest;
        check();
    }

    private void readback() {
        int w=width,h=height,packWidth=(w+1)/2;
        glActiveTexture(GL_TEXTURE0);
        glBindFramebuffer(GL_FRAMEBUFFER,framebuffer);
        glReadPixels(0,0,w,h,GL_RGBA,GL_UNSIGNED_BYTE,colors);
        injectLoss(3);
        if (depthDirty) {
            // ES3 has no portable depth readback. Pack two 16-bit samples into each RGBA pixel.
            glBindFramebuffer(GL_FRAMEBUFFER,packFramebuffer);
            glDisable(GL_BLEND); glDisable(GL_DEPTH_TEST); glDisable(GL_SCISSOR_TEST); glColorMask(true,true,true,true);
            glUseProgram(packProgram); glBindTexture(GL_TEXTURE_2D,depth);
            glUniform2i(packSizeLocation,w,h);
            glViewport(0,0,packWidth,h);
            glDrawArrays(GL_TRIANGLES,0,3);
            glReadPixels(0,0,packWidth,h,GL_RGBA,GL_UNSIGNED_BYTE,depths);
        }
        check();
    }

    private static void vertex(float[] out,int offset,float x,float y,float z,int c,Triangle t,int corner,int units,float iw,float fog) {
        out[offset++]=x; out[offset++]=y; out[offset++]=z;
        out[offset++]=(c>>16)&255; out[offset++]=(c>>8)&255; out[offset++]=c&255; out[offset++]=c>>>24;
        for(int unit=0;unit<4;unit++) {
            out[offset++]=unit>=units?0:corner==0?t.sA(unit):corner==1?t.sB(unit):t.sC(unit);
            out[offset++]=unit>=units?0:corner==0?t.tA(unit):corner==1?t.tB(unit):t.tC(unit);
            out[offset++]=iw;
        }
        out[offset]=fog;
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
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE) {
            check();
            throw new IllegalStateException("Incomplete GPU target");
        }
    }
    private static void check() {
        int error=glGetError();
        if(error==0x0507) throw new ContextLost("GL_CONTEXT_LOST");
        if (error!=GL_NO_ERROR) throw new IllegalStateException("GLES error "+error);
    }
    private static int shader(int kind,String source) {
        int id=glCreateShader(kind); glShaderSource(id,source); glCompileShader(id);
        int[] ok=new int[1]; glGetShaderiv(id,GL_COMPILE_STATUS,ok,0);
        if (ok[0]==0) { check(); throw new IllegalStateException(glGetShaderInfoLog(id)); }
        return id;
    }
    private static int program(String vertex,String fragment) {
        int id=glCreateProgram(), vs=shader(GL_VERTEX_SHADER,vertex), fs=shader(GL_FRAGMENT_SHADER,fragment);
        glAttachShader(id,vs); glAttachShader(id,fs); glLinkProgram(id); glDeleteShader(vs); glDeleteShader(fs);
        int[] ok=new int[1]; glGetProgramiv(id,GL_LINK_STATUS,ok,0);
        if (ok[0]==0) { check(); throw new IllegalStateException(glGetProgramInfoLog(id)); }
        return id;
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
            "} float a=floor(c.a*float(alphaFactor)/256.0); if(a<float(threshold) || (sprite!=0 && a==0.0))discard; c.a=a;\n"
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
