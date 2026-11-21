/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.pocketpc;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

public final class GizmondoLibrary extends PocketPcLibrary {
    public GizmondoLibrary() { super("gizmondo", GizmondoActivity.class); }
    @Override String importSource(File runtime, File input, String ext) throws IOException {
        if (!".zip".equals(ext)) throw new IOException("Choose a ZIP of the Gizmondo SD card.");
        return GizmondoBridge.importCard(runtime.getPath(), input.getPath());
    }
    @Override protected String platformName(Properties info) { return "Gizmondo"; }
    @Override protected String variant(Properties info) { return "SD card"; }
    @Override protected int platformId(Properties info) { return 23; }
    @Override protected int firstCardId() { return -1180000000; }
}
