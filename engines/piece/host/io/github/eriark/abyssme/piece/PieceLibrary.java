/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.piece;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;
import ru.playsoftware.j2meloader.catalog.SourceIdentity;
import ru.playsoftware.j2meloader.handheld.*;

public final class PieceLibrary extends ManagedGameLibrary {
    public PieceLibrary() { super("piece", PieceGameActivity.class); }
    File installed(Context context, String id) throws IOException {
        if (id == null || !id.matches("[a-f0-9]{64}")) throw new IOException("Import a base .pfi flash image first");
        return new File(new File(root(context), id), "image.pfi");
    }
    @Override protected String inspect(Context context, File file, String name, Properties info) throws IOException {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pfi")) {
            new PieceImage(file);
            info.setProperty("format", "PFI");
        } else {
            File base = installed(context, context.getSharedPreferences("piece", 0).getString("base", null));
            if (!base.isFile()) throw new IOException("Import a base .pfi flash image first");
            PieceImage image = new PieceImage(base);
            if (lower.endsWith(".pex")) {
                if (file.length() > image.bytes.length) throw new IOException("Application exceeds flash capacity");
                byte[] program = Files.readAllBytes(file.toPath());
                PieceImage.checkExecutable(program);
                image.add(lower, program);
            } else if (lower.endsWith(".zip")) {
                addDistribution(image, file);
            } else throw new IOException("Choose a .pfi image, .pex application or game ZIP with a .pfs file list");
            Files.write(file.toPath(), image.bytes);
            info.setProperty("format", "PEX");
        }
        return SourceIdentity.sha256(file);
    }
    @Override protected void imported(Context context, String id, Properties info) {
        if ("PFI".equals(variant(info))) context.getSharedPreferences("piece", 0).edit().putString("base",id).apply();
    }
    private static byte[] read(ZipFile zip, ZipEntry entry, int max) throws IOException {
        if (entry == null || entry.isDirectory() || entry.getSize() < 0 || entry.getSize() > max)
            throw new IOException("Missing or oversized P/ECE package file");
        try (DataInputStream in = new DataInputStream(zip.getInputStream(entry))) {
            byte[] bytes = new byte[(int)entry.getSize()];
            in.readFully(bytes);
            if (in.read() != -1) throw new IOException("Incorrect P/ECE ZIP entry size");
            return bytes;
        }
    }
    private static void addDistribution(PieceImage image, File file) throws IOException {
        try (ZipFile zip = new ZipFile(file)) {
            ZipEntry manifest = null;
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.getName().toLowerCase(Locale.ROOT).endsWith(".pfs")) continue;
                if (manifest != null) throw new IOException("Choose a ZIP with a single P/ECE file list");
                manifest = entry;
            }
            if (manifest == null) throw new IOException("Missing .pfs file list in game ZIP");
            String dir = manifest.getName().substring(0, manifest.getName().lastIndexOf('/') + 1);
            String list = new String(read(zip, manifest, 8192), StandardCharsets.US_ASCII);
            boolean executable = false;
            for (String line : list.split("[\\r\\n]+")) {
                String name = line.trim();
                if (name.isEmpty()) continue;
                String lower = name.toLowerCase(Locale.ROOT);
                byte[] bytes = read(zip, zip.getEntry(dir + name), image.bytes.length);
                if (lower.endsWith(".pex")) { PieceImage.checkExecutable(bytes); executable = true; }
                image.add(lower, bytes);
            }
            if (!executable) throw new IOException("No executable listed in P/ECE game ZIP");
        }
    }
    @Override protected void configureProfile(ru.playsoftware.j2meloader.config.ProfileModel profile) {
        LibretroKeys.defaultMappings(profile);
    }
    @Override protected String filename(Properties info) { return "image.pfi"; }
    @Override protected String variant(Properties info) { return info.getProperty("format", "PFI"); }
    @Override protected int firstCardId() { return -1600000000; }
    @Override protected int platformId(Properties info) { return 8; }
    @Override protected String platformName(Properties info) { return "Aquaplus P/ECE"; }
}
