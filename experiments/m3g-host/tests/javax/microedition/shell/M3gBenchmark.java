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
        String raster = InstrumentationRegistry.getArguments().getString("benchRaster");
        java.lang.reflect.Field nativeEnabled = null, nativeDraws = null, nativeSprites = null;
        java.lang.reflect.Field gpuEnabled = null, gpuDraws = null, gpuRenderer = null;
        java.lang.reflect.Field gpuUploads=null, gpuReadbacks=null, gpuRetain=null;
        java.lang.reflect.Field gpuBatch=null, gpuSubmissions=null, gpuSubmitted=null, gpuGeometry=null;
        java.lang.reflect.Field gpuCache=null,gpuTextureUploads=null;
        java.lang.reflect.Field hardwareGeometry=null,hardwareDraws=null;
        java.lang.reflect.Field discardDepth=null,depthReadbacks=null;
        String depthPolicy=InstrumentationRegistry.getArguments().getString("benchGpuDiscardDepth");
        String geometry=InstrumentationRegistry.getArguments().getString("benchGpuGeometry");
        String resident=InstrumentationRegistry.getArguments().getString("benchGpuResident");
        String batching=InstrumentationRegistry.getArguments().getString("benchGpuBatch");
        String caching=InstrumentationRegistry.getArguments().getString("benchGpuTextureCache");
        try {
            Class<?> backend = Class.forName("javax.microedition.m3g.NativeRasterizer");
            nativeEnabled = backend.getDeclaredField("enabled");
            nativeEnabled.setAccessible(true);
            nativeDraws = backend.getDeclaredField("draws");
            nativeDraws.setAccessible(true);
            nativeSprites = backend.getDeclaredField("spriteDraws");
            nativeSprites.setAccessible(true);
            if (raster != null) {
                assertTrue("benchRaster must be java, native or gpu", raster.equals("java") || raster.equals("native") || raster.equals("gpu"));
                nativeEnabled.setBoolean(null, !raster.equals("java"));
            }
            Class<?> gpu=Class.forName("javax.microedition.m3g.GpuRasterizer");
            gpuEnabled=gpu.getDeclaredField("enabled"); gpuEnabled.setAccessible(true);
            gpuDraws=gpu.getDeclaredField("draws"); gpuDraws.setAccessible(true);
            gpuRenderer=gpu.getDeclaredField("renderer"); gpuRenderer.setAccessible(true);
            gpuUploads=gpu.getDeclaredField("uploads"); gpuUploads.setAccessible(true);
            gpuReadbacks=gpu.getDeclaredField("readbacks"); gpuReadbacks.setAccessible(true);
            gpuRetain=gpu.getDeclaredField("retainTargets"); gpuRetain.setAccessible(true);
            gpuBatch=gpu.getDeclaredField("batchSubmission"); gpuBatch.setAccessible(true);
            gpuSubmissions=gpu.getDeclaredField("submissions"); gpuSubmissions.setAccessible(true);
            gpuSubmitted=gpu.getDeclaredField("submittedDraws"); gpuSubmitted.setAccessible(true);
            gpuGeometry=gpu.getDeclaredField("geometryUploads"); gpuGeometry.setAccessible(true);
            gpuCache=gpu.getDeclaredField("textureCacheEnabled"); gpuCache.setAccessible(true);
            gpuTextureUploads=gpu.getDeclaredField("textureUploads"); gpuTextureUploads.setAccessible(true);
            Class<?> stage=Class.forName("javax.microedition.m3g.GpuGeometry");
            hardwareGeometry=stage.getDeclaredField("enabled"); hardwareGeometry.setAccessible(true);
            hardwareDraws=stage.getDeclaredField("draws"); hardwareDraws.setAccessible(true);
            discardDepth=gpu.getDeclaredField("discardReleasedDepth");discardDepth.setAccessible(true);
            depthReadbacks=gpu.getDeclaredField("depthReadbacks");depthReadbacks.setAccessible(true);
            if(depthPolicy!=null) {
                assertTrue("benchGpuDiscardDepth must be on or off",depthPolicy.equals("on") || depthPolicy.equals("off"));
                discardDepth.setBoolean(null,depthPolicy.equals("on"));
            }
            if(geometry!=null) {
                assertTrue("benchGpuGeometry must be on or off",geometry.equals("on") || geometry.equals("off"));
                hardwareGeometry.setBoolean(null,geometry.equals("on"));
            }
            if(caching!=null) {
                assertTrue("benchGpuTextureCache must be on or off",caching.equals("on") || caching.equals("off"));
                gpuCache.setBoolean(null,caching.equals("on"));
            }
            if(batching!=null) {
                assertTrue("benchGpuBatch must be on or off",batching.equals("on") || batching.equals("off"));
                gpuBatch.setBoolean(null,batching.equals("on"));
            }
            if(resident!=null) {
                assertTrue("benchGpuResident must be on or off",resident.equals("on") || resident.equals("off"));
                gpuRetain.setBoolean(null,resident.equals("on"));
            }
            gpuEnabled.setBoolean(null,"gpu".equals(raster));
        } catch (ClassNotFoundException baseline) {
            assertNull("No selectable native software backend in this APK", raster);
        }
        String lighting = InstrumentationRegistry.getArguments().getString("benchLightingCache");
        java.lang.reflect.Field lightingCache=null, shadedVertices=null;
        if (lighting != null) {
            assertTrue("benchLightingCache must be on or off", lighting.equals("on") || lighting.equals("off"));
            Class<?> triangle=Class.forName("javax.microedition.m3g.Triangle");
            lightingCache=triangle.getDeclaredField("cacheLighting"); lightingCache.setAccessible(true);
            shadedVertices=triangle.getDeclaredField("shadedVertices"); shadedVertices.setAccessible(true);
            lightingCache.setBoolean(null,lighting.equals("on"));
        }
        File root = new File(context.getExternalFilesDir(null), "m3g-benchmark/" + run);
        assertTrue("Do not overwrite previous samples", root.mkdirs());
        PowerManager power = (PowerManager) context.getSystemService(android.content.Context.POWER_SERVICE);
        JSONObject report = new JSONObject().put("package", context.getPackageName())
                .put("device", android.os.Build.MODEL).put("sdk", android.os.Build.VERSION.SDK_INT)
                .put("debuggable", (context.getApplicationInfo().flags
                        & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0)
                .put("thermalStart", power.getCurrentThermalStatus())
                .put("lightingCache", lighting == null ? "default" : lighting)
                .put("gpuResident",gpuRetain!=null && gpuRetain.getBoolean(null))
                .put("gpuBatch",gpuBatch!=null && gpuBatch.getBoolean(null))
                .put("gpuTextureCache",gpuCache!=null && gpuCache.getBoolean(null))
                .put("gpuHardwareGeometry",hardwareGeometry!=null && hardwareGeometry.getBoolean(null))
                .put("gpuDiscardDepth",discardDepth!=null && discardDepth.getBoolean(null))
                .put("raster", "gpu".equals(raster) ? "gpu" : nativeEnabled == null ? "legacy" : nativeEnabled.getBoolean(null) ? "native" : "java");
        JSONArray cases = new JSONArray();
        report.put("cases", cases);
        for (int[] size : new int[][]{{240,320},{320,240}}) {
            for (String kind : new String[]{"rgb-nearest","rgb-linear","rgba-alpha","lit-fog","exp-fog","multi-texture",
                    "mip-nearest","mip-linear","sprites","lighting-grid","multi-draw",
                    "modulate","modulate-x2","depth-offset","material-churn"}) {
                String name = kind + "-" + size[0] + "x" + size[1];
                if (kind.equals("multi-texture") && (Integer) Graphics3D.getProperties().get("numTextureUnits") < 4) {
                    assertNull("Candidate must support all four layers", nativeEnabled);
                    cases.put(new JSONObject().put("name",name).put("skipped","Requires four texture units"));
                    continue;
                }
                Graphics3D.initGraphics3D();
                Graphics3D renderer = Graphics3D.getInstance();
                Image target = Image.createImage(size[0], size[1]);
                Node mesh = scene(kind);
                Camera camera = new Camera();
                camera.setPerspective(60, (float) size[0] / size[1], 0.1f, 10);
                renderer.setCamera(camera, new Transform());
                Light light = new Light();
                light.setMode(Light.AMBIENT);
                light.setIntensity(0.8f);
                renderer.addLight(light, new Transform());
                if (kind.equals("lighting-grid")) {
                    light.setIntensity(0.15f);
                    for (int mode : new int[]{Light.DIRECTIONAL,Light.OMNI,Light.SPOT}) {
                        Light extra=new Light(); extra.setMode(mode); extra.setIntensity(0.25f);
                        extra.setColor(mode==Light.DIRECTIONAL ? 0xff8080 : mode==Light.OMNI ? 0x80ff80 : 0x8080ff);
                        extra.setSpotAngle(60); extra.setSpotExponent(4);
                        Transform lightTransform=new Transform(); lightTransform.postTranslate(.5f,.3f,0);
                        renderer.addLight(extra,lightTransform);
                    }
                }
                Background background = new Background();
                background.setColor(0xff314159);
                Transform model = new Transform();
                java.lang.reflect.Field counter=kind.equals("sprites") ? nativeSprites : nativeDraws;
                long nativeStart = counter == null ? 0 : counter.getLong(null);
                long gpuStart = gpuDraws == null ? 0 : gpuDraws.getLong(null);
                long uploadStart=gpuUploads==null?0:gpuUploads.getLong(null);
                long readStart=gpuReadbacks==null?0:gpuReadbacks.getLong(null);
                long submitStart=gpuSubmissions==null?0:gpuSubmissions.getLong(null);
                long renderedStart=gpuSubmitted==null?0:gpuSubmitted.getLong(null);
                long geometryStart=gpuGeometry==null?0:gpuGeometry.getLong(null);
                long textureStart=gpuTextureUploads==null?0:gpuTextureUploads.getLong(null);
                long hardwareStart=hardwareDraws==null?0:hardwareDraws.getLong(null);
                long depthStart=depthReadbacks==null?0:depthReadbacks.getLong(null);
                long shadedStart = shadedVertices == null ? 0 : shadedVertices.getLong(null);
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
                try (FileOutputStream out = new FileOutputStream(new File(root, name + ".png"))) {
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
                }
                cases.put(new JSONObject().put("name", name).put("framesPerBatch", 32)
                        .put("warmupFrames", 64).put("wallNs", wall).put("threadCpuNs", cpu)
                        .put("pixelCrc32", crc.getValue()).put("changedPixels", changed)
                        .put("gpuDraws", gpuDraws == null ? 0 : gpuDraws.getLong(null)-gpuStart)
                        .put("gpuUploads",gpuUploads==null?0:gpuUploads.getLong(null)-uploadStart)
                        .put("gpuReadbacks",gpuReadbacks==null?0:gpuReadbacks.getLong(null)-readStart)
                        .put("gpuSubmissions",gpuSubmissions==null?0:gpuSubmissions.getLong(null)-submitStart)
                        .put("gpuSubmittedDraws",gpuSubmitted==null?0:gpuSubmitted.getLong(null)-renderedStart)
                        .put("gpuGeometryUploads",gpuGeometry==null?0:gpuGeometry.getLong(null)-geometryStart)
                        .put("gpuTextureUploads",gpuTextureUploads==null?0:gpuTextureUploads.getLong(null)-textureStart)
                        .put("gpuHardwareDraws",hardwareDraws==null?0:hardwareDraws.getLong(null)-hardwareStart)
                        .put("gpuDepthReadbacks",depthReadbacks==null?0:depthReadbacks.getLong(null)-depthStart)
                        .put("shadedVertices", shadedVertices == null ? -1 : shadedVertices.getLong(null)-shadedStart)
                        .put("nativeDraws", counter == null ? 0 : counter.getLong(null)-nativeStart));
                if ("native".equals(raster)) assertTrue("Native path was not exercised", counter.getLong(null)>nativeStart);
                if ("gpu".equals(raster)) {
                    assertTrue("GPU path was not exercised",gpuDraws.getLong(null)>gpuStart);
                    assertEquals("Every queued draw must reach GL",gpuDraws.getLong(null)-gpuStart,
                            gpuSubmitted.getLong(null)-renderedStart);
                }
                if ("gpu".equals(raster) && (kind.equals("multi-draw") || kind.equals("material-churn"))) {
                    assertEquals(224*16,gpuDraws.getLong(null)-gpuStart);
                    assertEquals(gpuRetain.getBoolean(null)?224:224*16,gpuUploads.getLong(null)-uploadStart);
                    assertEquals(gpuUploads.getLong(null)-uploadStart,gpuReadbacks.getLong(null)-readStart);
                    if(gpuRetain.getBoolean(null)) {
                        assertEquals(224*(gpuBatch.getBoolean(null)?2:17),gpuSubmissions.getLong(null)-submitStart);
                        assertEquals(224*(gpuBatch.getBoolean(null)?2:16),gpuGeometry.getLong(null)-geometryStart);
                    }
                    if(kind.equals("material-churn")) assertEquals(gpuCache.getBoolean(null)?4:224*16,
                            gpuTextureUploads.getLong(null)-textureStart);
                }
                renderer.releaseTarget();
            }
        }
        report.put("thermalEnd", power.getCurrentThermalStatus());
        report.put("gpuRenderer",gpuRenderer==null?"none":gpuRenderer.get(null));
        try (FileOutputStream out = new FileOutputStream(new File(root, "report.json"))) {
            out.write(report.toString(2).getBytes(StandardCharsets.UTF_8));
        }
        if (nativeEnabled != null) nativeEnabled.setBoolean(null, true);
        if (gpuEnabled != null) gpuEnabled.setBoolean(null, false);
        if (gpuRetain != null) gpuRetain.setBoolean(null, true);
        if (gpuBatch != null) gpuBatch.setBoolean(null, true);
        if (gpuCache != null) gpuCache.setBoolean(null, true);
        if (hardwareGeometry != null) hardwareGeometry.setBoolean(null, false);
        if (discardDepth != null) discardDepth.setBoolean(null,true);
        if (lightingCache != null) lightingCache.setBoolean(null, true);
    }

    private void frame(Graphics3D renderer, Image image, Node mesh, Background background, Transform transform) {
        renderer.bindTarget(image.getGraphics(), true, 0);
        try { renderer.clear(background); renderer.render(mesh, transform); }
        finally { renderer.releaseTarget(); }
    }

    private Node scene(String kind) {
        if (kind.equals("lighting-grid")) return lightingGrid();
        if (kind.equals("multi-draw") || kind.equals("material-churn")) {
            Group group=new Group();
            Mesh source=(Mesh)scene("rgb-linear");
            Appearance[] materials=new Appearance[4];
            for(int i=0;i<4;i++) {
                byte[] pixels=new byte[64*64*3];
                for(int p=0;p<pixels.length;p+=3) { pixels[p]=(byte)(64+i*48); pixels[p+1]=(byte)(192-i*32); pixels[p+2]=(byte)(64+(p/3)%64); }
                Texture2D texture=new Texture2D(new Image2D(Image2D.RGB,64,64,pixels));
                texture.setBlending(Texture2D.FUNC_REPLACE); texture.setFiltering(Texture2D.FILTER_BASE_LEVEL,Texture2D.FILTER_LINEAR);
                materials[i]=new Appearance(); materials[i].setTexture(0,texture);
            }
            for(int y=0;y<4;y++) for(int x=0;x<4;x++) {
                Mesh mesh=new Mesh(source.getVertexBuffer(),source.getIndexBuffer(0),kind.equals("material-churn")?materials[x]:source.getAppearance(0));
                mesh.setScale(.25f,.25f,1); mesh.setTranslation((x-1.5f)*.6f,(y-1.5f)*.6f,0);
                group.addChild(mesh);
            }
            return group;
        }
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
        boolean alpha = kind.equals("rgba-alpha") || kind.equals("sprites");
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
        if (kind.startsWith("mip-")) texture.setFiltering(kind.equals("mip-linear")
                ? Texture2D.FILTER_LINEAR : Texture2D.FILTER_NEAREST,Texture2D.FILTER_LINEAR);
        Appearance style = new Appearance();
        style.setTexture(0, texture);
        if (kind.equals("multi-texture")) {
            for (int unit=1; unit<4; unit++) {
                Texture2D layer = new Texture2D(texture.getImage());
                layer.setBlending(unit==2 ? Texture2D.FUNC_BLEND : Texture2D.FUNC_MODULATE);
                layer.setBlendColor(0x406090);
                layer.setFiltering(Texture2D.FILTER_BASE_LEVEL,
                        unit==1 ? Texture2D.FILTER_LINEAR : Texture2D.FILTER_NEAREST);
                layer.setTranslation(unit*0.125f,0,0);
                style.setTexture(unit,layer);
                vertices.setTexCoords(unit,coords,1f/128,null);
            }
        }
        PolygonMode polygon = new PolygonMode();
        polygon.setCulling(PolygonMode.CULL_NONE);
        polygon.setPerspectiveCorrectionEnable(true);
        style.setPolygonMode(polygon);
        CompositingMode blend = new CompositingMode();
        blend.setBlending(alpha ? CompositingMode.ALPHA : CompositingMode.REPLACE);
        if(kind.equals("modulate")) blend.setBlending(CompositingMode.MODULATE);
        if(kind.equals("modulate-x2")) blend.setBlending(CompositingMode.MODULATE_X2);
        if(kind.equals("depth-offset")) blend.setDepthOffset(-1,-100000000f);
        style.setCompositingMode(blend);
        if (kind.equals("lit-fog") || kind.equals("exp-fog")) {
            VertexArray normal = new VertexArray(count, 3, 1);
            normal.set(0, count, normals);
            vertices.setNormals(normal);
            Material material = new Material();
            material.setColor(Material.AMBIENT | Material.DIFFUSE, 0xffd0b090);
            style.setMaterial(material);
            Fog fog = new Fog();
            fog.setMode(kind.equals("exp-fog") ? Fog.EXPONENTIAL : Fog.LINEAR);
            fog.setDensity(0.4f);
            fog.setColor(0x314159);
            fog.setLinear(1, 4);
            style.setFog(fog);
        }
        if (kind.equals("sprites")) {
            Group group=new Group();
            for (int i=0; i<3; i++) {
                Sprite3D sprite=new Sprite3D(true,texture.getImage(),style);
                sprite.setScale(2.4f,2.4f,1);
                sprite.setTranslation((i-1)*0.3f,0,-2.2f+i*0.1f);
                group.addChild(sprite);
            }
            return group;
        }
        return new Mesh(vertices, new TriangleStripArray(0, strips), style);
    }

    private Mesh lightingGrid() {
        int cells=32, side=cells+1, count=side*side;
        short[] positions=new short[count*3]; byte[] normals=new byte[count*3];
        for (int y=0; y<side; y++) for (int x=0; x<side; x++) {
            int v=y*side+x;
            positions[v*3]=(short)(-120+x*240/cells);
            positions[v*3+1]=(short)(120-y*240/cells);
            positions[v*3+2]=(short)(-200-x);
            normals[v*3+2]=127;
        }
        int[] strips=new int[cells], indices=new int[cells*side*2];
        for (int y=0,i=0; y<cells; y++) {
            strips[y]=side*2;
            for (int x=0; x<side; x++) { indices[i++]=y*side+x; indices[i++]=(y+1)*side+x; }
        }
        VertexArray points=new VertexArray(count,3,2); points.set(0,count,positions);
        VertexArray normal=new VertexArray(count,3,1); normal.set(0,count,normals);
        VertexBuffer vertices=new VertexBuffer(); vertices.setPositions(points,.01f,null); vertices.setNormals(normal);
        Material material=new Material(); material.setColor(Material.DIFFUSE,0xffd0b090);
        material.setColor(Material.SPECULAR,0x808080); material.setShininess(16);
        Appearance style=new Appearance(); style.setMaterial(material);
        PolygonMode poly=new PolygonMode(); poly.setCulling(PolygonMode.CULL_NONE); style.setPolygonMode(poly);
        return new Mesh(vertices,new TriangleStripArray(indices,strips),style);
    }
}
