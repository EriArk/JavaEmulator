# Shared Game Launch Recovery

Date: 2026-11-02. Public source on main, unreleased. A separate combined Debug
build was installed on Retroid Pocket Classic (Android 14). No public APK,
release package, firmware or bundled games were changed or published.

## Behavior

Caught game-launch failures use a centered, controller/touch-accessible console
dialog with game name, technical reason, Retry and Exit game. Back follows Exit;
outside touches do not dismiss the error into a broken session. One dialog can
dispatch only one decision. Reasons are length-bounded, with actions before
details in the scrollable panel. New explanatory copy is translated into the
eight supported app languages; technical engine messages remain diagnostic text.

- Java: errors loading the MIDlet list, constructing the MIDlet or executing
  startApp reach the common screen. Failed guest lifecycle instances cannot
  resume when focus returns. Retry hands off to the existing separate restart
  process, which waits for the old VM binder to die before starting another VM.
  Failed previews are discarded rather than saved. The toolbar is hidden even
  when no Displayable was created. Network sessions retain return-to-room behavior.
- Mophun: initialization exceptions and worker-reported failures reach the
  same screen. Retry stops/joins the worker before Activity recreation and
  loads confirmed settings. Early failures can exit safely without a session.
- Symbian: caught play/preparation failures stop the native session and offer
  Retry only after the worker finishes. Rejected launchGame/session UI setup
  takes the stopped-session recovery path. A non-Original screen-mode rejection
  still offers the distinct Try Original preview, not an automatic generic retry.

No retry is automatic. Exit follows the host's normal destination, including
external callers; it does not force a library screen over an external launcher.

## Verification

Combined Debug and AndroidTest assembly pass. Separate Debug and test APKs were
installed with `adb install -r -d`. On Retroid:

- **22 checks pass:** MidletStartupTest (8), SettingsPreviewBarTest (9),
  GameFailureDialogTest (3), MophunLaunchFailureTest (1), LaunchRetryUiTest (1).
- Java fixture: deliberately missing converted code reaches the actual error
  screen in the separate MIDlet process. Retry obtains a different PID; Exit
  ends it. Confirmed profile bytes and an isolated save sentinel are unchanged.
  Constructor/startApp failure and normal lifecycle behavior have focused tests.
- Mophun fixture: an invalid executable fails in the actual worker. Retry replaces
  that worker only after it finishes, drops a 45-FPS draft in favor of confirmed
  30 FPS, and preserves the saved configuration and save sentinel. Back exits.
- **Two opt-in native UI checks pass:** a deliberately malformed one-launch
  draft offers generic Retry, then starts the installed Snakes fixture without
  saving a preview; the screenshot shows its N-Gage startup screen. The existing
  EKA1 rejected-mode Close/Try Original/wait/Undo/recover/Keep regression also
  passes and restores the previous profile in finally.
- Dark-theme Retroid captures for Java, Mophun and Symbian errors were visually
  inspected. The Java capture exposed an old toolbar before the first frame;
  this was fixed and recaptured. Locale variants compile but were not all rendered.

An initial Java UI assertion expected accessibility click acknowledgement when
Exit immediately kills its process. The test now checks the actual process exit;
the complete final run passes. Earlier build/test attempts are not counted as passes.

Ignored local evidence:

- `build/launch-failure-final-build.log`
- `build/launch-failure-retroid-final-tests.log`
- `build/launch-failure-retroid-final-native.log`
- `build/launch-failure-java.png`
- `build/launch-failure-mophun.png`
- `build/launch-failure-symbian.png`
- `build/launch-failure-symbian-retried.png`

## Limits

This does not recover an Android/native process crash, a hung native call,
arbitrary black screens, missing phone-system preparation, import failures or
all failures occurring later inside a guest game. Generic Retry does not repair
corrupt installed game files or invalid confirmed settings; the reason remains
visible if it fails again. Native launchGame rejection is wired but was not
independently forced in the device test. Network regression and emulator runs
were not repeated in this batch. No full-game, audio, arbitrary-resolution or
widescreen acceptance is implied, and #50 remains open. Preservation assertions
cover profile bytes and synthetic save sentinels, not a full phone/save-directory
hash audit. Test fixtures are disposable and are not shipped as games.
