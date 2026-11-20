/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.catalog;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import ru.playsoftware.j2meloader.BuildConfig;
import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class HandheldFormat {
    private HandheldFormat() {}
    static String name(Context c, Uri uri) {
        if ("content".equals(uri.getScheme())) try (Cursor cursor=c.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)) {
            if (cursor!=null && cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getString(0);
        } catch (RuntimeException unavailable) {
            // Some document providers only expose the filename in the URI.
        }
        return uri.getLastPathSegment()==null ? "" : uri.getLastPathSegment();
    }
    static int platform(String name) {
        String s=name.toLowerCase(Locale.ROOT);
        if (BuildConfig.DINGOO_PROBE && s.endsWith(".app")) return 6;
        if (BuildConfig.DINGOO_PROBE && (s.endsWith(".cc") || s.endsWith(".c2s") || s.endsWith(".c3s"))) return 7;
        if (BuildConfig.PIECE_PROBE && (s.endsWith(".pfi") || s.endsWith(".pex"))) return 8;
        if (BuildConfig.MEGADUCK_PROBE && (s.endsWith(".md1") || s.endsWith(".md2") || s.endsWith(".duck"))) return 11;
        if (BuildConfig.SUPERVISION_PROBE && s.endsWith(".sv")) return 10;
        if (BuildConfig.GP32_PROBE && (s.endsWith(".smc") || s.endsWith(".fpk") || s.endsWith(".fxe"))) return 9;
        return 0;
    }
    static boolean enabled() { return BuildConfig.WIPI_PROBE || BuildConfig.DINGOO_PROBE || BuildConfig.PIECE_PROBE || BuildConfig.GP32_PROBE || BuildConfig.SUPERVISION_PROBE || BuildConfig.MEGADUCK_PROBE; }
    static boolean candidate(String name) {
        String s=name.toLowerCase(Locale.ROOT);
        return enabled() && (platform(s)!=0 || s.endsWith(".zip") || s.endsWith(".bin") || s.endsWith(".exe"));
    }
    static Set<Integer> detect(Context c, Uri uri, String name) throws IOException {
        Set<Integer> result=new LinkedHashSet<>();
        String s=name.toLowerCase(Locale.ROOT);
        int platform=platform(s);
        if (s.endsWith(".zip")) {
            File tmp=null;
            try {
                File f;
                if ("file".equals(uri.getScheme())) f=new File(uri.getPath());
                else {
                    tmp=File.createTempFile("inspect-", ".zip",c.getCacheDir()); f=tmp;
                    try (InputStream in=c.getContentResolver().openInputStream(uri)) {
                        if (in==null) throw new IOException("Could not open archive");
                        Files.copy(in,tmp.toPath(),StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                try (ZipFile zip=new ZipFile(f,StandardCharsets.ISO_8859_1)) {
                    Set<String> paths=new HashSet<>(); boolean legacy=false, jar=false;
                    for (Enumeration<? extends ZipEntry> it=zip.entries();it.hasMoreElements();) {
                        ZipEntry e=it.nextElement(); if (e.isDirectory()) continue;
                        String p=e.getName().replace('\\','/').toLowerCase(Locale.ROOT); paths.add(p);
                        int id=platform(p);
                        if (id==6 || id==7) try(InputStream in=zip.getInputStream(e)) { if (!ccdl(prefix(in,4))) id=0; }
                        if (id!=0) result.add(id);
                        if (p.endsWith(".pfs") && BuildConfig.PIECE_PROBE) result.add(8);
                        if (p.endsWith(".jar")) jar=true;
                        if (p.endsWith(".mpn")||p.endsWith(".sis")||p.endsWith(".sisx")||p.endsWith(".n-gage")) legacy=true;
                    }
                    boolean carrier=false;
                    if (jar && BuildConfig.WIPI_PROBE) for (String p:paths) {
                        if (!p.endsWith(".jar")) continue;
                        String dir=p.substring(0,p.lastIndexOf('/')+1);
                        if(paths.contains(dir+"__adf__")||paths.contains(dir+"app_info")||paths.contains(p.substring(0,p.length()-4)+".msd")) carrier=true;
                    }
                    if (carrier) result.add(5);
                    if (result.isEmpty()) for(Enumeration<? extends ZipEntry> it=zip.entries();it.hasMoreElements();) {
                        ZipEntry e=it.nextElement();
                        if (e.getName().toLowerCase(Locale.ROOT).endsWith(".bin")) try(InputStream in=zip.getInputStream(e)) {
                            byte[] header = prefix(in, 32);
                            int id = cartridgeOrFirmware(in, header, e.getSize());
                            if (id != 0) result.add(id);
                        }
                    }
                    if (!result.isEmpty() && (legacy || jar&&!carrier)) result.add(0);
                }
            } finally { if(tmp!=null)tmp.delete(); }
        } else if (platform==6 || s.endsWith(".bin") || s.endsWith(".exe")) {
            try (InputStream in=c.getContentResolver().openInputStream(uri)) {
                if(in==null)throw new IOException("Could not open "+name);
                byte[] h=prefix(in,128);
                if (platform==6 && ccdl(h)) result.add(6);
                if (s.endsWith(".bin")) {
                    long length;
                    try (android.content.res.AssetFileDescriptor fd = c.getContentResolver().openAssetFileDescriptor(uri,"r")) {
                        length = fd == null ? -1 : fd.getLength();
                    }
                    int id = cartridgeOrFirmware(in, h, length);
                    if (id != 0) result.add(id);
                }
                if (s.endsWith(".exe") && BuildConfig.PIECE_PROBE && new String(h,StandardCharsets.ISO_8859_1).contains("LHA's SFX")) result.add(8);
            }
        } else if (platform!=0) result.add(platform);
        return result;
    }
    private static boolean ccdl(byte[] h) {return h.length>=4 && h[0]=='C' && h[1]=='C' && h[2]=='D' && h[3]=='L';}
    private static int binary(byte[] h,long length) {
        if (h.length>=28 && BuildConfig.PIECE_PROBE && new String(h,8,20,StandardCharsets.US_ASCII).equals("Piece Kernel Program")) return 8;
        if (h.length>=32 && BuildConfig.GP32_PROBE && length<=524288) {
            int vector=java.nio.ByteBuffer.wrap(h).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt();
            if ((vector&0x0e000000)==0x0a000000 || (vector&0x0f7ff000)==0x051ff000) return 9;
        }
        return 0;
    }
    private static int cartridgeOrFirmware(InputStream in, byte[] header, long length) throws IOException {
        int firmware = binary(header, length);
        if (firmware != 0) return firmware;
        // Retail Mega Duck cartridges commonly disable interrupts and jump into the first ROM window.
        if (BuildConfig.MEGADUCK_PROBE && header.length >= 4 && (length < 0 || length >= 16384 && length % 16384 == 0)
                && header[0] == (byte) 0xf3 && header[1] == (byte) 0xc3) {
            int entry = (header[2] & 255) | (header[3] & 255) << 8;
            if (entry >= 4 && entry < 0x8000) return 11;
        }
        if (!BuildConfig.SUPERVISION_PROBE) return 0;
        if (length < 16384 || length % 16384 != 0) return 0;
        // A headerless cartridge places the 65C02 reset vector in its last bank.
        long distance = length - 4 - header.length;
        while (distance > 0) {
            long skipped = in.skip(distance);
            if (skipped == 0) {
                if (in.read() < 0) return 0;
                skipped = 1;
            }
            distance -= skipped;
        }
        int low = in.read(), high = in.read();
        return low >= 0 && high >= 0x80 ? 10 : 0;
    }
    private static byte[] prefix(InputStream in,int size) throws IOException {
        byte[] bytes=new byte[size];int n=0;
        while(n<size){int read=in.read(bytes,n,size-n);if(read<0)break;n+=read;}
        return Arrays.copyOf(bytes,n);
    }
    public static int indexedPlatform(String path) {
        if (path != null && path.matches("pending_([5-9]|1[01])_[a-f0-9]+"))
            return Integer.parseInt(path.substring(8, path.indexOf('_', 8)));
        return 0;
    }
}
