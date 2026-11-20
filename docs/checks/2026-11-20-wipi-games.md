# WIPI and SKVM on Retroid Pocket Classic

Checked 2026-11-20 on Android 14 using the opt-in ARM64 debug package `io.github.eriark.abyssme.wipicheck.debug`. Both commercial titles opened from the Android document picker. The games, audio, captures and APK are local test materials. The ordinary build and public releases do not include this host.

| Check | Inotia: A Wanderer of Luone (Com2uS, KTF WIPI) | Metal Slug (SK Telecom SKVM, version 1.0) |
| --- | --- | --- |
| Gameplay and image | Title, new slot, class and attributes, then a priest in the village with NPCs and HUD | Title, new game, intro, controls tutorial, first jungle combat stage with soldiers and projectiles |
| Android playback | 109.8 s recording, mean −31.6 dBFS. After changing the audio route during playback, a further 17.1 s contained audio at −36.4 dBFS | 39.8 s recording, mean −29.7 dBFS, peak −0.3 dBFS |
| Touch input | Right changed class and OK confirmed the choice | Touch Down moved the game's cursor from entry 1 to 2 |
| Controller path | Directional hat changed class. The ordinary mapper assigned B to Right. Try and Keep persisted that binding and a B press event changed class after a cold relaunch | Directional hat Up moved the cursor back from entry 2 to 1 |
| Shared settings | Touch visibility and per-game physical mapping survived reopening | Selected CRT in Display and kept it. The saved game profile contained `RetroShader: 2`. After a cold relaunch, Display still showed CRT selected and the touch deck remained visible |

The controller input checks injected Linux button and hat events through the connected Xbox Wireless Controller's input device. Manual button presses, subjective speaker quality, long-session audio sync, in-game save restoration and complete playthroughs were outside these checks.

The original Inotia carrier ZIP (PID PD005362, version 01.00.05) had SHA-256 `93d5b6b8ceb5cf4ce15784e369a7af9cd8ef7a49d6f15816d665aedadc487a4a`. Its unflagged legacy directory names failed the old UTF-8 preflight. The new preflight identifies the ASCII carrier markers and forwards the untouched archive to the runtime. For Metal Slug, the original matching `2399999996.jar` and `.msd` were placed in a single-game ZIP. The JAR SHA-256 was `585c3f87d857622073b0a696d354e8c21ed23f1bf61b895a81dcd0daeb3ba11a`. The descriptor declares SKTP-1.1 and the game classes reference the SKT API.

Private evidence in `build/checks/wipi-play/` includes `gameplay.png`, `class-reloaded.png`, `class-reloaded-b.png`, `inotia-run.mka`, `after-route.mka`, `metal-stage.png`, `metal-audio.mka`, `metal-touch-down.png`, `metal-hat-up.png`, and `metal-display-reopened.png`. The Heart Catch and SKVM slider instrumentation runs each passed three tests, including legacy ZIP recognition. The shared physical-profile, presentation and touch suites passed 25 checks. Those suites complement the two per-game device checks.

## Dingoo follow-up

On the same device a small standalone Android process loaded the ARM64 libretro core from the [DingooEmu v0.3.0 release](https://github.com/AloysHF/DingooEmu/releases/tag/v0.3.0), revision `f57c9339e6021cd37c4bd694c14b0813a9b5ee72`. Its system-info entry point reported `DingooEmu 0.3.0, libretro API 1, formats app|cc|c2s|c3s, need_fullpath 1`. The shared library SHA-256 was `18f58f6a47b0ade058aa7e57e42ac4c1cfa3382b8b8f7d004831c29a05af8989`. The upstream archive includes the BSD license and third-party notices for the JIT. Local evidence is in `build/checks/dingoo/core-info.log`. Native A320 gameplay, Gemei gameplay, audio, controls, saves and AbyssME integration still need separate checks.
