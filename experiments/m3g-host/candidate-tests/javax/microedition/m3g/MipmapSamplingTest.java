package javax.microedition.m3g;

import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public class MipmapSamplingTest {
    @After public void restore() { NativeRasterizer.enabled=true; }

    @Test public void lodTracksLargestDerivativeInsteadOfArea() {
        assertEquals(3*256,MipmapSampling.lod(0,0,8,0,8,0,0,0,1));
        assertEquals(3*256,MipmapSampling.lod(0,0,0,8,0,0,0,0,1));
        assertEquals(0,MipmapSampling.lod(0,0,0.25f,0,0,0.5f,0,0,1));
        for (int i=0; i<1000; i++) {
            float derivative=(float)Math.pow(2,i/100.0);
            int actual=MipmapSampling.lod(0,0,derivative,0,0,1,0,0,1);
            int expected=(int)(Math.log(derivative)/Math.log(2)*256);
            assertTrue("Q8 LOD error: "+actual+" vs "+expected,Math.abs(actual-expected)<=2);
        }
        // Perspective quotient derivatives: (10 - 2*3) / 2 = 2 texels/pixel.
        assertEquals(256,MipmapSampling.lod(2,0,10,0,6,0,3,3,0.5f));
    }

    @Test public void levelInterpolationPreservesAlphaAndIndependentAxes() {
        Texture2D texture=coloredLevels(1,8);
        int a=texture.getImageForLOD(0).image[0], b=texture.getImageForLOD(1).image[0];
        Graphics3D.TextureFilter constant=(s,t,unit,image)->image.image[0];
        for (int weight : new int[]{0,1,64,128,192,255}) {
            assertEquals(referenceMix(a,b,weight),MipmapSampling.sample(texture,constant,0,3.25f,6.5f,weight,true));
        }
        assertEquals(a,MipmapSampling.sample(texture,constant,0,0,0,128,false));
        assertEquals(b,MipmapSampling.sample(texture,constant,0,0,0,129,false));
        assertEquals(texture.getImageForLOD(3).image[0],MipmapSampling.sample(texture,constant,0,0,0,Integer.MAX_VALUE,true));
        int[] calls={0};
        Graphics3D.TextureFilter coordinates=(s,t,unit,image)-> {
            assertEquals(3.25f,s,0);
            assertEquals(calls[0]++==0 ? 3.25f : 1.625f,t,0);
            return image.image[0];
        };
        MipmapSampling.sample(texture,coordinates,0,3.25f,6.5f,384,true);
        assertEquals(2,calls[0]);
    }

    @Test public void nativeInterpolatesLevelsBeforeTextureBlending() {
        NativeRasterizer.enabled=true;
        assertTrue(NativeRasterizer.available());
        Texture2D texture=coloredLevels(4,4);
        int[][] images={texture.getImageForLOD(0).image,texture.getImageForLOD(1).image,texture.getImageForLOD(2).image};
        float[] f=new float[51], mf=new float[18]; int[] p=new int[45], mp={3,2,0,0,0,0,0,0};
        f[1]=4; f[11]=0.75f; f[12]=1.25f;
        p[1]=4; p[3]=4; p[5]=4; p[6]=2; p[7]=0xc06080a0;
        p[14]=256; p[15]=-1; p[16]=4; p[17]=4; p[19]=1; p[25]=1;
        Graphics3D.TextureFilter constant=(s,t,unit,image)->image.image[0];
        for (int i=0; i<65; i++) {
            mf[0]=(float)Math.pow(2,i/32.0); mf[2]=mf[0];
            int lod=MipmapSampling.lod(f[11],f[12],mf[0],0,mf[2],0,0,0,1);
            int sample=MipmapSampling.sample(texture,constant,0,f[11],f[12],lod,true);
            for (boolean modulate : new boolean[]{false,true}) {
                p[20]=modulate ? 3 : 0;
                int expected=modulate ? Graphics3DPipelines.TextureBlenders.MODULATE_RGBA.blend(p[7],sample,0) : sample;
                int[] pixels=new int[16]; short[] depth=new short[16]; java.util.Arrays.fill(depth,Short.MAX_VALUE);
                assertTrue(NativeRasterizer.mipmapped(pixels,depth,images,f,p,mf,mp));
                for (int pixel : pixels) assertEquals("LOD="+lod+", modulate="+modulate,expected,pixel);
                for (short z : depth) assertEquals(Short.MAX_VALUE,z);
            }
        }
    }

    private static Texture2D coloredLevels(int w, int h) {
        Texture2D texture=new Texture2D(new Image2D(Image2D.RGBA,w,h,new byte[w*h*4]));
        int[] colors={0x80402010,0xe02080f0,0xffa04080,0xc040e020};
        int count=32-Integer.numberOfLeadingZeros(Math.max(w,h));
        for (int level=0; level<count; level++) java.util.Arrays.fill(texture.getImageForLOD(level).image,colors[level]);
        return texture;
    }

    private static int referenceMix(int a, int b, int weight) {
        int result=0;
        for (int shift=0; shift<32; shift+=8)
            result|=((((a>>>shift)&255)*(256-weight)+((b>>>shift)&255)*weight)/256)<<shift;
        return result;
    }
}
