/* Copyright 2026. Licensed under the Apache License, Version 2.0. */
package ru.playsoftware.j2meloader.config;

/** Small reversible overrides; never replace a game's controls or saves. */
public final class GamePresets {
    public static String[] phoneNames() { return CompatibilityProfileTester.presetNames(); }
    public static void phone(ProfileModel draft, String name) { CompatibilityProfileTester.applyPreset(draft, name); }
    public static String automaticPhone(ProfileModel draft) {
        String suggested = draft.suggestedCompatibilityProfile;
        if (!"high".equals(draft.compatibilityConfidence) && !"medium".equals(draft.compatibilityConfidence))
            return phoneNames()[0];
        for (String name : phoneNames()) if (name.equals(suggested)) return name;
        return phoneNames()[0];
    }
    private GamePresets() { }
}
