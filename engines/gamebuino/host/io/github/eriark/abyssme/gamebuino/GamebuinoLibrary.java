/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.gamebuino;

import android.content.Context;
import android.graphics.Bitmap;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import ru.playsoftware.j2meloader.applist.GameArtwork;
import ru.playsoftware.j2meloader.handheld.CartridgeLibrary;

public final class GamebuinoLibrary extends CartridgeLibrary {
    public GamebuinoLibrary() { super("gamebuino", GamebuinoGameActivity.class); }
    @Override protected boolean cartridge(String name) { return name.toLowerCase(Locale.ROOT).endsWith(".hex"); }
    @Override protected int platformId(Properties info) { return 26; }
    @Override protected int firstCardId() { return -1130000000; }
    @Override protected String filename(Properties info) { return "game.hex"; }
    @Override protected String variant(Properties info) { return "Gamebuino Classic"; }
    @Override protected String platformName(Properties info) { return variant(info); }

    @Override protected void inspectImage(File file, String name, Properties info) throws IOException {
        if (!cartridge(name)) throw new IOException("Choose a Gamebuino Classic HEX program");
        try (InputStream in = new FileInputStream(file)) { checkHex(in); }
    }

    @Override protected String inspect(Context context, File file, String name, Properties info) throws IOException {
        if (!name.toLowerCase(Locale.ROOT).endsWith(".zip")) return super.inspect(context, file, name, info);
        String title = null;
        try (ZipFile zip = new ZipFile(file)) {
            ZipEntry program = selectImage(zip);
            if (program != null) {
                // A packaged card seeds the first launch. A later import keeps the player's card.
                ZipEntry seed = null;
                for (Enumeration<? extends ZipEntry> it = zip.entries(); it.hasMoreElements();) {
                    ZipEntry entry = it.nextElement();
                    if (entry.isDirectory() || !entry.getName().toLowerCase(Locale.ROOT).endsWith(".img")) continue;
                    if (seed != null) { seed = null; break; }
                    seed = entry;
                }
                if (seed != null && seed.getSize() >= 512) {
                    try (InputStream in = zip.getInputStream(seed)) {
                        java.nio.file.Files.copy(in, new File(file.getParentFile(), "initial-sd.img").toPath());
                    }
                }
                String companion = program.getName().replaceFirst("(?i)\\.hex$", ".inf");
                for (Enumeration<? extends ZipEntry> it = zip.entries(); it.hasMoreElements();) {
                    ZipEntry entry = it.nextElement();
                    if (entry.getName().equalsIgnoreCase(companion)) {
                        try (InputStream in = zip.getInputStream(entry)) { title = readArt(in, file.getParentFile()); }
                        break;
                    }
                }
            }
        }
        String id = super.inspect(context, file, name, info);
        if (title != null && !title.isEmpty()) info.setProperty("title", title);
        return id;
    }

    private static String readArt(InputStream input, File dir) throws IOException {
        DataInputStream in = new DataInputStream(input);
        byte[] header = new byte[81];
        try { in.readFully(header); } catch (EOFException incomplete) { return null; }
        if (!"gbinf".equals(new String(header, 0, 5, StandardCharsets.US_ASCII)) || header[6] != 1) return null;
        writeBitmap(dir, "icon.png", header, 26, 19, 18);
        if (header[80] != 0) {
            byte[] slide = new byte[11 * 32];
            try { in.readFully(slide); writeBitmap(dir, "cover.png", slide, 0, 84, 32); }
            catch (EOFException incomplete) { /* Some packages contain only a complete icon. */ }
        }
        int end = 7;
        while (end < 25 && header[end] != 0) end++;
        return new String(header, 7, end - 7, StandardCharsets.ISO_8859_1).trim();
    }

    private static void writeBitmap(File dir, String name, byte[] bytes, int offset, int w, int h) throws IOException {
        int[] pixels = new int[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int bits = bytes[offset + y * ((w + 7) / 8) + x / 8];
            pixels[y * w + x] = (bits & (128 >> (x % 8))) != 0 ? 0xff404040 : 0xffcedde7;
        }
        Bitmap bitmap = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888);
        try { GameArtwork.writeBitmap(new File(dir, name), bitmap); } finally { bitmap.recycle(); }
    }
}
