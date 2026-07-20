# AbyssME

**A modern J2ME emulator for Android handhelds and phones.**

AbyssME is an independent fork of
[J2ME Loader](https://github.com/nikita36078/J2ME-Loader), focused on making
old mobile Java games feel at home on modern landscape handhelds. It keeps the
proven emulation core while replacing the setup-heavy workflow with a game
library, automatic first-launch preparation, and fast per-game controller
mapping.

Every release provides two installable variants built from the same emulation
core: a controller-first **Handheld** APK and a portrait/touch-friendly
**Phone** APK with an on-screen keypad.

> **Project status:** Beta. AbyssME is usable, but compatibility automation and
> launcher integration still need testing across more games and devices.

[Download the latest beta](https://github.com/EriArk/JavaEmulator/releases)

## Highlights

- Controller-first landscape interface with Gallery, List, and Grid views
- Separate Phone build with portrait mode, direct touch input, and virtual keys
- Recent games, favorites, folders, search, sorting, and local artwork
- Automatic compatibility preparation the first time a game is launched
- Per-game named control profiles and a quick mapping overlay
- Live in-game controls for display preset and virtual screen size
- Direct launching from ES-DE, Beacon, shortcuts, and other Android frontends
- JAR, JAD, ZIP, and 7Z sources through Android's Storage Access Framework
- Local-only library and diagnostics; no account, cloud service, or telemetry

## Requirements

- Android 10 or newer (API 29+)
- A physical controller is recommended for the Handheld build

The Handheld build is intentionally landscape-only and has no on-screen keypad.
The Phone build supports automatic rotation, a compact portrait library, direct
MIDP touch events, and configurable virtual controls.

## Install

1. Open [Releases](https://github.com/EriArk/JavaEmulator/releases).
2. Choose an APK:
   - `AbyssME-...-fdroid-release.apk` for handhelds and physical controllers.
   - `AbyssME-...-phone-release.apk` for phones, portrait mode, and touch keys.
3. Allow installation from your browser or file manager when Android asks.
4. Install the APK and open AbyssME.

The package IDs are `io.github.eriark.abyssme` and
`io.github.eriark.abyssme.phone`, so both variants can be installed together.
Older J2ME Loader builds also use a different package.

## Quick Start

1. Choose **Add game** for one file, or **Folders** to index a collection.
2. Select a JAR, JAD, ZIP, or 7Z file using Android's file picker.
3. Start the game. On first launch, AbyssME tests likely display and
   compatibility settings, then saves the result for that game.
4. On Handheld, tap the game display for quick **View**, **Screen**, and
   **Controls** actions, or hold **Select** for per-game mapping.
5. On Phone, use the on-screen keypad. Its visibility, layout, colors, haptics,
   and direct game touch input can be changed in the game's settings.

In the library, **Y** toggles the selected game as a favorite and **L1/R1**
cycles Gallery, List, and Grid views.

## External Launchers

AbyssME accepts read-only `content://` game URIs through Android `ACTION_VIEW`.
The first request indexes and prepares the game; later requests launch it
directly. Exiting returns to the calling frontend.

- Handheld package: `io.github.eriark.abyssme`
- Phone package: `io.github.eriark.abyssme.phone`
- Activity: `.MainActivity`
- Action: `android.intent.action.VIEW`
- Supported files: JAR, JAD, ZIP, 7Z

Ready-to-copy ES-DE rules and Beacon setup details are in
[Launcher integration](docs/LAUNCHER_INTEGRATION.md).

## Compatibility and Fixes

Automatic preparation is the default. If a title still has problems, open its
settings and use **Fix game** to describe the symptom: wrong size/cropping,
black screen/flicker, crash, or incorrect controls. The full expert settings
remain available per game without cluttering the normal flow.

Game behavior varies between phone releases. When possible, start with the
original build made for a common 176x220, 240x320, or 320x240 device profile.

## Build

The project uses the Gradle wrapper and requires a local Android SDK and NDK.

```shell
./gradlew :app:testFdroidDebugUnitTest :app:lintFdroidDebug \
  :app:assembleFdroidRelease :app:assemblePhoneRelease
```

On Windows, use `gradlew.bat`. APK output is written under
`app/build/outputs/apk/`.

## Known Limitations

- Beta builds have not yet been validated on every controller layout or Android
  phone/handheld combination.
- Automatic phone-profile detection is heuristic and can choose the wrong
  display size for unusual game releases.
- Archives containing several JAR files require a one-time game selection.
- Some vendor-specific J2ME APIs remain limited by the upstream emulation core.

Please report reproducible problems through
[GitHub Issues](https://github.com/EriArk/JavaEmulator/issues) and include the
device model, Android version, game filename, and the symptom shown in AbyssME.
Do not upload commercial game files.

## Credits

AbyssME is built on
[J2ME Loader](https://github.com/nikita36078/J2ME-Loader) by Nikita Shakarun and
its contributors. It also includes open-source Mascot Capsule work originating
from [JL-Mod](https://github.com/woesss/JL-Mod) by woesss.

## License

Licensed under the [Apache License 2.0](LICENSE). See the repository history and
source headers for individual copyright notices.
