/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.palm;

import android.app.Activity;
import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;
import io.github.eriark.abyssme.storage.StoragePaths;
import ru.playsoftware.j2meloader.catalog.SourceIdentity;
import ru.playsoftware.j2meloader.handheld.ManagedGameLibrary;
import ru.playsoftware.j2meloader.handheld.LibretroKeys;
import ru.playsoftware.j2meloader.config.ProfileModel;

public final class PalmLibrary extends ManagedGameLibrary {
    public PalmLibrary() { super("palm", PalmGameActivity.class); }
    static boolean database(String name) {
        String ext = name.toLowerCase(Locale.ROOT);
        return ext.endsWith(".prc") || ext.endsWith(".pdb");
    }
    static String appName(InputStream stream) throws IOException {
        byte[] head = new byte[78];
        new DataInputStream(stream).readFully(head);
        int len = 0;
        while (len < 32 && head[len] != 0) len++;
        if (len == 0 || len == 32) throw new IOException("Invalid Palm database header");
        return new String(head,60,4,StandardCharsets.US_ASCII).equals("appl")
                ? new String(head,0,len,StandardCharsets.ISO_8859_1) : "";
    }
    @Override protected String inspect(Context c, File file, String name, Properties meta) throws IOException {
        String lower = name.toLowerCase(Locale.ROOT);
        String kind;
        if (lower.endsWith(".zip")) {
            kind = "Bundle";
            int count = 0;
            try (ZipFile zip = new ZipFile(file)) {
                for (ZipEntry entry : Collections.list(zip.entries())) {
                    if (entry.isDirectory() || !database(entry.getName())) continue;
                    try (InputStream in = zip.getInputStream(entry)) { appName(in); }
                    count++;
                }
            }
            if (count == 0) throw new IOException("Choose a ZIP containing Palm PRC or PDB files");
        } else if (database(name)) {
            try (InputStream in = new FileInputStream(file)) {
                String title = appName(in);
                kind = title.isEmpty() ? "Database" : "Application";
                if (!title.isEmpty()) meta.setProperty("title",title);
            }
        } else {
            if (!lower.endsWith(".rom") && !lower.endsWith(".bin")) throw new IOException("Choose a Palm ROM, PRC, PDB or game ZIP");
            try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
                kind = in.readInt() == 0xcefaadba && in.readInt() == 0x07000000 ? "NAND" : "ROM";
            }
        }
        meta.setProperty("format",kind);
        return SourceIdentity.sha256(file);
    }
    File directory(Context c,String id) throws IOException {
        if (id == null || !id.matches("[a-f0-9]{64}")) throw new IOException("Invalid Palm package identity");
        return new File(root(c),id);
    }
    Properties info(File dir) throws IOException { return load(dir); }
    static File system(Context c) { return new File(StoragePaths.library(c),"palm/system"); }
    static void select(Context c,String kind,String id) throws IOException {
        File dir = system(c);
        Files.createDirectories(dir.toPath());
        AtomicFile target = new AtomicFile(new File(dir,kind));
        FileOutputStream out = target.startWrite();
        try { out.write(id.getBytes(StandardCharsets.US_ASCII)); target.finishWrite(out); }
        catch (IOException e) { target.failWrite(out); throw e; }
    }
    static String selected(Context c,String kind) throws IOException {
        File f = new File(system(c),kind);
        return f.exists() ? new String(new AtomicFile(f).readFully(),StandardCharsets.US_ASCII) : "";
    }
    @Override protected void imported(Context c,String id,Properties info) throws IOException {
        String kind = variant(info);
        if (kind.equals("NAND") || (kind.equals("ROM") && selected(c,"ROM").isEmpty())) select(c,kind,id);
    }
    @Override protected String filename(Properties p) { switch (variant(p)) {
        case "ROM": return "device.rom";
        case "NAND": return "nand.bin";
        case "Bundle": return "game.zip";
        case "Database": return "data.pdb";
        default: return "game.prc";
    } }
    @Override protected String variant(Properties p) { return p.getProperty("format"); }
    @Override protected int firstCardId() { return -2030000000; }
    @Override protected int platformId(Properties p) { return 16; }
    @Override protected String platformName(Properties p) { return "Palm OS"; }
    @Override protected void configureProfile(ProfileModel p) { LibretroKeys.defaultMappings(p); }
}
