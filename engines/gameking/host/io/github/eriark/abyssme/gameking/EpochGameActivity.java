/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.gameking;

import java.io.File;
import java.io.IOException;

public final class EpochGameActivity extends GameKingGameActivity {
    @Override protected String engine() { return "epoch"; }

    @Override protected File prepareCommand(String id) throws IOException {
        File image = new EpochLibrary().installed(this, id);
        File bios = EpochLibrary.bios(this);
        boolean builtIn = image.getName().equals(bios.getName());
        if (builtIn && !bios.isFile()) GameKingLibrary.copyFirmware(image, bios);
        if (!bios.isFile()) throw new IOException("Import gamepock.zip or egpcboot.bin, then open the cartridge again.");
        return writeCommand("gamepock", bios, builtIn ? null : image);
    }

    @Override protected void sendButton(int id, boolean down) {
        // Numbered from top left to bottom right. MAME binds buttons 1–4 to B, A, Y, X.
        switch (id) {
            case 9: id = 0; break;
            case 1: id = 8; break;
            case 8: id = 1; break;
            case 0: id = 9; break;
        }
        super.sendButton(id, down);
    }

    @Override protected int width() { return 75; }
    @Override protected int displayWidth() { return width(); }
    @Override protected int height() { return 64; }
}
