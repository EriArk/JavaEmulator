# Mega Duck / Cougar Boy on Android

Open **Platforms → Mega Duck**. Use **Add game** for a cartridge or a ZIP containing one cartridge. For a collection, extract its games into a folder and select **Library menu → Add folder**. The console boots the cartridge directly without a BIOS. The Mega Duck tab accepts aligned cartridge images with .bin, .md1, .md2 and .duck extensions. Folder scanning and Android file opening distinguish common Mega Duck .bin files from Supervision cartridges and firmware by their startup instructions. Use the platform tab to import a cartridge with an unusual entry point.

The optional ARM64 debug module uses [SameDuck](https://github.com/LIJI32/SameBoy/tree/5619abdb01cee6bedb47599cdb5532c318443b52). The host displays 160 × 144 pixels and plays the emulated APU's stereo output at 48 kHz. **Display** and **Controls** use the common graphics, game area, virtual deck and physical controller profiles. **More → Save state** stores a checkpoint for the game. Load it from the same menu after a cold launch. Reimporting identical cartridge bytes reuses its card and checkpoint. Portable backup includes installed cartridges, their settings and states.

On the Android builder, from the project root:

```sh
git clone --branch SameDuck https://github.com/LIJI32/SameBoy /path/to/SameDuck
git -C /path/to/SameDuck checkout 5619abdb01cee6bedb47599cdb5532c318443b52
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/28.2.13676358"
bash engines/megaduck/build-native.sh /path/to/SameDuck
bash gradlew -PmegaduckProbe=true :app:assembleFdroidDebug
```

The build applies a small [adapter patch](android.patch) to temporary copies of two pinned source files. It sets the core's audio output to the device rate and clears work RAM on reset so games can start consistently. The module bundles [third-party notices](bridge/src/main/assets/licenses). Games are supplied separately. [Collection and Retroid checks](../../docs/checks/2026-11-20-megaduck.md).
