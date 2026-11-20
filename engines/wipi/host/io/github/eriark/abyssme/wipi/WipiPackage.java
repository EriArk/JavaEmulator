/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.wipi;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class WipiPackage {
    private static final int MIB = 1024 * 1024;
    final byte[] bytes;
    final String id;
    final String carrier;

    private WipiPackage(byte[] bytes, String carrier) {
        this.bytes = bytes;
        this.carrier = carrier;
        try {
            StringBuilder hex = new StringBuilder(64);
            for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) {
                hex.append(String.format(Locale.ROOT, "%02x", b));
            }
            id = hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    static WipiPackage read(InputStream input) throws IOException {
        if (input == null) throw new IOException("Could not open package");
        byte[] buffer = new byte[16384];
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        for (int n; (n = input.read(buffer)) != -1;) {
            if (data.size() + n > 32 * MIB) throw new IOException("Package exceeds 32 MiB");
            data.write(buffer, 0, n);
        }
        byte[] bytes = data.toByteArray();
        Set<String> entries = new HashSet<>();
        long expanded = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                if (entries.size() >= 4096 || !entries.add(entry.getName())) {
                    throw new IOException("Invalid archive directory");
                }
                for (int n; (n = zip.read(buffer)) != -1;) {
                    expanded += n;
                    if (expanded > 64L * MIB) throw new IOException("Expanded archive exceeds 64 MiB");
                }
            }
        }
        // A carrier package pairs a descriptor with a guest JAR. A MIDlet
        // with descriptor-like resources inside the JAR stays on Java ME.
        String carrier = null;
        String folder = null;
        for (String path : entries) {
            if (!path.endsWith(".jar")) continue;
            String parent = path.substring(0, path.lastIndexOf('/') + 1);
            String candidate;
            if (entries.contains(parent + "__adf__")) candidate = "KTF";
            else if (entries.contains(parent + "app_info")) candidate = "LGT";
            else if (entries.contains(path.substring(0, path.length() - 4) + ".msd")) candidate = "SKT";
            else continue;
            if (folder != null && (!folder.equals(parent) || !carrier.equals(candidate))) {
                throw new IOException("Select an archive containing a single game");
            }
            carrier = candidate;
            folder = parent;
        }
        if (carrier == null) throw new IOException("Select a carrier ZIP with a descriptor and game JAR");
        return new WipiPackage(bytes, carrier);
    }
}
