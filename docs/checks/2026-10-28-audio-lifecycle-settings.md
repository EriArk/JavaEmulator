# Audio Lifecycle and Settings Resource Extraction

2026-10-28, unreleased work after `ebd71894`. Combined Debug only; no public
APK, release, sound bank, guest language or official Retroid app data changed.

## Repairs

- Deliver each player's asynchronous events in posting order on one lazily
  started worker, instead of spawning a competing thread for each callback.
  Different players remain independent. Listener snapshots tolerate removal;
  runtime exceptions in one listener do not stop delivery to the others.
  Guest callbacks run outside player locks and may replay/close their player.
  Idle workers expire; close drains queued events and shuts down its worker.
- Keep configured finite loop count separate from the remaining repetitions.
  Pause preserves the remainder; replay after full completion gets a full new
  sequence. Reject invalid negative counts other than -1. Ignore completion
  when the player is stopped, deallocated or closed.
- Keep completed media prefetched instead of resetting its backend. Cache the
  completed position from the known duration when Android reports position zero;
  clear it on replay, seek or reset. Retroid reproduced zero position and zero
  END_OF_MEDIA timestamps for a generated 333 ms WAV before this repair.
- Deallocation no longer re-realizes the source immediately. Nokia Sound ignores
  completion from a replaced player and does not report stopped between repeats.
- Gain curves, instrument banks and native seamless infinite-loop playback are
  unchanged. This does not implement missing per-loop notifications from that
  Android infinite-loop path or establish all MMAPI behaviors.

Reference contract: [JSR-135 Player lifecycle and event ordering](https://docs.oracle.com/javame/config/cldc/opt-pkgs/api/mm/jsr135/javax/microedition/media/Player.html).

## Settings Preparation (#43)

The first resource extraction covers general Settings, palette labels, folder
scan summaries/access errors in both Settings and Library, and shared menu
navigation/preview labels. Preference keys and theme IDs are unchanged. English
is still displayed; no new locale/picker is advertised. Numbered arguments and
plural resources replace concatenated counts. See [scope and contributor glossary](../LOCALIZATION.md).

## Verification

- Fresh JVM suite: 408 tests, zero failures/errors, including six lifecycle
  cases for ordered/independent callbacks, reentrant replay/close, finite-loop
  pause/replay, late completion and invalid counts.
- Combined Debug app and Android test APK build and install on Retroid Pocket
  Classic, Android 14, serial `MC94516AQF040305380`.

- Final Retroid audio run: **35 tests pass**, including six new real-backend
  lifecycle tests. Generated WAV and tone loops replay their full count, retain
  completed time, and allow restart/close from callbacks. Failed/self-removing
  listeners and queued old Nokia Sound events do not break the current sound.

- Final Retroid settings/catalog run: **24 tests pass** across SettingsResources,
  GameFolderIndexer, CompactSettings, ReleaseJourney and ThemeContrast. Covers
  English fallback, plurals/format arguments, read-access failures, saved setting
  recreation, shared materials, brightness cancellation and transfer rotation.
- Lint: **zero errors, 443 warnings**. `git diff --check` passes.
- Retroid system music volume is still speaker index 3, muted, after testing.
- Opened Debug Settings through the library menu and inspected the English
  screen at 1240x1080 (`build/settings-extraction.png`, local only). The direct
  ADB Activity start was correctly rejected because Settings is not exported;
  no manifest/access change was made. Debug was stopped and the official app
  reopened after verification. This is not new phone/RTL layout acceptance.

The initial device run had one failure: completed WAV position was zero despite
duration 333000 us; captured END_OF_MEDIA times were also zero. The final source
repairs this instead of weakening the position assertion. Generated WAV/tone
fixtures, not game-specific workarounds, cover repeats and listener behavior.

## Reproduction and Artifacts

```powershell
.\gradlew.bat :app:assembleFdroidDebug :app:assembleFdroidDebugAndroidTest :app:testFdroidDebugUnitTest --rerun -PsymbianRelease=true -PsymbianSourceDir=build/symbian-source-check --offline --console=plain --max-workers=2
.\gradlew.bat :app:lintFdroidDebug -PsymbianRelease=true -PsymbianSourceDir=build/symbian-source-check --offline --console=plain --max-workers=1
adb -s MC94516AQF040305380 shell am instrument -w -r -e audioMidi /sdcard/Download/AbyssME-audio-check/cops-title.mid -e class javax.microedition.media.AudioLifecycleTest,javax.microedition.media.AudioPipelineTest,javax.microedition.media.AudioGainTest,javax.microedition.media.AudioOutputGainTest,javax.microedition.media.AudioSpeedTest,javax.microedition.media.MidiOutputParityTest,javax.microedition.media.AdpcmPrecisionTest,javax.microedition.media.DirectMidiTest io.github.eriark.abyssme.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Local APK: `app/build/symbian-release/outputs/apk/fdroid/debug/AbyssME-0.6.0-beta.1-r1-debug.apk`.
SHA-256: `353b188a8590e2f2eaed7d4d712217e267d8ac1153ccaf39a8ce633fabe9803e`.
Android test APK SHA-256:
`c5c103509385185c646ccf63ce4a72a8344f61a4614f0739fa6bd25246e06703`.
The inherited r1 name is not a new release. Ignored local logs:
`build/audio-lifecycle-{unit,build,settings-build,final-build,settings-lint}.log`,
`build/audio-lifecycle-device-{first,final}.txt`.
The settings/catalog run is in `build/settings-resources-device-final.txt`.
Run its five named classes through the same instrumentation runner, replacing
the audio class list. No optional fixtures are required for those 24 checks.

## Limits

The supplied archive's Avatar manifests are 1.0.0, 1.0.5 and 1.0.6; Cops is
1.2.1. The reported Avatar 1.1.6 / Cops 1.2.3 remain unavailable and #41 remains
open. No game downloads, commercial assets, subjective listening, headset-route,
complete-game or emulator acceptance are claimed. Mophun/Symbian audio is unchanged.
#43 is only started: most screens, full locale switching and reviewed translations
remain future work. Existing inherited locale resources were not removed.
