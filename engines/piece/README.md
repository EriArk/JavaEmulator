# Aquaplus P/ECE on Android

The optional ARM64 debug build runs the MIT-licensed [piece-emu libretro core](https://github.com/autch/piece-emu/tree/9aedad032c5d06a8c6d7cf74142a2b9ac32d77ac). Open the P/ECE sticker on the platform grid to reach its library tab.

## Import and play

1. Use **Add game** to import a complete flash image (`.pfi`), Aquaplus’s `update120.exe`, or its extracted `all.bin`. AbyssME reads the updater as an archive and prepares the flash image on the device. The supplied 1.20 updater includes both capacities and selects the 2 MiB image. The image gets its own library card and becomes the base for subsequent game imports. Reimport a different base to select it.
2. Add a standalone `.pex` or a game ZIP containing a single `.pfs` file list and the files it references. The importer adds the application and its listed resources to a private copy of the base image. Retain the original PFFS filenames. Choose a base with sufficient free sectors and without conflicting files.
3. Open the new card and select the application in the P/ECE system menu. Each card keeps its own display settings, controller mapping and writable flash. When the guest writes a score or other data, the host atomically saves the changed image while playing and on pause or exit. A cold launch starts the guest OS with that flash. The core has no instant save states.

For desktop preparation of a base image from [Aquaplus's system updater](https://aquaplus.jp/piece/dl/update120.exe), extract the archive and use the upstream `mkpfi` tool. For example, `mkpfi -2mb piece/update/2mb/all.bin System.pfi` creates a 2 MiB image. The [upstream instructions](https://docs.libretro.com/library/piece/) also cover the original 512 KiB hardware. The application contains no firmware or games.

The guest has a 128 × 88 grayscale display and 32 kHz audio. The shared touch deck supplies directions, A (OK), B (0), Select (7) and Start (9). Physical controller profiles, per-game mapping, display effects and touch settings use the shared handheld controls.

## Build the optional debug host

On the Android builder with the NDK, CMake and Ninja available:

```sh
git clone https://github.com/autch/piece-emu /path/to/piece-emu
git -C /path/to/piece-emu checkout 9aedad032c5d06a8c6d7cf74142a2b9ac32d77ac
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/28.2.13676358"
bash engines/piece/build-native.sh /path/to/piece-emu
bash gradlew -PpieceProbe=true :app:assembleFdroidDebug
```

The build links upstream's libretro-only target, the common AbyssME RGB565/AAudio frontend and Android's static C++ runtime. The native host and upstream [notices](bridge/src/main/assets/licenses) belong to the optional debug build. Tested together with WIPI, Dingoo/Gemei and Symbian on [Retroid Pocket Classic](../../docs/checks/2026-11-20-piece.md).
