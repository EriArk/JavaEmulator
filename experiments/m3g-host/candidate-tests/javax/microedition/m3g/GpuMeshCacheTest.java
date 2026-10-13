/* SPDX-License-Identifier: GPL-3.0-or-later */
package javax.microedition.m3g;

import javax.microedition.lcdui.Image;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public class GpuMeshCacheTest {
    private Graphics3D g;
    private Image image;
    private VertexArray points;
    private VertexBuffer vertices;
    private IndexBuffer indices;
    private Appearance appearance;
    private Transform transform;

    @After public void restore() {
        GpuRasterizer.lossesRemaining=0; GpuRasterizer.lossPoint=0;
        GpuRasterizer.close(); Graphics3D.getInstance().releaseTarget();
        GpuGeometry.enabled=false; GpuRasterizer.enabled=false;
        GpuMeshCache.enabled=true; GpuMeshCache.byteLimit=4*1024*1024;
    }

    private void setup() {
        Graphics3D.initGraphics3D(); GpuRasterizer.enabled=true; GpuGeometry.enabled=true;
        g=Graphics3D.getInstance(); image=Image.createImage(64,64);
        Camera camera=new Camera(); camera.setParallel(4,1,1,10); g.setCamera(camera,new Transform());
        points=new VertexArray(4,3,1); points.set(0,4,new byte[]{-1,1,0,-1,-1,0,1,1,0,1,-1,0});
        vertices=new VertexBuffer(); vertices.setPositions(points,1,null); vertices.setDefaultColor(0xff30a060);
        indices=new TriangleStripArray(0,new int[]{4}); appearance=new Appearance();
        transform=new Transform(); transform.postTranslate(0,0,-3);
    }
    private void begin() { g.bindTarget(image.getGraphics(),true,0); g.clear(null); }
    private void draw() { g.render(vertices,indices,appearance,transform); }
    private int[] pixels() {
        g.releaseTarget(); int[] out=new int[4096]; image.getBitmap().getPixels(out,0,64,0,0,64,64); return out;
    }

    @Test public void repeatedFramesUploadOneCompactMeshAndCloseReleasesIt() {
        setup(); long uploads=GpuMeshCache.uploads,hits=GpuMeshCache.hits,bytes=GpuMeshCache.uploadedBytes;
        for(int i=0;i<8;i++) { begin(); draw(); assertEquals(0xff30a060,pixels()[32*64+32]); }
        assertEquals(uploads+1,GpuMeshCache.uploads); assertEquals(hits+7,GpuMeshCache.hits);
        assertEquals(4*92+6*2,GpuMeshCache.uploadedBytes-bytes);
        assertEquals(380,GpuRasterizer.cachedMeshBytes());
        GpuRasterizer.close(); assertEquals(0,GpuRasterizer.cachedMeshBytes());
    }

    @Test public void disablingResidencyKeepsIdenticalPixelsWithoutRetainingBuffers() {
        setup(); begin(); draw(); int[] expected=pixels();
        GpuMeshCache.enabled=false; long uploads=GpuMeshCache.uploads;
        for(int i=0;i<3;i++) { begin(); draw(); assertArrayEquals(expected,pixels()); }
        assertEquals(uploads+3,GpuMeshCache.uploads); assertEquals(0,GpuRasterizer.cachedMeshBytes());
    }

    @Test public void evictionAndOversizedTransientMeshesRespectByteBudget() {
        setup(); GpuMeshCache.byteLimit=400;
        long uploads=GpuMeshCache.uploads;
        for(int i=0;i<6;i++) {
            vertices.setDefaultColor(0xff304060+i*0x10000); begin(); draw();
            assertEquals(vertices.getDefaultColor(),pixels()[32*64+32]);
            assertTrue(GpuRasterizer.cachedMeshBytes()<=400);
        }
        assertEquals(uploads+6,GpuMeshCache.uploads);
        GpuMeshCache.byteLimit=100; begin(); draw();
        assertEquals(vertices.getDefaultColor(),pixels()[32*64+32]);
        assertEquals(0,GpuRasterizer.cachedMeshBytes());
    }

    @Test public void queuedRevisionsSurviveEvictionAndRealContextLoss() {
        int[] expected=revisionScene(false);
        long recoveries=GpuRasterizer.recoveries;
        assertArrayEquals(expected,revisionScene(true));
        assertEquals(recoveries+1,GpuRasterizer.recoveries);
        assertEquals(0xff30a060,expected[32*64+12]);
        assertEquals(0xffe05030,expected[32*64+44]);
    }

    @Test public void oversizedTransientMeshDoesNotEvictReusableSmallMeshes() {
        setup(); GpuMeshCache.byteLimit=400; begin(); draw(); pixels();
        VertexBuffer small=vertices; IndexBuffer smallIndices=indices;
        VertexArray big=new VertexArray(8,3,1); big.set(0,8,new byte[]{-1,1,0,-1,-1,0,1,1,0,1,-1,0,-1,1,0,-1,-1,0,1,1,0,1,-1,0});
        vertices=new VertexBuffer(); vertices.setPositions(big,1,null); vertices.setDefaultColor(0xff30a060);
        indices=new TriangleStripArray(0,new int[]{4,4}); begin(); draw(); pixels();
        assertEquals(380,GpuRasterizer.cachedMeshBytes());
        long uploads=GpuMeshCache.uploads,hits=GpuMeshCache.hits;
        vertices=small; indices=smallIndices; begin(); draw(); pixels();
        assertEquals(uploads,GpuMeshCache.uploads); assertEquals(hits+1,GpuMeshCache.hits);
    }
    private int[] revisionScene(boolean lose) {
        setup(); GpuMeshCache.byteLimit=400; begin();
        transform.postTranslate(-1,0,0); draw();
        vertices.setDefaultColor(0xffe05030); transform.postTranslate(2,0,0); draw();
        points.set(0,4,new byte[12]); vertices.setDefaultColor(0xff000000);
        if(lose) { GpuRasterizer.lossPoint=2; GpuRasterizer.lossesRemaining=1; }
        return pixels();
    }

    @Test public void alternatingPreparedAndIndexedCommandsPreserveOffsetsAndPaintOrder() {
        assertArrayEquals(mixed(false),mixed(true));
    }
    private int[] mixed(boolean hardware) {
        setup(); begin();
        for(int i=0;i<12;i++) {
            GpuGeometry.enabled=hardware && i%2==0;
            transform=new Transform(); transform.postTranslate(-1.5f+i*.25f,0,-3+i*.01f); transform.postScale(.6f,.6f,1);
            vertices.setDefaultColor(0xff304050+i*0x090507); draw();
        }
        return pixels();
    }

    @Test public void fourTextureUnitsAndSparseLastUnitUseHardwareWithNormals() {
        for(boolean sparse:new boolean[]{false,true}) {
            int[] expected=textured(false,sparse);
            long hardware=GpuGeometry.draws;
            assertArrayEquals(expected,textured(true,sparse));
            assertEquals(hardware+1,GpuGeometry.draws);
            assertTrue(expected[32*64+32]!=0);
        }
    }
    private int[] textured(boolean hardware,boolean sparse) {
        setup(); GpuGeometry.enabled=hardware;
        VertexArray uv=new VertexArray(4,2,1); uv.set(0,4,new byte[]{0,0,0,1,1,0,1,1});
        VertexArray normals=new VertexArray(4,3,1); normals.set(0,4,new byte[]{0,0,127,0,0,127,0,0,127,0,0,127});
        vertices.setNormals(normals); Material material=new Material(); material.setColor(Material.EMISSIVE,0xffffff);
        appearance.setMaterial(material);
        for(int i=sparse?3:0;i<4;i++) {
            vertices.setTexCoords(i,uv,1,null);
            Texture2D texture=new Texture2D(new Image2D(Image2D.RGB,1,1,new byte[]{(byte)(30+i*40),(byte)(180-i*30),80}));
            texture.setBlending(i==0||sparse?Texture2D.FUNC_REPLACE:Texture2D.FUNC_ADD); appearance.setTexture(i,texture);
        }
        begin(); draw(); return pixels();
    }

    @Test public void uvAndNormalRevisionsInvalidateResidentGeometry() {
        setup(); VertexArray uv=new VertexArray(4,2,1); uv.set(0,4,new byte[8]); vertices.setTexCoords(0,uv,1,null);
        Texture2D texture=new Texture2D(new Image2D(Image2D.RGB,2,1,new byte[]{-1,0,0,0,-1,0}));
        texture.setBlending(Texture2D.FUNC_REPLACE); appearance.setTexture(0,texture);
        VertexArray normals=new VertexArray(4,3,1); normals.set(0,4,new byte[12]); vertices.setNormals(normals);
        appearance.setMaterial(new Material());
        begin(); draw(); assertEquals(0xffff0000,pixels()[32*64+32]);
        long uploads=GpuMeshCache.uploads;
        uv.set(0,4,new byte[]{1,0,1,0,1,0,1,0}); vertices.setTexCoords(0,uv,.5f,null);
        begin(); draw(); assertEquals(0xff00ff00,pixels()[32*64+32]);
        normals.set(0,4,new byte[]{0,0,127,0,0,127,0,0,127,0,0,127});
        begin(); draw(); pixels(); assertEquals(uploads+2,GpuMeshCache.uploads);
    }

    @Test public void persistentLossNeverCommitsPartialIndexedFrame() {
        setup(); begin(); draw(); vertices.setDefaultColor(0xffff0000); draw();
        int[] before=new int[4096]; image.getBitmap().getPixels(before,0,64,0,0,64,64);
        GpuRasterizer.lossPoint=2; GpuRasterizer.lossesRemaining=2;
        try { pixels(); fail("Persistent loss must abort the frame"); }
        catch(IllegalStateException expected) { assertTrue(expected.getMessage().contains("not committed")); }
        int[] actual=new int[4096]; image.getBitmap().getPixels(actual,0,64,0,0,64,64);
        assertArrayEquals(before,actual);
    }

    @Test public void unsignedIndicesAbove32767RemainVisible() {
        setup(); int count=32772;
        short[] coords=new short[count*3];
        System.arraycopy(new short[]{-1,-1,0,1,-1,0,0,1,0},0,coords,(count-3)*3,9);
        points=new VertexArray(count,3,2); points.set(0,count,coords);
        vertices=new VertexBuffer(); vertices.setPositions(points,1,null); vertices.setDefaultColor(0xff30a060);
        indices=new TriangleStripArray(0,new int[]{count});
        PolygonMode polygon=new PolygonMode(); polygon.setCulling(PolygonMode.CULL_NONE); appearance.setPolygonMode(polygon);
        long bytes=GpuMeshCache.uploadedBytes;
        begin(); draw(); assertEquals(0xff30a060,pixels()[32*64+32]);
        assertEquals(count*92+(count-2)*6,GpuMeshCache.uploadedBytes-bytes);
    }

    @Test public void entryCountIsBoundedEvenForManyTinyMeshes() {
        setup();
        for(int i=0;i<GpuGeometry.CACHE_ENTRIES+6;i++) {
            vertices=new VertexBuffer(); vertices.setPositions(points,1,null); vertices.setDefaultColor(0xff30a060+i);
            begin(); draw(); assertEquals(vertices.getDefaultColor(),pixels()[32*64+32]);
            assertTrue(GpuRasterizer.cachedMeshBytes()<=GpuGeometry.CACHE_ENTRIES*380);
        }
        assertEquals(GpuGeometry.CACHE_ENTRIES*380,GpuRasterizer.cachedMeshBytes());
    }
}
