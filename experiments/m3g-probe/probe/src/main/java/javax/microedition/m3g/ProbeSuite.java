package javax.microedition.m3g;

import android.graphics.Bitmap;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;
import org.recompile.mobile.PlatformGraphics;

/** Synthetic tests only; deliberately not a game-compatibility claim. */
public final class ProbeSuite {
    private static final int W = 160, H = 160, BG = 0xff182027;
    public final Map<String, Bitmap> frames = new LinkedHashMap<>();
    private final Graphics3D g = Graphics3D.getInstance();
    private final JSONArray results = new JSONArray();
    private interface Check { void run() throws Exception; }

    public JSONObject run() throws Exception {
        test("transform_inverse", () -> {
            Transform t = new Transform();
            t.postTranslate(3, -2, 7);
            t.postRotate(37, 0, 1, 0);
            Transform inverse = new Transform(t);
            inverse.invert();
            float[] p = {1, 2, 3, 1};
            t.transform(p); inverse.transform(p);
            require(Math.abs(p[0] - 1) < .001 && Math.abs(p[1] - 2) < .001
                    && Math.abs(p[2] - 3) < .001, "transform round trip");
        });
        test("immediate_triangle", () -> {
            Image2D target = target();
            g.render(vertices(0xffedb84c), indices(), appearance(), positioned(-3));
            require(changed(target.image) > 1500, "nonblank immediate geometry");
            capture("immediate", target);
        });
        test("retained_matches_immediate", () -> {
            Image2D target = target();
            VertexBuffer vertices = vertices(0xff42c4ad);
            Appearance appearance = appearance();
            g.render(vertices, indices(), appearance, positioned(-3));
            int[] reference = target.image.clone();
            require(changed(reference) > 1500, "reference geometry missing");
            g.releaseTarget();
            target = target();
            World world = new World();
            Camera camera = camera();
            world.addChild(camera); world.setActiveCamera(camera);
            Background background = new Background(); background.setColor(BG); world.setBackground(background);
            Mesh mesh = new Mesh(vertices, indices(), appearance);
            mesh.setTranslation(0, 0, -3); world.addChild(mesh);
            g.render(world);
            require(Arrays.equals(reference, target.image), "retained and immediate differ");
            capture("retained", target);
        });
        test("depth_order", () -> {
            Image2D target = target();
            g.render(vertices(0xffedb84c), indices(), appearance(), positioned(-3));
            int[] reference = target.image.clone();
            require(changed(reference) > 1500, "near geometry missing");
            g.render(vertices(0xffd24b76), indices(), appearance(), positioned(-5));
            require(Arrays.equals(reference, target.image), "far triangle overwrote near triangle");
        });
        test("texture_checker", () -> {
            Image2D target = target();
            byte[] data = {(byte)255, 30, 30, 30, (byte)255, 30,
                    30, 30, (byte)255, (byte)255, (byte)255, 30};
            Texture2D texture = new Texture2D(new Image2D(Image2D.RGB, 2, 2, data));
            texture.setFiltering(Texture2D.FILTER_BASE_LEVEL, Texture2D.FILTER_NEAREST);
            texture.setBlending(Texture2D.FUNC_REPLACE);
            Appearance appearance = appearance(); appearance.setTexture(0, texture);
            VertexBuffer vertices = vertices(0xffffffff);
            VertexArray uv = new VertexArray(3, 2, 2);
            uv.set(0, 3, new short[]{0, 0, 2, 0, 1, 2});
            vertices.setTexCoords(0, uv, .5f, null);
            g.render(vertices, indices(), appearance, positioned(-3));
            java.util.HashSet<Integer> colors = new java.util.HashSet<>();
            for (int pixel : target.image) if (pixel != BG) colors.add(pixel);
            require(changed(target.image) > 1500 && colors.size() >= 3, "missing texture colors");
            capture("texture", target);
        });
        test("translated_clip", () -> {
            PlatformGraphics target = new PlatformGraphics(W, H);
            Arrays.fill(target.getFrameBuffer(), 0xff914fbe);
            target.translate(10, 12); target.setClip(20, 20, 60, 60);
            g.bindTarget(target);
            g.setCamera(camera(), new Transform());
            Background background = new Background(); background.setColor(BG); g.clear(background);
            g.render(vertices(0xff42c4ad), indices(), appearance(), positioned(-3));
            int count = 0;
            for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
                if (x < 30 || x >= 90 || y < 32 || y >= 92)
                    require(target.getFrameBuffer()[y * W + x] == 0xff914fbe, "clip leak");
                else if (target.getFrameBuffer()[y * W + x] != BG) count++;
            }
            require(count > 100, "clipped geometry missing");
            frames.put("clip", Bitmap.createBitmap(target.getFrameBuffer(), W, H, Bitmap.Config.ARGB_8888));
        });
        test("png_loader", () -> {
            Bitmap bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(0xff42c4ad);
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, data);
            Object3D[] loaded = Loader.load(data.toByteArray(), 0);
            require(loaded != null && loaded.length == 1 && loaded[0] instanceof Image2D, "PNG loading");
            Image2D image = (Image2D) loaded[0];
            require(image.getWidth() == 2 && image.image[0] == 0xff42c4ad, "PNG pixels");
        });
        test("release_and_rebind", () -> {
            for (int i = 0; i < 20; i++) {
                Image2D target = target();
                g.render(vertices(0xffedb84c), indices(), appearance(), positioned(-3));
                require(changed(target.image) > 1500, "rebind lost geometry");
                g.releaseTarget();
                require(g.getTarget() == null, "target not released");
            }
        });
        return new JSONObject().put("upstream", "baca76a4dda6f30abac67fb4a3db009eed05f91c")
                .put("device", android.os.Build.MODEL).put("sdk", android.os.Build.VERSION.SDK_INT)
                .put("abi", android.os.Build.SUPPORTED_ABIS[0]).put("tests", results);
    }

    private void test(String name, Check check) throws Exception {
        long started = System.nanoTime();
        JSONObject result = new JSONObject().put("name", name);
        try { check.run(); result.put("passed", true); }
        catch (Throwable error) {
            result.put("passed", false).put("error", error.toString());
            android.util.Log.e("M3GProbe", name, error);
        } finally { if (g.getTarget() != null) g.releaseTarget(); }
        result.put("elapsedMs", (System.nanoTime() - started) / 1_000_000.0);
        results.put(result);
    }
    private Image2D target() {
        Image2D target = new Image2D(Image2D.RGB, W, H);
        g.bindTarget(target);
        g.setCamera(camera(), new Transform());
        Background background = new Background(); background.setColor(BG); g.clear(background);
        return target;
    }
    private static Camera camera() {
        Camera camera = new Camera(); camera.setPerspective(60, 1, 1, 20); return camera;
    }
    private static Transform positioned(float z) {
        Transform transform = new Transform(); transform.postTranslate(0, 0, z); return transform;
    }
    private static Appearance appearance() {
        Appearance appearance = new Appearance();
        PolygonMode polygon = new PolygonMode(); polygon.setCulling(PolygonMode.CULL_NONE);
        appearance.setPolygonMode(polygon); return appearance;
    }
    private static VertexBuffer vertices(int color) {
        VertexArray positions = new VertexArray(3, 3, 2);
        positions.set(0, 3, new short[]{-1, -1, 0, 1, -1, 0, 0, 1, 0});
        VertexBuffer vertices = new VertexBuffer(); vertices.setPositions(positions, 1, null);
        vertices.setDefaultColor(color); return vertices;
    }
    private static TriangleStripArray indices() { return new TriangleStripArray(0, new int[]{3}); }
    private static int changed(int[] pixels) {
        int count = 0; for (int pixel : pixels) if (pixel != BG) count++; return count;
    }
    private void capture(String name, Image2D target) {
        frames.put(name, Bitmap.createBitmap(target.image, W, H, Bitmap.Config.ARGB_8888));
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
