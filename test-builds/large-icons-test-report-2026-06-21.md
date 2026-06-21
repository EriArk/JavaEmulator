# Large Icons Test Report - 2026-06-21

Device: Odin 2 via ADB (`192.168.50.34:32967`)

APK: `test-builds/J2ME_Loader-large-icons-fdroid-debug.apk`

## Build

- `./gradlew.bat :app:assembleFdroidDebug --console=plain`
- Result: success

## What Changed

- Launcher icons are rendered through a large-icon helper.
- Transparent padding inside extracted PNG icons is cropped at display time.
- Icon content is scaled up into a square bitmap with filtering disabled.
- List, Grid, Gallery fallback, Detail fallback, and installer modal use the larger icon rendering.
- Existing installed games do not need reinstalling for the bigger icon display.

## Device Checks

- Installed with `adb install -r -d`.
- List mode shows much larger icons.
- Grid mode shows much larger icons.
- Detail fallback icon is larger.
- Installer modal icon is larger.
- Logcat check found no `FATAL`, `AndroidRuntime` crash, or `OutOfMemory` entries during the test pass.

## Screenshots

- `large-icons-screenshots/01-list-large-icons.png`
- `large-icons-screenshots/02-grid-large-icons.png`
- `large-icons-screenshots/03-installer-large-icon.png`
