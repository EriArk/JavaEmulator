/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.cybiko;

import android.content.Context;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import ru.playsoftware.j2meloader.catalog.SourceIdentity;
import ru.playsoftware.j2meloader.config.ProfileModel;
import ru.playsoftware.j2meloader.handheld.*;

public final class CybikoLibrary extends ManagedGameLibrary {
    @Override public void installSystemFile(Context context, android.net.Uri uri, String model) throws IOException {
        if (!"classic".equals(model) && !"xtreme".equals(model)) throw new IOException("Choose a Cybiko model");
        File dir = new File(io.github.eriark.abyssme.storage.StoragePaths.library(context), "cybiko/system/" + model);
        if (CybikoFirmware.install(context, uri, dir, "classic".equals(model)) == 0)
            throw new IOException("No matching Cybiko system files");
    }

    public CybikoLibrary() { super("cybiko", CybikoGameActivity.class); }
    @Override protected String inspect(Context c, File file, String name, Properties info) throws IOException {
        if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            try (ZipFile zip = new ZipFile(file)) {
                ZipEntry app = null;
                for (ZipEntry entry : Collections.list(zip.entries())) {
                    if (entry.isDirectory() || !entry.getName().toLowerCase(Locale.ROOT).endsWith(".app")) continue;
                    if (app != null) throw new IOException("Choose one Cybiko application from this collection");
                    app = entry;
                }
                if (app == null) throw new IOException("Missing Cybiko application");
                File temp = new File(file.getParentFile(), "application");
                CRC32 crc = new CRC32();
                try (InputStream in = new CheckedInputStream(zip.getInputStream(app), crc)) {
                    if (Files.copy(in,temp.toPath()) != app.getSize() || crc.getValue() != app.getCrc())
                        throw new IOException("Damaged Cybiko application");
                }
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                String title = new File(app.getName()).getName();
                info.setProperty("title", title.substring(0,title.length()-4));
            }
        }
        try (RandomAccessFile in = new RandomAccessFile(file,"r")) {
            if (in.readUnsignedShort() != 0x4379) throw new IOException("Missing Cybiko application header");
            int entries = in.readUnsignedShort();
            if (entries == 0 || 6L + 10L*entries > in.length()) throw new IOException("Invalid Cybiko application directory");
        }
        try {
            String[] description=CybikoArtwork.description(file);
            if (description.length>1 && !description[1].trim().isEmpty()) info.setProperty("title", description[1].trim());
            if (description.length>8 && description[8].trim().matches("[0-9]+(\\.[0-9]+)+"))
                info.setProperty("minimum",description[8].trim());
        } catch (IOException e) { android.util.Log.d("CybikoLibrary","Application description unavailable",e); }
        return SourceIdentity.sha256(file);
    }
    @Override protected void extractInstalledArt(File game, File dir) throws IOException { CybikoArtwork.extract(game,dir); }
    File installed(Context c,String id) throws IOException {
        if (id==null || !id.matches("[a-f0-9]{64}")) throw new IOException("Unknown Cybiko application");
        return new File(new File(root(c),id),"game.app");
    }
    @Override protected void configureProfile(ProfileModel p) { LibretroKeys.defaultMappings(p); }
    @Override protected String filename(Properties p) { return "game.app"; }
    @Override protected String variant(Properties p) { return p.containsKey("minimum") ? "CyOS " + p.getProperty("minimum") + "+" : "CyOS"; }
    @Override protected int firstCardId() { return -2000000000; }
    @Override protected int platformId(Properties p) { return 12; }
    @Override protected String platformName(Properties p) { return "Cybiko"; }
}
