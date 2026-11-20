/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.dingoo;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

final class DingooArchive {
    static boolean executable(String path) {
        String lower=path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".app") || lower.endsWith(".cc") || lower.endsWith(".c2s") || lower.endsWith(".c3s");
    }
    static String relative(String input) throws IOException {
        String path=input.replace('\\','/');
        if (path.indexOf(':') >= 0) throw new IOException("Invalid package path");
        for (String part : path.split("/",-1))
            if (part.isEmpty() || part.equals(".") || part.equals("..")) throw new IOException("Invalid package path");
        return path;
    }
    static String unpack(File archive, Properties info) throws IOException {
        File stage=archive.getParentFile(), payload=new File(stage,"content"), program=new File(stage,"program");
        String main=null;
        try (ZipFile zip=new ZipFile(archive)) {
            for (ZipEntry e : Collections.list(zip.entries())) {
                if (!e.isDirectory() && executable(e.getName())) {
                    if (main!=null) throw new IOException("Choose a distribution containing one native game");
                    main=relative(e.getName());
                }
            }
            if (main==null) throw new IOException("No Dingoo or Gemei executable in this ZIP");
            boolean companions=zip.size()>1;
            for (ZipEntry e : Collections.list(zip.entries())) {
                String name=e.getName().replace('\\','/');
                if (e.isDirectory()) name=name.substring(0,name.length()-1);
                File target=new File(payload,relative(name));
                boolean entry=name.equals(main);
                if (e.isDirectory()) { Files.createDirectories(target.toPath()); continue; }
                Files.createDirectories(target.getParentFile().toPath());
                if (e.getSize()<0 || e.getSize()>stage.getUsableSpace()) throw new IOException("Insufficient storage for the game archive");
                CRC32 crc=new CRC32();
                try (InputStream in=new CheckedInputStream(zip.getInputStream(e),crc);
                     OutputStream out=Files.newOutputStream((entry?program:target).toPath(),StandardOpenOption.CREATE_NEW)) {
                    byte[] block=new byte[32768]; long left=e.getSize();
                    for (int n;(n=in.read(block))!=-1;) {
                        if ((left-=n)<0) throw new IOException("Incorrect ZIP entry size");
                        out.write(block,0,n);
                    }
                    if (left!=0 || e.getCrc()!=crc.getValue()) throw new IOException("Damaged game archive");
                }
            }
            if (companions) info.setProperty("entry",main);
        }
        Files.move(program.toPath(),archive.toPath(),StandardCopyOption.REPLACE_EXISTING);
        return main;
    }
}
