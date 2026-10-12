package javax.microedition.m3g;

import javax.microedition.lcdui.Image;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

/** Exact pixels against the retained Java path, including mixed execution. */
public class NativeRasterizerTest {
    @After public void restore() { NativeRasterizer.enabled = true; Graphics3D.getInstance().releaseTarget(); }

    @Test public void nativePixelsMatchJavaAcrossPipelineStates() {
        for (int mode : new int[]{CompositingMode.REPLACE, CompositingMode.ALPHA,
                CompositingMode.ALPHA_ADD, CompositingMode.MODULATE, CompositingMode.MODULATE_X2}) {
            for (int variant = 0; variant < 8; variant++) {
                int[] javaPixels = scene(false, mode, variant, false);
                int[] nativePixels = scene(true, mode, variant, false);
                assertArrayEquals("mode " + mode + ", variant " + variant, javaPixels, nativePixels);
            }
        }
    }

    @Test public void softwareFallbackSharesDepthAndColorWithNativeDraws() {
        for (int variant = 0; variant < 8; variant++) {
            assertArrayEquals("mixed variant " + variant,
                    scene(false, CompositingMode.ALPHA, variant, true),
                    scene(true, CompositingMode.ALPHA, variant, true));
        }
    }

    @Test public void malformedNativePacketsDoNotTouchBuffers() {
        assertTrue("Native library must be present", NativeRasterizer.available());
        int[] pixels = {0xff123456, 0xffabcdef, 0xff987654, 0xff102030};
        short[] depth = {1,2,3,4};
        int[] before = pixels.clone(); short[] depthBefore = depth.clone();
        float[] state = new float[32]; int[] flags = new int[25];
        flags[1] = 2; flags[3] = 2; flags[5] = 2; flags[14] = 256; flags[24] = 15;
        flags[2] = 100;
        assertFalse(NativeRasterizer.draw(pixels, depth, null, state, flags));
        flags[2] = 0; state[0] = Float.NaN;
        assertFalse(NativeRasterizer.draw(pixels, depth, null, state, flags));
        state[0] = 0; flags[6] = 2; flags[16] = 2; flags[17] = 2;
        assertFalse(NativeRasterizer.draw(pixels, depth, new int[1], state, flags));
        assertFalse(NativeRasterizer.draw(pixels, new short[1], null, state, flags));
        assertArrayEquals(before, pixels);
        assertArrayEquals(depthBefore, depth);
    }

    private int[] scene(boolean nativePath, int blending, int variant, boolean mixed) {
        NativeRasterizer.enabled = nativePath;
        long start = NativeRasterizer.draws;
        Graphics3D.initGraphics3D();
        Graphics3D g = Graphics3D.getInstance();
        Image target = Image.createImage(64, 48);
        g.bindTarget(target.getGraphics(), true, (variant & 1) == 0 ? 0 : Graphics3D.DITHER);
        try {
            // Offset and clipped viewports retain physical buffer indices.
            g.setViewport((variant & 2) == 0 ? 0 : -5, (variant & 2) == 0 ? 0 : 3, 64, 48);
            Background bg = new Background(); bg.setColor(0xff406080); g.clear(bg);
            Camera cam = new Camera();
            if ((variant & 4) == 0) cam.setParallel(2, 64f/48, 1, 10);
            else cam.setPerspective(60, 64f/48, 1, 10);
            g.setCamera(cam, new Transform());
            Appearance style = new Appearance();
            PolygonMode poly = new PolygonMode(); poly.setCulling(PolygonMode.CULL_NONE);
            poly.setPerspectiveCorrectionEnable(true); style.setPolygonMode(poly);
            CompositingMode blend = new CompositingMode(); blend.setBlending(blending);
            blend.setColorWriteEnable(variant != 2); blend.setAlphaWriteEnable(variant != 3);
            blend.setDepthWriteEnable(variant != 4); blend.setDepthTestEnable(variant != 5);
            blend.setAlphaThreshold(variant == 6 ? 0.6f : 0);
            blend.setDepthOffset(variant == 7 ? 0.25f : 0, 0);
            style.setCompositingMode(blend);
            Texture2D texture = new Texture2D(new Image2D(Image2D.RGBA, 2, 2,
                    new byte[]{-1,0,0,-128, 0,-1,0,-1, 0,0,-1,32, -1,-1,0,-64}));
            texture.setBlending((variant & 1)==0 ? Texture2D.FUNC_MODULATE : Texture2D.FUNC_REPLACE);
            texture.setFiltering(Texture2D.FILTER_BASE_LEVEL,
                    (variant & 2)==0 ? Texture2D.FILTER_NEAREST : Texture2D.FILTER_LINEAR);
            texture.setWrapping(Texture2D.WRAP_REPEAT, Texture2D.WRAP_CLAMP);
            style.setTexture(0, texture);
            Fog fog = new Fog(); fog.setMode(Fog.LINEAR); fog.setLinear(1, 6); fog.setColor(0x305070);
            style.setFog(fog);
            VertexArray pos = new VertexArray(4, 3, 2);
            pos.set(0,4,new short[]{-10,9,-21, -10,-9,-21, 10,9,-27, 10,-9,-27});
            VertexBuffer vertices = new VertexBuffer(); vertices.setPositions(pos, 0.1f, null);
            VertexArray uv = new VertexArray(4, 2, 1);
            uv.set(0,4,new byte[]{-2,-1, -2,4, 5,-1, 5,4});
            vertices.setTexCoords(0,uv,0.5f,null);
            VertexArray colors = new VertexArray(4,4,1);
            colors.set(0,4,new byte[]{-1,-128,64,-1, 32,-1,64,-128, -1,32,-1,-64, 96,64,-1,-1});
            vertices.setColors(colors);
            Mesh mesh = new Mesh(vertices, new TriangleStripArray(0,new int[]{4}), style);
            mesh.setAlphaFactor(0.8f);
            g.render(mesh, new Transform());
            if (mixed) {
                // Exponential fog remains on Java. A nearer transparent object must
                // see and update the same depth/color buffers before native resumes.
                fog.setMode(Fog.EXPONENTIAL); fog.setDensity(0.2f);
                Transform front = new Transform(); front.postTranslate(0.15f, 0, 0.3f);
                long beforeFallback = NativeRasterizer.draws;
                g.render(mesh, front);
                assertEquals("Unexpected native exponential-fog draw", beforeFallback, NativeRasterizer.draws);
                style.setFog(null); style.setTexture(0, null);
                front.postTranslate(-0.3f,0,0.2f);
                g.render(mesh,front);
            }
        } finally { g.releaseTarget(); }
        if (nativePath) assertTrue("No native draw for variant " + variant, NativeRasterizer.draws > start);
        int[] pixels = new int[64*48];
        target.getBitmap().getPixels(pixels,0,64,0,0,64,48);
        return pixels;
    }
}
