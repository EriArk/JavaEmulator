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
        default void social() { }
        default boolean hasSocial() { return false; }
    }

    private final GameMenuDialog menu;
    private final Host host;
    private final Runnable screenshot;
    private final FastForwardControl fastForward;

    public GameMenuPages(GameMenuDialog menu, Host host, Runnable screenshot) {
        this(menu, host, screenshot, null);
    }
    public GameMenuPages(GameMenuDialog menu, Host host, Runnable screenshot, FastForwardControl fastForward) {
        this.menu = menu;
        this.host = host;
        this.screenshot = screenshot;
        this.fastForward = fastForward;
    }

    public void home() {
        menu.page(menu.text(R.string.game_menu_title));
        menu.action(menu.text(R.string.game_resume), android.R.drawable.ic_media_play, menu::dismiss);
        menu.action(menu.text(R.string.game_display), R.drawable.ic_quick_size, host::display);
        menu.action(menu.text(R.string.game_controls), R.drawable.ic_action_keyboard, host::controls);
        if (host.hasSocial()) menu.action(menu.text(R.string.social_title), R.drawable.ic_library_messages_square, host::social);
        if (host.hasPreview()) {
            menu.action(menu.text(R.string.game_keep_changes), android.R.drawable.ic_menu_save, host::keep);
            menu.action(menu.text(R.string.game_undo_changes), android.R.drawable.ic_menu_revert, host::undo);
        }
        menu.action(menu.text(R.string.game_more), R.drawable.ic_library_ellipsis_vertical, this::more);
        menu.action(menu.text(R.string.game_exit), android.R.drawable.ic_menu_close_clear_cancel, this::confirmExit);
    }

    public void more() {
        menu.page(menu.text(R.string.game_more));
        if (fastForward != null) fastForward.menuAction(menu);
        if (screenshot != null) menu.action(menu.text(R.string.game_screenshot), R.drawable.ic_action_screenshot, () -> {
            menu.dismiss(); screenshot.run();
        });
        menu.action(menu.text(R.string.game_restart), android.R.drawable.ic_popup_sync, this::confirmRestart);
        host.more();
        menu.setBackAction(this::home);
    }

    public void confirmRestart() {
        menu.confirmationPage(menu.text(R.string.game_restart_question));
        menu.message(menu.text(host.hasPreview() ? R.string.game_restart_preview_warning : R.string.game_restart_warning));
        menu.action(menu.text(R.string.game_cancel), android.R.drawable.ic_media_previous, this::more);
        menu.action(menu.text(R.string.game_restart_confirm), android.R.drawable.ic_popup_sync, host::restart);
        menu.setBackAction(this::more);
    }

    public void confirmExit() {
        // Keep the same dialog alive so the engine stays paused until the user decides.
        menu.confirmationPage(menu.text(R.string.game_exit_question));
        menu.message(menu.text(R.string.game_exit_warning));
        menu.action(menu.text(R.string.game_resume), android.R.drawable.ic_media_play, menu::dismiss);
        menu.action(menu.text(R.string.game_leave_without_saving), android.R.drawable.ic_menu_close_clear_cancel, host::exit);
        menu.setBackAction(this::home);
    }
}
