# Crash of the Titans complaint investigation

Date: November 14, 2026. **Local device investigation; no code change or new APK.**
Tested the installed official ARM64 0.7.0 Beta 7 / code 321 on a Retroid Pocket
Classic, Android 14. No live issue/release review or ARMv7 check was performed.

The supplied complaint names the game but gives no failing action. The maintainer
confirmed that no further description was available. The local Kevtech pack has
`CrashoftheTitans.jar`, MIDlet `CrashTitans`, version 1.0.1, 289,861 bytes.
SHA-256: `a958aadb1aa5eaf144b3fb2e78ea8edb0922e464042b99f614672e234178e921`.
Its identity relative to the reporter's JAR is unknown.

## Observed behavior

- Import, language/music prompts, title animation, introductory dialogue, world
  map and tutorial load. Continue works after process restart.
- ADB-injected held D-pad/numeric keys and the visible Phone keypad operate menus,
  left/right movement, automatic traversal jumps and combat. Attacks reduce enemy
  health and one enemy was defeated. Pause, return and game options operate.
- Zero-duration ADB key events are sometimes missed; held input works. This does
  not establish a physical-button defect. Human controller comfort was not tested.
- No persistent graphical corruption, app crash, ANR or uncaught game exception
  appeared during this bounded sample. It is not a full tutorial/game completion.

## Reproduced sound loss across host-menu return

1. Start the game, enable music, and enter the tutorial. A media player is active
   and captured PCM contains a nonzero signal in both the title and gameplay.
2. Open AbyssME's Game menu with Android Back, then Resume. Dismiss the game's
   own resume/pause screens and continue.
3. The game's Options now reads **Sound: Off**, and its audio player is gone.
4. Change the game's Options back to **Sound: On** and Continue. A media player
   starts again. Restarting the game and enabling music also restores playback.

This also appeared after trying an on-screen-control change, but changing settings
is not required: ordinary host-menu return reproduces it. Read-only inspection
of this JAR's bytecode explains a likely game-side cause: its Canvas `hideNotify()`
closes the player and sets its own sound-disabled flag; `showNotify()` only clears
a visibility flag. This is evidence about this game variant, not proof that every
host lifecycle notification is correct. No general emulator lifecycle workaround
or title-specific patch was introduced without further validation.

## Audio evidence and limits

Android output was captured with scrcpy 5.0.1, not a microphone. The device started
at media volume zero. Initial silent recordings were unsuitable for judging game
sound; later capture explicitly verified an unmuted output and an active player.
The fresh recording's title and gameplay samples measured approximately -58 and
-60 dBFS RMS respectively; both contain nonzero PCM. These measurements establish
signal presence, not correct instruments, timing, sound effects or subjective
quality. The assistant's audio-input tool rejected playback, so **no listening
acceptance is claimed**.

Local evidence: `build/checks/2026-11-14-crash-titans/` contains screenshots,
process logs, audio service snapshots, bytecode inspection and raw recordings.
`fresh-sound-check.mp4` captures a fresh start through gameplay with audio;
`crash-titans-test-with-audio.mp4` raises that track by 25 dB for convenient review.
Neither game files nor captured commercial artwork/audio are published by this
report. The original APK remains installed. The new game is retained in the
private device library; its temporary touch setting was restored byte for byte,
and speaker media volume was returned to zero.
