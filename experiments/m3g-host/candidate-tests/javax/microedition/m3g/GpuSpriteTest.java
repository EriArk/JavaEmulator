/* SPDX-License-Identifier: GPL-3.0-or-later */
package javax.microedition.m3g;

import javax.microedition.lcdui.Image;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public class GpuSpriteTest {
    @Test public void samplingUsesCpuDdaAtFractionalTexelBoundaries() {
        int w=127,h=71; Image2D source=new Image2D(Image2D.RGBA,19,25);
        for(int i=0;i<source.image.length;i++) source.image[i]=0xff000000|i*2713;
        for(float step:new float[]{.1f,.2f,.01f,-.1f,-.3f}) {
            int[] pixels=new int[w*h]; short[] depth=new short[w*h]; java.util.Arrays.fill(depth,(short)32200);
            float[] f={step>0?0:18,step,-.3f,.04f}; int[] p=new int[20];
            p[0]=3; p[1]=124; p[2]=2; p[3]=69; p[8]=1; p[9]=2; p[10]=17; p[11]=20;
            p[13]=500; p[14]=256; p[16]=-1;
            GpuRasterizer.enabled=true;
            assertTrue(GpuRasterizer.sprite(pixels,depth,w,h,source,f,p,0,0,CompositingMode.REPLACE,false));
            for(int y=2;y<69;y++) {
                int ty=Math.max(2,Math.min(21,2+(int)((y+.5f-f[2])*f[3]*20)));
                float u=f[0];
                for(int x=3;x<124;x++,u+=step) {
                    int tx=Math.max(1,Math.min(17,1+(int)u));
                    assertEquals("step="+step+" at "+x+","+y,source.image[ty*19+tx],pixels[y*w+x]);
                }
            }
        }
    }

    @After public void restore() {
        Graphics3D.getInstance().releaseTarget(); GpuRasterizer.close();
        GpuRasterizer.enabled=false; NativeRasterizer.enabled=true;
    }

    @Test public void cropsFlipsProjectionMasksAndFogMatchJava() {
        for(int blend:new int[]{CompositingMode.REPLACE,CompositingMode.ALPHA,CompositingMode.ALPHA_ADD,
                CompositingMode.MODULATE,CompositingMode.MODULATE_X2})
            for(int variant=0;variant<32;variant++) {
                int[] cpu=scene(false,blend,variant),gpu=scene(true,blend,variant);
                // Three overlapping draws accumulate normalized-GL versus integer-CPU rounding.
                // Double multiplication also doubles each earlier rounding error: 2*(1+2+4).
                int tolerance=blend==CompositingMode.REPLACE?0:blend==CompositingMode.MODULATE_X2?14:6;
                for(int i=0;i<cpu.length;i++) channels("blend="+blend+" variant="+variant+" pixel="+i,cpu[i],gpu[i],tolerance);
            }
    }

    @Test public void rawSpritePixelsSupportEveryImageFormat() {
        for(int format=Image2D.ALPHA;format<=Image2D.RGBA;format++) {
            int n=format==Image2D.LUMINANCE_ALPHA?2:format==Image2D.RGB?3:format==Image2D.RGBA?4:1;
            byte[] data=new byte[4*n]; java.util.Arrays.fill(data,(byte)160);
            Sprite3D sprite=new Sprite3D(true,new Image2D(format,2,2,data),new Appearance());
            int[] cpu=simple(sprite,false),gpu=simple(sprite,true);
            assertArrayEquals("format="+format,cpu,gpu);
        }
    }

    @Test public void zeroAlphaDoesNotOccludeLaterSprites() {
        Graphics3D g=Graphics3D.getInstance(); Image2D target=bind(g); g.clear(null); GpuRasterizer.enabled=true;
        Image2D transparent=new Image2D(Image2D.RGBA,1,1);
        Sprite3D front=new Sprite3D(true,transparent,new Appearance()); front.setScale(2,2,1);
        long count=GpuRasterizer.spriteDraws;
        g.render(front,position(-2));
        Sprite3D back=new Sprite3D(true,new Image2D(Image2D.RGB,1,1,new byte[]{32,96,64}),new Appearance());
        back.setScale(2,2,1); g.render(back,position(-3)); g.releaseTarget();
        assertEquals(count+2,GpuRasterizer.spriteDraws);
        assertEquals(0xff206040,target.image[24*64+32]);
    }

    @Test public void mutationAndImageTargetAliasingStayVisible() {
        Image2D image=new Image2D(Image2D.RGBA,1,1);
        image.set(0,0,1,1,new byte[]{64,96,32,-1});
        Sprite3D sprite=new Sprite3D(true,image,new Appearance());
        assertEquals(0xff406020,simple(sprite,true)[24*64+32]);
        long uploads=GpuRasterizer.textureUploads;
        image.set(0,0,1,1,new byte[]{32,64,96,-1});
        assertEquals(0xff204060,simple(sprite,true)[24*64+32]);
        assertEquals(uploads+1,GpuRasterizer.textureUploads);
        Graphics3D g=Graphics3D.getInstance(); Image2D target=bind(g);
        Background bg=new Background(); bg.setColor(0xff408020); g.clear(bg);
        Sprite3D alias=new Sprite3D(false,target,new Appearance());
        long count=GpuRasterizer.spriteDraws; g.render(alias,position(-2)); g.releaseTarget();
        assertEquals(count,GpuRasterizer.spriteDraws);
        assertEquals(0xff408020,target.image[24*64+32]);
        Sprite3D oversized=new Sprite3D(false,new Image2D(Image2D.RGB,2049,1,new byte[2049*3]),new Appearance());
        simple(oversized,false);
        target=bind(g); GpuRasterizer.enabled=true; count=GpuRasterizer.spriteDraws;
        g.render(oversized,position(-2)); g.releaseTarget();
        assertEquals("Oversized sprites must fall back without Texture2D allocation",count,GpuRasterizer.spriteDraws);
    }

    @Test public void invisibleSpritesDoNotFlushResidentFrame() {
        Graphics3D g=Graphics3D.getInstance(); Image target=Image.createImage(64,48);
        g.bindTarget(target.getGraphics(),true,0); camera(g,false); g.clear(null); GpuRasterizer.enabled=true;
        Sprite3D sprite=new Sprite3D(true,new Image2D(Image2D.RGB,1,1,new byte[]{32,96,64}),new Appearance());
        long reads=GpuRasterizer.readbacks,uploads=GpuRasterizer.uploads,count=GpuRasterizer.spriteDraws;
        g.render(sprite,position(-2));
        sprite.setAppearance(null); g.render(sprite,position(-2)); sprite.setAppearance(new Appearance());
        g.render(sprite,position(2)); g.render(sprite,position(-20));
        sprite.setCrop(0,0,0,0); g.render(sprite,position(-2));
        assertEquals(reads,GpuRasterizer.readbacks); assertEquals(uploads+1,GpuRasterizer.uploads);
        assertEquals(count+1,GpuRasterizer.spriteDraws);
        g.releaseTarget(); assertEquals(reads+1,GpuRasterizer.readbacks);
        assertEquals(0xff206040,target.getBitmap().getPixel(32,24));
    }

    private static int[] scene(boolean gpu,int mode,int variant) {
        Graphics3D g=Graphics3D.getInstance(); Image2D target=new Image2D(Image2D.RGBA,64,48);
        g.bindTarget(target,(variant&16)==0,0); g.setViewport(-3,2,64,48); g.setDepthRange(.2f,.8f);
        Background bg=new Background(); bg.setColor(0x80406080); g.clear(bg); camera(g,(variant&1)!=0);
        Appearance style=new Appearance(); CompositingMode blend=new CompositingMode(); blend.setBlending(mode);
        blend.setColorWriteEnable(variant%8!=2); blend.setAlphaWriteEnable(variant%8!=3);
        blend.setDepthTestEnable(variant%8!=4); blend.setDepthWriteEnable(variant%8!=5);
        blend.setAlphaThreshold(variant%8==6?.6f:0); style.setCompositingMode(blend);
        Fog fog=new Fog(); fog.setMode((variant&2)==0?Fog.LINEAR:Fog.EXPONENTIAL);
        fog.setLinear(1,6); fog.setDensity(.2f); fog.setColor(0x905020); style.setFog(fog);
        byte[] data=new byte[19*25*4]; for(int i=0;i<data.length;i++) data[i]=(byte)(i*37+11);
        for(int i=3;i<data.length;i+=20) data[i]=0;
        Sprite3D sprite=new Sprite3D((variant&4)==0,new Image2D(Image2D.RGBA,19,25,data),style);
        sprite.setCrop(-2,1,(variant&8)==0?18:-18,(variant&2)==0?23:-23); sprite.setAlphaFactor(.7f);
        GpuRasterizer.enabled=gpu; NativeRasterizer.enabled=false; long count=GpuRasterizer.spriteDraws;
        Transform t=position(-2); g.render(sprite,t); t.postTranslate(.15f,0,-.5f); g.render(sprite,t);
        t.postTranslate(-.3f,0,.9f); g.render(sprite,t); g.releaseTarget();
        if(gpu) assertEquals(GpuRasterizer.failure,count+3,GpuRasterizer.spriteDraws);
        return target.image;
    }
    private static int[] simple(Sprite3D sprite,boolean gpu) {
        Graphics3D g=Graphics3D.getInstance(); Image2D target=bind(g); g.clear(null);
        GpuRasterizer.enabled=gpu; NativeRasterizer.enabled=false; long count=GpuRasterizer.spriteDraws;
        g.render(sprite,position(-2)); g.releaseTarget();
        if(gpu) assertEquals(GpuRasterizer.failure,count+1,GpuRasterizer.spriteDraws);
        return target.image;
    }
    private static Image2D bind(Graphics3D g) {
        Image2D target=new Image2D(Image2D.RGBA,64,48); g.bindTarget(target,true,0); camera(g,false); return target;
    }
    private static void camera(Graphics3D g,boolean perspective) {
        Camera c=new Camera(); if(perspective)c.setPerspective(60,64f/48,1,10); else c.setParallel(2,64f/48,1,10);
        g.setCamera(c,new Transform());
    }
    private static Transform position(float z) { Transform t=new Transform(); t.postTranslate(0,0,z); return t; }
    private static void channels(String label,int a,int b,int tolerance) {
        for(int shift=0;shift<32;shift+=8) assertTrue(label+" "+Integer.toHexString(a)+" / "+Integer.toHexString(b),
                Math.abs(((a>>>shift)&255)-((b>>>shift)&255))<=tolerance);
    }
}
