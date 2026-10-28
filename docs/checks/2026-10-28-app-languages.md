# App Language Selection and First Translation Batch

Date: 2026-10-28. Availability: development source and local combined Debug APK;
not a signed public release. Issue #43 remains open for the wider language rollout
and remaining backend diagnostics. No games, firmware, account data or private
test artifacts are published with this record.

## Implementation

- Settings > Language offers native language names and System default. Choice
  rows have one visible check mark and checked accessibility state; keyboard/
  controller focus is a separate state. The same fix covers list preferences.
- Spanish, Brazilian Portuguese and Russian each contain all 1505 canonical
  Android resources. Existing translations were reviewed, including product
  identity, reset/restart distinctions and destructive-action warnings. This is
  maintainer/tool-assisted review, not independent native-speaker approval.
- Android 13+ uses `LocaleManager`, so system per-app settings and the app picker
  use the same choice. Android 10-12 uses AppCompat and a non-exported provider
  as the single writer; observers update already running app-owned processes.
- AppCompat 1.6 does not update all application/plain-Activity contexts on older
  Android versions. The compatibility path updates those contexts too, including
  native engine menu creation and app-owned background messages.
- Game Activities handle locale changes without locale-driven recreation. Idle
  Symbian setup refreshes labels; active forms/import choices are not discarded
  to repaint text. Newly opened menus use the new language.
- Java's default `microedition.locale` is read from the device locale instead of
  the UI-adjusted process default. Explicit profile properties still override
  that default. No changes to guest firmware language, game resources, transport
  identifiers, user chat text or saved mapping names.
- Locale resources remain ordinary editable Android XML. The audit now rejects
  missing canonical keys for any locale listed in `app_locales.xml`, in addition
  to argument-type and array-length structural checks. Array references/order
  were reviewed with the translations, not semantically proved by the audit. Resource presence
  is not a claim of native-speaker approval or complete-game compatibility.
- Visible layout/menu/preference XML attributes are audited as well. Remaining
  literal game-screen/context-menu labels now reference resources. Shared game
  chrome refreshes accessibility labels on a locale change. Bundle language
  splitting is disabled for offline picker use; no App Bundle was distributed.

The implementation follows the platform/AppCompat boundary documented in
[Android per-app languages](https://developer.android.com/guide/topics/resources/app-languages).

## Verification Scope

Targets: Retroid Pocket Classic, serial `MC94516AQF040305380`, Android 14;
isolated `Language_API30` Android 11 x86_64 emulator, serial `emulator-5580`.
The emulator advertises an ARM translation bridge and accepts the combined
arm64 Debug APK. This is UI/lifecycle evidence, not engine performance evidence.

`AppLanguageTest` drives the real Settings picker, checks native names and
selection accessibility, exercises shared game panels/confirmation callbacks,
and changes language while a plain Activity stays alive in a separate process.
That fixture reads the actual displayed label, not just the underlying resource.
It also checks platform/picker parity, non-exported persistence and all three
runtime Activity configuration flags. It does not start a game or network match.

`AppLanguagePersistenceTest` is run in two explicitly selected stages, with a
force-stop and in-place `adb install -r -d` between them. It checks cold-start
selection and application/Activity text, then restores the original choice.
Only its own test checkpoint is removed; library/settings/account data are not
cleared. Initial English/Spanish runs passed on both targets.

## Results

- Combined Debug and instrumentation APKs assemble successfully. Final log:
  `build/language-final-complete-build.log`, `BUILD SUCCESSFUL` (2m 9s).
- Fresh JVM run: **414 tests, 0 failures/errors/skips**, 39 result XML files in
  `app/build/symbian-release/test-results/testFdroidDebugUnitTest`.
- Final lint: **0 errors / 565 warnings**. This is not a clean-warning claim;
  the report includes inherited and newly visible typography/resource warnings.
- Audit self-test and `--check` pass: 1505 canonical resources, zero missing
  advertised-locale entries, zero structural errors and zero visible XML
  literals. Nine Java candidates remain in the inventory (identifiers, color
  values and resolution syntax); none is in protected extraction boundaries.
- Retroid final run: **53 passes, no skips**, covering `AppLanguageTest`,
  `LocalizationJourneyTest`, `SocialLocalizationTest`, `LobbyPresentationTest`,
  native requirements/import/transfer localization, `GameChromeTest` and
  `FastForwardControlTest`. Log: `build/language-complete-retroid.log`.
  Queue layouts exercise 288 locale/font/viewport/theme/state cases; lobby seat
  layouts exercise 180. These are fixture layouts, not 468 live sessions.
- Retroid cold-update check: set and verify stages each pass, with force-stop
  and in-place APK reinstall between them. Logs:
  `build/language-complete-retroid-cold-set.log` and
  `build/language-complete-retroid-cold-verify.log`.
- Android 11 earlier checkpoint: **13 passes**, plus one pass for each
  cold-update stage. Logs: `build/language-final-api30.log`,
  `build/language-cold-api30-set.log`, `build/language-cold-api30-verify.log`.
  This checkpoint covered English/Spanish selection and the pre-33 process path,
  before the final three-language integration and selected-check-mark repair.
  The final 14-test retry did **not** finish: the emulator's `system_server`
  crashed with `HardwareBuffer.finalize()` timing out after 10 seconds, followed
  by `INSTRUMENTATION_ABORTED: System has crashed`. Logs:
  `build/language-complete-api30.log` and
  `build/language-complete-api30-system-crash.log`. It is neither a pass nor
  evidence of an app crash. The isolated emulator was stopped; fresh final-build
  Android 10-12 acceptance remains open.
- The initial final-batch device run exposed two test synchronization failures:
  platform locale selection returns before Activity recreation completes. The
  tests now wait for the applied application locale and the resumed Activity,
  then the full 53-test set passes. No failures were silently counted as passes.
- Final local Debug APK SHA-256:
  `23f54d5a32d0d2290c677a8671f512820a7ca637d13da63dd1369b65aaa0e517`.
- [Selected actual screenshots](../../screenshots/development/2026-10-28-localization/README.md)
  show the native-name picker, Russian controls, Portuguese display settings and
  Spanish exit confirmation. Retroid captures are 1240 x 1080; fixture names
  remain English intentionally. Source captures/logs remain under ignored `build/`.

Build command:

```powershell
.\gradlew.bat :app:assembleFdroidDebug :app:assembleFdroidDebugAndroidTest :app:testFdroidDebugUnitTest --rerun :app:lintFdroidDebug -PsymbianRelease=true -PsymbianSourceDir=build/symbian-source-check --offline --console=plain --max-workers=2
java tools/LocalizationAudit.java --self-test
java tools/LocalizationAudit.java --check
```

## Remaining Acceptance

- The other issue #43 languages, including RTL, are not enabled in the picker.
- Raw external/native/OS diagnostics, old raw journal entries and some remaining
  network-game preparation detail are not translated by guessing English text.
- No real multiplayer match, active native installer, long-running game or
  full library/save migration is claimed by the locale fixture tests.
- The earlier Android 11 checkpoint exercises the pre-33 implementation;
  Retroid Android 14 covers the final platform path. The interrupted final
  emulator retry and untested Android versions remain explicit gaps.
- No public APK, release asset, version code, GitHub automation or production
  device package is changed in this batch.
