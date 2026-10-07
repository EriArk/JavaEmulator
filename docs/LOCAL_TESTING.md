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

## Recorded checks: 2026-10-08

Both public-source variants built locally, and both JVM suites passed (14 tests
each). The Android emulator passed these 50 tests in **each** variant:

| Suite | Tests |
| --- | ---: |
| CompatibilityProfileTest | 14 |
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

- Only first launch without a saved configuration starts automatic preparation.
- Explicit target metadata wins over incidental vendor API references. Repeated
  aliases do not accumulate votes. Absent/conflicting vendor hints use Generic MIDP.
- Declared display dimensions take precedence over artwork dimensions. Atlases,
  sprites, tiles, icons and fonts are excluded; image area is not a ranking bonus.
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
