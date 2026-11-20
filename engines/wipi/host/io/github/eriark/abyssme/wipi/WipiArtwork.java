/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.wipi;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.*;
import ru.playsoftware.j2meloader.applist.GameArtwork;

final class WipiArtwork {
    static void extract(File archive, File directory) throws IOException {
        try (ZipFile zip = new ZipFile(archive, StandardCharsets.ISO_8859_1)) {
            for (ZipEntry entry : Collections.list(zip.entries())) {
                String path = entry.getName();
                if (!path.endsWith(".jar") || entry.getSize() < 0 || entry.getSize() > 64L * 1024 * 1024) continue;
                String parent = path.substring(0, path.lastIndexOf('/') + 1);
                if (zip.getEntry(parent + "__adf__") == null && zip.getEntry(parent + "app_info") == null
                        && zip.getEntry(path.substring(0, path.length()-4) + ".msd") == null) continue;
                File temp = File.createTempFile(".guest-", ".jar", directory);
                try {
                    try (InputStream in = zip.getInputStream(entry); OutputStream out = new FileOutputStream(temp)) {
                        byte[] buf = new byte[8192];
                        for (int n; (n = in.read(buf)) >= 0;) out.write(buf, 0, n);
                    }
                    try (JarFile jar = new JarFile(temp, false)) {
                        Manifest mf = jar.getManifest();
                        String icon = mf == null ? null : mf.getMainAttributes().getValue("MIDlet-Icon");
                        if (icon == null && mf != null) {
                            String midlet = mf.getMainAttributes().getValue("MIDlet-1");
                            if (midlet != null && midlet.split(",", -1).length == 3) icon = midlet.split(",", -1)[1];
                        }
                        GameArtwork.extractArchive(temp, directory, icon, false);
                    }
                } catch (IOException | IllegalArgumentException e) {
                    android.util.Log.d("WipiArtwork","Guest JAR artwork unavailable",e);
                } finally { Files.deleteIfExists(temp.toPath()); }
                // The carrier keeps its launcher artwork outside the guest JAR.
                for (String name : new String[]{"big.icon", "middle.icon", "small.icon"}) {
                    ZipEntry image = zip.getEntry(parent + name);
                    if (image == null || image.getSize() <= 0 || image.getSize() > 2 * 1024 * 1024) continue;
                    android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    try (InputStream in = zip.getInputStream(image)) { android.graphics.BitmapFactory.decodeStream(in, null, bounds); }
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0 || (long)bounds.outWidth * bounds.outHeight > 1024 * 1024) continue;
                    try (InputStream in = zip.getInputStream(image)) {
                        android.graphics.Bitmap icon = android.graphics.BitmapFactory.decodeStream(in);
                        if (icon == null) continue;
                        try { GameArtwork.writeBitmap(new File(directory, "icon.png"), icon); }
                        finally { icon.recycle(); }
                    }
                    break;
                }
                return;
            }
        }
    }
    private WipiArtwork() {}
}
