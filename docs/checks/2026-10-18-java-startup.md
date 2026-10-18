# Java startup visibility regression

Date: 2026-10-18. Development verification before the beta.5 release build.

## Reproduction and shared cause

The community reported Age of Empires II Deluxe 02.00.35 (240x320) and 02.00.39
(320x240) working in an older Phone Debug build but not beta.4 Phone, on Android 16.
The supplied collection contains earlier Age of Empires 1.x versions, not Deluxe.
A separate local Deluxe 02.00.35 fixture was obtained and its manifest checked.
SHA-256: `56b2acdc98884878d19e9c237a823bbd67e58485d90360c8914ffea0dcf3c1c9`.
It is not committed or included in any distribution.

The failure reproduced on the Android 15 emulator before the fix: an empty game
activity, with the guest startup thread continuously looping on Canvas.isShown().
Bytecode inspection confirmed that the loop runs inside startApp(). Commit
`e539e8ed` had made display activation wait for startApp() to return, introducing
a circular wait. This is not a Debug-versus-Release-only or Android-16-only defect.
The older upstream [issue #201](https://github.com/nikita36078/J2ME-Loader/issues/201)
also concerns this game/version, but does not by itself establish the cause here.

## Repair

- Publish display visibility after MIDlet construction and before invoking
  startApp(), allowing selected screens, queued lifecycle callbacks and offscreen
  frames to progress while the guest is still initializing.
- Keep the previous fixes that decouple guest rendering from Android Surface
  creation. No game name, hash, patched JAR, startup delay or new setting is used.
- Withdraw visibility immediately on host pause/destroy, even if the guest is
  still inside startApp(). Resume can restore an in-flight startup's visibility.
- Ignore queued start requests if the host is no longer requesting foreground.
- Withdraw the display after a refused/failed startup; do not undo notifyPaused().

## Verification

- Four initial real-handler regression cases failed against the old implementation.
  The final suite adds six MidletStartupTest cases: visibility/painting during
  startup, immediate-event mode, refused startup, self-pause, background/return
  during blocked startup, and a stale queued start after host pause.
- Android 15 x86_64 Phone emulator: **27 passed**, six startup plus 21 existing
  Canvas lifecycle/resize/GLES cases. Local log: `build/aoe-startup-final.txt`.
- Physical Retroid Pocket Classic, Android 14, combined Phone debug: **36 passed**,
  including the initial four startup cases, 21 Canvas cases, GameChrome and TouchDeck.
  This preceded addition of the last two test cases, not a runtime-code change.
  Local log: `build/aoe-retroid-phone-tests.txt`.
- Base Handheld/Phone debug builds and lint completed, with zero lint errors;
  29 JVM tests passed per variant. Both combined debug variants also built.
- Deluxe 02.00.35 reached its menu and generated a playable Quick play map on both
  the emulator and Retroid. On Retroid, touch navigation moved the map selection,
  the common menu paused/resumed the game, and confirmed exit returned to library.
- Real Mophun Phone UI test on Retroid passed Dark/Light controls with light app
  appearance, both layouts/orientations, relaunch persistence and shortcut auto-hide.
  Snakes passed the native shared-menu/display/mapper/Phone-controls test with a
  captured guest-picture check. These extend the previous component-only checks.

Local logs: `aoe-fixed-base-build.log`, `aoe-fixed-combined-build.log`,
`aoe-startup-before.txt`, `aoe-midlet-before.txt`, `beta5-phone-mophun-ui.txt`,
`beta5-phone-snakes-ui.txt` under ignored `build/`.

Android 16, Deluxe 02.00.39, full campaigns, audio and save round trips were not
verified. Successful startup is not universal compatibility. Earlier Ghost Recon
and native Asphalt limitations remain. Release APK verification is recorded
separately in the beta.5 release checks, not inferred from these debug runs.
