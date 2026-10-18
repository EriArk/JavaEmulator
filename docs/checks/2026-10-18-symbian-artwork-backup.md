# Symbian Artwork in Phone Backups

2026-10-18. Local, unreleased after 0.4.0 Beta 1. No version bump, commit,
push, release, Nextcloud upload or production-app replacement in this checkpoint.
Live GitHub release state was not rechecked.

## Behavior

- Version-2 whole-phone backups include custom covers and icons by game UID.
  Existing version-1 backups remain readable. Older apps cannot read version 2.
- Existing overrides remain at their host-owned location. Restored phones keep
  overrides inside their own `artwork` directory, promoted atomically alongside
  guest files and profiles. The launcher uses the same resolver as the exporter.
- Restored games use their new phone identity, not the original one. Resetting
  or replacing art on a copy leaves the source unchanged. Re-export follows
  the copy's current art. Automatic images remain engine-generated, not overrides.
- Allowed artwork paths contain a valid 32-bit positive UID and one of two
  filenames. Files are bounded to 16 MiB / 8 megapixels and decoded before use;
  contents retain the existing SHA-256 / CRC validation. Staging failures do not
  publish a partial phone or write art into a separate global directory.

## Verification

- Combined debug app and instrumentation builds passed. Local logs:
  `build/symbian-artwork-backup-build.log` and
  `build/symbian-artwork-backup-test-build.log`.
- Combined lint: zero errors, 414 warnings. Log:
  `build/symbian-artwork-backup-lint.log`.
- Retroid Pocket Classic, Android 14, isolated combined debug package:
  PhoneBackupTest (12), SymbianArtworkTest (5), SymbianLibraryTest (6 executed,
  1 optional skip). Runner reports 24 tests; 23 executed passes in 2.54 seconds.
  Log: `build/symbian-artwork-backup-retroid.log`.
- New checks cover original/copy isolation, launcher resolution, largest valid
  UID, repeated export after edit/reset, old archives without art, corrupt bytes,
  valid-hash non-images, empty/oversized images, unsupported paths, symlinks and
  failed native validation. Existing saves/profiles/bindings checks also passed.
- The installed PhoneBackupUiTest passed in 40.179 seconds with the private
  Snakes fixture: backup actions, preview/cancel, restore as separate phone,
  native library indexing, icon/cover resolution, guest startup and menu exit.
  Original drive hashes stayed unchanged; temporary custom image bytes were
  restored in cleanup. Log: `build/symbian-artwork-backup-ui-retroid-final.log`.
  The first attempt stopped on the old assumption that Game menu is permanently
  visible; the test now opens Show game controls before accessing that button.
- [Restore preview capture](../../screenshots/development/2026-10-18-symbian-artwork-backup/README.md)
  was visually inspected. The game capture shows the publisher startup screen,
  not gameplay or completion. The test did not drive Android's destination
  picker; it supplied a private archive to the normal restore-preview flow.

## Limits

The phone backup remains separate from the Java/Mophun library ZIP. Favorites,
history and app-wide preferences are not transferred. Tests are not complete-
game, physical-controller, cross-device or sudden-power-loss acceptance. Native
Symbian execution was not tested on the x86 Android emulator in this checkpoint.
Private game fixtures and phone firmware remain outside Git.
