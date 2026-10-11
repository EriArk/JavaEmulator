# AbyssME - Screenshots for Posts

Fresh, unedited captures from the installed **0.1.0-beta.2 experimental.15**
Phone and Handheld test builds, taken on **11 October 2026**.
These are Android 15 / API 35 emulator screenshots, not device photographs
or a claim of physical-device testing. This folder does not contain an APK.

[Download the complete PNG pack](../AbyssME-post-2026-10-11.zip?raw=true).

## Suggested Selection

For a short post: **01, 08, 10, 11, 12**. Gallery is the strongest library
overview; the two portrait captures show the alternative touch layouts.

| File | Screen |
| --- | --- |
| [01-library-gallery.png](01-library-gallery.png) | Eight-game Favorites collection with covers and game details |
| [02-library-grid.png](02-library-grid.png) | Icon grid, including the built-in fallback for missing icons |
| [03-library-list.png](03-library-list.png) | Compact game list with a detail pane |
| [04-settings.png](04-settings.png) | Main settings categories |
| [05-library-transfer.png](05-library-transfer.png) | Backup, restore, and J2ME Loader import entry points |
| [06-gameplay-2048.png](06-gameplay-2048.png) | Actual 2048 gameplay in Handheld mode |
| [07-game-menu.png](07-game-menu.png) | In-game pause menu |
| [08-controller-mapper.png](08-controller-mapper.png) | D-pad and diagonal inputs alongside the graphical phone keypad |
| [09-display-settings.png](09-display-settings.png) | In-game picture size, smoothing, and rotation settings |
| [10-phone-keypad.png](10-phone-keypad.png) | Portrait gameplay with the Phone numeric keypad |
| [11-phone-gamepad.png](11-phone-gamepad.png) | Portrait gameplay with the virtual Gamepad layout |
| [12-deep-abyss-gameplay.png](12-deep-abyss-gameplay.png) | Deep Abyss, first-level gameplay in the experimental Mophun runtime |

## Capture Notes

- Landscape: 1920 x 1080, 240 dpi. Portrait: 1080 x 1920, 400 dpi.
- PNG files are direct ADB screen captures. No UI was redrawn, composited,
  cropped, or retouched. System insets and game letterboxing remain intact.
- The library is a curated Favorites collection, not a bundled game catalog.
  Gallery covers were manually selected from the game authors' original images
  using the app's custom-artwork support. They do not demonstrate automatic
  cover extraction. Grid and List retain original icons or native fallback art.
- 2048 was played using directional input. Phone captures show the same board
  with the two supported touch layouts. The mapping capture does not change
  the game's saved controls.
- These images document UI and visible gameplay only, not complete game
  compatibility, audio quality, or save reliability. No game files are included.
- `manifest.json` records image sizes, SHA-256 hashes, and installed APK hashes.

## Game and Artwork Credits

- [2048 for J2ME](https://github.com/jsmucr/2048-for-J2ME), Jan Smucr.
- CavernsOfFire, DungeonsOfHack, MatrixMiner, Munchies, Ogrotron, PubMan,
  and SpaceRun: Boris van Schooten's
  [JGame mobile games](https://www.13thmonkey.org/~boris/jgame/JGame/html/mobile.html)
  and [original game artwork](https://www.13thmonkey.org/~boris/jgame/JGame/html/games.html).
- Deep Abyss: gameplay from the user-provided Mophun game, shown for emulator
  demonstration. Game and artwork rights remain with their respective owners;
  this screenshot pack does not relicense them.
