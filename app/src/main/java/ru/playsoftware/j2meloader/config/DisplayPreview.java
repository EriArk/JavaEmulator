/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.config;

/** A nested visual-only trial; never commits another page's pending controls or speed. */
public final class DisplayPreview {
    private final ProfileModel current, entry;
    private final ProfileModel inherited;
    private final Runnable render;
    private boolean dirty;

    public DisplayPreview(ProfileModel current, Runnable render) {
        this.current = current; this.entry = ProfilesManager.copy(current);
        this.inherited = current.previewBaseline; this.render = render;
    }
    public ProfileModel draft() { return ProfilesManager.copy(current); }
    public void apply(ProfileModel draft) {
        ProfilesManager.beginPreview(current);
        copyPicture(draft, current); dirty = true;
        try { render.run(); }
        catch (RuntimeException error) {
            restore();
            try { render.run(); } catch (RuntimeException rollback) { error.addSuppressed(rollback); }
            throw error;
        }
    }
    public boolean keep() {
        if (!dirty) return true;
        ProfileModel saved = ProfilesManager.copy(inherited == null ? entry : inherited);
        copyPicture(current, saved);
        if (!ProfilesManager.saveConfig(saved)) return false;
        copyPicture(current, entry);
        if (inherited != null) copyPicture(current, inherited);
        current.previewBaseline = inherited; dirty = false;
        return true;
    }
    public void undo() { if (dirty) { restore(); render.run(); } }
    private void restore() {
        copyPicture(entry, current); current.previewBaseline = inherited; dirty = false;
    }
    public static void copyPicture(ProfileModel from, ProfileModel to) {
        to.screenScaleType = from.screenScaleType; to.screenScaleRatio = from.screenScaleRatio;
        to.screenRotation = from.screenRotation; to.screenFilter = from.screenFilter;
        to.fsrUpscaling = from.fsrUpscaling; to.fsrSharpness = from.fsrSharpness;
        to.retroShader = from.retroShader; to.frameInterpolation = from.frameInterpolation;
        to.screenEffect = from.screenEffect; to.screenEffectIntensity = from.screenEffectIntensity;
    }
}
