# Portable Symbian Library Titles

Date: 2026-10-19. Local source/debug implementation and Beta 3 preparation;
not included in the public Beta 2 APK. No phone/game archives published.

## Change

Custom titles now belong to each virtual phone's portable data. A versioned,
bounded AtomicFile replaces the installation-only title preferences. Migration
persists the file before removing old values and cannot overwrite a newer edit
on retry. Android stable row IDs, favorites and play history are not transferred.
Title-save errors leave the editor open without changing the displayed card.

Phone archive version 3 includes a checksummed `library-titles.json`. Extraction
validates its schema, UID range, title type/length/content and size before atomic
phone promotion. Restoring creates a new identity; original titles and games
remain unchanged. Re-export uses the restored phone's current edits. Versions 1
and 2 still restore, using native titles until edited. Older apps cannot restore
version-3 archives.

## Fresh Evidence

Retroid Pocket Classic, Android 14, isolated combined debug package:

- `PhoneBackupTest` and `SymbianArtworkTest`: 21 passes, 4.796 seconds. Covers
  migration, title/artwork round trips, independent new identities, UID upper
  bound, rescan/re-export, stale-preference retry, old-format restoration,
  invalid/oversized metadata, failed validation, cancellation and existing
  backup path/hash/storage safeguards.
- `PhoneBackupUiTest`, explicitly enabled: one pass, 40.173 seconds. Opened the
  backup menu, cancelled export destination selection, inspected/cancelled an
  import, restored a separate real Snakes phone, verified custom title and both
  artwork paths in launcher data, then launched the restored game. The capture
  shows its Nokia startup screen, not completed gameplay. Original drive hashes
  were checked; temporary title/art changes and restored phone were cleaned up.
  This run did not exercise a new SAF export write.
- Shared library/focus/LCD/export checks: 20 passes, three conditional skips
  (runner total 23). Native artwork UI was then explicitly enabled: one pass,
  6.324 seconds, including picker result after activity recreation.
- Combined debug APK/test compilation and lint passed. Tests above ran in
  isolated debug applications, not the signed/minified candidate.

Local logs: `build/phone-titles-combined-build.log`,
`build/phone-titles-retroid-tests.log`, `build/beta3-combined-debug.log`,
`build/phone-titles-retroid-ui.log`, `build/beta3-retroid-regressions.log`,
`build/beta3-retroid-artwork-ui.log`.

Inspected screenshots are in `build/phone-titles-captures/`: restore preview,
restore completion and Snakes startup. These are physical-device screenshots;
input was automated. No new claim for physical-controller comfort, audio,
complete-game compatibility, cross-device transfer or abrupt power-loss recovery.
