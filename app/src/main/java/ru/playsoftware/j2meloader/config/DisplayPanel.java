/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.config;

import ru.playsoftware.j2meloader.R;
import ru.playsoftware.j2meloader.input.GameMenuDialog;

/** Live picture controls. Renderer and engine lifecycle remain host-owned. */
public final class DisplayPanel {
    private final GameMenuDialog menu;
    private final DisplayPreview preview;
    private final boolean gpu, nativePhone;
    private final Runnable back, advanced;
    private ProfileModel draft;

    public DisplayPanel(GameMenuDialog menu, ProfileModel profile, boolean gpu, boolean nativePhone,
                        Runnable render, Runnable back, Runnable advanced) {
        this.menu = menu; this.gpu = gpu; this.nativePhone = nativePhone;
        this.back = back; this.advanced = advanced;
        preview = new DisplayPreview(profile, render); draft = preview.draft();
    }
    public void show() {
        menu.page(menu.text(R.string.game_display));
        menu.setPageCleanup(this::undoQuietly);
        String[] names = menu.labels(nativePhone ? R.array.game_display_native_presets
                : gpu ? R.array.game_display_gpu_presets : R.array.game_display_software_presets);
        int[] swatches = nativePhone ? new int[]{0, 2, 2}
                : gpu ? new int[]{0, 0, 1, 0, 0, 2} : new int[]{0, 0, 1};
        int selected = nativePhone ? Math.min(2, Math.max(0, draft.screenEffect))
                : draft.screenEffect == 0 && !draft.frameInterpolation
                ? (draft.screenScaleType == 0 && !draft.screenFilter && !draft.fsrUpscaling && draft.getRetroShader() == 0
                    ? 0 : ImagePresets.selected(draft, gpu) + 1) : -1;
        menu.presetTiles(names, swatches, selected, i -> {
            if (nativePhone) draft.screenEffect = i;
            else if (i == 0) {
                ImagePresets.apply(draft, ImagePresets.PIXEL_SHARP, gpu);
                draft.screenScaleType = 0; draft.screenScaleRatio = 100;
                draft.frameInterpolation = false; draft.screenEffect = 0;
            } else {
                ImagePresets.apply(draft, i - 1, gpu);
                draft.frameInterpolation = false; draft.screenEffect = 0;
                if (draft.screenScaleType == 0) draft.screenScaleType = 1;
            }
            apply();
        });
        if (!gpu && !nativePhone) menu.message(menu.text(R.string.game_retro_gpu_required));
        if (nativePhone) menu.message(menu.text(R.string.game_native_display_hint));
        else {
            menu.choice(menu.text(R.string.game_picture_size), menu.labels(R.array.game_picture_sizes),
                    Math.max(0, Math.min(3, draft.screenScaleType)), i -> {
                        draft.screenScaleType = i; draft.screenScaleRatio = 100; apply();
                    });
            menu.choice(menu.text(R.string.game_rotation), menu.labels(R.array.game_rotations),
                    Math.floorMod(draft.screenRotation / 90, 4), i -> { draft.screenRotation = i * 90; apply(); });
        }
        menu.previewDecisions(() -> {
            if (preview.keep()) back.run(); else menu.message(menu.text(R.string.game_save_settings_failed));
        }, () -> { undoQuietly(); back.run(); });
        menu.action(menu.text(R.string.game_performance_advanced), R.drawable.ic_baseline_tune_24, advanced);
        menu.setBackAction(back);
    }
    private void apply() {
        boolean failed = false;
        try { preview.apply(draft); }
        catch (RuntimeException error) {
            draft = preview.draft();
            failed = true;
        }
        menu.setPageCleanup(null);
        show();
        if (failed) menu.message(menu.text(R.string.game_preview_failed));
    }
    private void undoQuietly() {
        try { preview.undo(); }
        catch (RuntimeException error) { android.util.Log.w("DisplayPanel", "Could not redraw restored picture", error); }
    }
}
