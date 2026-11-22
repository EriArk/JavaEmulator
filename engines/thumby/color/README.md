# Thumby Color

The optional Android host runs the TinyCircuits MicroPython and Tiny Game Engine WebAssembly ports inside the AbyssME game screen. It loads the system files offline and starts each game in its own directory. Users can open a Python script or a ZIP containing one game's entry script and companion assets. No separate device firmware is required. A packaged arcade preview supplies cover art. Shared settings control the game area, touch deck and physical controller mappings, including both shoulder buttons and Menu.

The worker presents a 128 × 128 RGB565 framebuffer, polls nine digital inputs, and mixes four channels into 22,050 Hz mono audio. The game clock responds to pause and the shared speed setting. Ordinary functions decorated with `micropython.native` run as bytecode. The bytecode fallback for Viper supports bounded 8-, 16- and 32-bit views of guest buffers. Code relying on native machine addresses, inline assembly or exact native arithmetic needs separate support. The port does not emulate the physical board or its link cable.

Game writes under the installed game directory and the engine's save directory are kept in an atomic per-game file under `thumby_color/data`. The shared library backup includes these changes. The original game package remains available for reimport.

## Building the private host

Prepare the [original Thumby resources](../README.md) and activate Emscripten 3.1.68. The script checks out the pinned MicroPython fork and engine submodules. Pass dedicated source and staging directories as optional arguments. Generated runtime files, Android builds and arcade games stay outside Git.

```sh
bash engines/thumby/color/build.sh
./gradlew -PthumbyColorProbe=true :app:assembleFdroidDebug
```

Staging copies the Tiny Game Engine, MicroPython, cglm, FastNoiseLite and bm8563 license notices alongside the generated resources. The Android host and its changes are available in this repository. [Engine source](https://github.com/TinyCircuits/TinyCircuits-Tiny-Game-Engine/tree/64b5f0dc4ceebf273bf6bbc36261fdcfbe2ee4f2) · [MicroPython fork](https://github.com/TinyCircuits/micropython/tree/28cfbc90d4122f4e900c82ff14ff6c7ebd211711) · [Retroid verification](../../../docs/checks/2026-11-22-thumby-color.md).
