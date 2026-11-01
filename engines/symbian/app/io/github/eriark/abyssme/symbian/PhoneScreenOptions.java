/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.symbian;

import android.util.AtomicFile;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Locale;
import org.json.JSONObject;

/** Cross-process, disposable capabilities; never part of the phone or game settings. */
final class PhoneScreenOptions {
    static final int UNKNOWN = -1;
    private static final int VERSION = 1;

    static int read(File files, String system) {
        if ("legacy".equals(system)) return 1;
        try {
            SystemFileCatalog catalog = new SystemFileCatalog(new File(files, "symbian-systems"));
            SystemFileCatalog.Entry phone = catalog.read(system);
            if (!SymbianGameSettings.supportsGuestScreen(phone.os)) return 1;
            AtomicFile cache = cache(files, system);
            if (cache.getBaseFile().length() > 4096) return UNKNOWN;
            JSONObject saved = new JSONObject(new String(cache.readFully(), StandardCharsets.UTF_8));
            int mask = saved.getInt("modes");
            return saved.getInt("version") == VERSION && valid(mask)
                    && fingerprint(catalog, phone).equals(saved.getString("fingerprint")) ? mask : UNKNOWN;
        } catch (Exception error) { return UNKNOWN; }
    }

    // Caller has prepared this exact phone on the serialized native worker.
    static void record(File files, String system, int modes) {
        if ("legacy".equals(system)) return;
        try {
            SystemFileCatalog catalog = new SystemFileCatalog(new File(files, "symbian-systems"));
            SystemFileCatalog.Entry phone = catalog.read(system);
            if (!valid(modes)) throw new IOException("Invalid native screen capabilities");
            if (!SymbianGameSettings.supportsGuestScreen(phone.os)) modes = 1;
            JSONObject value = new JSONObject().put("version", VERSION).put("modes", modes)
                    .put("fingerprint", fingerprint(catalog, phone));
            AtomicFile cache = cache(files, system);
            Files.createDirectories(cache.getBaseFile().getParentFile().toPath());
            FileOutputStream output = null;
            try {
                output = cache.startWrite();
                output.write(value.toString().getBytes(StandardCharsets.UTF_8));
                cache.finishWrite(output);
            } catch (IOException error) { cache.failWrite(output); throw error; }
        } catch (Exception error) {
            // Capability discovery must not prevent an otherwise valid game from starting.
            Log.w("AbyssSymbian", "Could not cache phone screen modes", error);
        }
    }

    private static boolean valid(int mask) { return mask >= 1 && mask <= 7 && (mask & 1) != 0; }
    private static AtomicFile cache(File files, String system) {
        return new AtomicFile(new File(new File(files, "symbian-screen-options"), system + ".json"));
    }
    private static String fingerprint(SystemFileCatalog catalog, SystemFileCatalog.Entry phone) throws IOException {
        File runtime = catalog.runtime(phone);
        String firmware = phone.firmware.toLowerCase(Locale.ROOT);
        if (!firmware.matches("[a-z0-9_-]+")) throw new IOException("Invalid firmware identity");
        File wsini = runtime;
        for (String part : new String[]{"data", "drives", "z", firmware, "system", "data", "wsini.ini"}) {
            File[] matches = wsini.listFiles(file -> file.getName().equalsIgnoreCase(part));
            if (matches == null || matches.length != 1) throw new IOException("Missing or ambiguous phone screen configuration");
            wsini = matches[0];
        }
        StringBuilder result = new StringBuilder();
        for (File file : new File[]{new File(catalog.set(phone.id), "set.json"),
                new File(runtime, "data/devices.yml"), new File(runtime, "config.yml"), wsini}) {
            if (!file.isFile() || file.length() > 1024 * 1024) throw new IOException("Invalid phone configuration size");
            result.append(SystemFileCatalog.hash(file)).append(':');
        }
        return result.toString();
    }
    private PhoneScreenOptions() { }
}
