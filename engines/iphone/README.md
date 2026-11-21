# Early iPhone OS on Android ARM64

The optional debug module imports an IPA into a managed library card. It runs its ARM executable inside the combined app using touchHLE and SDL2. No system firmware is required. Compatibility depends on the game's CPU, frameworks and device requirements. The guest sandbox is under `iphone/data/<card SHA-256>`.

## Preparing the module

Use an external checkout of [touchHLE v0.3.0](https://github.com/touchHLE/touchHLE/releases/tag/v0.3.0) at `8eb3418b3343bc1e3d1a330b55cb1d708a015721` with its recursive submodules. Use the Android AArch64 ZIP from that release. The patches in this directory target that revision and its pinned SDL submodule. Keep the checkout, generated assets, game files and native libraries outside source control.

1. Set `frontend` to this directory's absolute path and switch to the touchHLE checkout. Run `git apply "$frontend/arguments.patch"`, `git -C vendor/SDL apply "$frontend/sdl-host.patch"` and `cp "$frontend/android_host.rs" src/abyss_android.rs`.
2. Provide Boost headers at `vendor/boost/boost` as described in upstream's build instructions. Extract `lib/arm64-v8a/libSDL2.so` from the official APK into `android-sdl/libSDL2.so` in the checkout.
3. Set `ANDROID_NDK_HOME` (tested with 28.2.13676358). Install the Rust target `aarch64-linux-android` and upstream's CMake and native build prerequisites. Run `sh "$frontend/build-core.sh"` from the checkout.
4. Run `python3 "$frontend/prepare.py" "$PWD" /path/to/official-Android-AArch64.zip /path/to/AbyssME`. This stages the native core, matching SDL Java and binary, guest libraries, fonts, upstream configuration and notices under the project's ignored build directory.
5. Add `-PiphoneProbe=true` to the combined ARM64 debug build. The combined app supplies the NDK C++ shared runtime.

The SDL host presents a guest surface. The common handheld UI composites its frames, maps touches into the game area and handles overlays, controller mapping and display settings. The D-pad moves a cursor and the Touch action holds/releases a finger. Device acceptance is recorded in the [project check](../../docs/checks/2026-11-21-iphone.md).
