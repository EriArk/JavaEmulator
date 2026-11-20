/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.megaduck;

import java.util.Locale;
import java.util.Properties;
import ru.playsoftware.j2meloader.handheld.CartridgeLibrary;

public final class MegaDuckLibrary extends CartridgeLibrary {
    public MegaDuckLibrary() { super("megaduck", MegaDuckGameActivity.class); }

    @Override protected boolean cartridge(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".md1") || lower.endsWith(".md2") || lower.endsWith(".duck") || lower.endsWith(".bin");
    }

    @Override protected String filename(Properties info) { return "game.bin"; }
    @Override protected String variant(Properties info) { return "Mega Duck"; }
    @Override protected int firstCardId() { return -1900000000; }
    @Override protected int platformId(Properties info) { return 11; }
    @Override protected String platformName(Properties info) { return "Mega Duck"; }
}
