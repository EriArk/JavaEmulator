# ESPboy Little Game Engine

The optional Android host runs the LGE virtual machine, its 128 × 128 indexed display, sprites, timers, tones and RTTTL music offline. It supports native LGE containers including compressed programs, header icons and author fields, and raw LGE bytecode. A ZIP may contain a game with its artwork. Open headerless BIN files from the ESPboy shelf to distinguish them from other handheld binaries. The game package supplies the runtime program. A separate BIOS is unnecessary.

The game ID is the SHA-256 of the decoded bytecode. Raw, packed and ZIP copies share settings and guest EEPROM progress. The stored cartridge retains an LGE header for recognition when copied out of the library. The 24 × 16 header icon supplies library artwork. The standard Android game menu provides display settings, remapping, touch layouts and controller profiles. The physical ACT and ESC buttons correspond to LGE A and B. LFT and RGT provide Select and Start. Guest EEPROM writes use the shared per-game atomic storage bridge and travel with the library backup.

## Private build

Stage the SDK's VM, bitmap font and sound implementation at the pinned revision before enabling the debug host. The staging script copies the SDK license into the APK. The device game catalog is a separate test input.

```sh
git clone https://github.com/corax89/game_engine_for_esp8266_with_compiler /path/to/lge-sdk
git -C /path/to/lge-sdk checkout c225ad0aaf428081e50f105e5d56dbf401cf1fa2
python3 engines/espboy/prepare.py /path/to/lge-sdk build/espboy/assets
./gradlew -PespboyProbe=true :app:assembleFdroidDebug
```

The VM implements the LGE instruction set. ESP8266 firmware images, external peripherals and wireless services use different hardware interfaces. Guest saves preserve data written by a game. [SDK](https://github.com/corax89/game_engine_for_esp8266_with_compiler/tree/c225ad0aaf428081e50f105e5d56dbf401cf1fa2) · [Device implementation and game catalog](https://github.com/ESPboy-edu/ESPboy_little_game_engine/tree/3adf0c875f999de2f2f99c2815e825cb2a3bfbb8) · [Retroid device checks](../../docs/checks/2026-11-22-espboy.md).
