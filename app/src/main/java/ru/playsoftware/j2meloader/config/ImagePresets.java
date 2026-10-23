/* Copyright 2026. Licensed under the Apache License, Version 2.0. */
package ru.playsoftware.j2meloader.config;

/** Derived from existing fields, so imported/custom profiles never need a preset migration. */
public final class ImagePresets {
    public static final int PIXEL_SHARP = 0, SMOOTH = 1, ENHANCED = 2, XBRZ = 3, CRT_EASY = 4, CUSTOM = 5;
    public static final int ENHANCED_SHARPNESS = 50;
    private ImagePresets() { }

    public static String[] names(boolean gpu) {
        return gpu ? new String[]{"Pixel sharp", "Smooth", "Enhanced (FSR)", "xBRZ", "CRT EasyMode", "Custom"}
                : new String[]{"Pixel sharp", "Smooth", "Custom"};
    }

    public static int selected(ProfileModel profile, boolean gpu) {
        if (profile.getRetroShader() != 0) return gpu ? (profile.getRetroShader() == 1 ? XBRZ : CRT_EASY) : 2;
        if (!profile.fsrUpscaling) return profile.screenFilter ? SMOOTH : PIXEL_SHARP;
        if (gpu && !profile.screenFilter && profile.fsrSharpness == ENHANCED_SHARPNESS) return ENHANCED;
        return gpu ? CUSTOM : 2;
    }

    /** Retro presets are standalone. No preset changes CPU/speed, geometry or renderer choice. */
    public static void apply(ProfileModel profile, int preset, boolean gpu) {
        if (preset == PIXEL_SHARP || preset == SMOOTH) {
            profile.retroShader = 0;
            profile.fsrUpscaling = false;
            profile.screenFilter = preset == SMOOTH;
        } else if (gpu && preset == ENHANCED) {
            profile.retroShader = 0;
            profile.fsrUpscaling = true;
            profile.screenFilter = false;
            profile.fsrSharpness = ENHANCED_SHARPNESS;
        } else if (gpu && (preset == XBRZ || preset == CRT_EASY)) {
            profile.retroShader = preset == XBRZ ? 1 : 2;
            profile.fsrUpscaling = false;
            profile.screenFilter = false;
            profile.frameInterpolation = false;
            profile.screenEffect = 0;
        }
    }
}
