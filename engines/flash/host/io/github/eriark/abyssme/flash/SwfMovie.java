/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.eriark.abyssme.flash;

import java.io.*;
import java.util.zip.InflaterInputStream;

/** SWF RECT coordinates are signed, MSB first, in twentieths of a pixel. */
final class SwfMovie {
    static final long MAX_BYTES = 128L * 1024 * 1024;
    final int width, height, version;
    private SwfMovie(int w, int h, int version) { width=w; height=h; this.version=version; }

    static SwfMovie read(InputStream source) throws IOException {
        DataInputStream header = new DataInputStream(source);
        int type=header.readUnsignedByte();
        if (header.readUnsignedShort()!=0x5753 || (type!='C' && type!='F'))
            throw new IOException("Choose an uncompressed or zlib-compressed Flash movie");
        int version=header.readUnsignedByte();
        long length=Integer.toUnsignedLong(Integer.reverseBytes(header.readInt()));
        if (length<16 || length>MAX_BYTES || version==0) throw new IOException("Invalid SWF header");
        try (InputStream body=type=='C' ? new InflaterInputStream(header) : header) {
            int first=body.read();
            if (first<0 || (first>>>3)==0) throw new IOException("Missing SWF stage");
            int bits=first>>>3, byteCount=(5+4*bits+7)/8;
            byte[] rect=new byte[byteCount]; rect[0]=(byte)first;
            new DataInputStream(body).readFully(rect,1,byteCount-1);
            int[] coords=new int[4]; int bit=5;
            for (int i=0;i<4;i++) {
                int number=0;
                for (int k=0;k<bits;k++,bit++) number=(number<<1) | ((rect[bit/8]>>(7-bit%8))&1);
                coords[i]=(number<<(32-bits))>>(32-bits);
            }
            long w=((long)coords[1]-coords[0]+19)/20, h=((long)coords[3]-coords[2]+19)/20;
            if (w<=0 || h<=0) throw new IOException("Empty SWF stage");
            long remaining=length-8-byteCount;
            byte[] chunk=new byte[8192];
            while (remaining>0) {
                int n=body.read(chunk,0,(int)Math.min(remaining,chunk.length));
                if (n<0) throw new EOFException("Incomplete SWF movie");
                remaining-=n;
            }
            if (body.read()!=-1) throw new IOException("SWF length mismatch");
            double scale=Math.min(1,1024.0/Math.max(w,h));
            return new SwfMovie(Math.max(1,(int)Math.round(w*scale)), Math.max(1,(int)Math.round(h*scale)),version);
        }
    }
}
