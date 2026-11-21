# Handheld controls, 2026-11-21

The combined private ARM64 debug APK updated the existing AbyssME installation on a Retroid Pocket Classic running Android 14. The APK SHA-256 was `d75d7535932751496c6852ffdd06ca2c7763c73acf5f9c793cd7b785bf859b5b`. All 66 managed card paths present before the update were present afterward. The Carmageddon guest save was still present. No public release was made.

## Guest input inventory

| Host | Default touch controls and physical mapping targets |
| --- | --- |
| Dingoo | D-pad, A/B/X/Y, L/R, Select and Start. |
| GP32 | D-pad, A/B, L/R, Select and Start. |
| P/ECE, Watara Supervision, Mega Duck, Gamate, GameKing and GameKing III | D-pad, A/B, Select and Start. |
| Gizmondo | D-pad, Play, Stop, Rewind, Forward, L/R and five piano buttons for Home, Volume, Brightness, Geofence and Power. Playback actions display their symbols. |
| Cybiko | D-pad, Enter, Esc, Select, Space, Tab, Menu, Shift, Help, Del, Ins, F1 and F2. The existing keyboard handles text. |
| Atari Portfolio | Cursor keys, Enter, Space, Esc, Backspace, Tab and F1. The guest keyboard remains available. |
| Pocket PC | Stylus, directional pad, Action, Space, Esc, Backspace, Tab, Ctrl and four hardware application keys. Guest software determines the effect of an application key. |
| Palm OS | Stylus and four application buttons. The native host exposes five-way navigation. The classic runtime switches to its two page buttons. Before a ROM starts, the library editor shows the combined Palm targets. |
| Flash | Arrows, Enter, Backspace, Page Up and Page Down. Stage tapping and optional keypad input remain available. |
| webOS and Firefox OS | Touchscreen with supplementary navigation. webOS provides Enter, Space and Esc. Firefox OS provides Enter, Backspace and both soft keys. |
| WIPI/SKVM, MRE and KaiOS | Phone digits, star, pound, directional navigation, OK, soft keys and Clear/Backspace. WIPI/SKVM also exposes Call and End. |
| Java ME/Asha, Mophun and Symbian/N-Gage | Existing platform input paths. This check did not exercise their games again. |

The touch layout editor, physical mapper and runtime use the same [guest button definitions](../../app/src/main/java/ru/playsoftware/j2meloader/input/PlatformControls.java). A platform button can be reassigned in the touch editor and additional buttons can be placed separately in portrait and landscape. The physical mapper offers a keypad page for alternate or existing numeric assignments. Shared physical profiles retain their stored key codes. The global editor lets the user select which platform's button names to display.

## Verification

- The Android instrumentation run passed 33 tests across PlatformControlsTest, TouchDeckTest, TouchMappingTest, ControllerMapperTest and ExtraTouchButtonTest. The [new cases](../../app/src/androidTest/java/ru/playsoftware/j2meloader/input/PlatformControlsTest.java) hit-test each declared native key in four viewport sizes, compare the press and release from touch and controller mapping, and check old numeric bindings and alternate layouts.
- Carmageddon responded to directional input injected through the connected Xbox controller. The on-screen Play symbol activated the selected Quit confirmation. In a separate settings check, Xbox A was remapped from Play to Forward, saved through the in-game preview and verified after a cold launch. The temporary per-game configuration was removed to restore the original bindings.
- Crazy Jack reached its graphical menu on GP32. The overlay displayed B/A, L/R and Select/Start. This run did not establish gameplay beyond that menu.
- Raw screenshots, install inventory and the instrumentation log are local under `build/checks/platform-controls/`. The input checks used Android touch injection and the connected controller's event device. Hand-operated feel and the effect of every system key in guest software remain unverified. Audio was outside this input check.
