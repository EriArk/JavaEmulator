    // SPDX-License-Identifier: GPL-3.0-or-later
    private static void applyTextureUnit(int unit, float invPw) {
        float s=curS[unit]*invPw, t=curT[unit]*invPw;
        int color;
        if (mipModes[unit]==null) {
            color=texFilter[unit].sampleTexture(s,t,unit,textures[unit].getImage());
        } else {
            int lod=MipmapSampling.lod(s,t,sStepX[unit],tStepX[unit],sStepY[unit],tStepY[unit],dwdx,dwdy,invPw);
            color=MipmapSampling.sample(textures[unit],texFilter[unit],unit,s,t,lod,
                    mipModes[unit]==Graphics3DPipelines.MipmapModes.LINEAR);
        }
        // Blend once, after level interpolation, including alpha and DECAL.
        paintPixel=texBlenders[unit]==null ? color
                : texBlenders[unit].blend(paintPixel,color,textures[unit].getBlendColor());
        curS[unit]+=stepS[unit]; curT[unit]+=stepT[unit];
    }

    static class TexturingModes {
        static final TexturingMode NO_UNIT = new TexturingMode() {
            public void applyTexture(float invPw, int x, int y) { }
        };
        static final TexturingMode SINGLE_UNIT = new TexturingMode() {
            public void applyTexture(float invPw, int x, int y) { applyTextureUnit(0,invPw); }
        };
        static final TexturingMode MULTI_UNIT = new TexturingMode() {
            public void applyTexture(float invPw, int x, int y) {
                for (int i=0; i<ACTIVE_TEXTURE_UNITS; i++) applyTextureUnit(i,invPw);
            }
        };
    }
