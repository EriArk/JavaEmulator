/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.gameking;

import java.io.File;
import java.io.IOException;

public final class HartungGameActivity extends GameKingGameActivity {
    @Override protected String engine() { return "hartung"; }

    @Override protected File prepareCommand(String id) throws IOException {
        File cartridge = new HartungLibrary().installed(this, id);
        if (cartridge.getName().endsWith(".u1")) {
            android.widget.Toast.makeText(this, "Game Master BIOS installed. Open a cartridge to play.",
                    android.widget.Toast.LENGTH_LONG).show();
            finish();
            return null;
        }
        File bios = HartungLibrary.bios(this);
        if (!bios.isFile()) throw new IOException("Import gmaster.zip or the 4 KiB Game Master U1 BIOS, then reopen the cartridge.");
        return writeCommand("gmaster", bios, cartridge);
    }
    @Override protected void sendButton(int id, boolean down) {
        // MAME maps button 1 to RetroPad B. On this machine button 1 is labeled A.
        if (id == 8) id = 0;
        else if (id == 0) id = 8;
        super.sendButton(id, down);
    }

    @Override protected int width() { return 61; }
    @Override protected int displayWidth() { return width(); }
    @Override protected int height() { return 64; }
}
