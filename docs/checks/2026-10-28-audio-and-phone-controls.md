# Java Audio and Phone Controls: 2026-10-28

## Availability

Unreleased development source after `370f76e6`, with a locally installed combined
Debug APK. No signed release or public APK upload. The existing public page
still lists `0.6.0-beta.1-r1`, code 312; its asset names were rechecked on
2026-10-28, not downloaded/rehashed in this pass. No release tags were moved.

The approved queue is [#41 -> #42 -> #45 -> #43](../STABILIZATION_PLAN.md).
New platforms (#44) are deferred. #45 and #43 have not been implemented here.

## Audio (#41): Implemented, Not a Title-Fix Claim

- Nokia `Sound.setGain` is no longer a no-op. Clamp to 0..255, retain exact
  `getGain` readback, and apply through the backing player's VolumeControl.
  Positive gains do not round to zero. Keep the old effective full-volume default.
- Preserve the requested gain across stop/resume, replay and player replacement,
  including release followed by reinitialization; keep separate Sound objects
  independent. Frequency-created sounds also receive the player listener.
- Leave the inherited MMAPI logarithmic curve, default volume, mute and pan
  behavior unchanged. No title-specific override or unconditional boost.
- Add opt-in Debug audio tracing, gated by `log.tag.J2MEAudio=DEBUG`. It records
  player identity, type, state, level/mute/pan and applied gain, not game data.
  Retroid firmware discards debug logcat, so the same trace has a private,
  bounded 256 KiB cache fallback. Release builds do not enable this path.

References: [Nokia Sound API](https://nikita36078.github.io/J2ME_Docs/docs/Nokia_UI_API_1_1/com/nokia/mid/sound/Sound.html),
[JSR-135 VolumeControl](https://docs.oracle.com/javame/config/cldc/opt-pkgs/api/mm/jsr135/javax/microedition/media/control/VolumeControl.html),
[JSR-135 MIDIControl](https://docs.oracle.com/javame/config/cldc/opt-pkgs/api/mm/jsr135/javax/microedition/media/control/MIDIControl.html).

### Private Game Checks

These are user-supplied fixtures, not distributed assets:

| Edition | SHA-256 | Observed boundary |
| --- | --- | --- |
| Avatar 1.0.6, Gameloft SA, 240x320 | `8bc00120be65da45077928a95ad40f3a8321f62b44aa7cdf78fdf0a4a490592f` | MMAPI MIDI/WAV; first gameplay screen reached. Captured music and effects request level 100, unmuted, gain 1.0. |
| Cops L.A. Police 1.2.1, Gameloft SA, 240x320 | `9df6502d84b9a9ab297f79296ba49379c9c73025dacd0d644ea25e28e5a61f52` | MMAPI MIDI title music starts at default level 100, unmuted. Extracted title MIDI has channel volume commands 30..57 and note velocities 8..101. |

The reported editions are **Avatar 1.1.6 and Cops 1.2.3**, which were not located
in this local selection. The Nokia repair does not fix or reproduce their reported
quiet music: these checked editions use a different player path. The captured
full-level requests do not support blaming MMAPI level scaling for those tracks.
Low channel values in a MIDI asset alone do not prove an emulator defect either.
Synthesizer/output balance remains to be compared; authored fades must be preserved.

Direct MIDI channel-volume setters remain a separate open problem. Their current
shared driver needs player isolation before adding channel commands; simply
sending CC7 globally would change other players. No direct-MIDI repair is claimed.

### Measured Android Output

The generated 440 Hz WAV test attaches Android Visualizer to its own MediaPlayer
session, not a microphone or global output capture. With system music volume 1,
median RMS values were `-6635`, `-8354`, `-9600`, `-9600`, `-6734` millibels for
full gain, gain 128, zero gain, resumed zero gain and restored full gain.
The final repeat measured -6732 mB at restored full gain, within the same tolerance.
This establishes actual output attenuation/muting, not subjective listening
quality or the correct loudness of a commercial game's music.

The initial test incorrectly assumed a louder system mixer setting and failed
an absolute threshold. It was corrected to compare against the -9600 mB silence
floor and relative attenuation, then passed. The test restores the original
music volume/mute state; Retroid retained volume 3 and muted state afterwards.

## Phone Controls (#42)

- Java game menu **Controls / More > Handle & opacity** edits per-game settings
  through the existing detached draft and Try/Keep/Undo flow.
- **Move handle** reuses the touch-layout editor in an isolated handle mode.
  Dragging and directional nudge buttons affect only the handle, not touch keys.
  Portrait and landscape store independent normalized positions. Runtime clamps
  to the available viewport, system/cutout and mandatory-gesture insets.
- Existing profiles retain a visible top-right handle and 100% shortcut opacity.
  Hiding it removes its touch target; Android Back opens the menu, including the
  activity back callback. The settings show the alternative before confirmation.
- Shortcut opacity is independent of touch-button opacity. Both range 20..100;
  High contrast uses 100 without destroying the stored preference.
- Phone/Gamepad opacity now applies to the complete drawn controls. Previously
  some gamepad faces/borders reset alpha while painting. Separate mode no longer
  clamps the saved value to 80. The gameplay renderer and hitboxes are unchanged.
  At 100% opacity there is no extra alpha-composition layer.
- Preserve the existing global Display/Screenshot switches, five-second collapse,
  fast-forward behavior and other engines' own menu affordances. No fake handle
  was added to Mophun or Symbian. Shared TouchDeck opacity applies where reused;
  Legacy keyboard opacity remains under its existing advanced settings.
- A moved handle can intersect the temporary Keep/Undo bar. Hide just that handle
  while the bar overlaps it, then restore its chosen position after the preview.
  Do not move the game, obscure preview actions or change the saved position.

### Device Journey

On Retroid Pocket Classic / Android 14, using the separate Debug package:

- Opened the new page from the running Avatar fixture, dragged the handle, tried
  and kept the layout, and verified saved orientation-specific fields.
- Hid the handle, kept the change, opened the menu with Android Back, relaunched
  the game and reopened the same hidden-handle settings through Back.
- Re-enabled the handle, reduced shortcut opacity, tried and kept the change;
  observed the translucent moved handle without fading the game or keypad.
- Verified an injected long Back still opens Exit confirmation with the handle
  hidden; short Back opens the ordinary menu. Returned to the signed library.
- Reproduced the Keep/Undo overlap, fixed it, rebuilt, and visually confirmed
  the bar stays unobstructed and the handle returns after Keep.

This is ADB-driven device UI evidence, not human physical-button acceptance.
No Android emulator was connected in this pass. Portrait/landscape geometry,
small safe rectangles and malformed positions have instrumentation coverage;
real cutouts, gesture-navigation edge swipes and other-engine live journeys
still need additional device acceptance. #42 is not closed on this evidence alone.

## Checks and Local Artifacts

- Combined Debug app and Android test APK assembled successfully with
  `-PsymbianRelease=true -PsymbianSourceDir=build/symbian-source-check`.
- Fresh JVM suite: **395 tests, zero failures/errors/skips**.
- Final Retroid instrumentation: **60 discovered, 59 passed, one optional
  screenshot-capture assumption skip**, no failures; approximately 43 seconds.
  Includes eight audio tests, six GameChrome tests, five handle/editor/opacity
  tests and existing TouchDeck, visual palette, serialization and trial tests.
- Debug lint: **zero errors, 446 warnings**. An earlier offline audio-only lint
  attempt lacked the pinned `androidx.concurrent:concurrent-futures:1.1.0`
  cache entry; the online retry populated it successfully without changing
  dependencies. The final combined lint completed offline.
- APK SHA-256: `11fce23279b1328a4a5a87f849ca0777e27f582bfc21e0bf0973ead410958d8b`.
- Test APK SHA-256: `acd2f363299f5ec73e5e4764769801ef81032a0bf23560057f2c7e74926b2fbb`.

Local evidence, intentionally ignored:

- `build/audio-gain-20261028-retroid-final.txt`: initial eight-test audio pass.
- `build/audio-gain-20261028-{avatar,cops}-trace.txt`: private player traces.
- `build/phone-chrome-20261028-{build,final-build,lint,jvm-final}.log`.
- `build/phone-chrome-20261028-retroid-final.txt`: raw final Android results.
- `build/chrome-{page,editor-before,hidden-live,back-menu,after-relaunch,preview-priority,opacity-live}.png`:
  local UI captures; the editor-before capture precedes the preview-arrow size fix.

No app uninstall, data reset, account reset, game-save replacement, release APK
replacement or device-resolution override. Added only private test fixtures to
the Debug library. Restored diagnostic log properties and system audio state.
Builds retain the r1 development version name; they are **not** the signed r1 APK.
