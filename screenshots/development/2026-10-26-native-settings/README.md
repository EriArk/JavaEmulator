# Native Game Settings - Development Captures

Captured on **2026-10-26**, on Retroid Pocket Classic (Android 14, 1080x1240,
density 320). These are real development UI captures, not concept art or public
release screenshots. English UI; existing user-supplied game library retained.

## Build and Checks

- Combined arm64 Debug APK `io.github.eriark.abyssme.debug`, development identifier
  `0.5.0-beta.1` / 310. This does not replace the signed public beta with that name.
- APK SHA-256: `0c2f3af3f18ff6807b89fa177306bbdc8659503f6d987c70e745aa3c30e4e0e5`.
  Installed APK hashes match on Retroid and the phone emulator.
- Build `:app:assembleFdroidDebug` and `:app:assembleFdroidDebugAndroidTest` with
  `-PsymbianRelease=true -PsymbianSourceDir=build/symbian-source-check`
  and `-Pandroid.injected.build.abi=arm64-v8a`. Reuse unchanged native EKA2L1
  binaries; exclude its release CMake build/configure tasks. Artifacts are under
  `app/build/symbian-release/intermediates/apk`, not `outputs/apk`.
- Java/Mophun-only `:app:compileFdroidDebugJavaWithJavac` also passes without the
  Symbian properties. Existing compiler/manifest warnings remain.
- 379 JVM tests passed in this batch, no failures/errors/skips.
- Final Retroid instrumentation: **51 passed**, no skips, 59.687 seconds.
  Suites: GamePresetsUiTest, SettingsTrialTest, DisplayPanelTest,
  GameMenuLayoutTest, SymbianGameSettingsTest and two installed-catalog checks
  in LibraryInformationUiTest (`nativeSettingsUi` / `nativeSettingsLaunch`).
- Final Android 35 phone emulator: **49 passed**, no skips, 139.470 seconds,
  same five shared/profile suites without the two installed-native-catalog tests.
- Phone emulator at **480x320 dp, font scale 1.5**: both focused native-settings
  and pinned-footer checks passed on the final APK (16.354 seconds). Restore
  size/density overrides and font scale afterward.
- Local raw results: `.local/ux40/native-retroid-final-tests.txt`,
  `.local/ux40/native-small-final-tests.txt`, `.local/ux40/native-emulator-final-tests.txt`.

## Images

| File | What it demonstrates |
| --- | --- |
| `native-light-actions.png` | Native game card with Display/Controls and existing actions |
| `native-light-display.png` | Light molded selectors and pinned Try in game |
| `native-dark-display.png` | Same native display editor in dark theme |
| `native-light-controls.png` | Shared per-game controls; physical mapper retained |
| `native-dark-controls.png` | Dark controls panel with scrolling content |
| `native-snakes-library-trial.png` | Actual Snakes startup after a library rotation trial; Keep/Undo overlay |
| `native-snakes-library-undo.png` | Upright startup picture after Undo |
| `native-snakes-runtime-display.png` | Same supported display options inside the native game menu |

The installed-game path is library -> Display -> rotation -> Try in game ->
Undo -> runtime Display -> confirmed Exit -> library. Confirmed configuration
bytes are unchanged. This is launch/UI/rollback evidence, not full gameplay,
sound, save compatibility, performance or manual physical-button acceptance.
Main-process fake-host tests independently cover Keep, abandoned trials and
phone/UID isolation without pretending those are real native game sessions.

Initial test attempts exposed stale test navigation (old platform filter and
text-only icon lookup), corrected in the test harness. A phone-emulator
installed-catalog attempt failed because that AVD has no installed native games;
native library/game acceptance is therefore Retroid-only. A later emulator run
stalled with Android System UI unresponsive; another failed before attaching
instrumentation. Cold-restart the same AVD without wiping data, use 2048 MB RAM,
dismiss its System UI ANR dialog, then run the successful full suite. Do not count
the interrupted or unattached attempts.

No game binaries, firmware, credentials, release APK, Nextcloud deployment or
public-release revalidation is included. Source progress and remaining #40 work:
[UI refresh record](../../../docs/RETRO_LIBRARY_REFRESH.md#native-library-settings-2026-10-26-unreleased).
