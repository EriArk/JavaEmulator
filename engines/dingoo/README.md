# Dingoo A320 and Gemei A330 on Android

The optional ARM64 debug host uses the [DingooEmu 0.3.0 Android libretro core](https://github.com/AloysHF/DingooEmu/releases/tag/v0.3.0), source revision `f57c9339e6021cd37c4bd694c14b0813a9b5ee72`. The same library runs two different guest architectures. Choose **Dingoo A320** for a native MIPS `.app` or **Gemei A330** for an ARM `.cc`, `.c2s` or `.c3s`. Use **Add game** with the executable or a ZIP containing the executable and its companion files. Keep the original directory layout inside the ZIP so the game can find its assets. The importer checks the CCDL descriptors, load address and program range.

Each card uses a content hash. Reimporting the same file preserves its identity, per-game mapping, display settings and guest save directory. Removing a card removes its executable and artwork. Saves live under `dingoo/data/<hash>`, independently of the managed package. Use **Game menu → More → Save state** to replace the game’s checkpoint, then **Load state** to resume it, including after a cold restart. The host writes the core’s compressed state to a temporary file and atomically replaces the slot when the write succeeds. Each game also retains the files it writes through its guest filesystem.

## Build

Use the existing Android build environment on a Linux builder. Extract the upstream Android release archive outside tracked source, then run:

```sh
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/28.2.13676358"
bash engines/dingoo/build-native.sh /path/to/arm64-v8a/dingooemu_libretro_android.so
bash gradlew -PdingooProbe=true :app:assembleFdroidDebug :app:assembleFdroidDebugAndroidTest
```

The script verifies the upstream binary hash before linking the JNI frontend. The build includes the [upstream BSD license](bridge/src/main/assets/licenses/DingooEmu-BSD.txt) and [JIT dependency notices](bridge/src/main/assets/licenses/DingooEmu-dependencies.md). The debug application ID is `io.github.eriark.abyssme.dingoocheck.debug`. The ordinary APKs do not contain the core.

## Controls and presentation

The host presents the 320 × 240 RGB565 framebuffer, streams 22,050 Hz stereo PCM through AAudio, and pauses while the game menu or another activity has focus. The shared game area, display presets, physical mapping, touch layout and settings preview are the same ones used by WIPI. The native arrows profile uses the controller’s face buttons, shoulders, Start and Select. The touch deck provides these guest actions:

| Guest controls | Touch deck |
| --- | --- |
| Directions | D-pad or 2 / 4 / 6 / 8 |
| A / B | OK / 0 |
| X / Y | 1 / 3 |
| L / R | * / # |
| Select / Start | 7 / 9 |

On Retroid, an import regression checks the package target, rejects an incorrect extension, reimports a fixture and verifies its single managed copy. Push a local fixture to the debug app’s external files directory and run:

```sh
pkg=io.github.eriark.abyssme.dingoocheck.debug
adb shell am instrument -w -e dingooFixture Digger.app \
  -e class io.github.eriark.abyssme.dingoo.DingooImportTest \
  "$pkg.test/androidx.test.runner.AndroidJUnitRunner"
```

[Device results, game editions and current limits](../../docs/checks/2026-11-20-dingoo-gemei.md).
