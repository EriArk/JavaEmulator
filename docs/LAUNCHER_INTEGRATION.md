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

Create a custom emulator with the package, full activity, VIEW action and data
URI above. Configure its supported extensions for the files in your collection.
Select the same app variant that exported any `.abyssme` files.

## Evidence and limits

The dated [stage-one checks](checks/2026-10-15-import-launchers.md) distinguish
actual frontend runs, automated checks and remaining cases. Public 0.3.0-beta.1
assets are unchanged by this source work. Do not call an installed debug test
APK a new release.
