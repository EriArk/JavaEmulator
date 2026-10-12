        if (GpuRasterizer.enabled && !Mobile.halfResM3GRaster && !Mobile.M3GRenderWireframe
                && !doAntiAlias && Mobile.m3gDitheringMode != MODE_FORCE_ENABLE
                && !(Mobile.m3gDitheringMode == MODE_APP_CONTROLLED && (hints & DITHER) != 0)
                && GpuRasterizer.draw(rasterData, depthBuffer, canvasWidth, canvasHeight,
                    trisScreen, renderableTriangles[0], originX + viewx, originY + viewy,
                    originX + viewx + viewClipL, originY + viewy + viewClipT,
                    originX + viewx + viewClipR, originY + viewy + viewClipB,
                    hasColors, defVertColor, perspectiveCorrection, usesDepth, usesDepthWrite,
                    colorWriteMask, currAlphaFactor, alphaThreshold,
                    textures, ACTIVE_TEXTURE_UNITS, texFilter, mipModes,
                    compositingMode.getBlending(), hasDepthOffset ? depthFactor : 0,
                    hasDepthOffset ? depthUnits : 0, fog, parallelFog, parallelFogDepth,
                    this.target instanceof Graphics)) return;
        GpuRasterizer.flush();
        if (hasDepthOffset && usesDepthWrite) GpuRasterizer.cpuDepthWriteWithOffset(depthBuffer);
        if (ru.playsoftware.j2meloader.BuildConfig.M3G_GPU) GpuRasterizer.noteFallback(
                (doAntiAlias?1:0) | ((hints&DITHER)!=0?2:0) | (hasDepthOffset?4:0),
                compositingMode.getBlending(), ACTIVE_TEXTURE_UNITS==0?0:textures[0].getImage().getFormat());
