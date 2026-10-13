/* SPDX-License-Identifier: GPL-3.0-or-later */
package javax.microedition.m3g;

import java.util.Arrays;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import org.recompile.mobile.PlatformGraphics;
import org.recompile.mobile.BitmapTransfer;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public class GpuThroughputTest {
    @After public void restore() {
        GpuRasterizer.lossesRemaining=0; GpuRasterizer.lossPoint=0;
        Graphics3D.getInstance().releaseTarget(); GpuRasterizer.close();
        GpuRasterizer.enabled=false; GpuGeometry.enabled=false; GpuGeometry.frustumCulling=true;
        NativeRasterizer.enabled=true;
        BitmapTransfer.enabled=true;
        GpuRasterizer.deferValidatedFrames=true; GpuRasterizer.fastClear=true; PlatformGraphics.lazyRead=true;
    }

    private Graphics3D renderer() {
        Graphics3D.initGraphics3D(); GpuRasterizer.enabled=true; GpuGeometry.enabled=true;
        Graphics3D g=Graphics3D.getInstance();
        Camera camera=new Camera(); camera.setParallel(4,1,1,10); g.setCamera(camera,new Transform());
        return g;
    }

    private Mesh mesh(int color) {
        VertexArray points=new VertexArray(4,3,1);
        points.set(0,4,new byte[]{-1,1,-3,-1,-1,-3,1,1,-3,1,-1,-3});
        VertexBuffer vertices=new VertexBuffer(); vertices.setPositions(points,1,null); vertices.setDefaultColor(color);
        return new Mesh(vertices,new TriangleStripArray(0,new int[]{4}),new Appearance());
    }

    private Image image() {
        Image target=Image.createImage(32,32); target.getBitmap().eraseColor(0xff123456); return target;
    }

    @Test public void opaqueBitmapTransferPreservesChannelsClipAndStride() {
        Bitmap bitmap=Bitmap.createBitmap(19,13,Bitmap.Config.ARGB_8888);
        int[] input=new int[19*13];
        for(int i=0;i<input.length;i++) input[i]=0xff000000|((i*137)&0xffffff);
        bitmap.setPixels(input,0,19,0,0,19,13);
        int[] expected=new int[31*20],actual=new int[31*20];
        Arrays.fill(expected,0x11223344); Arrays.fill(actual,0x11223344);
        bitmap.getPixels(expected,3*31+2,31,2,3,16,8); long reads=BitmapTransfer.nativeReads;
        BitmapTransfer.read(bitmap,actual,31,2,3,16,8);
        assertEquals(reads+1,BitmapTransfer.nativeReads); assertArrayEquals(expected,actual);
        assertArrayEquals(input,readBitmap(bitmap));
    }

    @Test public void transparentBitmapTransferUsesAndroidUnpremultiplication() {
        Bitmap bitmap=Bitmap.createBitmap(19,13,Bitmap.Config.ARGB_8888);
        int[] input=new int[19*13];
        for(int i=0;i<input.length;i++) input[i]=((i&255)<<24)|0x794bdf;
        input[0]=0xffabcdef; bitmap.setPixels(input,0,19,0,0,19,13);
        int[] expected=readBitmap(bitmap),actual=new int[input.length];
        long reads=BitmapTransfer.fallbackReads; BitmapTransfer.read(bitmap,actual,19,0,0,19,13);
        assertEquals(reads+1,BitmapTransfer.fallbackReads); assertArrayEquals(expected,actual);
        assertArrayEquals(expected,readBitmap(bitmap));
    }

    @Test public void nonSrgbAndRgb565BitmapsKeepAndroidConversion() {
        for(Bitmap bitmap:new Bitmap[]{Bitmap.createBitmap(7,9,Bitmap.Config.RGB_565),
                Bitmap.createBitmap(7,9,Bitmap.Config.ARGB_8888,true,ColorSpace.get(ColorSpace.Named.DISPLAY_P3))}) {
            bitmap.eraseColor(0xff579bdf); int[] expected=readBitmap(bitmap),actual=new int[63];
            long reads=BitmapTransfer.fallbackReads; BitmapTransfer.read(bitmap,actual,7,0,0,7,9);
            assertEquals(reads+1,BitmapTransfer.fallbackReads); assertArrayEquals(expected,actual);
        }
    }

    private int[] readBitmap(Bitmap bitmap) {
        int[] result=new int[bitmap.getWidth()*bitmap.getHeight()];
        bitmap.getPixels(result,0,bitmap.getWidth(),0,0,bitmap.getWidth(),bitmap.getHeight()); return result;
    }

    @Test public void fullClearSkipsImportButPreservesClippedOutsideAndLater2d() {
        Graphics3D g=renderer(); Image target=image(); Graphics graphics=target.getGraphics();
        graphics.translate(3,4); graphics.setClip(2,2,20,18);
        long reads=PlatformGraphics.imports; g.bindTarget(graphics); g.clear(null); g.releaseTarget();
        assertEquals(reads,PlatformGraphics.imports);
        assertEquals(0xff123456,target.getBitmap().getPixel(4,6));
        assertEquals(0xff000000,target.getBitmap().getPixel(5,6));
        assertEquals(0xff123456,target.getBitmap().getPixel(25,7));
        graphics.setColor(0xffffff); graphics.fillRect(2,2,1,1);
        assertEquals(0xffffffff,target.getBitmap().getPixel(5,6));
    }

    @Test public void partialViewportClearReadsPreviousContents() {
        Graphics3D g=renderer(); Image target=image(); long reads=PlatformGraphics.imports;
        g.bindTarget(target.getGraphics()); g.setViewport(4,5,8,9); g.clear(null); g.releaseTarget();
        assertEquals(reads+1,PlatformGraphics.imports);
        assertEquals(0xff123456,target.getBitmap().getPixel(3,5));
        assertEquals(0xff000000,target.getBitmap().getPixel(4,5));
        assertEquals(0xff123456,target.getBitmap().getPixel(12,5));
    }

    @Test public void hardwareClearMatchesUploadsIncludingRealContextRecovery() {
        int[] expected=clearSequence(false,false,false);
        assertArrayEquals(expected,clearSequence(true,false,false));
        assertArrayEquals(expected,clearSequence(true,true,false));
    }

    @Test public void hardwareClearIsInvalidatedBeforeCpuFallback() {
        assertArrayEquals(clearSequence(false,false,true),clearSequence(true,false,true));
    }

    private int[] clearSequence(boolean fast,boolean lose,boolean cpu) {
        Graphics3D g=renderer(); GpuRasterizer.fastClear=fast;
        Image target=image(); Background bg=new Background(); bg.setColor(0xff315e97);
        Mesh mesh=mesh(0x8020a0e0);
        CompositingMode comp=new CompositingMode(); comp.setBlending(CompositingMode.ALPHA);
        mesh.getAppearance(0).setCompositingMode(comp);
        long clears=GpuRasterizer.clearedUploads;
        for(int pass=0;pass<2;pass++) {
            g.bindTarget(target.getGraphics()); g.clear(bg);
            if(cpu) { GpuRasterizer.enabled=false; g.render(mesh,new Transform()); GpuRasterizer.enabled=true; }
            g.render(mesh,new Transform());
            if(lose && pass==1) { GpuRasterizer.lossPoint=2; GpuRasterizer.lossesRemaining=1; }
            g.releaseTarget();
        }
        assertEquals(clears+(fast&&!cpu?(lose?3:2):0),GpuRasterizer.clearedUploads);
        int[] pixels=new int[1024]; target.getBitmap().getPixels(pixels,0,32,0,0,32,32);
        assertEquals(0xff315e97,pixels[0]); assertNotEquals(pixels[0],pixels[16*32+16]);
        return pixels;
    }

    @Test public void partialClearNeverClaimsUniformTarget() {
        Graphics3D g=renderer(); Image target=image(); long clears=GpuRasterizer.clearedUploads;
        g.bindTarget(target.getGraphics()); g.setViewport(4,4,24,24); g.clear(null);
        g.render(mesh(0xff20a0e0),new Transform()); g.releaseTarget();
        assertEquals(clears,GpuRasterizer.clearedUploads);
        assertEquals(0xff123456,target.getBitmap().getPixel(0,0));
        assertEquals(0xff20a0e0,target.getBitmap().getPixel(16,16));
    }

    @Test public void partialClearAfterUniformClearPreservesBothRegions() {
        Graphics3D g=renderer(); Image target=image(); long clears=GpuRasterizer.clearedUploads;
        g.bindTarget(target.getGraphics()); g.clear(null); g.setViewport(4,4,24,24);
        Background bg=new Background(); bg.setColor(0xff315e97); g.clear(bg);
        g.render(mesh(0xff20a0e0),new Transform()); g.releaseTarget();
        assertEquals(clears,GpuRasterizer.clearedUploads);
        assertEquals(0xff000000,target.getBitmap().getPixel(0,0));
        assertEquals(0xff315e97,target.getBitmap().getPixel(4,4));
        assertEquals(0xff20a0e0,target.getBitmap().getPixel(16,16));
    }

    @Test public void wholeMeshesOutsideEachClipPlaneSkipUploads() {
        for(boolean perspective:new boolean[]{false,true}) {
            Graphics3D g=renderer(); Camera c=new Camera();
            if(perspective) c.setPerspective(60,1,1,10); else c.setParallel(4,1,1,10);
            g.setCamera(c,new Transform()); Image target=image(); g.bindTarget(target.getGraphics()); g.clear(null);
            Mesh mesh=mesh(0xff20a0e0); long culled=GpuGeometry.culledDraws,uploads=GpuMeshCache.uploads;
            for(float[] xyz:new float[][]{{-100,0,0},{100,0,0},{0,-100,0},{0,100,0},{0,0,4},{0,0,-100}}) {
                Transform t=new Transform(); t.postTranslate(xyz[0],xyz[1],xyz[2]); g.render(mesh,t);
            }
            g.releaseTarget(); assertEquals(culled+6,GpuGeometry.culledDraws);
            assertEquals(uploads,GpuMeshCache.uploads); assertEquals(0xff000000,target.getBitmap().getPixel(16,16));
        }
    }

    @Test public void cullingTracksPositionRevisionAndScaleBias() {
        Graphics3D g=renderer(); Image target=image(); Mesh mesh=mesh(0xff20a0e0);
        VertexBuffer vb=mesh.getVertexBuffer(); VertexArray p=vb.getPositions(null);
        long builds=GpuGeometry.boundsBuilds,culled=GpuGeometry.culledDraws;
        g.bindTarget(target.getGraphics()); g.clear(null);
        vb.setPositions(p,1,new float[]{100,0,0}); g.render(mesh,new Transform());
        vb.setPositions(p,1,null); g.render(mesh,new Transform());
        p.set(0,4,new byte[]{99,1,-3,99,-1,-3,101,1,-3,101,-1,-3}); g.render(mesh,new Transform());
        vb.setPositions(p,-1,new float[]{100,0,-6}); g.render(mesh,new Transform());
        g.releaseTarget(); assertEquals(builds+2,GpuGeometry.boundsBuilds);
        assertEquals(culled+2,GpuGeometry.culledDraws);
        assertEquals(0xff20a0e0,target.getBitmap().getPixel(16,16));
    }

    @Test public void crossingNearPlaneAndMovingCameraMatchUnculledRendering() {
        assertArrayEquals(crossing(false),crossing(true));
    }

    private int[] crossing(boolean cull) {
        Graphics3D g=renderer(); GpuGeometry.frustumCulling=cull; Camera camera=new Camera();
        camera.setPerspective(60,1,1,10); Image target=image(); Mesh mesh=mesh(0xff20a0e0);
        mesh.getVertexBuffer().getPositions(null).set(0,4,new byte[]{-1,1,0,-1,-1,-3,1,1,-3,1,-1,-3});
        PolygonMode polygon=new PolygonMode(); polygon.setCulling(PolygonMode.CULL_NONE);
        mesh.getAppearance(0).setPolygonMode(polygon); int[] result=new int[1024*3];
        for(int pass=0;pass<3;pass++) {
            Transform cameraTransform=new Transform(); cameraTransform.postTranslate(pass==1?100:0,0,0);
            g.setCamera(camera,cameraTransform); g.bindTarget(target.getGraphics()); g.clear(null);
            g.render(mesh,new Transform()); g.releaseTarget();
            target.getBitmap().getPixels(result,pass*1024,32,0,0,32,32);
        }
        assertEquals(0xff20a0e0,result[16*32+16]); assertEquals(0xff000000,result[1024+16*32+16]);
        assertEquals(result[16*32+16],result[2048+16*32+16]); return result;
    }

    @Test public void emptyBindingAndDepthOnlyClearDoNotCopyColor() {
        Graphics3D g=renderer(); Image target=image();
        long reads=PlatformGraphics.imports,writes=PlatformGraphics.exports;
        g.bindTarget(target.getGraphics()); g.releaseTarget();
        g.bindTarget(target.getGraphics()); Background background=new Background(); background.setColorClearEnable(false);
        g.clear(background); g.releaseTarget();
        assertEquals(reads,PlatformGraphics.imports); assertEquals(writes,PlatformGraphics.exports);
        assertEquals(0xff123456,target.getBitmap().getPixel(16,16));
    }

    @Test public void renderWithoutClearImportsBackgroundForBlending() {
        Graphics3D g=renderer(); Image target=image(); Mesh mesh=mesh(0x8020a0e0);
        CompositingMode comp=new CompositingMode(); comp.setBlending(CompositingMode.ALPHA);
        mesh.getAppearance(0).setCompositingMode(comp); long reads=PlatformGraphics.imports;
        g.bindTarget(target.getGraphics()); g.render(mesh,new Transform()); g.releaseTarget();
        assertEquals(reads+1,PlatformGraphics.imports);
        assertEquals(0xff123456,target.getBitmap().getPixel(0,0));
        int center=target.getBitmap().getPixel(16,16);
        assertNotEquals(0xff123456,center); assertNotEquals(0xff20a0e0,center);
    }

    @Test public void eagerAndLazyTransfersMatchAcrossTargetsAndChangingViewports() {
        int[] expected=sequence(false); assertArrayEquals(expected,sequence(true));
    }

    private int[] sequence(boolean lazy) {
        Graphics3D g=renderer(); PlatformGraphics.lazyRead=lazy;
        Image target=image(); Mesh mesh=mesh(0xff20a0e0);
        for(int i=0;i<4;i++) {
            Graphics graphics=target.getGraphics(); graphics.setClip(i,i,32-i*2,32-i*2);
            g.bindTarget(graphics); g.clear(null); g.render(mesh,new Transform());
            g.setViewport(1,2,5,6); Background bg=new Background(); bg.setColor(0xffd08020); g.clear(bg);
            g.releaseTarget();
            Image2D other=new Image2D(Image2D.RGBA,32,32); g.bindTarget(other); g.clear(bg); g.releaseTarget();
        }
        int[] pixels=new int[1024]; target.getBitmap().getPixels(pixels,0,32,0,0,32,32); return pixels;
    }

    @Test public void immutableTargetIsRejectedWithoutBinding() {
        Graphics3D g=renderer(); Image2D target=new Image2D(Image2D.RGB,1,1,new byte[]{1,2,3});
        try { g.bindTarget(target); fail("Immutable target accepted"); } catch(IllegalArgumentException expected) { }
        assertNull(g.getTarget());
        Image image=image(); g.bindTarget(image.getGraphics()); g.clear(null); g.releaseTarget();
        assertEquals(0xff000000,image.getBitmap().getPixel(0,0));
    }

    @Test public void rgb888HintsPreservePixelsAndUseHardware() {
        assertEquals(Boolean.FALSE,Graphics3D.getProperties().get("supportDithering"));
        assertEquals(Boolean.FALSE,Graphics3D.getProperties().get("supportTrueColor"));
        int[] reference=null;
        for(int hints:new int[]{0,Graphics3D.DITHER,Graphics3D.TRUE_COLOR,Graphics3D.DITHER|Graphics3D.TRUE_COLOR}) {
            Graphics3D g=renderer(); Image target=image(); long draws=GpuGeometry.draws;
            g.bindTarget(target.getGraphics(),true,hints); assertEquals(hints,g.getHints());
            g.clear(null); g.render(mesh(0xff204060),new Transform()); g.releaseTarget();
            assertEquals(draws+1,GpuGeometry.draws);
            int[] pixels=new int[1024]; target.getBitmap().getPixels(pixels,0,32,0,0,32,32);
            if(reference==null) reference=pixels; else assertArrayEquals(reference,pixels);
        }
    }

    @Test public void invalidBackgroundFormatDoesNotPartiallyClearMidp() {
        Graphics3D g=renderer(); Image target=image(); g.bindTarget(target.getGraphics());
        Background background=new Background(); background.setImage(new Image2D(Image2D.RGBA,1,1));
        try { g.clear(background); fail("RGBA background accepted on RGB MIDP target"); }
        catch(IllegalArgumentException expected) { }
        g.releaseTarget(); assertEquals(0xff123456,target.getBitmap().getPixel(16,16));
    }

    @Test public void oneDepthUnitSeparatesCoplanarPolygonsOnEveryBackend() {
        for(int path=0;path<3;path++) for(int sign:new int[]{-1,1}) {
            Graphics3D g=renderer(); GpuRasterizer.enabled=path!=0; GpuGeometry.enabled=path==2;
            Image target=image(); g.bindTarget(target.getGraphics()); g.clear(null);
            Mesh base=mesh(0xffd04020),decal=mesh(0xff20a0e0);
            CompositingMode comp=new CompositingMode(); comp.setDepthOffset(0,sign);
            decal.getAppearance(0).setCompositingMode(comp);
            g.render(base,new Transform()); g.render(decal,new Transform()); g.releaseTarget();
            assertEquals("path="+path+" sign="+sign,sign<0?0xff20a0e0:0xffd04020,
                    target.getBitmap().getPixel(16,16));
        }
    }

    @Test public void depthWritingDoesNotRequireDepthTestingOnAnyMeshBackend() {
        for(int path=0;path<4;path++) for(boolean boundDepth:new boolean[]{false,true})
                for(boolean write:new boolean[]{false,true}) {
            Graphics3D g=renderer(); NativeRasterizer.enabled=path!=0;
            GpuRasterizer.enabled=path>=2; GpuGeometry.enabled=path==3;
            Image target=image(); g.bindTarget(target.getGraphics(),boundDepth,0); g.clear(null);
            Mesh front=mesh(0xff20a0e0); CompositingMode comp=new CompositingMode();
            comp.setDepthTestEnable(false); comp.setDepthWriteEnable(write);
            front.getAppearance(0).setCompositingMode(comp); g.render(front,new Transform());
            // Cross the GPU-to-CPU barrier as well as testing independent write/test flags.
            GpuRasterizer.enabled=false; Transform back=new Transform(); back.postTranslate(0,0,-2);
            g.render(mesh(0xffd04020),back); g.releaseTarget();
            assertEquals("path="+path+" depth="+boundDepth+" write="+write,
                    boundDepth&&write?0xff20a0e0:0xffd04020,target.getBitmap().getPixel(16,16));
        }
    }

    @Test public void writeOnlyDepthPassMasksColorButOccludesFollowingMesh() {
        for(int path=0;path<4;path++) {
            Graphics3D g=renderer(); NativeRasterizer.enabled=path!=0;
            GpuRasterizer.enabled=path>=2; GpuGeometry.enabled=path==3;
            Image target=image(); g.bindTarget(target.getGraphics()); g.clear(null);
            Mesh front=mesh(0xff20a0e0); CompositingMode comp=new CompositingMode();
            comp.setDepthTestEnable(false); comp.setColorWriteEnable(false); comp.setAlphaWriteEnable(false);
            front.getAppearance(0).setCompositingMode(comp); g.render(front,new Transform());
            Transform back=new Transform(); back.postTranslate(0,0,-2);
            g.render(mesh(0xffd04020),back); g.releaseTarget();
            assertEquals("path="+path,0xff000000,target.getBitmap().getPixel(16,16));
        }
    }

    @Test public void spritesWriteDepthWithoutTestingAndRespectDisabledTargetDepth() {
        for(int path=0;path<3;path++) for(boolean boundDepth:new boolean[]{false,true})
                for(boolean write:new boolean[]{false,true}) {
            Graphics3D g=renderer(); NativeRasterizer.enabled=path!=0;
            GpuRasterizer.enabled=path==2; Image target=image();
            g.bindTarget(target.getGraphics(),boundDepth,0); g.clear(null);
            Appearance appearance=new Appearance(); CompositingMode comp=new CompositingMode();
            comp.setDepthTestEnable(false); comp.setDepthWriteEnable(write); appearance.setCompositingMode(comp);
            Sprite3D front=new Sprite3D(true,new Image2D(Image2D.RGB,1,1,new byte[]{32,(byte)160,(byte)224}),appearance);
            front.setScale(2,2,1); Transform t=new Transform(); t.postTranslate(0,0,-3); g.render(front,t);
            GpuRasterizer.enabled=false; t.setIdentity(); t.postTranslate(0,0,-2);
            g.render(mesh(0xffd04020),t); g.releaseTarget();
            assertEquals("sprite path="+path+" depth="+boundDepth+" write="+write,
                    boundDepth&&write?0xff20a0e0:0xffd04020,target.getBitmap().getPixel(16,16));
        }
    }

    @Test public void depthOffsetsClampRatherThanWrapAcrossCpuGpuBarriers() {
        for(boolean gpu:new boolean[]{false,true}) {
            Graphics3D g=renderer(); GpuRasterizer.enabled=gpu; Image target=image();
            g.bindTarget(target.getGraphics()); g.clear(null);
            Mesh front=mesh(0xff20a0e0); CompositingMode comp=new CompositingMode(); comp.setDepthOffset(0,-100000);
            front.getAppearance(0).setCompositingMode(comp); g.render(front,new Transform());
            GpuRasterizer.enabled=false; g.render(mesh(0xffd04020),new Transform()); g.releaseTarget();
            assertEquals(0xff20a0e0,target.getBitmap().getPixel(16,16));
        }
    }

    @Test public void biasedHardwareSurvivesClippingMasksAndContextReplay() {
        int[] expected=biased(false); assertArrayEquals(expected,biased(true));
    }

    private int[] biased(boolean lose) {
        Graphics3D g=renderer(); Image target=image(); g.bindTarget(target.getGraphics()); g.clear(null);
        Mesh base=mesh(0xffd04020),decal=mesh(0xff20a0e0);
        CompositingMode comp=new CompositingMode(); comp.setDepthOffset(-2,-1);
        decal.getAppearance(0).setCompositingMode(comp);
        g.render(base,new Transform()); g.setViewport(-3,-4,40,40); g.render(decal,new Transform());
        if(lose) { GpuRasterizer.lossPoint=2; GpuRasterizer.lossesRemaining=1; }
        g.releaseTarget(); int[] pixels=new int[1024]; target.getBitmap().getPixels(pixels,0,32,0,0,32,32);
        assertEquals(0xff20a0e0,pixels[16*32+16]); return pixels;
    }

    @Test public void warmedFrameUsesOneSubmissionAndPreservesOutput() {
        Graphics3D g=renderer(); Image target=image(); Mesh mesh=mesh(0xff20a0e0);
        g.bindTarget(target.getGraphics()); g.clear(null); g.render(mesh,new Transform()); g.releaseTarget();
        long submissions=GpuRasterizer.submissions;
        g.bindTarget(target.getGraphics()); g.clear(null); g.render(mesh,new Transform());
        assertEquals(submissions,GpuRasterizer.submissions);
        g.releaseTarget(); assertEquals(submissions+1,GpuRasterizer.submissions);
        assertEquals(0xff20a0e0,target.getBitmap().getPixel(16,16));
    }

    @Test public void deferredFirstDrawSurvivesRealContextLoss() {
        Graphics3D g=renderer(); Image target=image(); Mesh mesh=mesh(0xff20a0e0);
        g.bindTarget(target.getGraphics()); g.clear(null); g.render(mesh,new Transform()); g.releaseTarget();
        long recovered=GpuRasterizer.recoveries;
        g.bindTarget(target.getGraphics()); g.clear(null); g.render(mesh,new Transform());
        GpuRasterizer.lossPoint=1; GpuRasterizer.lossesRemaining=1; g.releaseTarget();
        assertEquals(recovered+1,GpuRasterizer.recoveries);
        assertEquals(0xff20a0e0,target.getBitmap().getPixel(16,16));
    }

    @Test public void manySmallMeshesRemainResidentWithinExistingByteBudget() {
        Graphics3D g=renderer(); Image target=image(); Mesh[] meshes=new Mesh[96];
        for(int i=0;i<meshes.length;i++) meshes[i]=mesh(0xff20a000+i);
        for(int pass=0;pass<2;pass++) {
            long uploads=GpuMeshCache.uploads,misses=GpuGeometry.cacheMisses;
            g.bindTarget(target.getGraphics()); g.clear(null);
            for(Mesh mesh:meshes) g.render(mesh,new Transform());
            g.releaseTarget();
            if(pass==1) { assertEquals(uploads,GpuMeshCache.uploads); assertEquals(misses,GpuGeometry.cacheMisses); }
            assertTrue(GpuGeometry.cachedBytes()<=GpuGeometry.CACHE_BYTES);
            assertTrue(GpuRasterizer.cachedMeshBytes()<=GpuMeshCache.byteLimit);
        }
        assertEquals(0xff20a05f,target.getBitmap().getPixel(16,16));
    }

    @Test public void deferredRawTargetSwitchCommitsPreviousTarget() {
        // Establish a validated context before a direct rasterizer target switch.
        Graphics3D g=renderer(); Image warm=image();
        g.bindTarget(warm.getGraphics()); g.clear(null); g.render(mesh(0xff20a0e0),new Transform()); g.releaseTarget();
        int[] first=new int[16],second=new int[16]; short[] z1=new short[16],z2=new short[16];
        Arrays.fill(z1,(short)32200); Arrays.fill(z2,(short)32200);
        Image2D tex=new Image2D(Image2D.RGBA,1,1); tex.set(0,0,1,1,new byte[]{32,64,96,-1});
        int[] f={0,4,0,4,0,0,0,0,0,0,1,1,3,100,256,0,-1,0,0,0};
        assertTrue(GpuRasterizer.sprite(first,z1,4,4,tex,new float[4],f,0,0,CompositingMode.REPLACE,true));
        assertTrue(GpuRasterizer.sprite(second,z2,4,4,tex,new float[4],f,0,0,CompositingMode.REPLACE,true));
        assertEquals(0xff204060,first[0]); GpuRasterizer.flush(); assertArrayEquals(first,second);
    }
}
