# Contributing to AbyssME

Bug reports, compatibility observations, documentation and focused patches are
welcome. Search [existing issues](https://github.com/EriArk/JavaEmulator/issues)
before starting. Discuss changes to storage, input behavior, UI direction or
release policy before implementing them.

## Local setup

- JDK 21 (used by the current local verification environment).
- Android SDK Platform 34 and Build Tools 34.0.0.
- Android NDK 22.1.7171670 and platform-tools for device/emulator checks.
- Git on PATH; the Gradle configuration reads Git history for the dev flavor.
- Use the included Gradle 8.7 wrapper; do not install a separate Gradle version.

Clone the repository and open it in Android Studio, or set `ANDROID_HOME` to
your SDK directory. Android Studio can also create an ignored `local.properties`
with `sdk.dir`. Dependency downloads require Google Maven, Maven Central and
JitPack access.

### Temporary signing workaround

[Issue #1](https://github.com/EriArk/JavaEmulator/issues/1) tracks a build-system
bug: even debug tasks currently load `keystore.properties`. Until it is fixed,
create a disposable local key from the repository root:

```shell
keytool -genkeypair -keystore contributor-debug.jks -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 3650 -dname "CN=AbyssME Local Development"
```

Copy `keystore.properties.example` to `keystore.properties`:

```shell
cp keystore.properties.example keystore.properties
```

In PowerShell use `Copy-Item keystore.properties.example keystore.properties`.
Do not overwrite an existing signing configuration or keystore. Both the key
and `keystore.properties` are ignored by Git. These public example credentials
are only for local development, never for distributable releases. You do not
need the maintainer's private key.

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

Current JVM coverage is limited to two source-identity tests. Passing them is
not evidence that controller input, migrations or game compatibility work;
broader coverage is tracked in [#4](https://github.com/EriArk/JavaEmulator/issues/4).

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
Read [licensing status](docs/LICENSING.md) before adding an emulation engine or
changing license headers. No project-wide license transition has taken effect.
Wolphun integration experiments must remain in ignored local
files and opt-in debug builds until the compatibility blocker is resolved.
New release APKs are on hold; source-only updates do not authorize a release.

## Pull requests

Keep changes scoped and explain the user-visible problem, approach, checks run
and remaining gaps. Include before/after screenshots for UI changes. Do not
commit keys, local settings, caches, personal device backups or newly built
APKs. Discuss fixture licensing and binary publication in the relevant issues
before adding assets. Documentation-only fixes do not require an APK rebuild.
