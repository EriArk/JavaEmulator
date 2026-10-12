        // SPDX-License-Identifier: GPL-3.0-or-later
        if (NativeRasterizer.available() && !Mobile.halfResM3GRaster && spr.image != rasterData) {
            float[] f = nativeSpriteState;
            f[0]=uStart; f[1]=uStep; f[2]=sy0; f[3]=invSpanY;
            int[] p = nativeSpriteFlags;
            p[0]=pixL; p[1]=pixR; p[2]=pixT; p[3]=pixB;
            p[4]=(originY + viewy + pixT) * canvasWidth + originX + viewx;
            p[5]=canvasWidth; p[6]=spr.getWidth(); p[7]=spr.getHeight();
            p[8]=isectX; p[9]=isectY; p[10]=isectW; p[11]=isectH;
            p[12]=(depthTest?1:0) | (depthWrite?2:0) | (flipY?4:0) | (fog!=null?8:0);
            p[13]=ndcZ; p[14]=alphaFactor; p[15]=alphaThreshold;
            p[16]=colorWriteMask; p[17]=NativeRasterizer.compositeBlend(compBlender);
            p[18]=fogRB | (fogG << 8); p[19]=intFogFactor;
            if (NativeRasterizer.sprite(rasterData, depthBuffer, spr.image, f, p)) return;
        }
