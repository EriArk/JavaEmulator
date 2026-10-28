# Per-Game Touch Gamepad Mapping - 2026-10-28

## Availability

Development source on `main`, based on `f7a59cfd`. Local combined Debug APK only;
the signed 0.6.0-beta.1-r1 release, tags and public assets were not changed.
No game payloads, firmware, credentials or new binary dependencies are published.
Release asset hashes were not reverified during this implementation batch.

## User Path and Behavior

`Game menu > Controls > Edit touch layout > Buttons`.
Select Gamepad in Layout if using Phone. Select a drawn gameplay button or its
named selector, then choose a key from the phone-key picker. Native arrows and
Numpad 2/4/6/8 + 5 are starting templates; individual edits become Custom.
Use the header check, then Try changes and Keep (or Undo) in the normal flow.

- Stable semantic IDs identify directions, fire, soft keys and four action
  positions. One per-game map covers both orientations; geometry stays separate.
- Default diagonal cells retain two directions. With a numeric template they
  follow those directions (for example 2+4); an explicit override can send one
  digit instead. Duplicate target codes are reference-counted, not released
  while another finger still owns them.
- Keys capture resolved guest codes when the layout is rebuilt. Held keys retain
  their original press/repeat/release identity until cancellation/rebuild;
  applying or undoing a runtime draft cancels input before rebuilding.
- Rendering uses immutable layout identity, not remapped codes. Reassigning
  fire to a digit does not destroy the cross shape; labels show sent keys.
- Phone layout and the Gamepad 123 bank remain literal. Changing layout does
  not discard the Gamepad assignments. 123/Actions is never a guest mapping.
- Only canonical arrows/fire/soft keys, 0..9, star and hash are accepted. Invalid
  overrides fall back to defaults. Java vendor conversion remains at Canvas's
  existing boundary, not applied a second time by the touch mapper.
- Reset button mappings and Reset layout positions are independent. Physical
  controller profiles, saves, opacity and legacy keyboard behavior are preserved.
- New UI labels are English resources. This is not the #43 localization rollout.

## Verification

Workspace: `D:\Projects\JavaGameEmu`, branch `main`.
Device: Retroid Pocket Classic, Android 14, ADB serial
`MC94516AQF040305380`, authorized and boot complete. Physical display 1080x1240,
density 320; landscape captures are 1240x1080. No size/density overrides were used.
Installed with `adb install -r` into `.debug` and `.debug.test` only. The official
release app was not replaced and no app data was cleared.

Build:

```powershell
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :app:assembleFdroidDebug :app:assembleFdroidDebugAndroidTest :app:testFdroidDebugUnitTest --rerun :app:lintFdroidDebug -PsymbianRelease=true -PsymbianSourceDir=build/symbian-source-check --offline --console=plain
```

- Build successful. **398 fresh JVM tests**, zero failures/errors/skips.
- **72 Android tests passed**, zero failures/skips, 23.398 seconds. Classes:
  TouchMappingTest, TouchDeckTest, TouchFeedbackTest, GameHandlePlacementTest,
  SettingsTrialTest, GameChromeTest, ControllerMapperTest, SymbianControlsTest,
  SymbianGameSettingsTest, MophunTouchTest, and CanvasLifecycleTest's
  `remappedTouchKeysReachGuestCanvasCallbacksInBothOrientations`.
- Java fixture covers all 19 supported targets through actual Canvas
  keyPressed/keyReleased callbacks in both orientations. Native fixtures verify
  Mophun keypad/button masks and Symbian scan codes, not complete game sessions.
- Profile checks cover old defaults, detached copies, physical-profile isolation,
  interrupted trials, no autosave while trying, Keep/reload and Undo. Editor
  cancellation, restored drafts and independent mapping reset are covered.
- Lint: **zero errors, 447 warnings**. This is not a warning-free claim.

Local logs (ignored):

- `build/touch-mapping-20261028-verified-build.log`
- `build/touch-mapping-20261028-retroid-verified.txt`
- Initial successful 43-case and intermediate 65-case logs are retained.

APK SHA-256:

| Artifact | SHA-256 |
| --- | --- |
| `app/build/symbian-release/outputs/apk/fdroid/debug/AbyssME-0.6.0-beta.1-r1-debug.apk` | `3b4a2960625e5412db5a736dd9e08f382e406b587e7ddbc0022ae2a31d8e324d` |
| `app/build/symbian-release/outputs/apk/androidTest/fdroid/debug/app-fdroid-debug-androidTest.apk` | `18563af4ea76f39c4f3148808e865bc25297cb593dccf8f2835765e86d994937` |

## ADB-Driven UI Check

Used the existing local Avatar 1.0.6 240x320 fixture from the previous
[audio check](2026-10-28-audio-and-phone-controls.md), not the reported 1.1.6 edition.
Selected Numpad, tapped the lower-left diagonal, assigned 7, used the draft,
selected Try and Keep. Verified `touchGamepadMappings` in that Debug fixture's
saved config and its rendered labels after exit, reinstall/update and relaunch.
A held touch on the remapped Up/2 changed the sound prompt selection from No to
Yes. No subjective listening or full-game acceptance is inferred from this.

Final local captures (ignored):

- `build/touch-mapping-landscape-final.png`: complete editor, landscape preview.
- `build/touch-mapping-portrait-preview-final.png`: portrait-layout preview in
  the same Retroid editor, **not** a portrait phone hardware screenshot.
- `build/touch-mapping-relaunch-ready.png`: saved mapping after relaunch.
- `build/touch-mapping-guest-up-held.png`: guest menu response to mapped touch.
- `build/touch-mapping-preview.png`: Try/Keep/Undo before the final spacing-only
  adjustment; not a final-build UI beauty capture.

## Remaining Acceptance

- Treasure Towers was not found by a filename search of `local-games`; the
  specific report is **not yet reproduced**, not declared fixed by adding a mapper.
- No emulator run, touch-phone/cutout hardware check, TalkBack journey or human
  physical-controller acceptance in this batch. Input fixtures exercise portrait,
  landscape and compact geometry; that is not full-device UX certification.
- #41 MMAPI quiet music remains separate. No global volume boost, MIDI channel
  rewriting, new synth or game-name workaround was added in this batch.
- #43 localization is next in the approved queue; #44 platforms remain deferred.
