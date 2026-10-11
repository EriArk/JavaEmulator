package javax.microedition.m3g;

import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import org.json.JSONObject;
import javax.microedition.util.ContextHolder;

/** Bounded first-use state inventory, not game assets or a frame-time measurement. */
final class RenderStateTrace {
    private static final HashSet<String> seen = new HashSet<>();
    private static int calls;
    static synchronized void record(VertexBuffer vertices, Appearance appearance, int alpha) {
        if (vertices == null || appearance == null || seen.size() >= 256 || calls++ >= 20000) return;
        try {
            JSONObject out = new JSONObject();
            out.put("vertices", vertices.getVertexCount());
            out.put("defaultColor", Integer.toHexString(vertices.getDefaultColor()));
            out.put("vertexColors", vertices.getColors() != null);
            VertexArray colors = vertices.getColors();
            if (colors != null) {
                int components = colors.getComponentCount();
                out.put("colorComponents", components);
                int min = 255, max = 0;
                for (int i = components - 1; components == 4 && i < colors.vertArrayByteSize.length; i += components) {
                    int value = colors.vertArrayByteSize[i] & 255;
                    min = Math.min(min, value); max = Math.max(max, value);
                }
                out.put("vertexAlphaMin", components == 4 ? min : 255);
                out.put("vertexAlphaMax", components == 4 ? max : 255);
            }
            out.put("normals", vertices.getNormals() != null);
            out.put("alphaFactor", alpha);
            CompositingMode c = appearance.getCompositingMode();
            if (c == null) c = new CompositingMode();
            out.put("blend", c.getBlending());
            out.put("threshold", c.getAlphaThreshold());
            out.put("depthTest", c.isDepthTestEnabled());
            out.put("depthWrite", c.isDepthWriteEnabled());
            Material m = appearance.getMaterial();
            if (m != null) {
                out.put("diffuse", Integer.toHexString(m.getColor(Material.DIFFUSE)));
                out.put("tracking", m.isVertexColorTrackingEnabled());
            }
            Texture2D t = appearance.getTexture(0);
            if (t != null) {
                Image2D im = t.getImage();
                out.put("format", im.getFormat());
                out.put("width", im.getWidth());
                out.put("height", im.getHeight());
                out.put("textureBlend", t.getBlending());
                out.put("filter", t.getImageFilter());
                int min = 255, max = 0;
                for (int pixel : im.image) { min = Math.min(min, pixel >>> 24); max = Math.max(max, pixel >>> 24); }
                out.put("alphaMin", min);
                out.put("alphaMax", max);
            }
            if (!seen.add(out.toString())) return;
            out.put("draw", calls);
            File file = new File(ContextHolder.getAppContext().getCacheDir(), "m3g-states.jsonl");
            try (FileOutputStream stream = new FileOutputStream(file, seen.size() > 1)) {
                stream.write((out.toString() + "\n").getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) { Log.e("M3gTrace", "State capture failed", e); }
    }
}
