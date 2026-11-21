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

    protected static void checkHex(InputStream in) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, java.nio.charset.StandardCharsets.US_ASCII));
        boolean data = false;
        for (String line; (line = reader.readLine()) != null;) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (!line.startsWith(":") || line.length() < 11 || line.length() > 521 || line.length() % 2 == 0)
                throw new IOException("Invalid Intel HEX record");
            int sum = 0, length = 0, type = 0;
            for (int i = 1; i < line.length(); i += 2) {
                int hi = Character.digit(line.charAt(i), 16), lo = Character.digit(line.charAt(i + 1), 16);
                if (hi < 0 || lo < 0) throw new IOException("Invalid Intel HEX digit");
                int value = hi * 16 + lo;
                if (i == 1) length = value;
                if (i == 7) type = value;
                sum += value;
            }
            if (line.length() != length * 2 + 11 || (sum & 255) != 0) throw new IOException("Damaged Intel HEX record");
            if (type == 0 && length > 0) data = true;
            if (type == 1) break;
        }
        if (!data) throw new IOException("Intel HEX contains no program");
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
