# Pocket PC

The optional ARM64 debug module embeds PocketHLE 0.3.2 in a private process. The core implements Windows CE APIs and runs native ARM programs with Unicorn. No Windows CE system ROM is required.

## Build

Use an Android NDK 28.2 Linux builder with Rust and the `aarch64-linux-android` standard library installed. From a clean PocketHLE checkout:

```sh
export ABYSSME_ROOT=/path/to/AbyssME
export LIBXMP_SOURCE=/path/to/libxmp
git -C "$LIBXMP_SOURCE" checkout 5c63c2f7809dc6f17342df738a3dde5ca65221c9
git checkout 25d13039d8f0ad3755b441215745a53e382edb1b
git apply "$ABYSSME_ROOT/engines/pocketpc/session-clock.patch"
export ANDROID_NDK_HOME=/path/to/android-ndk
cp "$ABYSSME_ROOT/engines/pocketpc/fmod.rs" crates/pocket-winceapi/src/
sh "$ABYSSME_ROOT/engines/pocketpc/build-core.sh"
```

The script stages the core and notices under the ignored `build/pocketpc` directory. Build the Android app with `-PpocketpcProbe=true`. The mixer uses [libxmp-lite](https://github.com/libxmp/libxmp/tree/5c63c2f7809dc6f17342df738a3dde5ca65221c9) for tracker modules. The patch connects session pause, speed and additional PDA screen sizes to the underlying Android worker. [Pinned PocketHLE source](https://github.com/j92580498-max/PocketHLE/tree/25d13039d8f0ad3755b441215745a53e382edb1b).

## Using the platform

Open a Pocket PC CAB, a native Windows CE ARM EXE or a ZIP containing the program with its original files. InstallShield setup programs made for desktop Windows must be unpacked first. Select the main game executable when a package has several. The selection is remembered per game and can be changed under **More → Choose Pocket PC program**.

Use the shared display panel to rotate games that draw sideways. Rotation also transforms stylus coordinates. **More → Guest screen size** changes the resolution the guest sees and restarts it. The virtual keypad and controller mapping share the regular handheld settings. The default directional and Fire actions send the Windows cursor and Enter keys. Local game data, executable choices and profiles participate in the existing library backup. Game progress uses the game's own save support.
