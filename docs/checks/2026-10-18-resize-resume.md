# Java Resize, Foreground Return and Capture - 2026-10-18

## Availability

Unreleased development after `0bbefe84`, not a beta.6 APK update. Local debug
builds were installed on the Retroid Pocket Classic and Android 15 x86_64
emulator. No release, test-build upload, Nextcloud copy or website deployment
was performed. The public release listing was not rechecked in this checkpoint.
Commercial game fixtures and captured game artwork remain local.

## Shared Repairs

### Resize callback ordering

Host resizing updated Canvas dimensions and queued `SIZE_CHANGED` followed by
repaint. However, a paint already in the queue could run first, with the new
dimensions but without the guest's resize callback. Several queued resizes also
delivered obsolete dimensions, and a pending size callback could run after the
game had gone into the background.

Canvas now tracks the last notified dimensions. The size event, foreground
show path and paint entry point share one dispatcher that delivers the current
size only when needed and only while the display is foreground-selected.
Pending intermediate sizes are coalesced. GameCanvas still gets its buffer
resize through `doSizeChanged()` before the guest callback/show/paint.
No guest callback is invoked while holding a new UI/buffer lock.

This is not an arbitrary-title workaround. It fixes queued callback ordering;
it does not claim to make every fixed-resolution game support every resolution,
or serialize all independent game threads with an in-progress host resize.

### GLES screenshot readback

The real-game test exposed a second shared defect: a saved image could be black
even with a visible game frame. `requestRender()` followed by `queueEvent()` did
not ensure rendering before `glReadPixels`; the previously swapped back buffer
could contain discarded pixels. The old asynchronous conversion also reread
mutable viewport dimensions instead of using the dimensions of the capture.

The GL capture event now explicitly draws the retained frame before readback,
creates its bitmap using that capture's dimensions, and flips the resulting
bitmap off the GL thread. Unavailable surfaces fail instead of pretending to
produce a picture. The existing timeout remains. The intermediate bitmap is
released after conversion. No raw-capture or native-engine rendering path was
changed.

## Before/After Evidence

| Check | Result | Local evidence |
| --- | --- | --- |
| Three new queue tests on the old dispatcher, emulator | All three failed: paint before size, stale notifications, hidden callback | `build/resize-before.txt` |
| Screenshot test before capture fix, Retroid | Expected green, received black after deliberately clearing the unpresented back buffer | `build/resize-shot-before.txt` |
| Final five instrumentation suites, Retroid | 61 passed | `build/resize-retroid-final.txt` |
| Same final suites, Android 15 emulator | 61 passed | `build/resize-emulator-complete.txt` |
| Final real Java UI workflow, Retroid | 1 passed, four resolution cycles | `build/resize-live-retroid-final.txt` |
| Combined Handheld/Phone debug + instrumentation builds | Passed | `build/resize-combined-final.log` |
| Base Phone debug + instrumentation build | Passed | `build/resize-base-final.log` |
| Final combined JVM tests, explicitly rerun | 38 per variant, no failures/errors | `build/resize-checks-final.log` and test-result XML |
| Final combined lint | No errors; 405 Handheld / 412 Phone warnings remain | `build/resize-checks-final.log` and lint-result XML |

Suites: CanvasLifecycleTest, ScreenRotationTest, MidletStartupTest,
SettingsTrialTest and GamePresetsUiTest. Coverage includes real surface pixels
in software/GLES/hardware modes, surface recreation, direct flush, true logical
resize, callback ordering, hidden resize, startup/pause, settings trials and
presets. A new GameCanvas test keeps a cached Graphics object through repeated
hidden shrink/grow/resume and verifies the far corner of the new buffer.

The screenshot regression explicitly models discarded back-buffer contents;
it does not depend on a particular device driver retaining or clearing them.

## Real Game Scope

Fixture: local `aoe-check/aoe.jar`, Age of Empires II Deluxe. The optional
`resizeReturnTrials=true` instrumentation flow checks 128x160, 480x800,
320x240 and 240x320. For each: Try changes, capture actual game pixels, Home,
bring the existing game task back, capture again, Undo and capture again.
No game restart is used to recover the picture. Exit leaves settings unsaved.

Earlier attempts returned black saved pictures at 128x160 and after background
return at 320x240. Those are recorded failures, not passes
(`build/resize-live-retroid-retry.txt`, `build/resize-live-retroid-large.txt`).
After fixing readback, the final test passed with all original cases, including
128x160; the minimum color assertion was not relaxed. A separate initial
instrumentation process failed to attach and did not execute a game test.

Inspected local captures in `build/resize-captures/` include the startup logo,
the game's own Pause screen after return at 480x800, and the final 128x160 pause
screen. That small preset visibly crops/enlarges parts of this game's content;
it is not a recommended replacement for the game's native resolution. These
checks establish retained output and working display trials, not gameplay,
audio, save fidelity or complete-game compatibility. Native Mophun/Symbian
renderers were not modified or runtime-tested here. Ghost Recon issue #15 is
not closed by this work.

The official Handheld package was not replaced. Its library activity was
restored to the foreground on Retroid after tests. Games, saves, profiles and
firmware were not cleaned up. Logs/captures and test APKs are local-only.
