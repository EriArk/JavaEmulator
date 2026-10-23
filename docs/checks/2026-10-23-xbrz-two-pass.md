# Two-pass xBRZ - 2026-10-23

## Shared mechanism

The original freescale shader repeats neighborhood reads, YCbCr distances and
four-corner classification for every enlarged output pixel. The classification
depends on the guest neighborhood, not the subpixel position or output size.

The shared Java/Mophun GLES pipeline now emits those decisions once into a
source-sized RGBA8 texture. Each channel encodes one corner's active/line/shallow/
steep/neighbor decisions. The resolve pass uses the original `get_left_ratio`,
source colors, edge thresholds and z/w/y/x blend order at the full output size.
Metadata is nearest-filtered and drawn without dithering. No CPU readback or
lower working resolution is introduced. CRT EasyMode is untouched.

Use the split path only when neither axis shrinks and output pixel count is at
least four times source pixel count. Keep the single-pass reference for small
scales. Decision storage is four bytes per source pixel (80 KiB at 128x160,
300 KiB at 240x320), within the existing bounded source size. It is invalidated
on uploads, source/mode changes and history reset, but reused on output-only
resizes. GL context destruction disposes/recreates its resources normally.

Original notices remain in `xbrz.glsl`; adaptation details and the derived
resolve asset are listed in `assets/licenses/RETRO-SHADERS.txt`. Debug-only
availability, per-game profiles, Try/Keep/Undo, controls and guest speed remain
unchanged. No release or APK publication in this checkpoint; public release
state was not rechecked.

## Retroid evidence

Device: Retroid Pocket Classic, Android 12, `MC94516AQF040305380`.

- **85 focused checks passed**, no failure or skip statuses, in 59.820 s:
  EnhancementPresentationTest (15), CanvasLifecycleTest (40),
  MophunPresentationTest (13), ImagePresetsTest (4), GamePresetsUiTest (13).
- An additional installed Deep Abyss preset/menu test passed in 11.735 s with
  two game confirmations, GPU readiness checks and final profile rollback.
  This covers live preset changes and level-screen progression, not full-game
  navigation, controller usability or save/audio acceptance.
- 75 JVM tests passed on a fresh `--rerun` task invocation.
- Debug app/test assembly and lint completed successfully (0 errors, 399
  warnings). The same-runtime APK was repackaged with the updated shader
  provenance notice and installed on Retroid after the test runs.
- The production output still matches the owner-selected 384x480 audition
  image exactly: 0 of 184,320 pixels changed.
- Comparing single/split output for the private frame at 384x480 and 576x720:
  0 of 2,396,160 RGBA channel values changed.
- Synthetic fixtures include blocks, dense palettes, random RGB, grayscale
  gradients, odd dimensions, anisotropic/fractional scales, 1x1/1xN/Nx1 borders
  and 240x320 to 720x960. Of 3,146,080 compared channel values, 224 differ by 1;
  maximum difference is one 8-bit code value (1/255).
- Cache tests cover same-frame resize, changed guest pixels, mode switches,
  reset and the small-scale reference path. Existing crop/rotation and real
  Java SurfaceView recovery tests run against the optimized implementation.

Android 15 x86_64 emulator `emulator-5554`: **3 checks passed** in 155.577 s:
the full synthetic reference comparison, analysis-cache lifetime, and
crop/resize/rotation/mode switching. All 3,146,080 synthetic RGBA channel values
matched exactly on this renderer. These are offscreen GPU checks, not gameplay
or performance acceptance. No emulator reset or data wipe was used.

## Paired processing cost

Same Retroid context; 6 warmups + 20 measured frames, alternating AB/BA order,
`glFinish` around each sample. Both upload the same pixels using texSubImage.
The split measurement includes the final presentation copy; the reference
draws straight into the comparison target, making this a conservative comparison.
No thermal steady-state or whole-game FPS measurement is implied.

| 128x160 to 576x720 | Reference median / p95 | Split plus copy median / p95 |
| --- | --- | --- |
| Captured Deep Abyss frame | 44.206 / 45.740 ms | 6.887 / 7.617 ms |
| Dense synthetic palette | 71.476 / 76.623 ms | 9.474 / 10.507 ms |

The older mostly-flat synthetic fixture now costs 2.095 ms median / 2.542 ms
maximum for upload plus shader, without the final presentation copy. Its prior
14.96 ms result is a separate historical run, not a paired performance claim.
Extra source-sized work may not help small scales, hence the reference-path
threshold. Performance on other GPUs and games can differ.

## Reproduction and local artifacts

App/test builds: `:app:assembleFdroidDebug :app:assembleFdroidDebugAndroidTest`.
Opt-in private frame arguments: `retroShaderReview=true` and
`imageFixture=retro-review/source.png`. The source frame and game are maintainer-
supplied files, never source/APK assets. Without these arguments, fixture-dependent
review tests are skipped; generated synthetic coverage remains available.

Ignored maintainer logs:

- `build/xbrz-split-build-final.txt`: app/test assembly and fresh JVM execution.
- `build/xbrz-split-retroid-final.txt`: raw instrumentation statuses, parity
  counters and paired timings.
- `build/xbrz-split-mophun.txt`: installed Deep Abyss fixture.
- `build/xbrz-split-emulator.txt`: three emulator checks and parity counters.
- `build/xbrz-split-package-lint.txt`: final Debug packaging and lint.
- `build/xbrz-split-production.png`: production same-frame output, private.

Final locally installed Debug APK SHA-256:
`12d1e0c3d40b87288433c3259f9f9676651cf1ffe8102db33a6a380b96a61bcb`.
The filename retains the development base version, not a new public release.

Owner review of sustained motion, broader device coverage and public-release
promotion remain separate. No new combination with FSR/interpolation is enabled.
