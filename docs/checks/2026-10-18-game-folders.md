# Game-folder discovery and Mophun catalog promotion

Date: 2026-10-18. Development source after beta.6, not a new release.
App version remains 0.3.0-beta.6 / code 305 in local debug builds.
The signed public beta.6 APKs are unchanged. No new APK was uploaded to GitHub
or Nextcloud; no games, firmware or private diagnostic data are committed.

## Changes

- Shared Add folder scanning recognizes JAR, JAD, KJX, MPN, ZIP and 7z names
  case-insensitively. A same-base JAD next to a JAR is not a second game.
  Existing archive installation and validation rules are unchanged.
- Query document providers directly so denied/null/loading listings cannot
  silently masquerade as an empty successful scan. Accessible siblings remain
  discoverable when a nested folder is blocked. Root failure makes no additions.
- Bound traversal to eight nested levels and 10,000 documents, counting noise
  files and directories too. Avoid directory cycles, report truncation and honor
  interruption. This is not a background watcher or a large-library benchmark.
- Commit discovery as one Room transaction. Report new/existing counts only
  after completion. Existing source URIs and validated source aliases are not
  replaced, so custom metadata and history survive a rescan. A failed batch
  rolls back rather than reporting partial success.
- Both library/settings entry points use application context in workers and
  avoid accessing detached fragments. Remember a selected folder after successful
  indexing, not before an unsuccessful scan.
- Mophun installation promotes the pending card in the same catalog transaction,
  retaining its ID/favorite. Reimporting a validated copy reuses an installed
  card, preserves its history/artwork and removes only an indexed placeholder.
  A source alias is remembered after executable validation and hashing.
  No save/profile directory is changed by this catalog operation.

## Verification

- Java/Mophun Handheld + Phone debug builds and Phone instrumentation APK built.
- Combined Handheld + Phone debug builds and Phone instrumentation APK built with
  `-PsymbianExperiment=true -PsymbianSourceDir=build/symbian-source-check`.
- Final combined run: 32 JVM checks per variant passed; lint reported zero
  errors, with 402 Handheld / 409 Phone warnings remaining.
- Android 15 x86_64 Phone emulator: **26 passed**, no skipped cases.
- Retroid Pocket Classic, Android 14, combined Phone debug: **26 passed**, no skips.
- The selected suites are `GameFolderIndexerTest` (9), `MophunCatalogTest` (2)
  and existing `LibraryImporterTest` (15). They cover mixed extensions, pairs,
  inaccessible/loading providers, cycles/bounds, cancellation, repeat discovery,
  source aliases, transactional rollback, placeholder promotion and preservation
  of existing metadata. Transfer/backup regression cases remain passing.

Local logs: `build/folder-import-build.log`, `build/folder-import-combined.log`,
`build/folder-import-final.log`, `build/folder-import-emulator.txt`,
`build/folder-import-retroid.txt`, `build/folder-import-retroid-final.txt` and
`build/folder-import-emulator-final.txt`. These ignored logs are maintainer evidence,
not public assets or a requirement to build the application.
The official Retroid Handheld beta.6 was not replaced; checks used the existing
isolated combined Phone debug package, then returned to the official library.

## System-picker workflow

On the Phone emulator, selected a test directory under Download through Android's
actual folder picker and granted access. The directory contained a Sudoku JAR
and a loose MPN in a child folder. Both appeared as discovered entries. Opening
the MPN card installed it and reached a rendered, animated game menu; exiting
and selecting the same folder again produced no second MPN placeholder.
Read-back of the stopped app's database confirmed one ready MPN row (ID 18),
its validated source alias and one still-indexed Sudoku row (ID 17).

Fixture identities:

- SudokuJ2ME-0.1.jar: SHA-256
  `642458b610215810cbf30df5403364129f456ea8074c9cd9128b811bacc0337e`.
- Local `DeepAbyss.mpn` filename: SHA-256
  `a4a7c9e78f8762432907d8bbcfed71cab50877de29c3a4cc448471d6b90ecc0f`.
  Its observed menu has a bubble background and version 1.1. The filename is
  not evidence of game identity; this check is **not** Deep Abyss 1.4 acceptance.

Local captures are in `build/folder-smoke/`. No newly extracted commercial game
or mislabeled fixture is added to the repository or public screenshot gallery.

## Boundaries

This work does not implement Mophun ZIP/7z extraction, mixed-platform archive
installation, loose Symbian/SIS/firmware folder discovery or game-content
deduplication during a filename-only scan. Unknown copies can initially appear
as pending entries until installation validates their bytes. Modified files at
an already indexed URI require explicit reimport; rescanning is discovery, not
a silent update of installed games. Provider behavior outside the covered
fixtures/system picker, real physical-controller acceptance and full gameplay
are not certified by these checks.
