# TimeTop GameKing, Hartung Game Master and Epoch Game Pocket Computer

The optional ARM64 F-Droid debug integration runs the GM-218 and GM-220 in the GameKing tab. Add the matching BIOS through the normal import action. Opening a BIOS card starts its built-in software. A cartridge can be a raw BIN, a GK file or an individual game ZIP. To index a collection containing game ZIPs, extract the outer archive into a folder and add that folder to the library. The GM-218 and GM-220 firmware files can coexist.

The cartridge header selects the color model. Installed files, per-game profiles, save states and firmware are included by the shared handheld backup system. The launcher uses the common virtual controls, physical mappings, graphics settings, game area and artwork capture. Inputs and checkpoints use the libretro interface. Each title has its own state directory.

## Hartung Game Master

The same build adds a separate Game Master tile and library. Import `gmaster.zip` or the 4 KiB MCU ROM `d78c11agf_e19.u1` once. Open an individual game ZIP, raw BIN, ROM or GM cartridge. For a collection distributed as a 7z archive of game ZIPs, unpack the outer archive first. The original 61 × 64 display, D-pad, A, B, Start and Select use the common handheld settings. The global two-button profile is available for physical controller mapping. The library captures game artwork from the running screen and keeps a separate save-state slot for each cartridge. [Retroid check](../../docs/checks/2026-11-22-hartung.md).

## Epoch Game Pocket Computer

Import `gamepock.zip` or `egpcboot.bin` once. Opening its library card runs the built-in graphics editor and sliding puzzle without a cartridge. Import cartridge BIN, ROM or EGPC files, or an individual game ZIP. A cartridge uses the installed system ROM and its own profile and save-state directory. The touch layout and physical mapping editor expose the D-pad, four numbered buttons, Select and Start. The global profile editor includes the Epoch button set. [Retroid verification](../../docs/checks/2026-11-22-epoch.md).

## Build

Start with libretro/mame at commit `5f5a07748ce5c239c4118463ce8e6473743d4859`. Rebuild the native libraries when updating an older GameKing checkout. The MAME subset includes the GameKing, Game Master and Epoch Game Pocket Computer drivers and registers four machines. The libretro core uses the software renderer. The native build needs GNU Make, Python 3, the host tools required by MAME and the Android NDK (validated with r28c on Linux).

```sh
git -C /path/to/mame checkout 5f5a07748ce5c239c4118463ce8e6473743d4859
engines/gameking/build-native.sh /path/to/mame
./gradlew -PgamekingProbe=true :app:assembleFdroidDebug
```

The native build copies upstream COPYING and legal texts into the APK. The source checkout, user firmware, games and compiled libraries stay outside Git. The GameKing driver flags imperfect sound on GM-218 and GM-220. [Pinned source and terms](https://github.com/libretro/mame/tree/5f5a07748ce5c239c4118463ce8e6473743d4859) · [GameKing driver](https://github.com/libretro/mame/blob/5f5a07748ce5c239c4118463ce8e6473743d4859/src/mame/handheld/gameking.cpp) · [Game Master driver](https://github.com/libretro/mame/blob/5f5a07748ce5c239c4118463ce8e6473743d4859/src/mame/handheld/gmaster.cpp) · [Epoch driver](https://github.com/libretro/mame/blob/5f5a07748ce5c239c4118463ce8e6473743d4859/src/mame/epoch/gamepock.cpp) · [Sticker attribution](bridge/src/main/assets/licenses/gameking-art.txt).
