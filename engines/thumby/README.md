# Original Thumby

The optional Android host runs the original 72 × 40 Thumby board inside the AbyssME game screen. A local WebView executes the TinyCircuits fork of RP2040js, MicroPython firmware and LittleFS. Game code runs on the emulated board. The emulator makes no network requests outside the per-game asset origin.

Open a Python file or a ZIP containing one arcade game. Keep companion modules, fonts and data beside the entry script. The importer preserves their relative paths under `/Games/<title>`. Loose and wrapped copies with identical game files share one card and save directory. An arcade preview video supplies library artwork when present. The shared game menu provides display settings, touch controls and physical mapping for the D-pad and B/A buttons.

The host rebuilds the initial flash deterministically and restores the sectors written by the game. Those sectors are named by block number under `thumby/data/<game-id>/flash-v1` and travel with an AbyssME library backup. This restores the game's own saved progress. Live CPU checkpoints and link-cable multiplayer have no implementation.

## Private build

Use the [TinyCircuits editor snapshot](https://github.com/TinyCircuits/TinyCircuits-Thumby-Code-Editor/tree/5b3710cc100a). From the AbyssME root, stage its runtime and enable the debug module in the existing combined build.

```sh
python3 engines/thumby/prepare.py /path/to/TinyCircuits-Thumby-Code-Editor build/thumby/assets
./gradlew -PthumbyProbe=true :app:assembleFdroidDebug
```

The staging script copies the editor and RP2040js notices. Its output includes upstream firmware, the RP2040 boot ROM and the compiled LittleFS module. Deployment so far is the private Retroid build. Public binary distribution still requires a component and corresponding-source review. The [Retroid checks](../../docs/checks/2026-11-22-thumby.md) use games from the [original Thumby Arcade](https://github.com/TinyCircuits/TinyCircuits-Thumby-Games).
