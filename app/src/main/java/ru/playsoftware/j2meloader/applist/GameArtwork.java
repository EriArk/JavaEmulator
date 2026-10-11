/* Copyright 2026. Licensed under the Apache License, Version 2.0. */
package ru.playsoftware.j2meloader.applist;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.AtomicFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import ru.playsoftware.j2meloader.config.Config;
import ru.woesss.j2me.jar.Descriptor;

/** Conservative artwork selection. Size alone is never evidence of an icon or cover. */
public final class GameArtwork {
    public static final String USER_ICON = "user-icon.png";
    public static final String USER_COVER = "user-cover.png";
    public static final String AUTO_MARKER = "artwork-v1";
    public static final String[] FILES = {"icon.png", "cover.png", USER_ICON, USER_COVER, AUTO_MARKER};
    private static final int MAX_BYTES = 16 * 1024 * 1024;
    private static final long MAX_PIXELS = 8 * 1024 * 1024;

    private GameArtwork() { }

    public static final class Selection {
        public final String icon, cover;
        Selection(String icon, String cover) { this.icon = icon; this.cover = cover; }
    }

    private static final class Candidate {
        final String name;
        final int width, height;
        Candidate(String name, int width, int height) {
            this.name = name; this.width = width; this.height = height;
        }
    }

    public static Selection select(File jar, String preferredIcon) throws IOException {
        String preferred = preferredIcon == null ? "" : preferredIcon.trim().replaceFirst("^/+", "");
        try (ZipFile zip = new ZipFile(jar)) {
            List<Candidate> images = new ArrayList<>();
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName().toLowerCase(Locale.ROOT);
                if (entry.isDirectory() || entry.getSize() < 0 || entry.getSize() > MAX_BYTES
                        || !(entry.getName().equals(preferred) || name.endsWith(".png")
                        || name.endsWith(".jpg") || name.endsWith(".jpeg"))) continue;
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                try (InputStream in = zip.getInputStream(entry)) { BitmapFactory.decodeStream(in, null, bounds); }
                if (bounds.outWidth > 0 && bounds.outHeight > 0
                        && (long) bounds.outWidth * bounds.outHeight <= MAX_PIXELS) {
                    images.add(new Candidate(entry.getName(), bounds.outWidth, bounds.outHeight));
                }
            }
            String icon = null;
            for (Candidate image : images) {
                if (image.name.equals(preferred) && valid(zip, image.name)) { icon = image.name; break; }
            }
            if (icon == null) icon = choose(zip, images, false, null);
            return new Selection(icon, choose(zip, images, true, icon));
        }
    }

    private static String choose(ZipFile zip, List<Candidate> images, boolean cover,
                                 String icon) throws IOException {
        String best = null;
        int bestScore = 0, runnerUp = 0;
        for (Candidate image : images) {
            if (image.name.equals(icon)) continue;
            int score = score(image.name, image.width, image.height, cover);
            if (score < 80 || !valid(zip, image.name)) continue;
            if (score > bestScore) { runnerUp = bestScore; bestScore = score; best = image.name; }
            else runnerUp = Math.max(runnerUp, score);
        }
        // Ambiguous alternatives should not depend on ZIP entry order.
        return bestScore >= 80 && bestScore - runnerUp >= 15 ? best : null;
    }

    static int score(String path, int width, int height, boolean cover) {
        String name = path.substring(path.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        String lower = path.toLowerCase(Locale.ROOT);
        float ratio = width / (float) height;
        if (lower.matches(".*(font|sprite|atlas|sheet|texture|tile|button|digit|cursor|arrow|hud|congrat|game.?over|publisher|copyright|credits).*")) return 0;
        if (!cover) {
            if (width < 8 || height < 8 || width > 512 || height > 512 || ratio < .75f || ratio > 1.34f) return 0;
            return name.matches("(icon|midlet.?icon|app.?icon)([_.-]?[0-9]+)?\\.(png|jpe?g)") ? 100 : 0;
        }
        if (width < 96 || height < 96 || ratio < .45f || ratio > 2.6f) return 0;
        if (name.matches(".*(cover|title).*")) return 120;
        if (lower.matches(".*menu[/_-](background|bg)\\.(png|jpe?g)")) return 100;
        return 0;
    }

    private static boolean valid(ZipFile zip, String name) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 8;
        try (InputStream in = zip.getInputStream(zip.getEntry(name))) {
            Bitmap bitmap = BitmapFactory.decodeStream(in, null, options);
            if (bitmap == null) return false;
            bitmap.recycle();
            return true;
        }
    }

    /** Refresh only generated files. Explicit artwork always remains separate and wins. */
    public static void refresh(File directory, String preferredIcon) throws IOException {
        File jar = new File(directory, "res.jar");
        Selection selected = select(jar, preferredIcon);
        try (ZipFile zip = new ZipFile(jar)) {
            extract(zip, selected.icon, new File(directory, "icon.png"));
            extract(zip, selected.cover, new File(directory, "cover.png"));
        }
        write(new File(directory, AUTO_MARKER), new byte[]{1});
    }

    public static void refresh(File directory) throws IOException {
        Descriptor descriptor = new Descriptor(new File(directory, Config.MIDLET_MANIFEST_FILE), false);
        refresh(directory, descriptor.getIcon());
    }

    private static void extract(ZipFile zip, String entry, File target) throws IOException {
        if (entry == null) { Files.deleteIfExists(target.toPath()); return; }
        try (InputStream in = zip.getInputStream(zip.getEntry(entry))) { write(target, read(in)); }
    }

    private static byte[] read(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = input.read(buffer)) != -1) {
            if (output.size() + n > MAX_BYTES) throw new IOException("Image is too large");
            output.write(buffer, 0, n);
        }
        return output.toByteArray();
    }

    public static void saveUserImage(File directory, boolean icon, InputStream input) throws IOException {
        byte[] bytes = read(input);
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0
                || (long) bounds.outWidth * bounds.outHeight > MAX_PIXELS) throw new IOException("Invalid image");
        Bitmap decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        if (decoded == null) throw new IOException("Invalid image");
        decoded.recycle();
        write(new File(directory, icon ? USER_ICON : USER_COVER), bytes);
    }

    private static void write(File file, byte[] bytes) throws IOException {
        AtomicFile atomic = new AtomicFile(file);
        FileOutputStream out = atomic.startWrite();
        try { out.write(bytes); atomic.finishWrite(out); }
        catch (IOException e) { atomic.failWrite(out); throw e; }
    }

    public static void applyPaths(AppItem item, File directory) {
        String icon = new File(directory, USER_ICON).isFile() ? USER_ICON
                : new File(directory, "icon.png").isFile() ? "icon.png" : null;
        String cover = new File(directory, USER_COVER).isFile() ? USER_COVER
                : new File(directory, "cover.png").isFile() ? "cover.png" : null;
        if (icon == null) item.setImagePath(null); else item.setImagePathExt(icon);
        if (cover == null) item.setCoverPath(null); else item.setCoverPathExt(cover);
    }

    public static void preserveUserArt(File source, File destination) throws IOException {
        for (String name : new String[]{USER_ICON, USER_COVER}) {
            File file = new File(source, name);
            if (file.isFile()) Files.copy(file.toPath(), new File(destination, name).toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        // Older versions stored both picked and generated covers under the same name.
        File legacy = new File(source, "cover.png");
        if (!new File(source, AUTO_MARKER).exists() && legacy.isFile()
                && !new File(destination, USER_COVER).exists()) {
            Files.copy(legacy.toPath(), new File(destination, USER_COVER).toPath());
        }
    }
}
