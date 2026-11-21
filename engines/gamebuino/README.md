# Gamebuino Classic

The optional Classic module runs ATmega328P software inside AbyssME using the local Simbuino4Web hardware modules. Games use the original 84 × 48 LCD, D-pad and A, B and C buttons. The same controls are available in the on-screen layout and physical controller mapper.

Import a single Intel HEX program or a ZIP containing one program. The Gamebuino Classic tab selects the machine directly. Opening an unmarked HEX from a file manager offers the available platforms and remembers the choice for that file. A matching Classic INF file in the ZIP supplies the title and artwork. The library can also capture a running game's screen.

Each game gets its own EEPROM and writable FAT16 SD card. A ZIP with one disk image (`.img`) seeds the card on first install. Use **Game menu → More → Mount SD image** to replace it later. Reimporting the program preserves the writable card. Both stores travel with the library backup. No external BIOS is required.

## Build

Prepare the pinned hardware modules on a build host with Git, Python 3 and dosfstools.

```sh
git clone --filter=blob:none --no-checkout https://github.com/joyrider3774/gamebuino_classic_games_compiled classic-catalog
python3 engines/gamebuino/prepare.py classic-catalog
./gradlew -PgamebuinoProbe=true :app:assembleFdroidDebug
```

The preparation script copies the selected emulator files from revision `9088327dd0168c19bf2abe6d6db720f6a19ef962` and generates an empty FAT16 card. The catalog games and populated SD image are test inputs supplied separately. See the [packaged notices](bridge/src/main/assets/licenses).
