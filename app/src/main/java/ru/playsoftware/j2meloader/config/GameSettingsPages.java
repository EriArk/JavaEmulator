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
    private Runnable controlsBack;

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
        frameRateChoice();
        if (speedAvailable()) speedChoice(menu, draft);
        menu.choice("Picture size", new String[]{"Original", "Fit", "Stretch", "Integer"}, clamp(draft.screenScaleType, 4),
                i -> { draft.screenScaleType = i; draft.screenScaleRatio = 100; });
        if (!enhancementGpuAvailable() || draft.getRetroShader() == 0) {
            menu.toggle("Smoothing", draft.screenFilter, value -> draft.screenFilter = value);
            screenEffectChoice(menu, draft);
        }
        menu.action("Enhancements", R.drawable.ic_quick_quality, this::enhancements);
        menu.choice("Image rotation", new String[]{"0\u00b0", "90\u00b0", "180\u00b0", "270\u00b0"},
                Math.floorMod(draft.screenRotation / 90, 4), i -> draft.screenRotation = i * 90);
        if (!BuildConfig.HANDHELD_MODE) menu.choice("Orientation", new String[]{"Auto", "Portrait", "Landscape"},
                Math.max(0, clamp(draft.orientation, 4) - 1), i -> draft.orientation = i + 1);
        if (javaGame) menu.action("Advanced", R.drawable.ic_baseline_tune_24, this::advanced);
        footer();
    }

    public void controls() {
        menu.page("Controls");
        menu.choice("Control mode", new String[]{"Auto", "Touch", "Controller"}, clamp(draft.controlMode, 3),
                i -> draft.controlMode = i);
        menu.choice("On-screen buttons", new String[]{"Auto", "Always", "Off"}, draft.getTouchVisibility(),
                i -> draft.touchVisibility = i);
        menu.action("Controller mapping", R.drawable.ic_action_keyboard, () -> host.mapping(draft, this::controls));
        menu.action("Fast-forward", android.R.drawable.ic_media_ff, () -> fastForward(this::controls));
        if (!BuildConfig.HANDHELD_MODE) {
            int layout = draft.touchLayout == null ? 0 : clamp(draft.touchLayout, javaGame ? 3 : 2);
            menu.choice("Touch layout", javaGame ? new String[]{"Phone", "Gamepad", "Legacy"} : new String[]{"Phone", "Gamepad"}, layout, i -> {
                draft.touchLayout = i; controls();
            });
            menu.toggle("Tactile feedback", draft.vkFeedback, value -> draft.vkFeedback = value);
            if (layout == 2) {
                if (!live || legacyActive) menu.action("Legacy layout editor", R.drawable.ic_action_keyboard, host::legacyControls);
                controlsFooter(); return;
            }
            menu.action("Edit touch layout", R.drawable.ic_action_keyboard, () ->
                    menu.editTouchLayout(draft, this::controls));
            menu.choice("Button theme", new String[]{"Follow app", "Dark", "Light"}, clamp(draft.touchTheme, 3), i -> draft.touchTheme = i);
            menu.choice("Button size", new String[]{"Standard", "Large", "Extra large"}, clamp(draft.touchSize, 3), i -> draft.touchSize = i);
            menu.opacity(Math.max(80, Math.min(100, draft.touchOpacity)), v -> draft.touchOpacity = v);
            menu.choice("Portrait position", new String[]{"Bottom", "Raised"}, draft.touchPortraitReach > 0 ? 1 : 0,
                    i -> draft.touchPortraitReach = i);
            menu.choice("Landscape position", new String[]{"Bottom", "Raised"}, draft.touchLandscapeReach > 0 ? 1 : 0,
                    i -> draft.touchLandscapeReach = i);
        }
        controlsFooter();
    }

    public void enhancements() {
        menu.page("Enhancements (experimental)");
        boolean gpu = enhancementGpuAvailable();
        menu.choice("Image preset", ImagePresets.names(gpu), ImagePresets.selected(draft, gpu), i -> {
            if (i == ImagePresets.names(gpu).length - 1) { draft.retroShader = 0; enhancementTuning(); return; }
            int oldShader = draft.getRetroShader();
            ImagePresets.apply(draft, i, gpu);
            if (oldShader != draft.getRetroShader()) enhancements();
        });
        if (gpu && draft.getRetroShader() == 0) {
            menu.toggle("Frame interpolation", draft.frameInterpolation, v -> draft.frameInterpolation = v);
        }
        if (draft.getRetroShader() == 0) menu.action("Fine tuning", R.drawable.ic_baseline_tune_24, this::enhancementTuning);
        if (!javaGame) {
            menu.choice("Capture image", new String[]{"Enhanced", "Original"},
                    draft.mophunCaptureEnhanced ? 0 : 1, i -> draft.mophunCaptureEnhanced = i == 0);
            int[] budgets = {100, 150, 200, 300};
            int selected = 0;
            for (int i=0;i<budgets.length;i++) if (budgets[i]==draft.mophunCpuBudget) selected=i;
            menu.choice("CPU budget", new String[]{"100% (Original)","150%","200%","300%"},
                    selected, i -> draft.mophunCpuBudget=budgets[i]);
        }
        trialAction(); menu.setBackAction(this::display);
    }

    private boolean enhancementGpuAvailable() {
        return !javaGame || (draft.graphicsMode == 1 && draft.shader == null);
    }

    private void enhancementTuning() {
        menu.page("Image fine tuning");
        menu.toggle("Smoothing", draft.screenFilter, value -> draft.screenFilter = value);
        if (enhancementGpuAvailable()) {
            menu.toggle("FSR 1.0 upscaling", draft.fsrUpscaling, v -> draft.fsrUpscaling = v);
            menu.percentage("Sharpness", Math.max(0, Math.min(100, draft.fsrSharpness)), 0, v -> draft.fsrSharpness = v);
        } else {
            menu.message("Requires OpenGL ES without a custom shader.");
        }
        trialAction(); menu.setBackAction(this::enhancements);
    }

    /** Embed controls without exposing another engine's display options. */
    public void controls(Runnable back) { controlsBack = back; controls(); }
    private void controlsFooter() {
        footer();
        if (controlsBack != null) menu.setBackAction(controlsBack);
    }

    public void advanced() {
        menu.page("Advanced");
        resolutionChoice();
        if (!live) menu.action("Compatibility settings", R.drawable.ic_baseline_tune_24, host::advanced);
        footer();
    }

    public void screenPresets(Runnable back) {
        menu.page("Screen presets");
        resolutionChoice();
        trialAction();
        menu.setBackAction(back);
    }

    public static String frameRateLabel(int fps) { return fps <= 0 ? "Unlimited" : fps + " FPS"; }

    public static void screenEffectChoice(GameMenuDialog menu, ProfileModel draft) {
        menu.choice("Screen effect", ru.playsoftware.j2meloader.ui.LcdEffect.NAMES,
                ru.playsoftware.j2meloader.ui.LcdEffect.mode(draft.screenEffect), i -> draft.screenEffect = i);
        menu.percentage("Effect intensity", ru.playsoftware.j2meloader.ui.LcdEffect.intensity(draft.screenEffectIntensity),
                0, i -> draft.screenEffectIntensity = i);
        menu.choice("Screenshot image", new String[]{"Clean", "With LCD effect"},
                draft.screenshotWithEffect ? 1 : 0, i -> draft.screenshotWithEffect = i == 1);
    }

    public void speed(Runnable back) {
        menu.page("Emulation speed");
        if (speedAvailable()) { speedChoice(menu, draft); trialAction(); }
        else menu.message("Original JAR required. Reimport this game to enable speed control; keep its existing saves.");
        menu.setBackAction(back);
    }

    private boolean speedAvailable() { return !live || !javaGame || javax.microedition.shell.GuestTime.available(); }

    public void fastForward(Runnable back) {
        menu.page("Fast-forward");
        if (speedAvailable()) {
            int[] rates = {150, 200, 300, 400};
            int selected = 1;
            for (int i = 0; i < rates.length; i++) if (rates[i] == draft.fastForwardSpeed) selected = i;
            menu.choice("Target speed", new String[]{"150% (1.5x)", "200% (2x)", "300% (3x)", "400% (4x)"},
                    selected, i -> draft.fastForwardSpeed = rates[i]);
            int[] keys = ru.playsoftware.j2meloader.input.FastForwardControl.BUTTONS;
            int binding = 0;
            for (int i = 0; i < keys.length; i++) if (keys[i] == draft.fastForwardButton) binding = i;
            menu.choice("Controller button", ru.playsoftware.j2meloader.input.FastForwardControl.BUTTON_NAMES,
                    binding, i -> draft.fastForwardButton = keys[i]);
            menu.choice("Controller mode", new String[]{"Hold", "Toggle"}, draft.fastForwardToggle ? 1 : 0,
                    i -> draft.fastForwardToggle = i == 1);
            menu.toggle("On-screen shortcut", draft.fastForwardOnScreen, v -> draft.fastForwardOnScreen = v);
            trialAction();
        } else menu.message("Original JAR required. Reimport this game to enable speed control; keep its existing saves.");
        menu.setBackAction(back);
    }

    public static void speedChoice(GameMenuDialog menu, ProfileModel draft) {
        java.util.ArrayList<Integer> values = new java.util.ArrayList<>();
        java.util.Collections.addAll(values, 50, 75, 100, 125, 150, 200);
        int current = draft.getEmulationSpeed();
        if (!values.contains(current)) { values.add(current); java.util.Collections.sort(values); }
        String[] labels = new String[values.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = values.get(i) + "%" + (values.get(i) == 100 ? " (Normal)" : "");
        menu.choice("Emulation speed", labels, values.indexOf(current), i -> draft.emulationSpeed = values.get(i));
    }

    public void frameRate(Runnable back) {
        menu.page("Frame rate limit");
        frameRateChoice();
        trialAction();
        menu.setBackAction(back);
    }

    private void frameRateChoice() {
        frameRateChoice(menu, draft, javaGame);
    }

    public static void frameRateChoice(GameMenuDialog menu, ProfileModel draft, boolean javaGame) {
        java.util.ArrayList<Integer> values = new java.util.ArrayList<>();
        java.util.Collections.addAll(values, 0, 15, 20, 25, 30, 50, 60);
        int current = javaGame ? Math.max(0, draft.fpsLimit) : draft.getNativeFpsLimit();
        if (!values.contains(current)) values.add(current);
        String[] labels = new String[values.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = frameRateLabel(values.get(i));
        menu.choice("Frame rate limit", labels, values.indexOf(current), i -> {
            if (javaGame) draft.fpsLimit = values.get(i);
            else draft.nativeFpsLimit = values.get(i);
        });
    }

    public void phonePresets(Runnable back, java.util.function.Consumer<ProfileModel> restart) {
        menu.page("Phone profile");
        ProfileModel original = ProfilesManager.copy(draft);
        java.util.ArrayList<String> names = new java.util.ArrayList<>();
        String automatic = GamePresets.automaticPhone(draft);
        names.add("Current settings");
        names.add("Auto (" + automatic + ")");
        java.util.Collections.addAll(names, GamePresets.phoneNames());
        // Opening this page must not replace a custom or imported property set.
        menu.choice("Profile", names.toArray(new String[0]), 0, i -> {
            if (i == 0) {
                draft.systemProperties = original.systemProperties;
                draft.compatibilityProfile = original.compatibilityProfile;
                return;
            }
            GamePresets.phone(draft, i == 1 ? automatic : names.get(i));
        });
        menu.message("Restart required. Unsaved game progress may be lost.");
        menu.action("Restart & try", android.R.drawable.ic_media_play, () -> restart.accept(draft));
        menu.setBackAction(back);
    }

    private void resolutionChoice() {
        java.util.ArrayList<String> options = new java.util.ArrayList<>();
        int autoWidth = draft.detectedScreenWidth > 0 ? draft.detectedScreenWidth : 240;
        int autoHeight = draft.detectedScreenHeight > 0 ? draft.detectedScreenHeight : 320;
        options.add("Auto (" + autoWidth + " x " + autoHeight + ")");
        java.util.Collections.addAll(options, "128 x 128", "128 x 160", "176 x 208", "176 x 220", "208 x 208", "240 x 320", "320 x 240", "352 x 416", "360 x 640", "480 x 800", "480 x 854");
        String current = draft.screenWidth + " x " + draft.screenHeight;
        if (!options.contains(current)) options.add(current);
        String[] sizes = options.toArray(new String[0]);
        menu.choice("Phone resolution", sizes, options.indexOf(current), i -> {
            if (i == 0) {
                draft.screenWidth = autoWidth; draft.screenHeight = autoHeight;
            } else {
                String[] size = sizes[i].split(" x ");
                draft.screenWidth = Integer.parseInt(size[0]); draft.screenHeight = Integer.parseInt(size[1]);
            }
        });
    }

    private void footer() {
        trialAction();
        menu.setBackAction(this::home);
    }
    public ProfileModel draft() { return draft; }
    private void trialAction() { menu.action(live ? "Try changes" : "Try in game", android.R.drawable.ic_media_play, () -> host.preview(draft)); }
    private static int clamp(int value, int length) { return Math.max(0, Math.min(length - 1, value)); }
}
