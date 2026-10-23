# Intermediate Frame Rounding and Ambiguity - 2026-10-23

## Scope and Availability

Unreleased shared Java Canvas/GameCanvas GLES and Mophun GLES presentation.
FSR/interpolation remain Debug-only, opt-in and off by default. Ordinary output,
guest timing/input, image presets and Symbian rendering are unchanged. No public
APK, release tag, Nextcloud upload or GitHub Actions change. The live public
release listing was not rechecked in this checkpoint.

## Reproduced and Fixed

Two new GPU tests ran against the previously installed `a215eb66` Debug app on
Retroid Pocket Classic and both failed:

- Odd-pixel scrolling produced a wrong intermediate pixel at (18, 8) in a
  31x23 viewport with a padded 32x32 source. Resolve computed fractional source
  coordinates independently, leaving nearest-filter boundary selection to GPU
  rounding. It now snaps the guest coordinate and displacement once and derives
  the other frame's sample using the same integer motion vector.
- A periodic stripe texture had equally valid +2/-2 matches but reported full
  confidence. Search order/distance bias selected an unsupported direction. The
  existing search now accumulates minimum raw errors by axis/sign; an opposite
  candidate within 0.002 mean RGB error rejects confidence. No extra texture
  reads or additional search pass. The real current pixel is retained.

A separate deadline repair covers live pass preparation. Previously the phase
used the timestamp obtained before shader compilation, allocation and motion
search submission. Both production callers now use a shared live-clock entry
point that samples time again before resolve. An expired pair returns the current
real frame and stops intermediate redraws. Explicit-time reference paths remain
deterministic. An injected clock exercises 37.5, 50 and 100 ms of preparation
time, verifying intermediate position, deadline completion and no rewind on a
later reference draw. These are controlled timing cases, not measured driver
stall durations or input-to-photon results.

## Verification

- Debug app and instrumentation APK builds succeeded.
- Fresh JVM run: **75 tests**, zero failures/errors.
- Lint: **zero errors, 399 warnings**; not warning-free.
- Retroid Pocket Classic, serial `MC94516AQF040305380`: **94 selected checks**
  passed in **49.524 seconds** on the final installed app/test APKs.
- Includes 35 pipeline tests, seven presentation tests, 39 Canvas lifecycle
  tests, 12 Mophun presentation tests and one optional installed-game check.

New pixel coverage includes +/-1 and +/-3 horizontal/vertical motion, odd
diagonal motion at quarter/half/three-quarter/completed phases and opposite
periodic matches on both axes. Existing even motion, unique textured scrolling,
HUD/new/disappearing pixels, scene cuts, borders, padding, load suppression,
FSR/sharpening, rotations and preview/capture checks passed as well.

The optional Deep Abyss fixture used FSR + interpolation, portrait touch keys,
landscape controls/LCD, menu opening and restored Canvas output. A synthetic
A-button press reached the first-level introduction. Captured output was visually
inspected. This is not a full level, physical-controller, audio, thermal or
sustained-FPS acceptance result. The emulator had an existing unrelated app ANR
dialog; no emulator test or unrelated-app modification was performed here.

## Cost and Limits

Existing 240x320 to 720x960 synthetic benchmark, 30 timed samples after five
warmups, CPU submission plus test-only `glFinish`, texture-output reference path:

| Effects | Median ms | P95 ms |
| --- | ---: | ---: |
| Off | 0.129 | 0.145 |
| FSR | 9.833 | 11.359 |
| Interpolation | 2.938 | 3.261 |
| Both | 11.482 | 12.582 |

This is not a paired before/after performance study and does not establish game
FPS or an FPS gain. The algorithmic interpolation window is still capped at
50 ms. Subsequent GPU, FSR and host/compositor work can add latency. No new frame
queue, extrapolation or guest-clock acceleration was added.

Ambiguous periodic areas deliberately lose synthetic smoothing. Four-pixel
search radius, block-level motion, thin objects on unchanged backgrounds and
occlusion remain limits. Do not describe this as artifact-free frame generation
or universal 60 FPS support.

## Local Evidence

Ignored files, not redistributed game data or public release assets:

- `build/motion-rounding-retroid-before.txt`: both regressions failed before repair.
- `build/motion-rounding-retroid-targeted.txt`: first shader repair, 34 checks.
- `build/motion-rounding-final-build.txt`: assembly, fresh JVM run and lint.
- `build/motion-rounding-retroid-final.txt`: final 94-check device run.
- `build/motion-rounding-deep-abyss.png`: first-level introduction with touch keys.

Installed local Debug APK SHA-256:
`9c727a2d11759f6bf9f5b9766c7a9dcf24a867654e76d004f4d7cf14bbe68dff`.

Instrumentation APK SHA-256:
`68bb1bfd58ce5986b1d772df5be92f76a80376f13d917b88ecd87c07abdbdad6`.

The app filename/version is reused for development and is not an updated public
Beta 3 Revision 1 artifact. If local logs are unavailable, treat these results
as dated maintainer evidence rather than newly reproduced checks.
