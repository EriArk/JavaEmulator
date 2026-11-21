/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.handheld;

import android.app.Activity;
import android.content.Context;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import ru.playsoftware.j2meloader.catalog.SourceIdentity;
import ru.playsoftware.j2meloader.config.ProfileModel;

public abstract class CartridgeLibrary extends ManagedGameLibrary {
    protected CartridgeLibrary(String engine, Class<? extends Activity> activity) { super(engine, activity); }
    protected abstract boolean cartridge(String name);
    @Override protected String inspect(Context c, File file, String name, Properties info) throws IOException {
        if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            try (ZipFile zip = new ZipFile(file)) {
                ZipEntry image = selectImage(zip);
                if (image == null) throw new IOException("No " + platformName(info) + " cartridge in this ZIP");
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
        inspectImage(file, name, info);
        return SourceIdentity.sha256(file);
    }

    protected ZipEntry selectImage(ZipFile zip) throws IOException {
        ZipEntry image = null;
        for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements();) {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory() || !cartridge(entry.getName())) continue;
            if (image != null) throw new IOException("Choose one cartridge from this collection");
            image = entry;
        }
        return image;
    }

    protected void inspectImage(File file, String name, Properties info) throws IOException {
        if (!cartridge(name) || file.length() == 0 || file.length() % 16384 != 0)
            throw new IOException("Choose a " + platformName(info) + " cartridge made of complete 16 KiB banks");
        info.setProperty("format", variant(info));
    }

    public final File installed(Context c, String id) throws IOException {
        if (id == null || !id.matches("[0-9a-f]{64}")) throw new IOException("Invalid cartridge identity");
        File directory = new File(root(c), id);
        return new File(directory, filename(load(directory)));
    }
    @Override protected void configureProfile(ProfileModel p) { LibretroKeys.defaultMappings(p); }
}
