/* SPDX-License-Identifier: GPL-3.0-or-later */
package javax.microedition.m3g;

import javax.microedition.lcdui.Image;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public class GpuGeometryTest {
    @After public void restore() {
        GpuRasterizer.lossesRemaining=0; GpuRasterizer.lossPoint=0;
        GpuRasterizer.close(); Graphics3D.getInstance().releaseTarget();
        GpuGeometry.enabled=false; GpuRasterizer.enabled=false;
    }

    @Test public void unlitPerspectiveParallelAndViewportMatchPreparedPath() {
        for(boolean parallel:new boolean[]{false,true}) for(int mode=0;mode<3;mode++)
            compare(render(false,parallel,mode,0,false,false),render(true,parallel,mode,0,false,false),2,0.025);
    }

    @Test public void allLightTypesColorTrackingAndLocalViewerMatchPreparedPath() {
        for(int light=1;light<=6;light++)
            compare(render(false,false,0,light,false,false),render(true,false,0,light,false,false),3,0.025);
    }

    @Test public void textureAndFogFollowPerspectiveAndParallelCamera() {
        for(boolean parallel:new boolean[]{false,true}) for(int mode=3;mode<=5;mode++)
            compare(render(false,parallel,mode,1,false,false),render(true,parallel,mode,1,false,false),3,0.03);
    }

    @Test public void queuedGeometryAndMaterialsAreSnapshotsAcrossMutationAndContextLoss() {
        int[] expected=render(true,false,3,5,false,false);
        assertArrayEquals(expected,render(true,false,3,5,true,false));
        long recovered=GpuRasterizer.recoveries;
        assertArrayEquals(expected,render(true,false,3,5,true,true));
        assertEquals(recovered+1,GpuRasterizer.recoveries);
    }

    @Test public void unsupportedFlatTwoSidedAndDepthOffsetStayOnPreparedPath() {
        for(int mode=6;mode<=8;mode++) {
            int[] expected=render(false,false,mode,1,false,false);
            long draws=GpuGeometry.draws;
            assertArrayEquals(expected,render(true,false,mode,1,false,false));
            assertEquals(draws,GpuGeometry.draws);
        }
    }

    @Test public void nearPlaneClippingAndOffscreenGeometryRemainVisible() {
        compare(render(false,false,9,0,false,false),render(true,false,9,0,false,false),3,0.04);
    }

    @Test public void windingCullingAndMirroringMatchPreparedPath() {
        for(int mode=10;mode<=13;mode++)
            assertArrayEquals(render(false,false,mode,0,false,false),render(true,false,mode,0,false,false));
    }

    @Test public void cachedVerticesRefreshForMutationAndOldSnapshotsRemainImmutable() {
        Graphics3D.initGraphics3D(); GpuRasterizer.enabled=true; GpuGeometry.enabled=true;
        Graphics3D g=Graphics3D.getInstance(); Image2D image=new Image2D(Image2D.RGBA,32,32);
        Camera c=new Camera(); c.setParallel(4,1,1,10); g.setCamera(c,new Transform()); g.bindTarget(image,true,0);
        VertexArray p=new VertexArray(4,3,1); byte[] coords={-1,1,0,-1,-1,0,1,1,0,1,-1,0}; p.set(0,4,coords);
        VertexBuffer v=new VertexBuffer(); v.setPositions(p,1,null); v.setDefaultColor(0xff20a0e0);
        Appearance a=new Appearance(); IndexBuffer indices=new TriangleStripArray(0,new int[]{4});
        Transform t=new Transform(); t.postTranslate(0,0,-3);
        long hits=GpuGeometry.cacheHits,misses=GpuGeometry.cacheMisses;
        g.render(v,indices,a,t); g.render(v,indices,a,t);
        assertEquals(hits+1,GpuGeometry.cacheHits); assertEquals(misses+1,GpuGeometry.cacheMisses);
        assertEquals(0xff20a0e0,image.image[16*32+16]);
        g.clear(null); p.set(0,4,new byte[12]); g.render(v,indices,a,t);
        assertEquals(0,image.image[16*32+16]); assertEquals(misses+2,GpuGeometry.cacheMisses);
        p.set(0,4,coords); v.setDefaultColor(0xffe04020); g.render(v,indices,a,t);
        assertEquals(0xffe04020,image.image[16*32+16]); assertEquals(misses+3,GpuGeometry.cacheMisses);
        // getIndexArray is inherited and exposes mutable storage; do not assume it is immutable.
        java.util.Arrays.fill(indices.getIndexArray(),0); g.clear(null); g.render(v,indices,a,t);
        assertEquals(0,image.image[16*32+16]); assertEquals(misses+4,GpuGeometry.cacheMisses);
        assertTrue(GpuGeometry.cachedBytes()<=GpuGeometry.CACHE_BYTES);
        g.releaseTarget(); GpuRasterizer.close(); assertEquals(0,GpuGeometry.cachedBytes());
    }

    private int[] render(boolean hardware,boolean parallel,int mode,int lighting,boolean mutate,boolean lose) {
        Graphics3D.initGraphics3D(); GpuRasterizer.enabled=true; GpuGeometry.enabled=hardware;
        Graphics3D g=Graphics3D.getInstance(); int w=96,h=80;
        Image image=Image.createImage(w,h);
        Camera camera=new Camera();
        if(parallel) camera.setParallel(4,w/(float)h,1,20); else camera.setPerspective(60,w/(float)h,1,20);
        g.setCamera(camera,new Transform()); g.bindTarget(image.getGraphics(),true,0);
        Background background=new Background(); background.setColor(0xff182838); g.clear(background);
        if(mode==1) g.setViewport(8,6,71,62);
        if(mode==2) g.setDepthRange(.25f,.75f);
        VertexArray points=new VertexArray(4,3,2);
        points.set(0,4,new short[]{-100,100,0,-100,-100,0,100,100,-30,100,-100,-30});
        VertexBuffer vb=new VertexBuffer(); vb.setPositions(points,.01f,null); vb.setDefaultColor(0xffa4b8dc);
        VertexArray normals=new VertexArray(4,3,1);
        normals.set(0,4,new byte[]{0,0,127,0,0,127,0,0,127,0,0,127}); vb.setNormals(normals);
        Appearance style=new Appearance(); PolygonMode poly=new PolygonMode();
        poly.setCulling(PolygonMode.CULL_NONE); poly.setPerspectiveCorrectionEnable(true); style.setPolygonMode(poly);
        Material material=new Material(); material.setColor(Material.DIFFUSE,0xff80b0d0);
        material.setColor(Material.AMBIENT,0x607090); material.setColor(Material.EMISSIVE,0x080408);
        material.setColor(Material.SPECULAR,0x201018); material.setShininess(7);
        if(lighting!=0) {
            style.setMaterial(material);
            Light light=new Light(); light.setMode(lighting<=4?new int[]{0,Light.AMBIENT,Light.DIRECTIONAL,Light.OMNI,Light.SPOT}[lighting]:Light.DIRECTIONAL);
            light.setIntensity(.72f); light.setColor(0xe0ffe8); light.setAttenuation(1,.03f,.01f);
            Transform lightTransform=new Transform(); lightTransform.postTranslate(.2f,.1f,0);
            g.addLight(light,lightTransform);
            if(lighting>=5) { material.setVertexColorTrackingEnable(true); poly.setLocalCameraLightingEnable(true); }
            if(lighting==6) light.setScope(2);
        }
        if(mode>=3 && mode<=5) {
            VertexArray coords=new VertexArray(4,2,1); coords.set(0,4,new byte[]{0,0,0,1,1,0,1,1}); vb.setTexCoords(0,coords,1,null);
            byte[] bytes=new byte[8*8*3];
            for(int i=0;i<64;i++) { bytes[i*3]=(byte)(i%8*24+30);bytes[i*3+1]=(byte)(i/8*22+40);bytes[i*3+2]=(byte)160; }
            Texture2D texture=new Texture2D(new Image2D(Image2D.RGB,8,8,bytes));
            texture.setFiltering(Texture2D.FILTER_BASE_LEVEL,Texture2D.FILTER_LINEAR);
            texture.setBlending(Texture2D.FUNC_MODULATE); style.setTexture(0,texture);
            if(mode>=4) { Fog fog=new Fog(); fog.setMode(mode==4?Fog.LINEAR:Fog.EXPONENTIAL); fog.setLinear(1,10); fog.setDensity(.1f); fog.setColor(0x408060); style.setFog(fog); }
        }
        if(mode==6) poly.setShading(PolygonMode.SHADE_FLAT);
        if(mode==7) poly.setTwoSidedLightingEnable(true);
        if(mode==8) { CompositingMode comp=new CompositingMode();comp.setDepthOffset(.25f,1);style.setCompositingMode(comp); }
        if(mode>=10) { poly.setCulling(mode==10?PolygonMode.CULL_FRONT:PolygonMode.CULL_BACK); if(mode>=12) poly.setWinding(PolygonMode.WINDING_CW); }
        Transform transform=new Transform(); transform.postTranslate(0,0,mode==9?-1.05f:-3);
        transform.postRotate(18,0,1,0); transform.postScale(1,.85f,1.2f);
        if(mode==13) transform.postScale(-1,1,1);
        Mesh mesh=new Mesh(vb,new TriangleStripArray(0,new int[]{4}),style);
        long draws=GpuGeometry.draws;
        g.render(mesh,transform); transform.postTranslate(.12f,0,-.01f); g.render(mesh,transform);
        if(hardware && (mode<6 || mode>=9)) assertEquals("hardware stage must run",draws+2,GpuGeometry.draws);
        if(mutate) {
            points.set(0,4,new short[12]); material.setColor(Material.DIFFUSE,0xff000000);
            transform.postScale(0,0,0); vb.setDefaultColor(0xff000000);
        }
        if(lose) { GpuRasterizer.lossPoint=2; GpuRasterizer.lossesRemaining=1; }
        g.releaseTarget();
        int[] result=new int[w*h]; image.getBitmap().getPixels(result,0,w,0,0,w,h);
        return result;
    }

    private void compare(int[] expected,int[] actual,int tolerance,double fraction) {
        int bad=0,visible=0,max=0;
        for(int i=0;i<expected.length;i++) {
            if(actual[i]!=0xff182838) visible++;
            int error=0; for(int shift=0;shift<32;shift+=8) error=Math.max(error,Math.abs(((actual[i]>>>shift)&255)-((expected[i]>>>shift)&255)));
            max=Math.max(max,error); if(error>tolerance) bad++;
        }
        assertTrue("visible geometry",visible>100);
        assertTrue("different pixels="+bad+"/"+expected.length+", max channel error="+max,bad<=expected.length*fraction);
    }
}
