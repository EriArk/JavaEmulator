/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import android.content.*;
import android.os.*;
import android.view.View;
import android.widget.Button;
import java.io.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.BuildConfig;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.input.GameMenuDialog;

/** Presentation entry into the existing room owner. Never grants Ready or admission. */
public final class PlayTogether {
    static final String EXTRA_GAME = "abyssme.selectedNetworkGame";
    private static final ExecutorService FILES = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private PlayTogether() { }
    public static void action(GameMenuDialog menu, String path, Consumer<Intent> launch) {
        if (!BuildConfig.EXPERIMENTAL_MULTIPLAYER) return;
        Button button = menu.action(menu.text(R.string.social_play_together), android.R.drawable.ic_menu_share,
                () -> launch.accept(new Intent(menu.getContext(), LobbyRoomActivity.class).putExtra(EXTRA_GAME, path)));
        button.setVisibility(View.GONE);
        inspect(path, entry -> {
            if (entry != null && menu.isShowing() && button.isAttachedToWindow()) button.setVisibility(View.VISIBLE);
        });
    }
    static void inspect(String path, Consumer<NetworkGameCatalog.Entry> result) {
        FILES.execute(() -> {
            NetworkGameCatalog.Entry entry = null;
            try {
                if (BuildConfig.EXPERIMENTAL_MULTIPLAYER && path != null && path.length() <= 4096) {
                    File root = new File(Config.getAppDir()), dir = new File(path);
                    NetworkGameCatalog.verify(root, new NetworkGameCatalog.Entry(dir, ""));
                    if (BluetoothGameProbe.referencesBluetooth(new File(dir, "res.jar")))
                        entry = new NetworkGameCatalog.Entry(dir, new ru.woesss.j2me.jar.Descriptor(
                                new File(dir, "converted.dex.conf"), false).getName());
                }
            } catch (IOException | RuntimeException ignored) { }
            NetworkGameCatalog.Entry checked = entry;
            MAIN.post(() -> result.accept(checked));
        });
    }
    static void identity(NetworkGameCatalog.Entry entry, Consumer<String> result) {
        FILES.execute(() -> {
            String hash = "";
            try { hash = NetworkGameCatalog.verify(new File(Config.getAppDir()), entry).game.contentHash; }
            catch (IOException | RuntimeException ignored) { }
            String checked = hash; MAIN.post(() -> result.accept(checked));
        });
    }
}
