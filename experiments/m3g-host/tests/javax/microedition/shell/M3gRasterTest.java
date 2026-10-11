package javax.microedition.shell;

import android.graphics.Bitmap;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import javax.microedition.lcdui.Image;
import javax.microedition.m3g.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Identical public-API scenes for isolating alpha and texture sampling differences. */
@RunWith(AndroidJUnit4.class)
public class M3gRasterTest {
    private Graphics3D renderer;
    private VertexBuffer vertices;
    private Appearance appearance;
    private Mesh mesh;

    @Before public void setup() {
        Graphics3D.initGraphics3D();
        renderer = Graphics3D.getInstance();
        VertexArray positions = new VertexArray(4, 3, 2);
        positions.set(0, 4, new short[]{-1, 1, -2, -1, -1, -2, 1, 1, -2, 1, -1, -2});
        vertices = new VertexBuffer();
        vertices.setPositions(positions, 1, null);
        vertices.setDefaultColor(0xffffffff);
        appearance = new Appearance();
        PolygonMode polygon = new PolygonMode();
        polygon.setCulling(PolygonMode.CULL_NONE);
        appearance.setPolygonMode(polygon);
        CompositingMode composition = new CompositingMode();
        composition.setBlending(CompositingMode.ALPHA);
        appearance.setCompositingMode(composition);
        mesh = new Mesh(vertices, new TriangleStripArray(0, new int[]{4}), appearance);
    }

    @After public void release() { renderer.releaseTarget(); }

    @Test public void defaultColorAlphaProducesTranslucentShadow() throws Exception {
        vertices.setDefaultColor(0x80000000);
        gray(render("default-alpha"), 127);
    }

    @Test public void vertexAlphaProducesTranslucentShadow() throws Exception {
        VertexArray colors = new VertexArray(4, 4, 1);
        colors.set(0, 4, new byte[]{0,0,0,-128, 0,0,0,-128, 0,0,0,-128, 0,0,0,-128});
        vertices.setColors(colors);
        gray(render("vertex-alpha"), 127);
    }

    @Test public void textureAlphaProducesTranslucentShadow() throws Exception {
        texture(new Image2D(Image2D.RGBA, 1, 1, new byte[]{0,0,0,-128}), Texture2D.FILTER_NEAREST);
        gray(render("texture-alpha"), 127);
    }

    @Test public void nodeAlphaProducesTranslucentShadow() throws Exception {
        vertices.setDefaultColor(0xff000000);
        mesh.setAlphaFactor(0.5f);
        gray(render("node-alpha"), 128);
    }

    @Test public void textureAndVertexAlphaMultiply() throws Exception {
        vertices.setDefaultColor(0x80ffffff);
        texture(new Image2D(Image2D.RGBA, 1, 1, new byte[]{0,0,0,-128}), Texture2D.FILTER_NEAREST);
        appearance.getTexture(0).setBlending(Texture2D.FUNC_MODULATE);
        gray(render("modulated-alpha"), 191);
    }

    @Test public void linearFilterInterpolatesTextureCenter() throws Exception {
        coloredTexture(Texture2D.FILTER_LINEAR);
        int pixel = render("linear-center").getPixel(31, 24);
        near("red", (pixel >>> 16) & 255, 128, 12);
        near("blue", pixel & 255, 128, 12);
    }

    @Test public void linearClampDoesNotBlendPastLeftEdge() throws Exception {
        coloredTexture(Texture2D.FILTER_LINEAR);
        int pixel = render("linear-clamp").getPixel(4, 24);
        near("red", (pixel >>> 16) & 255, 255, 3);
        near("blue", pixel & 255, 0, 3);
    }

    @Test public void nearestFilterKeepsTextureBoundarySharp() throws Exception {
        coloredTexture(Texture2D.FILTER_NEAREST);
        int pixel = render("nearest").getPixel(28, 24);
        near("red", (pixel >>> 16) & 255, 255, 3);
        near("blue", pixel & 255, 0, 3);
    }

    @Test public void linearClampPreservesRightEdge() throws Exception {
        coloredTexture(Texture2D.FILTER_LINEAR);
        int pixel = render("linear-right").getPixel(59, 24);
        near("red", (pixel >>> 16) & 255, 0, 3);
        near("blue", pixel & 255, 255, 3);
    }

    @Test public void linearClampPreservesTopAndBottom() throws Exception {
        texture(new Image2D(Image2D.RGB, 2, 2, new byte[]{-1,0,0, -1,0,0, 0,0,-1, 0,0,-1}), Texture2D.FILTER_LINEAR);
        Bitmap bitmap = render("linear-vertical");
        near("top red", (bitmap.getPixel(24, 4) >>> 16) & 255, 255, 3);
        near("top blue", bitmap.getPixel(24, 4) & 255, 0, 3);
        near("bottom red", (bitmap.getPixel(24, 59) >>> 16) & 255, 0, 3);
        near("bottom blue", bitmap.getPixel(24, 59) & 255, 255, 3);
    }

    @Test public void linearRepeatStillBlendsAcrossSeam() throws Exception {
        coloredTexture(Texture2D.FILTER_LINEAR);
        appearance.getTexture(0).setWrapping(Texture2D.WRAP_REPEAT, Texture2D.WRAP_REPEAT);
        int pixel = render("linear-repeat").getPixel(0, 24);
        near("red", (pixel >>> 16) & 255, 128, 12);
        near("blue", pixel & 255, 128, 12);
    }

    private void coloredTexture(int filter) {
        texture(new Image2D(Image2D.RGB, 2, 2, new byte[]{-1,0,0, 0,0,-1, -1,0,0, 0,0,-1}), filter);
    }

    private void texture(Image2D image, int filter) {
        VertexArray coords = new VertexArray(4, 2, 1);
        coords.set(0, 4, new byte[]{0,0, 0,1, 1,0, 1,1});
        vertices.setTexCoords(0, coords, 1, null);
        Texture2D texture = new Texture2D(image);
        texture.setBlending(Texture2D.FUNC_REPLACE);
        texture.setFiltering(Texture2D.FILTER_BASE_LEVEL, filter);
        texture.setWrapping(Texture2D.WRAP_CLAMP, Texture2D.WRAP_CLAMP);
        appearance.setTexture(0, texture);
    }

    private Bitmap render(String name) throws Exception {
        Image image = Image.createImage(64, 64);
        renderer.bindTarget(image.getGraphics());
        renderer.setViewport(0, 0, 64, 64);
        Background background = new Background();
        background.setColor(0xffffffff);
        renderer.clear(background);
        Camera camera = new Camera();
        camera.setParallel(2, 1, 1, 10);
        renderer.setCamera(camera, new Transform());
        renderer.render(mesh, new Transform());
        renderer.releaseTarget();
        Bitmap bitmap = image.getBitmap();
        File root = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getExternalFilesDir(null), "m3g-raster");
        assertTrue(root.isDirectory() || root.mkdirs());
        try (FileOutputStream output = new FileOutputStream(new File(root, name + ".png"))) {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
        }
        return bitmap;
    }

    private void gray(Bitmap bitmap, int expected) {
        int pixel = bitmap.getPixel(24, 24);
        for (int shift : new int[]{16, 8, 0}) near("channel " + shift + " pixel " + Integer.toHexString(pixel), (pixel >>> shift) & 255, expected, 3);
    }

    private void near(String label, int actual, int expected, int tolerance) {
        assertTrue(label + ": expected " + expected + ", got " + actual, Math.abs(actual - expected) <= tolerance);
    }
}
