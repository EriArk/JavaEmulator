# Symbian Phone Backups

Originally verified on 2026-10-14 on `codex/symbian-integration`; the base
whole-phone workflow is included in the published combined beta, as recorded in
[PROJECT_STATUS](PROJECT_STATUS.md). Custom artwork is included in public
0.4.0-beta.2. The title extension below is local preparation for Beta 3 on
2026-10-19. No private phone archive was published.

## Custom Titles (Beta 3 Preparation, 2026-10-19)

New backups use archive format version 3 and also include custom game titles.
Restore continues to accept versions 1 and 2. Update the destination app to
Beta 3 or newer before restoring a version-3 backup; public Beta 2 cannot read it.

Titles live in a bounded, versioned `library-titles.json` within each phone.
Keys are guest application UIDs, not Android library row IDs or title matches.
The file is verified and promoted with the restored phone, so identically named
games or matching UIDs on different phones remain independent. Rescans and later
backup exports preserve current edits. Existing local preference overrides are
migrated before library reads or menu-driven export, with durable file writes
before removing the legacy values. A failed title save keeps the editor open.

Native titles are used when an older backup has no overrides. Favorites, recent
history and Android row IDs remain local. Titles are limited to 160 characters;
invalid UIDs, blank/control-character titles and oversized metadata are rejected.
See [title transfer checks](checks/2026-10-19-phone-titles.md).

## Custom Artwork Follow-Up (2026-10-18)

Beta 2 backups include user-selected covers and icons for Symbian, N-Gage and
N-Gage 2.0. They use archive format version 2. Restore still accepts version-1
backups without artwork. Older app versions cannot read version-2 backups;
update the destination app before transferring one.

Overrides are staged under `artwork/<uid>/user-icon.png` and `user-cover.png`
and promoted with the new phone's files. The launcher resolves these overrides
using the new identity. Existing phones retain their original storage. Re-export
uses the restored phone's current images, including subsequent edits and resets.
Automatic images are regenerated from the installed games, not copied as user
overrides. No favorites or recent-play history is added to the archive.

Only the two supported filenames and valid Symbian UIDs are accepted. Images
must decode, be at most 16 MiB and contain at most 8 megapixels; original image
bytes are retained. Existing ZIP path, link, checksum and staging safeguards
also cover artwork. See [current checks](checks/2026-10-18-symbian-artwork-backup.md).

## User Workflow

Open **Library transfer > Symbian / N-Gage phone backups**, or **System files >
Phone backups**. Choose a phone, then **Back up phone > Choose where to save**.
Android's document picker selects the destination. The game must be closed;
this is a saved-phone backup, not a running-game save state.

**Restore phone backup** shows the phone, unpacked size and included data before
confirmation. **Restore as new phone** creates a separate copy and indexes its
games in the shared library. It never merges or replaces existing phones/saves.
The ordinary library **+** also recognizes these backups, including renamed ZIPs.
Selecting one through the system/game importer routes it to the restore preview
instead of treating its contents as a fresh firmware dump.

The ZIP includes the supplied ROM/RPKG, installed system files, writable drives,
games, saves, supported engine settings, game-to-phone bindings and AbyssME game
profiles. It can therefore contain private or copyrighted material: keep it
private and restore only trusted backups. Firmware does not need to be selected
again. Java/Mophun retain their separate library transfer; experimental menu
labels make that scope explicit.

Not transferred: Android permissions/paths, host network passwords, engine
scripts/resources supplied by the APK, logs, screenshots, launcher favorites or
recent-play history. The preloaded N-Gage installer cache and pending imports are
not included; a platform already installed inside the phone is retained.

## Safety and Storage

- Native shutdown completes before inventory/hash/copy. The direct backup entry
  and archive preview do not prepare the original phone. This avoids EKA2L1's
  incidental SMS-service writes simply from opening a restore preview.
- Versioned `abyssme-phone.json` inventories paths, directory entries, sizes and
  SHA-256 hashes. ZIP paths, links, duplicates/case collisions, parent conflicts,
  missing/unlisted files and CRC/hash mismatches are rejected. Limits: 4 GiB ZIP,
  8 GiB expanded payload, 100,000 entries and bounded settings/manifest sizes.
- Export is prepared privately before writing through SAF. Restore requires
  space for the staged ZIP plus expanded data and a 64 MiB reserve. Copy failures
  do not report success. An incomplete provider output is deleted where the
  provider supports deletion; otherwise it may remain and must not be used.
- Restore extracts into `symbian-systems/pending-restore-*`. Config YAML is read
  with the engine's parser and rebuilt from allowed settings: host paths, remote
  endpoints and debugger settings cannot redirect the restored runtime. Device
  registration must describe one isolated phone. Native identity/library checks
  run before promotion; the native session is stopped before moving the folder.
- A fresh identity and embedded `profiles/<uid>` are promoted together using an
  atomic directory move. Existing profile locations remain unchanged. Global
  source bindings are not overwritten. Restored bindings are used when no prior
  binding exists; multiple matches remain a user choice.
- Interrupted staging is removed on the next host entry. A promoted phone can
  be reindexed on the next normal entry if the process died before publishing
  its launcher snapshot. Preview/process interruption may require selecting the
  ZIP again; no resumable background-transfer claim is made.

Files are verified before native initialization. The restored guest can then
update its own OS state normally; it is not expected to remain byte-identical
after starting. In the N-Gage check, EKA2L1 regenerates `sms_settings.dat` on
preparation. The original phone's drive hashes must remain unchanged.

## Verification

Retroid Pocket Classic, Android 14, isolated experimental Handheld/Phone apps.
Production apps and their data were not replaced. Physical controller ergonomics,
audio, full-game completion, cross-device acceptance and sudden power-loss
durability are not established by these automated checks.

- `build/symbian-backup-build.txt`: both experimental app/test variants build.
  Installed APK SHA-256: Handheld
  `020a54350285e32ba0ce1633bbda038e6eaf3c75ebc7be6549875aa2ddd4f5d6`;
  Phone `1cc7dd724377fd9d8f591eecc52363df07aed5e6c3897f0b0aad894424d3ce4a`.
- `build/symbian-backup-fdroid-final-tests.txt` and
  `build/symbian-backup-phone-final-tests.txt`: **19 passed per variant**, no
  skips, 41.554 / 40.258 seconds. Includes the new backup tests, existing catalog
  checks and shared Symbian controls checks. This is not a rerun of every older
  experimental test.
- `build/symbian-backup-ordinary-tests.txt`: ordinary Handheld/Phone compile
  without the optional module; 14 freshly executed JVM tests pass per variant.
- `PhoneBackupTest`: round-trip saves/profiles/bindings and repeated-copy
  isolation; corrupt/missing/unsafe archives; cancellation, failed validation,
  simulated low space, staging recovery, source links/changes and native
  configuration sanitization.
  Follow-up `build/symbian-backup-{fdroid,phone}-profile-tests.txt`: **seven
  passed per variant**, 1.272 / 1.362 seconds, with the round-trip assertion
  strengthened to load the actual serialized profile and verify image rotation,
  touch layout and a custom controller binding. App APKs were unchanged.
- `PhoneBackupUiTest`: backup menu, preview/cancel, separate restore, library
  publication and Snakes launch from the restored phone. Original drive hashes
  are checked before/after. Test-created restored copies are removed afterwards.
- `build/symbian-backup-saf-tests.txt`: the final Phone APK also passed the
  document-picker export and complete restore/launch flow, **one test**, 45.187
  seconds. This includes a real provider write, not just a locally created ZIP.
- SAF export produced `AbyssME-phone-RH-29-2026-10-14-1509.zip` in the Retroid's
  Downloads. Independent streaming verification of the saved ZIP checked all
  1,238 payload files, 55,465,409 unpacked bytes, against its size/hash inventory.
  The archive and its local copy stay private and ignored.

Local captures are `build/phone-symbian-backup-{preview,saved,restored,game}.png`
and `build/phone-symbian-backups-menu.png`. They are real device captures, not
concepts. The game capture shows Snakes startup, not a completed game.

## Never-Booted Phone Follow-Up (2026-10-14)

A supplied phone may have no `config.yml` before its first launch. Portable
export now emits defaults for that case without booting or modifying the source.
Malformed existing configuration still fails validation. The new regression
exports/restores such a phone and confirms its source config remains absent.
All eight backup tests pass in both final touch-update APKs as part of their
24-test suites; see [current follow-up evidence](SYMBIAN_INTEGRATION.md#direct-touch-and-visible-rotation-2026-10-14).

## Remaining

General SIS dependency handling, cache/version
management and external-launcher acceptance remain separate work. This does not
fix the earlier intermittent Asphalt `USER 44` issue or promise compatibility
for every Symbian game. Distribution still requires the engine/material review.
