# Visual polish - local debug captures, 2026-10-18

These are unedited screenshots from the working development build, not mockups
and not screenshots of the public 0.4.0 Beta 1 APK. No new release accompanies
this folder. English UI; existing mixed test libraries were left intact.

Retroid Pocket Classic, Android 14, isolated Symbian-enabled debug package:

| Screenshot | Contents |
| --- | --- |
| [Retro gallery](retroid-retro-gallery-dark.png) | Dark Retro LCD, Gallery mode, controller focus and hints |
| [Classic grid](retroid-classic-grid-dark.png) | Dark Classic, Grid mode |
| [Contrast list](retroid-contrast-list-dark.png) | Dark High contrast, compact List mode |
| [Light Retro grid](retroid-retro-grid-light.png) | Light Retro LCD, large game icons |
| [Touch editor](retroid-touch-editor.png) | Editor preview, not a running game's picture |
| [Applied touch layout](retroid-mophun-touch-applied.png) | Mophun framebuffer and a saved 80% Phone keypad in portrait |
| [Gamepad](retroid-mophun-gamepad.png) | Mophun framebuffer with landscape Gamepad controls |

Android 15 emulator:

- [Sudoku J2ME with the applied keypad](emulator-java-touch-applied.png),
  1080x2340 phone viewport.
- [Position editor](emulator-java-editor-position.png), per-game detached draft.
- [Square library](emulator-square-large-text-grid.png) and
  [square editor](emulator-square-large-text-editor.png), 720x720, density 240,
  font scale 1.5. The editor settings scroll below the preview in this layout.

The Mophun fixture was launched through the locally indexed
`/sdcard/Download/DeepAbyss14.mpn` path. Its displayed picture is retained exactly;
the filename alone is not evidence of the game's identity or compatibility.
No game binaries are included. These are UI/settings workflow checks, not
full-game, audio or save-game acceptance. Production package/data was not replaced.

[Implementation and verification](../../../docs/checks/2026-10-18-visual-polish.md).
