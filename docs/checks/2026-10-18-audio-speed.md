# Audio and emulation speed, 2026-10-18

## Scope and Availability

Post-Beta 6 development, not a new public APK. Beta 6's downloaded assets still
have normal-tempo audio. No game-specific exception or new user setting is added.

- Java's shared Android player follows guest speed, including WAV/compressed
  audio, MIDI files and generated tone sequences. Existing and newly started
  players update; reset/close remove stale state. MMAPI's system time base now
  uses the guest clock rather than an unrelated real-time clock.
- Mophun's session-owned MIDI player receives the same per-game speed, including
  changes before asynchronous preparation and while paused. The existing sound
  enablement boundary is unchanged: this does not enable unaccepted PCM games.
- Symbian's common cubeb output uses its already-bundled Speex resampler for
  mono/stereo PCM, synthesized music and decoded media. Device sample rate and
  recording are unchanged. The 100% path bypasses conversion until a speed change.
  Stream positions follow historical rates, not the latest percentage applied
  retroactively to the entire track. Paused frames do not advance media position.
- Audio rate conversion changes both tempo and pitch. This is not pitch-preserving
  time stretching. Normal speed restores normal playback. Existing FPS limits,
  Try/Keep/Undo, source files and saves are unchanged.

Android updates are applied only to playing media or immediately after start;
they must not resume a paused/prepared player. This follows the state-changing
behavior of [MediaPlayer.setPlaybackParams](https://developer.android.com/reference/android/media/MediaPlayer#setPlaybackParams(android.media.PlaybackParams)).
A rejected rate is logged without crashing the game or blocking a later return
to normal speed. Native converter setup failure retains the original audio path.

## Verification

- Retroid native tests: existing capture/routing/card/file/FPS/clock suites
  passed, plus the new audio tests using the exact bundled Speex source.
  Audio checks cover six rates, mono/stereo separation, non-silent waveform,
  frequency/duration, bounded buffers, EOF, reset, live changes and frame history.
- Initial Android 15 emulator audio run: six cases passed. Actual MIDI/WAV media
  positions advanced at approximately 0.5x / 2x / 1x. A 250 ms settling window
  initially measured the queued old-rate audio; a 1.2 s settling window plus a
  full-second measurement verified the steady rate without widening tolerance.
  Mophun MIDI duration, pause/preparation, replacement and cleanup checks passed.
  This is measured playback progress, not a listening assessment.

- Final matching APK/test pairs passed **36 cases each** on Retroid Pocket
  Classic (Android 14) and the Android 15 x86_64 emulator. This includes MIDI/WAV
  playback progress at 50/200/100%, paused-rate changes, reset, rejected-rate
  recovery, Mophun MIDI lifecycle/duration, converted guest time, FPS and settings
  trials. Logs: `build/audio-speed-retroid-common-final.txt` and
  `build/audio-speed-emulator-final.txt`. Earlier mismatched-artifact and
  short-settling-window runs are not counted as passes.
- Retroid combined Phone debug passed five UI cases; two unrequested fixtures
  were skipped, not passes. Deep Abyss and Snakes passed live speed/menu trials;
  Snakes also passed repeated restart and background/return checks. This is a
  visible launch/menu checkpoint, not full-game or listening acceptance.
  Log: `build/audio-speed-retroid-live.txt`.
- Both combined Debug variants built; 32 JVM tests per variant passed. Lint:
  zero errors, 403 Handheld / 410 Phone warnings. Final incremental assembly
  confirmed the Phone test pair was unchanged; Handheld was refreshed after
  the final shared-player edits. Logs: `build/audio-speed-retroid-build.log`,
  `build/audio-speed-final-build.log`, `build/audio-speed-emulator-final-build.log`.
- Tested Retroid package: `io.github.eriark.abyssme.symbian.phone.debug`.
  Its installed APK was retained locally as `build/audio-speed-phone-tested.apk`,
  SHA-256 `f565599ccb0a95159b1a7f549578da787797d28463b73515efd06ce4e65cedcb`.
  Debug fixtures/data remain separate from the official Handheld Beta 6, which
  was brought back to the foreground. No release APK was replaced or published.

Local logs, debug APKs and commercial fixtures remain ignored and are not
redistribution material. No physical-button, complete-save or listening test is
implied by automated playback progress and ADB-driven menu checks.

## Limits

Decoder/platform rejection can leave audio at its previous supported rate.
The output queue introduces transition latency. No claim of zero-delay or
sample-perfect game/audio synchronization, full-game compatibility or listening
quality. Direct Java MIDI event synthesis retains its normal voice pitch;
event scheduling follows the game's clock. Busy-loop and frame-paced games
may still differ from the selected percentage. Android 16 is not verified here.
