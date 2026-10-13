/* SPDX-License-Identifier: GPL-3.0-or-later */
package javax.microedition.m3g;

import java.util.Arrays;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public class GpuCacheTest {
    @After public void restore() {
        GpuRasterizer.lossesRemaining=0; GpuRasterizer.close(); Graphics3D.getInstance().releaseTarget();
        GpuRasterizer.enabled=false; GpuRasterizer.textureCacheEnabled=true;
        GpuRasterizer.textureCacheBytes=GpuTextureCache.UNIT_BYTES;
    }

    @Test public void alternatingMaterialsUploadOnceAndPreserveQueuedPixels() {
        assertArrayEquals(layers(false,false),layers(true,false));
    }

    @Test public void changedQueuedTexturesAndContextRecoveryPreserveOlderVersions() {
        assertArrayEquals(layers(false,true),layers(true,true));
    }

    private int[] layers(boolean cache,boolean mutate) {
        GpuRasterizer.close(); GpuRasterizer.enabled=true; GpuRasterizer.textureCacheEnabled=cache;
        Image2D[] images={image(4,0xffa04020),image(4,0xff2060a0),image(4,0xff608020)};
        int[] pixels=new int[32*24]; short[] depth=new short[pixels.length]; Arrays.fill(depth,(short)32200);
        long uploads=GpuRasterizer.textureUploads,recovery=GpuRasterizer.recoveries;
        for(int i=0;i<12;i++) {
            if(mutate && i==6) images[0].set(0,0,1,1,new byte[]{-32,96,32,-96});
            int[] flags=flags(32,24,1000-i);
            flags[0]=i; flags[2]=i; flags[10]=4; flags[11]=4;
            assertTrue(GpuRasterizer.sprite(pixels,depth,32,24,images[i%3],new float[4],flags,0,0,CompositingMode.ALPHA,true));
        }
        if(mutate) { GpuRasterizer.lossPoint=3; GpuRasterizer.lossesRemaining=1; }
        GpuRasterizer.flush();
        if(!mutate) assertEquals(uploads+(cache?3:12),GpuRasterizer.textureUploads);
        else assertEquals(recovery+1,GpuRasterizer.recoveries);
        assertTrue(GpuRasterizer.cachedTextureBytes()<=3*4*4*4);
        return pixels;
    }

    @Test public void entryLimitEvictsOldImagesWithoutChangingTheirLaterAppearance() {
        GpuRasterizer.close(); GpuRasterizer.enabled=true;
        Image2D first=image(1,0xff204060); long evictions=GpuTextureCache.evictions;
        assertEquals(0xff204060,draw(first));
        for(int i=0;i<8;i++) assertEquals(0xff406080+i,draw(image(1,0xff406080+i)));
        assertEquals(32,GpuRasterizer.cachedTextureBytes());
        long uploads=GpuRasterizer.textureUploads;
        assertEquals(0xff204060,draw(first)); assertEquals(uploads+1,GpuRasterizer.textureUploads);
        assertTrue(GpuTextureCache.evictions>=evictions+2);
    }

    @Test public void byteBudgetKeepsOversizedTexturesTransient() {
        GpuRasterizer.close(); GpuRasterizer.enabled=true; GpuRasterizer.textureCacheBytes=128;
        for(int i=0;i<12;i++) assertEquals(0xff305070+i,draw(image(4,0xff305070+i)));
        assertEquals(128,GpuRasterizer.cachedTextureBytes());
        assertEquals(0xff907030,draw(image(8,0xff907030)));
        assertEquals(128,GpuRasterizer.cachedTextureBytes());
        GpuRasterizer.close(); assertEquals(0,GpuRasterizer.cachedTextureBytes());
    }

    @Test public void mipStorageHighWaterMarkSurvivesSamplerChangesAndMutation() {
        GpuRasterizer.close(); GpuRasterizer.enabled=true;
        Image2D image=image(8,0xff406080); Texture2D texture=new Texture2D(image);
        texture.setBlending(Texture2D.FUNC_REPLACE);
        VertexArray pos=new VertexArray(4,3,1); pos.set(0,4,new byte[]{-1,1,0,-1,-1,0,1,1,0,1,-1,0});
        VertexArray uv=new VertexArray(4,2,1); uv.set(0,4,new byte[]{0,0,0,1,1,0,1,1});
        VertexBuffer vertices=new VertexBuffer(); vertices.setPositions(pos,1,null); vertices.setTexCoords(0,uv,1,null);
        Appearance style=new Appearance(); style.setTexture(0,texture);
        PolygonMode polygon=new PolygonMode(); polygon.setCulling(PolygonMode.CULL_NONE); style.setPolygonMode(polygon);
        Mesh mesh=new Mesh(vertices,new TriangleStripArray(0,new int[]{4}),style);
        Graphics3D g=Graphics3D.getInstance(); Camera camera=new Camera(); camera.setParallel(4,1,1,10);
        g.setCamera(camera,new Transform()); Transform t=new Transform(); t.postTranslate(0,0,-2);
        for(int pass=0;pass<3;pass++) {
            texture.setFiltering(pass==1?Texture2D.FILTER_BASE_LEVEL:Texture2D.FILTER_NEAREST,Texture2D.FILTER_NEAREST);
            Image2D target=new Image2D(Image2D.RGBA,16,16); g.bindTarget(target,true,0); g.clear(null);
            g.render(mesh,t); g.releaseTarget();
            assertEquals(0xff406080,target.image[8*16+8]);
            assertEquals((64+16+4+1)*4,GpuRasterizer.cachedTextureBytes());
        }
        long uploads=GpuRasterizer.textureUploads;
        image.set(0,0,1,1,new byte[]{32,64,96,-1});
        assertEquals(0xff204060,draw(image)); assertEquals(uploads+1,GpuRasterizer.textureUploads);
    }

    private int draw(Image2D image) {
        int[] pixels=new int[16]; short[] depth=new short[16]; Arrays.fill(depth,(short)32200);
        int[] flags=flags(4,4,500); flags[10]=image.getWidth(); flags[11]=image.getHeight();
        assertTrue(GpuRasterizer.sprite(pixels,depth,4,4,image,new float[4],flags,0,0,CompositingMode.REPLACE,false));
        return pixels[5];
    }

    private Image2D image(int size,int color) {
        Image2D image=new Image2D(Image2D.RGBA,size,size); byte[] bytes=new byte[size*size*4];
        for(int i=0;i<bytes.length;i+=4) { bytes[i]=(byte)(color>>16); bytes[i+1]=(byte)(color>>8); bytes[i+2]=(byte)color; bytes[i+3]=(byte)(color>>>24); }
        image.set(0,0,size,size,bytes); return image;
    }

    private static int[] flags(int w,int h,int z) {
        int[] f=new int[20]; f[1]=w; f[3]=h; f[10]=1; f[11]=1;
        f[12]=3; f[13]=z; f[14]=256; f[16]=-1; return f;
    }
}
