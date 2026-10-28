# Java Audio Pipeline Repairs and MIDI Comparison

Date: 2026-10-28. Unreleased development on `main`, based on `3e2dd02a`.
This extends [the initial audio investigation](2026-10-28-audio-and-phone-controls.md)
for [#41](https://github.com/EriArk/JavaEmulator/issues/41). No public APK,
release tag, native engine, dependency version or licensing change.

## Shared Repairs

- Copy input streams to their actual end, without treating `available()` as
  file size. Avoid trailing zero bytes and spinning on a zero-length read.
  Propagate read/close errors and remove incomplete temporary files.
- Reset Android's native player after a failed prepare. Do not mark a failed
  load successful, enter STARTED or prevent a valid retry on the same instance.
- Actually prepare audio during MMAPI prefetch, making duration available before
  playback. Missing/invalid audio produces a guest `MediaException`; optional
  metadata failures do not prevent otherwise playable audio. Release retrievers.
- Reset pending seek position on replay/reset. Convert microseconds to
  milliseconds before narrowing to an integer; clamp negative/oversized seeks.
- Retain known duration across completion/reset until the source changes.
  Retain requested volume, mute and looping across playback resource resets.
- Keep post-close pan changes harmless, matching existing volume/mute tolerance.
- Preserve the original ADPCM sample rate instead of always reducing it to
  16 kHz. Use structured FFmpeg arguments and remove failed conversion output.

**ADPCM precision limitation:** the pinned compact FFmpeg binary has only the
required legacy PCM encoder subset. A device attempt to use `pcm_s16le` failed
with `Unknown encoder 'pcm_s16le'`; it was not shipped as a broken conversion.
The working conversion still uses `pcm_u8`. This batch removes the forced
sample-rate reduction, **not** 8-bit quantization. Full 16-bit decoding remains
future work requiring an appropriate decoder/dependency and source audit.

The inherited MMAPI volume curve, MIDI channel controllers and note velocities
are unchanged. No game-name exceptions, automatic normalization or global boost.
Direct `device://midi` still has a separate shared-driver/control limitation;
these repairs and measurements concern file/stream players, not that synth.

Lifecycle references checked: [Android MediaPlayer](https://developer.android.com/reference/android/media/MediaPlayer)
and [JSR-135 Player](https://docs.oracle.com/javame/config/cldc/opt-pkgs/api/mm/jsr135/javax/microedition/media/Player.html).

## Before and After

The new pipeline tests were first installed against the previous Debug app.
Five failures reproduced: trailing bytes from overestimated `available()`,
swallowed read failure, missing source reporting successful start, stale pending
seek after reset, and long media time wrapping negative (one hour became
`-694967000` microseconds). The initial sixth failure was a **test-fixture setup
error**, not a product regression: compact FFmpeg cannot encode IMA ADPCM.
The final test creates an original valid IMA block directly and verifies the
converted format/rate and playback, without requiring that optional encoder.

Final Retroid checks:

- **20 audio tests pass**: 10 pipeline, 4 gain, 1 output gain, 3 speed, and
  2 MIDI comparisons (including the explicitly supplied private Cops track).
- **Two further private MIDI runs pass** for Bounce Tales and Splinter Cell 2.
- **398 JVM tests pass**, zero failures/errors. Lint: zero errors, 447 warnings.
- Audio tests include finite loops/replay, duration, failed-load recovery,
  prefetch, long seeks, stream failures, ADPCM rate, tone replacement after
  deallocation, independent concurrent MIDI/WAV players, mute across resume,
  and 50/100/200/400-percent playback speed behavior.

Device: Retroid Pocket Classic, Android 14, serial `MC94516AQF040305380`.
ADB authorized and boot completed. These are physical-device automated output
checks, not a human listening evaluation, headset comparison or full-game pass.
No emulator audio run in this batch. Original music state restored and verified:
speaker index 3, muted. Diagnostic `J2MEAudio` property cleared after checks.

## MIDI Output Results

Same bytes, same device/system volume, separate sessions: ordinary Android
MediaPlayer versus `Manager`'s MicroPlayer/AndroidPlayer backend. Visualizer
measures only each player's own session, not microphone or other apps.
Median RMS values below are millibels at deliberately quiet system volume 2;
they are **not** source-file loudness or calibrated speaker measurements.

| Fixture | Raw Android | MMAPI | Observation |
| --- | ---: | ---: | --- |
| Cops 1.2.1 title MIDI | -7664 | -7686 | Difference about 0.22 dB |
| Generated organ, CC7=32 | -8705 | -8706 | Authored low channel volume retained |
| Generated organ, CC7=127 | -6360 | -6333 | Authored higher channel volume retained |
| Bounce Tales 1.15, `aw.mid` | -5695 | -5697 | Final-duration rebuild |
| Splinter Cell 2 1.0.1, `0.mid` | -6341 | -6334 | Final-duration rebuild |

The generated sustained-note comparison also starts a concurrent WAV effect,
changes its volume and mutes/resumes the music. The two sessions stay independent.
Nokia output-gain measurement on the final app: full `-6635`, gain 128 `-8354`,
zero and resumed-zero `-9600`, restored full `-6734` millibels.

**Interpretation:** these samples show no extra MMAPI attenuation versus the same
Android MIDI synthesizer. They do not prove Android's instrument balance matches
an original phone. Reported Avatar **1.1.6** and Cops **1.2.3** remain unverified;
the available Cops **1.2.1** asset is a different edition. Keep #41 open.

Private track SHA-256 values (assets are not redistributed):

- Cops: `a2a1fb77ec1348d411bcb699505cf9e2ab9f165b2253897fd0dd37df4eca4897`.
- Bounce: `c8db4d6bd4093eb8eb657f8027b83c6d9fe33099efd7a192fc030bf2b054ad9a`.
- Splinter Cell: `1ca5ca2e364cebfad38648724e4fc7e41f08cc0cb948c41d1c9f99cc8041b1bb`.

## Installed Test Games

Five additional user-provided JARs were imported into the **Debug app only**.
Official `io.github.eriark.abyssme`, its saves, profiles and library were not
reinstalled or cleared. Bounce Tales already existed in Debug; no duplicate was
installed. Manifest metadata below identifies the supplied files, not verified
official distribution provenance. Each added entry exists in the converted library.

| JAR | Manifest version | Observed scope |
| --- | --- | --- |
| `Diamondrush.jar` | 1.1.6 | Installed; this edition displays its own landscape warning. Not audio-accepted. |
| `Rayman3.jar` | 1.0.3 | Sound prompt, title, menu and first gameplay screen. MIDI level 70, music repeat and separate sound starts traced. |
| `WormsForts_K700.jar` | 0.80 | Installed and publisher screen; gameplay/audio not accepted. |
| `SplinterCell2ExtendedOps.jar` | 1.0.1 | Title/menu, music at requested MMAPI level 60; extracted MIDI output comparison. |
| `Sonic_Jump.jar` | 1.0.4 | Installed, language/sound prompt and main menu; no full audio/gameplay acceptance. |
| `BounceTales.jar` (existing) | 1.15 | Additional MIDI output comparison, not a new installation. |

Game SHA-256 in the same order:

```text
1a27a721a4440e8bdf63aa456732d71cb4e0cbd00054899dd233b0f69fd96b00
4bcfe076108cb5a681cb797d86343a4562bf478136c73488c21ee87750c1220c
e869598fa290a6090468c232611cbccfcff5d5b3897422fbfff0b7fccc820d69
8b91c6c96067ecbf533ca71486072bec48c673872b323fbff09ce0468c97a31c
121c1c2b94f0b0aefda3c54f78eca9869973e7a3626e9b0c018ce1fbf1bfd7b9
d69de01b5e45267820b00386e45db2296b4ca761708dcca6ca4ab03f3c604e39
```

Private inputs remain under `local-games/kevtech-pack/`; staged device files are
in `/sdcard/Download/AbyssME-audio-check/`. No commercial JAR/media added to Git.

## Reproduction and Artifact Record

```powershell
.\gradlew.bat :app:assembleFdroidDebug :app:assembleFdroidDebugAndroidTest `
  :app:testFdroidDebugUnitTest --rerun :app:lintFdroidDebug `
  -PsymbianRelease=true -PsymbianSourceDir=build/symbian-source-check --offline --console=plain
adb -s MC94516AQF040305380 install -r -d <debug-apk>
adb -s MC94516AQF040305380 install -r -d <androidTest-apk>
adb -s MC94516AQF040305380 shell am instrument -w `
  -e audioMidi /sdcard/Download/AbyssME-audio-check/cops-title.mid `
  -e class javax.microedition.media.AudioPipelineTest,javax.microedition.media.MidiOutputParityTest,javax.microedition.media.AudioGainTest,javax.microedition.media.AudioOutputGainTest,javax.microedition.media.AudioSpeedTest `
  io.github.eriark.abyssme.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Without `audioMidi`, the private-asset test explicitly skips; generated checks
need no games. Further game samples use `MidiOutputParityTest#optionalPrivateMidiMatchesAndroid`.

Final local combined Debug APK (not a release despite its inherited version name):
`app/build/symbian-release/outputs/apk/fdroid/debug/AbyssME-0.6.0-beta.1-r1-debug.apk`,
SHA-256 `bd16855afecdeaef9c7a78be3352cf739cae45fe7f81b7ba3021768fa47c41a1`.
Test APK SHA-256 `4bc5b2a97014c082778146da772fcffdd761d22dff351d5501511dd9da7689e1`.

Ignored local evidence: `build/audio-pipeline-before.txt`,
`build/audio-pipeline-verified-build.log`, `build/audio-pipeline-verified.txt`,
`build/audio-parity-{bounce,splinter}-verified.txt`, `build/audio-trace-20261028.txt`,
and `build/audio-*.png`. Rayman gameplay/title and Splinter/Sonic UI captures
precede the final duration-cache-only change; they are not full-game proof.

Still open: exact reported quiet editions, original-phone synth balance,
16-bit ADPCM output, direct-MIDI isolation/control semantics, long-session
listening and Bluetooth/headset routing. Mophun/Symbian audio is unchanged and
not covered by these Java checks. Public release state/assets were not re-audited
or modified by this work.
