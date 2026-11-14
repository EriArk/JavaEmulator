# Independent graphics/control profiles — November 14, 2026

## Availability and build

Unreleased source and an isolated combined ARM64 Debug build. No new release tag,
public APK or version bump. Published Beta 6 retains combined touch/LCD profiles.
The test application ID is `io.github.eriark.abyssme.profilescheck.debug`; its
version label remains 0.7.0-beta.6/code 320 and must not be mistaken for the signed
Beta 6 release APK. Local Linux builds use the previously authorized cached
Podman builder; GitHub Actions remains disabled.

Final test APK SHA-256: `9486b42216281dd4ede4439b4c835e51fd48b541348cc28bd4188b1ca25863a9`.

## Checks

- Combined Debug APK and instrumentation APK build successfully.
- 419 app JVM tests pass, freshly executed on the implementation build.
- Final Debug lint: zero errors, 808 warnings.
- **116 targeted Android tests pass on Retroid, Android 14**: 8 graphics profiles,
  11 controls profiles, 2 profile UI, 8 serialization, 5 nested picture previews,
  22 trial handoffs, 27 library imports/backups, 6 Symbian settings, 5 Symbian UI
  and 16 phone-backup checks.
- Graphics tests cover independent global defaults and assignments, linked edits,
  all supported serialized values, Beta 6 migration (including controls-first
  catalog writes), existing overrides, collisions on restore, corrupt catalogs,
  and graphics-only Keep/Undo while a control trial is pending.
- The graphics UI test selects xBRZ through the actual preset selector and saves
  its shared definition while retaining an independent control assignment.
- A permitted local Diamond Rush fixture reaches its opening level/intro using
  a named xBRZ profile and integer scaling; the stored graphics assignment has
  no per-game graphics overrides and the control assignment remains independent.
  The bounded instrumentation smoke passes. The earlier 240×320 fixture showed
  the game's orientation warning; the verified run uses the previously established
  320×240 fixture. This is not resolution auto-detection or complete-game acceptance.

An intermediate Symbian change caused two existing paused-LCD preview tests to
fail. Full native graphics selection now goes through the host's Advanced / Try
flow, preserving the existing LCD-only preview and its no-guest-call invariant.
All 116 checks pass after that correction. Native integer scaling uses Fit.

## Evidence and reproduction

- [Graphics implementation tests](../../app/src/androidTest/java/ru/playsoftware/j2meloader/config/GraphicsProfilesTest.java)
- [Actual profile UI tests](../../app/src/androidTest/java/ru/playsoftware/j2meloader/config/SharedProfileUiTest.java)
- [Library round trip](../../app/src/androidTest/java/ru/playsoftware/j2meloader/catalog/LibraryImporterTest.java)
- [Optional permitted-game smoke](../../app/src/androidTest/java/ru/playsoftware/j2meloader/config/ProvidedJavaSweepTest.java),
  with `javaGraphicsProfile=3`, `javaGame=Diamondrush.jar`, `javaWidth=320`,
  `javaHeight=240`. The game itself is not distributed.
- [Retroid graphics-editor capture](../../screenshots/development/2026-11-14-graphics-profiles/graphics-editor.png).

Maintainer-local logs, commands and game captures are under the ignored
`build/checks/2026-11-14-graphics-profiles/`, including `build.log`,
`final-build.log`, `final-android-tests.log`, `game-verified.log` and
`game-profile.json`. Temporary game/profile fixtures and isolated test packages
were removed after verification. The official release package was not replaced.

## Limits

No fresh ARMv7 build, release optimization/signing, signed upgrade, public
release download, audible-audio or physical-button check is claimed. Test inputs
were injected through instrumentation/ADB. Native phone tests use bounded host/
archive fixtures; no new real Symbian game or firmware acceptance is claimed.
The game smoke verifies xBRZ application; it does not establish visual/performance
quality for every shader and game. Live GitHub release state was not rechecked
for this implementation-only change. See [engine support](../SHARED_PROFILES.md).
