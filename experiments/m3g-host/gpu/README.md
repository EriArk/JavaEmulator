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
clipping and depth test/write. Mutable texture content is uploaded each draw.

Fog, framebuffer blending, multiple textures, mipmaps, depth offsets, AA,
dithering, wireframe, half-resolution mode and sprites retain CPU execution.
Oversized or nonfinite geometry and target/texture aliases are rejected before
GPU execution. A GPU failure leaves the CPU buffers unchanged and suppresses
further GPU attempts until renderer reset. Context creation and shader errors
remain available in the research diagnostic field, not hidden as successful draws.

Each submesh is a transaction: upload current color/depth, render, read both
results, check GL status, restore the caller's EGL context, then commit to the
CPU arrays. This permits GPU/CPU/GPU interleaving without clearing shared depth
or losing previously rendered pixels. It is deliberately **not** yet a resident
GPU frame. Current per-submesh transfers limit speed, particularly for small draws.
The private pbuffer context is detached between calls and destroyed on renderer
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

## Device Results: 2026-10-12

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
remaining per-submesh transfers. Next: keep consecutive GPU work resident until
CPU access/release, then extend effects without breaking mixed-buffer semantics.

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
./experiments/m3g-host/benchmark.ps1 -Serial YOUR_DEVICE -Run cpu-a -Raster native
```

`-Raster gpu` allows unsupported draws to fall back, but requires real GPU
draws for the six supported workloads. Reports include GPU/native counts and
driver identity. For a separate manual game experiment only, add
`-Pm3gGpu=true` to a candidate build. This is not enabled in the verified APKs
below; tests/benchmarks explicitly toggle GPU within their own process.

Maintainer-local ignored evidence: `build/benchmarks/gpu-first-mesh`,
`gpu-bulk-mesh`, `gpu-bulk-native-reference`, and
`build/evidence/{device}-candidate[-phone]` under `experiments/m3g-host`.
Final APK SHA-256:

```text
2DF16717FF8B79ABD3D62222CF45043AB29EA29B22F83BBB54130E78D80AB2E6 Handheld
FC619A0E5189F0243B09F95DB70A68E6C5808E0648694EB796A1FAEAAAD94071 Handheld tests
3BAF28D50BB996C8C86D5D67CEB1A53DD34CC03E46D5F4C5FA2C0ED53F56C609 Phone
706BDAE062D4B64565F0B714EACF6769DEE88EEEC170B5623E5BACA2F853D4A4 Phone tests
```
