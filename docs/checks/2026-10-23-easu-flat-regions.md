# EASU Flat Regions - 2026-10-23

## Scope and Availability

Unreleased Java GLES enhancement work. FSR/interpolation remain Debug-only,
opt-in and off by default. No public APK, release asset, tag, Nextcloud upload
or GitHub Actions change. The live release listing was not rechecked in this
checkpoint. Mophun/Symbian rendering and guest CPU/time behavior are unchanged.

The twelve EASU taps share four columns and four rows; their clamped normalized
coordinates are now reused. On hardware renderers, identical RGB values at the
four nearest samples also skip the outer eight samples and filter arithmetic.
The final deringing clamp would force the same result regardless of those outer
samples. This is an exact-color condition, not an approximate flatness threshold.
Edges, gradients, low-contrast changes and equal-luminance/different-color inputs
still run the complete filter. No lower precision or resolution, CPU image scan,
new GPU target or per-game exception.

The [upstream EASU clamp](https://github.com/GPUOpen-Effects/FidelityFX-FSR/blob/a21ffb8f6c13233ba336352bdff293894c706575/ffx-fsr/ffx_fsr1.h#L409-L435)
was consulted. The test reference is the existing AbyssME GLES2 port at
`7ec60672`, not a bit-exact AMD implementation. Attribution and the MIT notice
remain intact; the frozen shader is packaged only in the instrumentation APK.

## Software Renderer Regression Fixed

An early-return candidate passed image comparisons on Retroid but made the
emulator's SwiftShader roughly three times slower. Replacing the early return
with a structured conditional helped flat scenes, but detailed inputs still
regressed. That candidate was not committed; its first full emulator run was
stopped after the performance regression was established, not counted as a pass.

The production shader now has a compile-time flat-region variant. SwiftShader,
llvmpipe, softpipe, Software Rasterizer and a missing renderer string select
branchless evaluation with shared coordinates and identical filter equations.
Hardware renderers select the shortcut. This is a renderer-class workaround,
not an emulator package-name exception. SwiftShader and Retroid are measured;
the other software names have policy tests, not device-performance acceptance.
Other hardware GPU families remain unverified.

## Quality and Checks

`EasuOptimizationTest` compares every output RGBA byte against the frozen shader
on the same GPU, accepting at most one 8-bit channel level of rounding error.
Both the actual pipeline and the forced hardware shortcut are checked on each
renderer, including the software device where that shortcut is normally disabled.

- 35 combinations: solid, tiled, text-like, gradient, seeded noise, one-bit
  differences and equal-luminance red/blue patterns; five upscale shapes.
- Flat 2x2 centers surrounded by contrasting outer samples, padded viewports,
  narrow 1-pixel dimensions and black/white/primary/near-extreme colors.
- Mixed scenes at 176x208, 240x320, 320x240, 360x640 and 480x800, output 720x960.
- Renderer-selection policy; existing pipeline, final-presentation, Canvas
  lifecycle, settings preview, profile and Mophun budget regression tests.

Final Debug app and instrumentation APK builds passed. Fresh JVM run: 54 tests,
zero failures/errors. Lint: zero errors and 400 warnings, not warning-free.
Retroid Pocket Classic: 86 instrumentation tests passed in 40.639 seconds.
Android 15 x86_64 emulator (Google SwiftShader): the same 86 passed in 349.305
seconds. This includes six new EASU checks. No fresh signed/R8 release build.

## Cost Boundary

Paired AB/BA order, 10 warmup pairs and 40 measured pairs per pattern. Both
programs use the same uploaded 240x320 image and 720x960 output target. Each
sample includes draw submission and `glFinish`, but not compilation, upload,
RCAS, motion estimation or window composition. No locked clocks or thermals.
Measurements are content-dependent shader costs, not game FPS or input latency.

Retroid final run (milliseconds):

| Pattern | Reference median / P95 | Optimized median / P95 | Median paired saving |
| --- | ---: | ---: | ---: |
| Solid | 7.19 / 8.48 | 2.05 / 2.36 | 5.12; 40/40 pairs faster |
| 16-pixel tiles | 7.31 / 8.77 | 5.76 / 6.22 | 1.54; 40/40 pairs faster |
| Text-like lines | 7.22 / 7.62 | 4.16 / 4.51 | 2.98; 40/40 pairs faster |
| Gradient | 7.40 / 8.81 | 7.43 / 8.80 | 0.01; 20/40 pairs faster |
| Seeded noise | 7.24 / 8.21 | 7.37 / 8.57 | -0.04; 18/40 pairs faster |

The shortcut benefits flat regions, not every frame. Earlier candidate runs
showed roughly 0.03-0.22 ms paired overhead on detailed patterns even after
coordinate reuse; the final gradient result is not a promise of zero overhead.
Clock/thermal variability and divergence within a GPU execution group matter.
The full-pipeline fine-checker benchmark still costs about 9.90 ms with FSR and
11.92 ms with motion plus FSR; this change does not make all workloads cheap.

With the branchless variant, final SwiftShader median costs were 28.24, 35.36,
26.38, 34.56 and 26.57 ms for the same five patterns, versus 34.10, 41.10, 33.19,
40.66 and 33.07 ms for the frozen shader. The earlier multi-fold slowdown is
removed. Host-dependent software timing is not handheld-performance evidence,
and some P95 values still increased.

## Installed Preview and Artifact

On Retroid's final Debug APK, Bounce Tales was opened from the library. FSR at
50% sharpness and interpolation were enabled through Try changes. Its menu and
first level introduction rendered with the effects active, and ADB A-button
input reached the level. The preview expired without keeping profile changes;
the subsequent dialogue displayed with the original rendering again. Captures
were inspected. This is not a sustained gameplay FPS, audio, physical-controller
or end-to-end latency assessment.

Local APK: `app/build/outputs/apk/fdroid/debug/AbyssME-0.4.0-beta.3-r1-debug.apk`

SHA-256: `61029c016876842e9d910ae228e7a7f2590852af4c2e8915b17fc30acb7e3671`

The reused Debug filename is not the published revision APK. ZIP inspection
confirmed that the frozen reference shader exists in the test APK, not the app
APK. Ignored evidence: `build/easu-renderer-build.txt`,
`build/easu-renderer-retroid.txt`, `build/easu-renderer-emulator.txt`,
`build/easu-final-enabled.png`, `build/easu-final-preview.png`,
`build/easu-final-level.png`, `build/easu-final-next.png`.
Earlier candidate measurements are in `build/easu-flat-*`, `build/easu-uv-*`,
`build/easu-final-*.txt` and `build/easu-structured-*`.
If local artifacts are missing, treat these as dated maintainer observations,
not public attached artifacts. No commercial game payload or capture is added
to the repository in this checkpoint.
