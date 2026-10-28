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
    private Runnable homeAction;
    public void setHomeAction(Runnable action) { homeAction = action; }

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
        if (homeAction != null) { homeAction.run(); return; }
        if (live) { host.back(); return; }
        menu.page(menu.text(R.string.game_settings_title));
        menu.action(menu.text(R.string.game_display), R.drawable.ic_quick_size, this::display);
        menu.action(menu.text(R.string.game_controls), R.drawable.ic_action_keyboard, this::controls);
        if (javaGame) menu.action(menu.text(R.string.game_advanced), R.drawable.ic_baseline_tune_24, this::advanced);
        trialAction();
    }

    public void display() {
        menu.page(menu.text(R.string.game_display));
        frameRateChoice();
        if (speedAvailable()) speedChoice(menu, draft);
        menu.choice(menu.text(R.string.game_picture_size), menu.labels(R.array.game_picture_sizes), clamp(draft.screenScaleType, 4),
                i -> { draft.screenScaleType = i; draft.screenScaleRatio = 100; });
        if (!enhancementGpuAvailable() || draft.getRetroShader() == 0) {
            menu.toggle(menu.text(R.string.game_smoothing), draft.screenFilter, value -> draft.screenFilter = value);
            screenEffectChoice(menu, draft);
        }
        menu.action(menu.text(R.string.game_enhancements), R.drawable.ic_quick_quality, this::enhancements);
        menu.choice(menu.text(R.string.game_rotation), menu.labels(R.array.game_rotations),
                Math.floorMod(draft.screenRotation / 90, 4), i -> draft.screenRotation = i * 90);
        if (!BuildConfig.HANDHELD_MODE) menu.choice(menu.text(R.string.game_orientation), menu.labels(R.array.game_orientations),
                Math.max(0, clamp(draft.orientation, 4) - 1), i -> draft.orientation = i + 1);
        if (javaGame) menu.action(menu.text(R.string.game_advanced), R.drawable.ic_baseline_tune_24, this::advanced);
        footer();
    }

    /** Native phone renderer capabilities, shared by pre-game and in-game panels. */
    public void nativePhoneDisplay() {
        menu.page(menu.text(R.string.game_display));
        frameRateChoice(menu, draft, false);
        speedChoice(menu, draft);
        menu.choice(menu.text(R.string.game_picture_size), menu.labels(R.array.game_native_picture_sizes), clamp(draft.screenScaleType, 3),
                i -> draft.screenScaleType = i);
        menu.choice(menu.text(R.string.game_rotation), menu.labels(R.array.game_rotations),
                Math.floorMod(draft.screenRotation / 90, 4), i -> draft.screenRotation = i * 90);
        if (!BuildConfig.HANDHELD_MODE) menu.choice(menu.text(R.string.game_orientation), menu.labels(R.array.game_orientations),
                Math.max(0, clamp(draft.orientation, 4) - 1), i -> draft.orientation = i + 1);
        screenEffectChoice(menu, draft);
        footer();
    }

    public void controls() {
        menu.page(menu.text(R.string.game_controls));
        menu.choice(menu.text(R.string.game_control_mode), menu.labels(R.array.game_control_modes), clamp(draft.controlMode, 3),
                i -> draft.controlMode = i);
        menu.choice(menu.text(R.string.game_touch_visibility), menu.labels(R.array.game_touch_visibility_modes), draft.getTouchVisibility(),
                i -> draft.touchVisibility = i);
        menu.action(menu.text(R.string.game_controller_mapping), R.drawable.ic_action_keyboard, () -> host.mapping(draft, this::controls));
        menu.action(menu.text(R.string.game_fast_forward), android.R.drawable.ic_media_ff, () -> fastForward(this::controls));
        if (!BuildConfig.HANDHELD_MODE) {
            if (javaGame) menu.action(menu.getContext().getString(R.string.game_shortcut_appearance), R.drawable.ic_baseline_tune_24,
                    () -> gameShortcuts(this::controls));
            int layout = draft.touchLayout == null ? 0 : clamp(draft.touchLayout, javaGame ? 3 : 2);
            menu.choice(menu.text(R.string.game_touch_layout), javaGame ? menu.labels(R.array.game_touch_layouts) : menu.labels(R.array.game_native_touch_layouts), layout, i -> {
                draft.touchLayout = i; controls();
            });
            menu.toggle(menu.text(R.string.game_feedback), draft.vkFeedback, value -> draft.vkFeedback = value);
            if (layout == 0) menu.choice(menu.text(R.string.game_keypad_placement), menu.labels(R.array.game_keypad_placements),
                    draft.touchKeypadOverlay ? 1 : 0, i -> { draft.touchKeypadOverlay = i == 1; controls(); });
            if (layout == 2) {
                if (!live || legacyActive) menu.action(menu.text(R.string.game_legacy_editor), R.drawable.ic_action_keyboard, host::legacyControls);
                controlsFooter(); return;
            }
            menu.action(menu.text(R.string.game_touch_editor), R.drawable.ic_action_keyboard, () ->
                    menu.editTouchLayout(draft, this::controls));
            menu.choice(menu.text(R.string.game_button_theme), menu.labels(R.array.game_button_themes), clamp(draft.touchTheme, 3), i -> draft.touchTheme = i);
            menu.choice(menu.text(R.string.game_button_size), menu.labels(R.array.game_button_sizes), clamp(draft.touchSize, 3), i -> draft.touchSize = i);
            menu.percentage(menu.getContext().getString(R.string.touch_button_opacity), Math.max(20, Math.min(100, draft.touchOpacity)), 20,
                    v -> draft.touchOpacity = v);
            contrastOpacityNotice();
            menu.choice(menu.text(R.string.game_portrait_position), menu.labels(R.array.game_touch_positions), draft.touchPortraitReach > 0 ? 1 : 0,
                    i -> draft.touchPortraitReach = i);
            menu.choice(menu.text(R.string.game_landscape_position), menu.labels(R.array.game_touch_positions), draft.touchLandscapeReach > 0 ? 1 : 0,
                    i -> draft.touchLandscapeReach = i);
        }
        controlsFooter();
    }

    public void gameShortcuts(Runnable back) {
        menu.page(menu.getContext().getString(R.string.game_shortcut_appearance));
        menu.toggle(menu.getContext().getString(R.string.game_handle_visible), draft.gameHandleVisible, value -> {
            draft.gameHandleVisible = value; gameShortcuts(back);
        });
        if (!draft.gameHandleVisible) menu.message(menu.getContext().getString(R.string.game_handle_back_hint));
        menu.action(menu.getContext().getString(R.string.game_handle_move), R.drawable.ic_baseline_tune_24, () ->
                menu.editGameHandle(draft, () -> gameShortcuts(back)));
        menu.percentage(menu.getContext().getString(R.string.game_shortcut_opacity), Math.max(20, Math.min(100, draft.gameShortcutOpacity)), 20,
                value -> draft.gameShortcutOpacity = value);
        contrastOpacityNotice();
        trialAction(); menu.setBackAction(back);
    }

    private void contrastOpacityNotice() {
        if ("contrast".equals(ru.playsoftware.j2meloader.ui.AppPalette.selected(menu.getContext())))
            menu.message(menu.getContext().getString(R.string.touch_contrast_opacity));
    }

    public void enhancements() {
        menu.page(menu.text(R.string.game_enhancements_experimental));
        boolean gpu = enhancementGpuAvailable();
        String[] presets = menu.labels(gpu ? R.array.game_image_presets : R.array.game_software_image_presets);
        menu.choice(menu.text(R.string.game_image_preset), presets, ImagePresets.selected(draft, gpu), i -> {
            if (i == presets.length - 1) { draft.retroShader = 0; enhancementTuning(); return; }
            int oldShader = draft.getRetroShader();
            ImagePresets.apply(draft, i, gpu);
            if (oldShader != draft.getRetroShader()) enhancements();
        });
        if (gpu && draft.getRetroShader() == 0) {
            menu.toggle(menu.text(R.string.game_interpolation), draft.frameInterpolation, v -> draft.frameInterpolation = v);
        }
        if (draft.getRetroShader() == 0) menu.action(menu.text(R.string.game_fine_tuning), R.drawable.ic_baseline_tune_24, this::enhancementTuning);
        if (!javaGame) {
            menu.choice(menu.text(R.string.game_capture_image), menu.labels(R.array.game_capture_modes),
                    draft.mophunCaptureEnhanced ? 0 : 1, i -> draft.mophunCaptureEnhanced = i == 0);
            int[] budgets = {100, 150, 200, 300};
            int selected = 0;
            for (int i=0;i<budgets.length;i++) if (budgets[i]==draft.mophunCpuBudget) selected=i;
            menu.choice(menu.text(R.string.game_cpu_budget), menu.labels(R.array.game_cpu_budgets),
                    selected, i -> draft.mophunCpuBudget=budgets[i]);
        }
        trialAction(); menu.setBackAction(this::display);
    }

    private boolean enhancementGpuAvailable() {
        return !javaGame || (draft.graphicsMode == 1 && draft.shader == null);
    }

    private void enhancementTuning() {
        menu.page(menu.text(R.string.game_image_fine_tuning));
        menu.toggle(menu.text(R.string.game_smoothing), draft.screenFilter, value -> draft.screenFilter = value);
        if (enhancementGpuAvailable()) {
            menu.toggle(menu.text(R.string.game_fsr), draft.fsrUpscaling, v -> draft.fsrUpscaling = v);
            menu.percentage(menu.text(R.string.game_sharpness), Math.max(0, Math.min(100, draft.fsrSharpness)), 0, v -> draft.fsrSharpness = v);
        } else {
            menu.message(menu.text(R.string.game_gpu_required));
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
        menu.page(menu.text(R.string.game_advanced));
        resolutionChoice();
        if (!live) menu.action(menu.text(R.string.game_compatibility_settings), R.drawable.ic_baseline_tune_24, host::advanced);
        footer();
    }

    public void screenPresets(Runnable back) {
        menu.page(menu.text(R.string.game_screen_presets));
        resolutionChoice();
        trialAction();
        menu.setBackAction(back);
    }

    public static String frameRateLabel(android.content.Context context, int fps) {
        return fps <= 0 ? context.getString(R.string.game_fps_unlimited) : context.getString(R.string.game_fps_value, fps);
    }

    public static void screenEffectChoice(GameMenuDialog menu, ProfileModel draft) {
        menu.choice(menu.text(R.string.game_screen_effect), menu.labels(R.array.game_lcd_effects),
                ru.playsoftware.j2meloader.ui.LcdEffect.mode(draft.screenEffect), i -> draft.screenEffect = i);
        menu.percentage(menu.text(R.string.game_effect_intensity), ru.playsoftware.j2meloader.ui.LcdEffect.intensity(draft.screenEffectIntensity),
                0, i -> draft.screenEffectIntensity = i);
        menu.choice(menu.text(R.string.game_screenshot_image), menu.labels(R.array.game_screenshot_modes),
                draft.screenshotWithEffect ? 1 : 0, i -> draft.screenshotWithEffect = i == 1);
    }

    public void speed(Runnable back) {
        menu.page(menu.text(R.string.game_speed));
        if (speedAvailable()) { speedChoice(menu, draft); trialAction(); }
        else menu.message(menu.text(R.string.game_speed_needs_jar));
        menu.setBackAction(back);
    }

    private boolean speedAvailable() { return !live || !javaGame || javax.microedition.shell.GuestTime.available(); }

    public void fastForward(Runnable back) {
        menu.page(menu.text(R.string.game_fast_forward));
        if (speedAvailable()) {
            int[] rates = {150, 200, 300, 400};
            int selected = 1;
            for (int i = 0; i < rates.length; i++) if (rates[i] == draft.fastForwardSpeed) selected = i;
            menu.choice(menu.text(R.string.game_target_speed), menu.labels(R.array.game_forward_rates),
                    selected, i -> draft.fastForwardSpeed = rates[i]);
            int[] keys = ru.playsoftware.j2meloader.input.FastForwardControl.BUTTONS;
            int binding = 0;
            for (int i = 0; i < keys.length; i++) if (keys[i] == draft.fastForwardButton) binding = i;
            menu.choice(menu.text(R.string.game_controller_button), menu.labels(R.array.game_forward_buttons),
                    binding, i -> draft.fastForwardButton = keys[i]);
            menu.choice(menu.text(R.string.game_controller_mode), menu.labels(R.array.game_forward_modes), draft.fastForwardToggle ? 1 : 0,
                    i -> draft.fastForwardToggle = i == 1);
            menu.toggle(menu.text(R.string.game_screen_shortcut), draft.fastForwardOnScreen, v -> draft.fastForwardOnScreen = v);
            trialAction();
        } else menu.message(menu.text(R.string.game_speed_needs_jar));
        menu.setBackAction(back);
    }

    public static void speedChoice(GameMenuDialog menu, ProfileModel draft) {
        java.util.ArrayList<Integer> values = new java.util.ArrayList<>();
        java.util.Collections.addAll(values, 50, 75, 100, 125, 150, 200);
        int current = draft.getEmulationSpeed();
        if (!values.contains(current)) { values.add(current); java.util.Collections.sort(values); }
        String[] labels = new String[values.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = menu.text(values.get(i) == 100
                ? R.string.game_speed_normal : R.string.game_percent_value, values.get(i));
        menu.choice(menu.text(R.string.game_speed), labels, values.indexOf(current), i -> draft.emulationSpeed = values.get(i));
    }

    public void frameRate(Runnable back) {
        menu.page(menu.text(R.string.game_fps_limit));
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
        for (int i = 0; i < labels.length; i++) labels[i] = frameRateLabel(menu.getContext(), values.get(i));
        menu.choice(menu.text(R.string.game_fps_limit), labels, values.indexOf(current), i -> {
            if (javaGame) draft.fpsLimit = values.get(i);
            else draft.nativeFpsLimit = values.get(i);
        });
    }

    public void phonePresets(Runnable back, java.util.function.Consumer<ProfileModel> restart) {
        menu.page(menu.text(R.string.game_phone_profile));
        ProfileModel original = ProfilesManager.copy(draft);
        java.util.ArrayList<String> names = new java.util.ArrayList<>();
        String automatic = GamePresets.automaticPhone(draft);
        names.add(menu.text(R.string.game_current_settings));
        names.add(menu.text(R.string.game_auto_value, automatic));
        String[] phoneIds = GamePresets.phoneNames();
        java.util.Collections.addAll(names, phoneIds);
        // Opening this page must not replace a custom or imported property set.
        menu.choice(menu.text(R.string.game_profile), names.toArray(new String[0]), 0, i -> {
            if (i == 0) {
                draft.systemProperties = original.systemProperties;
                draft.compatibilityProfile = original.compatibilityProfile;
                return;
            }
            GamePresets.phone(draft, i == 1 ? automatic : phoneIds[i - 2]);
        });
        menu.message(menu.text(R.string.game_phone_restart_warning));
        menu.action(menu.text(R.string.game_restart_try), android.R.drawable.ic_media_play, () -> restart.accept(draft));
        menu.setBackAction(back);
    }

    private void resolutionChoice() {
        int autoWidth = draft.detectedScreenWidth > 0 ? draft.detectedScreenWidth : 240;
        int autoHeight = draft.detectedScreenHeight > 0 ? draft.detectedScreenHeight : 320;
        java.util.ArrayList<int[]> values = new java.util.ArrayList<>();
        values.add(new int[]{autoWidth, autoHeight});
        java.util.Collections.addAll(values, new int[]{128,128}, new int[]{128,160}, new int[]{176,208},
                new int[]{176,220}, new int[]{208,208}, new int[]{240,320}, new int[]{320,240},
                new int[]{352,416}, new int[]{360,640}, new int[]{480,800}, new int[]{480,854});
        int selected = -1;
        for (int i = 1; i < values.size(); i++) {
            int[] size = values.get(i);
            if (size[0] == draft.screenWidth && size[1] == draft.screenHeight) selected = i;
        }
        if (selected < 0) { selected = values.size(); values.add(new int[]{draft.screenWidth, draft.screenHeight}); }
        String[] labels = new String[values.size()];
        for (int i = 0; i < labels.length; i++) {
            labels[i] = menu.text(R.string.game_resolution_value, values.get(i)[0], values.get(i)[1]);
            if (i == 0) labels[i] = menu.text(R.string.game_auto_value, labels[i]);
        }
        menu.choice(menu.text(R.string.game_phone_resolution), labels, selected, i -> {
            draft.screenWidth = values.get(i)[0]; draft.screenHeight = values.get(i)[1];
        });
    }

    private void footer() {
        trialAction();
        menu.setBackAction(this::home);
    }
    public ProfileModel draft() { return draft; }
    private void trialAction() { menu.trialDecision(live ? menu.text(R.string.game_try_changes) : menu.text(R.string.game_try_in_game), () -> host.preview(draft)); }
    private static int clamp(int value, int length) { return Math.max(0, Math.min(length - 1, value)); }
}
