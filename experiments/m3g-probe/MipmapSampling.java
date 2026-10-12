/* SPDX-License-Identifier: GPL-3.0-or-later
 * Sampling helpers for the GPL FreeJ2ME-Plus candidate renderer.
 */
package javax.microedition.m3g;

final class MipmapSampling {
    private static final int[] LOG_FRACTION = new int[256];
    static {
        // Q8 LOD = log2(rho squared) / 2. No logarithms in the pixel loop.
        for (int i=0; i<256; i++)
            LOG_FRACTION[i]=(int)(Math.log(1.0+i/256.0)*184.6649652337873);
    }

    static int lod(float s, float t, float sx, float tx, float sy, float ty,
            float wx, float wy, float invW) {
        float dxS=(sx-s*wx)*invW, dxT=(tx-t*wx)*invW;
        float dyS=(sy-s*wy)*invW, dyT=(ty-t*wy)*invW;
        float x=dxS*dxS+dxT*dxT, y=dyS*dyS+dyT*dyT;
        float rho=x>y ? x : y;
        if (!(rho>1)) return 0;
        int bits=Float.floatToRawIntBits(rho);
        return (((bits>>>23)&255)-127)*128 + LOG_FRACTION[(bits>>>15)&255];
    }

    static int mix(int a, int b, int weight) {
        int inv=256-weight;
        int rb=(((a&0x00ff00ff)*inv+(b&0x00ff00ff)*weight)>>>8)&0x00ff00ff;
        int ag=((((a>>>8)&0x00ff00ff)*inv+((b>>>8)&0x00ff00ff)*weight)>>>8)&0x00ff00ff;
        return (ag<<8)|rb;
    }

    static int sample(Texture2D texture, Graphics3D.TextureFilter filter, int unit,
            float s, float t, int lod, boolean linear) {
        Image2D base=texture.getImage();
        int last=31-Integer.numberOfLeadingZeros(Math.max(base.getWidth(),base.getHeight()));
        lod=Math.max(0,Math.min(last<<8,lod));
        int level=linear ? lod>>>8 : (lod+127)>>>8;
        Image2D low=texture.getImageForLOD(level);
        int color=filter.sampleTexture(s*((float)low.getWidth()/base.getWidth()),
                t*((float)low.getHeight()/base.getHeight()),unit,low);
        int weight=lod&255;
        if (!linear || weight==0 || level==last) return color;
        Image2D high=texture.getImageForLOD(level+1);
        int other=filter.sampleTexture(s*((float)high.getWidth()/base.getWidth()),
                t*((float)high.getHeight()/base.getHeight()),unit,high);
        return mix(color,other,weight);
    }
}
