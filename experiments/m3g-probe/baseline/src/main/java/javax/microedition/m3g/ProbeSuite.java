package javax.microedition.m3g;

import android.graphics.Bitmap;
import io.github.eriark.m3gprobe.SceneSuite;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONObject;

/** Same shared scenes, but no alternative-engine code is compiled into this APK. */
public final class ProbeSuite {
    public final Map<String, Bitmap> frames = new LinkedHashMap<>();
    public JSONObject run() throws Exception {
        SceneSuite suite = new SceneSuite();
        JSONObject report = new JSONObject().put("engine", "Existing EPL M3G").put("suiteVersion", 2)
                .put("javaTree", "af43b288fbf407f15cb50bb94ee2a405f2376109")
                .put("nativeTree", "2a7723dffafbb4699f8da6ab0f675b17a9d3c69f")
                .put("device", android.os.Build.MODEL).put("sdk", android.os.Build.VERSION.SDK_INT)
                .put("abi", android.os.Build.SUPPORTED_ABIS[0]).put("tests", suite.run());
        frames.putAll(suite.frames);
        return report;
    }
}
