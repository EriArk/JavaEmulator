# Fused Sharpening / Presentation - 2026-10-23

## Availability and Scope

Unreleased Debug-only Java GLES enhancement work. Effects remain opt-in and off
by default. The public APK, tags, release assets, Nextcloud and GitHub Actions
are unchanged; the live release listing was not rechecked. No games or firmware
are included. Mophun/Symbian presentation and CPU behavior are unchanged.

Canvas now requests the unsharpened EASU output and applies the existing RCAS
shader directly in the final screen draw. This removes the separate output-sized
sharpening target and its copy. At 720x960, that target contains 2,764,800 bytes
(about 2.64 MiB) of RGBA storage, excluding driver overhead. No guest-resolution
reduction, precision downgrade, new shader equations or CPU image copy.

Native-size padded sources are sampled with their viewport/texture dimensions;
minification, disabled FSR and zero sharpness retain their bypasses. The normal
shader is restored after drawing so surface callbacks address the right uniforms.
New uploads, effect changes and history reset disarm stale deferred sharpening.
The previous texture-output path remains available for pixel/cost comparisons.

Completed EASU output is still cached. RCAS runs on final presentation even for
an idle redraw; this avoids depending on discarded back-buffer contents, but
does not promise that every idle redraw has the same cost as the old copy-only
path. There is no new continuous redraw loop.

## Quality and Regression Checks

`EnhancementPresentationTest` compares fused output with the retained reference
on the same GPU, reading every output RGBA byte. Allowed channel error is at most
1/255, accounting for removal of an intermediate RGBA8 quantization. This is
not an AMD-reference or artistic-quality certification.

- 48 combinations of native, fractional, integer-upscale and smaller output;
  0/50/100 sharpness and four rotations, including a padded source and border.
- Four rotated intermediate-frame comparisons with image-based motion enabled.
- Repeated/cached presentation stability, no sharpening-target allocation on
  the fused path, and disabling/reset/new-upload state invalidation.
- Existing Canvas tests cover actual surfaces, colored corners, screenshot
  readback, context recreation, live resizing and guest-buffer lock separation.

The final build passed 80 checks on Retroid Pocket Classic. The initial candidate
passed 80 checks on the Android 15 x86_64 emulator; after the paired benchmark,
cached-output assertions and removal of an unused helper parameter, the final
build passed seven targeted emulator checks (all five presentation tests plus
Canvas rotation/resize and guest-buffer/submission checks). Build and lint passed;
54 JVM tests passed, lint has zero errors and 400 warnings. No fresh signed/R8
release build. Debug is installed separately from the signed app.

## Paired Retroid Measurement

Same synthetic 240x320 moving color pattern, 720x960 final FBO, strength 50.
Two independent pipelines use identical input and timestamps, alternate AB/BA
order, warm up for ten pairs and measure forty pairs with upload, processing,
final drawing and `glFinish`. Frequencies/temperature are not locked.

| Path | Reference median / P95 | Fused median / P95 | Median paired saving |
| --- | ---: | ---: | ---: |
| FSR | 10.98 / 11.76 ms | 10.57 / 12.03 ms | 0.34 ms; 28/40 pairs faster |
| Motion + FSR | 13.37 / 13.84 ms | 13.01 / 13.46 ms | 0.41 ms; 29/40 pairs faster |

The gain is modest and not uniform: the FSR-only P95 did not improve. Earlier
sequential runs gave mixed results for combined motion/FSR, so they are not
used to claim a speedup. These are offscreen pipeline measurements on Retroid,
not sustained game FPS, physical-input latency, battery or window-compositor
cost. Emulator timing is not handheld performance evidence. EASU remains the
dominant optimization target; fusion does not make FSR inexpensive.

## Artifact and Evidence

Local Debug APK: `app/build/outputs/apk/fdroid/debug/AbyssME-0.4.0-beta.3-r1-debug.apk`

SHA-256: `a03a9da53b5e847266905c3011dfd9285b0b767ae4e97fd759b843de52ab11ce`

The shared development filename does not identify the published release APK.
Ignored logs: `build/enhancement-fused-build.txt`,
`build/enhancement-fused-final-build.txt`, `build/enhancement-fused-retroid.txt`,
`build/enhancement-fused-retroid-final.txt`, `build/enhancement-fused-emulator.txt`,
`build/enhancement-fused-emulator-final.txt`.
Treat missing local logs as dated maintainer observations, not attached artifacts.
GPU fixtures and paired benchmark are committed in the instrumentation suite.

No new real-game or physical-controller acceptance is claimed in this checkpoint.
The prior [Bounce Tales preview](2026-10-23-enhancement-upload.md) is separate
evidence for the preceding implementation. Fast-motion/occlusion artifacts,
sustained cost and real-game visual acceptance remain open before public release.
