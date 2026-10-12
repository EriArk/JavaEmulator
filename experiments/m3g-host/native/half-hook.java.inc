        if (NativeRasterizer.available() && !Mobile.halfResM3GRaster) {
            int units = hasTexture ? ACTIVE_TEXTURE_UNITS : 0;
            boolean supported = units <= nativeImages.length;
            for (int i = 0; supported && i < units; i++) {
                Image2D image = textures[i].getImage();
                int blend = NativeRasterizer.textureBlend(texBlenders[i]);
                supported = mipModes[i] == null && blend >= 0 && image.image != rasterData;
                if (!supported) break;
                nativeImages[i] = image.image;
                int fi = i == 0 ? 11 : 33 + (i - 1) * 6;
                int pi = i == 0 ? 16 : 27 + (i - 1) * 6;
                nativeState[fi]=sL[i]; nativeState[fi+1]=tL[i];
                nativeState[fi+2]=dsL_dy[i]; nativeState[fi+3]=dtL_dy[i];
                nativeState[fi+4]=stepS[i]; nativeState[fi+5]=stepT[i];
                nativeFlags[pi]=image.getWidth(); nativeFlags[pi+1]=image.getHeight();
                nativeFlags[pi+2]=(texRepeatS[i]?1:0) | (texRepeatT[i]?2:0);
                nativeFlags[pi+3]=texFilter[i]==TextureFilters.BILINEAR?1:0;
                nativeFlags[pi+4]=blend;
                nativeFlags[i == 0 ? 26 : pi+5]=textures[i].getBlendColor();
            }
            int nativeComp = -1;
            if (compBlender == null) nativeComp = 0;
            else if (compBlender == Graphics3DPipelines.CompositingBlenders.ALPHA) nativeComp = 1;
            else if (compBlender == Graphics3DPipelines.CompositingBlenders.ALPHA_ADD) nativeComp = 2;
            else if (compBlender == Graphics3DPipelines.CompositingBlenders.MODULATE) nativeComp = 3;
            else if (compBlender == Graphics3DPipelines.CompositingBlenders.MODULATE_X2) nativeComp = 4;
            if (supported && nativeComp >= 0) {
                float[] f = nativeState;
                f[0]=xL; f[1]=xR; f[2]=zL; f[3]=pwL;
                f[4]=dxL_dy; f[5]=dxR_dy; f[6]=dzL_dy; f[7]=dpwL_dy;
                f[8]=zStep; f[9]=pwStep; f[10]=depthOffset;
                f[17]=triScreen.xA(); f[18]=triScreen.yA();
                f[19]=aStepX; f[20]=rStepX; f[21]=gStepX; f[22]=bStepX;
                f[23]=aStepY; f[24]=rStepY; f[25]=gStepY; f[26]=bStepY;
                f[27]=fogFarNorm; f[28]=invFogDiv;
                f[29]=fogEyeA; f[30]=fogEyeDx; f[31]=fogEyeDy;
                f[32]=fogDensity;
                int[] p = nativeFlags;
                p[0]=yStart; p[1]=yEnd; p[2]=rowIdx; p[3]=canvasWidth;
                p[4]=viewClipL; p[5]=viewClipR;
                p[6]=(hasColors?1:0) | (hasTexture?2:0) | (usesDepth?4:0)
                        | (usesDepthWrite?8:0) | (doPerspective?16:0)
                        | (hasFog?32:0) | (parallelFog?64:0) | (doDither?128:0)
                        | (fogMode==Fog.EXPONENTIAL?256:0);
                p[7]=defVertColor; p[8]=triScreen.colorA();
                p[9]=stepA; p[10]=stepR; p[11]=stepG; p[12]=stepB;
                p[13]=alphaThreshold; p[14]=currAlphaFactor; p[15]=colorWriteMask;
                p[21]=nativeComp;
                p[22]=fogRB; p[23]=fogG; p[24]=Mobile.m3gPerspCorrSubFactor;
                p[25]=units;
                boolean drawn;
                try { drawn = NativeRasterizer.draw(rasterData, depthBuffer, nativeImages, f, p); }
                finally { java.util.Arrays.fill(nativeImages, null); }
                if (drawn) return;
            } else {
                java.util.Arrays.fill(nativeImages, null);
            }
        }
