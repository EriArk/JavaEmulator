# Local regression checks

GitHub Actions is intentionally disabled. Set up the SDK/JDK and local signing
workaround in [Contributing](../CONTRIBUTING.md) first; #1 is still open.
Commands below use the public source tree, without optional private engines.

## Build and JVM tests

```shell
./gradlew :app:assemblePhoneDebug :app:assembleFdroidDebug :app:assemblePhoneDebugAndroidTest :app:assembleFdroidDebugAndroidTest
./gradlew :app:testPhoneDebugUnitTest --rerun :app:testFdroidDebugUnitTest --rerun
```

On Windows use `gradlew.bat`. JVM coverage is 14 tests per variant: source identity
and controller direction/diagonal transitions, release behavior and fallback.

## Android instrumentation

Use an explicit emulator/device serial. Example for `emulator-5554` and beta.2
development filenames (adjust filenames when the version changes):

```shell
adb -s emulator-5554 install -r -d app/build/outputs/apk/phone/debug/AbyssME-0.1.0-beta.2-phone-debug.apk
adb -s emulator-5554 install -r -d app/build/outputs/apk/androidTest/phone/debug/app-phone-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w -e package ru.playsoftware.j2meloader io.github.eriark.abyssme.phone.debug.test/androidx.test.runner.AndroidJUnitRunner

adb -s emulator-5554 install -r -d app/build/outputs/apk/fdroid/debug/AbyssME-0.1.0-beta.2-debug.apk
adb -s emulator-5554 install -r -d app/build/outputs/apk/androidTest/fdroid/debug/app-fdroid-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w -e package ru.playsoftware.j2meloader io.github.eriark.abyssme.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Do not uninstall or clear app data to work around a signing conflict on someone's
device. Test packages have separate `.debug` application IDs. Instrumentation
uses temporary test libraries; it can launch activities and write screenshots.

## Compact handheld interface: 2026-10-09

Follow-up: artwork and focus checks add 13 cases (11 generated artwork fixtures,
one backup/import round-trip and one Handheld-only real-activity focus test).
Final combined debug snapshots passed 108 selected cases on Handheld; Phone's
runner reported 108 with the Handheld-only case skipped (107 executed). This
includes 27 private engine cases; the shared source accounts for 81 cases.
Both public-source variants and test APKs also build separately. JVM suites
were rerun: 14 tests per variant.

The cold-start focus regression is fixed: resizing cards is deferred until after
RecyclerView layout, so pending adapter updates complete. The activity test
uses an isolated eight-item catalog, checks all three views and verifies that
subsequent layout does not steal deliberately placed header focus.
See [artwork rules and safe legacy refresh](ARTWORK.md).

Public-source Phone and Handheld APKs and both instrumentation APKs built.
Handheld passed 68 selected Android tests at 480x320 dp. The two additional
cases cover library card geometry/artwork modes and narrow mapper page bindings.
Mapper geometry also covers 480x302 dp usable space (system cutout allowance),
640x360 dp and 360x640 dp portrait. JVM suites passed 14 tests per variant.

See [compact library behavior and design references](COMPACT_LIBRARY.md).
Manual checks cover library view switching with L/R, visible selection, Start/X
menus, expanded search and B-to-clear, narrow mapper pages, and portrait Phone
browsing. The initial collection-selector focus found in that earlier pass is
fixed by the follow-up above. Visual acceptance on a physical handheld remains
to be checked. This is emulator coverage, not physical-device/controller acceptance.

## Settings trials: 2026-10-09

Both public-source APKs and instrumentation APKs built. Phone passed all 66
selected Android tests; Handheld passed the 10 new `SettingsTrialTest` cases.
Both JVM suites passed again (14 tests per variant). The full Handheld Android
suite was not repeated for this batch.

New coverage includes draft isolation, suppressed runtime autosave, repeated
preview/undo, Keep and failed Keep, one-shot launch consumption, malformed drafts,
restart-required detection, save-file preservation and interrupted atomic writes.
See [settings trial behavior and manual checks](SETTINGS_TRIALS.md).

Manual emulator checks covered the library-to-game trial, Keep, live rotation
Undo, Phone/Gamepad Undo and timeout rollback. At 480x320 dp, D-pad focus scrolls
the settings action into view. The library issue found in that pass is addressed
by the subsequent compact interface batch above (#17); this is not a claim that
every screen is polished at that size.

## Earlier full baseline: 2026-10-08

Both public-source variants built locally, and both JVM suites passed (14 tests
each). The Android emulator passed these 56 tests in **each** variant:

| Suite | Tests |
| --- | ---: |
| CompatibilityProfileTest | 20 |
| OrientationTest | 1 |
| DatabaseMigrationTest | 4 |
| LibraryImporterTest | 11 |
| AdditionalGamesTest | 1 |
| ControllerMapperTest | 7 |
| ScreenRotationTest | 3 |
| TouchDeckTest | 4 |
| ScreenshotTest | 3 |
| ThemeContrastTest | 2 |

This excludes inherited GraphicsTest and is not a blanket test of every
emulation API. MIDI/audio/native engines and full gameplay are not proven by it.
The static MIDP-1.0 metadata case is not a redistributable MIDP-1.0 gameplay fixture.

## Automatic preparation contract

- First launch or opening per-game settings without a saved configuration starts
  automatic preparation. Opening settings returns to the editor, not to gameplay.
- Explicit target metadata wins over incidental vendor API references. Repeated
  aliases do not accumulate votes. Absent/conflicting vendor hints use Generic MIDP.
- Distributor advertising and web addresses do not select a manufacturer. Exact
  manufacturer publisher names and vendor API references are weak evidence, not
  confirmation of a device model. Their confidence remains low.
- Declared display dimensions take precedence over artwork dimensions. Atlases,
  sprites, tiles, icons and fonts are excluded; image area is not a ranking bonus.
- Raw image dimensions must match a known phone resolution. An unusual background
  such as 255x160 is not treated as a native display. Comma-separated declared
  dimensions are supported; JAR URL hints use the filename, not the host or query.
- Conflicting equal-strength screen sizes keep the current size. Resource order
  does not decide the winner. 480x800 and 480x854, including landscape, are supported.
- Existing/imported/template settings are kept. Detected screen dimensions are
  stored separately so the in-game Auto size can restore them after relaunch.
  Legacy profiles with no detection record retain the old launch-size fallback.
- Preparation failures are reported instead of being silently treated as saved.
- This is static analysis, not running each profile or certifying compatibility.

## Manual acceptance still required

Physical controller/disconnect testing (#9), Phone recreation with touch on/off
(#3), actual ES-DE/Beacon launch-return flows (#7), more document providers and
more game releases remain separate checks. The external-launcher pass is deferred.
Use the manual matrix in Contributing; record actual games/devices/results rather
than turning untested rows into passed checkboxes.

The first [six-game archive smoke sample](GAME_SMOKE_MATRIX.md) includes both
successful screen transitions and unresolved failures. It is not a compatibility
whitelist and does not replace the remaining device checks.
