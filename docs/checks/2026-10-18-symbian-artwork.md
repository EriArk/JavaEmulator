# Symbian/N-Gage artwork - 2026-10-18

## Availability

Local source and isolated debug installations after 0.4.0 Beta 1, code 306.
No new version, release, commit/push or deployment in this checkpoint. Earlier
unreleased visual-polish work is preserved. Production packages are untouched.

## Shared fix

Native cards bypass the Java/Mophun action menu and use negative stable IDs.
The existing picker rejected every negative ID and queried only the Java Room
database. Merely showing an Artwork button would therefore not have worked.

- External library items can opt into custom artwork by providing a host-owned
  directory. Symbian's adapter implements this for all three native platform
  labels, with no title-specific behavior or native engine calls.
- The common Artwork menu exposes Icon / Cover > Choose image / Use automatic.
  Java's refresh-extraction command is not shown for native games.
- Picker results resolve the current native entry from its stable ID, including
  after activity recreation. Missing games are not silently written back to an
  obsolete path. A cancelled result does not change an image. Permission errors
  from the image provider use the existing error feedback instead of escaping
  the worker thread.
- Validated images are saved atomically through the existing GameArtwork helper.
  Overrides live under `files/symbian-library/user-art/<system>/<uid>/` and take
  precedence over generated icons/covers. Neither generated art nor guest data
  is overwritten. A refresh of the native index cannot replace the overrides.
- Reset removes only the selected override and reveals the current automatic
  image, or the existing launcher fallback when none exists. Library cards and
  the selected detail pane are refreshed without a restart or Java database row.

## Checks

- Combined debug APK and instrumentation APK built with
  `-PsymbianExperiment=true -PsymbianSourceDir=build/symbian-source-check`.
- Base Java/Mophun debug build and instrumentation build passed, as did all
  39 JVM tests. Combined lint before the final permission-catch/test cleanup:
  zero errors, 414 warnings; the final sources compiled successfully.
- Retroid Pocket Classic / Android 14, isolated combined debug: runner reports
  24 tests, with 23 executed passes and one optional native-indexing test skipped.
  Suites: SymbianArtworkTest, SymbianArtworkUiTest, SymbianLibraryTest and
  GameArtworkTest. Local log: `build/symbian-artwork-retroid.log`.
- Android 15 emulator / base debug: all 11 GameArtworkTest checks passed,
  preserving the Java/Mophun shared artwork regression coverage. Local log:
  `build/symbian-artwork-emulator.log`. This is not native Symbian execution.
- Five new isolated storage/index tests cover all three native platform labels,
  the same UID on another phone, rename/rescan/automatic art replacement,
  independent resets, no-automatic-art fallback and rejected invalid images.
- The installed Snakes-card UI test opens Artwork, verifies Cover actions,
  delivers a picker-result callback after activity recreation, saves both image
  types, resets them through the UI, and verifies cancellation. Existing custom
  images are restored in finally. The result URI is injected for determinism;
  vendor/system image-picker navigation is not asserted by this test.

[Retroid screenshot](../../screenshots/development/2026-10-18-symbian-artwork/README.md).
Build and instrumentation logs remain local under `build/symbian-artwork-*`.

## Limits

This changes library presentation, not emulator compatibility or rendering.
The native-index regression republishes snapshots with test data; it is not a
new full-game/native-install acceptance run. No physical phone acceptance.
At this checkpoint phone backups did not include these host-side artwork
overrides. The [subsequent backup change](2026-10-18-symbian-artwork-backup.md)
adds them, with separate verification. No game or firmware files were added to Git.
