package ru.playsoftware.j2meloader.mophun;

import org.json.JSONObject;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

/** Verified executable profiles, not title guesses or bundled game data. */
final class MophunCompatibility {
    private static final String DEEP_ABYSS_11_T300 =
            "a4a7c9e78f8762432907d8bbcfed71cab50877de29c3a4cc448471d6b90ecc0f";
    private static final String DEEP_ABYSS_14_MULTISCREEN =
            "d0fe59b2c90ea3c41f15c4612f1b62f2599f738dd95c9e3265cc3818b8625370";

    private MophunCompatibility() { }

    static boolean requireExplicitFileCreation(byte[] executable) {
        return isDeepAbyss14(executable);
    }

    static boolean isDeepAbyss14(byte[] executable) {
        try {
            StringBuilder hash = new StringBuilder(64);
            for (byte value : MessageDigest.getInstance("SHA-256").digest(executable)) {
                hash.append(String.format(Locale.ROOT, "%02x", value & 255));
            }
            return DEEP_ABYSS_14_MULTISCREEN.equals(hash.toString());
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    /** Recover only the invalid built-in-pack selection produced by the old probe. */
    static void repairPrototypeLevelSelection(File saves) throws IOException {
        File file = new File(saves, "maxlevel.sav");
        if (!file.isFile() || Files.isSymbolicLink(file.toPath())) return;
        if (file.length() != 0 && file.length() != 22) return;
        byte[] data = Files.readAllBytes(file.toPath());
        if (data.length == 22) {
            if (data[0] < 1 || data[0] > 15 || data[21] != 0) return;
            for (int i = 1; i <= 20; i++) if (data[i] != 0) return;
        } else if (data.length != 0) return;
        File backup = new File(saves, "maxlevel.sav.before-level-zero-fix");
        if (backup.exists()) return;
        if (data.length == 0) {
            Files.move(file.toPath(), backup.toPath());
            return;
        }
        Files.copy(file.toPath(), backup.toPath());
        data[21] = 1;
        AtomicFile atomic = new AtomicFile(file);
        FileOutputStream output = null;
        try {
            output = atomic.startWrite();
            output.write(data);
            atomic.finishWrite(output);
        } catch (IOException error) {
            atomic.failWrite(output);
            throw error;
        }
    }

    static int model(String sha256, int fallback) {
        return DEEP_ABYSS_11_T300.equals(sha256) ? 0 : fallback;
    }

    static int model(JSONObject metadata) {
        int stored = metadata.optInt("model", 2);
        if (metadata.optBoolean("manualModel", false)) return stored;
        return model(metadata.optString("sha256"), stored);
    }
}
