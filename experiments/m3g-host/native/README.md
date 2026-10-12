# Candidate Native Rasterizer

Research branch only. Ordinary builds and `main` retain their existing engine.
This is CPU rasterization, not a new GPU backend or a public release.

## Source and Integration

`raster.cpp` is a C++ adaptation of the scanline and pixel operations in
FreeJ2ME-Plus `Graphics3D.java` and `Graphics3DPipelines.java`, pinned at
[`baca76a4dda6f30abac67fb4a3db009eed05f91c`](https://github.com/TASEmulators/freej2me-plus/tree/baca76a4dda6f30abac67fb4a3db009eed05f91c).
It and the Java bridge/hook are GPL-3.0-or-later. The candidate's existing
upstream notices and GPL license asset still apply. No legacy EPL renderer
code or code from the inspected KEmulator/GLES2 reference is included here.

`host.gradle` injects triangle and sprite hooks into the generated, revision-guarded
candidate. The original upstream checkout is never edited. The candidate-only
NDK configuration builds `abyssm3graster` alongside the existing `micro3d` helper,
without linking `javam3g`. Missing native libraries retain the Java path.

Triangle setup, transforms, lighting and scene traversal remain in Java. The
native loop handles untextured or up to four textures, nearest/linear
sampling, independent repeat/clamp axes, vertex colors, depth, alpha rejection,
write masks, all five framebuffer compositing modes, linear/exponential fog and
dithering. All current texture blenders are supported, including ADD, BLEND and
DECAL for the applicable image formats. Each layer keeps its own coordinates,
transform, filtering, wrapping and blend color; sparse source slots keep API order.
Mipmap selection/sampling and Sprite3D pixels also run natively. Sprite setup,
crop/projection/clipping and fog preparation stay in Java. Half-resolution mode
retains Java execution. FILTER_LINEAR blends adjacent mip levels; with bilinear
image filtering this is trilinear sampling, not spatial LOD dithering.

Both paths read/write the same color and depth arrays. Native calls finish
before Java continues, so mixed draws do not require GPU readback or depth
conversion. Setup/buffer validation occurs before drawing; rejected packets
leave buffers untouched and fall back to Java. Array pins are scoped to a
single triangle half or sprite. Integer wraparound, float operation order and interpolation
match the Java reference; fast-math and fused multiply-add are disabled.
Repeated image references are pinned once. Target/texture aliasing is rejected
before drawing; this permits restricted output pointers inside the raster loop.
Single-layer calls avoid JNI object-array lookups and specialize common blends.
The hook is the packet contract: 51 floats and 45 integers, with common setup
followed by six values per extra texture. Java reference slots are cleared after
each call, including fallback, rather than retaining a second copy of image state.
Mipmapped calls add 18 floats (four derivatives per unit, two W derivatives),
eight integers (level count/filter per unit), and at most 64 borrowed image arrays.
Level dimensions follow independent width/height halving, down to 1x1. The
base-level fast path does not build or pin a mip chain. Sprite packets use four
floats and 20 integers; `sprite-hook.java.inc` describes the layout.

## Geometry and Lighting Preparation: 2026-10-12

The shared generated-source adaptations fix two correctness problems and avoid
repeated preparation work, without changing the pinned upstream checkout:

- Packed `VertexArray` transforms now process only the declared vertices. The
  old two-pass path transformed the entire oversized scratch buffer, modified
  its unused tail, and rejected otherwise sufficient non-multiple-of-four output
  capacity. The direct path preserves the original float operation order.
- Lighting colors are cached per vertex **and normal orientation**, within one
  draw only. Previously, a shared vertex could incorrectly reuse the preceding
  triangle's front-face color on a back face. The new cache also handles
  nonadjacent shared vertices and flat-shading provoking colors. Draw epochs
  invalidate material/light/transform changes, including epoch rollover.
- The normal matrix is copied once per lit draw, not once per triangle.
  Unlit geometry no longer prepares unused light transforms.
- Dense texture-coordinate scratch survives gaps in API texture slots and
  temporary untextured draws. A separate active view still clears inactive
  coordinates and texture-object references; retained buffers cannot accidentally
  enable texturing. Capacity is reused, not allocated on every sparse draw.

Final non-debuggable research Handheld and Phone APKs pass **81 checks, zero
skips**, respectively on Retroid arm64/Android 14 and emulator x86_64/Android 15.
Seven new regressions cover signed byte/short 2D/3D transforms and output tails,
invalid inputs, opposite faces, smooth/flat/color-tracked reuse, invalidation,
sparse texture-buffer identity and unlit-to-lit transitions. Standalone
`:probe:assembleDebug` also builds with the same generated-source adaptations.
Asphalt 3 reaches a moving race on the final Retroid APK; two successive local
captures show the car, track and HUD advancing. Sound was disabled for this
short check; it does not establish audio, complete-game or physical-controller
acceptance. Captures: `build/geometry-asphalt-{a,b}.png` under the host harness.

Same-APK Retroid measurements with native rasterization and thermal status 0
at both ends compare the new cache enabled/disabled, **not the old adjacent-only
cache**. The new grid has 1,089 shared vertices, 2,048 triangles, ambient,
directional, omni and spot lights, and a specular material:

| Scene | Cache off ms/frame | Cache on ms/frame | Shaded vertices/frame off / on |
| --- | ---: | ---: | ---: |
| Lighting grid 240x320 | 6.276 | 5.315 | 4,992 / 891 |
| Lighting grid 320x240 | 7.379 | 5.955 | 6,144 / 1,089 |

All **20** cache-on/off scene CRCs match; the **18** existing workloads also
retain the preceding trilinear checkpoint's exact pixels. The grid's frame cost
is 15-19% lower than fully uncached lighting, not a game-FPS or legacy-GLES parity
claim. The earlier adjacent-only implementation was not benchmarked on this new
grid. Rasterization still dominates many scenes; software/native-CPU rendering
does not establish performance parity with the legacy GPU renderer.

Reproduce with `benchmark.ps1 -Raster native -LightingCache on` / `off`, using
fresh run labels. Maintainer-local evidence is in
`build/benchmarks/geometry-cache-{on,off}` and
`build/evidence/{device}-candidate[-phone]` under `experiments/m3g-host`.
These are ignored diagnostics, not published APKs or bundled games.

```text
E6FFBD7C39019EBE2BBE07D1A40D4E31FB8E27C93771F5A756BB2043A89A9957 Handheld
D2C8E864C953551981EA772FB1B0BCBF857BD4B1F3FE637DC208837DD68E8BF2 Handheld tests
C9A3EDF60C94394627309ED6DBFF00F881651CF600C83C6C559AA3495F140D82 Phone
5C6879F5523DC58BBA0B2115793FE82F7866A1C31340BA6F35F62CFA833D156D Phone tests
```

## Trilinear Filtering: 2026-10-12

Replaced the spatial mip-level switch with weighted interpolation in Java and
native rendering. Raw samples, including alpha, are mixed before the texture
blend is applied once. Image filtering remains independent: nearest image
filtering takes one texel from each level, bilinear takes four from each.
The [OpenGL ES 1.1 specification, texture minification](https://registry.khronos.org/OpenGL/specs/es/1.1/es_full_spec_1.1.pdf)
describes this adjacent-level interpolation; the
[JSR-184 API specification, Texture2D](https://citeseerx.ist.psu.edu/document?doi=029a9282bfebe3f8d2c10567fcbff19fea0bad75&repid=rep1&type=pdf)
defines the independent level and image filters. No legacy EPL implementation
was copied for this change.

Fixed level selection too: both filters use the largest projected derivative,
not the determinant/area that could collapse to zero on narrow projections.
Nearest level filtering now rounds to the closest level instead of truncating.
LOD has Q8 precision with a 256-entry logarithm lookup; the analytical-reference
test bounds error to two Q8 steps over its sampled range. This is finite-precision
isotropic filtering, not anisotropic filtering or a claim of exact GPU pixels.
Native level dimensions and coordinate scales are prepared before the pixel loop.

Final Retroid arm64 Handheld and x86_64 Phone emulator each pass **74 checks,
zero skips**. New independent regressions cover collapsed derivatives, analytic
LOD values, interpolation weights/alpha, one-pixel-wide rectangular levels,
level clamping, and 130 native packets with known expected color before/after
modulation. The earlier 64 layered mip scenes and all shared checks still pass.
Standalone `:probe:assembleDebug` also builds with the shared helper/injection.
This batch does not add a new real-game or physical-controller acceptance claim.

All 18 same-APK Java/native benchmark CRCs match; thermal status is 0 at both
ends. Fourteen non-mip workloads also retain their previous checkpoint CRCs.
Changed mipmap pixels are intentional, not a parity claim against old dithering.

| Scene | Java ms/frame | Native ms/frame |
| --- | ---: | ---: |
| Mip nearest 240x320 | 6.434 | 5.003 |
| Mip linear 240x320 | 7.822 | 6.286 |
| Mip nearest 320x240 | 4.969 | 4.032 |
| Mip linear 320x240 | 6.345 | 5.017 |

Trilinear sampling costs more than the former one-level approximation
(4.666/3.821 ms in the previous checkpoint). Precomputing scales reduced this
batch's first native result from 6.714/5.315 ms to 6.286/5.017 ms without changing
pixels. The final mipmap workloads are 1.23-1.29x faster than current Java;
this is not game FPS or performance parity with legacy GLES.

Local evidence: `build/benchmarks/trilinear-{precomputed-native,java}` and
`build/evidence/{device}-candidate[-phone]` under `experiments/m3g-host`.
Research APK SHA-256 (not published release binaries):

```text
B2F690A0A2872D4D2B2284FAED9D43B3343D419EEF684BB9C5591FF8BF70B5EA Handheld
65E8BE49BA4DEE40784F84F44E581CCE27036806AF28F62F12473EDFADF650B5 Handheld tests
F8206659C19C600651520365A83D9F0097A7E6F9717F44DC62172807A9A965DD Phone
E2CEE31944C8AAACFCF0FF8D5417A3CA773E7DFBA2B730C87A075A7BB0F096DD Phone tests
```

## Mipmaps and Sprites: 2026-10-12

Final Handheld Retroid arm64 and Phone emulator x86_64 each pass **71 checks,
zero skips**. Added exact comparisons for 64 mipmapped layered scenes and 160
sprite combinations: projection, scaled/unscaled mode, signed crop, NPOT images,
alpha, fog, depth and write masks. Successful native draw counters are required.
The mixed-path regression now deliberately exercises half-resolution Java
drawing between native draws. Malformed mip/sprite packets leave buffers intact.

Fixed two shared Java/native mipmap problems rather than preserving them:
- Sampling no longer discards fractional coordinates or derives both axes from
  the image width. Bilinear magnification now matches base-level filtering;
  narrow, rectangular and NPOT levels keep their own coordinate scale.
- `Image2D.set`, internal pixel writes and release of an Image2D render target
  invalidate cached smaller images. Mipmaps rebuild lazily on the next LOD use.
  Replacing the base image is also covered. The pinned upstream is unchanged.

Same-APK non-debuggable Retroid measurements, thermal status 0 at both ends,
retain exact CRCs in all **18** scenes. New workloads:

| Scene | Java ms/frame | Native ms/frame |
| --- | ---: | ---: |
| Mip nearest 240x320 | 6.034 | 4.745 |
| Mip linear 240x320 | 6.458 | 4.666 |
| Sprites 240x320 | 3.568 | 2.157 |
| Mip nearest 320x240 | 4.952 | 3.828 |
| Mip linear 320x240 | 4.993 | 3.821 |
| Sprites 320x240 | 2.787 | 1.771 |

This is roughly 1.27-1.38x for mipmaps and 1.57-1.65x for the sprite scenes,
not a measured game-FPS improvement or performance parity with legacy GLES.
The twelve previous workloads also retain exact pixels; single-texture native
cost is 1.73-2.79 ms in this run. Full replacement/release acceptance remains open.
Asphalt 3 reaches a running Retroid race on this final APK, with successive
gameplay captures. This short check is not audio or complete-game acceptance.

Local reproducible evidence: `build/benchmarks/mip-sprite-{java,native}` and
`build/evidence/{device}-candidate[-phone]`, relative to `experiments/m3g-host`.
These generated diagnostics are ignored, not required source assets. APK SHA-256:

```text
D2E40E927FFAD99A094EA22555994B7B4CEF457B4DAA857FD8955DD36D0C35F4 Handheld
B2A889FD4AB53478EB77E76549FB5923D8E5E0EE296F0FE97743D12177B5FDA0 Handheld tests
8C49E55BC3DDCD66D869017C2836B4C3C7A01274D066B72E78EE19F6048DAF13 Phone
ECB609D5704A3A7BF5FF82B1A714D7AE3856ABFBD18C37109381CC7532F85A00 Phone tests
```

## Layered Textures and Fog: 2026-10-12

Final Handheld Retroid arm64 and Phone emulator x86_64 each pass **66 checks,
zero skips**. New comparisons cover 400 combinations of 1-4 layers, five blend
modes, five image formats, both camera types, filters and fog modes; 12 more
cover shared image arrays. Existing 40 pipeline combinations, eight mixed
native/Java scenes and atomic malformed-packet rejection still pass. Mipmap
selection, rather than exponential fog, now exercises the Java fallback.
Deep: Submarine Odyssey reaches the first playable objective on Retroid; its
3D models/effects/HUD render, direction input turns the craft, and the game's
pause/resume returns to gameplay. This is not audio or complete-game acceptance.

Fixed zero-density exponential fog tinting in both triangle and sprite paths.
It now bypasses fog entirely in both Java and native execution. This also applies
to standalone candidate preparation; the pinned upstream checkout is unchanged.

Same-APK non-debuggable comparisons on Retroid retain exact pixels in all 12
benchmark scenes (thermal status 0 at both ends). Newly accelerated scenes:

| Scene | Java ms/frame | Native ms/frame |
| --- | ---: | ---: |
| Exponential fog 240x320 | 4.712 | 2.566 |
| Exponential fog 320x240 | 3.648 | 2.155 |
| Four textures 240x320 | 9.349 | 4.982 |
| Four textures 320x240 | 7.268 | 3.980 |

Simple single-layer scenes cost 1.71-2.75 ms here versus 1.69-2.56 ms in the
initial checkpoint below. Specialized common loops/direct JNI reduce the
first expanded implementation's overhead, but do not establish no-regression
parity with the narrower implementation. Legacy GLES remains faster. These
are synthetic render costs, not game FPS or complete replacement acceptance.

Application SHA-256: `a2e63395a14982df14ea074bdb360d5639901f680bf732484df531c086ebd2bf`.
Test APK SHA-256: `9f39f5af39fe4b477b9d108a7bc48044f0bd65ca4904a6214f0420a6668572f7`.
Maintainer-local raw results: `../build/benchmarks/layered-native-direct` and
`../build/benchmarks/layered-java-final`; ignored outputs are not published.
The benchmark explicitly skips the four-layer scenes on engines advertising
fewer than four texture units, rather than silently measuring a smaller workload.

## Initial Single-Texture Checkpoint: 2026-10-12

Handheld arm64 on Retroid Pocket Classic and Phone x86_64 on the Android 15
emulator each pass **63 checks, zero skips**. Native-specific checks compare exact
pixels for 40 state combinations, eight mixed Java/native scenes and malformed
packets. They require actual native draws, not merely a loaded library.
Shared regressions cover both mixed-axis wrap directions with both filters.
Asphalt 3 reaches and displays a moving race on the Retroid candidate.
These are Java-only research builds, not new combined-Mophun acceptance.

The existing benchmark compares Java and native paths in the **same non-debuggable
APK**, with identical eight scene CRCs and thermal status 0 at each run's start/end:

| Scene | Java ms/frame | Native ms/frame |
| --- | ---: | ---: |
| RGB nearest 240x320 | 4.264 | 2.203 |
| RGB linear 240x320 | 4.674 | 2.564 |
| RGBA alpha 240x320 | 4.342 | 2.145 |
| Lit fog 240x320 | 4.628 | 2.517 |
| RGB nearest 320x240 | 3.234 | 1.687 |
| RGB linear 320x240 | 3.581 | 2.060 |
| RGBA alpha 320x240 | 3.366 | 1.771 |
| Lit fog 320x240 | 3.577 | 2.045 |

Application SHA-256: `d16ec2f4033a851216da60335b06a5ee121d91ee188a6a9db39624a8672d8c9a`.
Test APK SHA-256: `1d04b01027754b91d5fffe26bf1b5884a7665de31c01aabf383fa81b3b8afa5f`.
Maintainer-local raw results: `../build/benchmarks/native-raster-java-a` and
`../build/benchmarks/native-raster-final`; these ignored outputs are not published.
Reproduce using `benchmark.ps1 -Raster java` / `-Raster native` with fresh run labels.

The 1.74-2.02x improvement is synthetic render cost, **not game FPS**. The earlier
legacy GLES baseline (0.85-0.94 ms) remains faster. Native coverage, long-session
behavior and full replacement readiness remain incomplete. No quality setting
was lowered, no production engine switched, and no release/license transition
is implied by this checkpoint.
