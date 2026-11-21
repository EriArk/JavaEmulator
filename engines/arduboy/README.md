# Arduboy and Arduboy FX

The optional Android ARM64 module embeds [Ardens v0.25.12](https://github.com/tiberiusbrown/Ardens/tree/e811cc6be72c8a802bd3265a84daf4165c177b93) in a dedicated app process. It accepts Intel HEX programs and .arduboy packages. An outer ZIP can contain one game. A zipped FX manifest stays with its program, flash data and save seed. The importer uses the first binary listed in the manifest, matching Ardens. No separate BIOS is needed.

The platform grid and library tab share game cards, per-game graphics, physical mapping and touch profiles with the other handhelds. The native display is 128 × 64. The frontend sends stereo 50 kHz PCM to AAudio and forwards D-pad, A and B events. Cards use a supplied package screenshot or a captured gameplay preview. EEPROM and FX changes are saved by Ardens under the game's data directory. More in the game menu provides a separate save-state slot. Both forms of storage are included in library backup and restore.

## Build

Use an Ardens checkout at the revision above with its pinned miniz submodule initialized. On a Linux Android NDK 28 build host with CMake and Ninja available:

```sh
git -C "$ARDENS_SRC" submodule update --init deps/miniz
bash engines/arduboy/build-native.sh "$ARDENS_SRC" build/arduboy/jniLibs
./gradlew -ParduboyProbe=true :app:assembleFdroidDebug
```

The build links Ardens statically into libabyssarduboy.so. The bridge includes the upstream core, library, bootloader and photograph notices in its license assets. Games and upstream engine sources are supplied separately.

The JNI adapter writes Ardens' native save-state stream to a temporary file and atomically replaces the game's slot. Reading the stream directly permits switching between classic and FX titles with different state sizes and also accepts existing padded Ardens states.
