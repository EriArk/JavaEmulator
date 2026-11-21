/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.catalog;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/** Windows CE GUI PE32 with an ARM, Thumb or ARMNT machine identifier. */
public final class PocketPcFormat {
    private PocketPcFormat() {}
    public static boolean armExecutable(InputStream stream) throws IOException {
        DataInputStream in = new DataInputStream(stream);
        try {
            if (in.readUnsignedShort() != 0x4d5a) return false;
            skip(in, 58);
            long header = Integer.toUnsignedLong(Integer.reverseBytes(in.readInt()));
            if (header < 64) return false;
            skip(in, header - 64);
            if (in.readInt() != 0x50450000) return false;
            int machine = Short.toUnsignedInt(Short.reverseBytes(in.readShort()));
            if (machine != 0x1c0 && machine != 0x1c2 && machine != 0x1c4) return false;
            skip(in, 18);
            if (Short.reverseBytes(in.readShort()) != 0x10b) return false;
            skip(in, 66);
            return Short.reverseBytes(in.readShort()) == 9;
        } catch (EOFException incomplete) {
            return false;
        }
    }
    private static void skip(DataInputStream in, long count) throws IOException {
        while (count > 0) {
            long skipped = in.skip(count);
            if (skipped == 0) { in.readByte(); skipped = 1; }
            count -= skipped;
        }
    }
}
