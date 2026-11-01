# EKA2L1 Phone Transfer - 2026-11-01

## Availability and Scope

Unreleased source and a separate Retroid Debug installation. No release version,
public APK, firmware or game files are published by this change. Addresses the
shared transfer problem in issues #39 and #49; neither issue is closed solely on
the basis of this fixture. Android 14 Retroid Pocket Classic is the physical
device used here; this is not a second-device or full-game acceptance claim.

Library transfer now offers **Import from EKA2L1**: folder/ZIP selection, registered
phone selection, size and safety preview, then explicit copy. The same entry is
available in Phone backups. Completion opens the shared game library, including
when transfer was started from Settings rather than from the library itself.
The same completion-route correction applies to native phone-backup restore.

## Shared Mechanism

- Parse registrations using the already-linked yaml-cpp parser. Validate firmware
  identities, duplicate case variants, scalar field bounds and machine UIDs;
  skip phones pending deletion. No new parser dependency or upstream patch.
- Keep the selected ROM/Z, writable drives, installed registries and guest Java
  data together. Convert legacy shared drive paths to a private per-phone layout;
  do not merge into an existing phone. Preserve guest filename case.
- Retain portable identity/settings when the source includes `config.yml`.
  Exclude host paths, scripts and network settings before native validation.
- Stage a read-only snapshot; reject inaccessible nested folders, missing
  registration/ROM, links, path traversal, file/directory and case conflicts,
  changing file sizes, bad CRCs and configured size/count/depth limits.
- Hash destination files before native validation, then atomically add the
  validated phone. A stable source fingerprint makes the same snapshot reuse its
  existing imported copy without overwriting newer saves. A changed snapshot is
  a separate phone. Cancellation/validation failure before commit removes staging.
- Remove native backup export's unconditional deletion of the destination after
  an error. Final document-provider writes still cannot be universally atomic.
- Add all new UI strings in English, Spanish, Brazilian Portuguese, Russian,
  French, German, Japanese and Simplified Chinese.

The path contract was checked against the pinned EKA2L1 device reader and
`device_drive_folder` / `device_isolated_drives_folder` in `system/src/devices.cpp`,
and the existing host's portable settings and phone-backup implementation.

## Verification

- Combined `assembleFdroidDebug` and `assembleFdroidDebugAndroidTest` succeed.
  Final build log: local `build/eka-transfer-final-build.log`.
- 42 focused Android checks pass on Retroid in 30.068 seconds: 19 new transfer
  cases, existing phone backup regressions and transfer localization checks.
  Local log: `build/eka-transfer-tests.log`.
- New cases cover shared and isolated drives, separate-phone selection, guest
  registries/saves/identity, repeat-import save preservation, validation rollback,
  cancellation, data-only folders, ZIP/wrapped folders, native registration
  parsing, links, case/path collisions and SAF provider failure boundaries.
- Opt-in `Eka2l1DeviceTest`: pass in 12.869 seconds. Construct an EKA2L1-layout
  snapshot from the privately installed Snakes phone, copy it through the new
  importer, prepare the copied phone with the real native engine and index Snakes
  by its original UID. Compare all original runtime file hashes unchanged.
  This is a constructed compatible snapshot, not an independently supplied
  collection from the issue reporter. Local log: `build/eka-transfer-device-tests.log`.
- Opt-in `Eka2l1UiTest` covers Library transfer entry, Android document picker,
  phone preview, cancellation with unchanged phone count, confirmed copy, new
  Snakes catalog entry and return to the shared library. It removes only its own
  new phone/index/document and restores the previously active phone selection.
  Local log: `build/eka-transfer-ui-tests.log`.
- Final installed APK rerun: **43 tests pass in 36.86 seconds**, combining the
  storage/localization suites and native installed-phone test. The separate final
  UI scenario passes in **42.152 seconds**: **44 passing checks overall**.
  Aggregate local log: `build/eka-transfer-final-tests.log`.
- `LocalizationAudit --check`: 1,530 canonical resources, zero missing advertised
  translations, zero structure errors. This checks presence/formats, not native
  language approval. Local log: `build/eka-transfer-localization.log`.

Final installed Debug APK SHA-256:
`12c61d3cd6f0112453c261bc569826b18e3385be655bcb8478634f4376657566`.
Package `io.github.eriark.abyssme.debug`; the official release package is untouched.
Dark-theme source, phone-selection, preview, completion and library screenshots
are retained locally under `build/eka-transfer-evidence/` after visual inspection.

## Limits

One selected phone per import, not an automatic all-phone migration. Standalone
Drive E is insufficient. External mounts are not followed; include their contents
in the selected drives tree. Android private-storage restrictions still apply.
Close EKA2L1 before taking a snapshot: this is not a live transactional backup of
a running foreign emulator. Imported guest Mophun launchers remain Symbian apps;
no native Mophun conversion is claimed. Phone validation can initialize service
files in the new copy, never in the source. No game-specific compatibility, save
progression or complete-playthrough acceptance is added. Asphalt/N95 remains
outside this batch.
