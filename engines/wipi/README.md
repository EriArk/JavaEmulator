# WIPI / SKVM Android integration

AbyssME can open a carrier ZIP in a dedicated Android process in the optional ARM64 debug build. The app provides the document picker, calibrated game area, keyboard events and per-package data directory. The existing mobile adapter provides the guest runtime and framebuffer. Open **Settings → Open WIPI test package** and select a ZIP. Back opens the shared game menu. Display and Controls expose the same graphics presets, physical mappings, touch layouts and settings preview used by other platforms. The host supplies the GeneralUser GS bank for MIDI playback.

## Build the local probe

Use the pinned [mobile adapter](https://github.com/ParkJeongseop/WIPI-Emulator/tree/d632a4341229f818ececeb93267dbb5554af7e65). Its Cargo lockfile pins the [wie fork](https://github.com/ParkJeongseop/wie/tree/408fdd0ff58964172e7bca499078ca42d876111b). Both repositories declare MIT licenses. AbyssME adds the Java host and JNI declarations here. The build applies the Android clock and audio patches to a temporary copy of the pinned source. Compiled libraries and game data stay outside the repository.

On a Linux build host with Git, Rust, JDK and the Android build environment, set ANDROID_NDK_HOME to the installed NDK. The device check used NDK 28.2.13676358. From the AbyssME root run:

```sh
git clone https://github.com/ParkJeongseop/WIPI-Emulator.git build/wipi/upstream
git -C build/wipi/upstream checkout --detach d632a4341229f818ececeb93267dbb5554af7e65
rustup target add aarch64-linux-android
bash engines/wipi/build-native.sh build/wipi/upstream build/wipi/jniLibs
bash gradlew -PwipiProbe=true :app:assembleFdroidDebug :app:assembleFdroidDebugAndroidTest
```

The launcher label is **AbyssME WIPI**. The package ID is `io.github.eriark.abyssme.wipicheck.debug`. The probe uses a separate debug package and requires the native binary only when the option is enabled. The APK and test APK can be installed together using adb. The ordinary Gradle configuration does not include the bridge.

## Device checks

Use the upstream `demo/demo_wipi.zip` for the KTF Clet and the following command for the independent SKT fixture:

```sh
python3 engines/wipi/fixtures/skvm/build.py build/wipi/upstream build/wipi/slider.zip
```

The SKT slider calls the guest runtime's `com.skt.m.MathFP.min` when moving right. The generated JAR contains only the slider classes. To repeat the checks after installing the APKs:

```sh
pkg=io.github.eriark.abyssme.wipicheck.debug
dir=/sdcard/Android/data/$pkg/files
adb shell mkdir -p "$dir"
adb push build/wipi/upstream/demo/demo_wipi.zip "$dir/"
adb push build/wipi/slider.zip "$dir/"
for fixture in demo_wipi.zip slider.zip; do
    adb shell am instrument -w -e class io.github.eriark.abyssme.wipi.WipiGameTest \
        -e fixture "$fixture" "$pkg.test/androidx.test.runner.AndroidJUnitRunner"
done
```

The test watches the Android display for the orange object and requires its screen position to change after a right key event, then exits and repeats. It starts the activity with the fixture URI. The ZIP entry point recognizes KTF, LGT and SKT carrier descriptors paired with a guest JAR. Legacy Korean ZIP directory names are covered by an import regression. [Commercial game results and remaining coverage](../../docs/checks/2026-11-20-wipi-games.md). Public distribution also needs a dependency and embedded asset license review, including the font used by the core.
