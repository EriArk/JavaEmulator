# Gamebuino Classic on Retroid

The optional Gamebuino Classic host was installed over the existing combined ARM64 debug build on a Retroid Pocket Classic. The APK SHA-256 was `bc50532094ca95dbb0e91486cba37cc8da742ca88c1334287e59e6d7c0cf8550`. The [adapter](../../engines/gamebuino/README.md) runs locally inside the app and supplies per-game storage. This check used private copies of the games in the [pinned catalog](https://github.com/joyrider3774/gamebuino_classic_games_compiled/tree/9088327dd0168c19bf2abe6d6db720f6a19ef962).

| Input | Device result |
| --- | --- |
| Bomber, HEX SHA-256 `aa629b9bdce7b344e89f918473536f9713062299d4b3d11619b40772828d6fb4` | Android's Open action offered Arduboy / FX and Gamebuino Classic. Selecting Classic reached the 84 × 48 title screen. A opened the game menu and started a single-player match. A tapped on the touch overlay started another round from the score screen. The library captured a preview. |
| CHIP-8 Gamebuino, HEX SHA-256 `b6fd23f2b2bb054cbf90df39701c423e390d97e57b5cbb9eb35e9c554a332896` | Imported a ZIP containing the program and the catalog's SD disk image. The guest listed six CHIP-8 ROMs, including BRIX, Invaders and Tetris. Selecting BRIX read the program and displayed the brick field. |

Bomber accepted Android events sent through the attached Xbox Wireless Controller device node. A digital recording from the Retroid during gameplay measured −26.6 dB mean and −8.1 dB peak over 7.68 seconds after a 30 Hz high-pass filter. This checks Android playback during the opening match. Manual controller feel, the analog speaker and multiplayer remain untested.

The CHIP-8 loader wrote to its private SD card. The seed SHA-256 was `ff315c5c6dc1129f6e25577dd8fc965856a1c7c0e246af1a365ba37f51de4762`. After loading BRIX the writable card had SHA-256 `8463ba7f0c68fd4ea51de3dc5bd8e9edd30d970199f4421681f20a279facbd30`. A separate AVR test program read EEPROM byte zero, incremented it and wrote it back. Two cold launches using the same HEX produced 0 and then 1, with the other 1,023 bytes still erased. This exercises guest writes, per-game identity and restoration after process death.

Cold reopening the CHIP-8 ZIP reused its card and retained the modified SD hash. On the final build, the shared mapper assigned Xbox X to guest A. After a cold relaunch, X opened Bomber's single-player menu and touch A started the match. The CRT display setting also persisted and rendered visible scanlines. Default settings were restored after this check.

All 18 Android tests in LibraryTabsTest, PlatformControlsTest and HandheldCycleTest passed, including populated-library backup and restore to an isolated destination, ambiguous HEX routing, ZIP identity and INF artwork. All 70 previous managed cards remained and the two Classic cards brought the count to 72. The three pre-existing Arduboy save files matched their pre-update hashes. The private APK was updated in place. No public release check was part of this run.
