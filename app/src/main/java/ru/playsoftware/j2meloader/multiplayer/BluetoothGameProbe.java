/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/** Bounded API-reference probe, not a multiplayer compatibility certificate. */
final class BluetoothGameProbe {
    static boolean referencesBluetooth(File jar) throws IOException {
        long budget = 32L * 1024 * 1024;
        boolean found = false;
        try (ZipFile zip = new ZipFile(jar)) {
            Enumeration<? extends ZipEntry> entries = zip.entries(); int count = 0;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (++count > 20000) throw new IOException("Too many archive entries");
                if (!entry.getName().endsWith(".class") || entry.isDirectory()) continue;
                if (entry.getSize() < 0 || entry.getSize() > 2 * 1024 * 1024
                        || (budget -= entry.getSize()) < 0) throw new IOException("Game code is too large");
                try (InputStream in = zip.getInputStream(entry); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192]; int n;
                    while ((n = in.read(buffer)) != -1) {
                        if (out.size() + n > entry.getSize()) throw new IOException("Invalid class size");
                        out.write(buffer, 0, n);
                    }
                    found |= classReferencesBluetooth(new DataInputStream(new ByteArrayInputStream(out.toByteArray())));
                }
            }
        }
        return found;
    }
    static boolean classReferencesBluetooth(DataInputStream in) throws IOException {
        if (in.readInt() != 0xcafebabe) throw new IOException("Invalid Java class");
        in.readUnsignedShort(); in.readUnsignedShort();
        int count = in.readUnsignedShort();
        String[] strings = new String[count]; int[] classes = new int[count];
        for (int i = 1; i < count; i++) {
            switch (in.readUnsignedByte()) {
                case 1: strings[i] = in.readUTF(); break;
                case 7: classes[i] = in.readUnsignedShort(); break;
                case 8: case 16: case 19: case 20: in.readUnsignedShort(); break;
                case 3: case 4: case 9: case 10: case 11: case 12: case 17: case 18: in.readInt(); break;
                case 5: case 6: in.readLong(); i++; break;
                case 15: in.readUnsignedByte(); in.readUnsignedShort(); break;
                default: throw new IOException("Unknown Java constant");
            }
        }
        for (int index : classes) {
            if (index >= count) throw new IOException("Invalid class reference");
            String name = strings[index];
            if (name != null && name.startsWith("javax/bluetooth/")) return true;
        }
        return false;
    }
}
