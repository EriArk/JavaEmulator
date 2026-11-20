# Watara Supervision on Android

The optional ARM64 debug host uses [Potator](https://github.com/libretro/potator/tree/227c5f6f3ce74d32e9002ce24c1420288559a860). Select the Supervision sticker, then add a cartridge (.sv or .bin) or a ZIP with one cartridge. The console boots the cartridge directly. The card uses the hash of the extracted bytes, so importing it again as a ZIP keeps its settings and save state.

The host presents 160 × 160 pixels at 60 Hz. Potator synthesizes the cartridge's two square-wave channels, noise and DMA samples, and supplies 735 stereo frames at 44.1 kHz per emulated frame to the shared Android AAudio output. The common Controls and Display pages provide touch layouts, physical mappings and graphics settings. Use **Game menu → More → Save state** and **Load state** for a checkpoint. Portable library backup includes the cartridge, per-game settings and checkpoint.

On the Android builder:

```sh
git clone https://github.com/libretro/potator /path/to/potator
git -C /path/to/potator checkout 227c5f6f3ce74d32e9002ce24c1420288559a860
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/28.2.13676358"
bash engines/supervision/build-native.sh /path/to/potator
bash gradlew -PsupervisionProbe=true :app:assembleFdroidDebug
```

The repository-level Unlicense and the CPU source's separate noncommercial notice are included in the [optional module](bridge/src/main/assets/licenses). No games are bundled. [Retroid audio and gameplay checks](../../docs/checks/2026-11-20-supervision.md).
