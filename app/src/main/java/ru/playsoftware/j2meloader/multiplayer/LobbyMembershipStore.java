/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/** Intent marker, not a cached claim of membership. Never auto-rejoin after a recorded attempt. */
final class LobbyMembershipStore implements LobbyDirectory.Membership {
    private final File root;
    LobbyMembershipStore(Context context){this(new File(context.getNoBackupFilesDir(),"fluxer-community-attempts"));}
    LobbyMembershipStore(File root){this.root=root;}
    @Override public boolean claim(String account) throws IOException {
        if(!LobbyDirectory.id(account))throw new IOException("Invalid account");
        if(!root.isDirectory()&&!root.mkdirs())throw new IOException("Cannot save community access state");
        File marker=new File(root,account);
        if(!marker.createNewFile())return false;
        try(FileOutputStream output=new FileOutputStream(marker)){output.write(1);output.getFD().sync();}
        return true;
    }
}
