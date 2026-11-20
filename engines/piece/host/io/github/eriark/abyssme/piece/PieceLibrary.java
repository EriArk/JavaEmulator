/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.piece;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;
import ru.playsoftware.j2meloader.catalog.SourceIdentity;
import ru.playsoftware.j2meloader.handheld.*;

public final class PieceLibrary extends ManagedGameLibrary {
    public PieceLibrary() { super("piece", PieceGameActivity.class); }
    File installed(Context context, String id) throws IOException {
        if (id == null || !id.matches("[a-f0-9]{64}")) throw new IOException("Import a base .pfi flash image first");
        return new File(new File(root(context), id), "image.pfi");
    }
    @Override protected String inspect(Context context, File file, String name, Properties info) throws IOException {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".zip")) {
            // A system image may be distributed as ZIP just like a game.
            try (ZipFile zip = new ZipFile(file)) {
                ZipEntry main = null;
                boolean listed = false;
                for (ZipEntry entry : Collections.list(zip.entries())) {
                    String path = entry.getName().toLowerCase(Locale.ROOT);
                    if (path.endsWith(".pfs")) listed = true;
                    if (path.endsWith(".pfi") || path.endsWith(".pex") || path.equals("all.bin") || path.endsWith("/all.bin")) {
                        if (main == null || path.contains("/2mb/")) main = entry;
                    }
                }
                if (!listed && main != null) {
                    lower = new File(main.getName()).getName().toLowerCase(Locale.ROOT);
                    byte[] image = read(zip,main,3*1024*1024);
                    Files.write(file.toPath(),image);
                }
            }
        }
        if (lower.endsWith(".pfi") || lower.endsWith(".exe") || lower.endsWith(".bin")) {
            if (!lower.endsWith(".pfi")) {
                PieceFirmware.prepare(file,lower.endsWith(".exe"));
                info.setProperty("title","P/ECE system");
            }
            new PieceImage(file);
            info.setProperty("format", "PFI");
        } else {
            File marker = new File(root(context).getParentFile(),"system/base");
            String selected = marker.isFile() ? new String(Files.readAllBytes(marker.toPath()),StandardCharsets.US_ASCII).trim()
                    : context.getSharedPreferences("piece", 0).getString("base", null);
            File base = installed(context,selected);
            if (!base.isFile()) throw new IOException("Import a base .pfi flash image first");
            PieceImage image = new PieceImage(base);
            if (lower.endsWith(".pex")) {
                if (file.length() > image.bytes.length) throw new IOException("Application exceeds flash capacity");
                byte[] program = Files.readAllBytes(file.toPath());
                PieceImage.checkExecutable(program);
                image.add(lower, program);
                extractIcon(program,file.getParentFile());
            } else if (lower.endsWith(".zip")) {
                addDistribution(image, file);
            } else throw new IOException("Choose a .pfi image, .pex application or game ZIP with a .pfs file list");
            Files.write(file.toPath(), image.bytes);
            info.setProperty("format", "PEX");
        }
        return SourceIdentity.sha256(file);
    }
    @Override protected void imported(Context context, String id, Properties info) throws IOException {
        if (!"PFI".equals(variant(info))) return;
        File dir = new File(root(context).getParentFile(),"system");
        Files.createDirectories(dir.toPath());
        java.nio.file.Path tmp = Files.createTempFile(dir.toPath(),"base-", ".tmp");
        try {
            Files.write(tmp,id.getBytes(StandardCharsets.US_ASCII));
            Files.move(tmp,new File(dir,"base").toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(tmp); }
        context.getSharedPreferences("piece", 0).edit().putString("base",id).apply();
    }
    @Override protected void extractInstalledArt(File game,File dir) throws IOException {
        if (new File(dir,"icon.png").isFile() || !"PEX".equals(load(dir).getProperty("format"))) return;
        java.util.Map<String,byte[]> candidates=new PieceImage(game).executableHeaders();
        File packages=dir.getParentFile();
        File marker=new File(packages.getParentFile(),"system/base");
        if (marker.isFile() && marker.length()==64) {
            String id=new String(Files.readAllBytes(marker.toPath()),StandardCharsets.US_ASCII);
            if (id.matches("[a-f0-9]{64}")) {
                File base=new File(new File(packages,id),"image.pfi");
                if (base.isFile()) {
                    java.util.Map<String,byte[]> stock=new PieceImage(base).executableHeaders();
                    candidates.entrySet().removeIf(e->java.util.Arrays.equals(e.getValue(),stock.get(e.getKey())));
                }
            }
        }
        if (candidates.size()==1) extractIcon(candidates.values().iterator().next(),dir);
    }
    private static void extractIcon(byte[] program, File dir) {
        int offset=(program[6]&255) | (program[7]&255)<<8;
        int data=(program[2]&255) | (program[3]&255)<<8;
        if (program[0]!='X' || offset<24 || offset+256>data || offset+256>program.length) return;
        int[] pixels=new int[32*32];
        for (int i=0;i<pixels.length;i++) {
            int shade=3-((program[offset+i/4]>>(6-2*(i%4)))&3);
            pixels[i]=0xff000000 | shade*0x555555;
        }
        android.graphics.Bitmap bitmap=android.graphics.Bitmap.createBitmap(pixels,32,32,android.graphics.Bitmap.Config.ARGB_8888);
        try { ru.playsoftware.j2meloader.applist.GameArtwork.writeBitmap(new File(dir,"icon.png"),bitmap); }
        catch (IOException e) { android.util.Log.w("PieceLibrary","Cannot save executable icon",e); }
        finally { bitmap.recycle(); }
    }
    private static byte[] read(ZipFile zip, ZipEntry entry, int max) throws IOException {
        if (entry == null || entry.isDirectory() || entry.getSize() < 0 || entry.getSize() > max)
            throw new IOException("Missing or oversized P/ECE package file");
        try (DataInputStream in = new DataInputStream(zip.getInputStream(entry))) {
            byte[] bytes = new byte[(int)entry.getSize()];
            in.readFully(bytes);
            if (in.read() != -1) throw new IOException("Incorrect P/ECE ZIP entry size");
            return bytes;
        }
    }
    private static void addDistribution(PieceImage image, File file) throws IOException {
        try (ZipFile zip = new ZipFile(file)) {
            ZipEntry manifest = null;
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.getName().toLowerCase(Locale.ROOT).endsWith(".pfs")) continue;
                if (manifest != null) throw new IOException("Choose a ZIP with a single P/ECE file list");
                manifest = entry;
            }
            if (manifest == null) throw new IOException("Missing .pfs file list in game ZIP");
            String dir = manifest.getName().substring(0, manifest.getName().lastIndexOf('/') + 1);
            String list = new String(read(zip, manifest, 8192), StandardCharsets.US_ASCII);
            boolean executable = false;
            for (String line : list.split("[\\r\\n]+")) {
                String name = line.trim();
                if (name.isEmpty()) continue;
                String lower = name.toLowerCase(Locale.ROOT);
                byte[] bytes = read(zip, zip.getEntry(dir + name), image.bytes.length);
                if (lower.endsWith(".pex")) { PieceImage.checkExecutable(bytes);
                    if (!executable) extractIcon(bytes,file.getParentFile());
                    executable = true; }
                image.add(lower, bytes);
            }
            if (!executable) throw new IOException("No executable listed in P/ECE game ZIP");
        }
    }
    @Override protected void configureProfile(ru.playsoftware.j2meloader.config.ProfileModel profile) {
        LibretroKeys.defaultMappings(profile);
    }
    @Override protected String filename(Properties info) { return "image.pfi"; }
    @Override protected String variant(Properties info) { return info.getProperty("format", "PFI"); }
    @Override protected int firstCardId() { return -1600000000; }
    @Override protected int platformId(Properties info) { return 8; }
    @Override protected String platformName(Properties info) { return "Aquaplus P/ECE"; }
}
