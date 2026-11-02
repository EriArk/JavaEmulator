# Recovering a Rejected Symbian Screen Mode

Date: 2026-11-02. Public source on main, unreleased. A separate combined Debug
APK was installed on Retroid; the release package and public APKs were unchanged.

## User Flow

When native preparation rejects a non-Original per-game screen mode, the failed
native instance is stopped and a centered game-named confirmation offers
**Try Original** or **Close**. It uses the existing console styling and translated
copy in all eight app languages. Close does not change the confirmed settings.

Try Original reloads the confirmed game profile and changes only the emulated
screen preset in an in-memory preview. Failed/unclaimed trial settings are not
carried forward. Other confirmed display, input, speed and audio settings remain
intact. Keep explicitly saves the recovered profile. Undo returns to the library
without saving, rather than restarting the configuration already known to fail.

The recovery preview has no automatic timeout. Ordinary settings previews retain
their existing 20-second rollback, pause/resume behavior and controls. Recovery
cannot accidentally restart the rejected mode when its preview timer expires.
Buttons reject busy/closing/running or changed-phone contexts; no retry occurs
without user action, and Original preparation failures use the normal error path.

## Verification

- Combined `assembleFdroidDebug` and `assembleFdroidDebugAndroidTest` pass;
  `adb install -r -d` succeeds for the separate Debug package.
- Retroid Pocket Classic, Android 14: **32 focused Android tests pass**, with no
  skips, across game settings, phone screen capabilities, display controls and
  the shared preview bar. New coverage checks unchanged confirmed profile bytes,
  preservation of unrelated settings/a save sentinel, failed-trial removal,
  explicit Keep/Undo, recovery without timeout and ordinary trial expiry.
- The opt-in real UI test deliberately supplies an unsupported EKA1 screen
  preset to the installed Snakes fixture. It verifies Close leaves profile bytes
  unchanged, Try Original starts a preview, waiting 21 seconds does not roll
  back, Undo returns to the library without another recovery prompt, and a fresh
  explicit recovery can Keep Original while retaining the frame limit and other
  display settings. The pre-test profile is restored in `finally`.
- Dark-theme error and recovered-session captures are inspected separately.
  The recovered image shows the N-Gage Arena startup screen, not full-game
  acceptance. Other translations compile but were not individually rendered.

Ignored local evidence: `build/screen-recovery-final-build.log`,
`build/screen-recovery-settings-tests.log`, `build/screen-recovery-retroid-ui.log`,
`build/screen-recovery-error.png`, `build/screen-recovery-preview.png`.

## Limits

This handles a caught native screen-preparation failure, not arbitrary black
screens, OS crashes, hangs, or failures after the guest has started. It does not
detect that a game has reached gameplay. Source-phone/save-directory hashes were
not audited in this UI check; preservation assertions concern the settings file
and synthetic save sentinel. No firmware, game files or private captures are
published. #50 remains open for broader resolution/widescreen support.
