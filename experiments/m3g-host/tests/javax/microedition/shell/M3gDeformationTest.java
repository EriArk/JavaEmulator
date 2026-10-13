package javax.microedition.shell;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import javax.microedition.lcdui.Image;
import javax.microedition.m3g.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Public API fixtures: shared mutable geometry must affect rendering and picking. */
@RunWith(AndroidJUnit4.class)
public class M3gDeformationTest {
    private Group scene;
    private VertexBuffer base, target;
    private MorphingMesh morph;
    private SkinnedMesh skin;
    private Group skeleton, bone;

    @Before public void setup() {
        Graphics3D.initGraphics3D();
        scene = new Group();
        base = vertices(-2);
        target = vertices(-4);
        morph = new MorphingMesh(base, new VertexBuffer[]{target}, indices(), style());
        skeleton = new Group();
        bone = new Group();
        skeleton.addChild(bone);
        skin = new SkinnedMesh(base, indices(), style(), skeleton);
    }

    @Test public void morphReadsEditedTargetArrayWithoutAnotherWeightCall() {
        scene.addChild(morph);
        morph.setWeights(new float[]{1});
        distance(4);
        target.getPositions(null).set(0, 3, coordinates(-6));
        distance(6);
    }

    @Test public void morphReadsEditedBaseArray() {
        scene.addChild(morph);
        morph.setWeights(new float[]{.5f});
        distance(3);
        base.getPositions(null).set(0, 3, coordinates(-6));
        distance(5);
    }

    @Test public void morphRefreshesBaseScaleAndIgnoresTargetScale() {
        scene.addChild(morph);
        morph.setWeights(new float[]{.5f});
        distance(3);
        base.setPositions(base.getPositions(null), 2, new float[]{0, 0, -1});
        target.setPositions(target.getPositions(null), 7, new float[]{0, 0, 20});
        distance(7);
    }

    @Test public void morphRevalidatesChangedTargetLayoutAndRecovers() {
        scene.addChild(morph);
        distance(2);
        target.setNormals(normals());
        try { distance(2); fail("Invalid target layout"); }
        catch (IllegalStateException expected) { }
        target.setNormals(null);
        distance(2);
    }

    @Test public void morphRebuildsWhenBothVertexCountsChange() {
        scene.addChild(morph);
        morph.setWeights(new float[]{1});
        distance(4);
        resize(base, -6);
        resize(target, -8);
        distance(8);
    }

    @Test public void morphDefaultColorsRefreshAfterFirstRender() {
        morph.setWeights(new float[]{1});
        target.setDefaultColor(0xffff0000);
        color(morph, 0xff0000);
        target.setDefaultColor(0xff00ff00);
        color(morph, 0x00ff00);
    }

    @Test public void morphVertexColorMutationRefreshesWithoutWeights() {
        base.setColors(colors((byte) 0, (byte) 0, (byte) 0));
        VertexArray colors = colors((byte) 127, (byte) 0, (byte) 0);
        target.setColors(colors);
        morph.setWeights(new float[]{1});
        color(morph, 0x7f0000);
        colors.set(0, 3, new byte[]{0,127,0, 0,127,0, 0,127,0});
        color(morph, 0x007f00);
    }

    @Test public void skinRefreshesDefaultColorAndReplacementColorArray() {
        skin.addTransform(bone, 1, 0, 3);
        base.setDefaultColor(0xffff0000);
        color(skin, 0xff0000);
        base.setDefaultColor(0xff00ff00);
        color(skin, 0x00ff00);
        base.setColors(colors((byte) 0, (byte) 0, (byte) 127));
        color(skin, 0x00007f);
    }

    @Test public void skinRebuildsPositionStorageAfterResize() {
        skin.addTransform(bone, 1, 0, 3);
        scene.addChild(skin);
        distance(2);
        resize(base, -6);
        distance(6);
    }

    @Test public void overlappingBoneRangesReturnUniqueVertices() {
        skin.addTransform(bone, 1, 0, 2);
        skin.addTransform(bone, 3, 1, 2);
        Group other = new Group();
        skeleton.addChild(other);
        skin.addTransform(other, 4, 0, 3);
        assertEquals(3, skin.getBoneVertices(bone, null, null));
        int[] indices = new int[3];
        float[] weights = new float[3];
        assertEquals(3, skin.getBoneVertices(bone, indices, weights));
        boolean[] seen = new boolean[3];
        for (int i = 0; i < 3; i++) {
            assertFalse(seen[indices[i]]);
            seen[indices[i]] = true;
            float expected = indices[i] == 0 ? .2f : indices[i] == 1 ? .5f : 3f/7;
            assertEquals(expected, weights[i], .005f);
        }
    }

    @Test public void singleNullBoneQueryDoesNotWriteOrValidateOtherArray() {
        skin.addTransform(bone, 1, 0, 3);
        int[] indices = {17};
        float[] weights = {17};
        assertEquals(3, skin.getBoneVertices(bone, indices, null));
        assertEquals(3, skin.getBoneVertices(bone, null, weights));
        assertEquals(17, indices[0]);
        assertEquals(17, weights[0], 0);
    }

    @Test public void overflowingBoneRangeIsRejectedWithoutChangingMesh() {
        try { skin.addTransform(bone, 1, Integer.MAX_VALUE, 2); fail("Overflowing range"); }
        catch (IndexOutOfBoundsException expected) { }
        assertEquals(0, skin.getBoneVertices(bone, null, null));
    }

    @Test public void repeatedBoneUsesOneReportedBindPose() {
        skin.addTransform(bone, 1, 0, 3);
        bone.setTranslation(0, 0, 2);
        skin.addTransform(bone, 1, 0, 3);
        Transform bind = new Transform();
        skin.getBoneTransform(bone, bind);
        float[] origin = {0, 0, 0, 1};
        bind.transform(origin);
        bone.setTranslation(0, 0, 4);
        scene.addChild(skin);
        RayIntersection hit = new RayIntersection();
        assertTrue(scene.pick(-1, 0, 0, 10, 0, 0, -1, hit));
        assertEquals(8 - origin[2], hit.getDistance(), .01f);
    }

    @Test public void ancestorAnimationMovesSkinAndItsAttachment() {
        skin.addTransform(bone, 1, 0, 3);
        Mesh attachment = new Mesh(vertices(-4), indices(), style());
        bone.addChild(attachment);
        KeyframeSequence keys = new KeyframeSequence(2, 3, KeyframeSequence.LINEAR);
        keys.setKeyframe(0, 0, new float[]{0,0,0});
        keys.setKeyframe(1, 100, new float[]{0,0,-4});
        keys.setDuration(100);
        AnimationTrack track = new AnimationTrack(keys, AnimationTrack.TRANSLATION);
        track.setController(new AnimationController());
        skeleton.addAnimationTrack(track);
        scene.addChild(skin);
        scene.animate(50);
        distance(4);
        skin.setScope(0);
        distance(6);
    }

    @Test public void skinRangeValidationIsDeferredAndRecoverable() {
        skin.addTransform(bone, 1, 0, 4);
        scene.addChild(skin);
        try { distance(2); fail("Range exceeds positions"); }
        catch (IllegalStateException expected) { }
        resize(base, -2);
        distance(2);
    }

    @Test public void skinDuplicateHasIndependentPoseAndSharedSource() {
        skin.addTransform(bone, 1, 0, 3);
        SkinnedMesh copy = (SkinnedMesh) skin.duplicate();
        assertSame(base, copy.getVertexBuffer());
        copy.getSkeleton().getChild(0).setTranslation(0, 0, -3);
        scene.addChild(copy);
        distance(5);
        scene.removeChild(copy);
        scene.addChild(skin);
        distance(2);
    }

    @Test public void animatedTargetColorInvalidatesMorphPose() {
        morph.setWeights(new float[]{1});
        color(morph, 0xffffff);
        KeyframeSequence keys = new KeyframeSequence(1, 3, KeyframeSequence.STEP);
        keys.setKeyframe(0, 0, new float[]{0,1,0});
        keys.setDuration(100);
        AnimationTrack track = new AnimationTrack(keys, AnimationTrack.COLOR);
        track.setController(new AnimationController());
        target.addAnimationTrack(track);
        morph.animate(0);
        color(morph, 0x00ff00);
    }

    @Test public void loadedMorphKeepsWeightsAndMutableTargets() throws Exception {
        MorphingMesh loaded = (MorphingMesh) loaded(false);
        float[] weights = new float[1];
        loaded.getWeights(weights);
        assertEquals(.5f, weights[0], 0);
        loaded.setAppearance(0, style());
        scene.addChild(loaded);
        distance(3);
        loaded.getMorphTarget(0).getPositions(null).set(0, 3, coordinates(-8));
        distance(5);
    }

    @Test public void loadedSkinRetainsBindPoseThroughDuplication() throws Exception {
        SkinnedMesh loaded = (SkinnedMesh) loaded(true);
        Transform bind = new Transform();
        loaded.getBoneTransform(loaded.getSkeleton(), bind);
        float[] origin = {0,0,0,1};
        bind.transform(origin);
        assertEquals(-3, origin[2], .001f);
        loaded.setAppearance(0, style());
        SkinnedMesh copy = (SkinnedMesh) loaded.duplicate();
        copy.getSkeleton().setTranslation(0, 0, 1);
        scene.addChild(copy);
        distance(4);
        assertSame(loaded, loaded.getSkeleton().getParent());
    }

    private static Mesh loaded(boolean skin) throws Exception {
        for (Object3D object : Loader.load(DeformationFixture.file(skin), 0)) {
            if (object instanceof Mesh) return (Mesh) object;
        }
        throw new AssertionError("Missing mesh root");
    }

    @Test public void unmorphedAttributesFollowBaseArrayReplacement() {
        base.setColors(colors((byte) 127, (byte) 0, (byte) 0));
        morph.setWeights(new float[]{1});
        color(morph, 0x7f0000);
        base.setColors(colors((byte) 0, (byte) 127, (byte) 0));
        color(morph, 0x007f00);
    }

    @Test public void skinNormalsFollowNonUniformBoneScale() {
        VertexArray normals = new VertexArray(3, 3, 1);
        normals.set(0, 3, new byte[]{90,0,90, 90,0,90, 90,0,90});
        base.setNormals(normals);
        skin.addTransform(bone, 1, 0, 3);
        bone.setScale(2, 1, 1);
        Material material = new Material();
        material.setColor(Material.AMBIENT | Material.EMISSIVE | Material.SPECULAR, 0);
        material.setColor(Material.DIFFUSE, 0xffffffff);
        skin.getAppearance(0).setMaterial(material);
        Light light = new Light();
        light.setMode(Light.DIRECTIONAL);
        Graphics3D.getInstance().addLight(light, new Transform());
        VertexBuffer reference = vertices(-2);
        VertexArray expectedNormals = new VertexArray(3, 3, 1);
        expectedNormals.set(0, 3, new byte[]{45,0,90, 45,0,90, 45,0,90});
        reference.setNormals(expectedNormals);
        int expected = pixel(new Mesh(reference, indices(), skin.getAppearance(0))) & 255;
        assertTrue("Reference must be lit", expected > 100);
        assertEquals(expected, pixel(skin) & 255, 4);
    }

    private void distance(float expected) {
        RayIntersection hit = new RayIntersection();
        assertTrue(scene.pick(-1, 0, 0, 0, 0, 0, -1, hit));
        assertEquals(expected, hit.getDistance(), .015f);
    }
    private void color(Node node, int expected) {
        int pixel = pixel(node);
        for (int shift = 0; shift < 24; shift += 8) {
            assertEquals((expected >> shift) & 255, (pixel >> shift) & 255, 2);
        }
    }
    private int pixel(Node node) {
        Image image = Image.createImage(32, 32);
        Graphics3D renderer = Graphics3D.getInstance();
        renderer.bindTarget(image.getGraphics(), true, 0);
        try {
            Camera camera = new Camera();
            camera.setParallel(4, 1, 1, 20);
            renderer.setCamera(camera, new Transform());
            renderer.clear(new Background());
            renderer.render(node, new Transform());
        } finally { renderer.releaseTarget(); }
        int[] pixel = new int[1];
        image.getRGB(pixel, 0, 1, 16, 16, 1, 1);
        return pixel[0];
    }
    private static VertexBuffer vertices(int z) {
        VertexBuffer buffer = new VertexBuffer();
        VertexArray positions = new VertexArray(3, 3, 2);
        positions.set(0, 3, coordinates(z));
        buffer.setPositions(positions, 1, null);
        return buffer;
    }
    private static short[] coordinates(int z) { return new short[]{-1,-1,(short)z, 1,-1,(short)z, 0,1,(short)z}; }
    private static void resize(VertexBuffer buffer, int z) {
        buffer.setPositions(null, 1, null);
        VertexArray positions = new VertexArray(4, 3, 2);
        positions.set(0, 3, coordinates(z));
        positions.set(3, 1, new short[]{0,0,(short)z});
        buffer.setPositions(positions, 1, null);
    }
    private static VertexArray normals() {
        VertexArray normals = new VertexArray(3, 3, 1);
        normals.set(0, 3, new byte[]{0,0,127, 0,0,127, 0,0,127});
        return normals;
    }
    private static VertexArray colors(byte r, byte g, byte b) {
        VertexArray colors = new VertexArray(3, 3, 1);
        colors.set(0, 3, new byte[]{r,g,b, r,g,b, r,g,b});
        return colors;
    }
    private static TriangleStripArray indices() { return new TriangleStripArray(0, new int[]{3}); }
    private static Appearance style() {
        Appearance appearance = new Appearance();
        PolygonMode polygon = new PolygonMode();
        polygon.setCulling(PolygonMode.CULL_NONE);
        appearance.setPolygonMode(polygon);
        return appearance;
    }
}
