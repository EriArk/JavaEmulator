/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.supervision;

import java.util.Locale;
import java.util.Properties;
import ru.playsoftware.j2meloader.handheld.CartridgeLibrary;

public final class SupervisionLibrary extends CartridgeLibrary {
    public SupervisionLibrary() { super("supervision", SupervisionGameActivity.class); }

    @Override protected boolean cartridge(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".sv") || lower.endsWith(".bin");
    }

    @Override protected String filename(Properties info) { return "game.sv"; }
    @Override protected String variant(Properties info) { return "SV"; }
    @Override protected int firstCardId() { return -1800000000; }
    @Override protected int platformId(Properties info) { return 10; }
    @Override protected String platformName(Properties info) { return "Watara Supervision"; }
}
