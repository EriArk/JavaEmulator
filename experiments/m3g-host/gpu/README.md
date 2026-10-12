# Experimental GLES3 Rasterization

Research branch only, **off by default**, not a production engine switch.
The candidate's normal native-CPU/Java paths remain available. No game-specific
patches, legacy EPL renderer source or new public release binaries are included.

## Implemented Scope

`GpuRasterizer.java` consumes the candidate's already transformed, clipped,
culled and lit triangles. GLES3 rasterizes one complete submesh per draw. It
supports untextured color/lighting and one base-level RGB/RGBA texture with
REPLACE or MODULATE, nearest/bilinear filtering, independent repeat/clamp,
perspective-correct coordinates, alpha rejection, color/alpha masks, viewport
clipping and depth test/write. It also supports framebuffer ALPHA / ALPHA_ADD
and linear/exponential fog with parallel or perspective eye depth. Mutable
texture content is uploaded each draw.

Framebuffer MODULATE / MODULATE_X2, multiple textures, mipmaps, depth offsets, AA,
dithering, wireframe, half-resolution mode and sprites retain CPU execution.
Oversized or nonfinite geometry and target/texture aliases are rejected before
GPU execution. Degenerate/nonfinite fog settings also retain CPU execution.

Consecutive supported submeshes bound to a Graphics target retain color/depth
on the GPU. Barriers before CPU mesh/sprite rendering, clear, bind, release or
renderer reset read both buffers, check GL status and restore the caller's EGL
context before committing CPU arrays. Depth dirtiness accumulates across draws,
including draws with depth writes disabled. Image2D targets commit immediately
so image sampling and mutation do not observe stale pixels. Context switches and
geometry/texture uploads still occur per draw; this is not GPU scene preparation.

Unsupported states flush pending work before CPU fallback. GPU startup failure
before a resident target exists can fall back. An execution/readback/context
failure with resident work raises an explicit exception and disables the backend
until reset: there is no command replay yet, and silently continuing from stale
CPU arrays would lose already drawn geometry. Context-loss recovery is not
claimed. The private pbuffer context is detached between calls and destroyed on
reset. Android's shared EGL display is not terminated.

ARGB data uses bulk buffer copies and shader channel swizzles, not a Java
per-pixel conversion loop. Depth is uploaded as 16-bit data and packed two samples
per RGBA pixel by a second shader, including odd target dimensions. ES3 does not
provide portable direct depth readback via `glReadPixels`; the RGBA readback path
follows the [Khronos ES3 reference](https://registry.khronos.org/OpenGL-Refpages/es3.0/html/glReadPixels.xhtml).
Framebuffer and depth-texture setup use the
[ES3 texture API](https://registry.khronos.org/OpenGL-Refpages/es3.0/html/glTexImage2D.xhtml).
The shader/bridge is new GPL-3.0-or-later code for the FreeJ2ME-Plus-derived
candidate. The existing upstream license asset and notices remain in the APK.
Framebuffer alpha uses [separate RGB/alpha blend factors](https://registry.khronos.org/OpenGL-Refpages/es3.0/html/glBlendFuncSeparate.xhtml),
not destination sampling or a per-triangle readback. Normalized hardware blending
has rounding differences from the software integer pipeline.

## Resident Frames and Effects: 2026-10-12

Non-debuggable Handheld/Phone research APKs pass **92 checks, zero skips** on
Retroid Pocket Classic arm64/Android 14 and x86_64/Android 15 emulator. Eleven
GPU checks require actual GPU draws. Added coverage includes shared transfers,
accumulated depth dirtiness, GPU/CPU/GPU and sprite transitions, clear barriers,
immediate Image2D visibility and later texture use, both fog modes/projections,
ALPHA/ALPHA_ADD, node alpha, channel masks and zero/partial/full fragment alpha.
The fixed interior effect comparison permits up to three channel levels of
CPU/GPU difference; it is not a bound for arbitrary scenes or repeated layering.

Same-APK Retroid measurements (Adreno A12, thermal status 0 before/after each run):

| Scene | Native CPU ms | GPU per-mesh transfer ms | GPU resident ms |
| --- | ---: | ---: | ---: |
| 16 meshes 240x320 | 5.930 | 21.622 | 5.421 |
| 16 meshes 320x240 | 5.930 | 21.354 | 5.692 |
| RGBA alpha 240x320 | 2.228 | 2.155 | 2.106 |
| RGBA alpha 320x240 | 1.810 | 1.965 | 1.971 |
| Linear fog 240x320 | 2.551 | 2.216 | 2.219 |
| Linear fog 320x240 | 2.056 | 2.039 | 2.013 |
| Exponential fog 240x320 | 2.544 | 2.154 | 2.148 |
| Exponential fog 320x240 | 2.092 | 2.058 | 2.013 |

All 22 resident/nonresident image CRCs match. Fourteen scenes use GPU; eight
fallback scenes exactly match native CPU images. Each 16-mesh workload records
3,584 draws over 224 frames but only 224 color/depth uploads and readback pairs,
versus 3,584 pairs with residence disabled. This removes the observed per-mesh
transfer cost, not all GPU overhead: nearest filtering and landscape alpha still
lose to CPU in these samples. No overall game-FPS gain, legacy renderer parity,
real-game GPU acceptance, production switch or release is established.

Local ignored evidence: `build/benchmarks/gpu-resident-on`, `gpu-resident-off`,
`gpu-resident-native-reference` and `build/evidence/{device}-candidate[-phone]`.
Each performance case has 64 warm-up frames and five 32-frame batches.
Current APK hashes (no release artifacts published):

```text
139824C0E54974F1749B8BE5C55AE6B17BB2EC9D5E64F728A872446C6ACB4908 Handheld
5FBA59103734E1630079AAC7CE8B2F890DA667E40F8F2253D2A70FFBB615D218 Handheld tests
8D587B34D21A52B5FD7F94309AB98322565F65512F28034E9EAE8AB28399AEE1 Phone
02AB6E4933A2E7695F6E09A6FD272B9E9430A12B1DF2BCFA8A05B8802E6F2F76 Phone tests
```

## Initial GPU Checkpoint: 2026-10-12 (Historical)

Final non-debuggable Java-only research builds pass **87 checks, zero skips**
on Retroid Pocket Classic arm64/Android 14 and Phone emulator x86_64/Android 15.
The 81 previous checks run with the default CPU backend. Six GPU checks explicitly
require successful GPU draws and cover mixed depth ordering, odd-size transfers,
resize, write masks, disabled depth, texture orientation/mutation, alpha-cutout
depth, fog fallback, perspective images and caller-EGL-state preservation.
No skip or successful CPU fallback is counted as a GPU pass.

The perspective fixture's interior RGB mean/max differences against CPU are
2.077/4 byte levels on Retroid and 2.034/4 on the emulator. The regression requires
mean <3 and max <=12 in that fixed interior; it excludes triangle coverage edges.
Hardware interpolation, sampling and edge coverage are not bit-identical to the
software scanline implementation. These bounds are not general conformance proof.
No real-game GPU session, audio or physical-controller acceptance is added here.

Same-APK Retroid measurements identify **Adreno (TM) A12**, with thermal status
0 at both ends. Each scene has 64 warm-up frames and five 32-frame samples:

| Scene | Native CPU ms/frame | GLES3 ms/frame |
| --- | ---: | ---: |
| RGB nearest 240x320 | 2.314 | 3.009 |
| RGB linear 240x320 | 2.802 | 2.107 |
| Lit grid 240x320 | 5.322 | 5.122 |
| RGB nearest 320x240 | 1.717 | 1.987 |
| RGB linear 320x240 | 2.241 | 2.052 |
| Lit grid 320x240 | 5.932 | 5.642 |

These six scenes each record 224 GPU draws and zero native raster draws. The
other fourteen workloads remain on CPU, with exactly matching CPU-reference
CRCs. GPU images differ from CPU as noted above. No overall speedup, game-FPS
gain or parity with legacy GLES is established.

The initial GPU version spent 4.935/4.718 ms in the two linear scenes; replacing
Java per-pixel conversion with bulk copies and packed depth reduced that to
2.107/2.052 ms. All twenty scene CRCs stayed unchanged between those GPU versions.
This fixes an observed implementation bottleneck but does not remove the
remaining per-submesh transfers. The resident-frame checkpoint above supersedes
that limitation for consecutive supported Graphics-target draws.

Retroid captures from the same final APK, with unchanged dimensions:

CPU reference:

![CPU linear texture scene](cpu-linear-320x240.png)

GLES3:

![GPU linear texture scene](gpu-linear-320x240.png)

## Reproduce

Use the normal candidate build command from the host README. Both backends are
in the **same** research APK; the bounded benchmark selects one explicitly:

```powershell
./experiments/m3g-host/benchmark.ps1 -Serial YOUR_DEVICE -Run gpu-a -Raster gpu
./experiments/m3g-host/benchmark.ps1 -Serial YOUR_DEVICE -Run gpu-b -Raster gpu -GpuResident off
./experiments/m3g-host/benchmark.ps1 -Serial YOUR_DEVICE -Run cpu-a -Raster native
```

`-Raster gpu` allows unsupported draws to fall back, but requires real GPU
draws for the fourteen supported workloads (of 22). Reports include GPU/native
counts, upload/readback counts and driver identity. Residence is on by default
when GPU is explicitly selected; `-GpuResident off` compares the same APK using
the original per-mesh transfer strategy. For a separate manual game experiment, add
`-Pm3gGpu=true` to a candidate build. This is not enabled in the verified APKs
here; tests/benchmarks explicitly toggle GPU within their own process.

Maintainer-local ignored evidence: `build/benchmarks/gpu-first-mesh`,
`gpu-bulk-mesh`, `gpu-bulk-native-reference`, and
`build/evidence/{device}-candidate[-phone]` under `experiments/m3g-host`.
Initial checkpoint APK SHA-256 (historical, not the current build):

```text
2DF16717FF8B79ABD3D62222CF45043AB29EA29B22F83BBB54130E78D80AB2E6 Handheld
FC619A0E5189F0243B09F95DB70A68E6C5808E0648694EB796A1FAEAAAD94071 Handheld tests
3BAF28D50BB996C8C86D5D67CEB1A53DD34CC03E46D5F4C5FA2C0ED53F56C609 Phone
706BDAE062D4B64565F0B714EACF6769DEE88EEEC170B5623E5BACA2F853D4A4 Phone tests
```
