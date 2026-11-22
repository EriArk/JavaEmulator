/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.thumby;

import android.content.Context;
import android.net.Uri;
import io.github.eriark.abyssme.storage.StoragePaths;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.*;

/** Recognize the RP2040 boot ROM and flashable UF2 records independently of the picker's filename. */
final class ThumbyFirmware {
    static final String BOOT = "bootrom.bin";
    static final String FLASH = "emulator-firmware.uf2";
    private static final int FLASH_SIZE = 2 * 1024 * 1024;

    static File directory(Context c) { return new File(StoragePaths.library(c), "thumby/system"); }

    static void install(Context c, Uri uri) throws IOException {
        Path dir = Files.createDirectories(directory(c).toPath());
        Path temp = Files.createTempFile(dir, ".firmware-", ".tmp");
        try {
            try (InputStream in = c.getContentResolver().openInputStream(uri);
                 OutputStream out = Files.newOutputStream(temp)) {
                if (in == null) throw new IOException("Cannot open Thumby system file");
                byte[] buffer = new byte[8192];
                long size = 0;
                for (int n; (n = in.read(buffer)) != -1;) {
                    size += n;
                    if (size > 2L * FLASH_SIZE) throw new IOException("Thumby system file is too large");
                    out.write(buffer, 0, n);
                }
            }
            Files.move(temp, dir.resolve(identify(temp)), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }

    static String identify(Path file) throws IOException {
        long length = Files.size(file);
        byte[] block = new byte[512];
        ByteBuffer header = ByteBuffer.wrap(block).order(ByteOrder.LITTLE_ENDIAN);
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            if (length >= block.length) {
                in.readFully(block);
                if (length == 16384 && block[16] == 'M' && block[17] == 'u' && block[18] == 1)
                    return BOOT;
                if (length % block.length == 0) {
                    java.util.BitSet seen = new java.util.BitSet();
                    for (long pos = 0; pos < length; pos += block.length) {
                        if (pos != 0) in.readFully(block);
                        int address = header.getInt(12), payload = header.getInt(16);
                        int count = header.getInt(24), index = header.getInt(20);
                        if (count != 0 && (count != length / block.length || index < 0 || index >= count || seen.get(index)))
                            throw new IOException("Incomplete or repeated Thumby UF2 block");
                        if (count != 0) seen.set(index);
                        if (header.getInt(0) != 0x0a324655 || header.getInt(4) != 0x9e5d5157 ||
                                header.getInt(508) != 0x0ab16f30 || payload <= 0 || payload > 476 ||
                                address < 0x10000000 || address > 0x10000000 + FLASH_SIZE - payload ||
                                (header.getInt(8) & 1) != 0 ||
                                ((header.getInt(8) & 0x2000) != 0 && header.getInt(28) != 0xe48bff56))
                            throw new IOException("Expected the Thumby editor's RP2040 emulator firmware");
                    }
                    return FLASH;
                }
            }
        }
        throw new IOException("Choose the RP2040 boot ROM or the Thumby editor's emulator firmware");
    }
}
