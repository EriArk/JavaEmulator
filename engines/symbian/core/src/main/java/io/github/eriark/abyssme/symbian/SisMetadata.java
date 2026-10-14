package io.github.eriark.abyssme.symbian;

import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Bounded SIS v9 controller reader. File payloads and install expressions are never executed. */
final class SisMetadata {
    static final int LIMIT = 8 * 1024 * 1024;
    static final class Version implements Comparable<Version> {
        final int major, minor, build;
        Version(int major, int minor, int build) { this.major=major; this.minor=minor; this.build=build; }
        @Override public int compareTo(Version v) {
            int n=Integer.compare(major,v.major); if(n==0) n=Integer.compare(minor,v.minor);
            return n==0 ? Integer.compare(build,v.build) : n;
        }
        @Override public String toString() { return major+"."+minor+"."+build; }
    }
    static final class Need {
        final long uid;
        final String name;
        final Version from, to;
        Need(long uid, String name, Version from, Version to) { this.uid=uid; this.name=name; this.from=from; this.to=to; }
        boolean accepts(Version v) { return v!=null && v.compareTo(from)>=0 && (to==null || v.compareTo(to)<=0); }
        String description() { return name+"\nVersion "+from+(to==null ? " or newer" : " to "+to); }
    }
    final long uid;
    final String name;
    final Version version;
    final int installType;
    final List<Need> needs;
    final List<SisMetadata> embedded;
    private SisMetadata(long uid, String name, Version version, int type, List<Need> needs, List<SisMetadata> embedded) {
        this.uid=uid; this.name=name; this.version=version; this.installType=type; this.needs=needs; this.embedded=embedded;
    }
    static SisMetadata read(File file) throws IOException {
        try(RandomAccessFile in=new RandomAccessFile(file,"r")) {
            if(in.length()<32 || word(in)!=0x10201a7aL) throw bad();
            word(in); long uid=word(in); word(in);
            if(word(in)!=12) throw bad();
            long length=word(in), end=in.getFilePointer()+length;
            if(length>in.length()-in.getFilePointer() || length>0x7fffffffL) throw bad();
            while(in.getFilePointer()+8<=end) {
                long type=word(in), size=word(in), start=in.getFilePointer();
                if(size>0x7fffffffL || size>end-start) throw bad();
                if(type==3) {
                    if(size<12 || size>LIMIT) throw bad();
                    int algorithm=(int)word(in); long expanded=word(in), high=word(in);
                    if(high!=0 || expanded<8 || expanded>LIMIT) throw bad();
                    byte[] input=new byte[(int)size-12]; in.readFully(input);
                    byte[] controller;
                    if(algorithm==0) { if(input.length!=expanded) throw bad(); controller=input; }
                    else if(algorithm==1) {
                        Inflater inflater=new Inflater();
                        try {
                            inflater.setInput(input); controller=new byte[(int)expanded]; int offset=0;
                            while(offset<controller.length) {
                                int n=inflater.inflate(controller,offset,controller.length-offset);
                                if(n==0) throw bad(); offset+=n;
                            }
                            if(!inflater.finished() || inflater.getRemaining()!=0) throw bad();
                        } catch(DataFormatException e) { throw new IOException("Invalid SIS compression",e); }
                        finally { inflater.end(); }
                    } else throw bad();
                    Reader reader=new Reader(controller); Field root=reader.field();
                    if(root.type!=13 || reader.data.hasRemaining()) throw bad();
                    SisMetadata result=controller(root.body,0,new int[]{0});
                    if(result.uid!=uid) throw bad();
                    return result;
                }
                in.seek(start+((size+3)&~3L));
            }
            throw bad();
        } catch(BufferUnderflowException | IllegalArgumentException e) { throw new IOException("Invalid SIS metadata",e); }
    }
    private static long word(RandomAccessFile in) throws IOException { return Integer.toUnsignedLong(Integer.reverseBytes(in.readInt())); }
    private static SisMetadata controller(Reader r, int depth, int[] count) throws IOException {
        if(depth>16 || ++count[0]>256) throw bad();
        Reader info=r.expect(14);
        long uid=info.expect(9).uint(); info.expect(1);
        List<Reader> names=info.array(1); info.array(1);
        Version version=version(info.expect(4),false,false); info.expect(8);
        int type=Byte.toUnsignedInt(info.data.get()); info.data.get();
        if(type>4) throw bad();
        List<Need> needs=new ArrayList<>(); List<SisMetadata> embedded=new ArrayList<>();
        boolean prerequisites=false;
        while(r.data.hasRemaining()) {
            Field f=r.field();
            if(f.type==17) {
                if(prerequisites) throw bad(); prerequisites=true;
                f.body.array(18); // Target phone UIDs are not installable runtime dependencies.
                for(Reader dep:f.body.array(18)) {
                    long id=dep.expect(9).uint(); Reader range=dep.expect(5);
                    Version from=version(range.expect(4),true,false);
                    Version to=range.data.hasRemaining() ? version(range.expect(4),true,true) : null;
                    if(range.data.hasRemaining() || (to!=null && from.compareTo(to)>0)) throw bad();
                    needs.add(new Need(id,name(dep.array(1),id),from,to));
                }
            } else if(f.type==28) {
                f.body.array(24);
                for(Reader child:f.body.array(13)) embedded.add(controller(child,depth+1,count));
                // Conditional controllers are deliberately left to the guest installer.
            }
        }
        if(!prerequisites) throw bad();
        return new SisMetadata(uid,name(names,uid),version,type,needs,embedded);
    }
    private static Version version(Reader r, boolean wildcard, boolean upper) throws IOException {
        if(r.data.remaining()!=12) throw bad(); int[] parts=new int[3];
        for(int i=0;i<3;i++) { int n=r.data.getInt(); if(n<0 && (!wildcard || n!=-1)) throw bad(); parts[i]=n==-1 ? (upper ? Integer.MAX_VALUE : 0) : n; }
        return new Version(parts[0],parts[1],parts[2]);
    }
    private static String name(List<Reader> names,long uid) throws IOException {
        if(names.isEmpty()) return String.format(Locale.ROOT,"Component %08X",uid);
        ByteBuffer text=names.get(0).data;
        if(text.remaining()>2048 || (text.remaining()&1)!=0) throw bad();
        String value=StandardCharsets.UTF_16LE.decode(text).toString().replaceAll("[\\p{Cntrl}\\p{Cf}]", " ").trim();
        if(value.length()>160) value=value.substring(0,160);
        return value.isEmpty() ? String.format(Locale.ROOT,"Component %08X",uid) : value;
    }
    private static IOException bad() { return new IOException("Cannot read this SIS installer safely. Select an intact SIS/SISX or a ZIP containing one installer."); }
    private static final class Field {
        final int type; final Reader body;
        Field(int type,Reader body) { this.type=type; this.body=body; }
    }
    private static final class Reader {
        final ByteBuffer data;
        Reader(byte[] bytes) { this(ByteBuffer.wrap(bytes)); }
        Reader(ByteBuffer bytes) { data=bytes.order(ByteOrder.LITTLE_ENDIAN); }
        long uint() { return Integer.toUnsignedLong(data.getInt()); }
        Reader body() throws IOException {
            long size=uint(), padded=(size+3)&~3L;
            if(size>LIMIT || padded>data.remaining()) throw bad();
            ByteBuffer slice=data.slice(); slice.limit((int)size); data.position(data.position()+(int)padded);
            return new Reader(slice);
        }
        Field field() throws IOException { int type=data.getInt(); return new Field(type,body()); }
        Reader expect(int type) throws IOException { Field f=field(); if(f.type!=type) throw bad(); return f.body; }
        List<Reader> array(int type) throws IOException {
            Reader r=expect(2); if(r.data.getInt()!=type) throw bad(); List<Reader> result=new ArrayList<>();
            while(r.data.hasRemaining()) { if(result.size()>=4096) throw bad(); result.add(r.body()); }
            return result;
        }
    }
}
