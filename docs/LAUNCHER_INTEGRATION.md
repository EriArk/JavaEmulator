# Launcher integration

Use a **custom emulator/player** pointing to the installed AbyssME variant:

| Setting | Handheld | Phone |
| --- | --- | --- |
| Package | `io.github.eriark.abyssme` | `io.github.eriark.abyssme.phone` |
| Activity | `ru.playsoftware.j2meloader.MainActivity` | Same |
| Action | `android.intent.action.VIEW` | Same |
| Data | The selected game's readable `content://` URI | Same |

Enable the launcher's **read URI permission** grant. Do not put the URI in an
arbitrary string extra, shorten the activity to `.MainActivity`, or enable
kill-emulator-before-launch options. Debug/test builds use different package
names; substitute the package of the APK actually installed. The two variants
keep separate libraries and settings.

## Which files to launch

- Java: JAR, JAD, or ZIP. A ZIP containing several games opens game selection;
  it cannot choose your preferred game without that selection. JAD may need its
  matching JAR. Do not advertise arbitrary Java 7z collections as supported.
- Mophun: MPN.
- Symbian/N-Gage: install the phone files and game in AbyssME once, then use
  **Game actions > Export launch file**. Save the `.abyssme` file in the folder
  scanned by your frontend. Launching this file opens the installed game, not
  its installer. This is a new, unreleased source feature after 0.3.0-beta.1.

Launch files contain only the app variant and installed phone/game identity,
not ROMs, game files, credentials or private filesystem paths. They are not
backups. If the phone/game is removed or you switch app variants, export a new
file from the library in that app. An invalid or stale file gives a readable
error rather than starting another game.

## ES-DE

Merge [es_find_rules.xml](es-de/es_find_rules.xml) and
[es_systems.xml](es-de/es_systems.xml) into **`custom_systems` inside the ES-DE
data directory you selected during setup**. It is not necessarily a folder at
the root of internal storage. Back up existing files first; do not overwrite
other emulator entries. A custom system definition can override its upstream
system, so retain any other emulator choices you use. Restart ES-DE afterward.

Select `AbyssME (Standalone)` or `AbyssME Phone (Standalone)` for J2ME or
N-Gage. The N-Gage example scans exported `.abyssme` files, not installers.
The rules use the full Android component, explicit VIEW action and
`%ROMPROVIDER%` to pass the game via a content URI.

Syntax reference: [official ES-DE Android documentation](https://gitlab.com/es-de/emulationstation-de/-/blob/master/ANDROID-DEV.md).
These XML examples are **not ES-DE device certification**. ES-DE was unavailable
for the 2026-10-15 test session; [issue #7](https://github.com/EriArk/JavaEmulator/issues/7)
remains open. Success in another frontend does not prove ES-DE's discovery,
custom-system merging or URI grants work identically.

## Daijisho

Import [AbyssME.json](daijisho/AbyssME.json) through **Settings > Library >
Import platform**, then add the folder containing your games/launch files using
**Paths**, and sync. Choose the Handheld or Phone player as appropriate. Keep
the JSON as UTF-8 without a BOM. Do not replace your existing library/database.
The supplied definition has no online scraper and does not kill app processes.

## Pegasus

Put this `metadata.pegasus.txt` beside the games, then add that directory in
Pegasus settings. Change the package for Phone or a test APK.

```text
collection: AbyssME
extensions: jar, jad, zip, mpn, abyssme
launch: am start --user 0
  -a android.intent.action.VIEW
  -n io.github.eriark.abyssme/ru.playsoftware.j2meloader.MainActivity
  -d "{file.uri}"
  --grant-read-uri-permission
```

Use `{file.uri}`, not `{file.path}`, for the content-URI route tested on Android
14. See [Pegasus Android notes](https://pegasus-frontend.org/docs/user-guide/platform-android/)
and [metadata format](https://pegasus-frontend.org/docs/user-guide/meta-files/).

## Beacon

Verified with **Beacon 1.8.10** on Retroid Pocket Classic (Android 14), using
unreleased Handheld and Phone debug builds. In **Settings > Platforms**, add
a platform with these settings:

1. **Platform type:** Custom. Choose your own name and short name.
2. **Player app:** the installed AbyssME variant you want to use.
3. **ROMs folder:** select your games folder and allow access in Android's folder
   picker. Use the platform's sync button after adding more files.
4. Expand **Advanced**. Leave **File handling** on **Standard** and enable
   **Use custom launch**. Enter this **am start command** for the release
   Handheld package:

```text
am start -n io.github.eriark.abyssme/ru.playsoftware.j2meloader.MainActivity -a android.intent.action.VIEW -d {file_path} --grant-read-uri-permission
```

For the release Phone app, replace the package before `/` with
`io.github.eriark.abyssme.phone`. For debug builds, use their actual package
instead. The **Player app** and command must target the same variant.
Save the platform, select a game and use **Play**.

Beacon's placeholder is **`{file_path}`**, not Pegasus's `{file.uri}`. With
Standard handling, the observed launch carried a readable `content://` URI and
the read-permission flag. No separate extension field was required in this
custom-platform setup. Use the same variant that exported `.abyssme` files.

The [Beacon checks](checks/2026-10-15-beacon-launcher.md) cover real frontend
launches, first import, reuse, archive selection and returning to Beacon.
The cancel/error return fix and native launch-file support are **not in the
published 0.3.0-beta.1 APKs**. The command above substitutes release package
names for convenience; the acceptance run used debug packages, not signed
release APKs.

## Evidence and limits

The dated [stage-one checks](checks/2026-10-15-import-launchers.md) distinguish
actual frontend runs, automated checks and remaining cases. Public 0.3.0-beta.1
assets are unchanged by this source work. Do not call an installed debug test
APK a new release.
