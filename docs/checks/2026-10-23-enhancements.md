# Experimental Enhancements - 2026-10-23

## Availability and Scope

Unreleased, opt-in **Debug-only** implementation. The public Beta 3 Revision 1
release page was checked; no APK, source tag or release asset was changed.
The installed package is `io.github.eriark.abyssme.debug`, separate from the
signed application and its library. Property-free build: Java + Mophun, without
Symbian. No games, firmware or private diagnostics are included in Git.

See [behavior, provenance and limits](../EXPERIMENTAL_ENHANCEMENTS.md).
Related issue #26 remains open: this does not complete verified per-game native
FPS improvements, automatic profile selection or cross-engine enhancement support.

## Build and Regression Evidence

- `:app:testFdroidDebugUnitTest`: 53 tests, zero failures/errors.
- `:app:assembleFdroidDebug` and `:app:assembleFdroidDebugAndroidTest`: passed.
- `:app:lintFdroidDebug`: zero errors, 400 warnings; not a warning-free build.
- Retroid Pocket Classic, ARM64 Android: 61 instrumentation checks passed.
- Android 15 x86_64 emulator: the same 61 instrumentation checks passed.

The Android selection covers eight pipeline checks, 34 Canvas lifecycle checks,
nine settings UI checks, six profile serialization checks and four Mophun rate/
clock/budget checks. It includes GPU pixel readback (not merely successful shader
compilation), solid black/white/colors, viewport padding/corners, a translated
edge at intermediate time, cuts/history reset, target resize, output caching,
minification, non-integer scale and allocation bounds. Live Canvas surfaces use
PixelCopy through rotation, resize, effect toggle, foreground pause/resume and
surface recreation; the enhancement path must remain active, not silently fall
back. Old profiles default off/100%, new values round-trip and Undo restores them.

Mophun checks prove separation of configuration, clock and frame cap, not a
measured game-wide CPU speedup. Release guards are implemented, but a fresh
signed/R8 release build was not produced in this checkpoint.

Local final Debug APK:
`app/build/outputs/apk/fdroid/debug/AbyssME-0.4.0-beta.3-r1-debug.apk`

SHA-256:
`775466c9b0b2fd613cc19462770d7d0556dcc3048c3df21e7b798baf93923d90`

The filename retains the existing development version; this is not the APK
published under the similarly named public release.

## Retroid GPU Cost

Synthetic alternating 240x320 images, 720x960 output, 35 samples per mode with
five warmups excluded. Each timed sample includes upload/draw and `glFinish`.
The interpolation measurement includes fresh-frame motion estimation. It is
not a complete game frame, battery measurement or prediction of achievable FPS.

| Pipeline path | Median | P95 |
| --- | ---: | ---: |
| Crop-only pipeline baseline | 0.42 ms | 0.48 ms |
| FSR EASU + RCAS | 10.17 ms | 10.67 ms |
| Motion estimation + resolve | 4.47 ms | 5.14 ms |
| Motion + FSR | 12.86 ms | 13.70 ms |

The crop-only path is a benchmark reference, not the application's original
renderer. FSR cost is material on this device; larger output and game rendering
can exceed a 16.7 ms frame budget. Emulator GPU timings are host-dependent and
are not handheld performance evidence. Completed unchanged frames are cached;
these numbers intentionally measure fresh input instead.

## Real Game / Visual Check

An intermediate Debug candidate was driven with ADB on Retroid: opened Bounce
Tales, enabled both effects through the actual Enhancements page, applied Try,
and advanced through New game / chapter selection to the first level intro.
The menu, chapter art and first-level image were inspected in screen captures.
This establishes a launch/live-application smoke check, not sustained gameplay,
input-latency, physical-controller comfort or audio acceptance. The final binary
above was subsequently installed and passed the full device selection.

Local ignored evidence: `build/enhancements-2026-10-23/`, containing final build
log, `retroid-final.txt`, `emulator-final.txt`, intermediate settings/game captures
and the inspected upstream FSR header. If unavailable, treat these as dated
maintainer observations, not independently revalidated evidence.

## Remaining Work

- Interpolation quality under fast scrolling, occlusion and repeating textures;
  reduce the up-to-100 ms presentation delay before recommending action games.
- Frame-time comparisons in matching real scenes; sustained thermal/battery and
  input response checks. No full-game or universal compatibility acceptance.
- FSR comparisons against original/nearest/smooth rendering across small text,
  dithering, 2D and 3D; no claim that FSR always looks better for pixel art.
- Genuine guest-update improvements where feasible, separate from synthetic
  frames. Java has no phone-CPU frequency knob in the current ART architecture.
- Proper GPU adapters for Mophun/Symbian and independently measured Symbian CPU
  controls; neither is represented as implemented by this prototype.
- Curated result-oriented profiles and a release decision. Do not announce
  automatic enhancements or universal 60 FPS based on these checks.
