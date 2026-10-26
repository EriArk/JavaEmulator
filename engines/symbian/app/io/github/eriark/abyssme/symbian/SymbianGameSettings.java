/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.symbian;

import android.util.SparseIntArray;
import android.view.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import javax.microedition.lcdui.keyboard.KeyMapper;
import ru.playsoftware.j2meloader.config.ProfileModel;
import ru.playsoftware.j2meloader.config.ProfilesManager;
import ru.playsoftware.j2meloader.input.TouchDeck;

/** Shared by library and runtime; loading a draft does not initialize the native engine. */
final class SymbianGameSettings {
    static ProfileModel load(File files, String system, long uid) throws IOException {
        if (system == null || !system.matches("[0-9a-f]{64}|legacy") || uid <= 0 || uid > 0xffffffffL)
            throw new IOException("Invalid game identity");
        File directory = new File(PhoneBackup.profiles(files, system), Long.toString(uid));
        Files.createDirectories(directory.toPath());
        ProfileModel loaded = ProfilesManager.loadConfig(directory);
        ProfileModel profile = loaded == null ? new ProfileModel(directory) : loaded;
        if (loaded == null) {
            SparseIntArray defaults = KeyMapper.getDefaultKeyMap();
            defaults.put(KeyEvent.KEYCODE_BUTTON_X, '5'); defaults.put(KeyEvent.KEYCODE_BUTTON_Y, '0');
            defaults.put(KeyEvent.KEYCODE_BUTTON_B, -7); defaults.put(KeyEvent.KEYCODE_BUTTON_START, -6);
            profile.keyMappingProfiles.add(0, new ProfileModel.KeyMappingProfile("Symbian", defaults));
            profile.activeKeyMappingProfile = 0;
        }
        profile.ensureKeyMappingProfiles();
        if (profile.touchLayout == null || profile.touchLayout != TouchDeck.GAMEPAD)
            profile.touchLayout = TouchDeck.PHONE;
        return profile;
    }

    private SymbianGameSettings() { }
}
