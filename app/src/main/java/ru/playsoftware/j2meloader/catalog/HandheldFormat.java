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
    private static final java.util.regex.Pattern GIZMONDO_DIRECTORY =
            java.util.regex.Pattern.compile("(?:^|/)gz[a-z]{2}[0-9]{6}/");

    static int remembered(android.content.Context c, Uri uri) {
        return c.getSharedPreferences("web-package-platforms", 0).getInt(SourceIdentity.libraryKey(SourceIdentity.key(uri)), -1);
    }
    static void remember(android.content.Context c, Uri uri, int platform) {
        c.getSharedPreferences("web-package-platforms", 0).edit()
                .putInt(SourceIdentity.libraryKey(SourceIdentity.key(uri)), platform).apply();
    }

    public static boolean arduboyBundle(ZipFile zip) {
        ZipEntry manifest = zip.getEntry("info.json");
        if (manifest == null || manifest.getSize() > 1024 * 1024) return false;
        try (Reader reader = new InputStreamReader(zip.getInputStream(manifest), StandardCharsets.UTF_8)) {
            com.google.gson.JsonObject object = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            String program = object.getAsJsonArray("binaries").get(0).getAsJsonObject().get("filename").getAsString();
            return zip.getEntry(program) != null;
        } catch (IOException | RuntimeException invalid) { return false; }
    }

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
        if (BuildConfig.ARDUBOY_PROBE && (s.endsWith(".hex") || s.endsWith(".arduboy"))) return 25;
        if (BuildConfig.GAMEBUINO_PROBE && s.endsWith(".hex")) return 26;
        if (BuildConfig.ESPBOY_PROBE && s.endsWith(".lge")) return 32;
        if (BuildConfig.THUMBY_PROBE && s.endsWith(".py")) return 30;
        if (BuildConfig.POKITTO_PROBE && s.endsWith(".pop")) return 27;
        if (BuildConfig.IPHONE_PROBE && s.endsWith(".ipa")) return 24;
        if (BuildConfig.POCKETPC_PROBE && s.endsWith(".cab")) return 22;
        if (BuildConfig.PORTFOLIO_PROBE && s.endsWith(".com")) return 21;
        if (BuildConfig.WEBOS_PROBE && s.endsWith(".ipk")) return 18;
        if (BuildConfig.MRE_PROBE && s.endsWith(".vxp")) return 17;
        if (BuildConfig.PALM_PROBE && (s.endsWith(".prc") || s.endsWith(".pdb"))) return 16;
        if (BuildConfig.GAMEKING_PROBE && s.endsWith(".egpc")) return 29;
        if (BuildConfig.GAMEKING_PROBE && (s.endsWith(".gm") || s.endsWith(".u1"))) return 28;
        if (BuildConfig.GAMEKING_PROBE && s.endsWith(".gk")) return 15;
        if (BuildConfig.GAMATE_PROBE && s.endsWith(".gam")) return 14;
        if (BuildConfig.FLASH_PROBE && s.endsWith(".swf")) return 13;
        if (s.endsWith(".app")) return BuildConfig.DINGOO_PROBE ? 6 : BuildConfig.CYBIKO_PROBE ? 12 : 0;
        if (BuildConfig.DINGOO_PROBE && (s.endsWith(".cc") || s.endsWith(".c2s") || s.endsWith(".c3s"))) return 7;
        if (BuildConfig.PIECE_PROBE && (s.endsWith(".pfi") || s.endsWith(".pex"))) return 8;
        if (BuildConfig.MEGADUCK_PROBE && (s.endsWith(".md1") || s.endsWith(".md2") || s.endsWith(".duck"))) return 11;
        if (BuildConfig.SUPERVISION_PROBE && s.endsWith(".sv")) return 10;
        if (BuildConfig.GP32_PROBE && (s.endsWith(".smc") || s.endsWith(".fpk") || s.endsWith(".fxe"))) return 9;
        return 0;
    }
    private static boolean binaryFile(String name) {
        return name.endsWith(".bin") || ((BuildConfig.GAMATE_PROBE || BuildConfig.GAMEKING_PROBE || BuildConfig.PALM_PROBE) && name.endsWith(".rom"));
    }
    static boolean enabled() { return BuildConfig.ESPBOY_PROBE || BuildConfig.THUMBY_PROBE || BuildConfig.POKITTO_PROBE || BuildConfig.GAMEBUINO_PROBE || BuildConfig.ARDUBOY_PROBE || BuildConfig.IPHONE_PROBE || BuildConfig.WIPI_PROBE || BuildConfig.DINGOO_PROBE || BuildConfig.PIECE_PROBE || BuildConfig.GP32_PROBE || BuildConfig.SUPERVISION_PROBE || BuildConfig.MEGADUCK_PROBE || BuildConfig.CYBIKO_PROBE || BuildConfig.FLASH_PROBE || BuildConfig.GAMATE_PROBE || BuildConfig.GAMEKING_PROBE || BuildConfig.PALM_PROBE || BuildConfig.MRE_PROBE || BuildConfig.WEBOS_PROBE || BuildConfig.B2G_PROBE || BuildConfig.PORTFOLIO_PROBE || BuildConfig.POCKETPC_PROBE; }
    static boolean candidate(String name) {
        String s=name.toLowerCase(Locale.ROOT);
        return enabled() && (platform(s)!=0 || s.endsWith(".zip") || binaryFile(s) || s.endsWith(".exe"));
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
                    if (BuildConfig.B2G_PROBE) {
                        B2gManifest web = B2gManifest.read(zip);
                        if (web != null) return web.platforms;
                    }
                    if (BuildConfig.THUMBY_COLOR_PROBE && ThumbyPackage.colorArchive(zip)) return Collections.singleton(31);
                    if (BuildConfig.THUMBY_PROBE && ThumbyPackage.recognizes(zip)) return Collections.singleton(30);
                    boolean arduboyBundle = BuildConfig.ARDUBOY_PROBE && arduboyBundle(zip);
                    Set<String> paths=new HashSet<>(); boolean legacy=false, jar=false, ceProgram=false, gizmondoCard=false;
                    for (Enumeration<? extends ZipEntry> it=zip.entries();it.hasMoreElements();) {
                        ZipEntry e=it.nextElement();
                        String p=e.getName().replace('\\','/').toLowerCase(Locale.ROOT); paths.add(p);
                        if (BuildConfig.GIZMONDO_PROBE && !gizmondoCard) {
                            gizmondoCard = GIZMONDO_DIRECTORY.matcher(p).find();
                        }
                        if (e.isDirectory()) continue;
                        int id=platform(p);
                        if (id == 30) continue; // Python helpers alone do not identify a Thumby archive.
                        if (BuildConfig.POKITTO_PROBE && p.endsWith(".bin") && e.getSize() <= 262144) {
                            try (InputStream in = zip.getInputStream(e)) {
                                if (pokittoVector(prefix(in, 8), e.getSize())) id = 27;
                            }
                        }
                        if (BuildConfig.PORTFOLIO_PROBE && p.endsWith(".exe")) {
                            try (InputStream in=zip.getInputStream(e)) { if (dosProgram(in, e.getSize())) id=21; }
                        }
                        if (BuildConfig.POCKETPC_PROBE && p.endsWith(".exe")) {
                            try (InputStream in=zip.getInputStream(e)) { if (PocketPcFormat.armExecutable(in)) ceProgram=true; }
                        }
                        if (id==22 && p.endsWith(".cab")) {
                            try (InputStream in=zip.getInputStream(e)) { if (!cabinet(prefix(in,4))) id=0; }
                        }
                        if (id==6 || id==7 || id==12) try(InputStream in=zip.getInputStream(e)) {
                            byte[] h = prefix(in, 4);
                            if (p.endsWith(".app") && cybiko(h)) id=12;
                            else if (!ccdl(h) || id==12) id=0;
                        }
                        if (BuildConfig.GAMEBUINO_PROBE && p.endsWith(".hex") && !arduboyBundle) result.add(26);
                        if (id!=0) result.add(id);
                        if (p.endsWith(".pfs") && BuildConfig.PIECE_PROBE) result.add(8);
                        if (p.endsWith(".jar")) jar=true;
                        if (p.endsWith(".mpn")||p.endsWith(".sis")||p.endsWith(".sisx")||p.endsWith(".n-gage")) legacy=true;
                    }
                    if (ceProgram) {
                        boolean alienCard = paths.stream().anyMatch(p -> p.endsWith("/alien hominid.exe") || p.equals("alien hominid.exe"))
                                && paths.stream().anyMatch(p -> p.endsWith("/sky.bmp") || p.equals("sky.bmp"));
                        result.add(BuildConfig.GIZMONDO_PROBE && (gizmondoCard || alienCard) ? 23 : 22);
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
                        if (binaryFile(e.getName().toLowerCase(Locale.ROOT))) try(InputStream in=zip.getInputStream(e)) {
                            byte[] header = prefix(in, 128);
                            int id = cartridgeOrFirmware(in, header, e.getSize());
                            if (id != 0) result.add(id);
                        }
                    }
                    if (!result.isEmpty() && (legacy || jar&&!carrier)) result.add(0);
                }
            } finally { if(tmp!=null)tmp.delete(); }
        } else if (platform == 30 && BuildConfig.THUMBY_COLOR_PROBE) {
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                result.add(in != null && ThumbyPackage.colorScript(in) ? 31 : 30);
            }
        } else if (platform==6 || platform==12 || platform==22 || binaryFile(s) || s.endsWith(".exe")) {
            try (InputStream in=c.getContentResolver().openInputStream(uri)) {
                if(in==null)throw new IOException("Could not open "+name);
                byte[] h=prefix(in,128);
                if (platform==22 && cabinet(h)) result.add(22);
                if (platform==6 && ccdl(h)) result.add(6);
                if (s.endsWith(".app") && cybiko(h)) result.add(12);
                if (binaryFile(s)) {
                    long length;
                    try (android.content.res.AssetFileDescriptor fd = c.getContentResolver().openAssetFileDescriptor(uri,"r")) {
                        length = fd == null ? -1 : fd.getLength();
                    }
                    int id = cartridgeOrFirmware(in, h, length);
                    if (id != 0) result.add(id);
                }
                if (s.endsWith(".exe") && BuildConfig.POCKETPC_PROBE) {
                    try (InputStream program=c.getContentResolver().openInputStream(uri)) {
                        if (program!=null && PocketPcFormat.armExecutable(program)) result.add(22);
                    }
                }
                if (s.endsWith(".exe") && BuildConfig.PORTFOLIO_PROBE) {
                    try (InputStream program=c.getContentResolver().openInputStream(uri)) {
                        if(program!=null && dosProgram(program,-1))result.add(21);
                    }
                }
                if (s.endsWith(".exe") && BuildConfig.PIECE_PROBE && new String(h,StandardCharsets.ISO_8859_1).contains("LHA's SFX")) result.add(8);
            }
        } else if (platform!=0) result.add(platform);
        if (BuildConfig.GAMEBUINO_PROBE && s.endsWith(".hex")) result.add(26);
        return result;
    }
    private static boolean cabinet(byte[] header) {
        return header.length>=4 && header[0]=='M' && header[1]=='S' && header[2]=='C' && header[3]=='F';
    }
    private static boolean dosProgram(InputStream in, long size) throws IOException {
        byte[] h=prefix(in,64);
        if (h.length<28 || h[0]!='M' || h[1]!='Z') return false;
        if(new String(h,StandardCharsets.ISO_8859_1).contains("LHA's SFX"))return false;
        int pages=(h[4]&255)|((h[5]&255)<<8), last=(h[2]&255)|((h[3]&255)<<8);
        if(pages==0 || last>511 || size>=0 && (pages-1)*512L+(last==0?512:last)>size)return false;
        if(h.length==64) {
            int offset=java.nio.ByteBuffer.wrap(h).order(java.nio.ByteOrder.LITTLE_ENDIAN).getInt(60);
            if(offset>=64 && offset<=1048576) {
                long skip=offset-64;
                while(skip>0){long n=in.skip(skip);if(n==0)break;skip-=n;}
                if(skip==0){byte[] sig=prefix(in,2);if(sig.length==2 && (sig[0]=='P' && sig[1]=='E' ||
                        sig[0]=='N' && sig[1]=='E' || sig[0]=='L' && (sig[1]=='E'||sig[1]=='X')))return false;}
            }
        }
        return true;
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
        if (BuildConfig.ESPBOY_PROBE && header.length >= 5 && header[0]=='l' && header[1]=='g' && header[2]=='e') return 32;
        if (BuildConfig.GAMEKING_PROBE && (length == 4096 || length < 0) && hartungBootVector(header)) return 28;
        if (BuildConfig.GAMEKING_PROBE && (epochBootRom(header, length) || epochCartridge(header, length))) return 29;
        if (BuildConfig.POKITTO_PROBE && pokittoVector(header, length)) return 27;
        if (BuildConfig.PALM_PROBE && header.length >= 12) {
            java.nio.ByteBuffer words = java.nio.ByteBuffer.wrap(header);
            int card = words.getInt(8);
            boolean armCard = header.length >= 120 && words.getInt(104) == 0xefbeedfe
                    && new String(header, 112, 8, StandardCharsets.US_ASCII).equals("PalmCard");
            boolean palmNand = words.getInt(0) == 0xcefaadba && words.getInt(4) == 0x07000000;
            if (card == 0xfeedbeef || card == 0xefbeedfe || armCard || palmNand) return 16;
        }
        if (BuildConfig.GAMATE_PROBE) {
            if (header.length >= 30 && new String(header, 5, 25, StandardCharsets.US_ASCII)
                    .equals("COPYRIGHT BIT CORPORATION")) return 14;
            // Identify the boot jump table before trying the other handhelds' binary formats.
            if ((length == 4096 || length < 0) && header.length >= 9 && header[0] == 0x4c
                    && header[3] == 0x4c && header[6] == 0x4c
                    && (header[2] & 255) >= 0xe0 && (header[5] & 255) >= 0xe0) return 14;
        }
        if (BuildConfig.GAMEKING_PROBE && header.length >= 64 &&
                new String(header, 26, 37, StandardCharsets.US_ASCII).equals("GZ Daidaixing Tec.Electronics Co.,ltd")) return 15;
        if (BuildConfig.GAMEKING_PROBE && (length == 524288 || length < 0) && header.length >= 8
                && header[0] == (byte)0xea && header[1] == (byte)0xea && header[2] == (byte)0xea && header[3] == 0x78
                && header[4] == (byte)0xd8 && header[5] == (byte)0xa2 && header[6] == (byte)0xff && header[7] == (byte)0x9a) return 15;
        // The GM-218 begins with this little-endian firmware jump table.
        if (BuildConfig.GAMEKING_PROBE && (length == 524288 || length < 0) && header.length >= 8
                && header[0] == 7 && header[1] == 0x43 && header[2] == (byte)0xd6 && header[3] == 0x43
                && header[4] == (byte)0xe4 && header[5] == 0x43 && header[6] == (byte)0xfd && header[7] == 0x43) return 15;
        int firmware = binary(header, length);
        if (firmware != 0) return firmware;
        // Retail Mega Duck cartridges commonly disable interrupts and jump into the first ROM window.
        if (BuildConfig.MEGADUCK_PROBE && header.length >= 4 && (length < 0 || length >= 16384 && length % 16384 == 0)
                && header[0] == (byte) 0xf3 && header[1] == (byte) 0xc3) {
            int entry = (header[2] & 255) | (header[3] & 255) << 8;
            if (entry >= 4 && entry < 0x8000) return 11;
        }
        // Game Master cartridges carry a marker at 0x8000. Check stronger headers first.
        if (BuildConfig.GAMEKING_PROBE && header.length > 1 && header[0] == (byte) 0xaa
                && (length < 0 || length <= 32768)) return 28;
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
    public static boolean epochBootRom(byte[] h, long length) {
        return (length == 4096 || length < 0) && h.length >= 7
                && h[0] == 0 && h[1] == 0x48 && h[2] == 0x24 && h[3] == (byte) 0xcf
                && h[4] == 0x54 && h[5] == 0x0c && h[6] == 0x40;
    }

    private static boolean epochCartridge(byte[] h, long length) {
        if (h.length < 5 || h[0] != 0x55 || length > 32768) return false;
        // The BIOS reads two little-endian entry addresses after the cartridge identifier.
        int start = (h[1] & 255) | ((h[2] & 255) << 8);
        return start >= 0x4000 && start < 0x4000 + (length < 0 ? 32768 : length);
    }

    public static boolean hartungBootVector(byte[] h) {
        return h.length >= 8 && h[0] == 0x54 && h[1] == (byte) 0xc0 && h[2] == 0 && h[3] == 0
                && h[4] == 0x54 && h[5] == (byte) 0xc0 && h[6] == 0 && h[7] == 0;
    }
    public static boolean pokittoVector(byte[] header, long length) {
        if (header.length < 8 || (length >= 0 && (length < 8 || length > 262144))) return false;
        java.nio.ByteBuffer words = java.nio.ByteBuffer.wrap(header).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        int stack = words.getInt(), reset = words.getInt();
        boolean ram = (stack > 0x10000000 && stack <= 0x10008000)
                || (stack > 0x20000000 && stack <= 0x20000800) || (stack > 0x20004000 && stack <= 0x20004800);
        return ram && (stack & 3) == 0 && (reset & 1) == 1 && reset >= 9 && reset < (length < 0 ? 262144 : length);
    }
    private static boolean cybiko(byte[] h) { return BuildConfig.CYBIKO_PROBE && h.length >= 4 && h[0]=='C' && h[1]=='y'; }
    private static byte[] prefix(InputStream in,int size) throws IOException {
        byte[] bytes=new byte[size];int n=0;
        while(n<size){int read=in.read(bytes,n,size-n);if(read<0)break;n+=read;}
        return Arrays.copyOf(bytes,n);
    }
    public static int indexedPlatform(String path) {
        if (path != null && path.matches("pending_([5-9]|1[0-9]|2[0-9]|3[0-2])_[a-f0-9]+"))
            return Integer.parseInt(path.substring(8, path.indexOf('_', 8)));
        return 0;
    }
}
