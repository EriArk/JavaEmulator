# Bit Corporation Gamate

Optional ARM64 Android debug integration. The machine uses the pinned floooh/chips 6502 and AY-3-8910 with a Gamate memory map, cartridge interface and two-plane LCD adapted from MAME. The shared handheld activity supplies touch and controller mappings, graphics settings, artwork, audio playback and the save-state menu.

## Build

Fetch the two unmodified headers from [floooh/chips at 7ef8776](https://github.com/floooh/chips/tree/7ef8776f637a6424ac615da64de6b968b06de791/chips). The build verifies both digests before compiling.

```sh
mkdir -p build/gamate/chips
for name in m6502 ay38910; do
    curl -fsSL "https://raw.githubusercontent.com/floooh/chips/7ef8776f637a6424ac615da64de6b968b06de791/chips/$name.h" -o "build/gamate/chips/$name.h"
done
bash engines/gamate/build-native.sh build/gamate/chips
./gradlew -PgamateProbe=true :app:assembleFdroidDebug
```

## Play

Import a Gamate BIOS first. The app accepts a 4 KiB BIOS file and the usual ZIP with either or both BIOS variants. Importing or opening a BIOS card selects it for all games. When a ZIP contains both stock variants the importer prefers UMC. Firmware is stored in the library and included in portable library backups.

Open a cartridge or a single-game ZIP from the Gamate tab. Folder discovery identifies cartridge headers inside shared .bin files. The game uses the shared game menu for screen placement, touch controls, physical bindings and speed. **More → Save state** and **Load state** use a checkpoint per cartridge. Checkpoints verify both the cartridge and BIOS identities. Library covers can come from an archive or a runtime preview.

[Device results and remaining coverage](../../docs/checks/2026-11-21-gamate.md) · [Bundled component notices](bridge/src/main/assets/licenses/gamate-components.txt)
