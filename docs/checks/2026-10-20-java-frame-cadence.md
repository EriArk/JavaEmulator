# Java frame cadence and Metal Slug investigation

Date: 2026-10-20. Source baseline: `ef485e22`.
Unreleased Java-only rendering changes; no public APK or release asset update.
Retroid is not connected. Runtime evidence below is from the Android 15 x86_64
phone emulator, not handheld performance acceptance.

## Fixture identity and boundary

The documented Retroid collection contains **Metal Slug Mobile Impact**, not
Metal Slug 3. The exact staged fixture is `MetalSlugMobileImpact_S40v3.jar`,
MIDlet version 0.0.6, I-play, SHA-256
`fa2852ef2b9a87a29284cbe9bd096db0130fdb1cdb83ae035cdc7fa94e70ff72`.
See [collection provenance](2026-10-16-retroid-collection.md).

Local bytecode inspection found a 100 ms deadline per iteration in the game's
`I.run()` loop. It updates the game and calls repaint/serviceRepaints, then yields
until that deadline. This is approximately ten logic updates per second, not a
60 FPS game slowed down solely by Android. A second supplied build,
`MetalSlugMobileImpact.jar` version 1.0.2, also budgets 100 ms, using sleep.
The proprietary JARs and bytecode dumps remain ignored; no game patches or
title-specific production branches were introduced.

On the installed baseline Debug APK, SurfaceFlinger reported 80 presentations
over the approximately eight-second gameplay/tutorial sample, 9.91 FPS. After
the shared changes it reported 80 presentations, 9.90 FPS in the same opening
gameplay scene. This confirms the game-owned cap remains intact, not a claimed
performance gain. These are presentation counts, not unique-animation counts,
and not comparative Retroid benchmarks.

## Shared fixes

1. `FramePacer` previously anchored each deadline to the actual wake-up time.
   Small scheduler oversleeps therefore accumulated permanently. It now retains
   the deadline cadence through sub-period lateness, rebasing after a whole missed
   period rather than building a catch-up backlog. Per-canvas state, live limit
   changes, unlimited mode, interruption and pause/reset behavior are preserved.
2. Parallel software/hardware Canvas presentation previously removed queued
   redraw messages *after* posting the current frame. A producer could submit
   a newer frame while that post was in progress and have its request deleted.
   `FramePresenter` coalesces pending requests, releasing the queued slot before
   presentation so the running frame cannot erase its successor.

The pacer is shared by Canvas repaint, GameCanvas flush and ExtendedImage flush.
The presentation fix applies to parallel software/hardware modes, not GLES or
Android View invalidation. No default renderer changes, artificial CPU frequency,
game-time acceleration, audio-speed changes or new user setting.

## Verification

- Fresh JVM suite: **49 passed**, including deterministic jitter, missed-period
  recovery, coalesced bursts, requests during a blocked presentation and failure
  recovery.
- The new jitter test was also compiled against the unmodified baseline pacer:
  **one expected failure**. At 20 FPS over 200 intervals with 2 ms oversleep per
  wait, the baseline takes 10.400 seconds; the correction takes 10.002 seconds.
  This synthetic proof is not a measured hardware FPS uplift.
- Final phone-emulator suite: **37 passed** in 38.212 seconds. Includes Canvas
  lifecycle/resize/direct-flush/GLES recreation, shared fast-forward UI, converted
  guest clocks/timers, concurrent producer pacing and main-Looper redraw races.
  Actual 120 intervals at a 60 FPS cap took 2002.126 ms; 20 intervals at 20 FPS
  took 1003 ms while an independent 10 ms timer fired 101 times.
- Debug app/test builds and Debug lint passed: zero lint errors, 400 warnings.
- Additional supplied games on the same installed Debug build: Bounce Tales
  1.15 reached Chapter 1 and advanced the opening dialogue over its rendered
  scene; Ancient Empires II 1.0.2 reached its animated introduction. These are
  launch/render/input checkpoints, not complete gameplay or FPS benchmarks.
  Fixture SHA-256 values: Bounce Tales
  `d69de01b5e45267820b00386e45db2296b4ca761708dcca6ca4ab03f3c604e39`,
  Ancient Empires II
  `98f6af1dd266dec081fe744f0f4298e0125e7ff3beb5b0970ff09f48350ba7f4`.
- Games were staged into the Debug app's private files for URI import. A direct
  shell-pushed Download URI was not readable under the emulator's storage setup;
  no production permission or importer changes were made for this test setup.

Ignored evidence: `build/metal-slug-s40-bytecode.txt`,
`build/metal-slug-bytecode.txt`, `build/metal-baseline-latency.txt`,
`build/metal-after-play-latency.txt`, `build/metal-baseline-play.png`,
`build/metal-after-play.png`, `build/cadence-baseline-proof.log`,
`build/java-cadence-build.log`, `build/java-cadence-final-build.log`,
`build/java-cadence-emulator-final.log`, `build/cadence-bounce-play.png`,
`build/cadence-bounce-dialogue.png`, `build/cadence-ancient.png`,
`build/cadence-ancient-intro.png`.

## Remaining scope

These changes do not turn a ten-tick game into true 60 FPS. Removing its wait
would also speed up frame-dependent movement and animation. A true higher-rate
mode requires evidence that a game's logic is time-based, or separately validated
game-level changes; it cannot be promised by raising an emulator cap.
No complete-game, save-roundtrip, audio-listening or physical-controller
acceptance. Retroid checks remain pending.
