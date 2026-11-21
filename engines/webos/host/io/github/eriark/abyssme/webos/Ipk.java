/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.webos;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.GZIPInputStream;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.json.*;

final class Ipk {
    private static final String APPS = "usr/palm/applications/";
    static File resource(File root, String relative) throws IOException {
        File file = new File(root, relative).getCanonicalFile();
        if (!file.toPath().startsWith(root.getCanonicalFile().toPath()) || file.equals(root.getCanonicalFile()))
            throw new IOException("Invalid webOS resource path");
        return file;
    }
    static JSONObject manifest(File app) throws IOException, JSONException {
        return new JSONObject(new String(Files.readAllBytes(new File(app, "appinfo.json").toPath()), StandardCharsets.UTF_8));
    }

    static File application(Context c, File ipk, String hash) throws IOException {
        File cache = new File(c.getCacheDir(), "webos-apps");
        Files.createDirectories(cache.toPath());
        File ready = new File(cache, hash);
        if (ready.isDirectory()) return ready;
        File staging = Files.createTempDirectory(cache.toPath(), "unpack-").toFile();
        try {
            unpack(ipk, staging);
            File selected = null;
            for (File dir : staging.listFiles()) {
                if (!new File(dir, "appinfo.json").isFile()) continue;
                JSONObject meta = manifest(dir);
                if (!dir.getName().equals(meta.getString("id"))) throw new IOException("IPK application ID mismatch");
                if (!"web".equalsIgnoreCase(meta.optString("type", "web"))) continue;
                if (!resource(dir, meta.optString("main", "index.html")).isFile()) throw new IOException("Missing webOS start page");
                if (selected != null) throw new IOException("IPK contains multiple web apps");
                selected = dir;
            }
            if (selected == null) throw new IOException("This IPK has no web application; native PDK games need a different runtime");
            if (!selected.renameTo(ready) && !ready.isDirectory()) throw new IOException("Cannot prepare webOS application");
            return ready;
        } catch (JSONException e) { throw new IOException("Invalid webOS app manifest", e); }
        finally { discard(staging); }
    }
    private static void discard(File dir) {
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) discard(f);
        dir.delete();
    }
    private static String name(String path) {
        while (path.startsWith("./")) path = path.substring(2);
        return path;
    }
    private static void unpack(File ipk, File staging) throws IOException {
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(ipk))) {
            input.mark(2);
            boolean gzip = input.read() == 31 && input.read() == 139;
            input.reset();
            try (ArchiveInputStream<?> outer = gzip ? new TarArchiveInputStream(new GZIPInputStream(input)) : new ArArchiveInputStream(input)) {
                for (ArchiveEntry member; (member = outer.getNextEntry()) != null;) {
                    String path = name(member.getName());
                    if (!path.equals("data.tar.gz") && !path.equals("data.tar")) continue;
                    InputStream data = path.endsWith(".gz") ? new GZIPInputStream(outer) : outer;
                    try (TarArchiveInputStream tar = new TarArchiveInputStream(data)) {
                        for (TarArchiveEntry file; (file = tar.getNextEntry()) != null;) {
                            String entry = name(file.getName());
                            if (!file.isFile() || file.isSymbolicLink() || file.isLink() || !entry.startsWith(APPS)) continue;
                            String relative = entry.substring(APPS.length());
                            if (relative.contains("\\") || java.util.Arrays.asList(relative.split("/")).contains(".."))
                                throw new IOException("Invalid webOS archive path");
                            File dest = resource(staging, relative);
                            Files.createDirectories(dest.getParentFile().toPath());
                            Files.copy(tar, dest.toPath());
                        }
                    }
                    return;
                }
            }
        }
        throw new IOException("Missing IPK data archive");
    }
}
