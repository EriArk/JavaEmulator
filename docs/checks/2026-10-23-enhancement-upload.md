# Guest Buffer / Enhancement Separation - 2026-10-23

## Implementation and Availability

Unreleased development source; effects remain opt-in and Debug-only. No public
APK, release tag, Nextcloud upload or GitHub Actions change. The live release
listing was not rechecked in this checkpoint. The installed Debug package is
separate from the signed application and its library. No games or firmware are
included in the change.

Previously Canvas held `bufferLock` across texture upload, shader compilation,
motion estimation, FSR and final drawing. The enhanced path now calls
`EnhancementPipeline.upload()` while protecting the guest bitmap, then calls
`renderUploaded()` outside the lock. No bitmap reference survives upload.
This retains two GPU input textures without a new CPU copy or frame queue.

Geometry publication follows the existing buffer-then-vertex lock order. Resize
publishes the backing image and geometry under the same lock. Each enhanced draw
copies vertices and output dimensions into GL-owned state, releases both locks,
and presents that snapshot. FPS accounting uses the uploaded serial, not a
newer serial that a concurrent producer may publish during GPU processing.

The normal/custom-shader path retains its existing draw synchronization.
Texture upload/allocation itself may still block on driver work; no claim of
zero blocking, higher native game FPS or lower measured end-to-end input latency.
Mophun and Symbian execution are unchanged.

## Regression Evidence

Before installing the new app, the new lock test was run against the previous
Retroid Debug APK. It failed with `GPU presentation must not hold the guest
buffer`. The test substitutes a presentation shader on the GL thread and holds
its final vertex submission with latches, rather than relying on timing luck.
The new implementation passes: another thread flushes a new frame and a guest
key callback runs while presentation is held; only the old uploaded serial is
counted for that draw. There are no production test hooks.

Additional checks prove:

- Uploaded pixels survive mutation and recycling of the source bitmap.
- Upload does not instantiate image-processing shaders.
- Separate upload/draw preserves interpolation history and caching; reset
  requires a fresh upload and expired frames skip motion search.
- Invalid viewport sizes are rejected before sampling outside the source.
- Red/green/blue/white corners remain correctly oriented through four rotations
  and logical-size changes, using actual GLES screenshot readback.

Build: Debug app and instrumentation APK passed. JVM suite: 54 tests, no
failures/errors. Lint: zero errors, 400 warnings, not warning-free.
Retroid Pocket Classic: 75 instrumentation checks passed (20 pipeline, 36 Canvas
lifecycle, nine settings UI, six profile serialization, four Mophun rate/budget).
Android 15 x86_64 emulator: the same 75 checks passed. No fresh signed/R8 build.

## Cost Boundary

Retroid synthetic 240x320 moving checker, 720x960 output, both effects enabled,
35 samples with five warmups excluded. Upload is timed separately; processing
includes `glFinish` after the effects. Frequencies/temperature were not locked.

| Stage | Median | P95 |
| --- | ---: | ---: |
| Protected upload | 0.95 ms | 1.25 ms |
| Unlocked processing | 11.51 ms | 11.92 ms |

This demonstrates the synchronization boundary, not a proportional game speedup.
The full existing motion+FSR benchmark remains around 12.14 ms median / 12.52 ms
P95; shader math has not become faster in this patch. Emulator timings are
host-dependent and are not handheld performance evidence.

## Retroid Game Smoke

On the final Debug APK, Bounce Tales was opened through the launcher, both
effects enabled with Try changes, and New game / Chapter 1 opened. The first
level introduction and next dialogue rendered and accepted ADB button input.
Settings and game captures were inspected. This is an installed live-preview
smoke check, not physical-controller/audio, sustained gameplay, visual quality
approval or a measured FPS comparison. No saved enhancement profile is claimed.

Local APK: `app/build/outputs/apk/fdroid/debug/AbyssME-0.4.0-beta.3-r1-debug.apk`

SHA-256: `413a5e1919d3a86e1ed7ae5ece0bb0d03c9f6f0eddf21f7b15076453855078cc`

The shared filename does not make this the public release artifact.

Ignored local evidence: `build/enhancement-upload-before.txt`,
`build/enhancement-upload-build.txt`, `build/enhancement-upload-retroid.txt`,
`build/enhancement-upload-emulator.txt` and `build/enhancement-upload-*.png`.
If absent, treat these as dated maintainer observations, not attached public
test artifacts. Source fixtures are in the instrumentation test suite.

Next: lower the cost of full-output passes without hiding quality regressions;
actual scene comparisons, input response and sustained thermals still matter
before recommending these effects or enabling them in a public build.
