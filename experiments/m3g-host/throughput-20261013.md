# M3G throughput and compatibility, 2026-10-13

Research branch only. Main retains the existing engine. This batch does not
change the root license, publish a combined APK or establish release readiness.
The primary device is a Retroid Pocket Classic, Android 14 / Adreno A12.

## Implementation

- Import the bound LCDUI clip only when its contents are needed. A complete
  clear of that clip skips the import; unused bindings and depth-only clears
  do not copy color. Export only the captured clip, preserving surrounding 2D.
- Read opaque sRGB RGBA8888 bitmaps through a small NDK bridge. Transparent
  pixels and other formats/color spaces retain Android's exact conversion.
  No game setting, quality reduction or bitmap mutation is involved.
- Once the GPU backend is validated, submit a whole warmed frame in one EGL
  scope. Its first draw is included in the bounded recovery journal; initial
  startup still probes the backend immediately for CPU fallback.
- Use hardware clear instead of uploading two uniform arrays after a full
  solid clear. Partial clears, CPU barriers and target changes invalidate it.
  Recovery replays the original clear and draws without committing partial work.
- Raise small-mesh cache capacity to 256 entries, retaining the existing
  4 MiB byte bounds. Off-camera meshes are rejected before light/texture setup;
  conservative bounds follow vertex revisions, model/camera changes and bias.
  Their bounded weak-key cache does not keep abandoned position arrays alive.
- Skip unused normal inversion for unlit meshes and avoid temporary matrix
  copies in the common case. Zero slope bias skips derivative work in the shader.

## Compatibility Fixes

- Android RGB888 targets no longer advertise unsupported low-color dithering
  or higher-precision true-color hints. Requested hint bits are preserved, but
  `DITHER` alone no longer forces a slow low-color CPU renderer. Explicit
  research software-dither overrides remain; AA is not disabled.
- Reject immutable render targets and non-RGB background images on MIDP
  Graphics before changing the destination.
- Correct depth-offset units: one unit now separates coplanar surfaces on
  Java, native CPU, prepared GPU and hardware-geometry paths. Clamp depth
  instead of wrapping signed shorts; finite offsets stay on the hardware path.
- Separate depth testing from depth writing for meshes and sprites. Write-only
  passes update depth without rejecting fragments; disabled target depth still
  disables both. The native sprite packet validator/pinning and GPU-to-CPU
  readback were corrected too. Alpha rejection and color masks remain respected.

### Original-engine Review

Read-only inspection covered `m3g_rendercontext.c`, `m3g_mesh.c`,
`m3g_compositingmode.c` and `m3g_defs.h`: deferred front-buffer preservation,
OVERWRITE/quality hints, view-frustum rejection and independent depth flags.
The changes above are new adapter/GPU code and guarded candidate adaptations,
not copied EPL implementations. OVERWRITE is not used to discard arbitrary
uncleared content; the optimization requires a known complete clear.

Behavioral references: [Graphics3D hints and target rules](https://nikita36078.github.io/J2ME_Docs/docs/jsr184/javax/microedition/m3g/Graphics3D.html),
[CompositingMode depth operations](https://nikita36078.github.io/J2ME_Docs/docs/jsr184/javax/microedition/m3g/CompositingMode.html),
and the [Android NDK bitmap API](https://developer.android.com/ndk/reference/group/bitmap).

## Retroid Timings

Both engines use non-debuggable measured research APKs. Candidate additionally
enables GPU rasterization and hardware geometry. Each synthetic scene warms
for 256 frames, then records five batches of 32 frames. Values are mean wall
milliseconds per fixture frame, **not game FPS**. No resolution/filter reduction.

| Fixture | Previous candidate | Final candidate | Original engine |
| --- | ---: | ---: | ---: |
| RGB nearest 240x320 | 1.884 | 1.421 | 0.862 |
| RGB nearest 320x240 | 1.556 | 1.075 | 0.836 |
| RGB linear 320x240 | 1.579 | 1.083 | 0.854 |
| Sprites 320x240 | 2.043 | 1.456 | 0.950 |
| Flat lighting 320x240 | 1.850 | 1.451 | 0.845 |
| Two-sided lighting 320x240 | 1.948 | 1.620 | 0.885 |
| Multi-draw 320x240 | 2.530 | 1.808 | 1.245 |
| Depth offset 320x240 | 1.611 | 1.084 | 0.847 |
| Material churn 320x240 | 2.546 | 1.803 | 1.267 |

All 34 candidate fixtures complete, with **identical pixel CRCs before/after**.
Costs fall 17-34%, usually about 25-32%. Original completes 32 fixtures; the two
four-texture fixtures are explicitly skipped because its texture-unit limit is
lower. **Native speed parity is not reached**: remaining synthetic cost is
roughly 19-83% higher, especially extended lighting and sprites.

Same-device sequential runs are not locked-clock or randomized trials. Each
benchmark reports thermal status 0; gameplay thermal service also says its HAL
is not ready, so absence of throttling cannot be concluded. Early/late scene
order and JIT/device state can affect comparisons.

- [Previous candidate raw report and APK identity](throughput-20261013/throughput-before/).
- [Final candidate report, screenshots and APK identity](throughput-20261013/throughput-transfer/).
- [Fresh original report, screenshots and APK identity](throughput-20261013/throughput-native/).
- [All 34 comparisons and pixel checks](throughput-20261013/comparison.json).

## Deep Gameplay

This is **Deep: Submarine Odyssey Java**, not Mophun Deep Abyss. The final APK
reaches the opening fishing gameplay with the boat, station, effects and HUD.
Each 35-second capture receives fourteen Right long-presses separated by 600 ms.
Screenshots show the boat/camera turning and continued gameplay.

| Sample | Surface buffers/s | Largest window target P95 | Host updates/s | Bitmap import/update |
| --- | ---: | ---: | ---: | ---: |
| Intermediate candidate, before NDK bitmap read | 60.00 | 4.843 ms | 159.9 | 0.370 ms |
| Final candidate | 60.00 | 4.186 ms | 169.6 | 0.096 ms |
| Fresh original | 60.00 | 4.010 ms | 192.1 | Not instrumented |

All three captures have no >100 ms presentation gap; their longest intervals
are below 17 ms. Surface buffers can repeat, and game flush counts can exceed
display FPS: neither column measures unique game frames or logic ticks. Target
P95 is the maximum of two-second-window P95s, not one global percentile. Camera
position, particles and targeting labels differ, so the small final target-time
gap is a promising sample, not a controlled native-parity proof.

The previous-turn candidate recorded 151.5 host updates/s, but it is a separate
dated workload, not the denominator of a claimed game-FPS improvement. Its
captured CPU draw time was about 1.33 ms/update; this final sample is 0.32 ms.

- [Final Deep screenshots, timing windows, memory and installed hash](throughput-20261013/throughput-deep-transfer-candidate/).
- [Intermediate sample](throughput-20261013/throughput-deep-candidate/).
- [Original sample](throughput-20261013/throughput-deep-native-baseline/).

## Asphalt Gameplay

The final candidate also runs an Asphalt 3 Instant Play race on Retroid. The
35-second capture includes eighteen Up long-presses separated by 600 ms. Start
and end images show track/car movement, tire smoke and progression from lap 1
to lap 2. Presentation averages 60.00 surface buffers/s, with a maximum interval
of 16.91 ms and no interval over 100 ms. This is not unique game FPS, a complete
race or a matched original-engine comparison; manual controls/audio are untested.
[Screenshots, timing windows and installed APK identity](throughput-20261013/throughput-asphalt-transfer-candidate/).

## Verification and Boundaries

Published text logs use UTF-8 and normalized trailing whitespace; recorded
measurements, assertions and outcomes are unchanged.

- Final measured Handheld APK: **361 passed, zero skips on Retroid**.
  [Raw checks](throughput-20261013/retroid-tests.txt),
  [APK/test hashes](throughput-20261013/retroid-artifact.json).
- Final Phone APK on Android 15 x86_64 / SwiftShader: **36 focused checks
  passed**, covering all 28 new throughput/compatibility cases and eight meter
  cases. [Raw checks](throughput-20261013/phone-focused-tests.txt),
  [artifact hashes](throughput-20261013/phone-artifact.json).
- The earlier full Phone attempt exceeded its 600-second limit and was stopped.
  It is **not a full Phone pass**. [Incomplete-run record](throughput-20261013/phone-full-incomplete.json).
- The expanded depth contract initially exposed a native sprite validation
  failure. It was fixed before the final full Retroid run; the
  [failing run remains recorded](throughput-20261013/native-sprite-before-fix.txt).
- Intermediate assertions expecting eager bitmap copies, two warmed GPU
  submissions and the former cache entry limit were updated to explicit new
  contracts, with separate expected-pixel and context-loss checks.
- Both Handheld and Phone app/test APKs build offline with JDK 21. Research IDs
  and saves are separate from the ordinary installed app. No games or APKs are
  committed or distributed. Full-game, physical-button and audio acceptance
  are not established by these ADB-driven checks.

Next engineering target: reduce extended-lighting/sprite shader and final
readback costs without lowering resolution, filtering or compatibility.
Production switching, dependency/license review and signed upgrade/save checks
remain separate gates. Live release state was not re-queried in this batch.
