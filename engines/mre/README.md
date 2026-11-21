# MediaTek MRE on Android ARM64

This optional debug engine runs MediaTek MAUI Runtime Environment games in the handheld activity. A library card installs a loose VXP or a ZIP with one VXP and identifies it by the unpacked game's SHA-256. Firmware is unnecessary. The engine provides a 240×320 framebuffer, 30 Hz timing, stereo PCM, phone keys, pen input and per-game virtual drives. The More menu saves and loads a checkpoint. The shared library backup includes the game, settings and writable data.

## Build the private test engine

Use the existing Linux Android builder, NDK 28.2.13676358, CMake, Ninja, GNU patch, Python 3 and Zig 0.15.2. Check out the following revisions. Place the three dependency repositories inside the Handyplay checkout at the paths shown.

| Source | Revision | Location |
| --- | --- | --- |
| [Handyplay](https://github.com/mohamedahmed166e/my-mre-emulator) | 460b149302e9a52523bc314a37efabbee9f3d666 | checkout root |
| [Unicorn](https://github.com/unicorn-engine/unicorn) | 8028ec436f2d9376525352dd38ed9ed6b9f6be10 | vendor/unicorn |
| [TinySoundFont](https://github.com/schellingb/TinySoundFont) | a3621a19c6504885cd9583e6e26c35bc954fc8ec | vendor/TinySoundFont |
| [minimp3](https://github.com/lieff/minimp3) | 142823796ab8efa55e1bf11406028f3be77ecceb | vendor/minimp3 |

Download the [GNU Unifont 16.0.04 BMP hex glyphs](https://unifoundry.com/pub/unifont/unifont-16.0.04/font-builds/unifont-16.0.04.hex.gz). The script checks their digest, packs them for the renderer, patches an isolated copy of the core and builds only the ARM guest backend. The Android adapter loads the same GeneralUser GS bank as the existing WIPI/Symbian configuration. Its data and license come from the [Symbian build inputs](../symbian/DISTRIBUTION.md). The Android build does not embed Handyplay's gm.sf2 or unifont.bin.

```sh
MRE_UNIFONT=/absolute/path/to/unifont-16.0.04.hex.gz \
    bash engines/mre/build-native.sh /absolute/path/to/handyplay
```

Add `-PmreProbe=true` to the existing ARM64 fdroid debug build. Native outputs are kept under `build/mre/`. Use the combined install configuration for a Retroid update.

## Compatibility and distribution

Handyplay carries AGPL-3.0. Unicorn identifies its engine as GPLv2 and carries separate QEMU, LGPL and other component notices. TinySoundFont is MIT and minimp3 is CC0. The GNU Unifont glyphs use OFL 1.1. This is a private test configuration pending resolution of the combined distribution terms. The repository contains host/build changes and the adaptation patch. Public APKs do not include this engine.

The upstream checkpoint format restores guest RAM and screen data. It drops active audio voices and open host-file handles on load. Results for named games and the tested build are in the [Retroid report](../../docs/checks/2026-11-21-mre.md).
