# Pokitto

The optional ARM64 module runs Pokitto BIN programs and POP packages through PokittoEmu v1.0.5. A ZIP can contain one game and its SD files, including music in subdirectories. A root-level disk image next to the program can supply an existing card. Choose **More → Mount SD image** to replace the writable card. A separate BIOS is unnecessary.

The importer recognizes the LPC11U68 vector table in loose BIN programs. POP packages keep their title, icon and first full-size screenshot. Games without packaged art use the library's runtime preview. Graphics profiles, screen layout, physical mappings and touch buttons use the shared handheld settings. The display is 220 × 176. The controls include the D-pad, A, B, C and Flash.

Each program has a writable FAT card and 4 KiB of EEPROM in its library data directory. Importing a ZIP after a loose program also installs its companion files. New files are added to an existing card on the next launch, preserving files the player has already written. Library backups include the game, its configuration and both stores.

## Build

Use the [pinned PokittoEmu source](https://github.com/felipemanga/PokittoEmu/tree/1bc908e39256accd427a0b8590bfcc2ef2e3c0d4) and an Android NDK 28 Linux build host.

```sh
git clone --branch v1.0.5 --depth 1 https://github.com/felipemanga/PokittoEmu PokittoEmu
bash engines/pokitto/build-native.sh PokittoEmu build/pokitto/jniLibs
./gradlew -PpokittoProbe=true :app:assembleFdroidDebug
```

The native host drives the CPU and peripherals locally, presents the guest RGB565 framebuffer and resamples the guest DAC to Android's 48 kHz audio stream. It flushes EEPROM writes and complete SD sectors during play. The launcher sticker uses a photograph of the original device. [Notices](bridge/src/main/assets/licenses)
