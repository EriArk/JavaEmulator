/* Copyright 2026. Licensed under the Apache License, Version 2.0. */
package javax.microedition.shell;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipFile;
import ru.playsoftware.j2meloader.config.Config;
import ru.playsoftware.j2meloader.catalog.SourceIdentity;

/** Rebuild only executable code in private cache; never touch the imported game or saves. */
final class GuestDex {
    static File prepare(File app, File cache, File original) throws IOException {
        File jar = new File(app, Config.MIDLET_RES_FILE);
        GuestTime.available(false);
        if (!jar.isFile()) return original;
        try (ZipFile zip = new ZipFile(jar)) {
            if (zip.stream().noneMatch(e -> e.getName().endsWith(".class"))) return original;
        }
        File directory = new File(cache, "guest-time-v1");
        Files.createDirectories(directory.toPath());
        File result = new File(directory, SourceIdentity.sha256(jar) + ".dex");
        if (result.isFile() && result.length() > 0) { GuestTime.available(true); return result; }
        File temporary = File.createTempFile("convert-", ".dex", directory);
        try {
            com.android.dx.command.dexer.Main.main(new String[]{"--no-optimize", "--core-library",
                    "--output=" + temporary.getAbsolutePath(), jar.getAbsolutePath()});
            if (temporary.length() == 0) throw new IOException("Empty converted game");
            Files.move(temporary.toPath(), result.toPath(), StandardCopyOption.REPLACE_EXISTING);
            GuestTime.available(true);
            return result;
        } catch (Exception failure) {
            throw new IOException("Cannot prepare game timing", failure);
        } finally { Files.deleteIfExists(temporary.toPath()); }
    }
}
