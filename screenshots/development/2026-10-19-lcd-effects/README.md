# LCD Effects: Development Captures

Historical first implementation. The grid was subsequently corrected; use the
[new captures](../2026-10-19-lcd-export/README.md) for the current development appearance.

Captured from local debug builds after 0.4.0 Beta 2. These effects are not in the
published Beta 2 APK. Images are unretouched, not mockups. Games were supplied
separately; screenshots do not imply full compatibility or completion.

| Platform | Off | LCD Soft | LCD Classic |
| --- | --- | --- | --- |
| Java Sudoku J2ME, Android 15 emulator | [Off](java-off.png) | [Soft](java-soft.png) | [Classic](java-classic.png) |
| Mophun menu, Retroid / Android 14 | [Off](mophun-off.png) | [Soft](mophun-soft.png) | [Classic](mophun-classic.png) |
| Native Snakes startup, Retroid / Android 14 | [Off](native-off.png) | [Soft](native-soft.png) | [Classic](native-classic.png) |

[Display settings and intensity](display-settings.png).

The native startup advances between captures; do not present those as the same
frame. The Mophun fixture's filename is not a verified title identification.
The emulator retains its existing light-theme preferences, while Retroid uses
dark menus. Touch controls are not covered by the LCD mask.

[Implementation and checks](../../../docs/checks/2026-10-19-lcd-effects.md).
