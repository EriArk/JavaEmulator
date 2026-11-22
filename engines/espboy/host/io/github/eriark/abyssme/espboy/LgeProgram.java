/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.espboy;

import java.io.IOException;
import java.util.Arrays;

/** Parse the SDK file header and expand its literal and backwards-copy tokens. */
final class LgeProgram {
    static final int MEMORY = 65536;
    final byte[] code, icon;
    static boolean hasHeader(byte[] input) {
        return input.length >= 3 && input[0]=='l' && input[1]=='g' && input[2]=='e';
    }
    LgeProgram(byte[] file, boolean container) throws IOException {
        if (!container) {
            if (file.length == 0 || file.length > MEMORY) throw new IOException("Invalid LGE program size");
            code = file; icon = null; return;
        }
        if (file.length < 5 || !hasHeader(file))
            throw new IOException("Missing LGE header");
        int flags = file[3] & 255, start = file[4] & 255;
        boolean hasIcon = (flags & 2) != 0;
        if (start < (hasIcon ? 197 : 5) || start >= file.length) throw new IOException("Invalid LGE header size");
        icon = hasIcon ? Arrays.copyOfRange(file, 5, 197) : null;
        if ((flags & 1) == 0) {
            if (file.length - start > MEMORY) throw new IOException("Program exceeds LGE memory");
            code = Arrays.copyOfRange(file, start, file.length); return;
        }
        byte[] result = new byte[MEMORY];
        int written = 0;
        while (start < file.length) {
            if (start + 2 > file.length) throw new IOException("Truncated LGE token");
            int high = file[start++] & 255, low = file[start++] & 255;
            if (high < 128) {
                int count = high * 256 + low;
                if (count > file.length - start || count > MEMORY - written) throw new IOException("Invalid LGE literal");
                System.arraycopy(file, start, result, written, count);
                start += count; written += count;
            } else {
                int count = (high & 127) >> 1, back = (high & 1) * 256 + low;
                if (back == 0 || back > written || count > MEMORY - written) throw new IOException("Invalid LGE copy");
                for (int i=0; i<count; i++, written++) result[written] = result[written-back];
            }
        }
        if (written == 0) throw new IOException("Empty LGE program");
        code = Arrays.copyOf(result, written);
    }
}
