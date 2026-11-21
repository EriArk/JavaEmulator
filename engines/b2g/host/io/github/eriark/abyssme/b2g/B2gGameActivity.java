/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.b2g;

import java.io.*;
import java.util.zip.*;
import io.github.eriark.abyssme.storage.StoragePaths;
import ru.playsoftware.j2meloader.catalog.B2gManifest;
import ru.playsoftware.j2meloader.handheld.PackagedWebActivity;

public final class B2gGameActivity extends PackagedWebActivity {
    private ZipFile zip;
    private B2gManifest manifest;
    private int[][] screens;
    private boolean kai() { return "kaios".equals(getIntent().getStringExtra("artworkEngine")); }
    @Override protected String engine() { return kai() ? "kaios" : "firefox"; }
    @Override protected String platformTitle() { return kai() ? "KaiOS" : "Firefox OS"; }
    @Override protected String browserProfile() { return "b2g"; }
    @Override protected String originSuffix() { return "." + engine() + ".invalid"; }
    @Override protected boolean phoneKeys() { return true; }
    @Override protected int[][] sizes() { return screens; }
    @Override protected String extraScript() {
        return "<script src=\"/.abyss/phone.js\" data-legacy=\"" + !manifest.modern + "\"></script>";
    }
    @Override protected String extraPolicy() {
        return "; connect-src 'self'; worker-src 'self' blob:; frame-src 'self'; frame-ancestors 'none'; base-uri 'self'"
                + (manifest.csp.isEmpty() ? "" : ", " + manifest.csp);
    }

    @Override protected String openGame(String id) throws IOException {
        zip = new ZipFile(new File(StoragePaths.library(this), engine() + "/packages/" + id + "/game.zip"));
        manifest = B2gManifest.read(zip);
        if (manifest == null) throw new IOException("Missing local phone game manifest");
        screens = kai() ? new int[][]{{240,320}, {320,480}, {480,800}} : new int[][]{{320,480}, {240,320}, {480,800}};
        if (manifest.orientation.contains("landscape")) for (int[] size : screens) {
            int width = size[0]; size[0] = size[1]; size[1] = width;
        }
        return manifest.launch;
    }
    @Override protected InputStream openResource(String path) throws IOException {
        if (path.equals("/.abyss/phone.js")) return getAssets().open("b2g/keys.js");
        if (path.contains("\\") || java.util.Arrays.asList(path.split("/")).contains("..")) throw new FileNotFoundException();
        if (path.endsWith("/")) path += "index.html";
        ZipEntry entry = zip.getEntry(manifest.root + path.substring(1));
        if (entry == null || entry.isDirectory()) throw new FileNotFoundException();
        return zip.getInputStream(entry);
    }
    @Override protected void closeGame() {
        if (zip != null) try { zip.close(); } catch (IOException ignored) {}
    }
}
