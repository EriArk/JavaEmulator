/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.iphone;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.ParcelFileDescriptor;
import com.dd.plist.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;
import ru.playsoftware.j2meloader.applist.GameArtwork;
import ru.playsoftware.j2meloader.catalog.SourceIdentity;
import ru.playsoftware.j2meloader.handheld.ManagedGameLibrary;

public final class IphoneLibrary extends ManagedGameLibrary {
    public IphoneLibrary() { super("iphone", IphoneActivity.class); }
    @Override protected String platformName(Properties p) { return "iPhone OS"; }
    @Override protected int platformId(Properties p) { return 24; }
    @Override protected int firstCardId() { return -1160000000; }
    @Override protected String variant(Properties p) { return p.getProperty("version", "IPA"); }
    @Override protected String filename(Properties p) { return "game.ipa"; }

    private static ZipEntry manifest(ZipFile zip) throws IOException {
        for (Enumeration<? extends ZipEntry> it = zip.entries(); it.hasMoreElements();) {
            ZipEntry entry = it.nextElement();
            if (entry.getName().matches("Payload/[^/]+\\.app/Info\\.plist")) return entry;
        }
        throw new IOException("Missing iPhone application bundle in IPA");
    }
    private static String text(NSDictionary dict, String key, String fallback) {
        NSObject value = dict.objectForKey(key);
        return value instanceof NSString && !value.toString().isEmpty() ? value.toString() : fallback;
    }
    @Override protected String inspect(Context context, File game, String name, Properties info) throws IOException {
        try (ZipFile zip = new ZipFile(game)) {
            ZipEntry plist = manifest(zip);
            String directory = plist.getName().substring(0, plist.getName().lastIndexOf('/') + 1);
            NSDictionary metadata;
            if (plist.getSize() > 4 * 1024 * 1024) throw new IOException("Oversized Info.plist");
            try (InputStream stream = zip.getInputStream(plist)) {
                metadata = (NSDictionary) PropertyListParser.parse(stream);
            } catch (Exception e) { throw new IOException("Invalid application Info.plist", e); }
            String executable = text(metadata, "CFBundleExecutable", "");
            if (executable.isEmpty() || executable.contains("/") || zip.getEntry(directory + executable) == null)
                throw new IOException("Missing application executable in IPA");
            String bundle = text(metadata, "CFBundleIdentifier", "");
            if (bundle.isEmpty() || bundle.contains("/") || bundle.contains("\\") || bundle.equals("..") || bundle.equals("."))
                throw new IOException("Invalid application bundle identifier");
            info.setProperty("title", text(metadata, "CFBundleName", text(metadata, "CFBundleDisplayName", executable)));
            info.setProperty("bundle", bundle);
            info.setProperty("format", "IPA");
            info.setProperty("version", text(metadata, "CFBundleShortVersionString", text(metadata, "CFBundleVersion", "IPA")));
            String orientation = text(metadata, "UIInterfaceOrientation", "");
            if (orientation.isEmpty() && metadata.objectForKey("UISupportedInterfaceOrientations") instanceof NSArray) {
                NSArray orientations = (NSArray) metadata.objectForKey("UISupportedInterfaceOrientations");
                if (orientations.count() > 0) orientation = orientations.objectAtIndex(0).toString();
            }
            info.setProperty("orientation", orientation);
            info.setProperty("icon", directory + text(metadata, "CFBundleIconFile", "Icon.png"));
        }
        return SourceIdentity.sha256(game);
    }
    private static native boolean decodeIcon(int png, int rgba);
    @Override protected void extractInstalledArt(File game, File dir) throws IOException {
        Properties meta = load(dir);
        try (ZipFile zip = new ZipFile(game)) {
            ZipEntry icon = zip.getEntry(meta.getProperty("icon"));
            if (icon == null) icon = zip.getEntry(meta.getProperty("icon") + ".png");
            if (icon == null || icon.getSize() <= 0 || icon.getSize() > 16 * 1024 * 1024) return;
            File png = File.createTempFile("ipa-icon", ".png", dir);
            File raw = File.createTempFile("ipa-icon", ".rgba", dir);
            try {
                try (InputStream in = zip.getInputStream(icon)) {
                    Files.copy(in, png.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                System.loadLibrary("touchHLE");
                try (ParcelFileDescriptor src = ParcelFileDescriptor.open(png, ParcelFileDescriptor.MODE_READ_ONLY);
                     ParcelFileDescriptor dst = ParcelFileDescriptor.open(raw, ParcelFileDescriptor.MODE_WRITE_ONLY)) {
                    if (!decodeIcon(src.getFd(), dst.getFd())) return;
                }
                try (DataInputStream in = new DataInputStream(new FileInputStream(raw))) {
                    int width = in.readInt(), height = in.readInt();
                    byte[] bytes = new byte[width * height * 4];
                    in.readFully(bytes);
                    Bitmap art = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    try {
                        art.copyPixelsFromBuffer(ByteBuffer.wrap(bytes));
                        GameArtwork.writeBitmap(new File(dir, "icon.png"), art);
                    } finally { art.recycle(); }
                }
            } finally { Files.deleteIfExists(png.toPath()); Files.deleteIfExists(raw.toPath()); }
        }
    }
    File installed(Context context, String id) throws IOException {
        if (id == null || !id.matches("[a-f0-9]{64}")) throw new IOException("Invalid iPhone game identity");
        return new File(root(context),id);
    }
    Properties metadata(File dir) throws IOException { return load(dir); }
}
