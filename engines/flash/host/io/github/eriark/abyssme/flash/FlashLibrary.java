/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.flash;

import android.content.Context;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.zip.*;
import ru.playsoftware.j2meloader.catalog.SourceIdentity;
import ru.playsoftware.j2meloader.handheld.ManagedGameLibrary;

public final class FlashLibrary extends ManagedGameLibrary {
    public FlashLibrary() { super("flash",FlashGameActivity.class); }
    private static String stem(String path) {
        String name=new File(path).getName();
        int dot=name.lastIndexOf('.');
        return dot>0 ? name.substring(0,dot) : name;
    }
    @Override protected String inspect(Context context,File file,String name,Properties info) throws IOException {
        SwfMovie movie;
        if (name.toLowerCase(Locale.ROOT).endsWith(".zip")) {
            File standalone = new File(file.getParentFile(), "standalone.swf");
            try (ZipFile zip=new ZipFile(file)) {
                List<ZipEntry> movies=new ArrayList<>();
                int fileCount = 0;
                for (Enumeration<? extends ZipEntry> it=zip.entries();it.hasMoreElements();) {
                    ZipEntry e=it.nextElement();
                    if (e.isDirectory()) continue;
                    fileCount++;
                    if (e.getName().toLowerCase(Locale.ROOT).endsWith(".swf")) movies.add(e);
                }
                if (movies.isEmpty()) throw new IOException("No SWF in this ZIP");
                if (movies.size()>1) movies.removeIf(e->!stem(e.getName()).equalsIgnoreCase(stem(name)));
                if (movies.size()!=1) throw new IOException("For a multi-movie ZIP, name the ZIP after the starting SWF");
                ZipEntry entry=movies.get(0);
                if (!safePath(entry.getName())) throw new IOException("Invalid movie path");
                try (InputStream in=zip.getInputStream(entry)) { movie=SwfMovie.read(in); }
                if (fileCount == 1) {
                    try (InputStream in = zip.getInputStream(entry)) { Files.copy(in, standalone.toPath()); }
                    info.setProperty("entry", "movie.swf");
                } else {
                    info.setProperty("entry", entry.getName());
                    info.setProperty("format", "ZIP");
                }
                info.setProperty("title",stem(entry.getName()));
            }
            if (standalone.isFile()) Files.move(standalone.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } else {
            try (InputStream in=new FileInputStream(file)) { movie=SwfMovie.read(in); }
            info.setProperty("entry","movie.swf");
        }
        info.setProperty("swf",Integer.toString(movie.version));
        info.setProperty("width",Integer.toString(movie.width));
        info.setProperty("height",Integer.toString(movie.height));
        return SourceIdentity.sha256(file);
    }
    static boolean safePath(String path) {
        if (path.contains("\\") || path.indexOf(0)>=0) return false;
        for (String part:path.split("/",-1))
            if (part.isEmpty() || part.equals(".") || part.equals("..")) return false;
        return true;
    }
    static final class Installed {
        final File file;
        final Properties info;
        Installed(File dir,Properties p,String name) { file=new File(dir,name); info=p; }
        InputStream open(String path) throws IOException {
            if (!safePath(path)) throw new FileNotFoundException();
            if (!file.getName().endsWith(".zip")) {
                if (!path.equals("movie.swf")) throw new FileNotFoundException();
                return new FileInputStream(file);
            }
            ZipFile zip=new ZipFile(file);
            try {
                ZipEntry entry=zip.getEntry(path);
                if (entry==null || entry.isDirectory() || entry.getSize()<0 || entry.getSize()>SwfMovie.MAX_BYTES)
                    throw new FileNotFoundException();
                return new FilterInputStream(zip.getInputStream(entry)) {
                    @Override public void close() throws IOException { try { super.close(); } finally { zip.close(); } }
                };
            } catch (IOException | RuntimeException e) { zip.close(); throw e; }
        }
    }
    Installed installed(Context c,String id) throws IOException {
        if (id==null || !id.matches("[a-f0-9]{64}")) throw new IOException("Unknown Flash game");
        File dir=new File(root(c),id); Properties p=load(dir);
        return new Installed(dir,p,filename(p));
    }
    @Override protected String filename(Properties p) { return "ZIP".equals(p.getProperty("format")) ? "game.zip" : "game.swf"; }
    @Override protected String variant(Properties p) { return "SWF "+p.getProperty("swf"); }
    @Override protected int firstCardId() { return -2050000000; }
    @Override protected int platformId(Properties p) { return 13; }
    @Override protected String platformName(Properties p) { return "Flash Lite"; }
}
