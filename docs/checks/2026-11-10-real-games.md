# Real-game emulator checkpoint — 2026-11-10

Local-only follow-up to the [functionality candidate](2026-11-10-functionality-candidate.md).
The maintainer is away and explicitly requested continued work without the handheld.
No device installation, source push, signed build or release publication was performed.
Public release assets and live multiplayer services were not rechecked.

## Environment and isolation

Android 11/API 30 x86_64 emulator, ARM64 translation, headless SwiftShader,
1080×1920 display, no audible output. The installed combined Debug APK matches
SHA-256 `afaab3a0ac6ada2877279cc2440a80f188a5c1073e6a83afda1ea8ef94eee53b`
from the candidate manifest. Production code is unchanged in this checkpoint.
The observer instrumentation APK strengthens Java input injection and Symbian
launch checks (12-second build). The final lifecycle APK also resets generated
Mophun captures/results before each run (14-second build).

Java runs create a unique conversion/profile/save directory and remove only that
fixture after terminating its game VM. Mophun uses a temporary library/database,
profile and save tree, restores the previous Config directories, and joins its
workers before reading saves or removing the fixture. The N95 phone is a copied
fixture on this dedicated emulator. All nine original host game/firmware inputs are rehashed unchanged; the
handheld's app/data remain untouched. Private inputs, screenshots, tombstones,
APKs and logs remain ignored under `build/checks/2026-11-10-real-games/`.

## Exact game samples

| Sample | SHA-256 | Observed scope |
| --- | --- | --- |
| SimCityM / Metropolis 1.23.87, Electronic Arts/kriker | `a4ac62d7d241cf06c1b39f92dafc5ea937ddd93dd458dcd47127dc3d089761cd` | Conversion and launch succeed; soft-left/confirm input reaches the mission goals. Generic 240×320 crops this edition; a manual 320×240 profile fits the tutorial and reaches the city view. No automatic size-detection fix is claimed. |
| Diamond Rush 1.1.6, Nokia edition | `1a27a721a4440e8bdf63aa456732d71cb4e0cbd00054899dd233b0f69fd96b00` | Reaches a level using the known manual 320×240 profile and injected directional input. No save/relaunch or complete-level acceptance. |
| Crash Twinsanity 1.7.1, WonderPhone-VUG | `b17804442e53278d866ee5697818fcff5d7112383a0c9385835c10d8adc7ec18` | Launch reaches language/main menus; NUM5 input reaches a name-entry screen. No launch crash observed, but gameplay is not accepted. |
| Crash of the Titans 1.0.1, VGM/BiNPDA | `a958aadb1aa5eaf144b3fb2e78ea8edb0922e464042b99f614672e234178e921` | Launch/input reaches the introductory scene. No complete level, save or audio acceptance. |
| Deep Abyss 1.4, multiscreen | `d0fe59b2c90ea3c41f15c4612f1b62f2599f738dd95c9e3265cc3818b8625370` | Gameplay, background pause/foreground resume, duplicate import, and new activity launch. Two game-generated save files survive duplicate import byte-for-byte. |
| Deep Abyss 1.1, T300 | `a4a7c9e78f8762432907d8bbcfed71cab50877de29c3a4cc448471d6b90ecc0f` | Gameplay, pause/resume and new activity launch. This short run creates no save files, so it does not establish save persistence. |

The Crash editions do not identify the reporter's game in #52. Keep that issue open.
Mophun relaunch returns to the main menu; retained bytes do not demonstrate resumed
level progress. Instrumentation completion is not full-game acceptance. Java
observation scripts capture frames but do not assert guest gameplay or absence of
guest errors; conclusions above require screenshot/log review.

## Mophun lifecycle assertions

`ProvidedMophunLifecycleTest#providedGamePausesReimportsAndRelaunches` is opt-in
through `mophunFile` in the target app's external `sweep/mophun/` directory. It
imports the real MPN into a temporary root, waits for presented frames, injects
confirm/direction input, checks a live error-free worker, backgrounds the activity
until the VM parks, confirms presentation serial stability while paused, then
requires advancing frames after resume. After worker shutdown it snapshots all
generated save bytes and verifies duplicate import preserves them. A fresh activity
must present frames without a session error. No fixture or game is bundled.

Both final lifecycle runs pass: **1.4 in 64.698s; 1.1 in 43.049s**. Their test
APK SHA-256 is `f39f9d8125a9d35dbfe34ad55a9d9af7149c8efa1a6935543dd47968341f5f5f`.
Earlier successful runs are retained under `initial-fixtures/`; counts overlap.
Only test capture/report cleanup changed after the previous strengthened runs.
The 1.1 empty save tree is explicitly not a persistence result. No new audible
sound, game-completion, physical input, thermal or native ARMv7 acceptance is claimed.

The final SimCity 320×240 observer run completes in **45.068s** and its last frame
shows the city and Build/Pause controls. Its test APK is
`d1bb0d25db89ae2a02f9a829c9345a3cfdc5cf39f0a07a3135a47703bdb3fdab`.

## N95/N-Gage startup: emulator translation blocker

The supplied N95/RM-320 ROM/RPKG archive imports, the N-Gage 2.0 platform registers,
and the Asphalt 3 card package stages. Opening the platform then crashes the
separate `:symbian` process before an LCD comparison:

- Default `dynarmic`: `ndk_translation` reports undefined instruction `0xd50320bf`,
  followed by `SIGILL`; the tombstone points into `libndk_translation.so`.
- An isolated `cpu: dyncom` configuration experiment also fails in the translator,
  this time on `0x5ea1b842`. The app is stopped and the original phone config restored.

These are translation failures on this x86_64 Android test environment. They do
not reproduce or resolve the earlier physical N95 `KERN-EXEC 3`/LCD report. No
Original/LCD Soft/LCD Classic gameplay comparison is accepted in this checkpoint.
The original preparation helper reported `OK` despite the separate process dying;
it now requires game controls to remain accessible after its observation delay.
The guarded rerun fails after 38.233s with `Missing UI text: Show game controls`,
alongside the same translator crash. This is a failing startup check, not a passing game test.

## Fixture and infrastructure corrections

- Initial Java source checks failed because root-pushed external fixture files had
  the wrong owner and SELinux categories. Correct only the dedicated fixture
  directory to the app's observed UID/categories; SELinux remains enforcing.
- A host Android-emulator process exited with code 139 during the first additional
  SimCity run. Restart the same disposable AVD after verifying no process still
  owns it and removing its stale lock. Keep that interrupted log separately.
- Earlier Java touch events did not specify `SOURCE_TOUCHSCREEN` and ignored the
  injection result. Set the source and assert successful down/up injection for
  both keys and touch; do not interpret the earlier tap-only observations as a
  production touch failure. The observer remains an observation tool.
- The strengthened Twinsanity tap sequence fails twice on a rejected touch-up event
  (`InputDispatcher`: no touched foreground window). The VM is killed by fixture
  cleanup afterwards; no corresponding Java launch exception was observed.
  The last valid frame shows the Android Back/Confirm command popup; the next
  hard-coded tap misses that popup. This input sequence is not accepted. Earlier stale step/final images could remain
  in the shared output directory after failure, so the Java fixture now recreates
  only its generated capture directory before a run. The final failing replay has
  five fresh startup/step images and no stale final image. Mophun similarly resets
  its generated captures and success report. A corrected popup-targeting sequence
  completes injection checks in 26.334s, with the main menu still visible; it does
  not establish gameplay. Earlier key-only observations remain separate.
- No broad suite rerun or lint result is claimed for test-only edits. The earlier
  candidate's production checks remain dated evidence.

## Remaining acceptance

Finish edition-specific Java gameplay and save/relaunch, including the SimCity
city progression; check real-game backup round trips and broader Mophun saves/audio.
Resume N95 startup/LCD work on native ARM64 hardware or a verified compatible
execution environment. Exact #52 identification, real two-device/network sessions,
physical controllers/accessibility and native ARMv7 performance remain open.
