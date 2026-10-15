# Java display startup and lifecycle

Date: **2026-10-15**. Source follows `6cfc99aa`. Related report:
[#15](https://github.com/EriArk/JavaEmulator/issues/15).
This is an unreleased source change and installed debug-build verification,
not a public APK update. GitHub Releases was checked today: the newest
prerelease remains `v0.3.0-beta.1`, published 2026-10-14. Asset hashes were not
rechecked. Actions remains disabled.

## Shared mechanism, not a game-specific workaround

Previously Canvas painting depended on Android `surfaceCreated`. Meanwhile,
the inherited `isShown()` could report true because the Activity had selected
that Canvas. A game advancing initialization through repaint/serviceRepaints
could therefore lose early paint callbacks before the Android surface existed.
Surface recreation also delivered guest hide/show events unrelated to a guest
screen change. The same surface-bound mechanism is present in the locally
cached upstream revision `9b0fa48a0a0d1e61376c0b9af28b3d2caec0a4cc`; this is not a
claim about today's upstream HEAD or evidence against an optional native engine.

The change separates guest display lifecycle from Android presentation:

- Publish the selected screen after a successful `startApp` return, only while
  the host is visible. Pause and destroy withdraw guest visibility.
- Deliver initial size/show and repaint through the event queue. Paint into
  the existing offscreen buffer even if the Android surface is not ready.
  Surface creation presents that buffer instead of restarting guest callbacks.
- Ignore obsolete show requests and input/paint delivery to hidden canvases.
  Clear pending paint state even when a queued repaint is discarded.
- Recheck selection after game callbacks that can select another screen.
- Keep lifecycle delivery queued even in immediate-event compatibility mode,
  so guest callbacks do not execute under the Display monitor.
- Deliver a changed fullscreen size when a hidden Canvas becomes visible again.
- Defer Android alerts during pause and show them on resume without duplicates.
- Make the existing nullable Activity lookup safe before a host is attached.

No game-name/hash branches, artificial startup sleeps, modified JAR resources,
profile resets or new user settings were added. Mophun and Symbian rendering
are separate runtimes and were not changed by this Java LCDUI patch.

The lifecycle decisions were checked against Oracle's
[MIDP UI lifecycle/concurrency documentation](https://docs.oracle.com/javame/config/cldc/ref-impl/midp2.0/jsr118/javax/microedition/lcdui/package-summary.html)
and [Canvas event/painting contract](https://docs.oracle.com/javame/config/cldc/ref-impl/midp2.0/jsr118/javax/microedition/lcdui/Canvas.html).
This is not a complete MIDP conformance claim.

## Automated verification

Added 15 `CanvasLifecycleTest` cases. A deliberately surface-less host exercises
initial activation, 30 synchronous frames, pause/resume, obsolete screens and
input, pending paints, size/show callback screen switches, Form round trips,
callback failure, immediate-mode locking, GameCanvas flush/key polling, deferred
alerts and hidden fullscreen changes. Buffer pixels and event order are asserted.
These fixtures test the shared mechanism, not Ghost Recon game code.

The broader installed suite also includes `FramePacerDeviceTest`,
`GamePresetsUiTest`, `ControllerMapperTest`, `GameMenuLayoutTest`,
`ScreenRotationTest` and `TouchDeckTest`.

| Installed target | Final result |
| --- | --- |
| Retroid Pocket Classic, Android 14, combined Phone debug | 43 passed, no skips |
| Retroid Pocket Classic, Android 14, combined Handheld debug | 43 passed, no skips |
| Android 15 x86_64 emulator, ordinary Phone debug | 43 passed, no skips |

Build commands:

```text
gradlew -PsymbianExperiment=true -PsymbianSourceDir=build/symbian-source-check :app:assemblePhoneDebug :app:assemblePhoneDebugAndroidTest :app:assembleFdroidDebug :app:assembleFdroidDebugAndroidTest
gradlew :app:assemblePhoneDebug :app:assemblePhoneDebugAndroidTest :app:testFdroidDebugUnitTest --rerun :app:testPhoneDebugUnitTest --rerun
```

Both JVM tasks executed afresh: 25 tests passed per variant. APKs were installed
with `adb install -r -d`; no application data or saved game profiles were cleared.
Local build/suite logs use `build/canvas-lifecycle-*.log`, including
`retroid-phone`, `retroid-handheld` and `avd-phone` results. The final suite
includes the hidden-fullscreen follow-up added after the manual checks below.

## Real-game observations and limits

Unmodified local archive JARs and existing game configurations were used:

| Target and game | Observed |
| --- | --- |
| Retroid combined Phone: G.R.A.W. 2 v1.2.6, SE 128x160 JAR | Logos, sound prompt, title/menu, difficulty, mission briefing and first playable level. Holding the touch direction moved the player. The AbyssME menu returned to the game's own Resume menu. |
| Retroid combined Phone: AgeOfEmpires2_S40v3.jar | Title and main menu rendered. No campaign acceptance claimed. |
| Retroid combined Handheld: MIT Sudoku J2ME 0.1 | Existing library copy opened its game board without a touch deck. |
| Android 15 ordinary Phone: same G.R.A.W. 2 JAR | Existing copy reused, sound prompt and main menu rendered in portrait with the touch keypad. |

Ghost Recon SHA-256:
`101a60cdd265117e832ce10646c066fb542f086e2afb9895bca6d950d3e7d4af`.
Other fixture provenance is in [the filename-hint check](2026-10-15-source-hints.md)
and [the MIT Sudoku fixture](../../test-games/sudoku-j2me/README.md).

The captured Ghost Recon process logs on Retroid and the emulator contain no
`Error in paint`, `Failed startApp`, fatal exception or null-array exception.
Local captures include `build/canvas-retroid-ghost-play.png`,
`canvas-retroid-ghost-pause.png`, `canvas-retroid-ghost-resumed.png`,
`canvas-retroid-age-menu.png`, `canvas-retroid-sudoku.png` and
`canvas-avd-ghost-menu-final.png`. Raw logs, commercial games and screenshots
remain local; no game binaries were added to Git.

The Android 15 shell `file:///sdcard/Download` experiment was denied by scoped
storage. The same unmodified fixture was imported from this debug app's external
files directory instead. A temporary all-files app-op probe was restored to its
original `default`; no permission workaround was added to the product.

**Issue #15 remains open.** Inspection of the original game's bytecode showed
frame-count-dependent splash initialization that can be vulnerable to skipped
early paints, consistent with the reported stack. However, the intermittent
black screen did not reproduce in this session's pre-patch Retroid run either.
The shared lifecycle defect is corrected and covered; these successful launches
do not prove it was the only cause of that historical game failure. This batch
does not establish audio, save round trips, physical-button ergonomics, long
sessions or complete-game compatibility.
