/* Copyright 2026. Licensed under the Apache License, Version 2.0. */
package ru.playsoftware.j2meloader.input;

import android.app.Activity;
import ru.playsoftware.j2meloader.R;

/** Presentation only: each engine owns stopping its old session before retrying. */
public final class GameFailureDialog {
    public static GameMenuDialog show(Activity host, String game, String reason,
                                      Runnable retry, Runnable leave) {
        GameMenuDialog menu = new GameMenuDialog(host, game);
        menu.useEdgePanel();
        menu.setCanceledOnTouchOutside(false);
        menu.show();
        menu.confirmationPage(host.getString(R.string.game_failure_title));
        menu.message(host.getString(R.string.game_failure_body));
        boolean[] chosen = {false};
        Runnable exit = () -> {
            if (chosen[0]) return;
            chosen[0] = true;
            menu.dismiss();
            leave.run();
        };
        if (retry != null) menu.action(host.getString(R.string.compatibility_test_retry),
                android.R.drawable.ic_popup_sync, () -> {
                    if (chosen[0]) return;
                    chosen[0] = true;
                    menu.dismiss();
                    retry.run();
                });
        menu.action(host.getString(R.string.game_exit), android.R.drawable.ic_menu_close_clear_cancel, exit);
        menu.setBackAction(exit);
        menu.setOnCancelListener(d -> exit.run());
        if (reason != null && !reason.trim().isEmpty()) menu.message(detail(reason));
        return menu;
    }

    static String detail(String reason) {
        String text = reason.trim();
        return text.length() <= 1200 ? text : text.substring(0, 1200) + "...";
    }

    private GameFailureDialog() { }
}
