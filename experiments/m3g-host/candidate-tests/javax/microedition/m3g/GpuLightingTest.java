/* SPDX-License-Identifier: GPL-3.0-or-later */
package javax.microedition.m3g;

import javax.microedition.lcdui.Image;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public class GpuLightingTest {
    private static final int SIZE=96, BACKGROUND=0xff182838;

    @After public void restore() {
        GpuRasterizer.lossesRemaining=0; GpuRasterizer.lossPoint=0;
        GpuRasterizer.close(); Graphics3D.getInstance().releaseTarget();
        GpuGeometry.enabled=false; GpuRasterizer.enabled=false;
    }

    @Test public void flatLightingUsesLastVertexWithDifferentNormalsAndColors() {
        for(int light=0;light<4;light++) compare(scene(false,1,light,false,false),scene(true,1,light,false,false));
    }

    @Test public void twoSidedLightingFollowsWindingWithoutCulling() {
        compare(scene(false,2,1,false,false),scene(true,2,1,false,false));
    }

    @Test public void combinedFlatAndTwoSidedLightingMatchesPreparedPath() {
        for(int light=1;light<4;light++) compare(scene(false,3,light,false,false),scene(true,3,light,false,false));
    }

    @Test public void flatProvokingColorSurvivesClippingOfLastVertex() {
        compare(scene(false,5,1,false,false),scene(true,5,1,false,false));
    }

    @Test public void lightingModesRetainTextureFogAndAlphaBehavior() {
        compare(scene(false,7,2,false,false),scene(true,7,2,false,false));
    }

    @Test public void unlitFlatModeMatchesCorrectedPreparedColorBehavior() {
        compare(scene(false,9,1,false,false),scene(true,9,1,false,false));
    }

    @Test public void mixedPreparedAndHardwareCommandsResetShadingAndUniforms() {
        compare(scene(false,11,3,true,false),scene(true,11,3,true,false));
    }

    @Test public void queuedLightingSnapshotsSurviveMutationAndContextLoss() {
        int[] expected=scene(true,7,3,true,false);
        long recoveries=GpuRasterizer.recoveries;
        assertArrayEquals(expected,scene(true,7,3,true,true));
        assertEquals(recoveries+1,GpuRasterizer.recoveries);
    }

    @Test public void consecutiveCommandsReuseUnchangedUniformGroups() {
        long reused=GpuGeometry.uniformReuses;
        scene(true,3,1,false,false);
        assertTrue("Repeated state must not be uploaded for every object",GpuGeometry.uniformReuses>=reused+20);
    }

    @Test public void switchingLightingModesCompilesOnlyTwoOwnedPipelines() {
        long compiled=GpuRasterizer.pipelineCompilations;
        scene(true,11,2,true,false);
        assertEquals(compiled+2,GpuRasterizer.pipelineCompilations);
    }

    @Test public void unlitFlatRgbAndRgbaUseThirdVertexOnAllRasterPaths() {
        for(int path=0;path<3;path++) for(boolean alpha:new boolean[]{false,true}) exactFlat(path,alpha,false);
    }

    @Test public void clippedUnlitFlatColorIsNotInterpolatedWithDiscardedVertices() {
        for(int path=0;path<3;path++) exactFlat(path,false,true);
    }

    @Test public void nearestTexelsRemainExactWithFlatAndTwoSidedLighting() {
        for(boolean hardware:new boolean[]{false,true}) {
            Graphics3D.initGraphics3D(); GpuRasterizer.enabled=true; GpuGeometry.enabled=hardware;
            Graphics3D g=Graphics3D.getInstance(); Image2D image=new Image2D(Image2D.RGBA,64,64);
            Camera camera=new Camera(); camera.setParallel(4,1,1,10); g.setCamera(camera,new Transform());
            g.bindTarget(image,true,0); g.clear(null);
            VertexArray p=new VertexArray(4,3,1); p.set(0,4,new byte[]{-1,1,0,-1,-1,0,1,1,0,1,-1,0});
            VertexArray n=new VertexArray(4,3,1); n.set(0,4,new byte[]{0,0,127,0,0,127,0,0,127,0,0,127});
            VertexArray uv=new VertexArray(4,2,1); uv.set(0,4,new byte[]{0,0,0,1,1,0,1,1});
            VertexBuffer v=new VertexBuffer(); v.setPositions(p,1,null); v.setNormals(n); v.setTexCoords(0,uv,1,null);
            Texture2D texture=new Texture2D(new Image2D(Image2D.RGB,2,2,new byte[]{-1,0,0,0,-1,0,0,0,-1,-1,-1,0}));
            texture.setFiltering(Texture2D.FILTER_BASE_LEVEL,Texture2D.FILTER_NEAREST);
            texture.setBlending(Texture2D.FUNC_REPLACE);
            PolygonMode polygon=new PolygonMode(); polygon.setCulling(PolygonMode.CULL_NONE);
            polygon.setShading(PolygonMode.SHADE_FLAT); polygon.setTwoSidedLightingEnable(true);
            Material material=new Material(); material.setColor(Material.EMISSIVE,0xffffff);
            Appearance a=new Appearance(); a.setPolygonMode(polygon); a.setMaterial(material); a.setTexture(0,texture);
            Transform t=new Transform(); t.postTranslate(0,0,-3);
            long draws=GpuGeometry.draws;
            g.render(v,new TriangleStripArray(0,new int[]{4}),a,t); g.releaseTarget();
            if(hardware) assertEquals(draws+1,GpuGeometry.draws);
            int[] expected={0xffff0000,0xff00ff00,0xff0000ff,0xffffff00};
            for(int y=18;y<46;y++) for(int x=18;x<46;x++) {
                if(Math.abs(x-32)<2 || Math.abs(y-32)<2) continue;
                assertEquals("Nearest texel, hardware="+hardware,expected[(y>=32?2:0)+(x>=32?1:0)],image.image[y*64+x]);
            }
        }
    }

    private void exactFlat(int path,boolean alpha,boolean clipped) {
        Graphics3D.initGraphics3D(); GpuRasterizer.enabled=path!=0; GpuGeometry.enabled=path==2;
        Graphics3D g=Graphics3D.getInstance(); Image2D image=new Image2D(Image2D.RGBA,64,64);
        Camera camera=new Camera(); camera.setParallel(4,1,1,10); g.setCamera(camera,new Transform());
        g.bindTarget(image,true,0); g.clear(null);
        VertexArray p=new VertexArray(3,3,1); p.set(0,3,new byte[]{-1,1,0,-1,-1,0,1,0,clipped?(byte)3:0});
        VertexArray colors=new VertexArray(3,alpha?4:3,1);
        colors.set(0,3,alpha?new byte[]{-1,0,0,-1,0,-1,0,-1,0,64,-32,-128}:new byte[]{-1,0,0,0,-1,0,0,64,-32});
        VertexBuffer v=new VertexBuffer(); v.setPositions(p,1,null); v.setColors(colors);
        PolygonMode polygon=new PolygonMode(); polygon.setCulling(PolygonMode.CULL_NONE); polygon.setShading(PolygonMode.SHADE_FLAT);
        Appearance a=new Appearance(); a.setPolygonMode(polygon);
        Transform t=new Transform(); t.postTranslate(0,0,-3);
        long draws=GpuGeometry.draws; g.render(v,new TriangleStripArray(0,new int[]{3}),a,t); g.releaseTarget();
        if(path==2) assertEquals(draws+1,GpuGeometry.draws);
        int visible=0,expected=alpha?0x800040e0:0xff0040e0;
        for(int c:image.image) if(c!=0) { assertEquals("Third vertex color, raster path="+path,expected,c); visible++; }
        assertTrue("Nonempty flat triangle",visible>100);
    }

    int[] scene(boolean hardware,int mode,int lightKind,boolean mixed,boolean lose) {
        Graphics3D.initGraphics3D(); GpuRasterizer.enabled=true; GpuGeometry.enabled=hardware;
        Graphics3D g=Graphics3D.getInstance(); Image image=Image.createImage(SIZE,SIZE);
        Camera camera=new Camera(); camera.setPerspective(60,1,1,20); g.setCamera(camera,new Transform());
        g.bindTarget(image.getGraphics(),true,0);
        Background background=new Background(); background.setColor(BACKGROUND); g.clear(background);
        Light light=new Light(); light.setMode(new int[]{Light.AMBIENT,Light.DIRECTIONAL,Light.OMNI,Light.SPOT}[lightKind]);
        light.setIntensity(.8f); light.setColor(0xe8d8ff); light.setAttenuation(1,.02f,.01f); g.addLight(light,new Transform());
        VertexArray p=new VertexArray(4,3,1);
        p.set(0,4,new byte[]{-10,10,0,-10,-10,0,10,10,mode==5?(byte)28:0,10,-10,0});
        VertexArray n=new VertexArray(4,3,1);
        n.set(0,4,new byte[]{40,0,110,0,70,100,-70,0,90,0,-100,70});
        VertexArray colors=new VertexArray(4,4,1);
        colors.set(0,4,new byte[]{-1,40,20,-1,20,-1,40,-1,40,20,-1,-1,-1,-1,60,-1});
        VertexBuffer v=new VertexBuffer(); v.setPositions(p,.1f,null); v.setNormals(n); v.setColors(colors);
        IndexBuffer indices=new TriangleStripArray(0,new int[]{4});
        Material material=new Material(); material.setColor(Material.DIFFUSE,0xff90c0e0);
        material.setColor(Material.AMBIENT,0x403060); material.setColor(Material.EMISSIVE,0x101018);
        material.setColor(Material.SPECULAR,0x605040); material.setShininess(9); material.setVertexColorTrackingEnable(true);
        Appearance a=new Appearance(); if(mode!=9) a.setMaterial(material);
        PolygonMode polygon=new PolygonMode(); polygon.setCulling(PolygonMode.CULL_NONE);
        polygon.setShading((mode&1)!=0?PolygonMode.SHADE_FLAT:PolygonMode.SHADE_SMOOTH);
        polygon.setTwoSidedLightingEnable((mode&2)!=0); polygon.setLocalCameraLightingEnable(true);
        a.setPolygonMode(polygon);
        if(mode==7) {
            VertexArray uv=new VertexArray(4,2,1); uv.set(0,4,new byte[]{0,0,0,1,1,0,1,1}); v.setTexCoords(0,uv,1,null);
            Texture2D tex=new Texture2D(new Image2D(Image2D.RGB,2,2,new byte[]{-1,80,60,40,-1,80,80,60,-1,-1,-1,100}));
            tex.setBlending(Texture2D.FUNC_MODULATE); a.setTexture(0,tex);
            Fog fog=new Fog(); fog.setMode(Fog.LINEAR); fog.setLinear(1,12); fog.setColor(0x203040); a.setFog(fog);
            CompositingMode blend=new CompositingMode(); blend.setBlending(CompositingMode.ALPHA); a.setCompositingMode(blend);
            material.setColor(Material.DIFFUSE,0xc090c0e0); material.setVertexColorTrackingEnable(false);
        }
        long draws=GpuGeometry.draws;
        int count=mode==5?1:8,hardwareCount=0;
        for(int i=0;i<count;i++) {
            boolean raw=hardware && (!mixed || i%3!=1); GpuGeometry.enabled=raw;
            // Changes must be queued, not hidden by a readback or a new GL context.
            polygon.setWinding((i&1)==0?PolygonMode.WINDING_CCW:PolygonMode.WINDING_CW);
            if(mixed) {
                polygon.setShading(i%3==0?PolygonMode.SHADE_FLAT:PolygonMode.SHADE_SMOOTH);
                polygon.setTwoSidedLightingEnable(i%2==0);
                material.setColor(Material.EMISSIVE,0x101018+i*0x030509);
                light.setIntensity(.4f+i*.06f);
                polygon.setCulling(i==3?PolygonMode.CULL_FRONT:PolygonMode.CULL_NONE);
            }
            Transform t=new Transform();
            t.postTranslate(mode==5?0:(i%4-1.5f)*.8f,mode==5?0:(i/4-.5f)*1.4f,-3.5f);
            // Keep nearest-texel boundaries away from pixel centers: GL subpixel
            // precision differs between prepared and homogeneous hardware geometry.
            if(mode==7) t.postTranslate(.017f,.017f,0);
            t.postScale(mode==5?1:((i&2)!=0?-.32f:.32f),mode==5?1:.55f,1);
            g.render(v,indices,a,t); if(raw) hardwareCount++;
        }
        assertEquals("Hardware geometry must handle supported shading",draws+hardwareCount,GpuGeometry.draws);
        // All queued commands own snapshots of these mutable objects.
        p.set(0,4,new byte[12]); n.set(0,4,new byte[12]); light.setIntensity(0);
        material.setColor(Material.EMISSIVE,0); polygon.setWinding(PolygonMode.WINDING_CW);
        if(lose) { GpuRasterizer.lossPoint=2; GpuRasterizer.lossesRemaining=1; }
        g.releaseTarget();
        int[] out=new int[SIZE*SIZE]; image.getBitmap().getPixels(out,0,SIZE,0,0,SIZE,SIZE); return out;
    }

    private void compare(int[] expected,int[] actual) {
        int visible=0,bad=0,max=0;
        for(int i=0;i<actual.length;i++) {
            if(actual[i]!=BACKGROUND) visible++;
            int error=0;
            for(int shift=0;shift<32;shift+=8) error=Math.max(error,Math.abs((actual[i]>>>shift&255)-(expected[i]>>>shift&255)));
            if(error>4) bad++; max=Math.max(max,error);
        }
        assertTrue("Visible scene",visible>100);
        StringBuilder samples=new StringBuilder();
        if(bad>actual.length*.015) {
            saveFailure("expected",expected); saveFailure("actual",actual);
            int reported=0;
            for(int i=0;i<actual.length && reported<16;i++) {
                int error=0;
                for(int shift=0;shift<32;shift+=8) error=Math.max(error,Math.abs((actual[i]>>>shift&255)-(expected[i]>>>shift&255)));
                if(error>4) {
                    samples.append(" bad@").append(i%SIZE).append(',').append(i/SIZE).append(':')
                            .append(Integer.toHexString(expected[i])).append('/').append(Integer.toHexString(actual[i]));
                    reported++;
                }
            }
        }
        if(bad>actual.length*.015) for(int y:new int[]{64,31}) for(int x:new int[]{20,39,58,77}) {
            int i=y*SIZE+x;
            samples.append(" ").append(x).append(',').append(y).append(':')
                    .append(Integer.toHexString(expected[i])).append('/').append(Integer.toHexString(actual[i]));
        }
        assertTrue("Different pixels="+bad+", max channel error="+max+", renderer="+GpuRasterizer.renderer+samples,bad<=actual.length*.015);
    }

    private void saveFailure(String name,int[] pixels) {
        java.io.File dir=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getExternalFilesDir(null);
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(pixels,SIZE,SIZE,android.graphics.Bitmap.Config.ARGB_8888);
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(dir,"lighting-"+name+".png"))) {
            assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));
        } catch(java.io.IOException error) { throw new AssertionError(error); }
        finally { bitmap.recycle(); }
    }
}
