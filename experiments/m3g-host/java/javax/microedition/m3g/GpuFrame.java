/* SPDX-License-Identifier: GPL-3.0-or-later */
package javax.microedition.m3g;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.IdentityHashMap;

/** Bounded replay data for work not yet committed to the CPU target. */
final class GpuFrame {
    static final int MAX_BYTES = 8 * 1024 * 1024, MAX_DRAWS = 256;
    int byteLimit = MAX_BYTES;
    int[] pixels;
    short[] depth;
    int width, height, bytes;
    final ArrayList<Draw> draws = new ArrayList<>();
    private final IdentityHashMap<Image2D, Image> images = new IdentityHashMap<>();
    private final ArrayList<ByteBuffer> buffers = new ArrayList<>();
    private int pooledBytes;

    static final class Image {
        final Image2D source;
        final long revision;
        final int width, height, format;
        final int[] pixels;
        Image(Image2D source) {
            this.source=source; revision=source.revision;
            width=source.getWidth(); height=source.getHeight(); format=source.getFormat();
            pixels=source.isMutable()?source.image.clone():source.image;
        }
    }

    static final class Texture {
        final Image2D[] sources;
        final Image[] levels;
        final int mode, mip, blendColor;
        final boolean linear, repeatS, repeatT;
        Texture(Texture2D texture, Image2D sprite, Graphics3D.TextureFilter filter,
                Graphics3DPipelines.MipmapMode mipMode) {
            Image2D base=sprite!=null?sprite:texture.getImage();
            mip=mipMode==null?0:mipMode==Graphics3DPipelines.MipmapModes.LINEAR?2:1;
            int count=mip==0?1:32-Integer.numberOfLeadingZeros(Math.max(base.getWidth(),base.getHeight()));
            sources=new Image2D[count]; levels=new Image[count];
            for(int i=0;i<count;i++) sources[i]=sprite!=null?sprite:texture.getImageForLOD(i);
            mode=sprite!=null?Texture2D.FUNC_REPLACE:texture.getBlending();
            blendColor=sprite!=null?0:texture.getBlendColor();
            linear=filter==Graphics3D.TextureFilters.BILINEAR;
            repeatS=sprite==null && texture.getWrappingS()==Texture2D.WRAP_REPEAT;
            repeatT=sprite==null && texture.getWrappingT()==Texture2D.WRAP_REPEAT;
        }
    }

    static final class Draw {
        GpuGeometry.State raw;
        ByteBuffer geometry, indices;
        int count, left, top, right, bottom, mask, alpha, threshold, blend;
        int fogMode, fogColor, spriteFog;
        float fogNear, fogFar, fogDensity;
        boolean depthTest, depthWrite, parallelFog, sprite;
        Texture[] textures;
        int storage(int w,int h) { return raw!=null?raw.mesh.bytes()+4096:count*240+(sprite?Math.max(w,h)*8:0)+1024; }
    }

    long required(Draw draw,int w,int h) {
        long size=draw.storage(w,h);
        if(draw.textures.length==0) return size;
        IdentityHashMap<Image2D,Boolean> seen=new IdentityHashMap<>();
        for(Texture t:draw.textures) for(Image2D source:t.sources) {
            Image previous=images.get(source);
            if((previous==null || previous.revision!=source.revision) && seen.put(source,true)==null)
                size+=(long)source.getWidth()*source.getHeight()*4;
        }
        return size;
    }

    void capture(Draw draw,int[] pixels,short[] depth,int w,int h) {
        this.pixels=pixels; this.depth=depth; width=w; height=h;
        bytes+=draw.storage(w,h);
        for(Texture t:draw.textures) for(int i=0;i<t.sources.length;i++) {
            Image2D source=t.sources[i]; Image image=images.get(source);
            if(image==null || image.revision!=source.revision) {
                image=new Image(source); images.put(source,image); bytes+=image.width*image.height*4;
            }
            t.levels[i]=image;
        }
        draws.add(draw);
    }

    ByteBuffer acquire(int size) {
        for(int i=0;i<buffers.size();i++) if(buffers.get(i).capacity()==size) {
            ByteBuffer buffer=buffers.remove(i); pooledBytes-=buffer.capacity();
            buffer.clear(); buffer.limit(size); return buffer;
        }
        return ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
    }

    private void recycle(ByteBuffer buffer) {
        // At most 4 MiB retained between frames, independent of unusually large draws.
        if(buffer!=null && buffers.size()<64 && pooledBytes+buffer.capacity()<=4*1024*1024) {
            buffers.add(buffer); pooledBytes+=buffer.capacity();
        }
    }

    void clear() {
        for(Draw draw:draws) { if(draw.raw==null) recycle(draw.geometry); recycle(draw.indices); }
        draws.clear(); images.clear(); bytes=0; pixels=null; depth=null;
    }

    void close() { clear(); buffers.clear(); pooledBytes=0; }
}
