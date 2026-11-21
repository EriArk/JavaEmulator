/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.b2g;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.*;
import java.util.Properties;
import java.util.zip.*;
import ru.playsoftware.j2meloader.catalog.B2gManifest;
import ru.playsoftware.j2meloader.catalog.SourceIdentity;
import ru.playsoftware.j2meloader.handheld.ManagedGameLibrary;

public abstract class B2gLibrary extends ManagedGameLibrary {
    public static final class Firefox extends B2gLibrary {
        public Firefox() { super(false); }
    }
    public static final class Kai extends B2gLibrary {
        public Kai() { super(true); }
    }
    private final boolean kai;
    private B2gLibrary(boolean kai) {
        super(kai ? "kaios" : "firefox", B2gGameActivity.class);
        this.kai = kai;
    }
    @Override protected String inspect(Context c, File file, String name, Properties info) throws IOException {
        try (ZipFile zip = new ZipFile(file)) {
            B2gManifest m = B2gManifest.read(zip);
            if (m == null || !m.platforms.contains(platformId(info))) throw new IOException("No packaged game for this platform");
            info.setProperty("title", m.title);
            info.setProperty("format", m.modern ? "webmanifest" : "webapp");
            info.setProperty("version", m.version);
            if (m.icon != null) try (InputStream in = zip.getInputStream(zip.getEntry(m.root + m.icon))) {
                Bitmap image = BitmapFactory.decodeStream(in);
                if (image != null) try (OutputStream out = new FileOutputStream(new File(file.getParentFile(), "icon.png"))) {
                    image.compress(Bitmap.CompressFormat.PNG, 100, out);
                } finally { if (image != null) image.recycle(); }
            }
        }
        return SourceIdentity.sha256(file);
    }
    @Override protected String filename(Properties p) { return "game.zip"; }
    @Override protected int platformId(Properties p) { return kai ? B2gManifest.KAIOS : B2gManifest.FIREFOX; }
    @Override protected String platformName(Properties p) { return kai ? "KaiOS" : "Firefox OS"; }
    @Override protected int firstCardId() { return kai ? -1300000000 : -1400000000; }
    @Override protected String variant(Properties p) { return p.getProperty("format") + " " + p.getProperty("version", ""); }
}
