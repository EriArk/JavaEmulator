package ru.playsoftware.j2meloader.input;

import ru.playsoftware.j2meloader.R;

/** Shared navigation for Java, Mophun and Symbian; hosts retain engine lifecycle ownership. */
public final class GameMenuPages {
    public interface Host {
        void display();
        void controls();
        void more();
        void exit();
        void restart();
        boolean hasPreview();
        void keep();
        void undo();
    }

    private final GameMenuDialog menu;
    private final Host host;
    private final Runnable screenshot;

    public GameMenuPages(GameMenuDialog menu, Host host, Runnable screenshot) {
        this.menu = menu;
        this.host = host;
        this.screenshot = screenshot;
    }

    public void home() {
        menu.page("Game menu");
        menu.action("Resume", android.R.drawable.ic_media_play, menu::dismiss);
        menu.action("Display", R.drawable.ic_quick_size, host::display);
        menu.action("Controls", R.drawable.ic_action_keyboard, host::controls);
        if (screenshot != null) menu.action("Screenshot", R.drawable.ic_action_screenshot, () -> {
            menu.dismiss();
            screenshot.run();
        });
        if (host.hasPreview()) {
            menu.action("Keep changes", android.R.drawable.ic_menu_save, host::keep);
            menu.action("Undo changes", android.R.drawable.ic_menu_revert, host::undo);
        }
        menu.action("More", android.R.drawable.ic_menu_more, host::more);
        menu.action("Restart game", android.R.drawable.ic_popup_sync, this::confirmRestart);
        menu.action("Exit game", android.R.drawable.ic_menu_close_clear_cancel, this::confirmExit);
    }

    public void confirmRestart() {
        menu.page("Restart game?");
        menu.message("Unsaved progress may be lost. Saved settings will be used."
                + (host.hasPreview() ? " Unconfirmed setting changes will be discarded." : ""));
        menu.action("Cancel", android.R.drawable.ic_media_previous, this::home);
        menu.action("Restart", android.R.drawable.ic_popup_sync, host::restart);
        menu.setBackAction(this::home);
    }

    public void confirmExit() {
        // Keep the same dialog alive so the engine stays paused until the user decides.
        menu.page("Exit game?");
        menu.message("Unsaved progress may be lost.");
        menu.action("Resume", android.R.drawable.ic_media_play, menu::dismiss);
        menu.action("Leave without saving", android.R.drawable.ic_menu_close_clear_cancel, host::exit);
        menu.setBackAction(this::home);
    }
}
