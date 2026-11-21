/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.gamate;

import android.content.Context;
import io.github.eriark.abyssme.storage.StoragePaths;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import ru.playsoftware.j2meloader.handheld.CartridgeLibrary;

public final class GamateLibrary extends CartridgeLibrary {
    public GamateLibrary() { super("gamate", GamateGameActivity.class); }

    @Override protected boolean cartridge(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".gam") || lower.endsWith(".bin") || lower.endsWith(".rom");
    }

    @Override protected ZipEntry selectImage(ZipFile zip) throws IOException {
        ZipEntry bios = null;
        for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements();) {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory() || !cartridge(entry.getName())) continue;
            if (entry.getSize() != 4096) return super.selectImage(zip);
            if (bios == null || entry.getName().toLowerCase(Locale.ROOT).contains("umc")) bios = entry;
        }
        return bios;
    }

    @Override protected void inspectImage(File file, String name, Properties info) throws IOException {
        if (cartridge(name) && file.length() == 4096) {
            byte[] image = Files.readAllBytes(file.toPath());
            for (int vector = 4090; vector < 4096; vector += 2)
                if ((image[vector+1] & 255) < 0xe0) throw new IOException("Invalid Gamate BIOS vectors");
            info.setProperty("format","BIOS");
            return;
        }
        super.inspectImage(file,name,info);
    }

    static File systemBios(Context c) { return new File(StoragePaths.library(c),"gamate/system/boot.rom"); }

    static void selectBios(Context c, File source) throws IOException {
        Path target = systemBios(c).toPath();
        Files.createDirectories(target.getParent());
        Path tmp = Files.createTempFile(target.getParent(),".bios-", ".tmp");
        try {
            Files.copy(source.toPath(),tmp,StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(tmp); }
    }

    @Override protected void imported(Context c,String id,Properties info) throws IOException {
        if ("BIOS".equals(variant(info))) selectBios(c,installed(c,id));
    }
    @Override protected String filename(Properties info) { return "game.bin"; }
    @Override protected String variant(Properties info) { return info.getProperty("format","Gamate"); }
    @Override protected int firstCardId() { return -2100000000; }
    @Override protected int platformId(Properties info) { return 14; }
    @Override protected String platformName(Properties info) { return "Gamate"; }
}
