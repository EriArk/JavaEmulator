/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.espboy;

import android.content.Context;
import android.graphics.Bitmap;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;
import ru.playsoftware.j2meloader.catalog.SourceIdentity;
import ru.playsoftware.j2meloader.handheld.ManagedGameLibrary;

public final class EspboyLibrary extends ManagedGameLibrary {
    private static final int[] COLORS = {0x000000,0xede3c7,0xbe3746,0x7fb8b5,0x4a3e4f,0x6ea76c,0x273f68,0xdebb59,
            0xb48d6c,0x42595a,0xc0624d,0x333333,0x777777,0x8fab62,0x3abfd1,0xbbbbbb};
    public EspboyLibrary() { super("espboy", EspboyActivity.class); }
    @Override protected String inspect(Context c, File file, String name, Properties info) throws IOException {
        byte[] input;
        if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            try (ZipFile zip = new ZipFile(file)) {
                List<ZipEntry> packed = new ArrayList<>(), raw = new ArrayList<>();
                for (Enumeration<? extends ZipEntry> it=zip.entries(); it.hasMoreElements();) {
                    ZipEntry e=it.nextElement();
                    String path=e.getName().toLowerCase(Locale.ROOT);
                    if (e.isDirectory() || path.startsWith("__macosx/")) continue;
                    if (path.endsWith(".lge")) packed.add(e);
                    else if (path.endsWith(".bin")) raw.add(e);
                }
                List<ZipEntry> games = packed.isEmpty() ? raw : packed;
                if (games.size()!=1) throw new IOException("Choose one ESPboy LGE game");
                ZipEntry e=games.get(0);
                checkSize(e.getSize());
                try (InputStream in=zip.getInputStream(e); DataInputStream data=new DataInputStream(in)) {
                    input=new byte[(int)e.getSize()]; data.readFully(input);
                }
                name=e.getName().substring(e.getName().lastIndexOf('/')+1);
            }
        } else {
            checkSize(file.length());
            input=Files.readAllBytes(file.toPath());
        }
        boolean header = LgeProgram.hasHeader(input);
        LgeProgram game=new LgeProgram(input, header || name.toLowerCase(Locale.ROOT).endsWith(".lge"));
        Files.write(file.toPath(), game.code);
        String identity=SourceIdentity.sha256(file);
        // Keep a self-identifying cartridge in backups, including raw imports.
        try (OutputStream out = new FileOutputStream(file)) {
            if (!header) out.write(new byte[]{'l','g','e',0,5});
            out.write(input);
        }
        int ext=name.lastIndexOf('.');
        info.setProperty("title", ext>0 ? name.substring(0,ext) : name);
        if (game.icon!=null) {
            int[] colors=new int[384];
            for(int i=0;i<colors.length;i++) {
                int b=game.icon[i/2]&255;
                colors[i]=0xff000000 | COLORS[(i%2==0 ? b>>4 : b&15)];
            }
            Bitmap icon=Bitmap.createBitmap(colors,24,16,Bitmap.Config.ARGB_8888);
            try(OutputStream out=new FileOutputStream(new File(file.getParentFile(),"icon.png"))) {
                icon.compress(Bitmap.CompressFormat.PNG,100,out);
            } finally { icon.recycle(); }
        }
        return identity;
    }
    @Override protected void mergePackage(File stage, File target) throws IOException {
        File packed=new File(stage,"import"), current=new File(target,"program.bin");
        // Preserve the embedded icon/author from the richer copy on reimport.
        try (DataInputStream old=new DataInputStream(new FileInputStream(current));
             DataInputStream fresh=new DataInputStream(new FileInputStream(packed))) {
            if(current.length()>=5 && old.readUnsignedByte()=='l' && old.readUnsignedByte()=='g' && old.readUnsignedByte()=='e') {
                old.readUnsignedByte();
                fresh.skipBytes(4);
                if (old.readUnsignedByte()>=fresh.readUnsignedByte()) return;
            }
        }
        Files.move(packed.toPath(),current.toPath(),java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
    private static void checkSize(long size) throws IOException {
        if(size<=0 || size>3L*LgeProgram.MEMORY+255) throw new IOException("Invalid LGE program size");
    }
    @Override protected String filename(Properties p) { return "program.bin"; }
    @Override protected String platformName(Properties p) { return "ESPboy"; }
    @Override protected int platformId(Properties p) { return 32; }
    @Override protected String variant(Properties p) { return "Little Game Engine"; }
    @Override protected int firstCardId() { return -1060000000; }
}
