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
        menu.page("Display");
        menu.setPageCleanup(this::undoQuietly);
        String[] names = nativePhone ? new String[]{"Original", "LCD Soft", "LCD Classic"}
                : gpu ? new String[]{"Original", "Pixel sharp", "Smooth", "FSR", "xBRZ", "CRT"}
                : new String[]{"Original", "Pixel sharp", "Smooth"};
        int selected = nativePhone ? Math.min(2, Math.max(0, draft.screenEffect))
                : draft.screenEffect == 0 && !draft.frameInterpolation
                ? (draft.screenScaleType == 0 && !draft.screenFilter && !draft.fsrUpscaling && draft.getRetroShader() == 0
                    ? 0 : ImagePresets.selected(draft, gpu) + 1) : -1;
        menu.presetTiles(names, selected, i -> {
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
        if (!gpu && !nativePhone) menu.message("FSR, xBRZ and CRT require OpenGL ES without a custom shader.");
        if (nativePhone) menu.message("Size and rotation apply on resume in Performance & advanced.");
        else {
            menu.choice("Picture size", new String[]{"Original", "Fit", "Stretch", "Integer"},
                    Math.max(0, Math.min(3, draft.screenScaleType)), i -> {
                        draft.screenScaleType = i; draft.screenScaleRatio = 100; apply();
                    });
            menu.choice("Image rotation", new String[]{"0\u00b0", "90\u00b0", "180\u00b0", "270\u00b0"},
                    Math.floorMod(draft.screenRotation / 90, 4), i -> { draft.screenRotation = i * 90; apply(); });
        }
        menu.previewDecisions(() -> {
            if (preview.keep()) back.run(); else menu.message("Could not save settings. Your previous settings are unchanged.");
        }, () -> { undoQuietly(); back.run(); });
        menu.action("Performance & advanced", R.drawable.ic_baseline_tune_24, advanced);
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
        if (failed) menu.message("Could not preview this picture. Previous settings restored.");
    }
    private void undoQuietly() {
        try { preview.undo(); }
        catch (RuntimeException error) { android.util.Log.w("DisplayPanel", "Could not redraw restored picture", error); }
    }
}
