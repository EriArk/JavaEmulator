/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.webos;

import android.net.Uri;
import java.io.*;
import java.util.Arrays;
import java.util.regex.*;
import org.json.JSONException;
import ru.playsoftware.j2meloader.handheld.PackagedWebActivity;

public final class WebOsGameActivity extends PackagedWebActivity {
    private static final Pattern EMPTY_SCRIPT = Pattern.compile("(<script\\b[^>]*?)\\s*/>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ENYO_PATH = Pattern.compile(
            "^/(?:usr/palm/frameworks|opt/PalmSDK/Current/share/framework)/enyo/(?:1\\.0|0\\.10)/framework/(.+)$");
    private File app;
    private static final int[][] SIZES = {{320, 480}, {480, 800}, {1024, 768}};

    @Override protected String engine() { return "webos"; }
    @Override protected String platformTitle() { return "Palm webOS"; }
    @Override protected int[][] sizes() { return SIZES; }
    @Override protected String openGame(String id) throws IOException, JSONException {
        app = new WebOsLibrary().installed(this, id);
        return "/" + Uri.encode(Ipk.manifest(app).optString("main", "index.html"), "/");
    }
    @Override protected String prepareHtml(String html) {
        return EMPTY_SCRIPT.matcher(html).replaceAll("$1></script>");
    }
    @Override protected InputStream openResource(String path) throws IOException {
        Matcher framework = ENYO_PATH.matcher(path);
        if (framework.matches()) {
            String asset = framework.group(1);
            if (Arrays.asList(asset.split("/")).contains("..") || asset.contains("\\")) throw new FileNotFoundException();
            return getAssets().open("webos/enyo/" + asset);
        }
        if (path.equals("/usr/palm/frameworks/mojo/mojo.js")) return getAssets().open("webos/mojo.js");
        return new FileInputStream(Ipk.resource(app, path.substring(1)));
    }
}
