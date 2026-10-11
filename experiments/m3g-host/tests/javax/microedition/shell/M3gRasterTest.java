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
    private Mesh beforeMesh, afterMesh;
    private boolean perspective;
    private boolean collapsedDepthRange;

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

    @Test public void rgbReplacePreservesDefaultAlpha() throws Exception {
        vertices.setDefaultColor(0x99030303);
        texture(new Image2D(Image2D.RGB, 1, 1, new byte[]{0,0,0}), Texture2D.FILTER_NEAREST);
        gray(render("rgb-replace-alpha"), 102);
    }

    @Test public void rgbDecalPreservesVertexAlpha() throws Exception {
        VertexArray colors = new VertexArray(4, 4, 1);
        colors.set(0, 4, new byte[]{0,0,0,-128, 0,0,0,-128, 0,0,0,-128, 0,0,0,-128});
        vertices.setColors(colors);
        texture(new Image2D(Image2D.RGB, 1, 1, new byte[]{0,0,0}), Texture2D.FILTER_NEAREST);
        appearance.getTexture(0).setBlending(Texture2D.FUNC_DECAL);
        gray(render("rgb-decal-alpha"), 127);
    }

    @Test public void luminanceReplacePreservesDefaultAlpha() throws Exception {
        vertices.setDefaultColor(0x80030303);
        texture(new Image2D(Image2D.LUMINANCE, 1, 1, new byte[]{0}), Texture2D.FILTER_NEAREST);
        gray(render("luminance-replace-alpha"), 127);
    }

    @Test public void rgbReplaceCombinesWithNodeAlpha() throws Exception {
        vertices.setDefaultColor(0x80ffffff);
        mesh.setAlphaFactor(0.5f);
        texture(new Image2D(Image2D.RGB, 1, 1, new byte[]{0,0,0}), Texture2D.FILTER_NEAREST);
        gray(render("rgb-replace-node-alpha"), 191);
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

    @Test public void luminanceAlphaModulatesShadow() throws Exception {
        texture(new Image2D(Image2D.LUMINANCE_ALPHA, 1, 1, new byte[]{0,-128}), Texture2D.FILTER_NEAREST);
        appearance.getTexture(0).setBlending(Texture2D.FUNC_MODULATE);
        gray(render("luminance-alpha"), 127);
    }

    @Test public void multiplyShadowPreservesGray() throws Exception {
        vertices.setDefaultColor(0xff808080);
        appearance.getCompositingMode().setBlending(CompositingMode.MODULATE);
        gray(render("multiply-shadow"), 128);
    }

    @Test public void materialDiffuseAlphaAffectsShadow() throws Exception {
        Material material = new Material();
        material.setColor(Material.AMBIENT | Material.DIFFUSE | Material.EMISSIVE, 0x80000000);
        appearance.setMaterial(material);
        VertexArray normals = new VertexArray(4, 3, 1);
        normals.set(0, 4, new byte[]{0,0,127, 0,0,127, 0,0,127, 0,0,127});
        vertices.setNormals(normals);
        gray(render("material-alpha"), 127);
    }

    @Test public void parallelFogPreservesAlpha() throws Exception {
        fogShadow();
        gray(render("parallel-fog-alpha"), 191);
    }

    @Test public void perspectiveFogPreservesAlpha() throws Exception {
        fogShadow();
        perspective = true;
        gray(render("perspective-fog-alpha"), 191);
    }

    @Test public void parallelFogDoesNotDependOnDepthTesting() throws Exception {
        fogShadow();
        appearance.getCompositingMode().setDepthTestEnable(false);
        gray(render("parallel-fog-no-depth"), 191);
    }

    @Test public void parallelFogDoesNotDependOnDepthRange() throws Exception {
        fogShadow();
        collapsedDepthRange = true;
        gray(render("parallel-fog-collapsed-depth"), 191);
    }

    @Test public void parallelFogInterpolatesSlopedGeometry() throws Exception {
        fogShadow();
        VertexArray positions = new VertexArray(4, 3, 2);
        positions.set(0, 4, new short[]{-1,1,-1, -1,-1,-1, 1,1,-3, 1,-1,-3});
        vertices.setPositions(positions, 1, null);
        Bitmap bitmap = render("parallel-fog-slope");
        near("left", bitmap.getPixel(8, 24) & 255, 144, 5);
        near("right", bitmap.getPixel(55, 24) & 255, 237, 5);
    }

    @Test public void emissiveMaterialWithoutLightsRetainsColor() throws Exception {
        Material material = new Material();
        material.setColor(Material.DIFFUSE, 0x80000000);
        material.setColor(Material.EMISSIVE, 0xff0000);
        appearance.setMaterial(material);
        VertexArray normals = new VertexArray(4, 3, 1);
        normals.set(0, 4, new byte[]{0,0,127, 0,0,127, 0,0,127, 0,0,127});
        vertices.setNormals(normals);
        int pixel = render("emissive-no-lights").getPixel(24, 24);
        near("red", (pixel >>> 16) & 255, 255, 3);
        near("green", (pixel >>> 8) & 255, 127, 3);
        near("blue", pixel & 255, 127, 3);
    }

    @Test public void disabledDepthTestDrawsBehindExistingGeometry() throws Exception {
        beforeMesh = solidMesh(0xff0000ff, -1);
        vertices.setDefaultColor(0xffff0000);
        appearance.getCompositingMode().setDepthTestEnable(false);
        int pixel = render("depth-disabled").getPixel(24, 24);
        near("red", (pixel >>> 16) & 255, 255, 3);
        near("blue", pixel & 255, 0, 3);
    }

    @Test public void disabledDepthWriteDoesNotOccludeLaterGeometry() throws Exception {
        vertices.setDefaultColor(0xffff0000);
        appearance.getCompositingMode().setDepthWriteEnable(false);
        afterMesh = solidMesh(0xff0000ff, -3);
        int pixel = render("depth-write-disabled").getPixel(24, 24);
        near("red", (pixel >>> 16) & 255, 0, 3);
        near("blue", pixel & 255, 255, 3);
    }

    @Test public void alphaRejectedFragmentDoesNotWriteDepth() throws Exception {
        vertices.setDefaultColor(0x40000000);
        appearance.getCompositingMode().setAlphaThreshold(0.5f);
        afterMesh = solidMesh(0xff0000ff, -3);
        int pixel = render("alpha-reject-depth").getPixel(24, 24);
        near("red", (pixel >>> 16) & 255, 0, 3);
        near("blue", pixel & 255, 255, 3);
    }

    private void fogShadow() {
        vertices.setDefaultColor(0x80000000);
        Fog fog = new Fog();
        fog.setMode(Fog.LINEAR);
        fog.setLinear(1, 3);
        fog.setColor(0xffffff);
        appearance.setFog(fog);
    }

    @Test public void alphaWriteMaskPreservesTargetAlpha() throws Exception {
        Bitmap result = maskedTarget(true, false, "alpha-mask");
        int pixel = result.getPixel(24, 24);
        near("red", (pixel >>> 16) & 255, 255, 3);
        near("green", (pixel >>> 8) & 255, 191, 3);
        near("blue", pixel & 255, 191, 3);
    }

    @Test public void colorWriteMaskStillAllowsAlphaWrite() throws Exception {
        Bitmap result = maskedTarget(false, true, "color-mask");
        int pixel = result.getPixel(24, 24);
        near("red", (pixel >>> 16) & 255, 127, 3);
        near("green", (pixel >>> 8) & 255, 127, 3);
        near("blue", pixel & 255, 255, 3);
    }

    @Test public void disabledColorAndAlphaPreserveTarget() throws Exception {
        Bitmap result = maskedTarget(false, false, "both-masks");
        int pixel = result.getPixel(24, 24);
        near("red", (pixel >>> 16) & 255, 191, 3);
        near("green", (pixel >>> 8) & 255, 191, 3);
        near("blue", pixel & 255, 255, 3);
    }

    @Test public void spriteAlphaMaskPreservesTargetAlpha() throws Exception {
        int pixel = maskedTarget(true, false, "sprite-alpha-mask", true).getPixel(32, 32);
        near("red", (pixel >>> 16) & 255, 255, 3);
        near("green", (pixel >>> 8) & 255, 191, 3);
    }

    @Test public void spriteColorMaskStillAllowsAlphaWrite() throws Exception {
        int pixel = maskedTarget(false, true, "sprite-color-mask", true).getPixel(32, 32);
        near("red", (pixel >>> 16) & 255, 127, 3);
        near("blue", pixel & 255, 255, 3);
    }

    @Test public void spriteBothMasksPreserveTarget() throws Exception {
        int pixel = maskedTarget(false, false, "sprite-both-masks", true).getPixel(32, 32);
        near("red", (pixel >>> 16) & 255, 191, 3);
        near("blue", pixel & 255, 255, 3);
    }

    @Test public void spriteBeforeNearPlaneIsClipped() throws Exception {
        int pixel = maskedTarget(true, true, "sprite-before-near", true, -0.5f).getPixel(32, 32);
        near("red", (pixel >>> 16) & 255, 191, 3);
        near("blue", pixel & 255, 255, 3);
    }

    @Test public void spriteBeyondFarPlaneIsClipped() throws Exception {
        int pixel = maskedTarget(true, true, "sprite-beyond-far", true, -11).getPixel(32, 32);
        near("red", (pixel >>> 16) & 255, 191, 3);
        near("blue", pixel & 255, 255, 3);
    }

    private Bitmap maskedTarget(boolean rgb, boolean alpha, String name) throws Exception {
        return maskedTarget(rgb, alpha, name, false);
    }

    private Bitmap maskedTarget(boolean rgb, boolean alpha, String name, boolean sprite) throws Exception {
        return maskedTarget(rgb, alpha, name, sprite, -2);
    }

    private Bitmap maskedTarget(boolean rgb, boolean alpha, String name, boolean sprite, float z) throws Exception {
        Image2D target = new Image2D(Image2D.RGBA, 64, 64);
        renderer.bindTarget(target);
        renderer.setViewport(0, 0, 64, 64);
        Background background = new Background();
        background.setColor(0x400000ff);
        renderer.clear(background);
        Camera camera = new Camera();
        camera.setParallel(2, 1, 1, 10);
        renderer.setCamera(camera, new Transform());
        vertices.setDefaultColor(0x80ff0000);
        CompositingMode mode = appearance.getCompositingMode();
        mode.setBlending(CompositingMode.REPLACE);
        mode.setColorWriteEnable(rgb);
        mode.setAlphaWriteEnable(alpha);
        if (sprite) {
            byte[] pixels = new byte[8 * 8 * 4];
            for (int i = 0; i < pixels.length; i += 4) {
                pixels[i] = -1;
                pixels[i + 3] = -128;
            }
            Sprite3D node = new Sprite3D(true,
                    new Image2D(Image2D.RGBA, 8, 8, pixels), appearance);
            Transform position = new Transform();
            position.postTranslate(0, 0, z);
            renderer.render(node, position);
        } else renderer.render(mesh, new Transform());
        renderer.releaseTarget();
        mode.setBlending(CompositingMode.ALPHA);
        mode.setColorWriteEnable(true);
        mode.setAlphaWriteEnable(true);
        vertices.setDefaultColor(0xffffffff);
        texture(target, Texture2D.FILTER_NEAREST);
        return render(name);
    }

    private Mesh solidMesh(int color, int z) {
        VertexArray positions = new VertexArray(4, 3, 2);
        positions.set(0, 4, new short[]{-1,1,(short)z, -1,-1,(short)z, 1,1,(short)z, 1,-1,(short)z});
        VertexBuffer buffer = new VertexBuffer();
        buffer.setPositions(positions, 1, null);
        buffer.setDefaultColor(color);
        Appearance style = new Appearance();
        style.setPolygonMode(appearance.getPolygonMode());
        return new Mesh(buffer, new TriangleStripArray(0, new int[]{4}), style);
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
        if (collapsedDepthRange) renderer.setDepthRange(0.5f, 0.5f);
        Background background = new Background();
        background.setColor(0xffffffff);
        renderer.clear(background);
        Camera camera = new Camera();
        if (perspective) camera.setPerspective(60, 1, 1, 10);
        else camera.setParallel(2, 1, 1, 10);
        renderer.setCamera(camera, new Transform());
        if (beforeMesh != null) renderer.render(beforeMesh, new Transform());
        renderer.render(mesh, new Transform());
        if (afterMesh != null) renderer.render(afterMesh, new Transform());
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
