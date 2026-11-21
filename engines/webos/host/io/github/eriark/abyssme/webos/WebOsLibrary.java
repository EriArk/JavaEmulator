/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.webos;

import android.content.Context;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.json.*;
import ru.playsoftware.j2meloader.catalog.SourceIdentity;
import ru.playsoftware.j2meloader.handheld.ManagedGameLibrary;

public final class WebOsLibrary extends ManagedGameLibrary {
    public WebOsLibrary() { super("webos", WebOsGameActivity.class); }

    @Override protected String inspect(Context context, File file, String name, Properties info) throws IOException {
        if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            File unpacked = new File(file.getParentFile(), "unpacked.ipk");
            try (ZipFile zip = new ZipFile(file)) {
                ZipEntry game = null;
                for (Enumeration<? extends ZipEntry> it = zip.entries(); it.hasMoreElements();) {
                    ZipEntry entry = it.nextElement();
                    if (entry.isDirectory() || !entry.getName().toLowerCase(Locale.ROOT).endsWith(".ipk")) continue;
                    if (game != null) throw new IOException("Choose one IPK from this collection");
                    game = entry;
                }
                if (game == null) throw new IOException("No IPK in ZIP");
                try (InputStream in = zip.getInputStream(game)) { Files.copy(in, unpacked.toPath()); }
            }
            Files.move(unpacked.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        String id = SourceIdentity.sha256(file);
        File app = Ipk.application(context, file, id);
        try {
            JSONObject manifest = Ipk.manifest(app);
            info.setProperty("title", manifest.optString("title", manifest.getString("id")));
            info.setProperty("version", manifest.optString("version"));
            info.setProperty("format", "IPK");
            String icon = manifest.optString("icon", "icon.png");
            if (!icon.isEmpty()) {
                File image = Ipk.resource(app, icon);
                if (image.isFile() && icon.toLowerCase(Locale.ROOT).endsWith(".png"))
                    Files.copy(image.toPath(), new File(file.getParentFile(), "icon.png").toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (JSONException e) { throw new IOException("Invalid webOS app manifest", e); }
        return id;
    }

    File installed(Context c, String id) throws IOException {
        if (id == null || !id.matches("[0-9a-f]{64}")) throw new IOException("Invalid webOS game identity");
        return Ipk.application(c, new File(new File(root(c), id), "game.ipk"), id);
    }
    @Override protected String filename(Properties info) { return "game.ipk"; }
    @Override protected String variant(Properties info) { return "webOS " + info.getProperty("version", ""); }
    @Override protected int firstCardId() { return -2010000000; }
    @Override protected int platformId(Properties info) { return 18; }
    @Override protected String platformName(Properties info) { return "Palm webOS"; }
}
