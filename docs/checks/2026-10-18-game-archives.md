# Java / Mophun archive import

Date: 2026-10-18. Development source after beta.6, not a new release.
Local debug version remains 0.3.0-beta.6 / code 305. Signed public APKs are
unchanged. No games, firmware or private fixture bytes are published.

## Changes

- Extend the existing ZIP/7z chooser from JAR to JAR/MPN. A single game skips
  the chooser; multiple games use the current controller-focusable dialog.
  Installation, optional external-launch auto-start and normal Start work
  through the same library and engine dispatch.
- Extract only the selected payload to a fixed cache filename, never to a
  path supplied by the archive. Solid 7z can still require decoding preceding
  compressed data; this is not random-access decompression of every format.
- Retain the selected MPN leaf name for title/device hints and the original
  archive URI/entry for provenance. Validate/hash before publishing aliases.
  Reuse identical installed MPNs without replacing their files, settings or saves.
- Promote a single-game archive's indexed placeholder. A multi-game archive's
  existing chooser card remains available; entry aliases stay independent.
- Route Java/Mophun 7z to the common installer in the combined build. Keep native
  packages/system archives on their own paths and reject ambiguous mixtures of
  native games, Java/Mophun and system files, as ZIP routing already does.
- Limit game archives to 512 MiB compressed, 1 GiB declared expanded data,
  10,000 entries and 1,024 eligible games. Selected MPN/JAR limits are 16/64 MiB;
  streaming byte counts enforce output limits as well. 7z decoder memory is
  capped at 128 MiB. Reject unsafe paths and case-insensitive duplicate names.
- Content-URI staging in the common installer now has a 512 MiB copy limit and
  interruption checks; failed copying removes incomplete staged output.
  Extraction failure removes partial payloads. Original input is not modified.

## Verification

- Java/Mophun Phone debug and instrumentation APKs built.
- Combined Handheld/Phone debug and Phone instrumentation built with
  `-PsymbianExperiment=true -PsymbianSourceDir=build/symbian-source-check`.
- Retroid Pocket Classic, Android 14, combined Phone debug: **25 passed**:
  InstallerArchiveTest (2), InstallerSourceHintsTest (3), InstallerJadTest (4),
  InstallerSourceTest (4), MophunCatalogTest (4), UnifiedImportTest (8).
- Further Retroid regressions: **31 passed**: GameFolderIndexerTest (9),
  LibraryImporterTest (15), InstallerSourceAliasTest (7).
- Android 15 x86_64 Phone emulator: **17 passed**, the same import suites without
  the combined-build UnifiedImportTest. No skipped cases in these device runs.
- Archive UI tests go through AdditionalGames.install, including the actual
  combined router. ZIP and 7z tests select both MPN and JAR, import the same MPN
  again, compare saved/profile marker bytes, and ensure invalid MPNs add no card.
- Unit GameArchiveTest covers both formats, selected extraction, case/path
  rejection, missing entries, corrupt/truncated input, interruption and size
  bounds. The combined Phone JVM suite passed **38 tests**.
- Final Java/Mophun JVM runs passed **38 tests per variant**. Handheld/Phone lint
  passed with zero errors; 396/403 warnings remain. This lint result is for
  the Java/Mophun configuration, not a new combined-build lint certification.

Retroid UI tests used the private MPN fixture supplied by instrumentation
argument `archiveMpn`; the emulator used a generated minimal VMGP header for
installation checks, not gameplay. Public tests do not bundle commercial games.
The private fixture SHA-256 is
`a4a7c9e78f8762432907d8bbcfed71cab50877de29c3a4cc448471d6b90ecc0f`.
Its local filename is DeepAbyss.mpn, but the rendered menu is a bubble-themed
version 1.1; this is **not** Deep Abyss 1.4 compatibility acceptance.

A separate Retroid VIEW-intent smoke check opened a real mixed ZIP containing
that MPN and the MIT Sudoku fixture, displayed the chooser and auto-started the
selected MPN to a rendered menu. The shared Game menu and Exit game worked.
Local captures: `build/archive-smoke/retroid-chooser.png`,
`retroid-install.png` (game menu after automatic start) and `retroid-menu.png`.
Tests used an isolated debug package; the official Handheld beta.6 was not replaced.

Local logs: `build/archive-base-final.log`, `archive-combined.log`,
`archive-retroid-tests.log`, `archive-retroid-regression.log`,
`archive-emulator-final.log`, `archive-final-checks.log`.
The first emulator attempt exposed a case-sensitive expected-label mistake in
the new test (Install versus the existing install resource); the test now reads
the resource. The final full 17-case rerun passed.

## Limits

This does not add recursive archive-within-archive import, Mophun RAR import,
batch install, password handling or JAD/KJX extraction from archives.
Loose JAD/KJX installation is unchanged. A JAD inside a ZIP is not automatically
paired with its JAR. Native and system importers retain their separate limits.
This is import/catalog/launch evidence, not full gameplay, sound quality,
physical-controller acceptance or broad Mophun compatibility.
