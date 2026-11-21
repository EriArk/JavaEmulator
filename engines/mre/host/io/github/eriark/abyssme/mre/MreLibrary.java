/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.mre;

import java.util.Locale;
import java.util.Properties;
import ru.playsoftware.j2meloader.handheld.CartridgeLibrary;

public final class MreLibrary extends CartridgeLibrary {
    public MreLibrary() { super("mre", MreGameActivity.class); }

    @Override protected boolean cartridge(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".vxp");
    }

    @Override protected void inspectImage(java.io.File file, String name, Properties info) throws java.io.IOException {
        try (java.io.RandomAccessFile in = new java.io.RandomAccessFile(file, "r")) {
            if (!cartridge(name) || in.length() < 20) throw new java.io.IOException("Incomplete MRE package");
            int magic = in.readInt();
            in.seek(in.length() - 12);
            long tags = Integer.toUnsignedLong(Integer.reverseBytes(in.readInt()));
            boolean elf = magic == 0x7f454c46;
            boolean compressed = (magic >>> 24) == 0x78;
            if ((!elf && !compressed) || tags >= in.length() - 12)
                throw new java.io.IOException("Invalid MRE program or tag table");
        }
        info.setProperty("format", variant(info));
    }
    @Override protected String filename(Properties info) { return "game.vxp"; }
    @Override protected String variant(Properties info) { return "MediaTek MRE"; }
    @Override protected int firstCardId() { return -2020000000; }
    @Override protected int platformId(Properties info) { return 17; }
    @Override protected String platformName(Properties info) { return "MediaTek MRE"; }
}
