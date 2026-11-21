# TimeTop GameKing

The optional ARM64 F-Droid debug integration runs the GM-218 and GM-220 in the GameKing tab. Add the matching BIOS through the normal import action. Opening a BIOS card starts its built-in software. A cartridge can be a raw BIN, a GK file or an individual game ZIP. To index a collection containing game ZIPs, extract the outer archive into a folder and add that folder to the library. The GM-218 and GM-220 firmware files can coexist.

The cartridge header selects the color model. Installed files, per-game profiles, save states and firmware are included by the shared handheld backup system. The launcher uses the common virtual controls, physical mappings, graphics settings, game area and artwork capture. Inputs and checkpoints use the libretro interface. Each title has its own state directory.

## Build

Start with libretro/mame at commit `5f5a07748ce5c239c4118463ce8e6473743d4859`. The build compiles the GameKing translation unit, which registers both handheld drivers. The libretro core uses the software renderer. The native build needs GNU Make, Python 3, the host tools required by MAME and the Android NDK (validated with r28c on Linux).

```sh
git -C /path/to/mame checkout 5f5a07748ce5c239c4118463ce8e6473743d4859
engines/gameking/build-native.sh /path/to/mame
./gradlew -PgamekingProbe=true :app:assembleFdroidDebug
```

The native build copies upstream COPYING and legal texts into the APK. The source checkout, user firmware, games and compiled libraries stay outside Git. The driver flags imperfect sound on both models. [Pinned source and terms](https://github.com/libretro/mame/tree/5f5a07748ce5c239c4118463ce8e6473743d4859) · [GameKing driver](https://github.com/libretro/mame/blob/5f5a07748ce5c239c4118463ce8e6473743d4859/src/mame/handheld/gameking.cpp) · [Sticker attribution](bridge/src/main/assets/licenses/gameking-art.txt).
