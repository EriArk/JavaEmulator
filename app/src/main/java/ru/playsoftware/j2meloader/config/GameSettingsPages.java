/* Copyright 2026. Licensed under the Apache License, Version 2.0. */
package ru.playsoftware.j2meloader.config;

import ru.playsoftware.j2meloader.BuildConfig;
import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.input.GameMenuDialog;

/** Shared library/runtime editor. Changes stay in the draft until Try is selected. */
public final class GameSettingsPages {
    public interface Host {
        void preview(ProfileModel draft);
        void mapping(ProfileModel draft, Runnable back);
        void advanced();
        void back();
        default void legacyControls() { advanced(); }
    }
    private final GameMenuDialog menu;
    private final ProfileModel draft;
    private final Host host;
    private final boolean live;
    private final boolean javaGame;
    private final boolean legacyActive;

    public GameSettingsPages(GameMenuDialog menu, ProfileModel current, boolean live, Host host) {
        this(menu, current, live, true, host);
    }
    public GameSettingsPages(GameMenuDialog menu, ProfileModel current, boolean live, boolean javaGame, Host host) {
        this.menu = menu; this.draft = ProfilesManager.copy(current); this.host = host; this.live = live;
        this.javaGame = javaGame;
        if (draft.touchLayout == null) draft.touchLayout = draft.dir != null
                && new java.io.File(draft.dir + Config.MIDLET_KEY_LAYOUT_FILE).exists() ? 2 : 0;
        legacyActive = draft.touchLayout == 2;
        draft.ensureKeyMappingProfiles();
    }

    public void home() {
        menu.page("Game settings");
        menu.action("Display", R.drawable.ic_quick_size, this::display);
        menu.action("Controls", R.drawable.ic_action_keyboard, this::controls);
        if (javaGame) menu.action("Advanced", R.drawable.ic_baseline_tune_24, this::advanced);
        trialAction();
    }

    public void display() {
        menu.page("Display");
        menu.choice("Picture size", new String[]{"Original", "Fit", "Stretch"}, clamp(draft.screenScaleType, 3),
                i -> { draft.screenScaleType = i; draft.screenScaleRatio = 100; });
        menu.toggle("Smoothing", draft.screenFilter, value -> draft.screenFilter = value);
        menu.choice("Image rotation", new String[]{"0\u00b0", "90\u00b0", "180\u00b0", "270\u00b0"},
                Math.floorMod(draft.screenRotation / 90, 4), i -> draft.screenRotation = i * 90);
        if (!BuildConfig.HANDHELD_MODE) menu.choice("Orientation", new String[]{"Auto", "Portrait", "Landscape"},
                Math.max(0, clamp(draft.orientation, 4) - 1), i -> draft.orientation = i + 1);
        if (javaGame) menu.action("Advanced", R.drawable.ic_baseline_tune_24, this::advanced);
        footer();
    }

    public void controls() {
        menu.page("Controls");
        menu.action("Controller mapping", R.drawable.ic_action_keyboard, () -> host.mapping(draft, this::controls));
        if (!BuildConfig.HANDHELD_MODE) {
            int layout = draft.touchLayout == null ? 0 : clamp(draft.touchLayout, javaGame ? 3 : 2);
            menu.choice("Touch layout", javaGame ? new String[]{"Phone", "Gamepad", "Legacy"} : new String[]{"Phone", "Gamepad"}, layout, i -> {
                draft.touchLayout = i; controls();
            });
            if (layout == 2) {
                if (!live || legacyActive) menu.action("Legacy layout editor", R.drawable.ic_action_keyboard, host::legacyControls);
                footer(); return;
            }
            menu.choice("Button size", new String[]{"Standard", "Large", "Extra large"}, clamp(draft.touchSize, 3), i -> draft.touchSize = i);
            menu.opacity(Math.max(80, Math.min(100, draft.touchOpacity)), v -> draft.touchOpacity = v);
            menu.choice("Portrait position", new String[]{"Bottom", "Raised"}, draft.touchPortraitReach > 0 ? 1 : 0,
                    i -> draft.touchPortraitReach = i);
            menu.choice("Landscape position", new String[]{"Bottom", "Raised"}, draft.touchLandscapeReach > 0 ? 1 : 0,
                    i -> draft.touchLandscapeReach = i);
        }
        footer();
    }

    public void advanced() {
        menu.page("Advanced");
        java.util.ArrayList<String> options = new java.util.ArrayList<>();
        if (draft.detectedScreenWidth > 0 && draft.detectedScreenHeight > 0) options.add("Auto");
        java.util.Collections.addAll(options, "128 x 128", "128 x 160", "176 x 208", "176 x 220", "240 x 320", "320 x 240", "360 x 640", "480 x 800", "480 x 854");
        String current = draft.screenWidth + " x " + draft.screenHeight;
        if (!options.contains(current)) options.add(current);
        String[] sizes = options.toArray(new String[0]);
        menu.choice("Phone resolution", sizes, options.indexOf(current), i -> {
            if ("Auto".equals(sizes[i])) {
                draft.screenWidth = draft.detectedScreenWidth; draft.screenHeight = draft.detectedScreenHeight;
            } else {
                String[] size = sizes[i].split(" x ");
                draft.screenWidth = Integer.parseInt(size[0]); draft.screenHeight = Integer.parseInt(size[1]);
            }
        });
        // Runtime engine changes require a restart; the full editor remains library-only.
        if (!live) menu.action("Compatibility settings", R.drawable.ic_baseline_tune_24, host::advanced);
        footer();
    }

    private void footer() {
        trialAction();
        menu.setBackAction(this::home);
    }
    public ProfileModel draft() { return draft; }
    private void trialAction() { menu.action(live ? "Try changes" : "Try in game", android.R.drawable.ic_media_play, () -> host.preview(draft)); }
    private static int clamp(int value, int length) { return Math.max(0, Math.min(length - 1, value)); }
}
