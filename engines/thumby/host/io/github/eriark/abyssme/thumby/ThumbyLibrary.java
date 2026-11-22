/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.thumby;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import ru.playsoftware.j2meloader.catalog.*;
import ru.playsoftware.j2meloader.handheld.ManagedGameLibrary;

public class ThumbyLibrary extends ManagedGameLibrary {
    public static final class Color extends ThumbyLibrary {
        public Color() { super(true); }
    }
    private final boolean color;
    public ThumbyLibrary() { this(false); }
    private ThumbyLibrary(boolean color) {
        super(color ? "thumby_color" : "thumby", color ? ThumbyColorActivity.class : ThumbyGameActivity.class);
        this.color = color;
    }

    @Override protected String inspect(Context c, File file, String name, Properties info) throws IOException {
        if (name.toLowerCase(Locale.ROOT).endsWith(".py")) {
            File packed = new File(file.getParentFile(), "script.zip");
            try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(packed))) {
                ZipEntry entry = new ZipEntry(name.substring(0, name.length() - 3) + ".py");
                entry.setTime(0);
                out.putNextEntry(entry);
                Files.copy(file.toPath(), out);
            }
            Files.move(packed.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        try (ZipFile zip = new ZipFile(file)) {
            ThumbyPackage game = ThumbyPackage.read(zip, color, name.replaceFirst("(?i)\\.(zip|py)$", ""));
            if (color) info.setProperty("directory", game.title);
            info.setProperty("title", game.title);
            // Identity is independent of ZIP compression, wrapper folders and optional arcade artwork.
            java.security.MessageDigest digest;
            try { digest = java.security.MessageDigest.getInstance("SHA-256"); }
            catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
            try (DataOutputStream out = new DataOutputStream(new java.security.DigestOutputStream(new OutputStream() {
                @Override public void write(int ignored) {}
                @Override public void write(byte[] b, int o, int n) {}
            }, digest))) {
                out.writeUTF(game.title);
                for (Map.Entry<String, ZipEntry> part : game.files.entrySet()) {
                    out.writeUTF(part.getKey());
                    out.writeLong(part.getValue().getSize());
                    try (InputStream in = zip.getInputStream(part.getValue())) {
                        byte[] buffer = new byte[8192];
                        int n;
                        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                    }
                }
            }
            if (game.cover != null) try (InputStream in = zip.getInputStream(game.cover)) {
                Files.copy(in, new File(file.getParentFile(), "cover.png").toPath(), StandardCopyOption.REPLACE_EXISTING);
            } else if (game.movie != null) extractMovie(zip, game.movie, file.getParentFile());
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) hex.append(String.format(Locale.ROOT, "%02x", b));
            return hex.toString();
        } catch (IllegalArgumentException invalid) { throw new IOException(invalid.getMessage(), invalid); }
    }
    private static void extractMovie(ZipFile zip, ZipEntry entry, File directory) {
        File movie = new File(directory, "art.webm");
        try (InputStream in = zip.getInputStream(entry); MediaMetadataRetriever media = new MediaMetadataRetriever()) {
            Files.copy(in, movie.toPath());
            media.setDataSource(movie.getPath());
            Bitmap frame = media.getScaledFrameAtTime(500000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 320, 320);
            if (frame != null) try (OutputStream out = new FileOutputStream(new File(directory, "cover.png"))) {
                frame.compress(Bitmap.CompressFormat.PNG, 100, out);
            } finally { if (frame != null) frame.recycle(); }
        } catch (IOException | RuntimeException e) { android.util.Log.d("Thumby", "Arcade preview unavailable", e); }
        finally { movie.delete(); }
    }
    @Override protected String filename(Properties p) { return "game.zip"; }
    @Override protected int platformId(Properties p) { return color ? 31 : 30; }
    @Override protected String platformName(Properties p) { return color ? "Thumby Color" : "Thumby"; }
    @Override protected String variant(Properties p) { return "MicroPython"; }
    @Override protected int firstCardId() { return color ? -1070000000 : -1080000000; }
}
