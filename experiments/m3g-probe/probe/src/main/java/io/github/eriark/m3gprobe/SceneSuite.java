package io.github.eriark.m3gprobe;

import android.graphics.Bitmap;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.microedition.m3g.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.recompile.mobile.PlatformGraphics;

/** Identical public JSR-184 calls in two separately built APKs; neither engine is the oracle. */
public final class SceneSuite {
    public final Map<String, Bitmap> frames = new LinkedHashMap<>();
    private final JSONArray results = new JSONArray();
    private final Graphics3D graphics = Graphics3D.getInstance();
    private static final int SIZE = 160;
    private interface Check { void run() throws Exception; }

    public JSONArray run() throws Exception {
        test("scene_raw", () -> checkLoaded(false, false, "scene_raw"));
        test("scene_zlib", () -> checkLoaded(true, false, "scene_zlib"));
        test("scene_multisection", () -> checkLoaded(false, true, "scene_multisection"));
        test("scene_zlib_multisection", () -> checkLoaded(true, true, "scene_zlib_multisection"));
        test("scene_offset", () -> {
            byte[] data = SceneFixtures.scene(false, false);
            byte[] embedded = new byte[data.length + 17];
            Arrays.fill(embedded, (byte)0x61); System.arraycopy(data, 0, embedded, 7, data.length);
            World world = loaded(embedded, 7);
            require(visible(render(world, "scene_offset")) > 1500, "embedded scene is blank");
        });
        test("scene_truncated_rejected", () -> {
            byte[] data = SceneFixtures.scene(false, false);
            try { Loader.load(Arrays.copyOf(data, data.length - 12), 0); }
            catch (IOException expected) { return; }
            throw new AssertionError("truncated file accepted");
        });
        test("animation_translation", () -> {
            Mesh mesh = SceneFixtures.mesh(); World world = SceneFixtures.world(mesh);
            KeyframeSequence keys = new KeyframeSequence(2, 3, KeyframeSequence.LINEAR);
            keys.setKeyframe(0, 0, new float[]{-.6f, 0, -3});
            keys.setKeyframe(1, 1000, new float[]{.6f, 0, -3}); keys.setDuration(1001);
            track(mesh, keys, AnimationTrack.TRANSLATION);
            world.animate(0); int[] before = render(world, "translation_start");
            world.animate(500); float[] position = new float[3]; mesh.getTranslation(position);
            require(Math.abs(position[0]) < .001f && Math.abs(position[2] + 3) < .001f, "translation midpoint");
            world.animate(1000); int[] after = render(world, "translation_end");
            require(centroid(after) - centroid(before) > 40, "animated pixels did not move");
        });
        test("animation_orientation", () -> {
            Mesh mesh = SceneFixtures.mesh(); World world = SceneFixtures.world(mesh);
            KeyframeSequence keys = new KeyframeSequence(2, 4, KeyframeSequence.SLERP);
            keys.setKeyframe(0, 0, new float[]{0, 0, 0, 1});
            keys.setKeyframe(1, 1000, new float[]{0, 0, 1, 0}); keys.setDuration(1001);
            track(mesh, keys, AnimationTrack.ORIENTATION);
            world.animate(0); int[] before = render(world, "rotation_start");
            world.animate(500); float[] orientation = new float[4]; mesh.getOrientation(orientation);
            require(Math.abs(orientation[0] - 90) < .1f, "rotation midpoint " + orientation[0]);
            int[] after = render(world, "rotation_mid");
            require(changes(before, after) > 1000, "rotation did not change image");
        });
        test("morph_vertices", () -> {
            MorphingMesh mesh = new MorphingMesh(SceneFixtures.vertices(0),
                    new VertexBuffer[]{SceneFixtures.vertices(8)}, SceneFixtures.indices(), SceneFixtures.appearance());
            mesh.setTranslation(0, 0, -3); World world = SceneFixtures.world(mesh);
            mesh.setWeights(new float[]{0}); int[] before = render(world, "morph_start");
            mesh.setWeights(new float[]{1}); int[] after = render(world, "morph_end");
            require(centroid(after) - centroid(before) > 25, "morph vertices did not move");
        });
        test("skin_bone", () -> {
            Group skeleton = new Group(); Group bone = new Group(); skeleton.addChild(bone);
            SkinnedMesh mesh = new SkinnedMesh(SceneFixtures.vertices(0), SceneFixtures.indices(),
                    SceneFixtures.appearance(), skeleton);
            mesh.addTransform(bone, 100, 0, 3); mesh.setTranslation(0, 0, -3);
            World world = SceneFixtures.world(mesh); int[] before = render(world, "skin_start");
            bone.setTranslation(.8f, 0, 0); int[] after = render(world, "skin_end");
            require(centroid(after) - centroid(before) > 25, "bone did not deform vertices");
        });
        return results;
    }
    private static void track(Object3D object, KeyframeSequence keys, int property) {
        AnimationController controller = new AnimationController();
        controller.setActiveInterval(0, 1001); controller.setPosition(0, 0); controller.setSpeed(1, 0);
        AnimationTrack track = new AnimationTrack(keys, property); track.setController(controller);
        object.addAnimationTrack(track);
    }
    private void checkLoaded(boolean compressed, boolean split, String name) throws Exception {
        World loaded = loaded(SceneFixtures.scene(compressed, split), 0);
        require(loaded.getChildCount() == 2 && loaded.find(SceneFixtures.MESH_ID) instanceof Mesh,
                "loaded graph missing camera/mesh");
        int[] expected = render(SceneFixtures.world(SceneFixtures.mesh()), name + "_api");
        int[] actual = render(loaded, name);
        require(visible(actual) > 1500, "loaded scene blank");
        require(Arrays.equals(expected, actual), "loaded and API-created scene pixels differ");
    }
    private static World loaded(byte[] data, int offset) throws IOException {
        Object3D[] objects = Loader.load(data, offset);
        if (objects == null) throw new AssertionError("loader returned null");
        for (Object3D object : objects) if (object instanceof World) return (World)object;
        throw new AssertionError("World root missing; returned " + objects.length + " objects");
    }
    private int[] render(World world, String name) {
        PlatformGraphics target = new PlatformGraphics(SIZE, SIZE);
        try {
            graphics.bindTarget(target, true, 0); graphics.setViewport(0, 0, SIZE, SIZE);
            graphics.render(world);
        } finally { graphics.releaseTarget(); }
        int[] pixels = target.readReleasedPixels();
        frames.put(name, Bitmap.createBitmap(pixels, SIZE, SIZE, Bitmap.Config.ARGB_8888));
        return pixels;
    }
    private void test(String name, Check check) throws Exception {
        long start = System.nanoTime(); JSONObject result = new JSONObject().put("name", name);
        try { check.run(); result.put("passed", true); }
        catch (Throwable failure) {
            result.put("passed", false).put("error", failure.toString());
            android.util.Log.e("M3GProbe", name, failure);
        } finally { if (graphics.getTarget() != null) graphics.releaseTarget(); }
        result.put("elapsedMs", (System.nanoTime() - start) / 1_000_000.0); results.put(result);
    }
    private static boolean geometry(int color) {
        int r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
        return r > 100 && g > 70 && b < 120;
    }
    private static int visible(int[] pixels) {
        int count = 0; for (int pixel : pixels) if (geometry(pixel)) count++; return count;
    }
    private static double centroid(int[] pixels) {
        long sum = 0; int count = 0;
        for (int i = 0; i < pixels.length; i++) if (geometry(pixels[i])) { sum += i % SIZE; count++; }
        require(count > 1000, "not enough visible geometry"); return sum / (double)count;
    }
    private static int changes(int[] first, int[] second) {
        int count = 0; for (int i = 0; i < first.length; i++) if (first[i] != second[i]) count++; return count;
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
