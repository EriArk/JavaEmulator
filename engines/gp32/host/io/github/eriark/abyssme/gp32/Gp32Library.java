/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.gp32;

import android.content.Context;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import io.github.eriark.abyssme.storage.StoragePaths;
import ru.playsoftware.j2meloader.handheld.*;
import ru.playsoftware.j2meloader.config.ProfileModel;
import ru.playsoftware.j2meloader.catalog.SourceIdentity;

public final class Gp32Library extends ManagedGameLibrary {
    public Gp32Library() { super("gp32", Gp32GameActivity.class); }

    @Override protected void imported(Context context, String id, Properties info) throws IOException {
        if (!"bin".equals(variant(info))) return;
        Path system = new File(StoragePaths.library(context), "gp32/system").toPath();
        Files.createDirectories(system);
        Path staging = Files.createTempFile(system, ".bios-", ".tmp");
        try {
            Files.copy(installed(context, id).toPath(), staging, StandardCopyOption.REPLACE_EXISTING);
            Files.move(staging, system.resolve("gp32166m.bin"), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(staging);
        }
    }
    @Override protected String inspect(Context context, File file, String name, Properties info) throws IOException {
        if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            name = Gp32Archive.unpack(file);
            int dot = name.lastIndexOf('.');
            if (dot > 0) info.setProperty("title", name.substring(0, dot));
        }
        String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        byte[] head = new byte[(int)Math.min(file.length(), 1124)];
        try (DataInputStream in = new DataInputStream(new FileInputStream(file))) { in.readFully(head); }
        ByteBuffer b = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
        switch (ext) {
            case "bin":
                if (head.length < 32 || file.length() > 512 * 1024) throw new IOException("Invalid GP32 BIOS size");
                int vector = b.getInt(0);
                if ((vector & 0x0e000000) != 0x0a000000 && (vector & 0x0f7ff000) != 0x051ff000)
                    throw new IOException("Missing GP32 BIOS reset vector");
                break;
            case "fxe":
                if (head.length >= 32 && (b.getInt(0) & 0x0f000000) == 0x0a000000) {
                    long base = Integer.toUnsignedLong(b.getInt(4));
                    long bss = Integer.toUnsignedLong(b.getInt(12));
                    if (base >= 0x0c000000 && base < 0x0e000000 && Integer.toUnsignedLong(b.getInt(8)) >= base
                            && bss >= base && Integer.toUnsignedLong(b.getInt(16)) >= bss) break;
                }
                if (head.length < 1124 || b.getInt(0) != 0x20657866 || b.getInt(8) != 0x454 ||
                        b.getInt(1116) < 32 || b.getInt(1120) <= 0 ||
                        1124L + b.getInt(1116) + b.getInt(1120) > file.length())
                    throw new IOException("Invalid GP32 FXE header");
                int end = 12;
                while (end < 44 && head[end] >= 32 && head[end] < 127) end++;
                if (end > 12 && (end == 44 || head[end] == 0)) {
                    String title = new String(head, 12, end - 12, StandardCharsets.US_ASCII).trim();
                    if (!title.isEmpty()) info.setProperty("title", title);
                }
                break;
            case "fpk":
                if (head.length < 0x90 || b.getInt(0) != 0x474b5047) throw new IOException("Missing GP32 GPKG header");
                break;
            case "smc":
                long length = file.length();
                boolean nand = length >= 528 * 32 && length % (528 * 32) == 0;
                boolean header = length > 1024 && (head[0] == (byte)0xec || head[0] == (byte)0x98)
                        && (length - 1024) % 264 == 0;
                boolean fat = length >= 512 && length % 512 == 0 && head[510] == 0x55 && head[511] == (byte)0xaa;
                if (!nand && !header && !fat) throw new IOException("Unrecognized SmartMedia card geometry");
                break;
            default: throw new IOException("Choose a GP32 .fxe, .fpk, .smc or BIOS .bin file");
        }
        info.setProperty("format", ext);
        return SourceIdentity.sha256(file);
    }
    @Override protected void extractInstalledArt(File game, File dir) throws IOException {
        if (!game.getName().endsWith(".fxe") || game.length()<1124) return;
        try (RandomAccessFile f=new RandomAccessFile(game,"r")) {
            if (f.readInt()!=0x66786520) return;
            f.seek(92);
            byte[] icon=new byte[1024];
            f.readFully(icon);
            int[] levels={0,51,84,102,127,168,204,255}, blue={0,84,168,255};
            int[] pixels=new int[1024];
            for (int x=0;x<32;x++) for (int y=0;y<32;y++) {
                int index=icon[x*32+31-y]&255;
                pixels[y*32+x]=0xff000000 | levels[index>>5]<<16 | levels[(index>>2)&7]<<8 | blue[index&3];
            }
            android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(pixels,32,32,android.graphics.Bitmap.Config.ARGB_8888);
            try { ru.playsoftware.j2meloader.applist.GameArtwork.writeBitmap(new File(dir,"icon.png"),bitmap); }
            finally { bitmap.recycle(); }
        }
    }
    File installed(Context c, String id) throws IOException {
        if (id == null || !id.matches("[0-9a-f]{64}")) throw new IOException("Invalid GP32 game identity");
        File dir = new File(root(c), id);
        return new File(dir, filename(load(dir)));
    }
    @Override protected void configureProfile(ProfileModel profile) { LibretroKeys.defaultMappings(profile); }
    @Override protected String variant(Properties p) { return p.getProperty("format", ""); }
    @Override protected String filename(Properties p) {
        String ext = variant(p);
        return ext.matches("fxe|fpk|smc|bin") ? "game." + ext : "unknown";
    }
    @Override protected int firstCardId() { return -1700000000; }
    @Override protected int platformId(Properties p) { return 9; }
    @Override protected String platformName(Properties p) { return "GamePark GP32"; }
}
