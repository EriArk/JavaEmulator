# M3G in the Real AbyssME Host

Branch-only research, not an end-user engine option. The ordinary app build and
`main` retain the existing M3G engine. No release APK, license change, Mophun or
Symbian integration is part of this experiment.

## Build

Use the repository wrapper, JDK 21, SDK 34 and NDK 22.1.7171670. Prepare the
clean, pinned FreeJ2ME-Plus checkout described in the
[standalone probe](../m3g-probe/README.md). From the worktree root:

```powershell
.\gradlew.bat :app:assembleFdroidDebug :app:assembleFdroidDebugAndroidTest -Pm3gExperiment=candidate -Pfreej2meSource=C:/absolute/path/to/freej2me-plus
.\experiments\m3g-host\verify.ps1 -Serial YOUR_DEVICE -Engine candidate
.\gradlew.bat :app:assembleFdroidDebug :app:assembleFdroidDebugAndroidTest -Pm3gExperiment=baseline
.\experiments\m3g-host\verify.ps1 -Serial YOUR_DEVICE -Engine baseline
```

Set `ANDROID_HOME` or pass `-Adb` to the verifier. Only `fdroidDebug` is enabled
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

`FrameMeter` records bounded two-second windows in the app-private cache, capped
at 512 KiB. File output is deliberate: this Retroid suppresses Info/Warning
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
