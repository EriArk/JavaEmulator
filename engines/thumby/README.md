# Original Thumby

The optional Android host runs the original 72 × 40 Thumby board inside the AbyssME game screen. A local WebView executes the TinyCircuits fork of RP2040js, MicroPython firmware and LittleFS. Game code runs on the emulated board. The emulator makes no network requests outside the per-game asset origin.

Open a Python file or a ZIP containing one arcade game. Keep companion modules, fonts and data beside the entry script. The importer preserves their relative paths under `/Games/<title>`. Loose and wrapped copies with identical game files share one card and save directory. An arcade preview video supplies library artwork when present. The shared game menu provides display settings, touch controls and physical mapping for the D-pad and B/A buttons.

The host rebuilds the initial flash deterministically and restores the sectors written by the game. Those sectors are named by block number under `thumby/data/<game-id>/flash-v1` and travel with an AbyssME library backup. This restores the game's own saved progress. Live CPU checkpoints and link-cable multiplayer have no implementation.

## System files

Open the Thumby library menu and select **Install system files**. Select `bootrom.bin` and `emulator-firmware.uf2` from the [original editor snapshot](https://github.com/TinyCircuits/TinyCircuits-Thumby-Code-Editor/tree/5b3710cc100a). Empty platforms offer the same installer from their launcher tile. The files live under `thumby/system` and travel with the existing library backup. Thumby Color uses its own runtime.

## Private build

Use the [TinyCircuits editor snapshot](https://github.com/TinyCircuits/TinyCircuits-Thumby-Code-Editor/tree/5b3710cc100a). From the AbyssME root, stage its runtime and enable the debug module in the existing combined build.

```sh
git clone https://github.com/littlefs-project/littlefs /path/to/littlefs
git -C /path/to/littlefs checkout 1863dc7883d82bd6ca79faa164b65341064d1c16
# Activate Emscripten 3.1.68 in this shell.
bash engines/thumby/build-littlefs.sh /path/to/littlefs build/thumby/assets
python3 engines/thumby/prepare.py /path/to/TinyCircuits-Thumby-Code-Editor build/thumby/assets
./gradlew -PthumbyProbe=true :app:assembleFdroidDebug
```

The APK contains editor runtime scripts, RP2040js and LittleFS built from pinned C source with the AbyssME flash adapter. Staging includes their notices. [Original gameplay checks](../../docs/checks/2026-11-22-thumby.md) · [System-file import and existing-save checks](../../docs/checks/2026-11-22-license-remediation.md).
