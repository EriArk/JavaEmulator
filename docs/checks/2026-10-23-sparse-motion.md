# Sparse Motion and Surface Recovery - 2026-10-23

## Scope and Availability

Unreleased Java Canvas/GameCanvas and Mophun GLES enhancement work. Effects
remain Debug-only, opt-in and off by default. Ordinary rendering, source clocks,
input, FSR equations, image presets and Symbian rendering are unchanged. No
public APK/release/tag, Nextcloud upload or GitHub Actions mutation. The public
release listing was not rechecked in this checkpoint.

## Shared Rendering Repair

Before repair, two new GPU tests failed on the installed `e5a30743` Retroid app:
single-pixel motion on an unchanged background and motion across a flow-tile
boundary. A third test preserving static/new/disappearing occluders already
passed and remains a guard. The old center-only patch could miss small objects;
the unchanged-pixel early exit also prevented an intermediate object from
occupying background that was empty in both real frames.

The estimator now recognizes sparse tiles through matching background corners
in both source frames and at most 16 non-background pixels per frame. A bounded
scan of at most 8x8 pixels finds a changed current foreground anchor. The same
3x3 matcher, four-pixel search, ambiguity and endpoint checks then apply.

One flow-grid pass carries a confirmed sparse direction into adjacent eligible
background tiles. It never propagates recursively, fills dense/unsupported tiles,
overrides a rejected moving anchor or chooses between conflicting directions.
The original residual feeds the scene-cut guard before propagation. Resolve
permits intermediate objects only on verified background, preserves static HUD
pixels and checks that both warped source endpoints actually changed before
introducing foreground into unchanged background. No CPU image scan/readback or
guest-buffer-lock extension is added.

The new target costs 4,800 RGBA bytes at a 240x320 guest viewport (30x40 flow
cells). The scan/pass adds GPU work; this is a correctness improvement, not a
claimed speedup. All added resources follow existing reset/release/fallback
ownership. No additional game-frame queue.

## Surface Lifecycle Repair

The first broad run reached 100/101 passes but PixelCopy caught an invalid
surface during repeated Mophun effect pause/resume. A new explicit detach test
then reproduced production capture accepting a request after surface destruction.

Mophun now checks surface validity for readiness and capture, clears presentation
on surface destruction/creation, cancels pending capture on destruction and uses
a presentation epoch to reject old queued frame acknowledgements across surface
and active-state changes. The detach/re-add test verifies rejection while absent
and correct pixels after recreation. The test harness also serializes PixelCopy
validation/request on the UI thread and bounds retries to transient invalid/no-data
surfaces; GPU fallback, other copy failures and timeout still fail the test.

This does not claim that every historical black-screen report is fixed.

## Verification

- Debug app and test APK assembly succeeded. Final lint: zero errors, 399 warnings.
- Fresh JVM run: 75 tests, zero failures/errors.
- Retroid Pocket Classic (`MC94516AQF040305380`): **102 selected tests passed**
  in **51.051 seconds** on the final installed APKs: 42 pipeline, seven presentation,
  39 Canvas lifecycle, 13 Mophun presentation and one installed-game check.
- Android emulator (`emulator-5554`, Google SwiftShader): **49 GPU tests passed**
  in **121.334 seconds**. This was offscreen rendering/presentation verification,
  not emulator UI or installed-game acceptance; no unrelated app was dismissed.
- New pixel cases cover black/white/colored backgrounds, 1x1 through 3x3 sprites,
  positive/negative horizontal/vertical/diagonal moves, odd displacement, partial
  tiles, boundary crossing, HUD/new/disappearing occluders, stationary detail
  duplication, conflicting neighbors and rejected local matches.
- Existing scene cuts, dense scrolling, rounding, source deadlines, load protection,
  padding, FSR, rotation, capture and lifecycle tests remain in the selected run.
- Deep Abyss again reached its first-level introduction with GPU effects, touch
  controls, rotation/LCD preview and restoration to Canvas. This is not a full
  level, physical-controller, audio or sustained gameplay acceptance result.

The optional `enhancementCapture=true` test produces a three-panel comparison
from source pixels and actual Retroid GPU readback, enlarged without filtering.
It is a synthetic rendering fixture, not a commercial-game screenshot or a
measurement of perceived game smoothness.

## Cost and Limits

Existing 240x320 to 720x960 synthetic benchmark, 30 timed samples after five
warmups, submission plus test-only glFinish, texture-output reference path:

| Effects | Median ms | P95 ms |
| --- | ---: | ---: |
| Off | 0.120 | 0.152 |
| FSR | 9.620 | 10.638 |
| Interpolation | 2.782 | 3.182 |
| Both | 11.429 | 11.993 |

The separately measured static-source workload with both effects took 5.887 ms
median / 6.800 ms P95. Sparse detection scans otherwise uniform tiles too; it is
not free. These are not paired old/new measurements, locked-clock comparisons,
guest FPS or input-latency measurements.

Dense/textured backgrounds, several independent motions inside one tile,
objects covering background corners, complex occlusion, motion beyond four
pixels and guest-image edges may remain unsmoothed. Conservative fallback can
mix interpolated regions with real regions. The 50 ms algorithmic window remains;
GPU/host delay can be additional. No artifact-free or universal 60 FPS claim.

## Local Evidence

Ignored logs and diagnostic captures are not public game artifacts:

- `build/sparse-motion-retroid-before.txt`: two failures out of three before repair.
- `build/sparse-motion-retroid-targeted.txt`: first pass required adapting one
  assertion from exact confidence-channel zero to the unchanged rejection threshold;
  the channel now carries sparse-eligibility metadata as well as confidence.
- `build/sparse-motion-retroid-expanded.txt`: 41 GPU checks passed.
- `build/sparse-motion-retroid-final.txt`: first broad run exposed the surface race.
- `build/sparse-surface-retroid-before.txt`: detached-surface capture regression failed.
- `build/sparse-motion-final-build.txt`: initial assembly, fresh JVM run and lint.
- `build/sparse-motion-complete-build.txt`: final surface-repair assembly and lint.
- `build/sparse-motion-retroid-complete.txt`: final 102-check passing device run.
- `build/sparse-motion-emulator.txt`: final 49-check passing SwiftShader run.
- `build/sparse-motion-comparison.png`: synthetic actual-GPU intermediate frame.
- `build/sparse-motion-deep-abyss.png`: installed-game first-level introduction.

Installed local Debug APK SHA-256:
`1a71a750bfc222b3f25162d846dab7dd3896830f90ee43d1b7b4d2c9b643a595`.

Instrumentation APK SHA-256:
`539530cc82ded4d3fae348c168195846658825dddcc688b728f6b7202ccf1664`.

The reused development filename/version does not identify an updated public APK.
If local evidence is inaccessible, these are dated maintainer records, not newly
reproduced acceptance checks.
