/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.pokitto;

import android.content.Context;
import android.graphics.Bitmap;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import ru.playsoftware.j2meloader.applist.GameArtwork;
import ru.playsoftware.j2meloader.catalog.HandheldFormat;
import ru.playsoftware.j2meloader.catalog.SourceIdentity;
import ru.playsoftware.j2meloader.handheld.CartridgeLibrary;

public final class PokittoLibrary extends CartridgeLibrary {
    public PokittoLibrary() { super("pokitto", PokittoGameActivity.class); }
    @Override protected String platformName(Properties p) { return "Pokitto"; }
    @Override protected String variant(Properties p) { return "Pokitto"; }
    @Override protected int platformId(Properties p) { return 27; }
    @Override protected int firstCardId() { return -1120000000; }
    @Override protected String filename(Properties p) { return p.getProperty("program", "game.bin"); }
    @Override protected boolean cartridge(String name) {
        String s = name.toLowerCase(Locale.ROOT);
        return s.endsWith(".bin") || s.endsWith(".pop");
    }

    @Override protected ZipEntry selectImage(ZipFile zip) throws IOException {
        ZipEntry game = null;
        for (Enumeration<? extends ZipEntry> it = zip.entries(); it.hasMoreElements();) {
            ZipEntry entry = it.nextElement();
            if (!cartridge(entry.getName())) continue;
            if (entry.getName().toLowerCase(Locale.ROOT).endsWith(".bin")) {
                byte[] head = new byte[8];
                try (DataInputStream in = new DataInputStream(zip.getInputStream(entry))) {
                    if (entry.getSize() < 8) continue;
                    in.readFully(head);
                }
                if (!HandheldFormat.pokittoVector(head, entry.getSize())) continue;
            }
            if (game != null) throw new IOException("Choose one Pokitto program from the collection");
            game = entry;
        }
        return game;
    }

    @Override protected String inspect(Context c, File file, String name, Properties props) throws IOException {
        if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            try (ZipFile zip = new ZipFile(file)) {
                ZipEntry game = selectImage(zip);
                if (game != null) {
                    File pkg = file.getParentFile(), assets = new File(pkg, "sd-files");
                    String path = game.getName(), prefix = path.substring(0, path.lastIndexOf('/') + 1);
                    for (Enumeration<? extends ZipEntry> it = zip.entries(); it.hasMoreElements();) {
                        ZipEntry entry = it.nextElement();
                        String member = entry.getName();
                        if (entry.isDirectory() || member.equals(path) || !member.startsWith(prefix)) continue;
                        String rel = member.substring(prefix.length()).replace('\\', '/');
                        if (rel.startsWith("__MACOSX/") || rel.startsWith(".") || rel.contains("/.")) continue;
                        Path root = assets.toPath(), target = root.resolve(rel).normalize();
                        if (!target.startsWith(root) || target.equals(root)) throw new IOException("Invalid SD path");
                        if (rel.toLowerCase(Locale.ROOT).endsWith(".img") && rel.indexOf('/') < 0) {
                            target = new File(pkg, "initial.img").toPath();
                        }
                        Files.createDirectories(target.getParent());
                        try (InputStream in = zip.getInputStream(entry)) { Files.copy(in, target); }
                    }
                    if (assets.isDirectory()) Files.write(new File(pkg, "sd-version").toPath(),
                            SourceIdentity.sha256(file).getBytes(StandardCharsets.US_ASCII));
                }
            }
        }
        return super.inspect(c, file, name, props);
    }

    @Override protected void mergePackage(File stage, File target) throws IOException {
        // Reimporting the complete bundle can add music to a previously installed loose POP.
        File assets = new File(stage, "sd-files");
        if (!assets.isDirectory()) return;
        try (java.util.stream.Stream<Path> paths = Files.walk(assets.toPath())) {
            for (Iterator<Path> it = paths.iterator(); it.hasNext();) {
                Path from = it.next(), to = target.toPath().resolve(stage.toPath().relativize(from));
                if (Files.isDirectory(from)) Files.createDirectories(to);
                else Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Files.move(new File(stage, "sd-version").toPath(), new File(target, "sd-version").toPath(),
                StandardCopyOption.REPLACE_EXISTING);
    }

    @Override protected void inspectImage(File file, String name, Properties p) throws IOException {
        boolean pop = name.toLowerCase(Locale.ROOT).endsWith(".pop");
        p.setProperty("program", pop ? "game.pop" : "game.bin");
        try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
            while (in.length() - in.getFilePointer() >= 8) {
                long offset = in.getFilePointer();
                byte[] header = new byte[8];
                in.readFully(header);
                if (HandheldFormat.pokittoVector(header, in.length() - offset)) return;
                if (!pop) break;
                java.nio.ByteBuffer fields = java.nio.ByteBuffer.wrap(header).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                int tag = fields.getInt();
                long size = Integer.toUnsignedLong(fields.getInt());
                if (size > in.length() - in.getFilePointer()) break;
                if (tag == 7 && size > 0 && size <= 4096) {
                    byte[] text = new byte[(int) size]; in.readFully(text);
                    String title = new String(text, StandardCharsets.UTF_8).split("\\x00", 2)[0].trim();
                    if (!title.isEmpty()) p.setProperty("title", title);
                }
                File dir = file.getParentFile();
                if (tag == 15 && size == 24 * 24 * 2) writeArt(in, new File(dir, "icon.png"), 24, 24);
                if (tag == 18 && size == 220 * 176 * 2 && !new File(dir, "cover.png").exists())
                    writeArt(in, new File(dir, "cover.png"), 220, 176);
                in.seek(offset + 8 + size);
            }
        }
        throw new IOException("Could not find a Pokitto program in this file");
    }

    private static void writeArt(RandomAccessFile in, File dest, int width, int height) throws IOException {
        int[] pixels = new int[width * height];
        for (int i = 0; i < pixels.length; i++) {
            int rgb = Short.toUnsignedInt(Short.reverseBytes(in.readShort()));
            pixels[i] = 0xff000000 | ((rgb >> 11) * 255 / 31 << 16)
                    | ((rgb >> 5 & 63) * 255 / 63 << 8) | (rgb & 31) * 255 / 31;
        }
        Bitmap bitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
        try { GameArtwork.writeBitmap(dest, bitmap); } finally { bitmap.recycle(); }
    }
}
