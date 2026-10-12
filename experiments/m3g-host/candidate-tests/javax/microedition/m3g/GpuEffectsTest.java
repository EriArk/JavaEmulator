package javax.microedition.m3g;

import javax.microedition.lcdui.Image;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public class GpuEffectsTest {
    @After public void restore() {
        Graphics3D.getInstance().releaseTarget(); GpuRasterizer.close(); GpuRasterizer.enabled=false;
    }

    @Test public void signedUnitsAndSlopesPreserveCoplanarOrdering() {
        for(boolean gpu:new boolean[]{false,true}) for(boolean slope:new boolean[]{false,true})
            for(int sign:new int[]{-1,1}) {
                Graphics3D.initGraphics3D(); Graphics3D g=Graphics3D.getInstance();
                Image2D target=bind(g); GpuRasterizer.enabled=gpu;
                Mesh base=mesh(0xffa02040,slope), overlay=mesh(0xff20c060,slope);
                CompositingMode mode=new CompositingMode();
                mode.setDepthOffset(slope?sign*2:0,slope?0:sign*100000000f);
                overlay.getAppearance(0).setCompositingMode(mode);
                long offsets=GpuRasterizer.offsetDraws,draws=GpuRasterizer.draws;
                g.render(base,new Transform()); g.render(overlay,new Transform()); g.releaseTarget();
                if(gpu) { assertEquals(draws+2,GpuRasterizer.draws); assertEquals(offsets+1,GpuRasterizer.offsetDraws); }
                assertEquals("gpu="+gpu+" slope="+slope+" sign="+sign,
                        sign<0?0xff20c060:0xffa02040,target.image[15*32+16]);
            }
    }

    @Test public void offsetsRespectDisabledDepthAndWriteMasks() {
        for(boolean write:new boolean[]{false,true}) for(boolean test:new boolean[]{false,true}) {
            Graphics3D.initGraphics3D(); Graphics3D g=Graphics3D.getInstance(); Image2D target=bind(g);
            GpuRasterizer.enabled=true; Mesh first=mesh(0xff20c060,false);
            CompositingMode mode=new CompositingMode(); mode.setDepthWriteEnable(write); mode.setDepthTestEnable(test);
            mode.setDepthOffset(0,test?-100000000f:Float.POSITIVE_INFINITY);
            first.getAppearance(0).setCompositingMode(mode); long before=GpuRasterizer.draws;
            g.render(first,new Transform()); assertEquals(before+1,GpuRasterizer.draws);
            // A CPU draw at the original depth must observe the biased GPU depth only if written.
            GpuRasterizer.enabled=false; g.render(mesh(0xffa02040,false),new Transform()); g.releaseTarget();
            assertEquals(write && test?0xff20c060:0xffa02040,target.image[15*32+16]);
        }
    }

    @Test public void multiplicationHasKnownColorsAndSaturatesAllChannels() {
        for(int mode:new int[]{CompositingMode.MODULATE,CompositingMode.MODULATE_X2})
            for(int foreground:new int[]{0x80604020,0xffe0c0a0,0x00ffffff}) {
                Graphics3D.initGraphics3D(); Graphics3D g=Graphics3D.getInstance(); Image2D target=bind(g);
                Background bg=new Background(); bg.setColor(0xc0a08060); g.clear(bg);
                Mesh overlay=mesh(foreground,false); CompositingMode comp=new CompositingMode();
                comp.setBlending(mode); overlay.getAppearance(0).setCompositingMode(comp);
                GpuRasterizer.enabled=true; long before=GpuRasterizer.multiplyDraws;
                g.render(overlay,new Transform()); g.releaseTarget(); assertEquals(before+1,GpuRasterizer.multiplyDraws);
                // Existing candidate semantics treat zero fragment alpha as opaque after threshold testing.
                int source=foreground>>>24==0?foreground|0xff000000:foreground;
                for(int shift=0;shift<32;shift+=8) {
                    int expected=Math.min(255,(((source>>>shift)&255)*((0xc0a08060>>>shift)&255))
                            >> (mode==CompositingMode.MODULATE?8:7));
                    assertTrue("channel "+shift+" mode="+mode,
                            Math.abs(expected-((target.image[15*32+16]>>>shift)&255))<=2);
                }
            }
    }

    @Test public void clippedMeshesAndBlendSwitchesKeepOneResidentFrame() {
        Graphics3D.initGraphics3D(); Graphics3D g=Graphics3D.getInstance();
        Image target=Image.createImage(32,32); g.bindTarget(target.getGraphics(),true,0); camera(g); g.clear(null);
        GpuRasterizer.enabled=true;
        long uploads=GpuRasterizer.uploads,reads=GpuRasterizer.readbacks,empty=GpuRasterizer.emptyDraws;
        Mesh overlay=mesh(0xff808080,false);
        Transform outside=new Transform(); outside.postTranslate(0,0,-20);
        // Also exercise a clipped first mesh, before a rasterized triangle exists.
        g.render(overlay,outside);
        g.render(mesh(0xffa0c0e0,false),new Transform());
        CompositingMode comp=new CompositingMode(); comp.setDepthOffset(0,-100000000f);
        overlay.getAppearance(0).setCompositingMode(comp);
        for(int blend:new int[]{CompositingMode.MODULATE,CompositingMode.MODULATE_X2,
                CompositingMode.ALPHA,CompositingMode.ALPHA_ADD,CompositingMode.REPLACE}) {
            g.render(overlay,outside); comp.setBlending(blend); g.render(overlay,new Transform());
        }
        assertEquals(empty+6,GpuRasterizer.emptyDraws);
        assertEquals(uploads+1,GpuRasterizer.uploads); assertEquals(reads,GpuRasterizer.readbacks);
        g.releaseTarget(); assertEquals(reads+1,GpuRasterizer.readbacks);
        assertEquals(0xff808080,target.getBitmap().getPixel(16,15));
    }

    @Test public void outOfRangeOffsetFallsBackBeforeGpuWrites() {
        int[] pixels=new int[2];
        for(int path=0;path<2;path++) {
            Graphics3D.initGraphics3D(); Graphics3D g=Graphics3D.getInstance(); Image2D target=bind(g);
            GpuRasterizer.enabled=path==1;
            g.render(mesh(0xff204060,false),new Transform());
            Mesh huge=mesh(0xffa0c0e0,false); CompositingMode mode=new CompositingMode();
            mode.setDepthOffset(0,Float.MAX_VALUE); huge.getAppearance(0).setCompositingMode(mode);
            long before=GpuRasterizer.draws; g.render(huge,new Transform()); g.releaseTarget();
            assertEquals(before,GpuRasterizer.draws); pixels[path]=target.image[15*32+16];
        }
        assertEquals(pixels[0],pixels[1]);
    }

    @Test public void negativeCpuDepthRemainsInFrontOfLaterGpuGeometry() {
        Graphics3D.initGraphics3D(); Graphics3D g=Graphics3D.getInstance(); Image2D target=bind(g);
        GpuRasterizer.enabled=true;
        g.render(mesh(0xff204060,false),new Transform());
        Mesh outside=mesh(0xff20c060,false); CompositingMode mode=new CompositingMode();
        mode.setDepthOffset(0,-100000000000f); outside.getAppearance(0).setCompositingMode(mode);
        g.render(outside,new Transform());
        assertEquals(0xff20c060,target.image[15*32+16]);
        g.render(mesh(0xffa02040,false),new Transform());
        assertEquals("Negative CPU depth must occlude later positive depth",0xff20c060,target.image[15*32+16]);
        g.clear(null); long before=GpuRasterizer.draws;
        g.render(mesh(0xffa02040,false),new Transform()); g.releaseTarget();
        assertEquals("Cleared depth may use GPU again",before+1,GpuRasterizer.draws);
        assertEquals(0xffa02040,target.image[15*32+16]);
    }

    private static Image2D bind(Graphics3D g) {
        Image2D target=new Image2D(Image2D.RGBA,32,32); g.bindTarget(target,true,0); camera(g); g.clear(null); return target;
    }
    private static void camera(Graphics3D g) {
        Camera camera=new Camera(); camera.setParallel(4,1,1,10); g.setCamera(camera,new Transform());
    }
    private static Mesh mesh(int color,boolean slope) {
        VertexArray points=new VertexArray(4,3,1);
        points.set(0,4,new byte[]{-1,1,-3,-1,-1,-3,1,1,(byte)(slope?-4:-3),1,-1,(byte)(slope?-4:-3)});
        VertexBuffer vertices=new VertexBuffer(); vertices.setPositions(points,1,null); vertices.setDefaultColor(color);
        Appearance style=new Appearance(); PolygonMode polygon=new PolygonMode(); polygon.setCulling(PolygonMode.CULL_NONE);
        style.setPolygonMode(polygon); return new Mesh(vertices,new TriangleStripArray(0,new int[]{4}),style);
    }
}
