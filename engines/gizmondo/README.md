# Gizmondo

The private ARM64 debug module loads PocketHLE Reborn v0.4.9 in its own Android process. It shares the AbyssME catalog, display, controller profiles and PCM output with the other handhelds. The Pocket PC module keeps its separately pinned core.

## Build

Use the [upstream source at d243f1da](https://github.com/guizmox/PocketHLE/tree/d243f1da20c5e4b382b2b2cc84af5d832f9b60ba) and its vendored Unicorn fork. From a clean checkout of that revision, set `ANDROID_NDK_HOME` to Android NDK r28c and `ABYSSME_ROOT` to the AbyssME checkout. Install the Rust `aarch64-linux-android` target. Run:

```sh
git apply "$ABYSSME_ROOT/engines/gizmondo/host.patch"
sh "$ABYSSME_ROOT/engines/gizmondo/build-core.sh"
```

Prepare the [Pocket PC module](../pocketpc/README.md) and build the debug variant with `-PgizmondoProbe=true`. The native libraries and packaging notices are staged under the ignored build directory. The test configuration enables Unicorn and the native graphics path. The optional static FFmpeg video decoder is disabled. Camera, Bluetooth, GPS, vibration and Internet service callbacks are outside this adapter's current scope.

## Import and controls

Open a ZIP of the game SD card with its executable and resources. A Windows CE ARM executable accompanied by a Gizmondo catalogue directory is routed to Gizmondo. For an unmarked ZIP, choose it through the Gizmondo tab. The original directory layout and the game's own save and flash files are included in handheld backup. Multi-game cards recognized by the importer present their titles at launch.

Use the D-pad to move and Fire or 5 to confirm. Soft Left or 1 sends A, Soft Right or 3 sends B, and Star or 7 sends C. Clear sends Escape and Pound sends Tab. The five piano keys are available on 2, 4, 6, 8 and 0. Map a controller in the shared controls panel. The screen uses the original 320 × 240 landscape resolution.
