/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.portfolio;

import android.content.Context;
import android.net.Uri;
import android.util.AtomicFile;
import io.github.eriark.abyssme.storage.StoragePaths;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;
import ru.playsoftware.j2meloader.util.IOUtils;

final class PortfolioFirmware {
    static final String[] NAMES = {"c101782-007.u4", "c101781-007.u3", "c101783-001a-01.u3", "hd61830.bin"};
    private static final int[] SIZES = {131072, 131072, 32768, 1472};
    static File directory(Context c) { return new File(StoragePaths.library(c), "portfolio/system"); }
    static List<String> missing(Context c) {
        List<String> missing = new ArrayList<>();
        for (int i = 0; i < NAMES.length; i++)
            if (new File(directory(c), NAMES[i]).length() != SIZES[i]) missing.add(NAMES[i]);
        return missing;
    }

    static int install(Context c, Uri uri) throws IOException {
        File temp = File.createTempFile("portfolio-rom-", ".tmp", c.getCacheDir());
        try {
            try (InputStream in = c.getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(temp)) {
                if (in == null) throw new IOException("Cannot read firmware");
                IOUtils.copy(in, out);
            }
            Map<Integer, byte[]> parts = new HashMap<>();
            try (ZipFile zip = new ZipFile(temp)) {
                for (Enumeration<? extends ZipEntry> it = zip.entries(); it.hasMoreElements();) {
                    ZipEntry entry = it.nextElement();
                    if (entry.isDirectory() || !possible(entry.getSize())) continue;
                    try (InputStream in = zip.getInputStream(entry)) {
                        identify(IOUtils.toByteArray(in), new File(entry.getName()).getName(), parts);
                    }
                }
            } catch (ZipException raw) {
                if (possible(temp.length())) identify(Files.readAllBytes(temp.toPath()), "", parts);
            }
            if (parts.isEmpty()) throw new IOException("No Portfolio system ROM or LCD font found");
            Files.createDirectories(directory(c).toPath());
            for (Map.Entry<Integer, byte[]> part : parts.entrySet()) {
                AtomicFile file = new AtomicFile(new File(directory(c), NAMES[part.getKey()]));
                FileOutputStream out = file.startWrite();
                try { out.write(part.getValue()); file.finishWrite(out); }
                catch (IOException e) { file.failWrite(out); throw e; }
            }
            return parts.size();
        } finally { temp.delete(); }
    }
    private static boolean possible(long n) { return n == 262144 || n == 131072 || n == 32768 || n == 1472; }
    private static void identify(byte[] bytes, String name, Map<Integer,byte[]> parts) {
        if (bytes.length == 262144) {
            byte[] lo = Arrays.copyOf(bytes, 131072), hi = Arrays.copyOfRange(bytes, 131072, 262144);
            if ((lo[131056] == (byte)0xea) == (hi[131056] == (byte)0xea)) return;
            identify(lo, "", parts); identify(hi, "", parts); return;
        }
        int role = bytes.length == 1472 ? 3 : bytes.length == 32768 ? 2 : bytes[131056] == (byte)0xea ? 1 : 0;
        // A merged ROM set can contain multiple models. Prefer the named original Portfolio parts.
        if (!parts.containsKey(role) || NAMES[role].equalsIgnoreCase(name)) parts.put(role, bytes);
    }
}
