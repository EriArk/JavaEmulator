# Image Presets and Sharpness Comparison - 2026-10-23

## Scope and Availability

Unreleased Java/Mophun source and local Debug builds only. No public APK, release
asset, version number, GitHub Actions setting or Symbian rendering path changed.
Experimental rendering remains opt-in and Debug-only.

The reported soft image was investigated using a frozen Deep Abyss 1.4 source
frame (128 x 160), not screenshots of different animation frames. Each device
rendered the same local source at 3x through the shared GLES pipeline for all
three presets. The labelled comparison sheets are composed diagnostics, not
screenshots of an app layout. Commercial game data and these local captures are
not bundled in the repository.

## Findings and Implementation

- Pixel sharp uses nearest-neighbor sampling without FSR. Smooth uses linear
  filtering. Enhanced (FSR) uses the existing EASU and fused RCAS path at 50%
  sharpening, without an additional linear filter.
- The small source already contains shaded text. FSR visibly softens pixel-art
  edges relative to nearest-neighbor output; this is not confined to one title.
  A synthetic black/white checkerboard and thin-line fixture reproduces it.
- At exact 4x magnification of that 32 x 24 fixture, intermediate-color counts
  were 0 / 8,384 / 7,866 out of 12,288 pixels for Sharp / Smooth / FSR on both
  devices. This counts resampling, not overall image quality. The Sharp test
  additionally checks every output pixel against its exact source texel.
- Repeated unchanged source frames produced identical FSR output with and
  without an intermediate presentation frame. This does not rule out artifacts
  on moving content or establish motion quality for every game.
- Reviewed EASU edge-direction/length calculations against the pinned
  [AMD reference header](https://raw.githubusercontent.com/GPUOpen-Effects/FidelityFX-FSR/a21ffb8f6c13233ba336352bdff293894c706575/ffx-fsr/ffx_fsr1.h).
  No double-filtering or formula defect was established in this investigation;
  no shader formula or per-title workaround was changed.
- Add derived presets and Custom/Fine tuning to the shared Enhancements page.
  Preserve imported values and the existing per-game Try / Keep / Undo workflow.
  No new stored preset field or migration is necessary.
- Presets only change spatial filter/FSR/sharpness settings. They do not enable
  interpolation, increase CPU budget, change speed/FPS, remove custom shaders,
  switch renderers, or change size/rotation/LCD effects. Unsupported Java GPU
  configurations omit Enhanced and retain the existing renderer.
- Pixel sharp is not integer scaling. Fractional Fit magnification can still
  give source pixels uneven physical widths. Existing defaults are unchanged.

## Verification

Build logs and device captures are local ignored artifacts under `build/`.

- `image-presets-build.txt`: app/test Debug assembly, 71 freshly rerun JVM tests
  (0 failures/errors), and lint passed. `image-presets-test-build.txt` contains
  the final instrumented APK rebuild after adding the live-game menu check.
- `image-presets-final-lint.txt`: final lint passed, 0 errors / 400 warnings.
  `image-presets-final-test-build.txt` also passed test APK assembly and lint
  after adding a bounded landscape-layout wait to the screenshot test.
  `image-presets-readiness-build.txt` passed assembly and lint after replacing
  the fixed GPU-startup sleep with a bounded first-frame readiness check.
  Capture synchronization was then rebuilt in `image-presets-capture-final-build.txt`;
  the preceding `image-presets-capture-sync-build.txt` lint run also passed.
- Retroid Pocket Classic `MC94516AQF040305380`:
  `image-presets-retroid.txt` passed 21 tests in 15.353 s (GPU presentation,
  preset model/persistence, shared menu UI).
- `image-presets-retroid-game.txt` passed the optional installed Deep Abyss
  menu/preview test in 7.730 s. All three presets applied, Sharp/Smooth returned
  to Canvas, Enhanced presented through GLES, and CPU/motion settings remained
  unchanged. Portrait/landscape menu captures were visually inspected.
- `image-presets-retroid-regression.txt` passed 18 tests in 15.892 s (Java
  resize/rotation/context recreation, Mophun presentation and serialization).
  Total: 40 distinct selected Retroid checks.
  The final menu-test rerun passed in 7.938 s
  (`image-presets-retroid-menu-final.txt`).
  With first-frame synchronization, the same menu test passed in 5.075 s
  (`image-presets-retroid-readiness.txt`).
  Final capture synchronization passed in 5.108 s
  (`image-presets-retroid-capture-final.txt`).
- Android 15 x86_64 `emulator-5554`: the first instrumentation launch failed
  before tests, with a process-startup ANR. Exit info and
  `image-presets-startup-anr.txt` place the main thread in Android
  `ActivityThread.handleBindApplication` / `BaseBundle.getInt`. No application
  fix or timeout relaxation was made on this evidence. The unchanged APK was
  retried; `image-presets-emulator-retry.txt` passed 18 selected tests in 476.890 s:
  preset model/persistence, same-frame GPU comparisons, Java context lifecycle,
  shared menu and optional real-game menu. This is not a performance benchmark.
  The first landscape capture raced the orientation change and did not show the
  menu. The test now waits for landscape layout before opening it; no product
  behavior was changed to mask a screenshot timing problem.
  After reinstalling the final test APK, its first launch again failed before
  test execution with a process-startup ANR (PID 6112, 14:31:19 device UTC;
  `image-presets-emulator-menu-final.txt`). Do not report emulator startup as
  fully reliable based on the successful preceding test run.
  Its subsequent rerun (`image-presets-emulator-menu-final-retry.txt`, 84.189 s)
  reached the test but failed the GPU `presented != null` assertion after a
  fixed sleep and screenshot. The test now waits for the asynchronous first
  frame before requesting a potentially slow screenshot; the assertion and
  renderer-failure checks remain, with a 10-second readiness bound.
  `image-presets-emulator-readiness.txt` then passed preset/GPU assertions but
  failed the landscape wait (186.867 s total). Capture timing can exceed the
  ordinary 20-second preview countdown and undo the fixture settings. The
  optional capture test now stops only its countdown after asserting Try, while
  retaining the profile baseline and restoring it in `finally`. Product preview
  timing and behavior are unchanged; this test does not verify timed auto-undo.
  **Final emulator acceptance remains open:**
  `image-presets-emulator-capture-final.txt` failed its bounded GPU first-frame
  assertion (83.364 s total). Inspection of the capture from that very run,
  `emulator-mophun-image-preset-0.png`, revealed Android's **PicotronNest
  Execution Lab isn't responding** system dialog over AbyssME. This external
  modal invalidates unobstructed focus/presentation acceptance for the rerun.
  No other application's processes, data or dialog were modified. Do not
  attribute every earlier timeout to this dialog without further evidence, or
  call the final emulator rerun green. Retroid's final test passed; the earlier
  18-test emulator pass and same-frame offscreen comparisons remain recorded
  separately. The stale emulator landscape capture is not accepted UI evidence.

Local visual evidence:

- `image-presets-retroid-game.png`: identical captured game frame, three filters.
- `image-presets-retroid-pixels.png`: synthetic pixel/line comparison.
- `retroid-mophun-image-presets-menu.png` and
  `retroid-mophun-image-presets-landscape-menu.png`: actual in-game settings UI.
- `retroid-mophun-image-preset-0.png` and `retroid-mophun-image-preset-2.png`:
  actual Sharp/Enhanced game screens with the touch keypad and preview controls.
- `image-presets-emulator-game.png` and
  `emulator-mophun-image-presets-menu.png`: emulator comparison and light-theme
  portrait menu, also visually inspected.

Automated device execution and visual inspection are not physical-controller,
audio, sustained gameplay, complete-game or all-games motion-quality acceptance.

## Local Artifact Identity

- `app/build/outputs/apk/fdroid/debug/AbyssME-0.4.0-beta.3-r1-debug.apk`
  SHA-256: `45862b0d78c8bc69fe3f1f6bbb23efcb5305ab5990f8f62926a1ae0c9cea9c85`
- `app/build/outputs/apk/androidTest/fdroid/debug/app-fdroid-debug-androidTest.apk`
  Final SHA-256: `196df7dde0db7da00a61f86feadeb1775dca094de48e7541d126bdc75b407ad8`
  The preceding full test runs used
  `9e37917a8b2dcaf64b84dca5ca929f157f3bd9f3f6a98b049145ec901ac61195`;
  only test rotation/first-frame/capture synchronization changed afterwards.

These artifacts were installed locally for the checks, not uploaded as a release.
