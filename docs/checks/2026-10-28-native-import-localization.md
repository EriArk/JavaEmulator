# Native Import Localization and Queue Persistence

2026-10-28, main source after `80814005`. Public source, unreleased combined
Debug work. No new supported language, public APK, GitHub release or Nextcloud
distribution. The official app and owner games, saves, systems and accounts
were not cleared or replaced. Release assets were not reverified in this batch.

## Implementation

- Add 163 app-owned native import errors to the existing 55 backup/removal
  keys: 218 total. Errors retain IOException behavior, English diagnostics and
  live causes. Android presentation uses stable identities, not English matching.
- Twenty typed progress events (14 new) cover input staging, ZIP/RAR/7z,
  system-file preparation, components, queue processing and backup/restore.
  Legacy callbacks retain English fallbacks and propagate cancellation.
- Eleven queue notice identities and optional structured errors survive journal
  reload and snapshots. Version-1 scheduling, roles and retained file identities
  stay unchanged. Old journals lack this metadata and retain their raw wording.
  Unknown or malformed metadata falls back to the original diagnostic; it does
  not select, install, retry or delete anything. Generated batch headings carry
  explicit identity instead of interpreting a user's filename.
- Bound persisted error arguments and reject journals over the existing 1 MiB
  read limit before replacing the previous atomic file. Regression checks keep
  the last readable journal and verify its retained payload after rejection.
- Localize native inspection state when it is rendered, including after view
  recreation. Extract Mophun installer labels; Mophun backend errors remain a
  separate open boundary. Preserve user names and unknown OS/native errors.
- Repair collection-import cancellation: InterruptedIOException,
  InterruptedException or an interrupted worker must escape the per-phone
  failure handler. Previously installed phones remain; staged files are cleaned
  and later phones are not installed. Incomplete ROM/Z pairs carry typed reasons
  while preserving their source names and the legacy textual summary.

## Verification

- Combined Debug app/instrumentation assembly, JVM tests and fresh lint pass.
  JVM: **410 passes, zero failures/errors/skips**. Lint: **zero errors / 418
  warnings**; existing warnings remain.
- Retroid Pocket Classic, Android 14, serial `MC94516AQF040305380`: Debug-only
  update, with `adb devices` ready and `sys.boot_completed=1` before testing.
  **124 passes, six optional skips, zero failures** (runner: 130 total).
- Device classes: SystemTransferLocalizationTest, NativeImportLocalizationTest,
  SystemRequirementsLocalizationTest, ArchiveBatchTest, UnifiedImportTest,
  PreparedSystemArchiveTest, GameInstallFlowTest, SisComponentsTest,
  CompressedGameArchiveTest, SystemFileCatalogTest, PhoneBackupTest,
  GameRemovalTest, RawPhoneArchiveTest and PackageDependenciesTest.
- Optional skips: real platform dependency installation, supplied phone
  collection, supplied RAR/solid 7z, RAR5/encrypted fixtures, real installer
  metadata and supplied SIS/SISX preview. These are not passes. Synthetic archive,
  component, backup/removal and cancellation checks do run on the device.
- All 218 errors, 20 progress events and 11 queue notices are checked against
  English fallbacks and isolated expanded/RTL pseudolocale contexts. Persistence
  tests cover old, future, malformed and oversized metadata, names containing
  percent signs, success/reset paths and raw external messages.
- The existing 144 locale/font/viewport/theme/state queue layout combinations
  pass again. This is not full RTL navigation, translation review, native game
  performance, live Social or complete-game acceptance. Device locale unchanged.
- Manual audit and self-tests: 199 Java files, 30 direct UI literal candidates,
  zero in protected extraction boundaries, 1394 canonical translatable resources,
  zero translation-structure errors. Resource presence is not language approval.

## Reproduction and Local Evidence

```powershell
java tools/LocalizationAudit.java --self-test
java tools/LocalizationAudit.java --check
.\gradlew.bat :app:assembleFdroidDebug :app:assembleFdroidDebugAndroidTest :app:testFdroidDebugUnitTest :app:lintFdroidDebug --rerun -PsymbianRelease=true -PsymbianSourceDir=build/symbian-source-check --offline --console=plain --max-workers=2
```

Ignored local logs: `build/localization-import-verified-build.log`,
`build/localization-import-retroid.txt`, `build/localization-*.tsv`.
JVM XML: `app/build/symbian-release/test-results/testFdroidDebugUnitTest/`.
Lint XML: `app/build/symbian-release/reports/lint-results-fdroidDebug.xml`.
Debug filenames retain `0.6.0-beta.1-r1`; they are not a new public revision.

| Installed local artifact | SHA-256 |
| --- | --- |
| Combined Debug APK | `116a0a6d84b3845dbee645d34687733760713a7a665818e8f918f52519b5cdb6` |
| Instrumentation APK | `c06a73be9e4b6aed50f1bf08d88111db7cd8e913af2c481cbc6b2b2a221099da` |

After checks, Debug was stopped and the official app reopened. No new screenshot
set was published; this batch changes message plumbing and import safety, not
the approved visual design.

## Remaining Scope

Room/publication/contact backend notices, remaining app-owned helper/error
messages and inherited About/Help still need extraction or review. Unknown
external diagnostics must not be translated by guessing their English wording.
Safe process-aware language selection and reviewed language batches remain
outstanding. Old stored raw queue messages are intentionally not retroactively
translated. No locale is announced as complete; #43 remains open.
