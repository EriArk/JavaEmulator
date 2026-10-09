# Library transfer (unreleased)

Available in development builds from **Settings > Library > Library transfer**. Published
beta.2 APKs do not include this workflow. Keep the original installation and a
backup until you have checked your games and saves in the destination app.

## From J2ME Loader

Choose **Import from J2ME Loader > Whole library** and select an accessible library folder in Android's file
picker. Review the detected games, select the ones to copy, then import.

Some J2ME Loader document providers allow only a single game folder at a time.
For these, choose **Single game** and the game under `converted`, then open
**Transfer options > Add saves** for its matching folder under `data` and
**Add settings** for the matching folder under `configs`.
All selection happens in the app and Android file picker; root and manual copying
into AbyssME's private folders are not required. Import without saves requires
confirmation. Arbitrary private app storage is not accessible.

The source is read-only. Java resources are converted again; foreign compiled
code and the old catalog database are not copied. Compatible settings and RMS
saves are retained. Duplicate imports are skipped, and same-title variants are
not silently substituted. Optional shared filesystem import copies missing
files only and reports conflicts without replacing existing files.

## Between AbyssME installations

1. Exit the running game, then choose **Back up library** in Library transfer.
2. Save the ZIP using Android's file picker and transfer it to the other device.
3. In the destination app choose **Restore backup**, select the ZIP, review and restore.

Backups contain installed resources, saves, compatible per-game settings,
library metadata and shared game files. Unprepared folder links must be launched
first; they cause an explicit backup error rather than being silently omitted.
Restoring does not overwrite an existing game's saves. Own-backup metadata such
as favorites is retained; global storage paths and external shader references
are not imported. An optional engine must be present to restore its games.

Archive paths, declared sizes and SHA-256 hashes are checked before preview.
Malformed archives fail without installation. Limits: 2 GiB uncompressed,
50,000 files, 16 MiB metadata. Export is blocked while this installation has an
active Java game process. Third-party games should also be closed before import.

## Verification and limits

The compact transfer screen separates the operation chooser, preview and result.
Preview keeps the game list central and Select all / Import at the bottom.
Shared-file options and single-game save/settings pickers live under the header's
Transfer options button. Back during a running operation requests safe cancellation;
Back from a preview returns to the chooser. Original-source and missing-save
warnings remain. Archive/import rules are unchanged.

The 2026-10-09 UI checks use synthetic preview/result states for selection,
recreation, cancellation and retry, alongside the existing archive/import tests.
A real Phone menu backup of 11 games was saved through Android DocumentsUI and
reopened for preview without importing duplicates into the source installation.
These are emulator checks, not physical-device acceptance.

On 2026-10-08, an Android emulator running genuine J2ME Loader 1.8.2 was used to
import Sudoku through the single-folder UI, including separate save and settings
grants. Source and imported RMS file hashes matched, and the imported game opened
the saved board. A Phone backup was restored through the menu into Handheld;
existing installed entries were retained. Local tests cover rollback, duplicates,
malformed input, archive tampering and copy-only behavior.

This is not proof for every J2ME Loader version/document provider or physical
device. Shared-file conflicts are not automatically merged. Backups are not
encrypted and can contain personal game data; share them deliberately.
