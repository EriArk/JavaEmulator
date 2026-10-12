        if (GpuRasterizer.enabled && !Mobile.halfResM3GRaster && !Mobile.M3GRenderWireframe
                && !doAntiAlias && Mobile.m3gDitheringMode != MODE_FORCE_ENABLE
                && !(Mobile.m3gDitheringMode == MODE_APP_CONTROLLED && (hints & DITHER) != 0)
                && !hasDepthOffset && ACTIVE_TEXTURE_UNITS <= 1
                && (ACTIVE_TEXTURE_UNITS == 0 || mipModes[0] == null)
                && GpuRasterizer.draw(rasterData, depthBuffer, canvasWidth, canvasHeight,
                    trisScreen, renderableTriangles[0], originX + viewx, originY + viewy,
                    originX + viewx + viewClipL, originY + viewy + viewClipT,
                    originX + viewx + viewClipR, originY + viewy + viewClipB,
                    hasColors, defVertColor, perspectiveCorrection, usesDepth, usesDepthWrite,
                    colorWriteMask, currAlphaFactor, alphaThreshold,
                    ACTIVE_TEXTURE_UNITS == 0 ? null : textures[0],
                    ACTIVE_TEXTURE_UNITS != 0 && texFilter[0] == TextureFilters.BILINEAR,
                    compositingMode.getBlending(), fog, parallelFog, parallelFogDepth,
                    this.target instanceof Graphics)) return;
        GpuRasterizer.flush();
