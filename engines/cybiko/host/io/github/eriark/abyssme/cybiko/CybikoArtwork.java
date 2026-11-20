/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.cybiko;
import android.graphics.Bitmap;
import java.io.*;
import java.util.Arrays;
import ru.playsoftware.j2meloader.applist.GameArtwork;

/** CyOS names root.ico in the big-endian application directory. */
final class CybikoArtwork {
    static void extract(File app,File dir) throws IOException {
        byte[] image=resource(app,"root.ico");
        if (image.length<8) return;
        int w=image[2]&255,h=image[3]&255,stride=(w+3)/4;
        if (w==0 || h==0 || 8+stride*h>image.length) return;
        int[] pixels=new int[w*h];
        for (int y=0;y<h;y++) for (int x=0;x<w;x++) {
            int shade=3-((image[8+stride*y+x/4]>>(6-2*(x%4)))&3);
            pixels[y*w+x]=0xff000000 | shade*0x555555;
        }
        Bitmap bitmap=Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);
        try { GameArtwork.writeBitmap(new File(dir,"icon.png"),bitmap); }
        finally { bitmap.recycle(); }
    }
    static String[] description(File app) throws IOException {
        return new String(resource(app,"root.inf"),java.nio.charset.StandardCharsets.ISO_8859_1).split("\\r?\\n",-1);
    }
    private static byte[] resource(File app,String nameInArchive) throws IOException {
        byte[] resource=(nameInArchive+"\0").getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        try (RandomAccessFile f=new RandomAccessFile(app,"r")) {
            if (f.readUnsignedShort()!=0x4379) return new byte[0];
            int count=f.readUnsignedShort();
            for (int i=0;i<count;i++) {
                f.seek(6L+10L*i);
                int name=f.readUnsignedShort();
                long offset=Integer.toUnsignedLong(f.readInt()), size=Integer.toUnsignedLong(f.readInt());
                if (name+resource.length>f.length() || size<2 || size>65536 || offset+size>f.length()) continue;
                f.seek(name);
                byte[] key=new byte[resource.length]; f.readFully(key);
                if (!Arrays.equals(key,resource)) continue;
                f.seek(offset); byte[] blob=new byte[(int)size]; f.readFully(blob);
                return unpack(blob);
            }
        }
        return new byte[0];
    }
    // Each row is the prefix (with leading sentinel bit), base and number of extra bits.
    private static final int[][] LENGTH={{2, 2, 1}, {6, 4, 1}, {28, 6, 0}, {29, 8, 1}, {60, 7, 0}, {61, 10, 1}, {248, 12, 0}, {249, 13, 0}, {250, 20, 2}, {251, 16, 1}, {252, 24, 3}, {253, 14, 0}, {508, 32, 5}, {509, 18, 1}, {510, 15, 0}, {1022, 64, 6}, {2046, 128, 7}, {2047, 256, 0}};
    private static final int[][] DISTANCE={{8, 1024, 10}, {9, 512, 9}, {10, 256, 8}, {11, 64, 6}, {12, 128, 7}, {26, 2048, 11}, {27, 4096, 12}, {28, 32, 5}, {29, 16, 4}, {60, 0, 1}, {61, 6, 2}, {125, 2, 0}, {124, 4, 1}, {126, 12, 2}, {254, 3, 0}, {255, 10, 1}};
    private final byte[] blob;
    private int bit=40;
    private CybikoArtwork(byte[] blob) { this.blob=blob; }
    private int bits(int n) throws IOException {
        int value=0;
        while (n-->0) {
            if (bit/8>=blob.length) throw new EOFException("Truncated icon");
            value=(value<<1) | ((blob[bit/8]>>(bit%8))&1); bit++;
        }
        return value;
    }
    private int code(int[][] table) throws IOException {
        int prefix=1;
        for (int i=0;i<10;i++) {
            prefix=2*prefix+bits(1);
            for (int[] row:table) if (row[0]==prefix) return row[1]+bits(row[2]);
        }
        throw new IOException("Invalid CyOS icon code");
    }
    private static byte[] unpack(byte[] b) throws IOException {
        if (b[0]==0) return Arrays.copyOfRange(b,1,b.length);
        if (b[0]!=2 || b.length<5) return new byte[0];
        int size=java.nio.ByteBuffer.wrap(b,1,4).getInt();
        if (size<=0 || size>65536) return new byte[0];
        byte[] out=new byte[size]; CybikoArtwork reader=new CybikoArtwork(b);
        for (int pos=0;pos<size;) {
            if (reader.bits(1)==0) { out[pos++]=(byte)reader.bits(8); continue; }
            int length=reader.code(LENGTH), distance=length+reader.code(DISTANCE);
            for (int i=0;i<length && pos<size;i++,pos++)
                out[pos]=pos>=distance ? out[pos-distance] : 0;
        }
        return out;
    }
}
