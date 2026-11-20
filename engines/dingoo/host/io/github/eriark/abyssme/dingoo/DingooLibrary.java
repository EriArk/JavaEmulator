/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.dingoo;

import android.content.Context;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;
import java.util.Properties;
import ru.playsoftware.j2meloader.handheld.ManagedGameLibrary;
import ru.playsoftware.j2meloader.catalog.SourceIdentity;

public final class DingooLibrary extends ManagedGameLibrary {
    public DingooLibrary() { super("dingoo", DingooGameActivity.class); }
    @Override protected String inspect(android.content.Context context, File file, String name, Properties info) throws IOException {
        String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        byte[] header = new byte[128];
        try (DataInputStream reader = new DataInputStream(new FileInputStream(file))) { reader.readFully(header); }
        ByteBuffer b = ByteBuffer.wrap(header);
        if (b.getInt(0) != 0x4343444c || b.getInt(32) != 0x494d5054 || b.getInt(64) != 0x45585054
                || b.getInt(96) != 0x52415744) throw new IOException("Missing native CCDL package header");
        b.order(ByteOrder.LITTLE_ENDIAN);
        long origin = Integer.toUnsignedLong(b.getInt(120));
        long entry = Integer.toUnsignedLong(b.getInt(116));
        long size = Integer.toUnsignedLong(b.getInt(108));
        long end = origin + Math.max(size, Integer.toUnsignedLong(b.getInt(124)));
        if (Integer.toUnsignedLong(b.getInt(104)) + size > file.length() || entry < origin || entry >= end || end > 0x100000000L)
            throw new IOException("Invalid native program range");
        boolean dingoo = origin >= 0x80000000L && "app".equals(ext);
        boolean gemei = (origin == 0x10100000L || origin == 0x13800000L)
                && ("cc".equals(ext) || "c2s".equals(ext) || "c3s".equals(ext));
        if (!dingoo && !gemei) throw new IOException("Package extension does not match a supported Dingoo or Gemei program");
        info.setProperty("format",ext);
        return SourceIdentity.sha256(file);
    }
    File installed(Context context, String id) throws IOException {
        if (id == null || !id.matches("[a-f0-9]{64}")) throw new IOException("Invalid game identity");
        File dir = new File(root(context), id);
        return new File(dir, filename(load(dir)));
    }
    @Override protected void configureProfile(ru.playsoftware.j2meloader.config.ProfileModel profile) {
        ru.playsoftware.j2meloader.handheld.LibretroKeys.defaultMappings(profile);
    }
    @Override protected String filename(Properties info) { return "game." + variant(info); }
    @Override protected String variant(Properties info) {
        String ext = info.getProperty("format","");
        return ext.matches("app|cc|c2s|c3s") ? ext : "unknown";
    }
    @Override protected int firstCardId() { return -1500000000; }
    @Override protected int platformId(Properties info) { return "app".equals(variant(info)) ? 6 : 7; }
    @Override protected String platformName(Properties info) { return platformId(info) == 6 ? "Dingoo A320" : "Gemei A330"; }
}
