# Library transfer recovery - 2026-11-01

## Scope and availability

Shared Java/Mophun library import/restore and export, based on `f50cc55c`.
Unreleased source and a separate `io.github.eriark.abyssme.debug` installation
on Retroid Pocket Classic, Android 14. The official package and public release
APKs were not replaced. No engine, firmware or commercial game files changed.
This does not implement whole-library EKA2L1 migration or address Asphalt/N95.

## Changes

- Partial/canceled batches retain only selected games that did not commit.
  Retry opens a review screen; importing remains an explicit second action.
  The unpacked backup stays available for that retry instead of being deleted.
- Shared-file failures remain retryable even when every selected game succeeded.
  Shared-file result rows no longer inflate the completed-game count.
- Repeated sources fail before reading/recompiling the original game; the
  existing commit-time duplicate guard still protects concurrent arrivals.
- A staging cleanup failure after a successful database commit is logged rather
  than misreported as a failed import; startup recovery retains committed games.
- Export builds a local ZIP before opening the provider destination. Preparation
  errors/cancellation leave that destination unopened. The previous unconditional
  delete-on-error is removed. Open/write/close failures remain errors, not success.
- Result lists, selection and Retry/Done remain distinct after Activity recreation
  and cancellation before the first completed game. Existing localized labels are
  reused. Result text has an inset from the focus border.

## Verification

Combined Debug and instrumentation build passed in **3m 2s**:

```text
:app:assembleFdroidDebug :app:assembleFdroidDebugAndroidTest
-PsymbianRelease=true -PsymbianSourceDir=build/symbian-source-check
--offline --console=plain --max-workers=2
```

Installed using `adb install -r -d` into the separate Debug package. Final APK
SHA-256: `4caa4f5fa0cb54716145cb163a362ccd5ea7d4d8d81761e51be817cb066a0072`.

**39 Android instrumentation tests passed in 15.473s on physical Retroid:**

| Suite | Passed | Coverage |
| --- | ---: | --- |
| LibraryImporterTest | 27 | Copy-only saves/settings/artwork, duplicate and rollback guards, staged export, partial/canceled retry, shared-only retry |
| TransferRecoveryTest | 4 | Restored picker and persisted folder grants |
| CompactSettingsTest | 7 | Selection/results, Retry review, shared-only review, recreation, early cancellation |
| AdditionalGamesTest | 1 | Unknown/damaged additional-game catalog entries are retained |

Fifteen cases were added in this batch. The newer-save regression changes the
first imported game's destination save, repairs the second source, retries only
that second game and verifies the first save is unchanged. Export tests cover
preparation, cancellation, provider open/write/close failures and a checksum-checked
archive round trip. Shared-copy failure is injected with an unsupported target
symlink; the original target remains unchanged.

An initial test incorrectly treated an existing shared-file conflict as an error.
The importer correctly retained that file; the fixture was changed to an actual
rejected path. The final full run above has no failures.

Local evidence (ignored): `build/transfer-recovery-verified-build.log`,
`build/transfer-recovery-verified-device.log`, `build/transfer-partial-result.png`,
`build/transfer-retry-review.png`. Both final dark-theme screenshots were inspected
at Retroid's 1240x1080 resolution. Their result rows are test fixtures, not a user's
failed import or a gameplay compatibility claim.

## Limits

- Local ZIP staging needs additional free space. A provider failure/cancellation
  during final copying can leave an incomplete output; SAF offers no universal
  atomic replacement. Use a new filename and keep an earlier verified backup.
- After process death, the existing recovery rescans the source for review;
  it does not automatically resume writes. No process-kill-during-write or cloud
  provider live test was added in this batch.
- These are installed-device fixture and UI checks, not a fresh real J2ME Loader
  installation migration, manual controller acceptance, or complete-game testing.
  Symbian phone backups and renderer/game compatibility were not modified/tested.
