/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.piece;

import com.orangesignal.jlha.CRC16;
import com.orangesignal.jlha.LhaFile;
import com.orangesignal.jlha.LhaHeader;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** Build the same PFI as upstream mkpfi. The self-extractor is read only as an archive. */
final class PieceFirmware {
    private static final int SMALL = 512 * 1024, LARGE = 2 * 1024 * 1024;
    static void prepare(File file, boolean updater) throws IOException {
        byte[] kernel;
        if (updater) {
            LhaFile archive = new LhaFile(file);
            try {
                LhaHeader best = null;
                for (LhaHeader h : archive.getEntries()) {
                    String path = h.getPath().replace('\\','/').toLowerCase(Locale.ROOT);
                    if (path.equals("all.bin") || path.endsWith("/all.bin")) {
                        if (best == null || path.contains("/2mb/")) best = h;
                    }
                }
                if (best == null || best.getOriginalSize() < 32 || best.getOriginalSize() > LARGE - 8192)
                    throw new IOException("No P/ECE system image in this updater");
                kernel = new byte[(int)best.getOriginalSize()];
                try (DataInputStream in = new DataInputStream(archive.getInputStream(best))) {
                    in.readFully(kernel);
                }
                CRC16 crc = new CRC16(); crc.update(kernel,0,kernel.length);
                if (best.getCrc() >= 0 && best.getCrc() != crc.getValue()) throw new IOException("Damaged P/ECE updater");
            } finally { archive.close(); }
        } else {
            if (file.length() > LARGE - 8192) throw new IOException("Invalid P/ECE kernel size");
            kernel = Files.readAllBytes(file.toPath());
        }
        int fatAfterSmall = 0x26000 + 32 + 96 * 32 + 88 * 2;
        if (kernel.length < fatAfterSmall + 2 ||
                !"Piece Kernel Program".equals(new String(kernel,8,20,StandardCharsets.US_ASCII)))
            throw new IOException("Missing P/ECE kernel signature");
        int capacity = kernel.length <= SMALL - 8192 && kernel[fatAfterSmall] == (byte)0xdd
                && kernel[fatAfterSmall+1] == (byte)0xdd ? SMALL : LARGE;
        byte[] image = new byte[48 + capacity];
        Arrays.fill(image,(byte)0xff);
        ByteBuffer h = ByteBuffer.wrap(image).order(ByteOrder.LITTLE_ENDIAN);
        h.putInt(0x50464931).putInt(48).putShort((short)32).putShort((short)0x100)
                .putShort((short)0x11d).putShort((short)0xaa7).putInt(24000000)
                .putShort((short)0xce4).putShort((short)0).putInt(0x100000).putInt(0x140000)
                .putInt(0xc28000).putInt(0xc00000+capacity);
        h.putInt(48,0xc02004);
        System.arraycopy(kernel,0,image,48+8192,kernel.length);
        Files.write(file.toPath(),image);
    }
}
