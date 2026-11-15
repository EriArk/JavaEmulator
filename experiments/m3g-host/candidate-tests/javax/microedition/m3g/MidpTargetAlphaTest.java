/* SPDX-License-Identifier: GPL-3.0-or-later */
package javax.microedition.m3g;

import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.Graphics;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

/** MIDP targets are RGB; Image2D RGBA targets must still preserve alpha. */
public class MidpTargetAlphaTest {
    @After public void restore() {
        Graphics3D.getInstance().releaseTarget();
        Graphics3D.initGraphics3D();
        GpuRasterizer.enabled = true;
        GpuGeometry.enabled = true;
        NativeRasterizer.enabled = true;
    }

    @Test public void replaceWithZeroAlphaRemainsVisibleOnMidpTargets() {
        for (int path = 0; path < 4; path++) {
            select(path);
            Image target = Image.createImage(32, 32);
            Graphics graphics = target.getGraphics();
            graphics.setColor(0x112233);
            graphics.fillRect(0, 0, 32, 32);
            graphics.setClip(8, 8, 16, 16);
            render(graphics, 0, CompositingMode.REPLACE);
            assertEquals("path=" + path, 0xff40a0e0, target.getBitmap().getPixel(16, 16));
            assertEquals(0xff112233, target.getBitmap().getPixel(0, 0));
            // The final MIDP draw must cover old loading-screen content.
            Image screen = Image.createImage(32, 32);
            screen.getGraphics().drawImage(target, 0, 0, Graphics.TOP | Graphics.LEFT);
            assertEquals(0xff40a0e0, screen.getBitmap().getPixel(16, 16));
        }
    }

    @Test public void alphaBlendingStillUsesFragmentAlpha() {
        for (int path = 0; path < 4; path++) {
            select(path);
            Image target = Image.createImage(32, 32, 0xff000000);
            render(target.getGraphics(), 128, CompositingMode.ALPHA);
            int pixel = target.getBitmap().getPixel(16, 16);
            assertEquals(255, pixel >>> 24);
            assertTrue(Math.abs(((pixel >> 16) & 255) - 32) <= 1);
            assertTrue(Math.abs(((pixel >> 8) & 255) - 80) <= 1);
            assertTrue(Math.abs((pixel & 255) - 112) <= 1);
        }
    }

    @Test public void rgbaImageTargetRetainsZeroAlphaAndRgb() {
        for (int path = 0; path < 4; path++) {
            select(path);
            Image2D target = new Image2D(Image2D.RGBA, 32, 32);
            render(target, 0, CompositingMode.REPLACE);
            assertEquals("path=" + path, 0x0040a0e0, target.image[16 * 32 + 16]);
        }
    }

    private void select(int path) {
        Graphics3D.initGraphics3D();
        GpuRasterizer.enabled = path >= 2;
        GpuGeometry.enabled = path == 3;
        NativeRasterizer.enabled = path == 1;
    }

    private void render(Object target, int alpha, int blend) {
        Graphics3D g = Graphics3D.getInstance();
        Camera camera = new Camera(); camera.setParallel(4, 1, 1, 10);
        g.setCamera(camera, new Transform());
        VertexArray positions = new VertexArray(4, 3, 1);
        positions.set(0, 4, new byte[]{-1,1,-3, -1,-1,-3, 1,1,-3, 1,-1,-3});
        VertexArray uv = new VertexArray(4, 2, 1);
        uv.set(0, 4, new byte[]{0,0, 0,1, 1,0, 1,1});
        VertexBuffer vertices = new VertexBuffer();
        vertices.setPositions(positions, 1, null); vertices.setTexCoords(0, uv, 1, null);
        vertices.setDefaultColor((alpha << 24) | 0xffffff);
        Texture2D texture = new Texture2D(new Image2D(Image2D.RGB, 1, 1,
                new byte[]{64, (byte)160, (byte)224}));
        texture.setBlending(Texture2D.FUNC_REPLACE);
        Appearance appearance = new Appearance(); appearance.setTexture(0, texture);
        CompositingMode mode = new CompositingMode(); mode.setBlending(blend);
        appearance.setCompositingMode(mode);
        g.bindTarget(target, true, 0);
        try {
            g.setViewport(0, 0, 32, 32);
            g.render(vertices, new TriangleStripArray(0, new int[]{4}), appearance, new Transform());
        } finally { g.releaseTarget(); }
    }
}
