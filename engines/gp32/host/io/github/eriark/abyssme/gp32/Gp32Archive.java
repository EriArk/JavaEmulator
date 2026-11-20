/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.gp32;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Unwrap an image, or preserve a homebrew executable and its files in a GP Manager package. */
final class Gp32Archive {
    private static final long CARD_BYTES = 128L * 1024 * 1024 / 512 * 528 + 1024;

    static String unpack(File archive) throws IOException {
        File staged = new File(archive.getParentFile(), "unwrapped");
        String name;
        try (ZipFile zip = new ZipFile(archive);
             OutputStream out = new BufferedOutputStream(new FileOutputStream(staged))) {
            List<ZipEntry> files = new ArrayList<>();
            List<ZipEntry> images = new ArrayList<>(), firmware = new ArrayList<>();
            long total = 0;
            for (Enumeration<? extends ZipEntry> it = zip.entries(); it.hasMoreElements();) {
                ZipEntry entry = it.nextElement();
                if (entry.isDirectory()) continue;
                if (entry.getSize() < 0 || entry.getSize() > CARD_BYTES - total)
                    throw new IOException("Package exceeds GP32 card capacity");
                total += entry.getSize();
                files.add(entry);
                String path = entry.getName().toLowerCase(Locale.ROOT);
                if (path.endsWith(".smc") || path.endsWith(".fpk") || path.endsWith(".fxe")) images.add(entry);
                if (path.endsWith(".bin")) firmware.add(entry);
            }
            if (images.isEmpty()) images.addAll(firmware);
            if (images.size() != 1) throw new IOException("Select a single GP32 BIOS, executable, package or SmartMedia image");
            ZipEntry main = images.get(0);
            name = new File(main.getName().replace('\\', '/')).getName();
            boolean sidecars = files.size() > 1;
            if (name.toLowerCase(Locale.ROOT).endsWith(".fxe") && sidecars) {
                writePackage(zip, files, main, out);
                name = name.substring(0, name.length() - 4) + ".fpk";
            } else {
                copy(zip, main, out);
            }
        }
        Files.move(staged.toPath(), archive.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return name;
    }

    private static void copy(ZipFile zip, ZipEntry entry, OutputStream out) throws IOException {
        CRC32 crc = new CRC32();
        long remaining = entry.getSize();
        byte[] buffer = new byte[16384];
        try (InputStream in = zip.getInputStream(entry)) {
            for (int n; (n = in.read(buffer)) != -1;) {
                if (n > remaining) throw new IOException("Incorrect ZIP entry length");
                remaining -= n;
                crc.update(buffer, 0, n);
                out.write(buffer, 0, n);
            }
        }
        if (remaining != 0 || crc.getValue() != entry.getCrc()) throw new IOException("Damaged GP32 ZIP entry");
    }

    private static void writePackage(ZipFile zip, List<ZipEntry> files, ZipEntry main, OutputStream out) throws IOException {
        byte[] header = new byte[0x87];
        java.nio.ByteBuffer h = java.nio.ByteBuffer.wrap(header).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        h.putInt(0, 0x474b5047);
        h.putShort(0x79, (short) 4);
        h.position(0x7b);
        h.put(new byte[]{'g','p',':',0});
        h.putInt(0x83, files.size());
        out.write(header);
        String mainPath = main.getName().replace('\\', '/');
        int slash = mainPath.lastIndexOf('/');
        String prefix = mainPath.substring(0, slash + 1);
        // Keep card-root paths even when a distributor wraps the whole tree in a folder.
        java.util.regex.Pattern root = java.util.regex.Pattern.compile("(?i)(?:^|/)(?:GAME|GPMM|GPETC|GPSYS|MP3)/");
        java.util.regex.Matcher rootInMain = root.matcher(mainPath);
        String wrapper = rootInMain.find() ? mainPath.substring(0, rootInMain.start()) : "";
        for (ZipEntry entry : files) {
            String path = entry.getName().replace('\\', '/');
            if (path.startsWith("/") || path.contains(":") || Arrays.asList(path.split("/")).contains(".."))
                throw new IOException("Invalid GP32 package path");
            if (!wrapper.isEmpty() && path.startsWith(wrapper + "/")) path = path.substring(wrapper.length() + 1);
            String upper = path.toUpperCase(Locale.ROOT);
            if (!upper.startsWith("GAME/") && !upper.startsWith("GPMM/") && !upper.startsWith("GPETC/")
                    && !upper.startsWith("GPSYS/") && !upper.startsWith("MP3/")) {
                if (path.startsWith(prefix)) path = path.substring(prefix.length());
                path = "GPMM/" + path;
            }
            slash = path.lastIndexOf('/');
            out.write('1');
            writeField(out, "gp:\\" + path.substring(0, slash).replace('/', '\\'));
            writeField(out, path.substring(slash + 1));
            long size = entry.getSize();
            for (int shift = 0; shift < 32; shift += 8) out.write((int)(size >>> shift));
            copy(zip, entry, out);
        }
        out.write('2');
    }

    private static void writeField(OutputStream out, String text) throws IOException {
        byte[] field = text.getBytes(StandardCharsets.UTF_8);
        if (field.length > 254) throw new IOException("Path does not fit in a GP32 package");
        out.write(field.length + 1);
        out.write(field);
        out.write(0);
    }
}
