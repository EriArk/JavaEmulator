/* SPDX-License-Identifier: GPL-3.0-or-later */
package javax.microedition.m3g;

import javax.microedition.lcdui.Image;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public class GpuReleaseTest {
    @After public void restore() {
        GpuRasterizer.lossesRemaining=0;GpuRasterizer.lossPoint=0;
        GpuRasterizer.close();Graphics3D.getInstance().releaseTarget();
        GpuRasterizer.discardReleasedDepth=true;GpuRasterizer.enabled=false;GpuGeometry.enabled=false;
    }
    @Test public void releaseDiscardsOnlyDepthAndRebindClearsIt() { check(false,false); }
    @Test public void cpuFallbackStillReadsDepthBeforeContinuing() { check(true,false); }
    @Test public void contextReplayPreservesColorBeforeDiscardingDepth() { check(false,true); }
    @Test public void conservativeAndDiscardedReleaseProduceIdenticalFrames() {
        GpuRasterizer.discardReleasedDepth=false;int[] expected=check(false,false);
        GpuRasterizer.discardReleasedDepth=true;assertArrayEquals(expected,check(false,false));
    }
    private int[] check(boolean cpu,boolean lose) {
        Graphics3D.initGraphics3D();GpuRasterizer.enabled=true;GpuGeometry.enabled=true;
        Graphics3D g=Graphics3D.getInstance();Image image=Image.createImage(32,32);
        Camera c=new Camera();c.setParallel(4,1,1,10);g.setCamera(c,new Transform());
        VertexArray p=new VertexArray(4,3,1);p.set(0,4,new byte[]{-1,1,0,-1,-1,0,1,1,0,1,-1,0});
        VertexBuffer v=new VertexBuffer();v.setPositions(p,1,null);v.setDefaultColor(0xff20a0e0);
        IndexBuffer indices=new TriangleStripArray(0,new int[]{4});Appearance a=new Appearance();
        Transform t=new Transform();t.postTranslate(0,0,-3);
        long reads=GpuRasterizer.depthReadbacks,discarded=GpuRasterizer.discardedDepthReadbacks;
        g.bindTarget(image.getGraphics(),true,0);g.clear(null);g.render(v,indices,a,t);
        if(cpu) {
            // Geometry behind the GPU draw must fail its depth test on the CPU too.
            GpuRasterizer.enabled=false;t.postTranslate(0,0,-1);v.setDefaultColor(0xffe04020);
            g.render(v,indices,a,t);GpuRasterizer.enabled=true;
            assertEquals(reads+1,GpuRasterizer.depthReadbacks);
        }
        if(lose) {GpuRasterizer.lossPoint=3;GpuRasterizer.lossesRemaining=1;}
        g.releaseTarget();
        assertEquals(0xff20a0e0,image.getBitmap().getPixel(16,16));
        if(!cpu) {
            assertEquals(reads+(GpuRasterizer.discardReleasedDepth?0:1),GpuRasterizer.depthReadbacks);
            assertEquals(discarded+(GpuRasterizer.discardReleasedDepth?1:0),GpuRasterizer.discardedDepthReadbacks);
        }
        // No explicit clear after rebinding: the previous near depth must not leak.
        g.bindTarget(image.getGraphics(),true,0);t=new Transform();t.postTranslate(0,0,-5);
        v.setDefaultColor(0xffe04020);g.render(v,indices,a,t);g.releaseTarget();
        assertEquals(0xffe04020,image.getBitmap().getPixel(16,16));
        int[] out=new int[1024];image.getBitmap().getPixels(out,0,32,0,0,32,32);return out;
    }
}
