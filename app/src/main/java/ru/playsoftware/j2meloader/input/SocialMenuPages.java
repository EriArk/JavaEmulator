/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.input;

import android.content.Context;
import android.content.Intent;
import java.util.function.Consumer;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.multiplayer.*;

/** Navigation only: existing social activities retain account and room ownership. */
public final class SocialMenuPages {
    private SocialMenuPages() { }
    public static void show(GameMenuDialog menu, Context context, Runnable back, Consumer<Intent> launch) {
        menu.page("Social");
        menu.message("Multiplayer: early experimental, low compatibility. Java Bluetooth games only; both players need the same game version. Some networks cannot connect.");
        // Keep the launcher's room below the MIDlet. Reordering it above the game
        // lets Back destroy the return destination after a game crash.
        if (RoomStatusProvider.active(context))
            menu.action("Return to active room", android.R.drawable.ic_media_play,
                    () -> launch.accept(new Intent(context, LobbyRoomActivity.class)));
        menu.action("Lobbies", android.R.drawable.ic_menu_share,
                () -> launch.accept(new Intent(context, LobbyDirectoryActivity.class)));
        menu.action("Friends", android.R.drawable.ic_menu_myplaces,
                () -> launch.accept(new Intent(context, FluxerChatsActivity.class).putExtra("friends", true)));
        menu.action("Chats", R.drawable.ic_library_messages_square,
                () -> launch.accept(new Intent(context, FluxerChatsActivity.class)));
        menu.action("Fluxer account", android.R.drawable.ic_menu_myplaces,
                () -> launch.accept(new Intent(context, FluxerAccountActivity.class)));
        menu.setBackAction(back);
    }
}
