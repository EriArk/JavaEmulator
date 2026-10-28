/* SPDX-License-Identifier: GPL-3.0-only */
package ru.playsoftware.j2meloader.multiplayer;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.ZipFile;
import ru.woesss.j2me.jar.Descriptor;
import ru.playsoftware.j2meloader.catalog.SourceIdentity;

/** Local installed Java entries only. Names are presentation, never network identity. */
final class NetworkGameCatalog {
    static final class Entry {
        final File directory;
        final String title;
        Entry(File directory, String title) { this.directory=directory; this.title=title; }
    }
    static List<Entry> list(File root) throws IOException {
        List<Entry> result=new ArrayList<>(); File[] directories=root.listFiles();
        if(directories==null)throw new NetworkGameException(SocialNotice.GAME_LIBRARY_UNAVAILABLE);
        for(File directory:directories){
            if(Thread.currentThread().isInterrupted())throw new NetworkGameException(SocialNotice.GAME_LOOKUP_CANCELLED);
            if(!directory.isDirectory()||Files.isSymbolicLink(directory.toPath()))continue;
            try { Descriptor d=descriptor(directory);result.add(new Entry(directory,d.getName())); }
            catch(IOException|RuntimeException ignored) { }
        }
        result.sort(Comparator.comparing(e->e.title,String.CASE_INSENSITIVE_ORDER));return result;
    }
    static InstalledNetworkGame verify(File root,Entry entry) throws IOException {
        File directory=entry.directory.getCanonicalFile();
        if(!root.getCanonicalFile().equals(directory.getParentFile()))throw new NetworkGameException(SocialNotice.GAME_OUTSIDE_LIBRARY);
        Descriptor local=descriptor(directory);
        File jar=new File(directory,"res.jar");
        if(!jar.isFile()||Files.isSymbolicLink(jar.toPath())||jar.length()>128L*1024*1024)throw new NetworkGameException(SocialNotice.GAME_FILES_INVALID);
        // A modified JAD must not silently choose another application from the same JAR.
        try(ZipFile zip=new ZipFile(jar)){
            java.util.zip.ZipEntry manifest=zip.getEntry("META-INF/MANIFEST.MF");
            if(manifest==null||manifest.getSize()>1024*1024)throw new NetworkGameException(SocialNotice.GAME_MANIFEST_INVALID);
            byte[] bytes;
            try(InputStream in=zip.getInputStream(manifest);ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[8192];int n;
                while((n=in.read(buffer))!=-1){if(out.size()+n>1024*1024)throw new NetworkGameException(SocialNotice.GAME_MANIFEST_INVALID);out.write(buffer,0,n);}
                bytes=out.toByteArray();
            }
            Descriptor original=new Descriptor(new String(bytes,java.nio.charset.StandardCharsets.UTF_8),false);
            if(!singleClass(original).equals(singleClass(local)))throw new NetworkGameException(SocialNotice.GAME_ENTRY_DIFFERS);
        }
        LobbySession.Game game=new LobbySession.Game("java",SourceIdentity.sha256(jar),"bluetooth","1",2);
        return InstalledNetworkGame.verify(root,directory,game);
    }
    private static Descriptor descriptor(File directory) throws IOException {
        File manifest=new File(directory,"converted.dex.conf");
        if(!manifest.isFile()||manifest.length()>1024*1024||Files.isSymbolicLink(manifest.toPath()))throw new NetworkGameException(SocialNotice.GAME_MANIFEST_INVALID);
        Descriptor descriptor=new Descriptor(manifest,false);singleClass(descriptor);return descriptor;
    }
    private static String singleClass(Descriptor descriptor) throws IOException {
        Map<String,String> attrs=descriptor.getAttrs();String entry=attrs.get("MIDlet-1");
        long count=attrs.keySet().stream().filter(k->k.matches("MIDlet-[0-9]+")).count();
        if(count!=1||entry==null||entry.indexOf(',')<0)throw new NetworkGameException(SocialNotice.GAME_SINGLE_MIDLET);
        String clazz=entry.substring(entry.lastIndexOf(',')+1).trim();
        if(!clazz.matches("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*"))throw new NetworkGameException(SocialNotice.GAME_MANIFEST_INVALID);
        return clazz;
    }
}
