# Linked profiles and extra Gamepad buttons — November 14, 2026

## Availability

Unreleased public source for #53 and #54, with a locally built combined ARM64
Debug package `io.github.eriark.abyssme.profilescheck.debug`. The temporary
application ID was applied only in the isolated build workspace. The official
Retroid installation was not replaced. The build retains base version 319;
it is not a new Beta 5 release asset. [User-facing scope](../SHARED_PROFILES.md).

## Verified behavior

- Global default → named profile → explicit game overrides; changing a linked
  definition affects reloaded games, and clearing an override uses the assigned
  base. Existing configs migrate to explicit overrides without changing values.
- Detached drafts, recreation, trial handoff, Keep/Undo and nested display trials
  preserve override metadata and do not mutate shared definitions accidentally.
- Profile ID/name collisions restore separate definitions; repeated imports reuse
  the matching imported identity. Equal-valued profiles with different identities
  remain separate. Explicit null members survive all serialization paths.
- Java/Mophun archive round-trip preserves game progress, controls, metadata,
  extra buttons, linked profiles and unassigned definitions. The phone backup
  callback includes portable profile metadata without modifying source files;
  existing native phone archive and settings checks also pass.
- Two added keys and a D-pad work simultaneously. Shared target keys release once
  after the last finger, layout/mode changes release held keys, orientation
  placements stay independent, and invalid extra-button definitions are ignored.
- Actual editor windows add two buttons, restore a draft, confirm, and cancel a
  deletion. The profile UI selects a named base and assigns it to two selected
  fixture games. Retroid screenshots cover the manager, editor and extra buttons.

## Checks and artifact boundary

- **424 JVM tests pass**, no failures/errors/skips: app and dexlib Debug suites.
- **86 targeted Android tests pass** on Retroid Pocket Classic, Android 14.
  Classes: SharedProfilesTest, SharedProfileUiTest, ExtraTouchButtonTest,
  ProfileSerializationTest, DisplayPreviewTest, TouchMappingTest,
  LibraryImporterTest, PhoneBackupTest and SymbianGameSettingsTest.
- **Debug lint: zero errors, 807 warnings.** The new indentation error found on
  the first lint pass was corrected; existing warnings remain.
- Full combined Debug/test APKs build with `-PsymbianRelease=true`, the restored
  pinned source bundle and the established `-PnativeBuildJobs=2` configuration.
  The final package contains `lib/arm64-v8a/libabysssymbian.so`.

The initial global-default/restore checks exposed nullable-object handling and
loss of explicit null members in the Gson paths; the dedicated adapter fixes
both, and regression tests pass. A UI-test assertion incorrectly treated a
Switch's `performClick()` return as its checked state and was corrected. An
intermediate Java-only incremental APK omitted the Symbian native library,
causing two native-dependent tests to fail; the complete build and all 86 checks
were rerun successfully. Initial failures remain in local logs.

A final label-only polish changes the extra-button key picker to readable key
names, followed by a focused editor capture check. It does not change bindings.
No optimized release build, new signed official-app upgrade, exclusively ARMv7
hardware run, real-game completion, human touch-comfort study or fresh audible
sound acceptance is claimed. This task did not recheck the live release assets
or multiplayer services.

Local commands, build/lint reports, instrumentation logs, APK hashes and captures
are retained under `build/checks/2026-11-14-shared-profiles/`. These private test
APKs are not GitHub release assets. Public UI captures use synthetic test settings
and contain no game payloads, account data or signing material.

[Actual device UI captures](../../screenshots/development/2026-11-14-profiles/README.md).
