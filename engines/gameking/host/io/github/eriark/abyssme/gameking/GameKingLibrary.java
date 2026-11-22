/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.gameking;

import android.content.Context;
import io.github.eriark.abyssme.storage.StoragePaths;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import ru.playsoftware.j2meloader.handheld.CartridgeLibrary;

public final class GameKingLibrary extends CartridgeLibrary {
    public GameKingLibrary() { super("gameking", GameKingGameActivity.class); }

    @Override protected boolean cartridge(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".gk") || lower.endsWith(".bin");
    }

    static final String BUILT_IN = "7843fd082889cac046d446160f38755f7c8c4a8e4f9dd9d5104b185f0bc1dec4";

    static final String COLOR_BIOS = "c9c5dc3141c9461717aa147adb4d0dda13c5ca0390b17e79bf7361c91fc4e230";

    static boolean colorCartridge(File file) throws IOException {
        try (RandomAccessFile image = new RandomAccessFile(file, "r")) {
            if (image.length() < 66) return false;
            image.seek(26);
            byte[] manufacturer = new byte[37];
            image.readFully(manufacturer);
            image.seek(64);
            return "GZ Daidaixing Tec.Electronics Co.,ltd".equals(
                    new String(manufacturer, java.nio.charset.StandardCharsets.US_ASCII))
                    && image.readUnsignedShort() == 0x2002;
        }
    }

    @Override protected void inspectImage(File file, String name, Properties info) throws IOException {
        if (!cartridge(name) || file.length() == 0 || file.length() > 1048576)
            throw new IOException("Choose a GameKing GM-218 BIOS or a cartridge of up to 1 MiB");
        String identity = file.length() == 524288 ? ru.playsoftware.j2meloader.catalog.SourceIdentity.sha256(file) : "";
        if (BUILT_IN.equals(identity)) {
            info.setProperty("format", "GM-218 BIOS");
            info.setProperty("title", "GameKing built-in games");
        } else if (COLOR_BIOS.equals(identity)) {
            info.setProperty("format", "GM-220 BIOS");
            info.setProperty("title", "GameKing 3 built-in game");
        } else if (colorCartridge(file)) info.setProperty("format", "GameKing 3");
    }

    static File systemBios(Context context, boolean color) {
        return new File(StoragePaths.library(context), color ? "gameking/system/gamekin3/gm220.bin" : "gameking/system/gameking/gm218.bin");
    }

    static void selectBios(Context c, File source, boolean color) throws IOException {
        copyFirmware(source, systemBios(c, color));
    }

    static void copyFirmware(File source, File destination) throws IOException {
        Path target = destination.toPath();
        Files.createDirectories(target.getParent());
        Path tmp = Files.createTempFile(target.getParent(),".bios-", ".tmp");
        try {
            Files.copy(source.toPath(),tmp,StandardCopyOption.REPLACE_EXISTING);
            Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(tmp); }
    }

    @Override protected void imported(Context c,String id,Properties info) throws IOException {
        if (BUILT_IN.equals(id) || COLOR_BIOS.equals(id)) selectBios(c,installed(c,id), !BUILT_IN.equals(id));
    }
    @Override protected String filename(Properties info) { return "game.bin"; }
    @Override protected String variant(Properties info) { return info.getProperty("format","GameKing"); }
    @Override protected int firstCardId() { return -2080000000; }
    @Override protected int platformId(Properties info) { return 15; }
    @Override protected String platformName(Properties info) { return "GameKing"; }
}
