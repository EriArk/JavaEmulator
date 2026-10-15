# Phone shortcuts and tactile feedback

Date: **2026-10-15**. Source follows `e539e8ed`. Reports:
[#27](https://github.com/EriArk/JavaEmulator/issues/27) and
[#28](https://github.com/EriArk/JavaEmulator/issues/28).
These are unreleased source changes and installed debug checks, not a new APK
release. GitHub Releases was rechecked today: `v0.3.0-beta.1`, published
2026-10-14, remains the newest prerelease. Public asset hashes were not rechecked.

## User-visible changes

- **Game menu > More > On-screen shortcuts** controls the floating buttons.
  Java Phone offers independent Display and Screenshot switches. The same
  choices are in **Settings > Playing > On-screen shortcuts**. Existing
  screenshot preferences are reused; both buttons remain visible by default
  on Phone. Handheld's default presentation is unchanged.
- Menu remains the Phone escape/access path. Hiding a shortcut does not remove
  Display or Screenshot from the game menu. Hidden views are `GONE`, not
  transparent touch targets; no viewport or immersive-mode policy was changed.
- Java refreshes all shortcut visibility together during initialization,
  resume, quick-overlay/mapper transitions and display changes. Mophun and
  Symbian expose the same shortcuts page with Screenshot only: those hosts
  do not have a floating Display button. Symbian now rereads the preference
  when returning to its session.
- **Controls > Tactile feedback** is available for Phone and Gamepad, and Java's
  Legacy layout. It uses the existing per-game `vkFeedback` setting. Changes
  follow the existing **Try changes / Keep / Undo** workflow, not a new global
  preference or a silent profile reset.
- Phone/Gamepad request one pulse for a new virtual key. A diagonal emits one
  request, not one for each direction. Moving to another unheld key and the
  Gamepad number-panel switch also give feedback. Holding, repeating, releasing,
  cancellation, relayout and a second finger on an already-held key do not.
- Java, Mophun and Symbian connect the shared deck to Android's virtual-key
  haptic API. Legacy Java uses the same platform policy. It honors Android's
  touch-feedback settings and does not force a custom long vibration. Hardware
  controller input does not acquire a new vibration side effect.
- The existing Playing > Vibration label is now **Game vibration**, to separate
  guest-requested vibration from touch-key feedback. Its preference and MIDlet
  behavior are unchanged. UI feedback requires per-game opt-in and Android
  touch feedback; the Game vibration switch is not its master switch.

Platform reference: [Android haptic feedback](https://developer.android.com/develop/ui/views/haptics/haptic-feedback).

## Verification

Eight new instrumented cases cover shared pulse policy, actual Java keyboard
callback wiring, provider independence, available shortcut controls and the
per-game draft/preview boundary. Extended Mophun routing checks count pulses;
Symbian session fixtures check screenshot visibility after resume and a
reachable Menu. Existing layout, controller, theme, screenshot storage and
native touch-ownership tests were also run.

| Installed target | Result |
| --- | --- |
| Retroid Pocket Classic, Android 14, combined Phone debug | 47 passed |
| Retroid Pocket Classic, Android 14, combined Handheld debug | 45 passed, 2 Phone-only skips |
| Android 15 x86_64 emulator, ordinary Phone debug | 43 passed |
| Combined JVM checks, each variant | 25 passed per variant |

Manual observations:

- Retroid Java Phone: hid both buttons in More, dismissed/reopened menus,
  returned from the library/background, and retained Menu access. Settings
  changed without resetting the game viewport or controls.
- Retroid: enabled feedback through Controls > Try changes. A screen-key press
  produced a completed `Usage=TOUCH`, `effect=CLICK` vibration attributed to the
  Phone debug package (46 ms including scheduling). An injected directional
  key event did not add a pulse. Undo restored feedback-off; another screen-key
  press did not add a pulse. This is platform evidence, not a claim that a human
  felt or approved the sensation, nor a physical controller-press test.
- Phone emulator: verified both switches, menu-only gameplay, Display access
  through Menu, landscape preview and return to portrait, and a fresh game
  launch retaining hidden shortcuts. Native UI inspection found only Menu in
  the shortcut area when both switches were off. The library's nested Playing
  > On-screen shortcuts page displayed those same off values and restored them
  to on through the main-process provider.
- Retroid and emulator shortcut preferences were restored to their initial values. Game
  feedback/display trials were undone. No app data, games or saves were cleared.

Limits: perceived haptic strength on a real phone remains unaccepted; no
vibration is expected from the emulator. Legacy hardware sensation and a
manual LCDUI Form round trip were not rechecked. Mophun/Symbian results here
are host/input fixtures, not fresh complete-game sessions. No release APKs
were distributed. GitHub Actions remains disabled.

## Reproduction and artifacts

Combined build:

```shell
./gradlew -PsymbianExperiment=true -PsymbianSourceDir=build/symbian-source-check :app:assemblePhoneDebug :app:assembleFdroidDebug :app:assemblePhoneDebugAndroidTest :app:assembleFdroidDebugAndroidTest
./gradlew -PsymbianExperiment=true -PsymbianSourceDir=build/symbian-source-check :app:testPhoneDebugUnitTest :app:testFdroidDebugUnitTest
```

Ordinary emulator build omits the two Symbian properties. Instrumented suite:
`TouchFeedbackTest`, `TouchDeckTest`, `ScreenshotTest`, `GameShortcutsTest`,
`GamePresetsUiTest`, `MophunTouchTest`, `ThemeContrastTest`, `GameMenuLayoutTest`,
`ControllerMapperTest`, plus `SymbianTouchTest` on combined builds.

Local-only logs: `build/shortcuts-{retroid-phone,retroid-handheld,avd}-tests.log`,
`build/shortcuts-retroid-vibrator-{on,key,off}.txt`. The emulator's ADB connection
stalled after its initial run; it was restarted without wiping data and the
final 43-case suite and manual checks passed afterward.

Installed APK SHA-256 (local debug artifacts, not downloads):

| Variant | SHA-256 |
| --- | --- |
| Combined Phone | `daf88090e4024ef5266381b73eedee5594afeb848f32bb1bf3fe0d068984d6e1` |
| Combined Handheld | `a8608289dd6edfb4433d2344334058f93f8b7c51f0a41402deaa6cfeb7feba2d` |
| Ordinary Phone | `55363649db867e2587b95a1839cd3891abe29f384c8c4f5346c8974ef47bfb04` |

## Screenshots

Actual app captures, not concepts. The Retroid uses Dark; the Phone emulator
uses Light. Game pixels are not UI artwork or bundled games.

- [Retroid shortcut switches](../screenshots/2026-10-15-shortcuts/retroid-shortcuts.png)
- [Retroid Controls with tactile feedback](../screenshots/2026-10-15-shortcuts/retroid-controls.png)
- [Phone portrait shortcut switches, Light](../screenshots/2026-10-15-shortcuts/phone-shortcuts-light.png)
- [Phone portrait with only Menu visible](../screenshots/2026-10-15-shortcuts/phone-menu-only.png)
