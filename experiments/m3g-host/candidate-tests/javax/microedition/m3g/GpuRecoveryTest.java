/* SPDX-License-Identifier: GPL-3.0-or-later */
package javax.microedition.m3g;

import android.opengl.*;
import java.util.Arrays;
import javax.microedition.lcdui.Image;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;
import static android.opengl.EGL14.*;

public class GpuRecoveryTest {
    @After public void restore() {
        GpuRasterizer.lossesRemaining=0; GpuRasterizer.lossPoint=0;
        GpuRasterizer.close(); Graphics3D.getInstance().releaseTarget();
        GpuRasterizer.enabled=false; GpuRasterizer.retainTargets=true;
        GpuRasterizer.frame.byteLimit=GpuFrame.MAX_BYTES;
    }

    @Test public void lostContextReplaysPendingColorAndDepthExactlyOnce() {
        int[][] expected=sprites(0);
        for(int point=1;point<=3;point++) {
            long recoveries=GpuRasterizer.recoveries,replays=GpuRasterizer.replayedDraws;
            int[][] actual=sprites(point);
            assertArrayEquals("color at loss point "+point,expected[0],actual[0]);
            assertArrayEquals("depth at loss point "+point,expected[1],actual[1]);
            assertEquals(recoveries+1,GpuRasterizer.recoveries);
            assertEquals(replays+2,GpuRasterizer.replayedDraws);
        }
    }

    private int[][] sprites(int point) {
        GpuRasterizer.close(); GpuRasterizer.enabled=true;
        int w=47,h=35; int[] pixels=new int[w*h]; short[] depth=new short[w*h];
        Arrays.fill(pixels,0x80406080); Arrays.fill(depth,(short)32200);
        Image2D image=new Image2D(Image2D.RGBA,1,1);
        image.set(0,0,1,1,new byte[]{-96,64,32,-128});
        int[] flags=flags(w,h,1000); float[] mapping={0,0,0,0};
        assertTrue(GpuRasterizer.sprite(pixels,depth,w,h,image,mapping,flags,0,0,CompositingMode.ALPHA,true));
        image.set(0,0,1,1,new byte[]{32,96,-64,-96});
        flags[0]=7; flags[2]=5; flags[13]=500;
        GpuRasterizer.lossPoint=point; GpuRasterizer.lossesRemaining=point==0?0:1;
        assertTrue(GpuRasterizer.sprite(pixels,depth,w,h,image,mapping,flags,0,0,CompositingMode.ALPHA,true));
        // No partial color/depth commit, even after losing the original framebuffer.
        for(int i=0;i<pixels.length;i++) { assertEquals(0x80406080,pixels[i]); assertEquals(32200,depth[i]); }
        GpuRasterizer.flush(); int[] copiedDepth=new int[depth.length];
        for(int i=0;i<depth.length;i++) copiedDepth[i]=depth[i];
        assertEquals(1000,depth[2*w+2]); assertEquals(500,depth[10*w+10]);
        assertNotEquals(0x80406080,pixels[10*w+10]);
        return new int[][]{pixels,copiedDepth};
    }

    @Test public void meshReplaySnapshotsMutableTexturesFogGeometryAndSpriteState() {
        int[] expected=meshScene(false); long count=GpuRasterizer.recoveries;
        assertArrayEquals(expected,meshScene(true)); assertEquals(count+1,GpuRasterizer.recoveries);
    }

    private int[] meshScene(boolean lose) {
        Graphics3D.initGraphics3D(); Graphics3D g=Graphics3D.getInstance();
        Image target=Image.createImage(47,35); g.bindTarget(target.getGraphics(),true,0);
        Camera camera=new Camera(); camera.setParallel(4,47f/35,1,10); g.setCamera(camera,new Transform());
        Background bg=new Background(); bg.setColor(0xff123456); g.clear(bg);
        Image2D image=new Image2D(Image2D.RGBA,2,2);
        byte[] data={-1,32,64,-128,32,-1,64,-128,32,64,-1,-128,-1,-1,64,-128}; image.set(0,0,2,2,data);
        Texture2D texture=new Texture2D(image); texture.setBlending(Texture2D.FUNC_REPLACE);
        texture.setFiltering(Texture2D.FILTER_LINEAR,Texture2D.FILTER_LINEAR);
        VertexArray points=new VertexArray(4,3,1); points.set(0,4,new byte[]{-2,1,0,-2,-1,0,1,1,0,1,-1,0});
        VertexArray uv=new VertexArray(4,2,1); uv.set(0,4,new byte[]{0,0,0,4,4,0,4,4});
        VertexBuffer vertices=new VertexBuffer(); vertices.setPositions(points,1,null);
        vertices.setTexCoords(0,uv,1,null); vertices.setDefaultColor(0xff408060);
        Appearance style=new Appearance(); PolygonMode poly=new PolygonMode(); poly.setCulling(PolygonMode.CULL_NONE);
        style.setPolygonMode(poly); style.setTexture(0,texture);
        CompositingMode blend=new CompositingMode(); blend.setBlending(CompositingMode.ALPHA); style.setCompositingMode(blend);
        Fog fog=new Fog(); fog.setMode(Fog.LINEAR); fog.setLinear(1,6); fog.setColor(0x806020); style.setFog(fog);
        Mesh mesh=new Mesh(vertices,new TriangleStripArray(0,new int[]{4}),style);
        Transform t=new Transform(); t.postTranslate(0,0,-3); GpuRasterizer.enabled=true;
        long draws=GpuRasterizer.draws; g.render(mesh,t);
        image.set(0,0,1,1,new byte[]{32,64,-96,-64}); fog.setColor(0x20a080); fog.setLinear(2,8);
        texture.setBlending(Texture2D.FUNC_BLEND); texture.setBlendColor(0x906040);
        texture.setWrapping(Texture2D.WRAP_CLAMP,Texture2D.WRAP_CLAMP);
        texture.setFiltering(Texture2D.FILTER_BASE_LEVEL,Texture2D.FILTER_NEAREST);
        points.set(0,4,new byte[]{-1,1,0,-1,-1,0,2,1,0,2,-1,0}); t.postTranslate(.2f,0,1);
        g.render(mesh,t);
        Sprite3D sprite=new Sprite3D(true,image,style); sprite.setScale(1.3f,.8f,1); g.render(sprite,t);
        assertEquals(draws+3,GpuRasterizer.draws); assertEquals(3,GpuRasterizer.frame.draws.size());
        short[] depth=GpuRasterizer.frame.depth;
        if(lose) { GpuRasterizer.lossPoint=3; GpuRasterizer.lossesRemaining=1; }
        g.releaseTarget();
        int[] result=new int[47*35*2]; target.getBitmap().getPixels(result,0,47,0,0,47,35);
        for(int i=0;i<depth.length && i<47*35;i++) result[47*35+i]=depth[i];
        return result;
    }

    @Test public void journalBudgetCheckpointsAndLargeCommandsFallBack() {
        GpuRasterizer.close(); GpuRasterizer.enabled=true;
        int w=32,h=32; int[] pixels=new int[w*h]; short[] depth=new short[w*h]; Arrays.fill(depth,(short)32200);
        Image2D image=new Image2D(Image2D.RGB,1,1,new byte[]{32,96,64});
        int[] flags=flags(w,h,500); float[] mapping={0,0,0,0};
        long barriers=GpuRasterizer.journalBarriers, reads=GpuRasterizer.readbacks;
        GpuRasterizer.frame.byteLimit=2000;
        for(int i=0;i<3;i++) assertTrue(GpuRasterizer.sprite(pixels,depth,w,h,image,mapping,flags,0,0,CompositingMode.REPLACE,true));
        assertEquals(barriers+2,GpuRasterizer.journalBarriers); assertEquals(reads+2,GpuRasterizer.readbacks);
        GpuRasterizer.frame.byteLimit=1;
        assertFalse(GpuRasterizer.sprite(pixels,depth,w,h,image,mapping,flags,0,0,CompositingMode.REPLACE,true));
        assertTrue(GpuRasterizer.frame.draws.isEmpty()); assertEquals(0,GpuRasterizer.frame.bytes);
        assertEquals(0xff206040,pixels[10*w+10]);
    }

    @Test public void journalDrawLimitBoundsTinyCommands() {
        GpuRasterizer.close(); GpuRasterizer.enabled=true;
        int[] pixels=new int[16]; short[] depth=new short[16]; Arrays.fill(depth,(short)32200);
        Image2D image=new Image2D(Image2D.RGB,1,1,new byte[]{32,96,64});
        long before=GpuRasterizer.journalBarriers;
        for(int i=0;i<=GpuFrame.MAX_DRAWS;i++)
            assertTrue(GpuRasterizer.sprite(pixels,depth,4,4,image,new float[4],flags(4,4,500),0,0,CompositingMode.REPLACE,true));
        assertEquals(before+1,GpuRasterizer.journalBarriers); assertEquals(1,GpuRasterizer.frame.draws.size());
        GpuRasterizer.flush(); assertEquals(0xff206040,pixels[5]);
    }

    @Test public void persistentLossDoesNotCommitPartialBuffersAndRequiresReset() {
        GpuRasterizer.close(); GpuRasterizer.enabled=true;
        int[] pixels=new int[16]; short[] depth=new short[16]; Arrays.fill(depth,(short)32200);
        Image2D image=new Image2D(Image2D.RGB,1,1,new byte[]{32,96,64});
        assertTrue(GpuRasterizer.sprite(pixels,depth,4,4,image,new float[4],flags(4,4,500),0,0,CompositingMode.REPLACE,true));
        GpuRasterizer.lossPoint=3; GpuRasterizer.lossesRemaining=2;
        try { GpuRasterizer.flush(); fail("Persistent loss must be explicit"); }
        catch(IllegalStateException expected) { assertTrue(expected.getMessage().contains("not committed")); }
        for(int i=0;i<16;i++) { assertEquals(0,pixels[i]); assertEquals(32200,depth[i]); }
        try { GpuRasterizer.flush(); fail("Do not silently use stale CPU buffers"); }
        catch(IllegalStateException expected) { assertTrue(expected.getMessage().contains("reset required")); }
        GpuRasterizer.close();
        assertTrue(GpuRasterizer.sprite(pixels,depth,4,4,image,new float[4],flags(4,4,500),0,0,CompositingMode.REPLACE,false));
        assertEquals(0xff206040,pixels[5]);
    }

    @Test public void lostFirstDrawCanFallBackBeforeAnyFrameIsPending() {
        GpuRasterizer.close(); GpuRasterizer.enabled=true;
        int[] pixels=new int[16]; short[] depth=new short[16]; Arrays.fill(depth,(short)32200);
        GpuRasterizer.lossPoint=1; GpuRasterizer.lossesRemaining=2;
        assertFalse(GpuRasterizer.sprite(pixels,depth,4,4,new Image2D(Image2D.RGB,1,1,new byte[]{32,96,64}),
                new float[4],flags(4,4,500),0,0,CompositingMode.REPLACE,true));
        GpuRasterizer.flush(); assertTrue(GpuRasterizer.frame.draws.isEmpty()); assertEquals(0,pixels[5]);
    }

    @Test public void callerEglContextAndStateSurviveRecovery() {
        EGLDisplay display=eglGetDisplay(EGL_DEFAULT_DISPLAY); int[] version=new int[2];
        assertTrue(eglInitialize(display,version,0,version,1));
        EGLConfig[] configs=new EGLConfig[1]; int[] count=new int[1];
        assertTrue(eglChooseConfig(display,new int[]{EGL_RENDERABLE_TYPE,EGL_OPENGL_ES2_BIT,
                EGL_SURFACE_TYPE,EGL_PBUFFER_BIT,EGL_NONE},0,configs,0,1,count,0));
        EGLContext context=eglCreateContext(display,configs[0],EGL_NO_CONTEXT,new int[]{EGL_CONTEXT_CLIENT_VERSION,2,EGL_NONE},0);
        EGLSurface surface=eglCreatePbufferSurface(display,configs[0],new int[]{EGL_WIDTH,1,EGL_HEIGHT,1,EGL_NONE},0);
        try {
            assertTrue(eglMakeCurrent(display,surface,surface,context)); GLES20.glClearColor(.25f,.5f,.75f,1);
            sprites(2); assertEquals(context,eglGetCurrentContext());
            assertEquals(surface,eglGetCurrentSurface(EGL_DRAW)); assertEquals(surface,eglGetCurrentSurface(EGL_READ));
            float[] color=new float[4]; GLES20.glGetFloatv(GLES20.GL_COLOR_CLEAR_VALUE,color,0);
            assertArrayEquals(new float[]{.25f,.5f,.75f,1},color,0); assertEquals(GLES20.GL_NO_ERROR,GLES20.glGetError());
        } finally {
            GpuRasterizer.close(); eglMakeCurrent(display,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT);
            eglDestroySurface(display,surface); eglDestroyContext(display,context);
        }
    }

    private static int[] flags(int w,int h,int z) {
        int[] f=new int[20]; f[1]=w; f[3]=h; f[10]=1; f[11]=1;
        f[12]=3; f[13]=z; f[14]=256; f[16]=-1; return f;
    }
}
