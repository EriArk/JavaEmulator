# AbyssME

**Old mobile games on Android handhelds and phones.**

AbyssME is an independent fork of
[J2ME Loader](https://github.com/nikita36078/J2ME-Loader), focused on making
old mobile Java games feel at home on modern landscape handhelds. It builds on
the J2ME Loader runtime while replacing the setup-heavy workflow with a game
library, automatic first-launch preparation, and fast per-game controller
mapping. Mophun and experimental Symbian / N-Gage support share the same
library, display controls and in-game menu.

The current beta provides two installable variants built from the same emulation
core: a controller-first **Handheld** APK and a portrait/touch-friendly
**Phone** APK with an on-screen keypad.

> **Project status:** Beta. AbyssME is usable, but compatibility automation and
> launcher integration still need testing across more games and devices.

[Download 0.3.0 Beta 5](https://github.com/EriArk/JavaEmulator/releases/tag/v0.3.0-beta.5)

## Current Beta

For website copy, progress posts and handoffs, start with the
[current product/development status](docs/PROJECT_STATUS.md) and
[dated changelog](CHANGELOG.md). They also describe local/private experiments,
explicitly separated from public downloads. The
[0.3.0 beta.5 notes](docs/releases/v0.3.0-beta.5.md) include the combined-engine scope,
verification limits and source downloads.

Beta.5 fixes a shared Java startup deadlock reproduced with **Age of Empires II
Deluxe**, improves live display changes and transfer recovery, and adds a
per-game **Button theme** for Phone controls. The refreshed numeric keypad can
stay dark with light app menus. Phone shortcuts overlay the game and hide after
five idle seconds, without reserving a top row.

It retains **Automatic / Wide / Portrait** Gallery cover formats without
cropping, a compact library for square and near-square screens, and **Restart
game** in the shared in-game menu. Restart uses saved settings and discards
unconfirmed previews; it is a recovery shortcut, not a general black-screen fix.

The release retains Light/Dark/System appearance, hideable Phone shortcuts, touch-key
feedback, per-game FPS presets and safer duplicate Java imports. It improves
Java screen startup and filename-based setup hints, external-launcher return
paths, system-file import and guarded SIS/SISX removal. N-Gage cards remain
visible alongside ordinary SIS games on the same virtual phone.

The 0.3 beta adds embedded Symbian / N-Gage, reusable system-file setup,
SIS/SISX and native-game archive import, and whole-phone backup/restore.
Games from all three engines appear in the library. Their common in-game menu
provides **Resume, Display, Controls, Screenshot, More, Restart game and Exit game**.
Symbian compatibility remains experimental; games and firmware are not included.
See [what to provide for Symbian](docs/releases/v0.3.0-beta.1.md#getting-started-with-symbian).

The release retains the graphical controller mapper with diagonal bindings, two Phone touch
layouts, live Java game-image rotation, quick display settings, J2ME Loader
library import, menu-based backup/restore, a hideable screenshot button and
non-destructive database migrations. Saved Phone orientation and settings-field
contrast with Android's Light theme are also fixed.

Use **Settings > Library > Library transfer** to import from J2ME Loader or move installed
games and saves between AbyssME installations. See the
[workflow and limitations](docs/LIBRARY_TRANSFER.md).

Both builds share **Display** and **Controls** pages between the library's
per-game settings and the in-game menu. On Phone, choose **Controls > Touch layout >
Phone / Gamepad**. Use **Try changes** (or **Try in game** from the library), then
**Keep** or **Undo**; unconfirmed changes revert after about 20 seconds of preview.
Quick screen-size and phone-profile presets are under **More** while playing;
the full compatibility editor remains under **Advanced**.
The old expert editor still saves ordinary edits directly; its **Fix game** action
now offers a trial instead. See [settings trials](docs/SETTINGS_TRIALS.md).

Short landscape screens and Phone portrait now use a [compact library](docs/COMPACT_LIBRARY.md):
one header, larger game area and an on-demand library menu. Menus keep Close/Back
visible while their contents scroll; short landscape also uses compact mapper pages.
Handheld hides Android system bars; Gallery, List and Grid remain available.
General Settings is split into **Library**, **Playing** and **Support**. Library
transfer separates the operation chooser, game selection and results, leaving
more room for the game list on small screens.
The installer keeps its actions reachable while details and archive choices
scroll. Installation errors stay visible until closed; deletion confirmations
start on Cancel when using a controller.
ZIP collections selected through Android's file picker now reach archive selection
instead of being mistaken for a single JAR.
Continuing past a mismatched JAD/JAR description now keeps the selected game and
its original import identity, so reusing the same source URI finds the existing
entry. Identical Java imports through another URI offer reuse of an installed
game or an intentional separate copy; confirmed reuse preserves saves and is
remembered. Existing duplicates are not automatically merged.
Automatic artwork now favors declared icons and identifiable title art over
arbitrary textures. **Game actions > Artwork** offers custom icons/covers and
an explicit refresh for older libraries. See [artwork behavior](docs/ARTWORK.md).

This beta includes a FreeJ2ME-derived replacement M3G renderer
and experimental Wolphun-based Mophun support. The legacy EPL M3G/tone engine
has been removed. The combined application uses GPLv3 while retaining component
notices; see [licensing](docs/LICENSING.md). Historical release assets are unchanged.

## Screenshots

![Handheld library in Grid view](screenshots/releases/v0.3.0-beta.5/01-handheld-grid.png)

<img src="screenshots/releases/v0.3.0-beta.5/03-phone-keypad.png" alt="Phone build playing Age of Empires II Deluxe in portrait with its numeric keypad" width="270">

Unretouched captures from the signed beta.5 APKs on a Retroid Pocket Classic
(Android 14), including the separate Phone variant. These are app screenshots,
not photographs. Games were installed separately and are not bundled.
See the [fresh screenshot pack and captions](screenshots/releases/v0.3.0-beta.5/README.md).

## Highlights

- Controller-first landscape interface with Gallery, List, and Grid views
- Separate Phone build with portrait mode, direct touch input, and virtual keys
- Recent games, favorites, folders, search, sorting, and local artwork
- Automatic compatibility preparation the first time a game is launched
- Per-game named control profiles and a quick mapping overlay
- Live in-game controls for display preset and virtual screen size
- Android intent entry point for external launchers; ES-DE and Beacon setup notes
- JAR, JAD, ZIP, and 7Z sources through Android's Storage Access Framework
- Local-only library and diagnostics; no account, cloud service, or telemetry

## Requirements

- Android 10 or newer (API 29+)
- 64-bit ARM device (arm64-v8a) for the combined 0.3 APKs; the Java/Mophun-only
  [0.2 beta](https://github.com/EriArk/JavaEmulator/releases/tag/v0.2.0-beta.1)
  remains available for other architectures
- A physical controller is recommended for the Handheld build

The Handheld build is intentionally landscape-only and has no on-screen keypad.
The Phone build has a rotatable library, a compact portrait layout, direct MIDP
touch events, and configurable virtual controls. Per-game orientation is preserved;
broader device acceptance remains tracked in [#3](https://github.com/EriArk/JavaEmulator/issues/3).

## Install

1. Open [Releases](https://github.com/EriArk/JavaEmulator/releases).
2. Choose an APK:
   - `AbyssME-...-handheld.apk` for handhelds and physical controllers.
   - `AbyssME-...-phone.apk` for phones, portrait mode, and touch keys.
3. Allow installation from your browser or file manager when Android asks.
4. Install the APK and open AbyssME.

The package IDs are `io.github.eriark.abyssme` and
`io.github.eriark.abyssme.phone`, so both variants can be installed together.
Older J2ME Loader builds also use a different package.

## Quick Start

1. Choose **Add game** for one file, or **Folders** to index a collection.
2. Select a JAR, JAD, ZIP, or 7Z file using Android's file picker. Experimental
   Mophun uses a direct `.mpn` file; Mophun archive/folder indexing is not included.
3. Start the Java game. On first launch, AbyssME analyzes metadata, resources and API
   hints to select initial display and compatibility settings, then saves them.
4. Open the in-game menu for **Display** and **Controls**, or hold **Select**
   for per-game mapping. Java also retains tap-to-show quick display actions.
5. On Phone, open **Controls > Touch layout** and choose **Phone** or **Gamepad**.
   Try display/control changes in the game, then **Keep** or **Undo** them.

In the library, **Y** toggles the selected game as a favorite and **L1/R1**
cycles Gallery, List, and Grid views.

For Symbian / N-Gage, add a compatible phone dump in **System files**, then add
the game from the library. Setup asks for missing system/component installers
and retains the selected game while you provide them. See the
[Symbian setup notes](docs/releases/v0.3.0-beta.1.md#getting-started-with-symbian).

## External Launchers

AbyssME accepts read-only `content://` game URIs through Android `ACTION_VIEW`.
The intended flow is to prepare a new source once, launch prepared games
directly, and return to the frontend on exit. End-to-end cold/warm launch and
return behavior was exercised with Beacon, Pegasus and Daijisho on Retroid
during development. **ES-DE remains unverified**; see
[#7](https://github.com/EriArk/JavaEmulator/issues/7).
Installed Symbian/N-Gage games can export an `.abyssme` launch file from their
game actions; the exact virtual phone and game remain bound to that entry.

- Handheld package: `io.github.eriark.abyssme`
- Phone package: `io.github.eriark.abyssme.phone`
- Activity: `ru.playsoftware.j2meloader.MainActivity` (both variants)
- Action: `android.intent.action.VIEW`
- Supported files: JAR, JAD, ZIP, 7Z, MPN and exported `.abyssme` launch files

ES-DE rule examples and Beacon setup details are in
[Launcher integration](docs/LAUNCHER_INTEGRATION.md).

## Compatibility and Fixes

Automatic preparation is heuristic: it does not run the game under several
phone profiles or verify that gameplay is correct. If a title has problems, open its
settings and use **Fix game** to describe the symptom: wrong size/cropping,
black screen/flicker, crash, or incorrect controls. The full expert settings
remain available per game without cluttering the normal flow.

Game behavior varies between phone releases. When possible, start with the
original build made for a common 176x220, 240x320, or 320x240 device profile.

## Build

See [Contributing](CONTRIBUTING.md) for JDK/SDK versions, signing and verification.
Debug builds use the normal Android debug key without a private signing file.
The M3G and Mophun sources are included; no private engine checkout is required:

```shell
./gradlew :app:assembleFdroidDebug :app:assemblePhoneDebug
```

On Windows, use `gradlew.bat`. APK output is written under
`app/build/outputs/apk/`.
These commands build Java/Mophun only. For the complete 0.3 build, restore the
separate Symbian source bundle, follow its [build prerequisites](engines/symbian/DISTRIBUTION.md),
and pass `-PsymbianRelease=true`. Combined outputs are under
`app/build/symbian-release/outputs/apk/`.
GitHub Actions is intentionally disabled. Builds and checks are run locally,
and release assets are uploaded manually.

## Known Limitations

- Beta builds have not yet been validated on every controller layout or Android
  phone/handheld combination.
- Automatic phone-profile detection is heuristic and can choose the wrong
  display size for unusual game releases.
- Archives containing several JAR files require a one-time game selection.
- Some vendor-specific J2ME APIs remain limited by the upstream emulation core.
- Back up libraries and saves before updating. Storage-provider restrictions and
  URI-alias duplicate imports are not universally resolved.
- Mophun is experimental; broad compatibility and audio are not established.
- Symbian/N-Gage is experimental and needs user-supplied system files. Some
  games still fail or require specific phone/component versions; installation
  success does not guarantee gameplay. See the [current limits](docs/releases/v0.3.0-beta.1.md#known-limits).

Please report reproducible problems through
[GitHub Issues](https://github.com/EriArk/JavaEmulator/issues) and include the
device model, Android version, game filename, and the symptom shown in AbyssME.
Do not upload commercial game files.

Current work and unresolved decisions are tracked in
[Issues](https://github.com/EriArk/JavaEmulator/issues). Contribution instructions
and the project layout are in [CONTRIBUTING.md](CONTRIBUTING.md).

## Credits

AbyssME is built on
[J2ME Loader](https://github.com/nikita36078/J2ME-Loader) by Nikita Shakarun and
its contributors. It also includes open-source Mascot Capsule work originating
from [JL-Mod](https://github.com/woesss/JL-Mod) by woesss.

## License

The combined application uses [GNU GPL version 3](LICENSE). Individual components
retain their original licenses and copyright notices, including Apache-2.0,
GPL-3.0-or-later, MPL and LGPL components. The old EPL M3G/tone implementation
was removed, not relicensed. M3G now uses the FreeJ2ME-derived replacement;
experimental Mophun support derives from Wolphun. See [licensing](docs/LICENSING.md)
for pinned sources, attribution and matching release-source materials. Historical
versions retain their original terms.

Third-party games and artwork are not relicensed by this notice. The provenance
and licenses of current test fixtures, plus the disposition of legacy files,
are documented separately in [test-games](test-games/README.md).
