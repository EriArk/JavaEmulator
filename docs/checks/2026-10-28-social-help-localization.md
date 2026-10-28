# Room Notices and Product Help - 2026-10-28

## Scope and Availability

Unreleased source after `58be83d3`, continuing #43. No signed release APK,
release asset replacement, Nextcloud upload or new advertised language.
GitHub Actions remains disabled. Live release state was not rechecked here.

- 66 additional Social notices use typed display identities and English fallback.
  Publication rejection codes remain raw arguments to a whole formatted sentence.
  Network packets, room admission and relationship decisions do not use labels.
- Contact journal version 1 accepts an optional `errorCode` alongside `error`.
  Old, unknown and malformed metadata retains raw text; no text-to-key guessing.
  This metadata is local and is not added to contact messages on the wire.
- Mailbox reconfiguration clears the old account's status. JVM regression tests
  cover this as well as typed delivery failures and journal recovery.
- About/Help describe current library, controls, picture, transfer, system-file
  and experimental online workflows. Symbian guidance follows the build flag.
  Project support links replace inherited product-specific support links;
  copyright, upstream attribution and license access remain present.
- Metadata labels and direct helper actions use resources; game/vendor names,
  version strings, paths and unknown diagnostics remain original data.

## Build and Automated Results

JDK 21 / Gradle 8.7, offline combined Debug build:

```powershell
.\gradlew.bat :app:assembleFdroidDebug :app:assembleFdroidDebugAndroidTest :app:testFdroidDebugUnitTest :app:lintFdroidDebug --rerun -PsymbianRelease=true -PsymbianSourceDir=build/symbian-source-check --offline --console=plain --max-workers=2
java tools/LocalizationAudit.java --self-test
java tools/LocalizationAudit.java --check
```

- Build successful; **414 JVM tests**, no failures, errors or skips.
- Lint: **0 errors / 439 warnings**. Warnings remain, including legacy unused
  resources and typography; this is not warning-free acceptance.
- Audit: 200 Java files; nine direct UI literal candidates, none in protected
  extraction boundaries; 1501 canonical resources, zero structural errors.
  This inventory is not an exhaustive helper/backend scan or translation review.
- Final instrumentation: **29 pass / 7 skip / 0 fail**. Runner `OK (36 tests)`
  includes skips and must not be reported as 36 executed passes.
- Tests: SocialLocalizationTest, InfoContentTest, LocalizationJourneyTest,
  SettingsResourcesTest, LobbyPresentationTest, LobbyRoomDeviceTest and
  LobbyRuntimeLifecycleDeviceTest. Account-protected room fixtures were skipped.
- The initial About/More test checked before Android dispatched the button's
  queued callback. The test now waits for the UI queue between actions and
  verifies both More and Help actually close; the full focused suite passed again.

## Device and Visual Evidence

Retroid Pocket Classic, Android 14, serial `MC94516AQF040305380`; ADB ready and
`sys.boot_completed=1`. Updated only the Debug and instrumentation packages.
No app-data clear, real account mutation, firmware change or game/save deletion.
The official package was not replaced.

InfoContentTest checks 36 isolated layout cases: English, expanded and RTL
pseudolocales; 100/200% fonts; 320x640, 540x540 and 720x400 dp; Help and About.
No ellipsis, content fits width, long Help scrolls. Actual SettingsActivity
dialog callbacks were exercised separately. Device language/preferences were
not switched. URLs and build version are preserved across five locale contexts.

Three rendered fixture PNGs were pulled and visually inspected in ignored
`build/localization-social-help-fixtures/`: `about-dark-square.png`,
`help-dark-square.png`, `help-expanded-portrait.png`. These are isolated view
renders on the device, not screenshots of three different physical screens.

Local logs (ignored): `build/localization-social-help-final-build.log`,
`build/localization-social-help-test-build.log`,
`build/localization-social-help-device-final.log`; JVM XML and lint XML are in
`app/build/symbian-release/`. Final APK SHA-256:

- Debug: `7a7b201e36739cb307ee665a3a24396aea532f1fc6e5f93543ae1370add838a4`
- Instrumentation: `5313764bfb0a9a4e56f39c20ca03541b1b6a116514483a7fa96ab8092ca4ca9f`

## Still Open

Full translations, reviewed per-locale journeys and process-safe language choice
are not implemented. Some helper/backend errors, including network-game
preparation and Mophun diagnostics, remain English. Legacy journal text stays
raw by design. Pseudolocale results do not establish full RTL navigation or
real-language quality. No live multiplayer, gameplay, FPS or audio acceptance
is claimed in this localization batch. #43 remains open.
