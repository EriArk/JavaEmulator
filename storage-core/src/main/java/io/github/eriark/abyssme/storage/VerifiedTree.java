/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.storage;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.Arrays;

/** Copy without following links. Originals are deleted only against verified destination bytes. */
public final class VerifiedTree {
    public interface Progress { void update(long bytes) throws IOException; }
    private VerifiedTree() { }
    private static void check(File f) throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Move cancelled");
        if (Files.isSymbolicLink(f.toPath())) throw new IOException("Cannot move a symbolic link: " + f.getName());
    }
    private static File[] children(File f) throws IOException {
        File[] files=f.listFiles(); if(files==null) throw new IOException("Storage cannot be read: " + f); return files;
    }
    public static long size(File f) throws IOException {
        check(f); if(f.isFile()) return f.length();
        if(!f.isDirectory()) throw new IOException("Storage entry disappeared: " + f);
        long total=0; for(File child:children(f)) total=Math.addExact(total,size(child)); return total;
    }
    public static long allocatedSize(File f,long block) throws IOException {
        check(f);block=Math.max(4096,block);
        if(f.isFile())return Math.multiplyExact((Math.addExact(f.length(),block-1))/block,block);
        if(!f.isDirectory())throw new IOException("Storage entry disappeared: "+f);
        long total=block;for(File child:children(f))total=Math.addExact(total,allocatedSize(child,block));return total;
    }
    public static void copy(File from, File to, Progress progress) throws IOException {
        check(from); check(to);
        if(to.exists()) throw new IOException("Move destination already exists: " + to);
        if(from.isDirectory()) {
            if(!to.mkdirs()) throw new IOException("Cannot create " + to);
            for(File child:children(from)) copy(child,new File(to,child.getName()),progress);
        } else {
            if(!from.isFile()) throw new IOException("Source file unavailable: " + from);
            File parent=to.getParentFile(); if(!parent.isDirectory()&&!parent.mkdirs()) throw new IOException("Cannot create " + parent);
            try(InputStream in=new FileInputStream(from); FileOutputStream out=new FileOutputStream(to)) {
                byte[] b=new byte[262144]; for(int n;(n=in.read(b))!=-1;) {check(from);out.write(b,0,n);progress.update(n);} out.getFD().sync();
            }
            if(!Arrays.equals(hash(from),hash(to))) throw new IOException("Copy checksum mismatch: " + from.getName());
        }
    }
    public static void verify(File from, File to) throws IOException { verify(from,to,n->{}); }
    public static void verify(File from, File to, Progress progress) throws IOException {
        check(from);check(to);
        if(from.isDirectory()) {
            if(!to.isDirectory()) throw new IOException("Missing copied folder: " + to);
            File[] a=children(from),b=children(to); if(a.length!=b.length) throw new IOException("Library changed during migration");
            for(File f:a)verify(f,new File(to,f.getName()),progress);
        }else if(!from.isFile()||!to.isFile()||from.length()!=to.length()||!Arrays.equals(hash(from),hash(to)))
            throw new IOException("Library verification failed: " + from.getName());
        if(from.isFile())progress.update(from.length());
    }
    public static void deleteVerified(File from, File to) throws IOException { deleteVerified(from,to,n->{}); }
    public static void deleteVerified(File from, File to,Progress progress) throws IOException {
        check(from);check(to); if(!from.exists())return;
        long bytes=from.isFile()?from.length():0;
        if(from.isDirectory()) {for(File f:children(from))deleteVerified(f,new File(to,f.getName()),progress);}
        else verify(from,to);
        Files.delete(from.toPath());progress.update(bytes);
    }
    public static void discard(File f) throws IOException {
        check(f);if(!f.exists())return;
        if(f.isDirectory())for(File child:children(f))discard(child);
        Files.delete(f.toPath());
    }
    private static byte[] hash(File f) throws IOException {
        try {
            MessageDigest hash=MessageDigest.getInstance("SHA-256");
            try(InputStream in=new FileInputStream(f)) {byte[] b=new byte[262144];for(int n;(n=in.read(b))!=-1;) {check(f);hash.update(b,0,n);}}
            return hash.digest();
        }catch(NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
}
