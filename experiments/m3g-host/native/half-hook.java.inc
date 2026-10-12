        if (NativeRasterizer.available() && !Mobile.halfResM3GRaster
                && (!hasFog || fogMode == Fog.LINEAR)
                && (!hasTexture || (ACTIVE_TEXTURE_UNITS == 1 && mipModes[0] == null))) {
            int nativeBlend = -1;
            if (!hasTexture || texBlenders[0] == null) nativeBlend = 0;
            else if (texBlenders[0] == Graphics3DPipelines.TextureBlenders.REPLACE_RGB) nativeBlend = 1;
            else if (texBlenders[0] == Graphics3DPipelines.TextureBlenders.MODULATE_RGB) nativeBlend = 2;
            else if (texBlenders[0] == Graphics3DPipelines.TextureBlenders.MODULATE_RGBA) nativeBlend = 3;
            else if (texBlenders[0] == Graphics3DPipelines.TextureBlenders.ALPHA_MUL) nativeBlend = 4;
            else if (texBlenders[0] == Graphics3DPipelines.TextureBlenders.REPLACE_ALPHA) nativeBlend = 5;
            int nativeComp = -1;
            if (compBlender == null) nativeComp = 0;
            else if (compBlender == Graphics3DPipelines.CompositingBlenders.ALPHA) nativeComp = 1;
            else if (compBlender == Graphics3DPipelines.CompositingBlenders.ALPHA_ADD) nativeComp = 2;
            else if (compBlender == Graphics3DPipelines.CompositingBlenders.MODULATE) nativeComp = 3;
            else if (compBlender == Graphics3DPipelines.CompositingBlenders.MODULATE_X2) nativeComp = 4;
            Image2D nativeImage = hasTexture ? textures[0].getImage() : null;
            if (nativeBlend >= 0 && nativeComp >= 0
                    && (nativeImage == null || nativeImage.image != rasterData)) {
                float[] f = nativeState;
                f[0]=xL; f[1]=xR; f[2]=zL; f[3]=pwL;
                f[4]=dxL_dy; f[5]=dxR_dy; f[6]=dzL_dy; f[7]=dpwL_dy;
                f[8]=zStep; f[9]=pwStep; f[10]=depthOffset;
                f[11]=sL[0]; f[12]=tL[0]; f[13]=dsL_dy[0]; f[14]=dtL_dy[0];
                f[15]=stepS[0]; f[16]=stepT[0];
                f[17]=triScreen.xA(); f[18]=triScreen.yA();
                f[19]=aStepX; f[20]=rStepX; f[21]=gStepX; f[22]=bStepX;
                f[23]=aStepY; f[24]=rStepY; f[25]=gStepY; f[26]=bStepY;
                f[27]=fogFarNorm; f[28]=invFogDiv;
                f[29]=fogEyeA; f[30]=fogEyeDx; f[31]=fogEyeDy;
                int[] p = nativeFlags;
                p[0]=yStart; p[1]=yEnd; p[2]=rowIdx; p[3]=canvasWidth;
                p[4]=viewClipL; p[5]=viewClipR;
                p[6]=(hasColors?1:0) | (hasTexture?2:0) | (usesDepth?4:0)
                        | (usesDepthWrite?8:0) | (doPerspective?16:0)
                        | (hasFog?32:0) | (parallelFog?64:0) | (doDither?128:0);
                p[7]=defVertColor; p[8]=triScreen.colorA();
                p[9]=stepA; p[10]=stepR; p[11]=stepG; p[12]=stepB;
                p[13]=alphaThreshold; p[14]=currAlphaFactor; p[15]=colorWriteMask;
                p[16]=hasTexture?nativeImage.getWidth():0;
                p[17]=hasTexture?nativeImage.getHeight():0;
                p[18]=(texRepeatS[0]?1:0) | (texRepeatT[0]?2:0);
                p[19]=texFilter[0]==TextureFilters.BILINEAR?1:0;
                p[20]=nativeBlend; p[21]=nativeComp;
                p[22]=fogRB; p[23]=fogG; p[24]=Mobile.m3gPerspCorrSubFactor;
                if (NativeRasterizer.draw(rasterData, depthBuffer,
                        hasTexture?nativeImage.image:null, f, p)) return;
            }
        }
