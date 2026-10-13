/* SPDX-License-Identifier: GPL-3.0-or-later */
package javax.microedition.m3g;

import java.util.Arrays;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public class GpuTransferTest {
    private static final int W=67,H=39,BG=0xff172839,COLOR=0xff40a0e0;

    @After public void restore() {
        GpuRasterizer.lossPoint=0; GpuRasterizer.lossesRemaining=0;
        Graphics3D.getInstance().releaseTarget(); GpuRasterizer.close();
        GpuRasterizer.specializedPipelines=true; GpuRasterizer.partialReadback=true;
        GpuRasterizer.compactSubmission=true;
        GpuRasterizer.enabled=false; GpuGeometry.enabled=false;
    }

    private int[] pixels() { int[] p=new int[W*H]; Arrays.fill(p,BG); return p; }
    private short[] depth() { short[] z=new short[W*H]; Arrays.fill(z,(short)32200); return z; }

    private void sprite(int[] p,short[] z,int left,int top,int right,int bottom,int mask,int flags) {
        GpuRasterizer.enabled=true;
        Image2D image=new Image2D(Image2D.RGB,1,1,new byte[]{64,(byte)160,(byte)224});
        int[] f={left,right,top,bottom,0,0,0,0,0,0,1,1,flags,1234,256,0,mask,0,0,0};
        assertTrue(GpuRasterizer.sprite(p,z,W,H,image,new float[4],f,0,0,CompositingMode.REPLACE,true));
    }

    private void checkRect(int[] p,int left,int top,int right,int bottom) {
        for(int y=0;y<H;y++) for(int x=0;x<W;x++)
            assertEquals("pixel "+x+","+y,x>=left&&x<right&&y>=top&&y<bottom?COLOR:BG,p[y*W+x]);
    }

    @Test public void oddWidthSpriteReadsAndCommitsOnlyItsRectangle() {
        int[] p=pixels(); short[] z=depth();
        long reads=GpuRasterizer.colorReadPixels,commits=GpuRasterizer.colorCommittedPixels;
        sprite(p,z,3,5,18,12,-1,3); GpuRasterizer.flush();
        checkRect(p,3,5,18,12);
        assertEquals(105,GpuRasterizer.colorReadPixels-reads);
        assertEquals(105,GpuRasterizer.colorCommittedPixels-commits);
        for(int y=0;y<H;y++) for(int x=0;x<W;x++)
            assertEquals(x>=3&&x<18&&y>=5&&y<12?1234:32200,z[y*W+x]);
    }

    @Test public void disjointDrawsReadUnionAndPreserveGap() {
        int[] p=pixels(); short[] z=depth(); long reads=GpuRasterizer.colorReadPixels;
        sprite(p,z,2,4,9,10,-1,0); sprite(p,z,48,27,62,34,-1,0); GpuRasterizer.flush();
        assertEquals(60*30,GpuRasterizer.colorReadPixels-reads);
        for(int y=0;y<H;y++) for(int x=0;x<W;x++) {
            boolean drawn=x>=2&&x<9&&y>=4&&y<10 || x>=48&&x<62&&y>=27&&y<34;
            assertEquals(drawn?COLOR:BG,p[y*W+x]);
        }
    }

    @Test public void depthOnlyDrawSkipsColorButStillCommitsDepth() {
        int[] p=pixels(); short[] z=depth(); long reads=GpuRasterizer.colorReadPixels;
        sprite(p,z,3,5,18,12,0,2); GpuRasterizer.flush();
        assertEquals(reads,GpuRasterizer.colorReadPixels); assertArrayEquals(pixels(),p);
        assertEquals(1234,z[6*W+4]); assertEquals(32200,z[0]);
    }

    @Test public void alphaOnlyMaskKeepsRgbWithPartialTransfer() {
        int[] p=pixels(); Arrays.fill(p,0x40172839); short[] z=depth();
        sprite(p,z,3,5,18,12,0xff000000,0); GpuRasterizer.flush();
        for(int y=0;y<H;y++) for(int x=0;x<W;x++)
            assertEquals(x>=3&&x<18&&y>=5&&y<12?BG:0x40172839,p[y*W+x]);
    }

    @Test public void allRecoveryPointsPreservePartialCommit() {
        sprite(pixels(),depth(),0,0,1,1,-1,0); GpuRasterizer.flush();
        for(int point=1;point<=3;point++) {
            int[] p=pixels(); short[] z=depth();
            sprite(p,z,3,5,18,12,-1,3);
            long recoveries=GpuRasterizer.recoveries;
            GpuRasterizer.lossPoint=point; GpuRasterizer.lossesRemaining=1;
            GpuRasterizer.flush(); checkRect(p,3,5,18,12);
            assertEquals(recoveries+1,GpuRasterizer.recoveries);
            assertEquals(1234,z[6*W+4]); assertEquals(32200,z[0]);
        }
    }

    @Test public void changingReadDimensionsDoesNotRetainOldBufferPosition() {
        int[] p=pixels(); short[] z=depth();
        sprite(p,z,3,5,18,12,-1,0); GpuRasterizer.flush(); checkRect(p,3,5,18,12);
        sprite(p,z,0,0,W,H,-1,0); GpuRasterizer.flush(); checkRect(p,0,0,W,H);
        Arrays.fill(p,BG);
        sprite(p,z,63,35,67,39,-1,0); GpuRasterizer.flush(); checkRect(p,63,35,67,39);
    }

    @Test public void specializedSpriteMatchesGenericShaderAndFullReadback() {
        int[] reference=pixels(); short[] z=depth();
        GpuRasterizer.specializedPipelines=false; GpuRasterizer.partialReadback=false;
        sprite(reference,z,3,5,18,12,-1,3); GpuRasterizer.flush();
        GpuRasterizer.specializedPipelines=true; GpuRasterizer.partialReadback=true;
        int[] actual=pixels(); sprite(actual,depth(),3,5,18,12,-1,3); GpuRasterizer.flush();
        assertArrayEquals(reference,actual);
    }

    @Test public void specializedLightingMatchesGenericAndReplaysProgramSwitches() {
        GpuLightingTest fixtures=new GpuLightingTest();
        for(int mode:new int[]{1,2,3,7,9,11}) {
            GpuRasterizer.specializedPipelines=false;
            int[] expected=fixtures.scene(true,mode,2,true,false);
            GpuRasterizer.specializedPipelines=true;
            assertArrayEquals("mode="+mode,expected,fixtures.scene(true,mode,2,true,true));
        }
    }

    @Test public void everyTextureCountAndLightingVariantIsReusedAndMatchesReference() {
        GpuRasterizer.specializedPipelines=false; int[] expected=textureVariants();
        GpuRasterizer.specializedPipelines=true; long compiled=GpuRasterizer.pipelineCompilations;
        assertArrayEquals(expected,textureVariants());
        assertEquals("Ten variants, no recompilation on the second pass",10,GpuRasterizer.pipelineCompilations-compiled);
    }

    private int[] textureVariants() {
        Graphics3D.initGraphics3D(); GpuRasterizer.enabled=true; GpuGeometry.enabled=true;
        Graphics3D g=Graphics3D.getInstance();
        Camera camera=new Camera(); camera.setParallel(4,1,1,10); g.setCamera(camera,new Transform());
        VertexArray p=new VertexArray(4,3,1);
        p.set(0,4,new byte[]{-1,1,-3,-1,-1,-3,1,1,-3,1,-1,-3});
        VertexArray uv=new VertexArray(4,2,1); uv.set(0,4,new byte[]{0,0,0,1,1,0,1,1});
        VertexBuffer v=new VertexBuffer(); v.setPositions(p,1,null); v.setDefaultColor(COLOR);
        for(int i=0;i<4;i++) v.setTexCoords(i,uv,1,null);
        TriangleStripArray indices=new TriangleStripArray(0,new int[]{4});
        int[] result=new int[10*32*32];
        for(int pass=0;pass<2;pass++) for(int mode=0;mode<2;mode++) for(int units=0;units<=4;units++) {
            Appearance a=new Appearance(); PolygonMode polygon=new PolygonMode();
            polygon.setShading(mode==0?PolygonMode.SHADE_SMOOTH:PolygonMode.SHADE_FLAT); a.setPolygonMode(polygon);
            for(int i=0;i<units;i++) {
                // Alternate occupied slots for one/two layers, too: packing must stay correct.
                int slot=units<3?3-i:i;
                Texture2D texture=new Texture2D(new Image2D(Image2D.RGB,1,1,new byte[]{(byte)(40+30*i),100,(byte)180}));
                texture.setBlending(Texture2D.FUNC_ADD); a.setTexture(slot,texture);
            }
            Image2D target=new Image2D(Image2D.RGBA,32,32);
            g.bindTarget(target); g.clear(null); g.render(v,indices,a,new Transform()); g.releaseTarget();
            assertNotEquals("Visible variant",0,target.image[16*32+16]);
            int offset=(mode*5+units)*32*32;
            if(pass==0) System.arraycopy(target.image,0,result,offset,1024);
            else for(int i=0;i<1024;i++) assertEquals(result[offset+i],target.image[i]);
        }
        return result;
    }

    @Test public void compactSubmissionPreservesMutableTexturesAndProgramSwitches() {
        GpuRasterizer.compactSubmission=false;
        int[] expected=bindingSequence();
        GpuRasterizer.compactSubmission=true;
        long reused=GpuRasterizer.textureBindingReuses;
        assertArrayEquals(expected,bindingSequence());
        assertTrue("Unchanged bindings should not call GL again",GpuRasterizer.textureBindingReuses>reused+20);
    }

    private int[] bindingSequence() {
        Graphics3D.initGraphics3D(); GpuRasterizer.enabled=true; GpuGeometry.enabled=true;
        Graphics3D g=Graphics3D.getInstance();
        Camera camera=new Camera(); camera.setParallel(4,1,1,10); g.setCamera(camera,new Transform());
        VertexArray p=new VertexArray(4,3,1);
        p.set(0,4,new byte[]{-1,1,-3,-1,-1,-3,1,1,-3,1,-1,-3});
        VertexArray uv=new VertexArray(4,2,1); uv.set(0,4,new byte[]{0,0,0,1,1,0,1,1});
        VertexBuffer v=new VertexBuffer(); v.setPositions(p,1,null); v.setTexCoords(0,uv,1,null);
        Image2D image=new Image2D(Image2D.RGB,1,1);
        Texture2D texture=new Texture2D(image); texture.setBlending(Texture2D.FUNC_REPLACE);
        Appearance a=new Appearance(); a.setTexture(0,texture);
        TriangleStripArray indices=new TriangleStripArray(0,new int[]{4});
        int[] result=new int[3*32*32];
        for(int frame=0;frame<3;frame++) {
            javax.microedition.lcdui.Image target=javax.microedition.lcdui.Image.createImage(32,32);
            g.bindTarget(target.getGraphics(),true,0); g.clear(null);
            for(int draw=0;draw<20;draw++) {
                if(draw==8) image.set(0,0,1,1,new byte[]{40,100,(byte)(160+frame*20)});
                texture.setFiltering(Texture2D.FILTER_BASE_LEVEL,draw<10?Texture2D.FILTER_NEAREST:Texture2D.FILTER_LINEAR);
                a.setTexture(0,draw==12?null:texture);
                if(draw%4==0) g.setViewport(draw/4%4*8,draw/16*6,8,6);
                g.render(v,indices,a,new Transform());
            }
            g.releaseTarget(); target.getBitmap().getPixels(result,frame*1024,32,0,0,32,32);
        }
        return result;
    }
}
