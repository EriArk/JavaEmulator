/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.gameking;

import android.content.Context;
import io.github.eriark.abyssme.storage.StoragePaths;
import java.io.*;
import java.util.Locale;
import java.util.Properties;
import ru.playsoftware.j2meloader.catalog.HandheldFormat;
import ru.playsoftware.j2meloader.handheld.CartridgeLibrary;

public final class EpochLibrary extends CartridgeLibrary {
    private static final String BIOS = "egpcboot.bin";

    public EpochLibrary() { super("epoch", EpochGameActivity.class); }

    @Override protected boolean cartridge(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".bin") || lower.endsWith(".rom") || lower.endsWith(".egpc");
    }

    @Override protected void inspectImage(File file, String name, Properties info) throws IOException {
        if (!cartridge(name) || file.length() < 1 || file.length() > 32768)
            throw new IOException("Choose a Game Pocket Computer cartridge of up to 32 KiB or the 4 KiB BIOS");
        boolean firmware = name.equalsIgnoreCase(BIOS);
        if (file.length() == 4096) {
            byte[] prefix = new byte[8];
            try (DataInputStream input = new DataInputStream(new FileInputStream(file))) { input.readFully(prefix); }
            firmware |= HandheldFormat.epochBootRom(prefix, file.length());
        }
        if (firmware) {
            if (file.length() != 4096) throw new IOException("The Game Pocket Computer MCU ROM must be 4096 bytes");
            info.setProperty("format", "System BIOS");
            info.setProperty("title", "Game Pocket Computer built-in programs");
        }
    }

    static File bios(Context context) {
        return new File(StoragePaths.library(context), "epoch/system/gamepock/" + BIOS);
    }

    @Override protected void imported(Context context, String id, Properties info) throws IOException {
        if (!BIOS.equals(filename(info))) return;
        GameKingLibrary.copyFirmware(installed(context, id), bios(context));
    }

    @Override protected String filename(Properties info) { return "System BIOS".equals(variant(info)) ? BIOS : "game.bin"; }
    @Override protected String variant(Properties info) { return info.getProperty("format", "Game Pocket Computer"); }
    @Override protected int firstCardId() { return -1090000000; }
    @Override protected int platformId(Properties info) { return 29; }
    @Override protected String platformName(Properties info) { return "Epoch Game Pocket Computer"; }
}
