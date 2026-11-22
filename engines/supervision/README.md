# Watara Supervision on Android

The optional ARM64 debug host uses [Potator](https://github.com/libretro/potator/tree/227c5f6f3ce74d32e9002ce24c1420288559a860). Select the Supervision sticker, then add a cartridge (.sv or .bin) or a ZIP with one cartridge. The console boots the cartridge directly. The card uses the hash of the extracted bytes, so importing it again as a ZIP keeps its settings and save state.

The host presents 160 × 160 pixels at 60 Hz. Potator synthesizes the cartridge's two square-wave channels, noise and DMA samples, and supplies 735 stereo frames at 44.1 kHz per emulated frame to the shared Android AAudio output. The common Controls and Display pages provide touch layouts, physical mappings and graphics settings. Use **Game menu → More → Save state** and **Load state** for a checkpoint. Portable library backup includes the cartridge, per-game settings and checkpoint.

On the Android builder:

```sh
git clone https://github.com/libretro/potator /path/to/potator
git -C /path/to/potator checkout 227c5f6f3ce74d32e9002ce24c1420288559a860
git clone https://github.com/visrealm/vrEmu6502 /path/to/vrEmu6502
git -C /path/to/vrEmu6502 checkout aae98cb14386d832cb7357c99626520b6590bc24
export VREMU6502_SRC=/path/to/vrEmu6502
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/28.2.13676358"
bash engines/supervision/build-native.sh /path/to/potator
bash gradlew -PsupervisionProbe=true :app:assembleFdroidDebug
```

The build combines Potator's console emulation with Troy Schrapel's MIT-licensed vrEmu6502. The staging script selects files from both pinned Git revisions. The Android CPU adapter handles the memory bus, interrupts and the register layout used by existing checkpoints. The CPU extension uses previously reserved checkpoint bytes. [Bundled notices](bridge/src/main/assets/licenses) · [CPU and migration checks](../../docs/checks/2026-11-22-license-remediation.md).

To run the CPU regression on the host:

```sh
stage=$(mktemp -d)
bash engines/supervision/prepare-core.sh /path/to/potator /path/to/vrEmu6502 "$stage"
cc -std=gnu11 -I"$stage/src" -Iengines/supervision/native engines/supervision/native/watara_cpu.c engines/supervision/tests/native/cpu_state.c -o "$stage/check"
"$stage/check"
```
