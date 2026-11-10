# Adaptive ARMv7 Java/Mophun Target — 2026-11-10

Local-only implementation of the #29 build target. `-Parmv7=true` selects one
adaptive `fdroid` APK with Java/Mophun and `armeabi-v7a` dependencies, excluding
Symbian/N-Gage. Android 10+ and existing application IDs remain unchanged.
Output goes to `app/build/armv7/`; filenames include `-armv7`. Combining ARMv7
with Symbian/research modes or requesting the old Phone flavor fails explicitly.

The library hides unsupported platform tabs, cycles only Java/Mophun and falls
back from a remembered Symbian tab without deleting its stored tab state.
Import/system-file actions already follow the same engine capability flag.
The server launcher accepts `-Target armv7`, skips Symbian restoration and records
the target in output names/provenance. Its default remains `combined`.

## Verified locally

- ARMv7 Debug/test and optimized unsigned Release APKs assemble. The production
  guard passes: expected backend/notice payloads, no Symbian, no foreign ABI,
  ELF32/ARM headers, no game archives or Debug network probe in Release.
- Sixteen packaged shared libraries are ELF32/ARM. `readelf` dependency inventory
  contains only packaged libraries and Android system dependencies. The peer
  runtime uses a static C++ runtime; `libc++_shared.so` is not required. An initial
  overly broad guard incorrectly required it; the dependency inventory was used
  to correct the guard before its successful rerun.
- Android reports `primaryCpuAbi=armeabi-v7a`; the explicit smoke test verifies
  a 32-bit process and loads all required native backends. Cold library launch
  succeeds and the Java/Mophun-only UI was inspected.
- **70 selected Android cases pass** on API 30 with ARM translation: ARMv7
  process/tabs, native M3G CPU rendering, generated audio, Mophun MIDI/scene/save,
  library import/backup, and native encrypted transport.
- The first run passed 62/70. Seven CPU-reference cases ran with the production
  GPU path enabled; the fixture now explicitly selects CPU and restores prior
  renderer state. All 11 CPU-reference cases pass on rerun. A MIDI completion-time
  ratio also failed on an isolated rerun; completion includes platform synthesis/
  output latency. The fixture now measures media-position progress between
  startup and completion, retaining its speed-ratio requirement; that case passes.
  No production audio/renderer behavior was changed to satisfy these fixtures.
- **419 app JVM tests pass**, zero failures/errors/skips. The five converter tests
  retain the earlier passing result; no fresh converter execution is claimed.
- ARMv7 lint with optional production M3G test sources: **zero errors, 1300
  warnings** (610 unused resources, including excluded-engine resources). This is
  a different target/test-source scope from the combined build's 804 warnings.
- Four negative configuration checks cover Symbian, research, malformed property
  and unavailable Phone flavor. The first guard harness expected an uppercase
  word in Gradle's missing-task message; the corrected exact substring passes.
- Shell syntax and embedded artifact-metadata Python parse. No PowerShell/SSH
  end-to-end execution is claimed for the new server target.

## Reproduce

```sh
bash gradlew -Parmv7=true :app:assembleFdroidDebug
bash gradlew -Parmv7=true -PunsignedRelease=true :app:verifyProductionApks
bash tools/remote-build/verify-armv7-guards.sh
```

The ARMv7 runtime checks additionally build instrumentation with
`-Pm3gProductionTests=true`; select `Armv7TargetTest` with instrumentation argument
`armv7=true`. Test classes/logs, APKs, source hashes, dependency inventory and a
library capture are in ignored `build/checks/2026-11-10-armv7/BUILD-INFO.json` and
its directory. These are local Debug/unsigned artifacts, not public releases.

## Remaining acceptance

Native ARMv7 hardware, real-game compatibility/performance, representative save
and signed-upgrade behavior, source bundles and release signing remain open.
The connected handheld is still unauthorized in ADB. Translation on an x86_64
emulator is not physical ARMv7 acceptance. The normal combined build is checked
separately in the final candidate checkpoint. No GitHub Actions was enabled.
