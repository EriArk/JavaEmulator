# Odin 2 test report - 2026-06-21

Device: AYN Odin 2, Android 13, 1080x1920 @ 369 dpi.

APK installed:
`test-builds/J2ME_Loader-quick-look-presets-fdroid-debug.apk`

Checked:
- Launcher opens in landscape and shows the existing library.
- Installed games from `/sdcard/Download/pack mas 1000 juegos java Kevtech/...`.
- Imported `240x320_megacityempirenewyork.jar`.
- Imported `alien_shooter_3d_240x320-90538.jar`.
- First-run compatibility test runs before game launch.
- Runtime quick settings overlay opens on tap and auto-hides.
- `Look` preset cycles live; `Full` visibly stretches the game.
- Touch controls / virtual keypad are not drawn over the game.

Observed profile results:
- Megacity Empire New York: `240x320`, `TouchInput=false`, `ShowKeyboard=false`, compatibility `Motorola`.
- Alien Shooter 3D: `240x320`, `TouchInput=false`, `ShowKeyboard=false`, compatibility `Nokia S40`.

Issues found:
- In-game old toolbar still appears at the top with keyboard/camera/menu icons.
- Quick settings buttons use black text on teal buttons; readable, but visually off-style.
- MIDlet installer dialog is still stock Material gray and not integrated with the launcher style.
- Some existing games still have tiny icons in the launcher, especially `1916 Dogfight`.
- Megacity selected `Motorola` even though this may not be the best default for a Gameloft game.

Screenshots:
- `odin2-screenshots/01-launcher.png`
- `odin2-screenshots/03-quick-overlay.png`
- `odin2-screenshots/05-look-full.png`
- `odin2-screenshots/10-megacity-start.png`
- `odin2-screenshots/13-import-alien.png`
- `odin2-screenshots/15-alien-start.png`
- `odin2-screenshots/16-launcher-after-tests.png`
