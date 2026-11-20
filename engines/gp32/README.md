# GamePark GP32 on Android

Choose the GamePark GP32 sticker in the platform launcher, then use **Add game** in its library tab.

- For a retail SmartMedia game, import the console BIOS (`.bin`) and the card image (`.smc`). Both can be imported directly from single-image ZIPs. Open the game card, then select **Play** in the stock BIOS. The most recently imported firmware becomes the default. Open the firmware's own library card to boot it independently.
- Homebrew can be imported as an FXE executable or a GP Manager FPK package. A ZIP with a single FXE and supporting files retains the files with their guest paths. Direct boot without a BIOS works with some homebrew. An extracted retail game directory alone lacks the original card layout.
- The touch deck, physical controller mappings, graphics profiles and game area use the common handheld settings. The in-game menu has **Save state** and **Load state**. When the guest writes to SmartMedia, the emulator keeps a writable card for that game and preserves the imported image.

## Optional ARM64 debug build

On the Android builder with the NDK installed, run:

```sh
git clone https://github.com/gameblabla/gp32emu.git /path/to/gp32emu
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/28.2.13676358"
bash engines/gp32/build-native.sh /path/to/gp32emu
bash gradlew -Pgp32Probe=true :app:assembleFdroidDebug
```

The build script extracts the pinned revision into the local build directory and links the portable ARM interpreter and libretro frontend to the Android handheld host. The user supplies firmware and games. See the [device checks](../../docs/checks/2026-11-20-gp32.md) and [licensing record](../../docs/LICENSING.md).
