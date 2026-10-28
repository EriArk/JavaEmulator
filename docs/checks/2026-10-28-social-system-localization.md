# Social and System-File Localization Preparation

2026-10-28, main source after `63d2834f`. Unreleased combined Debug work.
No public APK/release update or language rollout. The official Retroid app,
saved accounts, game library, firmware and saves were not replaced or cleared.

## Implementation

- Four canonical resource files cover Social labels/authentication/notices and
  system-file requirements/pending-import decisions. The source inventory grows
  from 662 to 983 translatable resources; this is not a translation count.
- Account/chat/directory actors expose notice enums. Android presentation renders
  them through resources; English fallbacks remain for diagnostics and pure-Java
  clients/tests. Compact-status and delivery-action logic no longer compares text.
- Messages carry content kinds and channels mark generated names. Real user
  content such as `[Attachment]` and `Group conversation` is preserved verbatim.
  Localized timestamps do not change wire timestamps or stored user data.
- System requirement rows carry import kinds. Rendering distinguishes original
  N-Gage, older/newer Symbian and N-Gage 2.0 readiness without parsing English.
  The separate platform installer cannot satisfy a missing phone dump. XLIFF
  placeholders preserve technical names/extensions in translated help.
- Extend the protected direct-literal audit to chat, directory and room screens.

## Verification

- Combined Debug and instrumentation APKs built and installed successfully.
  Existing local filename: `AbyssME-0.6.0-beta.1-r1-debug.apk`; this is not a new
  public version. JVM: **410 tests, zero failures/errors/skips**.
- Retroid Pocket Classic, Android 14, `MC94516AQF040305380`:
  focused suite **42 passed / 10 skipped / zero failures**; follow-up Settings
  and localization journey suite **11 passed / zero failures/skips**.
- The focused run includes SocialLocalizationTest, FluxerAccountDeviceTest,
  FluxerChatsDeviceTest, LobbyDirectoryDeviceTest, LobbyRoomDeviceTest,
  LobbyPresentationTest, SystemRequirementsLocalizationTest and
  SymbianControlsTest. Seven room tests and two signed-out account tests skipped
  to preserve an existing development account/room. One public-service test
  skipped because live network testing was not enabled. Runner's `OK (52 tests)`
  includes these ten skips; it does not mean 52 passes.
- Resource tests cover all account/chat/directory notice keys, generated versus
  real user text, preserved URLs/IDs and generation-specific requirements.
- Seat layout: English/en-XA/ar-XB, widths 320/393/480/540/720 dp, font scales
  1/1.5/2, light/dark: **90 combinations** inside one test method. This proves
  bounded layout containment, not full RTL navigation or guest mirroring safety.
- `LocalizationAudit --self-test` and `--check` pass: 197 Java files, 80 direct
  UI literal candidates, zero in protected screens, 983 canonical translatable
  resources and zero structural translation errors. Helper/native/XML gaps are
  not fully represented by the direct Java inventory.
- Chat and lobby-directory fixture screenshots were visually inspected with no
  new text overlap observed. Providers are injected offline: no real messages,
  invitations or public rooms were sent by these fixtures.
- Final combined build completed APK assembly, then stalled in lint source-tree
  traversal (`KtModuleUtilsKt.collectSourceFilePaths`, Windows FindFirstFile).
  After prolonged lack of progress it was cancelled. **Fresh lint is unverified**;
  the older 408-warning report must not be attributed to this batch.
  A fresh-daemon retry also stalled, this time in the native dependency build,
  and was cancelled. The successful APK/JVM/device results above are separate.
- Debug was stopped and the official app reopened afterward. This is not a
  whole-game, physical-controller or live multiplayer acceptance claim.

## Commands and Local Evidence

```powershell
java tools/LocalizationAudit.java --self-test
java tools/LocalizationAudit.java --check
.\gradlew.bat :app:assembleFdroidDebug :app:assembleFdroidDebugAndroidTest :app:testFdroidDebugUnitTest -PsymbianRelease=true -PsymbianSourceDir=build/symbian-source-check --offline --console=plain --max-workers=2
.\gradlew.bat :app:assembleFdroidDebug :app:assembleFdroidDebugAndroidTest :app:lintFdroidDebug -PsymbianRelease=true -PsymbianSourceDir=build/symbian-source-check --offline --console=plain --max-workers=2
```

The second Gradle invocation was cancelled during lint, not a successful lint run.
Ignored evidence: `build/localization-social-build.log`,
`build/localization-social-final-build.log`, `build/localization-social-device.log`,
`build/localization-social-lint-retry.log`,
`build/localization-social-regression-device.log`,
`build/localization-lint-threads.log`, `build/localization-social-chat.png`,
`build/localization-social-lobbies.png` and `build/localization-*.tsv`.
JVM XML: `app/build/symbian-release/test-results/testFdroidDebugUnitTest/`.

Final installed artifact SHA-256 (hashing succeeded on retry):

| Local artifact | SHA-256 |
| --- | --- |
| Combined Debug APK | `1bd8c9a548c7b8d7bac06d7d65cc425790ee79607df933fc6f0eabc0c723af0c` |
| Instrumentation APK | `56985d7347854962c350fd63d5e15814b14bd7e48f314dabe5fc23718e0d5641` |

## Remaining Scope

Archive/backup/component subflows, room/publication/temporary-contact backend
notices, helper-generated text and error details, inherited About/Help, safe
multi-process language selection and reviewed translations remain open.
Existing locale folders are partial; no new supported locale is advertised.
Issue #43 stays open. Release assets were neither changed nor reverified here.
