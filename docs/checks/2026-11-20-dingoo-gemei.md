# Dingoo and Gemei on Retroid Pocket Classic

Checked on Android 14 with a local ARM64 debug build. Both platforms appeared in the [root grid](2026-11-20-dingoo-platforms.png) and opened their own tab in the existing library. Native packages were imported through the library provider and launched by selecting their cards. Commercial content, raw game captures and test APKs stay local. The core, input mapping and build instructions are in the [Android host notes](../../engines/dingoo/README.md).

| Platform and fixture | Image, gameplay and input | Audio and settings |
| --- | --- | --- |
| Dingoo A320, Digger native OS rev. 6, MIPS32 | Reached the playable first maze. The virtual controls started play from the title and controller input reached the maze. | Nine-second Android playback capture, mean −48.2 dBFS, peak −33.3 dBFS. Set touch controls to Always and kept CRT. Both settings persisted after cold launch. |
| Gemei A330, 7 Days Salvation, Chinese 2010-11-12 corrected C3S, ARM32 | Reached the title, save-slot selection and the opening room with the player character. Returning to the game resumed the scene after the settings preview. | Fourteen-second playback capture, mean −31.2 dBFS, peak −8.4 dBFS. Kept the touch deck and a per-game B → Down mapping. On cold relaunch the mapped button selected the next slot. The game wrote its own config.sdt in its save directory. |
| Gemei A330, Sword and Fairy and Doom for A330, homebrew CC | Both executables passed structural import checks and displayed black on the device. Each also produced a solid frame after 600 ticks in the upstream 0.3.0 standalone emulator on Linux. An additional run with the SDK Platform directory still produced a solid frame at 2,400 ticks. | Gameplay and audio remain unverified for these two files. The same Sword and Fairy fixture was also black with the 2026-11-20 upstream nightly on Linux. |

Controller checks used events injected through the connected Xbox Wireless Controller input device. Touch checks used Android taps and holds on the game deck. Audio levels came from Android playback capture. Hand-operated button feel, speaker quality, complete games, restoration through the games’ own save-slot interfaces and a separate C2S title remain unverified.

**Cold restart and save states**

On both platforms, **Game menu → More → Save state** created a per-game snapshot. After force-stopping the debug application, each game booted from its own title sequence. **Load state** returned Digger to its playable maze and 7 Days Salvation to its first 3D room with the character at the desk. Digger’s snapshot occupied 480,942 bytes and the Gemei snapshot was 5.3 MiB. The game exit action returned to the Gemei library. Private evidence includes `state-title.png`, `state-restored.png`, `seven-state-cold.png` and `seven-state-restored.png`.

The library tab suite passed five checks. The shared WIPI regression suite passed four checks, including the SKVM slider after relaunch. The two Dingoo import checks passed with each of the MIPS and ARM fixtures.

**Fixture provenance**

- [Digger native rev. 6, original project download](https://storage.googleapis.com/google-code-archive-downloads/v2/code.google.com/dingoo-digger/digger_rev6_dingoo.zip). Extracted file SHA-256 `31ff7d461c81b37ba22926783929b17a57db2771e90409641fa1a2e533982d32`.
- [7 Days Salvation, preserved Gemei builds](https://github.com/minexew/Soft3D-RE/tree/master/Releases/Gemei%20A330). Used `7days_b20101112-fix.c3s`, SHA-256 `fab3690345d4faf85f1a6da7837cc68389f175a0c9939990061fb383118fb582`.
- [MP4SDK project downloads](https://code.google.com/archive/p/mp4sdk/downloads). Sword and Fairy SHA-256 `10c53770cbb3d3d49d05685f488e58d6fdc829460fa445e90e1b48f8184c9a94`. Doom SHA-256 `a9b18f514dd9b4ea0c00d0d1cfe10b676cd45f8c97676c18905ba87291e819ab`.

Private device evidence is under `build/checks/dingoo/`, including `digger-touch-right.png`, `digger-physical.png`, `digger.mka`, `seven-room.png`, `slots-b.png` and `seven.mka`.
