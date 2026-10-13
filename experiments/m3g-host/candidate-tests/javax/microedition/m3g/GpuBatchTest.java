/* SPDX-License-Identifier: GPL-3.0-or-later */
package javax.microedition.m3g;

import java.util.Arrays;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public class GpuBatchTest {
    @After public void restore() {
        GpuRasterizer.lossesRemaining=0; GpuRasterizer.lossPoint=0;
        GpuRasterizer.close(); Graphics3D.getInstance().releaseTarget();
        GpuRasterizer.enabled=false; GpuRasterizer.batchSubmission=true;
    }

    @Test public void queuedMutableSpritesMatchImmediateSubmissionWithTwoUploads() {
        int[][] expected=layers(false);
        int[][] actual=layers(true);
        assertArrayEquals(expected[0],actual[0]); assertArrayEquals(expected[1],actual[1]);
    }

    private int[][] layers(boolean batch) {
        GpuRasterizer.close(); GpuRasterizer.enabled=true; GpuRasterizer.batchSubmission=batch;
        int w=47,h=35; int[] pixels=new int[w*h]; short[] depth=new short[w*h];
        Arrays.fill(pixels,0xff183048); Arrays.fill(depth,(short)32200);
        Image2D image=new Image2D(Image2D.RGBA,1,1);
        long submits=GpuRasterizer.submissions,uploads=GpuRasterizer.geometryUploads;
        long rendered=GpuRasterizer.submittedDraws;
        int[] flags=flags(w,h,1000);
        for(int i=0;i<12;i++) {
            image.set(0,0,1,1,new byte[]{(byte)(i*17),64,(byte)(220-i*11),-96});
            flags[0]=i; flags[2]=i; flags[13]=1000-i*10;
            assertTrue(GpuRasterizer.sprite(pixels,depth,w,h,image,new float[4],flags,0,0,CompositingMode.ALPHA,true));
        }
        // Deferred commands must not read the final mutable image or clip rectangle.
        image.set(0,0,1,1,new byte[]{0,0,0,0}); Arrays.fill(flags,0);
        assertEquals(rendered+(batch?1:12),GpuRasterizer.submittedDraws);
        assertEquals(0xff183048,pixels[20*w+20]);
        GpuRasterizer.flush();
        assertEquals(rendered+12,GpuRasterizer.submittedDraws);
        assertEquals(submits+(batch?2:13),GpuRasterizer.submissions);
        assertEquals(uploads+(batch?2:12),GpuRasterizer.geometryUploads);
        int[] z=new int[depth.length]; for(int i=0;i<z.length;i++) z[i]=depth[i];
        return new int[][]{pixels,z};
    }

    @Test public void targetSwitchCommitsAllQueuedDrawsBeforeResize() {
        GpuRasterizer.close(); GpuRasterizer.enabled=true;
        int[] first=new int[16],second=new int[35]; short[] z1=new short[16],z2=new short[35];
        Arrays.fill(z1,(short)32200); Arrays.fill(z2,(short)32200);
        sprite(first,z1,4,4,0x204060,1000); sprite(first,z1,4,4,0x60a020,500);
        assertEquals(0,first[5]); sprite(second,z2,7,5,0x804020,1000);
        assertEquals(0xff60a020,first[5]); assertEquals(500,z1[5]); assertEquals(0,second[8]);
        sprite(second,z2,7,5,0xa06080,500); GpuRasterizer.flush();
        assertEquals(0xffa06080,second[8]); assertEquals(500,z2[8]);
    }

    @Test public void closeCommitsQueuedDrawsAndNextTargetStartsClean() {
        GpuRasterizer.close(); GpuRasterizer.enabled=true;
        int[] pixels=new int[16]; short[] z=new short[16]; Arrays.fill(z,(short)32200);
        sprite(pixels,z,4,4,0x204060,1000); sprite(pixels,z,4,4,0x60a020,500);
        GpuRasterizer.close(); assertEquals(0xff60a020,pixels[5]);
        assertTrue(GpuRasterizer.frame.draws.isEmpty());
        Arrays.fill(z,(short)32200); sprite(pixels,z,4,4,0x804020,1000); GpuRasterizer.flush();
        assertEquals(0xff804020,pixels[5]);
    }

    @Test public void queuedPersistentLossNeverCommitsPartialColorOrDepth() {
        GpuRasterizer.close(); GpuRasterizer.enabled=true;
        int[] pixels=new int[16]; short[] z=new short[16]; Arrays.fill(z,(short)32200);
        sprite(pixels,z,4,4,0x204060,1000); sprite(pixels,z,4,4,0x60a020,500);
        GpuRasterizer.lossPoint=2; GpuRasterizer.lossesRemaining=2;
        try { GpuRasterizer.flush(); fail("Persistent queued loss must be explicit"); }
        catch(IllegalStateException expected) { assertTrue(expected.getMessage().contains("not committed")); }
        for(int i=0;i<16;i++) { assertEquals(0,pixels[i]); assertEquals(32200,z[i]); }
    }

    @Test public void lcdUiHudAndClippedTranslatedTargetsPreservePaintOrder() {
        int[] immediate=mixed(false),batched=mixed(true);
        assertArrayEquals(immediate,batched);
        assertEquals(0xff30a060,batched[17*47+20]);
        assertEquals(0xffffc040,batched[8*47+10]);
        assertEquals(0xff204060,batched[0]);
    }

    private int[] mixed(boolean batch) {
        Graphics3D.initGraphics3D(); GpuRasterizer.enabled=true; GpuRasterizer.batchSubmission=batch;
        Graphics3D g=Graphics3D.getInstance(); Image image=Image.createImage(47,35);
        Graphics canvas=image.getGraphics(); canvas.setColor(0x204060); canvas.fillRect(0,0,47,35);
        canvas.translate(3,2); canvas.setClip(2,2,39,29);
        Camera camera=new Camera(); camera.setParallel(4,47f/35,1,10); g.setCamera(camera,new Transform());
        g.bindTarget(canvas,true,0); g.clear(null);
        Transform transform=new Transform(); transform.postTranslate(0,0,-3);
        g.render(quad(0xffa02030),transform); g.render(quad(0xff30a060),transform); g.releaseTarget();
        canvas.setColor(0xffc040); canvas.fillRect(5,5,10,4);
        // Depth is undefined on rebind; preserve color and explicitly clear depth.
        g.bindTarget(canvas,true,0); Background background=new Background();
        background.setColorClearEnable(false); g.clear(background); g.setViewport(30,20,8,8);
        g.render(quad(0xff4020ff),transform); g.render(quad(0xff4020ff),transform); g.releaseTarget();
        int[] pixels=new int[47*35]; image.getBitmap().getPixels(pixels,0,47,0,0,47,35);
        return pixels;
    }

    private Mesh quad(int color) {
        VertexArray points=new VertexArray(4,3,1); points.set(0,4,new byte[]{-1,1,0,-1,-1,0,1,1,0,1,-1,0});
        VertexBuffer vertices=new VertexBuffer(); vertices.setPositions(points,1,null); vertices.setDefaultColor(color);
        PolygonMode polygon=new PolygonMode(); polygon.setCulling(PolygonMode.CULL_NONE);
        Appearance style=new Appearance(); style.setPolygonMode(polygon);
        return new Mesh(vertices,new TriangleStripArray(0,new int[]{4}),style);
    }

    @Test public void changingMeshSpriteAndMaterialStateMatchesImmediateDraws() {
        // The first divergent prefix and the complete sequence. Exhaustively
        // rebuilding every prefix needlessly recompiles shaders on software GL.
        for(int steps:new int[]{11,20})
            assertArrayEquals("after command "+steps,stateChanges(false,false,steps),stateChanges(true,false,steps));
    }

    @Test public void replayReinitializesEveryStateAfterMixedQueuedDraws() {
        int[] expected=stateChanges(false,false,20);
        long recovered=GpuRasterizer.recoveries;
        assertArrayEquals(expected,stateChanges(true,true,20));
        assertEquals(recovered+1,GpuRasterizer.recoveries);
    }

    private int[] stateChanges(boolean batch,boolean lose,int steps) {
        Graphics3D.initGraphics3D(); GpuRasterizer.enabled=true; GpuRasterizer.batchSubmission=batch;
        Graphics3D g=Graphics3D.getInstance(); Image image=Image.createImage(47,35);
        Camera camera=new Camera(); camera.setParallel(4,47f/35,1,10); g.setCamera(camera,new Transform());
        g.bindTarget(image.getGraphics(),true,0);
        Background bg=new Background(); bg.setColor(0xff183048); g.clear(bg);
        int[] blends={CompositingMode.REPLACE,CompositingMode.ALPHA,CompositingMode.ALPHA_ADD,
                CompositingMode.MODULATE,CompositingMode.MODULATE_X2};
        long before=GpuRasterizer.draws;
        for(int i=0;i<steps;i++) {
            Mesh mesh=quad(0xc0c08040+i*256); Appearance a=mesh.getAppearance(0);
            VertexArray uv=new VertexArray(4,2,1); uv.set(0,4,new byte[]{0,0,0,1,1,0,1,1});
            int units=i%5;
            for(int u=0;u<units;u++) {
                int side=1+(i%2),format=i%2==0?Image2D.RGB:Image2D.RGBA;
                byte[] pixels=new byte[side*side*(format==Image2D.RGB?3:4)]; Arrays.fill(pixels,(byte)(96+i*5));
                Texture2D t=new Texture2D(new Image2D(format,side,side,pixels));
                t.setBlendColor(0x408020+i*256);
                t.setBlending(i%2==0?Texture2D.FUNC_BLEND:Texture2D.FUNC_MODULATE);
                t.setFiltering(i%2==0?Texture2D.FILTER_BASE_LEVEL:Texture2D.FILTER_LINEAR,Texture2D.FILTER_LINEAR);
                a.setTexture(u,t); mesh.getVertexBuffer().setTexCoords(u,uv,1,null);
            }
            CompositingMode c=new CompositingMode(); c.setBlending(blends[i%blends.length]);
            c.setDepthTestEnable(i%3!=0); c.setDepthWriteEnable(i%4!=0);
            c.setColorWriteEnable(i%6!=0); c.setAlphaWriteEnable(i%4!=0); c.setAlphaThreshold(i%2==0?0:.1f);
            a.setCompositingMode(c);
            if(i%3!=0) {
                Fog f=new Fog(); f.setMode(i%2==0?Fog.LINEAR:Fog.EXPONENTIAL);
                f.setColor(0x205080+i*512); f.setLinear(1,6); f.setDensity(.1f*i); a.setFog(f);
            }
            mesh.setAlphaFactor(.5f+.02f*i);
            Transform transform=new Transform(); transform.postTranslate((i%3-1)*.5f,0,-4+i*.05f);
            g.setViewport(i%3,i%2,44,33);
            g.render(mesh,transform);
            if(i%4==0) {
                Sprite3D sprite=new Sprite3D(true,new Image2D(Image2D.RGBA,1,1,new byte[]{64,-96,32,-96}),a);
                sprite.setScale(.5f,.5f,1); sprite.setAlphaFactor(.7f); g.render(sprite,transform);
            }
        }
        if(lose) { GpuRasterizer.lossPoint=2; GpuRasterizer.lossesRemaining=1; }
        g.releaseTarget();
        assertEquals(before+steps+(steps+3)/4,GpuRasterizer.draws);
        int[] pixels=new int[47*35]; image.getBitmap().getPixels(pixels,0,47,0,0,47,35);
        if(steps==20) assertNotEquals(0xff183048,pixels[17*47+23]);
        return pixels;
    }

    private void sprite(int[] pixels,short[] depth,int w,int h,int color,int z) {
        Image2D image=new Image2D(Image2D.RGB,1,1,new byte[]{(byte)(color>>16),(byte)(color>>8),(byte)color});
        assertTrue(GpuRasterizer.sprite(pixels,depth,w,h,image,new float[4],flags(w,h,z),0,0,CompositingMode.REPLACE,true));
    }

    private static int[] flags(int w,int h,int z) {
        int[] f=new int[20]; f[1]=w; f[3]=h; f[10]=1; f[11]=1;
        f[12]=3; f[13]=z; f[14]=256; f[16]=-1; return f;
    }
}
