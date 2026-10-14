package io.github.eriark.abyssme.symbian;

import com.github.eka2l1.emu.Emulator;
import org.apache.commons.compress.archivers.sevenz.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;

/** Native decoding stays in the Symbian process; the main-process router only reads signatures/7z headers. */
final class CompressedGameArchive {
    static boolean isSeven(File file) throws IOException { return signature(file,new byte[]{0x37,0x7a,(byte)0xbc,(byte)0xaf,0x27,0x1c}); }
    static boolean accepts(File file) throws IOException {
        return isSeven(file) || signature(file,new byte[]{0x52,0x61,0x72,0x21,0x1a,0x07,0x00})
                || signature(file,new byte[]{0x52,0x61,0x72,0x21,0x1a,0x07,0x01,0x00});
    }
    private static boolean signature(File file,byte[] expected) throws IOException {
        try(InputStream in=new FileInputStream(file)) { for(byte b:expected)if(in.read()!=(b&255))return false; return true; }
    }
    static void recover(File directory) throws IOException {
        File[] files=directory.listFiles(f->f.getName().startsWith("decoded-") && f.getName().endsWith(".zip"));
        if(files!=null)for(File file:files)if(!file.isDirectory())Files.deleteIfExists(file.toPath());
    }
    static boolean sevenHasSystemFiles(File file) throws IOException {
        if(!isSeven(file))return false;
        try(SevenZFile archive=new SevenZFile(file,SevenZFileOptions.builder().withMaxMemoryLimitInKb(128*1024).build())) {
            int count=0; boolean system=false;
            for(SevenZArchiveEntry entry:archive.getEntries()) {
                if(++count>250000)throw new IOException("Too many archive entries");
                String path=entry.getName().toLowerCase(Locale.ROOT);
                system|=!entry.isDirectory() && (path.endsWith(".rom") || path.endsWith(".rpkg"));
            }
            return system;
        }
    }
    static File toZip(File source,PreparedSystemArchive.Progress progress) throws IOException {
        if(source.length()>ArchiveBatch.LIMIT)throw new IOException("Game archive exceeds 1 GB");
        progress.update("Reading RAR / 7z archive");
        File output=File.createTempFile("decoded-",".zip",source.getParentFile()); boolean complete=false;
        try {
            try(FileOutputStream file=new FileOutputStream(output); ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(file))) {
                zip.setLevel(Deflater.NO_COMPRESSION);
                Emulator.decodeGameArchive(source.getAbsolutePath(),new Emulator.ArchiveSink() {
                    final NavigableMap<String,Boolean> paths=new TreeMap<>();
                    long total,written,last; String current;
                    public void begin(String name,long size,boolean directory) throws IOException {
                        check(); String path=ArchiveBatch.safe(name); String key=path.toLowerCase(Locale.ROOT).replaceAll("/+$","");
                        if(paths.size()>=20000)throw new IOException("Too many game archive entries");
                        if(paths.put(key,directory)!=null)throw new IOException("Duplicate archive path");
                        for(int slash=key.lastIndexOf('/');slash>0;slash=key.lastIndexOf('/',slash-1))
                            if(Boolean.FALSE.equals(paths.get(key.substring(0,slash))))throw new IOException("Conflicting archive paths");
                        String child=paths.ceilingKey(key+"/");
                        if(!directory && child!=null && child.startsWith(key+"/"))throw new IOException("Conflicting archive paths");
                        if(size<0 || size>ArchiveBatch.LIMIT-total)throw new IOException("Expanded game archive exceeds 1 GB");
                        total+=size;
                        if(output.getParentFile().getUsableSpace()<size+64L*1024*1024)throw new IOException("Not enough space to read this archive");
                        current=ArchiveBatch.clean(path,100); progress.update("Reading: "+current);
                        ZipEntry entry=new ZipEntry(directory && !path.endsWith("/") ? path+"/" : path); entry.setTime(0); zip.putNextEntry(entry);
                    }
                    public void write(byte[] bytes,int length) throws IOException {
                        check(); zip.write(bytes,0,length); written+=length;
                        long now=System.nanoTime(); if(now-last>250000000L) {last=now;progress.update("Reading: "+current+" ("+written/(1024*1024)+" MB)");}
                    }
                    public void end() throws IOException {check();zip.closeEntry();}
                    private void check() throws IOException {
                        if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Archive import cancelled");
                    }
                });
                zip.finish();zip.flush();file.getFD().sync();
            }
            complete=true;return output;
        } finally {if(!complete)Files.deleteIfExists(output.toPath());}
    }
}
