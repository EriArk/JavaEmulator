// SPDX-License-Identifier: GPL-3.0-or-later
package javax.microedition.m3g;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.zip.Adler32;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/** Validated section framing; no allocation uses unchecked file lengths. */
final class M3gSectionReader {
    // Per-section resource ceiling, not a limit on total scene size.
    private static final int MAX_SECTION = 64 * 1024 * 1024;
    final byte[] data;
    final int length, compression;

    M3gSectionReader(DataInputStream input, long remaining) throws IOException {
        byte[] header = new byte[9];
        if (remaining < 13) throw new IOException("Truncated M3G section");
        input.readFully(header);
        compression = header[0] & 255;
        length = integer(header, 1);
        int decodedLength = integer(header, 5);
        if (compression > 1 || length < 13 || length > remaining || length > MAX_SECTION
                || decodedLength < 0 || decodedLength > MAX_SECTION
                || (compression == 0 && decodedLength != length - 13)) {
            throw new IOException("Invalid M3G section framing");
        }
        byte[] encoded = new byte[length - 13];
        input.readFully(encoded);
        byte[] checksumBytes = new byte[4]; input.readFully(checksumBytes);
        Adler32 checksum = new Adler32(); checksum.update(header); checksum.update(encoded);
        if ((int) checksum.getValue() != integer(checksumBytes, 0)) throw new IOException("M3G checksum mismatch");
        if (compression == 0) { data = encoded; return; }
        data = new byte[decodedLength];
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(encoded);
            int offset = 0;
            while (offset < data.length && !inflater.finished()) {
                int n = inflater.inflate(data, offset, data.length - offset);
                if (n == 0) break;
                offset += n;
            }
            // Consume the zlib trailer and reject output longer than advertised.
            byte[] extra = new byte[1];
            int excess = inflater.finished() ? 0 : inflater.inflate(extra);
            if (offset != data.length || excess != 0 || !inflater.finished() || inflater.getRemaining() != 0) {
                throw new IOException("Incomplete or oversized M3G compressed section");
            }
        } catch (DataFormatException invalid) {
            throw new IOException("Invalid M3G compression", invalid);
        } finally { inflater.end(); }
    }

    static int integer(byte[] bytes, int offset) {
        return (bytes[offset] & 255) | ((bytes[offset+1] & 255) << 8)
                | ((bytes[offset+2] & 255) << 16) | (bytes[offset+3] << 24);
    }
}
