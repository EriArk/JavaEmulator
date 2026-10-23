# Enhancement Refinement - 2026-10-23

## Availability

Unreleased Debug-only Java OpenGL ES changes. Defaults remain off, the signed
application/library is preserved, and no public APK, release tag or asset is
changed. This checkpoint does not recheck the live release listing. Mophun CPU
budget and Symbian behavior are unchanged. No game assets are published.

## Shared Repairs

- Interpolation finishes within a 33.3 ms presentation window instead of 100 ms.
  Slower games then hold the current image. This is not uniform 60 FPS or a
  measured end-to-end input-latency bound.
- Preserve unchanged HUD/text pixels, reject out-of-viewport warps and use the
  largest channel disagreement when rejecting inconsistent matches.
- Cache the current motion template, skip static-block searches, skip estimation
  for streams outside the interpolation range, and bypass completed resolve.
- Use integer pixel centers and nine cross samples (center plus offsets 1/2 in
  each direction). The old sparse five-sample template could confuse one-pixel
  and two-pixel edges; boundary sampling also differed across GPU backends.
- Run EASU only for actual upscaling, skip RCAS at zero sharpness, and support
  native-size RCAS directly on padded input. Native size with zero strength is
  pixel-identical to the source. Cache uniform locations per shader program.

## Before-Fix Evidence

New tests were installed against the previous Debug application before rebuilding
it. On Retroid, fast input incorrectly allocated the motion estimator, and native
FSR at zero sharpness changed red/green checker pixels. Two additional tests
injected a known motion field and reproduced a stationary red HUD pixel becoming
white and a red viewport-edge pixel becoming blue. These controlled-field tests
isolate resolve safeguards; they do not prove general optical-flow quality.

A four-direction real-estimator test then exposed an emulator failure despite
passing on Retroid. Integer-center sampling alone was insufficient; adding the
adjacent samples resolves the ambiguity in this fixture. Both changes are kept.

## Build and Checks

- JVM suite: 54 tests, no failures/errors.
- Debug application and instrumentation APK assembly passed.
- Lint passed with zero errors and 400 existing warnings; not warning-free.
- Final Retroid Pocket Classic selection: 69 checks passed.
- Final Android 15 x86_64 emulator selection: the same 69 checks passed.

The selection includes 16 GPU pipeline tests, 34 Canvas lifecycle tests, nine
settings UI tests, six profile serialization tests and four Mophun clock/budget
tests. Assertions use GPU pixel readback and Canvas PixelCopy, not compilation
alone. No fresh signed/R8 release build or physical-button/audio acceptance.

Final local APK: `app/build/outputs/apk/fdroid/debug/AbyssME-0.4.0-beta.3-r1-debug.apk`

SHA-256: `c2a30bdd6eecbbac84309cfba537295316f5ebfca29c50175c60622547c38667`

This filename is the development version, not the public release artifact.

## Retroid GPU Cost

Same synthetic 240x320 input / 720x960 output workload before and after, 35
samples with five warmups excluded, timed upload/draw plus `glFinish`. The device
frequency and temperature were not locked. These are pipeline costs, not game
FPS, sustained performance or power measurements.

| Path | Before median / P95 | Final median / P95 |
| --- | ---: | ---: |
| FSR | 10.02 / 10.54 ms | 9.44 / 10.28 ms |
| Motion | 4.46 / 5.05 ms | 3.78 / 4.07 ms |
| Motion + FSR | 13.23 / 13.74 ms | 12.09 / 12.51 ms |

An intermediate five-sample candidate measured lower motion cost but failed
the emulator direction fixture. Those faster numbers are not the delivered
implementation. The raw path now bypasses cropping for unpadded input, so its
timing is not a like-for-like baseline comparison. FSR remains expensive on
this handheld; no overall game speedup is inferred from these samples.

Additional solid-frame workloads measured 10.31 ms median for new static frames,
10.15 ms for 60 Hz input, 9.32 ms for intermediate redraw and 9.91 ms for zero
sharpness. They have different content and must not be compared directly with
the table. Static-block skips do not eliminate FSR work for each new guest frame.

## Evidence and Open Acceptance

Ignored local logs: `build/enhancements-before.txt`,
`build/enhancements-before-resolve.txt`,
`build/enhancements-refinement-build.txt`,
`build/enhancements-refinement-build-shader.txt`,
`build/enhancements-refinement-retroid-final.txt`,
`build/enhancements-refinement-emulator-final.txt`.
If absent, these are dated maintainer observations rather than reproducible
attached artifacts. The previous real-game smoke is linked in
[the initial checkpoint](2026-10-23-enhancements.md); it was not repeated here.

Block boundaries, occlusions, fast scrolling and repeating patterns remain
experimental limitations. Preserving unchanged pixels can also suppress valid
intermediate motion in repeating imagery. Actual input latency, sustained GPU
cost and scene-by-scene visual quality remain open. Next implementation work is
reducing render/input contention and avoiding unnecessary full-size passes,
with matching-scene evidence before any release decision. Mophun/Symbian GPU
adapters are not implemented by this patch.
