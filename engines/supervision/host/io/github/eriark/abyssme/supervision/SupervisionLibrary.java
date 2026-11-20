/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.supervision;

import android.content.Context;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import ru.playsoftware.j2meloader.catalog.SourceIdentity;
import ru.playsoftware.j2meloader.config.ProfileModel;
import ru.playsoftware.j2meloader.handheld.LibretroKeys;
import ru.playsoftware.j2meloader.handheld.ManagedGameLibrary;

public final class SupervisionLibrary extends ManagedGameLibrary {
    public SupervisionLibrary() { super("supervision", SupervisionGameActivity.class); }

    private static boolean cartridge(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".sv") || lower.endsWith(".bin");
    }

    @Override protected String inspect(Context c, File file, String name, Properties info) throws IOException {
        if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            try (ZipFile zip = new ZipFile(file)) {
                ZipEntry image = null;
                for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements();) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory() || !cartridge(entry.getName())) continue;
                    if (image != null) throw new IOException("Choose one cartridge from this collection");
                    image = entry;
                }
                if (image == null) throw new IOException("No Supervision cartridge in this ZIP");
                File extracted = new File(file.getParentFile(), "cartridge");
                CRC32 crc = new CRC32();
                try (InputStream input = new CheckedInputStream(zip.getInputStream(image), crc)) {
                    if (Files.copy(input, extracted.toPath()) != image.getSize() || crc.getValue() != image.getCrc())
                        throw new IOException("Damaged cartridge in ZIP");
                }
                Files.move(extracted.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                name = new File(image.getName().replace('\\','/')).getName();
                info.setProperty("title", name.substring(0, name.lastIndexOf('.')));
            }
        }
        if (!cartridge(name) || file.length() == 0 || file.length() % 16384 != 0)
            throw new IOException("Choose a Supervision cartridge made of complete 16 KiB banks");
        info.setProperty("format","SV");
        return SourceIdentity.sha256(file);
    }

    File installed(Context c, String id) throws IOException {
        if (id == null || !id.matches("[0-9a-f]{64}")) throw new IOException("Invalid cartridge identity");
        return new File(new File(root(c), id), "game.sv");
    }
    @Override protected void configureProfile(ProfileModel p) { LibretroKeys.defaultMappings(p); }
    @Override protected String filename(Properties info) { return "game.sv"; }
    @Override protected String variant(Properties info) { return "SV"; }
    @Override protected int firstCardId() { return -1800000000; }
    @Override protected int platformId(Properties info) { return 10; }
    @Override protected String platformName(Properties info) { return "Watara Supervision"; }
}
