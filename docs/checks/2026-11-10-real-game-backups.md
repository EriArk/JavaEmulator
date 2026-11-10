# Real-game backup and restore — 2026-11-10

Local-only follow-up to [the real-game launch checkpoint](2026-11-10-real-games.md).
The same combined Debug candidate runs on the dedicated Android 11/API 30
x86_64 emulator with ARM64 translation. The handheld remains unused at the
maintainer's request. No production runtime change, public source push, signed
release or asset publication is part of this checkpoint. Public release assets,
live services, audible audio and physical hardware were not rechecked.

## What the new fixture verifies

`ProvidedBackupRoundTrip` copies only the supplied game's converted payload,
profile and generated saves into a temporary library. It uses the production
staged export, archive checksum validation and library importer to restore into
another empty database/root. It verifies payload hashes, every save-file path
and hash, catalog metadata and available screen settings. Java DEX is excluded
from the archive and recompiled on restore. Re-importing the same backup must be
rejected without changing the destination save bytes or source saves.

The Java observer stops only its UUID fixture VM before export, replaces only
that fixture's files with the restored result, and launches a fresh VM. The
Mophun fixture joins its worker, restores into a separate root, switches its
in-process Config to that root and launches a new activity; finally it restores
the original Config and deletes its temporary tree. Neither fixture touches the
owner's active library or saves. Java's catalog favorite flag is synthetic test
metadata, not evidence of real game progress. Shared `fs` data and Android's
DocumentsUI/provider picker are outside this particular test.

`javaBackupRoundTrip=true` and `mophunBackupRoundTrip=true` enable the additional
cycle. It requires game-generated save files, so an empty-data run cannot be
reported as a passing save test. `javaRelaunchActions`, `mophunSaveKeys` and
`mophunRelaunchKeys` record explicit game input before/after restoration;
`mophunRequiredSave` can additionally require a particular nonempty guest save.

## Evidence and first results

Private evidence stays ignored in `build/checks/2026-11-10-real-backups/`:
per-run input SHA-256/arguments, instrumentation logs, logcat, captures, and
`backup.json` save hashes. Games, archives, generated saves and captures are not
added to Git. Exact game editions/hashes are the same as the linked launch record.

The initial opt-in test APK builds in 15s. Deep Abyss 1.4 passes export, restore,
duplicate rejection and fresh activity launch in 46.020s. It preserves
`maxlevel.sav` and `resume.sav`; the latter is empty in this first run, so this
result does not establish resumed level progress. Diamond Rush 1.1.6 passes in
54.091s with four RMS files (`DiamondRush` and `Preferences` headers/records),
and the restored copy displays the title screen.

The initial SimCity 1.23.87 run remains in tutorial setup and produces no save
files. The new guard correctly fails with `Game must generate real save files
before backup` (67.603s); no backup or save acceptance is inferred from that run.
A separate menu-observation run is retained rather than erasing this failure.

## Follow-up observations

- `diamond-resume/diamond`: **1 test passes, 64.343s** using the updated fixture.
  Four RMS files survive export/import and duplicate rejection. A fresh VM
  reaches the title screen, New game menu, loading screen and a controllable
  level. The menu explicitly says New game; this is **not** acceptance of loaded
  level progress, despite the local evidence directory's `diamond-resume` name.
- `save-confirm/deep-abyss14`: **1 test fails, 30.504s**, on the explicit
  nonempty `resume.sav` assertion. Capture collection was interrupted and the
  two recovered save-menu PNGs are empty; they are not visual evidence.
- `deep-exit-observe/deep-abyss14`: **1 test passes, 50.427s** with the nonempty
  assertion disabled for menu observation. X opens the guest quit confirmation;
  a second X cancels it and returns to gameplay. The two save files survive the
  archive round trip, but `resume.sav` is still empty. This explains why the
  previous key sequence cannot establish a completed guest save operation.
- SimCity menu-only observations pass with backup disabled: 48.571s at the build
  wheel, then 53.200s reaching the pause menu. These are navigation evidence,
  not save/restore checks.

- `deep-save-fire/deep-abyss14`: **1 test passes, 38.015s**, retaining the strict
  nonempty `resume.sav` assertion. X opens the confirmation and A confirms it.
  The saved resume file has SHA-256
  `f87d5b5068fe6cf1c3d4a2063d011b8be47b90c4ea64c03462b7bb80402840d7` before
  and after restoration. The fresh activity offers **Resume game**, and A enters
  the Level 1 introduction. This establishes recognition of the restored guest
  save; exact in-level position and later-level progress remain unverified.
- `simcity-menu3/simcity`: **1 observation test passes, 65.231s**. Pause → Menu
  opens a confirmation explicitly stating that the game will be saved.

- `simcity-saved/simcity`: **1 test passes, 102.420s**. Confirming Pause → Menu
  generates `a2.1.rsr` and `a2.rsh`. Both survive archive export/import and
  duplicate rejection byte-for-byte, and the fresh VM reaches the main menu.
  This supersedes the initial no-save scenario only for this explicit save path.

- `deep-save-play/deep-abyss14`: **1 test passes, 45.643s** with the same strict
  save guard and `mophunRelaunchKeys=96,96,22`. The restored Resume game entry
  reaches Level 1 gameplay and accepts directional input. The nonempty resume
  file SHA-256 is `ecd7b06dbb425f05024c688fed53359b429d19cb8e1e05952d2490ab8da43225`
  on both sides of this run's archive cycle. Guest save contents may differ
  between runs; equality is required within each run.

- `simcity-resumed/simcity`: **1 test passes, 119.191s**, including additional
  navigation after restoration. Sunny Glen appears with a saved-game disk icon;
  selecting it loads the city and tutorial instead of the new-game asteroid
  introduction. Both RMS files match the source hashes. This accepts recognition
  and reopening of this early district save, not developed-city progression or
  completion of the tutorial.

The refined test APK builds in 13s and has SHA-256
`699441d3999bd7204d5ec0d8f0c71c27c0edbc252497329d5a350c2a80664f13`.
The initial test APK has SHA-256
`27153584035e61e3153fbac8e8e3b093f82023e6e67272666aff6ad026169fdf`.
Both test only the existing combined Debug candidate, SHA-256
`afaab3a0ac6ada2877279cc2440a80f188a5c1073e6a83afda1ea8ef94eee53b`.
No broad JVM/lint rerun is claimed for these test-only edits; earlier production
checks remain dated evidence. Source copies were compared with the compiled
worktree, the installed production APK hash was rechecked, and all three original
game input hashes remain unchanged. Private evidence includes the runner and exact opt-in arguments.

## Reproduction controls

The opt-in tests are `ru.playsoftware.j2meloader.config.ProvidedJavaSweepTest`
and `ru.playsoftware.j2meloader.mophun.ProvidedMophunLifecycleTest`. Supply an
independently permitted game under the Debug app's external `files/sweep/java`
or `files/sweep/mophun` directory and pass only its basename as `javaGame` or
`mophunFile`. Both fixtures delete only their temporary conversion/profile/save
roots and recreate the associated capture directory on each invocation.

For the nonempty Deep Abyss save check, retain the default three initial A presses
and set `mophunBackupRoundTrip=true`, `mophunSaveKeys=99,96`,
`mophunRequiredSave=resume.sav`. Android key 99 is X and 96 is A; the guest handles
A as confirmation here. `mophunRelaunchKeys=96` selects Resume game.

For the Java samples use `javaWidth=320`, `javaHeight=240`,
`javaBackupRoundTrip=true`. `javaActions` and `javaRelaunchActions` are semicolon-
separated `key:<Android key code>`, `tap:<x>:<y>` or `wait:<milliseconds>` actions.
The exact per-edition sequences and input digests are in ignored `input.json`
files beside each run, with the runner retained in the evidence root. These
sequences are observations of these editions, not universal game navigation.

The body of [#13](https://github.com/EriArk/JavaEmulator/issues/13) was updated
with the nonempty-save checkpoint and its limits; its original history is retained
and the issue remains open.

## Boundaries

Byte-preserving restore and a live restarted process are narrower than complete
game progress, audio, full-level completion or release acceptance. Mophun 1.1,
other editions, whole-collection/shared-files restore, external-storage provider
behavior and physical controllers remain separate work. The N95 translation
blocker and unidentified Crash #52 report from the earlier checkpoint remain open.
