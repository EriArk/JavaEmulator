# N-Gage 2.0 Installer Preflight (#47)

2026-10-28. Shared modern SIS/SISX dependency validation repair, prepared for
0.7.0 Beta 1. No game-specific UID or title exceptions and no native installer
changes. See the release verification record for signed-build availability.

## Reproduction and Repair

The privately supplied N-Gage 2.0 1.40.1557 installer reproduces the reported
`Duplicate embedded component UID` in the old production preflight:

| Controller | UID | Version | Install type |
| --- | --- | --- | --- |
| N-Gage (root) | 20003B78 | 1.40.1557 | 0, full installation |
| Arena_repository (child) | 10202BE9 | 1.1.0 | 2, partial update |
| N-Gage Repository (child) | 10202BE9 | 1.1.3200 | 2, partial update |

These are two distinct updates, not two competing full installations. The
[upstream package manager](https://github.com/EKA2L1/EKA2L1/blob/master/src/emu/package/src/manager.cpp)
and the pinned local registry types distinguish partial updates from full
installations. AbyssME now preserves all such controllers and their dependency
constraints instead of rejecting every repeated UID or overwriting a map entry.
The original SIS is passed unchanged to the native installer, in its own order.

- Allow distinct partial updates sharing a UID, with or without one full base.
- Reject repeated full installations, identical controller bodies and repeated
  combinations involving unsupported augmentation/preinstalled types.
- Do not treat a partial update's version as a complete dependency provider.
- Intersect every embedded dependency range, including nested requirements;
  retain cycle, malformed metadata, size/depth and downgrade guards.
- Compare bounded SHA-256 controller fingerprints, without unpacking payloads.
- Name the offending installer, UID and conflicting versions/types in a localized
  error. Do not tell users to erase a virtual phone or replace their firmware.
  Older persisted error identities remain readable.

Augmentation/preinstalled repeats are conservatively unsupported, not declared
universally invalid. Conditional installers remain the guest's responsibility.
This is not a complete implementation of every Symbian package arrangement.

## Verification

- Host probe using the real production parser/planner reproduces the old error
  and accepts the same file after the repair. Logs: `build/issue47/baseline.log`
  and `build/issue47/fixed-preflight.log`. Its empty backend is not native proof.
- Correct the old real-device test to provide a nonnull `ComponentStore`, just
  like production. The earlier four-argument flow bypassed this preflight.
- Retroid Pocket Classic / Android 14: a fresh isolated N95 archive starts with
  `hasPlatform=false`; preflight runs once, native installation succeeds once,
  and N-Gage 2.0 registers. A resumed import does not restage a consumed file;
  a new import reuses the installed platform without another preflight/install.
- Separate real-path run: **1 pass**, 10.164 seconds. Full focused run:
  **45 passes, no skips**, 36.259 seconds. Classes: `SisComponentsTest`,
  `GameInstallFlowTest`, `SystemTransferLocalizationTest`,
  `NativeImportLocalizationTest`. Both opt-in real-fixture arguments are enabled.
- Synthetic coverage includes sibling/nested repeats, partial-update dependencies,
  incompatible version ranges, ordinary full dependencies, retry/recreation and
  conflicting platform rejection before any guest installation. The rejected
  platform retains the pending game's hash, payload and import state.
- R8-optimized, debug-signed isolated rehearsal: **28 installer tests pass**,
  10.912 seconds, including the same fresh N95/native platform installation and
  reuse with production preflight enabled. Log: `build/issue47/optimized-installers.log`.
  This uses existing test-only API retention rules, not the production signing key.
- Fresh JVM suite: **419 passes**, no failures/errors/skips. Debug assembly and
  lint succeed: **zero errors, 802 warnings**, not warning-free.
- All **1518 canonical resources** exist in each advertised translated locale.
  Audit/self-tests pass with zero format/structure errors or missing entries.
  English remains canonical; translation completeness is not native-speaker review.

Ignored logs: `build/issue47/gradle.log`, `retroid-native.log`,
`retroid-focused.log`, `localization-audit.log`. Private N95, platform SIS and
Asphalt files are test inputs only and are not committed or distributed.
Tests use isolated Debug cache/catalog directories; no official virtual phone,
game library or save is wiped. This is installer/registration evidence, not
Asphalt gameplay or complete-game compatibility.

The reporter's exact file/build remains unknown. This reproduces the same
failure class with an existing real installer, but does not establish that
every reported installation failure is resolved. Keep #47 open for release
and matching-case feedback; do not ask the reporter to erase existing data.
