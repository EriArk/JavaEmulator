/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.portfolio;

import android.content.Context;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import ru.playsoftware.j2meloader.catalog.SourceIdentity;
import ru.playsoftware.j2meloader.handheld.ManagedGameLibrary;

public final class PortfolioLibrary extends ManagedGameLibrary {
    @Override public void installSystemFile(Context context, android.net.Uri uri, String model) throws IOException {
        PortfolioFirmware.install(context, uri);
    }

    public PortfolioLibrary() { super("portfolio", PortfolioGameActivity.class); }

    static List<String> programs(ZipFile zip) {
        List<String> result = new ArrayList<>();
        for (Enumeration<? extends ZipEntry> it = zip.entries(); it.hasMoreElements();) {
            ZipEntry e = it.nextElement();
            String name = e.getName().toLowerCase(Locale.ROOT);
            if (!e.isDirectory() && !name.startsWith("__macosx/") && (name.endsWith(".com") || name.endsWith(".exe")))
                result.add(e.getName());
        }
        return result;
    }
    @Override protected String inspect(Context c, File content, String name, Properties info) throws IOException {
        if (!name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            File wrapped = new File(content.getParentFile(), "packed.zip");
            try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(wrapped))) {
                ZipEntry entry = new ZipEntry(new File(name).getName());
                entry.setTime(0);
                out.putNextEntry(entry); Files.copy(content.toPath(), out); out.closeEntry();
            }
            Files.move(wrapped.toPath(), content.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        try (ZipFile zip = new ZipFile(content)) {
            if (programs(zip).isEmpty()) throw new IOException("Choose a Portfolio game with a COM or EXE program");
        }
        String title = name.replaceFirst("(?i)\\.(com|exe|zip)$", "");
        if (!title.isEmpty()) title = Character.toUpperCase(title.charAt(0)) + title.substring(1);
        info.setProperty("title", title);
        info.setProperty("format", "DIP-DOS");
        return SourceIdentity.sha256(content);
    }
    @Override protected String filename(Properties p) { return "game.zip"; }
    @Override protected int firstCardId() { return -1200000000; }
    @Override protected int platformId(Properties p) { return 21; }
    @Override protected String platformName(Properties p) { return "Atari Portfolio"; }
    @Override protected String variant(Properties p) { return "DIP-DOS"; }
}
