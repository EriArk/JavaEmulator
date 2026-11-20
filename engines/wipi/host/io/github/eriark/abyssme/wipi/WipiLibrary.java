/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.wipi;

import java.io.*;
import java.util.Properties;
import ru.playsoftware.j2meloader.handheld.ManagedGameLibrary;

public final class WipiLibrary extends ManagedGameLibrary {
    public WipiLibrary() { super("wipi", WipiGameActivity.class); }
    @Override protected String inspect(android.content.Context context, File content, String name, Properties info) throws IOException {
        try (InputStream in = new FileInputStream(content)) {
            WipiPackage game = WipiPackage.read(in);
            info.setProperty("carrier", game.carrier);
            return game.id;
        }
    }
    @Override protected String filename(Properties info) { return "game.zip"; }
    @Override protected int firstCardId() { return Integer.MIN_VALUE + 1; }
    @Override protected int platformId(Properties info) { return 5; }
    @Override protected String platformName(Properties info) { return "SKT".equals(variant(info)) ? "SKVM" : "WIPI"; }
    @Override protected String variant(Properties info) { return info.getProperty("carrier"); }
}
