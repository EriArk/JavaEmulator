/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.cybiko;

import android.content.Context;
import android.net.Uri;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

final class CybikoFirmware {
    static boolean ready(File dir) { return new File(dir,"boot.bin").isFile() && new File(dir,"system.bin").isFile(); }
    static int install(Context c, Uri uri, File dir, boolean classic) throws IOException {
        Files.createDirectories(dir.toPath());
        File tmp=File.createTempFile("firmware-", ".tmp",c.getCacheDir());
        try {
            try (InputStream in=c.getContentResolver().openInputStream(uri)) {
                if (in==null) throw new IOException("Cannot open selected file");
                Files.copy(in,tmp.toPath(),StandardCopyOption.REPLACE_EXISTING);
            }
            try (RandomAccessFile in=new RandomAccessFile(tmp,"r")) {
                if (in.length()>=4 && in.readInt()==0x504b0304) {
                    int recognized=0;
                    try (ZipFile zip=new ZipFile(tmp)) {
                        Map<String,ZipEntry> selected=new LinkedHashMap<>();
                        for (ZipEntry e:Collections.list(zip.entries())) {
                            String path=e.getName().toLowerCase(Locale.ROOT);
                            if (e.isDirectory() || path.contains(classic?"xtreme/":"classic/") ||
                                    path.endsWith(classic?"cyrom150.bin":"cyrom112.bin")) continue;
                            String dest=destination(e.getSize(),classic);
                            if (dest==null) continue;
                            if (selected.put(dest,e)!=null) throw new IOException("Multiple firmware versions in this ZIP. Select the matching files individually.");
                        }
                        for (Map.Entry<String,ZipEntry> match:selected.entrySet()) {
                            ZipEntry e=match.getValue();
                            try (InputStream bytes=zip.getInputStream(e)) { store(bytes,dir,match.getKey(),e.getSize()); }
                            recognized++;
                        }
                    }
                    return recognized;
                }
            }
            String dest=destination(tmp.length(),classic);
            if (dest==null) return 0;
            try (InputStream in=new FileInputStream(tmp)) { store(in,dir,dest,tmp.length()); }
            return 1;
        } finally { Files.deleteIfExists(tmp.toPath()); }
    }
    private static String destination(long n,boolean classic) {
        if (n==32768) return "boot.bin";
        if (classic ? n==540672 || n==1081344 : n==524288) return "system.bin";
        return null;
    }
    private static void store(InputStream in,File dir,String name,long length) throws IOException {
        Path tmp=Files.createTempFile(dir.toPath(),"system-", ".tmp");
        try {
            if (Files.copy(in,tmp,StandardCopyOption.REPLACE_EXISTING)!=length) throw new IOException("Incomplete firmware file");
            Files.move(tmp,new File(dir,name).toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
        } finally { Files.deleteIfExists(tmp); }
    }
    private CybikoFirmware() {}
}
