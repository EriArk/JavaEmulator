# LCD Effects: Implementation and Verification

Date: 2026-10-19. Local development after published 0.4.0 Beta 2, not a new
release. Installed only isolated debug packages; production APK/data unchanged.

Historical initial checkpoint: the repeating-texture mask below was replaced
after a fractional-grid report, and optional filtered PNG export was added.
See [the follow-up checkpoint](2026-10-19-lcd-export.md) for current behavior,
fresh grid evidence and performance scope. Older captures remain historical.

## Implemented

- Shared Off / LCD Soft / LCD Classic selection and 0-100% intensity in Display.
- Stable serialized profile keys, default Off, reversible settings previews,
  per-game persistence and live application without a renderer restart.
- Original cached BitmapShader mask, no third-party shader code or new licenses.
- Java surface overlay, Mophun bitmap presentation and native TextureView mask,
  using actual guest size rather than the Android viewport for the grid.
- Rotation, clipping and small-scale grid attenuation; no effect on menu/input
  pixels. Native guest geometry is sampled at most five times per second while
  enabled. No guest-frame readback or new render thread.

## Checks

- Base and combined debug app/instrumentation builds passed. Fresh JVM run:
  39 passes. Base lint: zero errors, 403 warnings; combined lint: zero errors,
  413 warnings. Logs: `build/lcd-base-build.log`, `build/lcd-build.log`,
  `build/lcd-combined-checks.log`.
- Retroid Pocket Classic / Android 14 and Android 15 x86_64 emulator each passed
  34 focused instrumentation tests: LCD pixel behavior, rotation, profile
  serialization, preview bar, game menu and touch deck. Includes six new LCD
  checks for exact Off/zero behavior, unknown values, clipping, rotation/stretch,
  attenuation, cached mode changes, old profiles, Keep/save/reload and Undo.
  Logs: `build/lcd-retroid-regressions.log`, `build/lcd-emulator-regressions.log`.
- Retroid Mophun UI: Off/Soft/Classic, Keep, Undo, rotation and saved selection
  after exit/relaunch passed (43.926 s). The private fixture filename is
  `DeepAbyss14.mpn`; its filename is not treated as verified game identity.
  Log: `build/lcd-mophun-ui.log`.
- Retroid native Snakes UI: the same flow passed (39.068 s), using the existing
  virtual phone. Captures show startup screens, not full gameplay acceptance.
  Log: `build/lcd-native-ui.log`.
- Emulator Java Sudoku J2ME: the same flow passed (65.095 s), including changing
  the effect on the OpenGL surface and reloading its saved profile.
  Log: `build/lcd-java-ui-complete.log`. Initial harness attempts used an
  inaccessible Downloads path and an old empty JAR, then an overlapping runner
  interrupted a trial. Those attempts are not counted as passes. The final run
  used the existing converted game; no installer/import behavior was altered.
- Final Java capture flow passed again (83.291 s), with the library Activity
  owning the installed-game launch. Log: `build/lcd-java-ui-final-captures.log`.
- Screenshot review exposed Android's full-surface focus highlight on the
  clickable Mophun frame. Disabled that highlight only on the game surface;
  menu/button focus indicators remain unchanged.
  Rebuilt combined debug successfully (`build/lcd-focus-build.log`) and reran
  the complete Mophun UI flow: passed in 55.412 s
  (`build/lcd-mophun-ui-focus-fixed.log`). The final Mophun captures use this build.
  Comparing final Off/Classic PNGs on a four-pixel sampling grid: all 53,978
  samples outside the game rectangle were identical; 29,702 inside changed.
  The game rectangle was x=312..927, y=155..924 in the 1240x1080 captures.

## Retroid Presentation Sample

The optional instrumentation benchmark recorded Android `gfxinfo framestats`
for four eight-second Mophun menu samples, with six seconds settling before
each sample. This measures app presentation, not guest FPS or busy-game CPU cost.

| Mode, in run order | Frames reported | Janky frames | p95 frame | p90 GPU |
| --- | ---: | ---: | ---: | ---: |
| Off (first sample) | 488 | 0 | 17 ms | 12 ms |
| LCD Classic | 482 | 0 | 8 ms | 2 ms |
| LCD Soft | 481 | 0 | 8 ms | 2 ms |
| Off (repeat) | 481 | 0 | 8 ms | 2 ms |

No presentation regression was observed in this sample. The slower first Off
sample shows why these numbers must not be presented as an LCD speedup. They
do not establish universal zero overhead, performance on busy 3D games, battery
impact or native/Java GPU timing. Raw local records: `build/lcd-performance/`;
runner log: `build/lcd-mophun-performance.log`.

## Captures and Limits

[Unedited device/emulator captures](../../screenshots/development/2026-10-19-lcd-effects/README.md)
show the actual presentation and menu, not generated concepts. Native startup
screens advance between captures, so they are not identical-frame comparisons.
Automated pixel tests use identical generated inputs. No commercial game or
firmware files are included in Git.

Android 10 hardware, Android 16, tactile controller acceptance, audio and full
campaigns were not tested here. Built-in game-frame screenshots and automatic
artwork remain unfiltered. No public APK, website or Nextcloud distribution.
