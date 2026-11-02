# Supplied Game Sample and Shared Compatibility Repairs

Date: 2026-11-02. Target: Retroid Pocket Classic, Android 14.
Availability: unreleased source and a separate local Debug install. No release
APK update. Supplied games, firmware and raw captures remain ignored local data.

## Scope and Method

The sample contains ten Java JARs and ten Symbian installation packages (nine
Symbian titles; Marble Revolution 2 has two resolution-labelled variants).
N95/RM-320 and 5800/RM-356 are the native phone fixtures. Each native run uses a
private copied/imported phone; Java runs use disposable conversions, settings
and saves. Installed user games and their save directories are not test targets.
No new Mophun or original N-Gage gameplay sweep is included in this batch.

The opt-in `ProvidedJavaSweepTest` and `ProvidedGameSweepTest` install/launch,
send recorded input and capture intermediate frames. Their JUnit success means
the observation procedure completed, **not gameplay compatibility**. Early guest
exit or a game displaying its own error can still produce a screenshot. The
native harness uses the actual engine with a test-owned surface, not the full
production game-menu/touch-overlay path. Native taps use viewport coordinates;
Java taps use host-screen coordinates. No complete-game, audio-quality,
performance or physical tilt-control acceptance is implied.

## Shared Repairs

- Explicit Java screen metadata and filename `WxH` labels can retain uncommon
  bounded dimensions such as 240x400 and 240x432. Image-only guesses remain
  conservative; signs, decimal fragments and out-of-range values are rejected.
- Map Android DPAD_CENTER to the same default Java Fire action as Enter, while
  retaining explicit user mappings (including disabling the key).
- Normalize legacy JVM boolean returns during conversion. SimCity Metropolis
  previously failed ART verification because a short-valued return was used by
  a boolean method. The converter now emits the JVM low-bit narrowing operation
  for boolean returns only. It does not identify or special-case the game.
- Version the executable-code cache so already installed JARs are reconverted
  on demand. Source archives, installed original DEX and saves are untouched;
  installations without retained Java classes still use their original DEX.
- Bind the Symbian Android sensor event queue to the main looper before native
  preparation, even when the library was loaded by a worker. Handle repeated
  preparation, timeout and interruption explicitly. Labyrinth Touch no longer
  logs the reproduced event-queue/-22 registration failure in the retest process,
  but its scene still renders incompletely. This is not blanket tilt-game support.
- Replace Symbian mode-table rewriting with selection of an existing initial
  mode. The old override changed dimensions of every mode, including 5800's
  240x320 compatibility mode, while retaining IDs used by Avkon and games.
  Fruitz reproduced a KERN-EXEC (3) startup failure under the old Portrait
  override. The repaired Portrait trial reaches its menu without that exit.
  Physical panel dimensions and the entire native mode table now remain
  unchanged; an application can still select its own mode afterwards.

Boolean-return semantics reference:
[JVMS ireturn](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-6.html#jvms-6.5.ireturn).

## Resolution and Aspect Ratio

Java custom width and height already exist in the advanced pre-game editor.
The running-game shared display menu offers presets and retains a saved custom
size; it does not yet contain an arbitrary width/height entry dialog. Improved
metadata recognition is not proof that every fixed-resolution game can resize.

Symbian screen presets select an initial mode from the supplied phone's table.
Unknown/legacy phones retain Original; this is not an arbitrary virtual monitor.
Fit preserves aspect ratio and can leave bars. Stretch fills the area by
distorting it. Selecting Landscape cannot make a fixed portrait game draw a
wider scene: Marble Revolution 2 still switched its active mode to 240x320 in
the recorded landscape-phone trial. Universal widescreen remains open (#50).
This supersedes the earlier experimental behavior that changed the physical
panel dimensions when selecting an orientation.

Diamond Rush's supplied JAR contains a fixed 320x240 rendering path and a
misleading orientation-error message. Its bytecode rejects a portrait viewport;
the manual 320x240 retest reached level gameplay. No title-specific automatic
rotation rule was added without trustworthy metadata.

## Verification and Evidence

- Combined Debug APK and Android test APK build successfully.
- Five dexlib JVM tests pass, including boolean narrowing and unchanged integer
  returns.
- Final Retroid focused run: 46 tests pass (profile detection, cache upgrade and
  ART loading, clock behavior, center mapping, sensor initialization, touch
  ownership and phone-screen capability invalidation).
- Native mode-selector C++ assertions pass, including a 5800-style table with
  its distinct 240x320 compatibility mode. Retroid copied-phone mode cycles pass
  for N95/Asphalt3 and EKA1/Snakes: initial orientation, unchanged physical panel,
  legal game-selected active modes, return to Original and original-file hashes.
  These two tests capture startup, not additional complete-game acceptance.
- An earlier profile test failed because it assumed build-flavour-only gravity.
  The assertion now follows existing connected-controller behavior; product
  gravity behavior was not changed to satisfy the test.

Ignored local evidence: `build/sweep-2026-11-02/`,
`build/sweep-2026-11-02-cache-final-build.log`, and
`build/sweep-2026-11-02-converter-tests.log`,
`build/sweep-2026-11-02-native-mode-build.log`, and
`build/sweep-2026-11-02-complete-build.log`. Logcat snapshots can include earlier
processes: correlate PID/time with each run rather than counting old errors as
new failures. Native sensor retest PID 32194 had no event-queue registration
error; the earlier failing process was 30115.

## Observations

The matrix below records visible results, not a supported-game list.

| Package/title | Observed result | Remaining boundary |
| --- | --- | --- |
| Diamond Rush (Java) | Level gameplay at manually selected 320x240 | No reliable size label in supplied filename/manifest |
| SimCity Metropolis (Java) | New-game introduction after converter repair | Full play/save/audio not established |
| Asphalt 3 Street Rules 3D (Java) | Race scene, car and HUD | No complete race or performance acceptance |
| Sonic the Hedgehog 2 (Java) | Level scene, character and advancing timer | Short gameplay only |
| Predator 176x220 (Java) | Training introduction | Text clipping visible; not resolved |
| The Witcher 128x160 (Java) | Quest selection menu | Gameplay not established |
| Tennis Open 2007 128x128 (Java) | Tips/loading screen | Match gameplay not established |
| Avatar 640x360 (Java) | Sound choice and title screen; touch advances it | Gameplay not established; earlier transition frame had visual artifacts |
| Halloween Zombie Massacre touch (Java) | Touch selected English and reached sound choice | Gameplay not established |
| Wonder Zoo touch 640x360 (Java) | Touch advanced into the zoo/tutorial scene | Short startup/tutorial only |
| 3D Minigolf (Symbian) | Language selection/native choice popup | No golf gameplay established |
| Frozen Bubble 240x320 (Symbian) | Level-one board; shots changed the board | Short gameplay only |
| Fruitz 360x640 (Symbian) | Original and repaired Portrait reach the main menu; former Portrait panic is gone | Touch progression not established even after waiting for the menu |
| Fruitaholic 360x640 (Symbian) | Touch selected Easy and reached the game board after loading, on repaired Portrait | Short gameplay only; dialogue text clipping visible |
| Labyrinth Touch (Symbian) | Stays alive after sensor repair; partial scene | Level background/rendering still broken; no physical tilt acceptance |
| Marble Maze (Symbian) | Native unlock dialog responds | Supplied copy is gated; no unlock bypass or gameplay claim |
| Marble Revolution 2, both labelled sizes (Symbian) | Level-one field and advancing timer | Game retained portrait mode in landscape-phone trial |
| Sky Force Reloaded touch (Symbian) | Black output in sampled path | Unresolved |
| Tilt Blocks Gravity (Symbian) | Installs/registers but no capturable startup frame | Unresolved; observation test fails, not a pass |
