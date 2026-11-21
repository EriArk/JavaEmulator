/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.catalog;

import android.net.Uri;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.json.*;

/** Recognizes a packaged phone app by its manifest and an actual local launch resource. */
public final class B2gManifest {
    public static final int FIREFOX = 19, KAIOS = 20;
    public final Set<Integer> platforms = new LinkedHashSet<>();
    public final String root, launch, title, version, orientation, icon, csp;
    public final boolean modern;

    private B2gManifest(ZipFile zip, ZipEntry entry, JSONObject json, boolean modern) throws IOException {
        this.modern = modern;
        root = entry.getName().substring(0, entry.getName().lastIndexOf('/') + 1);
        title = json.optString("name", json.optString("short_name")).trim();
        if (title.isEmpty()) throw new IOException("Phone game manifest has no name");
        JSONObject features = json.optJSONObject("b2g_features");
        if (features == null) features = json.optJSONObject("b2g_feature");
        String start = json.optString(modern ? "start_url" : "launch_path", "/index.html");
        Uri uri = Uri.parse(start);
        String path = localPath(uri);
        if (path != null && (path.isEmpty() || path.endsWith("/"))) path += "index.html";
        if (path == null || zip.getEntry(root + path) == null || zip.getEntry(root + path).isDirectory()) throw new IOException("Missing local launch page");
        launch = "/" + Uri.encode(path, "/") + (uri.getEncodedQuery() == null ? "" : "?" + uri.getEncodedQuery())
                + (uri.getEncodedFragment() == null ? "" : "#" + uri.getEncodedFragment());
        version = json.optString("version", features == null ? "" : features.optString("version"));
        csp = json.optString("csp", features == null ? "" : features.optString("csp"));
        orientation = json.optString("orientation", "portrait");
        icon = icon(zip, json.opt("icons"));
        if (modern || features != null || json.toString().toLowerCase(Locale.ROOT).contains("kaios")) platforms.add(KAIOS);
        else {
            platforms.add(FIREFOX);
            platforms.add(KAIOS); // Both systems use the same legacy manifest format.
        }
    }

    static int remembered(android.content.Context c, Uri uri) {
        return c.getSharedPreferences("web-package-platforms", 0).getInt(SourceIdentity.libraryKey(SourceIdentity.key(uri)), -1);
    }
    static void remember(android.content.Context c, Uri uri, int platform) {
        c.getSharedPreferences("web-package-platforms", 0).edit()
                .putInt(SourceIdentity.libraryKey(SourceIdentity.key(uri)), platform).apply();
    }

    public static String localPath(Uri uri) {
        if (uri.getScheme() != null || uri.getEncodedAuthority() != null) return null;
        String path = uri.getPath();
        if (path == null || path.contains("\\") || path.indexOf('\0') >= 0) return null;
        if (path.startsWith("/")) path = path.substring(1);
        while (path.startsWith("./")) path = path.substring(2);
        if (Arrays.asList(path.split("/")).contains("..")) return null;
        return path;
    }

    private String icon(ZipFile zip, Object icons) {
        Map<Integer, String> paths = new TreeMap<>(Collections.reverseOrder());
        if (icons instanceof JSONObject) {
            JSONObject map = (JSONObject) icons;
            for (Iterator<String> it = map.keys(); it.hasNext();) {
                String key = it.next();
                paths.put(size(key), map.optString(key));
            }
        } else if (icons instanceof JSONArray) {
            JSONArray list = (JSONArray) icons;
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.optJSONObject(i);
                if (item != null) paths.put(size(item.optString("sizes")), item.optString("src"));
            }
        }
        for (String value : paths.values()) {
            String path = localPath(Uri.parse(value));
            if (path != null && zip.getEntry(root + path) != null) return path;
        }
        return null;
    }
    private static int size(String value) {
        try { return Integer.parseInt(value.split("[x ]", 2)[0]); }
        catch (NumberFormatException e) { return 0; }
    }

    public static B2gManifest read(ZipFile zip) throws IOException {
        String root = "";
        if (zip.getEntry("manifest.webapp") == null && zip.getEntry("manifest.webmanifest") == null) {
            String wrapper = null;
            for (Enumeration<? extends ZipEntry> it = zip.entries(); it.hasMoreElements();) {
                String name = it.nextElement().getName();
                if (!name.matches("[^/]+/manifest\\.(?:webapp|webmanifest)") || name.startsWith("__MACOSX/")) continue;
                String prefix = name.substring(0, name.indexOf('/') + 1);
                if (wrapper != null && !prefix.equals(wrapper)) return null;
                wrapper = prefix;
            }
            if (wrapper == null) return null;
            root = wrapper;
        }
        for (String name : new String[]{"manifest.webmanifest", "manifest.webapp"}) {
            ZipEntry entry = zip.getEntry(root + name);
            if (entry == null || entry.getSize() > 1024 * 1024) continue;
            try (InputStream in = zip.getInputStream(entry)) {
                JSONObject json = new JSONObject(new String(ru.playsoftware.j2meloader.util.IOUtils.toByteArray(in), StandardCharsets.UTF_8));
                boolean modern = name.endsWith(".webmanifest");
                if (modern && !json.has("b2g_features") && !json.has("b2g_feature")) continue;
                return new B2gManifest(zip, entry, json, modern);
            } catch (JSONException | IOException invalid) {
                // A dual-generation package may still contain a valid legacy manifest.
            }
        }
        return null;
    }
}
