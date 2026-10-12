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

`host.gradle` injects `half-hook.java.inc` into the generated, revision-guarded
candidate. The original upstream checkout is never edited. The candidate-only
NDK configuration builds `abyssm3graster` alongside the existing `micro3d` helper,
without linking `javam3g`. Missing native libraries retain the Java path.

Triangle setup, transforms, lighting and scene traversal remain in Java. The
native loop handles untextured or single base-level textures, nearest/linear
sampling, independent repeat/clamp axes, vertex colors, depth, alpha rejection,
write masks, all five framebuffer compositing modes, linear fog and dithering.
Supported texture combinations use replace, RGB/RGBA modulate or alpha operations.
Mipmaps, multiple textures, other texture blends, exponential fog and half-resolution
mode retain Java execution. Sprites also retain their existing Java implementation.

Both paths read/write the same color and depth arrays. Native calls finish
before Java continues, so mixed draws do not require GPU readback or depth
conversion. Setup/buffer validation occurs before drawing; rejected packets
leave buffers untouched and fall back to Java. Array pins are scoped to a
single triangle half. Integer wraparound, float operation order and interpolation
match the Java reference; fast-math and fused multiply-add are disabled.

## Verified on 2026-10-12

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
