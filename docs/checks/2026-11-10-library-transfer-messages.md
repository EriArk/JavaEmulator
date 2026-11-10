# Library Transfer Recovery Messages — 2026-11-10

Local-only continuation of #40/#43 after the multi-phone queue checkpoint.
No source publication, release or physical-device acceptance is claimed.

Java/Mophun library scans, import previews, shared-file copying and backup
export/restore now separate actionable user messages from technical diagnostics.
Thirty message resources are provided in all eight supported languages. Known
nested failures render in the current language; original exception messages,
unknown provider errors, file names and stored identities remain unchanged.
Folder-access recovery retains the full localized message across recreation.
Cancellation and successful settings/shared-file summaries use the same resources.
Storage validation, transactions and archive formats are unchanged.

## Verification

The maintainer explicitly authorized local builds for this session. The isolated
Linux builder and API 30 emulator are the same as in the
[queue checkpoint](2026-11-10-phone-transfer-queue.md).

- Combined Debug and instrumentation APKs assemble successfully.
- 424 JVM tests pass with no failures/errors/skips after the behavioral changes.
- 38 targeted Android cases pass: `LibraryImporterTest`,
  `LibraryTransferLocalizationTest`, and `CompactSettingsTest`. The initial run
  passed 37/38; one UI assertion still expected an English error prefix. After
  changing it to the complete localized recovery message, six affected cases
  passed on rerun (that UI scenario, the archive round-trip and four localization
  cases). No product failure was concealed by weakening storage assertions.
- Synthetic archive tests preserve progress, controls, library metadata and
  existing shared files; invalid sources remain unchanged.
- Locale resource keys/placeholders match across eight languages.
- Final lint: zero errors, 804 warnings, unchanged from the queue checkpoint.

Build commands use `-PsymbianRelease=true -PsymbianSourceDir=/sources/symbian`:
`:app:assembleFdroidDebug :app:assembleFdroidDebugAndroidTest`,
`:app:testFdroidDebugUnitTest --rerun`, and `:app:lintFdroidDebug`.
The unaffected dexlib result is retained from the preceding checkpoint.

Artifacts, hashes and logs are in ignored
`build/checks/2026-11-10-library-transfer/BUILD-INFO.json` and its directory.
These binaries use the local debug key and are not release assets.

## Remaining acceptance

Native-speaker review, physical controller/touch and representative user-library
acceptance remain open. Raw OS/provider or unknown persisted diagnostics may be
shown verbatim. No real games were used. The connected handheld still awaits ADB
authorization; a valid official Discord destination remains pending separately.
