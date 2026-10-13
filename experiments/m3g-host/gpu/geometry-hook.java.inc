        if (GpuGeometry.enabled && GpuRasterizer.enabled && !Mobile.halfResM3GRaster
                && !Mobile.M3GRenderWireframe && !doAntiAlias
                && Mobile.m3gDitheringMode != MODE_FORCE_ENABLE
                && !(Mobile.m3gDitheringMode == MODE_APP_CONTROLLED && (hints & DITHER) != 0)
                && GpuGeometry.draw(vertices, triangles, appearance, transform, currCamTransInv,
                    projectionMatrix, currLights, currLightTrans, scope, perspectiveCorrection,
                    fog, projType == Camera.PARALLEL, rasterData, depthBuffer, canvasWidth, canvasHeight,
                    originX + viewx, originY + viewy, vieww, viewh,
                    originX + viewx + viewClipL, originY + viewy + viewClipT,
                    originX + viewx + viewClipR, originY + viewy + viewClipB,
                    near, far, this.depthEnabled && compositingMode.isDepthTestEnabled() && isDepthBufferEnabled(),
                    currAlphaFactor, this.target instanceof Graphics)) return;

