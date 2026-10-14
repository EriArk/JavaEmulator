# Contributing to AbyssME

Bug reports, compatibility observations, documentation and focused patches are
welcome. Search [existing issues](https://github.com/EriArk/JavaEmulator/issues)
before starting. Discuss changes to storage, input behavior, UI direction or
release policy before implementing them.

## Status and Change Records

Keep [PROJECT_STATUS](docs/PROJECT_STATUS.md) current after meaningful changes,
test-build distributions and acceptance checks. Append user-visible changes to
[CHANGELOG](CHANGELOG.md), with dates, availability and evidence links. These
documents are also the handoff source for the website and progress posts.
Summarize local-only work without publishing ignored implementation or game files.

Separate released APKs, unreleased `main`, private test snapshots and local
experiments. A successful build or emulator run is not physical-device acceptance.
For substantial commits, add a body describing the changes, verification and
remaining limitations. Do not rewrite published history just to expand messages.

## Local setup

- JDK 21 (used by the current local verification environment).
- Android SDK Platform 34 and Build Tools 34.0.0.
- Android NDK 22.1.7171670 and platform-tools for device/emulator checks.
- Git is useful for development; source ZIPs also build without Git history.
- Use the included Gradle 8.7 wrapper; do not install a separate Gradle version.

Clone the repository and open it in Android Studio, or set `ANDROID_HOME` to
your SDK directory. Android Studio can also create an ignored `local.properties`
with `sdk.dir`. Dependency downloads require Google Maven, Maven Central and
JitPack access.

### Signing

Debug builds use the standard Android debug key automatically. No
`keystore.properties`, custom key generation or maintainer credentials are needed.
The former debug-build prerequisite tracked in
[issue #1](https://github.com/EriArk/JavaEmulator/issues/1) has been removed.

For a signed release, copy `keystore.properties.example` to `keystore.properties`:

```shell
cp keystore.properties.example keystore.properties
```

In PowerShell use `Copy-Item keystore.properties.example keystore.properties`.
Fill all four fields with your own release-key details. Do not overwrite an
existing signing configuration or keystore. Use forward slashes in Windows paths.
Both keystores and `keystore.properties` are ignored by Git. Release signing
validation fails explicitly if the settings are incomplete; it does not fall
back to the debug key. Existing Bitrise signing environment variables remain
supported. Publication also requires the source/notice and artifact checks.

### Build and checks

```shell
./gradlew :app:assembleFdroidDebug :app:assemblePhoneDebug
./gradlew :app:testFdroidDebugUnitTest :app:testPhoneDebugUnitTest
./gradlew :app:lintFdroidDebug :app:lintPhoneDebug
```

On Windows use `.\gradlew.bat` instead of `./gradlew`. APKs are written under
`app/build/outputs/apk/`. Debug builds have a `.debug` package suffix and do not
replace the official release apps. If reporting a fresh unit-test run, use
`--rerun` after each unit-test task so an UP-TO-DATE result is not mistaken for
new execution.

GitHub Actions is intentionally disabled. Checks and builds run locally;
maintainers publish signed release assets manually. Do not add workflows or
automatic publishing as part of an unrelated contribution.
New APKs belong only in GitHub Releases, not in the Git tree. Historical tracked
APKs remain an archive. See the [manual release checklist](docs/RELEASING.md).

## Manual verification

For the combined Java/Mophun/Symbian build, first restore the pinned engine
source bundle and follow [Symbian build prerequisites](engines/symbian/DISTRIBUTION.md).
Pass `-PsymbianRelease=true` to the commands above. This enables the embedded
engine for both Debug and Release with normal package IDs (Debug still has its
`.debug` suffix), and restricts the combined APKs to arm64-v8a. The property-free
build remains a Java/Mophun-only development option. Phone dumps and commercial
games are never required to compile the application.

For changes that affect both variants, check Handheld and Phone separately:

- Import a JAR and an archive, then relaunch an already prepared game.
- Check Gallery, List, Grid, focus navigation and missing-artwork fallbacks.
- Verify quick settings, exit behavior and per-game configuration persistence.
- Check physical mappings and short/held Select for Handheld changes.
- Check the virtual keypad, portrait layout and rotation for Phone changes.
- For launcher changes, test cold/warm content-URI launches and return behavior
  in the actual frontend; a valid manifest alone is not sufficient.
- For storage changes, test upgrades with existing library entries and saves.

Record the commit, APK variant, commands/results, game identification and
Android/device or emulator version. Mark untested cases explicitly. Use games
you are permitted to use, and never attach commercial JARs to reports.

Current local shared-source coverage includes 14 JVM tests and 101 focused Android
instrumentation cases. Variant/viewport-specific assumptions skip inapplicable
checks; run the Phone search case in portrait and controller focus on Handheld.
See [commands, scope and recorded checks](docs/LOCAL_TESTING.md).
Passing these is not physical-controller or full-game compatibility acceptance;
remaining coverage is tracked in [#4](https://github.com/EriArk/JavaEmulator/issues/4).

## Project layout

- `app/src/main/java/ru/playsoftware/j2meloader`: library, profiles and Android UI.
- `app/src/main/java/javax/microedition`: emulation APIs and MIDlet runtime.
- `app/src/main/java/ru/woesss/j2me/installer`: import and conversion.
- `app/src/phone`: Phone manifest and resource overrides.
- `app/src/main/cpp` and `dexlib`: native components and bytecode tooling.
- `docs` and `screenshots/website-beta2`: integration notes and authentic captures.
- `test-builds`: historical binaries and test reports, not a build prerequisite.

The installed package IDs are AbyssME-specific; the Java namespace remains
`ru.playsoftware.j2meloader`. Preserve upstream copyright and license notices.
Read [licensing](docs/LICENSING.md) before adding engines or changing headers.
The combined application is GPLv3; inherited component terms remain intact.
`engines/m3g` contains the replacement renderer and tone sources, and
`engines/mophun` contains the selectively promoted Wolphun integration.
The incompatible legacy M3G/tone implementation was removed, not relicensed.
Private games, diagnostics and signing material must stay outside tracked files.
Use `:app:verifyProductionApks` before publication to reject stale legacy classes,
research hooks and game payloads in the signed APKs.

## Pull requests

Keep changes scoped and explain the user-visible problem, approach, checks run
and remaining gaps. Include before/after screenshots for UI changes. Do not
commit keys, local settings, caches, personal device backups or newly built
APKs. Discuss fixture licensing and binary publication in the relevant issues
before adding assets. Documentation-only fixes do not require an APK rebuild.
