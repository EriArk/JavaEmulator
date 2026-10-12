package javax.microedition.shell;

import android.graphics.Bitmap;
import android.os.Debug;
import android.os.PowerManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import javax.microedition.lcdui.Image;
import javax.microedition.m3g.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Bounded, opt-in identical workloads. Wall time is not game FPS. */
@RunWith(AndroidJUnit4.class)
public class M3gBenchmark {
    @Test public void warmedPublicApiScenes() throws Exception {
        String run = InstrumentationRegistry.getArguments().getString("benchRun");
        assertTrue("Pass a fresh benchRun label", run != null && run.matches("[A-Za-z0-9_-]+"));
        android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File root = new File(context.getExternalFilesDir(null), "m3g-benchmark/" + run);
        assertTrue("Do not overwrite previous samples", root.mkdirs());
        PowerManager power = (PowerManager) context.getSystemService(android.content.Context.POWER_SERVICE);
        JSONObject report = new JSONObject().put("package", context.getPackageName())
                .put("device", android.os.Build.MODEL).put("sdk", android.os.Build.VERSION.SDK_INT)
                .put("debuggable", (context.getApplicationInfo().flags
                        & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0)
                .put("thermalStart", power.getCurrentThermalStatus());
        JSONArray cases = new JSONArray();
        report.put("cases", cases);
        for (int[] size : new int[][]{{240,320},{320,240}}) {
            for (String kind : new String[]{"rgb-nearest","rgb-linear","rgba-alpha","lit-fog"}) {
                Graphics3D.initGraphics3D();
                Graphics3D renderer = Graphics3D.getInstance();
                Image target = Image.createImage(size[0], size[1]);
                Mesh mesh = scene(kind);
                Camera camera = new Camera();
                camera.setPerspective(60, (float) size[0] / size[1], 0.1f, 10);
                renderer.setCamera(camera, new Transform());
                Light light = new Light();
                light.setMode(Light.AMBIENT);
                light.setIntensity(0.8f);
                renderer.addLight(light, new Transform());
                Background background = new Background();
                background.setColor(0xff314159);
                Transform model = new Transform();
                for (int i = 0; i < 64; i++) frame(renderer, target, mesh, background, model);
                JSONArray wall = new JSONArray(), cpu = new JSONArray();
                for (int batch = 0; batch < 5; batch++) {
                    long start = System.nanoTime(), thread = Debug.threadCpuTimeNanos();
                    for (int i = 0; i < 32; i++) frame(renderer, target, mesh, background, model);
                    cpu.put(Debug.threadCpuTimeNanos() - thread);
                    wall.put(System.nanoTime() - start);
                }
                Bitmap bitmap = target.getBitmap();
                int[] pixels = new int[size[0] * size[1]];
                bitmap.getPixels(pixels, 0, size[0], 0, 0, size[0], size[1]);
                CRC32 crc = new CRC32();
                int changed = 0;
                for (int pixel : pixels) {
                    if (pixel != 0xff314159) changed++;
                    for (int shift = 0; shift < 32; shift += 8) crc.update(pixel >>> shift);
                }
                assertTrue("Scene must draw visible geometry", changed > pixels.length / 4);
                String name = kind + "-" + size[0] + "x" + size[1];
                try (FileOutputStream out = new FileOutputStream(new File(root, name + ".png"))) {
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
                }
                cases.put(new JSONObject().put("name", name).put("framesPerBatch", 32)
                        .put("warmupFrames", 64).put("wallNs", wall).put("threadCpuNs", cpu)
                        .put("pixelCrc32", crc.getValue()).put("changedPixels", changed));
                renderer.releaseTarget();
            }
        }
        report.put("thermalEnd", power.getCurrentThermalStatus());
        try (FileOutputStream out = new FileOutputStream(new File(root, "report.json"))) {
            out.write(report.toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }

    private void frame(Graphics3D renderer, Image image, Mesh mesh, Background background, Transform transform) {
        renderer.bindTarget(image.getGraphics(), true, 0);
        try { renderer.clear(background); renderer.render(mesh, transform); }
        finally { renderer.releaseTarget(); }
    }

    private Mesh scene(String kind) {
        int cells = 8, count = cells * cells * 4;
        short[] positions = new short[count * 3], uv = new short[count * 2];
        byte[] normals = new byte[count * 3];
        int[] strips = new int[cells * cells];
        for (int y = 0, vertex = 0; y < cells; y++) for (int x = 0; x < cells; x++) {
            for (int corner = 0; corner < 4; corner++, vertex++) {
                int right = corner / 2, down = corner % 2;
                positions[vertex * 3] = (short) (-120 + (x + right) * 30);
                positions[vertex * 3 + 1] = (short) (120 - (y + down) * 30);
                positions[vertex * 3 + 2] = (short) (-200 - (x + right) * 3);
                normals[vertex * 3 + 2] = 127;
                uv[vertex * 2] = (short) (right * 128);
                uv[vertex * 2 + 1] = (short) (down * 128);
            }
            strips[y * cells + x] = 4;
        }
        VertexArray points = new VertexArray(count, 3, 2);
        points.set(0, count, positions);
        VertexArray coords = new VertexArray(count, 2, 2);
        coords.set(0, count, uv);
        VertexBuffer vertices = new VertexBuffer();
        vertices.setPositions(points, 0.01f, null);
        vertices.setTexCoords(0, coords, 1f / 128, null);
        vertices.setDefaultColor(0xffffffff);
        boolean alpha = kind.equals("rgba-alpha");
        int channels = alpha ? 4 : 3;
        byte[] pixels = new byte[64 * 64 * channels];
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) {
            int offset = (y * 64 + x) * channels;
            pixels[offset] = (byte) (x * 4);
            pixels[offset + 1] = (byte) (y * 4);
            pixels[offset + 2] = (byte) (((x / 8 + y / 8) % 2) * 180 + 40);
            if (alpha) pixels[offset + 3] = (byte) (64 + x * 2);
        }
        Texture2D texture = new Texture2D(new Image2D(alpha ? Image2D.RGBA : Image2D.RGB, 64, 64, pixels));
        texture.setBlending(Texture2D.FUNC_MODULATE);
        texture.setFiltering(Texture2D.FILTER_BASE_LEVEL, kind.equals("rgb-linear") ? Texture2D.FILTER_LINEAR : Texture2D.FILTER_NEAREST);
        Appearance style = new Appearance();
        style.setTexture(0, texture);
        PolygonMode polygon = new PolygonMode();
        polygon.setCulling(PolygonMode.CULL_NONE);
        polygon.setPerspectiveCorrectionEnable(true);
        style.setPolygonMode(polygon);
        CompositingMode blend = new CompositingMode();
        blend.setBlending(alpha ? CompositingMode.ALPHA : CompositingMode.REPLACE);
        style.setCompositingMode(blend);
        if (kind.equals("lit-fog")) {
            VertexArray normal = new VertexArray(count, 3, 1);
            normal.set(0, count, normals);
            vertices.setNormals(normal);
            Material material = new Material();
            material.setColor(Material.AMBIENT | Material.DIFFUSE, 0xffd0b090);
            style.setMaterial(material);
            Fog fog = new Fog();
            fog.setMode(Fog.LINEAR);
            fog.setColor(0x314159);
            fog.setLinear(1, 4);
            style.setFog(fog);
        }
        return new Mesh(vertices, new TriangleStripArray(0, strips), style);
    }
}
