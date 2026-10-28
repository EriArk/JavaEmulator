# Direct MIDI Isolation and 16-bit ADPCM

Date: 2026-10-28. Unreleased development source after `3e7b3534`.
The combined Debug APK was installed on Retroid only. No signed/public APK,
release update, sound-bank replacement or game files are published by this work.
This extends [the earlier pipeline checks](2026-10-28-audio-pipeline.md).

## Changes

- Replace the direct `device://midi` singleton wrapper with a separate instance
  of the existing EAS synthesizer and AudioTrack for each prefetched player.
  Instruments and reverb preset are unchanged. Native calls are serialized;
  synth state, voices, controllers, output volume and lifetime are independent.
- Implement channel volume and independent player volume/mute/pan. Preserve
  authored channel volume, expression and note velocity; do not normalize or
  boost particular games. Master gain is applied to the player's PCM, not to
  shared MIDI channels. Retain the inherited MMAPI volume curve.
- Validate short event sizes and send the requested slice of long events.
  Read channel volume through split buffers/running status; return unknown after
  vendor SysEx rather than inventing a controller value. Send bank selection for
  `setProgram`. Unsupported bank-enumeration APIs remain unsupported.
- Stop direct notes even when they were played while PREFETCHED without a
  timeline start. Volume changes alone do not resume stopped sound. Close and
  deallocate stop/join the output thread before freeing native state.
- Decode IMA WAV and Yamaha ADPCM through the existing pinned FFmpeg decoder
  APIs, retaining signed 16-bit output, original rate and mono/stereo channels.
  Write WAV directly instead of using the compact build's `pcm_u8` encoder.
  Bound decoded output to 256 MiB; reject unsupported layouts/failed conversion
  and remove partial output. No new decoder or prebuilt library dependency.
- Include pinned public headers and provenance; check native ABI versions before
  using structs. Production APK verification now requires the bridge and notice.
  [Source ownership and build notes](../../app/src/main/cpp/audio/README.md).

## Verification

- Combined `assembleFdroidDebug` and `assembleFdroidDebugAndroidTest` pass.
- Fresh JVM suite: **402 tests, zero failures/errors** (four new MIDI stream tests).
- Final `lintFdroidDebug` passes: zero errors, 447 warnings. The installed APK
  contains the arm64 bridge and `AUDIO-BRIDGE.txt` notice.
- Retroid Pocket Classic, Android 14, serial `MC94516AQF040305380`: **29 audio
  instrumentation tests pass** on the installed combined Debug APK. Includes
  the explicitly supplied private Cops MIDI fixture, not an optional skip.
- Native bridge compiles/links for armeabi-v7a, arm64-v8a, x86 and x86_64 with
  NDK 22.1.7171670. Only arm64-v8a was executed; no emulator acceptance claimed.
- Generated IMA mono/stereo fixtures preserve low bits (1000, 1002, 1004, ...;
  the right stereo channel is negative), 22050 Hz and channel order. Yamaha
  stereo retains 16000 Hz and +/-47, +/-94, ... samples. These are exact decoded
  sample checks, not FFprobe metadata alone. Invalid input leaves no output file.
- Two native synth instances demonstrate no shared notes; closing one leaves
  the other operational. Twelve create/deallocate/recreate/close cycles leave
  no live `DirectMidiAudio` threads. Invalid controls and offsets are tested.
- A normal Debug launch of Rayman 3 passes its sound prompt and title menu,
  then resumes gameplay. Local capture: `build/audio-isolation-rayman.png`.
  This is a bounded launch/UI check, not a listening or complete-game result.
  Debug was stopped afterward and the official app reopened. Its installation
  and saves were not replaced. System music volume remains at the original
  speaker index 3 with mute enabled, verified through `dumpsys audio`.

Measured direct MIDI session RMS at deliberately low system volume:

| Condition | RMS (millibels) |
| --- | ---: |
| Full player/channel level | -5169 |
| Authored channel volume 32 | -7576 |
| Player level 50, channel 127 | -6780 |
| Muted effect, other player active | -9600 |
| Stopped PREFETCHED player | -9600 |

Changing one player's channel volume does not change the other player's same
channel. File MIDI/WAV concurrency, Nokia gain, loops, load recovery, speed and
mute/resume regressions also pass. Cops 1.2.1 file MIDI remains close to direct
Android playback; it is not routed through the new direct-event session.

The first measurement pass exposed a test-boundary issue: Android Visualizer
retains old peak/RMS measurements for up to 2000 ms after silence. Waveform
capture was already zero. Direct-session measurements now wait beyond that
window; the tests were not relaxed to accept a nonzero muted signal. The IMA
golden values were corrected against the pinned decoder's combined multiply/
shift rounding, rather than changing the decoder to match a mistaken fixture.

## Reproduction and Artifacts

```powershell
$env:ANDROID_HOME="$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :app:assembleFdroidDebug :app:assembleFdroidDebugAndroidTest :app:testFdroidDebugUnitTest --rerun -PsymbianRelease=true -PsymbianSourceDir=build/symbian-source-check --offline --console=plain --max-workers=2
adb -s MC94516AQF040305380 shell am instrument -w -r -e audioMidi /sdcard/Download/AbyssME-audio-check/cops-title.mid -e class javax.microedition.media.AudioPipelineTest,javax.microedition.media.AudioGainTest,javax.microedition.media.AudioOutputGainTest,javax.microedition.media.AudioSpeedTest,javax.microedition.media.MidiOutputParityTest,javax.microedition.media.AdpcmPrecisionTest,javax.microedition.media.DirectMidiTest io.github.eriark.abyssme.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Omit `audioMidi` to run without a private game sample; that one case then skips
explicitly. Generated fixtures and all new checks require no commercial games.

Local Debug APK: `app/build/symbian-release/outputs/apk/fdroid/debug/AbyssME-0.6.0-beta.1-r1-debug.apk`.
SHA-256: `be0b33d0006e6e369a18b0886d9234f3ee3326606c4cf5a868c0935d4fb75b4b`.
The inherited version label is not a new public release; identify this snapshot
by source and hash. Test APK SHA-256:
`78af30368edd64affb24784306e46fc4a4af1036b85af2a9200b9de9a0c7216e`.

Ignored evidence: `build/audio-isolation-{final-build,lint-final}.log`,
`build/audio-isolation-device-final.txt`, earlier diagnostic runs in
`build/audio-isolation-*.txt`, and `build/audio-native-check/`.
One combined build/lint attempt was cancelled during very slow local I/O;
build and lint were rerun separately. Only completed runs are counted above.

## Limits

No subjective listening, Bluetooth/headset routing or complete-game acceptance.
The exact reported Avatar 1.1.6 / Cops 1.2.3 quiet-music editions remain open in
#41; this does not claim original-phone synth balance equivalence. Mophun and
Symbian audio paths are unchanged. No new music/effects sliders, automatic
loudness normalization, SoundFont or instrument replacement was added.
