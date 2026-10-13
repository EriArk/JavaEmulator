# M3G in the Real AbyssME Host

## Current Checkpoint

The [optimized upgrade checkpoint](release-20261013.md) records R8 profile fixes,
stack-map import repair, isolated cross-engine RMS updates and separate release/
debug renderer suites. It does not change the production engine or publish APKs.

The [2026-10-12 compatibility/performance report](../../docs/m3g-performance-2026-10-12/README.md)
records the earlier Java-only checkpoint. The candidate now includes a
[native raster fast path](native/README.md) with a shared-buffer Java fallback.
The verifier requires 62 shared checks, 23 candidate animation checks, 21
candidate deformation checks and 180
candidate implementation checks (tone, math, native rasterization, mipmaps,
geometry preparation and GPU checks), plus 76 scene/loader/alpha checks:
362 for an unmeasured candidate, 370 when measured, and 70 for measured baseline
before any engine-specific skips. These are expected totals, not evidence that
each combination has been executed.
Eight additional timing checks apply only when measurement is enabled.
The [shader and readback follow-up](shaders-20261013.md) adds bounded texture-count
and sprite pipelines, partial color readback and same-APK reference switches.
The [throughput and compatibility follow-up](throughput-20261013.md) covers lazy
bitmap transfers, hardware clears, whole-frame submission, conservative mesh
culling and independent depth-write behavior. Main keeps the original engine.
The [Retroid gameplay report](gameplay-20261013.md) records real-game surface
and host cadence, original-engine comparisons, automatic font defaults and
GPU diagnostic lifecycle recovery. Surface cadence is not unique game FPS.
The [GPU submission report](submission-20261013.md) records bulk vertex packing,
scoped state reuse, mixed-material regression coverage and fresh timings.
The [scene compatibility report](scene-contracts-20261013.md) records the preceding
camera, lighting, loader and CPU/GPU fixes, full device/emulator results and a
private 70-resource corpus. The [deformation report](deformation-20261013.md)
covers mutable geometry and skeletons. The [animation report](animation-20261013.md) covers curves
and playback. Measured totals are expected counts, not a new measurement run.
Both Handheld (`fdroid`) and Phone research builds are supported; pass
`-Variant phone` to `verify.ps1` for Phone.
Use `-ApkRoot` to verify a saved `outputs/apk` snapshot independently of later
builds; slow software-rendered emulators can use `-TestTimeoutSeconds 900`
(maximum 3600).

Candidate builds replace the inherited EPL tone package with the original
[ToneControl implementation](tone-java/README.md). Run `:app:testToneCompiler`
for the independent JVM MIDI-parser checks. Opt-in local `-PmophunSource=<path>`
and `-PmophunTestGames=<path>` add private Mophun adapters/tests and test-only
fixtures. These are not required for the Java-only research build, do not
publish private sources, and must never add games to the application APK.

`-Pm3gTrace=true` enables bounded distinct draw-state capture in private cache.
It is mutually exclusive with measurement; never compare traced timings with
untraced timings as renderer performance. `-Pm3gMeasure=true` now records
aggregate bitmap-read/write and draw phases in addition to target sessions.

`-Pm3gNonDebuggable=true` disables ART debug execution in research builds for
controlled comparisons. It retains research IDs/debug signing, does not enable
R8, and is not a public release configuration. Use it for both engines, never
only one side. `benchmark.ps1 -Serial YOUR_DEVICE -Engine candidate -Run NEW_LABEL`
runs 34 bounded original scenes and saves PNGs, timings, thermal status and
APK hashes. This benchmark is opt-in, not part of the normal verifier. Its wall
cost is not game FPS. Use `-ApkRoot` for saved APK snapshots when alternating
engines: Gradle can remove the other engine's stale test output directory.
For a same-APK fast-path comparison, pass `-Raster java`, `-Raster native` or `-Raster gpu`
with separate run labels. Native runs require a successful native draw in every
scene; GPU runs now require GPU draws in all 34 scenes, including sprites.
Library loading alone is not accepted as proof.

## Original Setup

Branch-only research, not an end-user engine option. The ordinary app build and
`main` retain the existing M3G engine. No release APK, license change, Mophun or
Symbian integration is part of this experiment.

## Build

### Optimized Upgrade Rehearsal

`-Pm3gReleaseCheck=true` with `-Pm3gExperiment=baseline|candidate` enables only
Handheld/Phone **release** variants, retaining R8, resource shrinking and the
real `:midlet` process. It uses debug signing and isolated shared package IDs
`io.github.eriark.abyssme.m3grehearsal[.phone]`, never production keys or IDs.
The candidate automatically enables GPU rendering and hardware geometry.
Measurement, tracing and private Mophun inputs are rejected in this mode.

Build `:app:assembleFdroidRelease :app:assemblePhoneRelease` and corresponding
`ReleaseAndroidTest` tasks. Pass `-ReleaseCheck` to `verify.ps1`; optional
`-Variant phone` selects Phone. Snapshot `outputs/apk` before changing engines:
Gradle may remove outputs from an earlier configuration even with separate
build directories. `-ApkRoot` accepts that saved snapshot.

The test APK is kept unoptimized; rehearsal-only app rules retain AndroidX
Trace and Kotlin APIs used by the external runner. App/profile/renderer
optimization remains enabled. These are not final release APKs.
Use the [RMS MIDlet fixture](upgrade-probe/README.md) for a reproducible
before/after save check without private game payloads.

### Debug Research

Use the repository wrapper, JDK 21, SDK 34 and NDK 22.1.7171670. The pinned
FreeJ2ME-Plus archive is fetched and hash-verified automatically; no maintainer
checkout is required. Offline/archive-override options are documented in the
[standalone probe](../m3g-probe/README.md). From the worktree root:

```powershell
.\gradlew.bat :app:assembleFdroidDebug :app:assembleFdroidDebugAndroidTest -Pm3gExperiment=candidate
.\experiments\m3g-host\verify.ps1 -Serial YOUR_DEVICE -Engine candidate
.\gradlew.bat :app:assembleFdroidDebug :app:assembleFdroidDebugAndroidTest -Pm3gExperiment=baseline
.\experiments\m3g-host\verify.ps1 -Serial YOUR_DEVICE -Engine baseline
```

Set `ANDROID_HOME` or pass `-Adb` to the verifier. By default only `fdroidDebug` and `phoneDebug` are enabled
in research mode; private signing configuration is not required. Build outputs
are separated under `app/build/m3g-candidate` and `app/build/m3g-baseline`.
Both support arm64-v8a and x86_64. Application IDs are isolated:

- `io.github.eriark.abyssme.m3gcandidate.debug`
- `io.github.eriark.abyssme.m3gbaseline.debug`

They cannot update ordinary AbyssME packages and have independent libraries,
profiles and saves. The verifier checks package metadata, engine isolation and
notices before installing; it does not uninstall or erase user data. ADB calls
are bounded and a missing/failed test report is not accepted as a pass.

Do not run the baseline on an emulator as an unattended acceptance step: the
earlier standalone native-engine probe stalled/exited that target. This remains
unresolved. The real-host baseline was tested on Retroid only.

## Adapter

The candidate uses the same revision-guarded, generated FreeJ2ME M3G package as
the standalone probe. Its notices and GPL text are retained; no upstream engine
source or game archive is committed. The shared preparation still applies the
AWT removal and multi-section loader fix. The original checkout is untouched.

Additional exact-match guarded changes connect `Graphics3D` to this host:

- On binding real LCDUI `Graphics`, copy its bitmap into a reusable Android
  scratch buffer; on release, flush it back. This preserves 2D-before-3D and
  2D-after-3D paint order, translation and clipping.
- Keep that scratch buffer separate from an `Image2D` render target's storage.
  Reusing the renderer's current raster array corrupts an equal-sized texture.
- Resolve resources through the active `AppClassLoader`/`ContextHolder` and
  reset the singleton through the existing `MicroLoader` initialization hook.
- Debug wireframe is disabled and explicitly unsupported. Renderer flags are
  fixed in the research adapter, not exposed as new user settings.

The candidate excludes the original EPL Java bindings and `libjavam3g.so`; it
still builds the unrelated existing `micro3d` library. The baseline uses the
existing M3G Java/native sources without modification. No original LCDUI,
loader, compatibility heuristic, game JAR or native engine source is patched.

This adapter copies a framebuffer in both directions on each binding cycle.
Its sustained CPU/memory/frame-time cost is not established by the initial smoke tests. This is not
zero-copy rendering, full JSR-184 conformance, or publication-license clearance.
No combined binary is distributed here; any later distribution requires review
of the complete artifact and applicable source/notice obligations.

## Checks

`M3gHostTest` is compiled into both research test APKs and exercises the real
AbyssME LCDUI/resource host:

1. Preserve 2D background, translated clip and later HUD drawing.
2. Switch and resize Graphics targets across 20 dimensions without stale pixels.
3. Reset renderer state between sessions.
4. Release an Image2D target without writing the previous Graphics bitmap.
5. Load an original PNG through the active MIDlet archive class loader.
6. Bind an equal-sized Graphics target without overwriting an Image2D texture.

The sixth test demonstrably fails with the earlier shared-buffer adapter and
passes with the separate scratch buffer. These are focused integration checks,
not the complete existing app test suite or physical controller acceptance.

`M3gRasterTest` includes the original eleven public-API scenes: default/vertex/texture/node alpha,
multiplied vertex/texture alpha, linear texture centers and all four clamp edges,
nearest filtering and repeat seams. The verifier
pulls the raw 64x64 scene images. The candidate's continuous bilinear coordinate
is clamped before splitting texel neighbors and weights; clamping only the first
neighbor incorrectly mixed edge texels. This generated-source fix also applies
to the standalone candidate. Upstream and the baseline stay unchanged.

Twelve more raster cases cover material diffuse alpha/emission without lights,
luminance-alpha textures, multiply blending, depth test/write and alpha rejection,
and fog under parallel/perspective projection, including sloped geometry and
disabled/collapsed depth state. The verifier requires **29 host/raster tests**.
Generated candidate fixes preserve material evaluation with an empty light list
and recover parallel eye depth before viewport/depth-range mapping for fog.

[Dated device results, game variants and screenshots](../../docs/m3g-host-2026-10-11/README.md).

## Performance Observation

Add `-Pm3gMeasure=true` to **both** research build commands for comparable host
instrumentation. This opt-in task generates a copy of Canvas with three guarded
hooks: completion of `paint`, GameCanvas flush and ExtendedImage flush. The
original Canvas source remains unchanged. Generated Graphics3D copies also
observe successful target binding through release on both engines. No checked-in
production Java/native source is edited. Do not compare an
instrumented engine against an uninstrumented one as if costs were identical.

`FrameMeter` records bounded two-second windows in app-specific external files,
capped at 512 KiB. This allows ADB capture from non-debuggable research builds
without `run-as`. It is research-only telemetry, not production logging.
File output is deliberate: this Retroid suppresses Info/Warning
log messages. It records Canvas update count/intervals and paint-call duration,
not game-logic ticks or isolated GPU/renderer time. A game may flush multiple
times per logical frame. Observing paint includes game drawing and dispatch;
telemetry itself adds some overhead and has not been independently calibrated.

`targetSessions`, `targetP50Ns` and `targetP95Ns` describe wall time from the end
of a successful `bindTarget` through `releaseTarget`, including intervening game
code and buffer release/readback, but excluding binding. They are not isolated
renderer timings. This captures 3D work even for games that bypass `Canvas.paint`.
No samples are reported as -1, not as zero cost. Three extra hook tests verify
held duration, duplicate release and invalid binding. The verifier detects the
measurement class in the APK and requires **32 tests** for measured builds.

After manually reaching and inspecting gameplay, run:

```powershell
.\experiments\m3g-host\test-frame-metrics.ps1
.\experiments\m3g-host\measure.ps1 -Serial YOUR_DEVICE -Engine candidate -Run game-session1 -Seconds 90
```

Use a fresh run label; existing evidence is never overwritten. The script does
not select games, steer or install APKs. It collects the installed APK hash,
unmodified start/end screenshots, per-process PSS every 30 seconds, battery
temperature and deduplicated SurfaceFlinger presentation timestamps. It checks
the process and foreground activity and rejects missing cadence, sample
overflow, a stale surface or a polling gap that could lose history. Only complete
telemetry windows within the sample are used; no forced GC is performed.

Surface refresh is **not game FPS**: Android may repeatedly present the same
game image. The timestamp column order and invalid fence sentinel follow the
[AOSP FrameTracker implementation](https://android.googlesource.com/platform/frameworks/native/+/51de481/services/surfaceflinger/FrameTracker.cpp).
Inspect screenshots and host cadence too. Surface polling is supplemental;
screen changes, warm-up, pauses and game switches must not be mixed into a
steady-game measurement. A 90-second PSS series cannot prove absence of leaks.

[Runtime observations, lifecycle checks and rejected samples](../../docs/m3g-runtime-2026-10-11/README.md).

## Opt-in GPU Backend

The candidate now includes an off-by-default GLES3 submesh backend, selected
explicitly by `benchmark.ps1 -Raster gpu` or a manual `-Pm3gGpu=true` research
build. Ordinary builds and main's engine are unaffected. Unsupported effects
retain native-CPU/Java execution. Consecutive Graphics-target draws share GPU
color/depth until a CPU-access barrier; all framebuffer blend modes, bounded depth
offsets, fog, four texture layers and mipmapping also run on GPU. Empty meshes
do not force readback; signed CPU depth keeps its safe fallback. Revision-aware
upload caching preserves mutable textures. Bounded per-unit LRU caches retain
alternating materials without reuploading each draw. The current verifier
requires 140 candidate checks (92 API/host/CPU checks plus 48 GPU-specific checks),
or 147 with measurement (not rerun in this checkpoint).
See [scope, device evidence, screenshots and performance limits](gpu/README.md).

## Scene References and Copies

Shared-resource slots, animation-track identity, skeleton/alignment remapping
and camera handling are corrected in generated candidate sources. Eleven new
public-API cases run against both engines. See the
[dated comparison and verification boundaries](scene-copies-20261013.md).
