# LCD Export and Pixel Alignment

2026-10-19. Local development after 0.4.0 Beta 2; not a published release.

## Changes

- Per-game Clean / With LCD effect screenshot selection in shared Display.
- Immutable shutter-time effect settings and shared bounded PNG composition for
  Java, Mophun and Symbian/N-Gage; no touch controls, app menu or letterbox.
- Mophun capture follows selected rotation. Java raw capture owns its bitmap
  rather than potentially sharing the live buffer; logical cropping is retained.
- A reported uneven grid exposed minified thin-line aliasing in the repeating
  mask. Replaced it with cached, pixel-aligned batched lines, without a complex
  intersecting path. Mophun uses logical guest dimensions rather than its HD buffer.
- Explicit bitmap sampling flags preserve the Smoothing selection during export.

## Verification

- Base and combined debug/instrumentation APKs built successfully. Fresh JVM
  run: 39 passes. Base and combined lint completed without errors before the
  final line-batching refinement; final APKs were rebuilt after that refinement.
  Logs: `build/lcd-export-base-build.log`, `build/lcd-export-combined-final.log`,
  `build/lcd-export-grid-final-build.log`, `build/lcd-export-grid-combined-final.log`.
- Android 15 emulator: 18 focused tests passed on the final implementation
  (`build/lcd-export-emulator-final-tests.log`). Coverage includes clean/off/zero
  identity, bounded export, immutable shutter settings, image ownership, PNG
  readback, profiles/Undo, clipping, rotation, fractional-grid uniformity and
  alignment with scaled source pixels. Rasterization allows one 8-bit rounding
  step between horizontal and vertical lines, not periodic intensity variation.
- The original checkerboard test accidentally retained Android's default bitmap
  filtering; explicit `setFilterBitmap(false)` corrected the fixture and the
  same explicit sampling choice is used by screenshot export.
- An emulator UI attempt lost responsiveness and was restarted. A subsequent
  attempt exported PNGs but failed because its assertion did not scroll the
  landscape Display page. The harness now reveals the setting before checking
  it. Neither failed attempt counts as a completed UI pass.
- Final Java Sudoku emulator UI flow passed in 79.332 s: Clean/LCD gallery
  exports, dimensions, rotation, Undo and preference persistence after relaunch
  (`build/lcd-export-java-final-ui.log`).
- Final Mophun Retroid batch passed all three optional flows in 182.592 s:
  presentation samples, gallery exports/rotation/persistence, and live LCD
  selection/Undo/relaunch (`build/lcd-export-mophun-final-ui.log`).
- Final Retroid batch passed 19 checks in 49.279 s: the same 18 focused tests
  plus native Snakes export, rotation, Undo and relaunch persistence
  (`build/lcd-export-retroid-final-tests.log`). Captures show startup screens,
  not a gameplay or complete-game acceptance checkpoint.
- Actual final Retroid capture, blue scanline y=200 across x=312..927: 128 gap
  samples all have blue=97; 488 interior samples all have blue=132. This confirms
  equal line intensity at the real 616/128 = 4.8125x Fit scale, instead of the
  previous periodically varying dark values. Tests also compare alignment to a
  scaled checkerboard, rather than only testing the mask against itself.

[Fresh live captures and game-only PNG exports](../../screenshots/development/2026-10-19-lcd-export/README.md).

## Presentation Sample

Final batched-line implementation, Retroid Mophun menu: four eight-second samples
after six seconds settling. This measures Android presentation, not guest FPS.

| Mode | Frames | Janky frames | p95 | p90 GPU |
| --- | ---: | ---: | ---: | ---: |
| Off, first | 483 | 0 | 17 ms | 15 ms |
| Classic | 482 | 0 | 5 ms | 3 ms |
| Soft | 482 | 0 | 5 ms | 3 ms |
| Off, repeat | 482 | 0 | 5 ms | 2 ms |

No extra jank was observed. The first sample is not a fair speed comparison;
these numbers do not establish universal zero overhead or busy-game performance.
Local records: `build/lcd-export-performance/`.

## Availability and Limits

Only isolated debug packages are updated; no production-app replacement, GitHub
push/release, website or Nextcloud upload. Live GitHub release state was not
refreshed during this implementation. Automatic artwork remains unfiltered.
Native/UI checks use existing private fixtures and do not establish full-game,
audio, save or physical-controller acceptance. Android 10 hardware was not tested.
