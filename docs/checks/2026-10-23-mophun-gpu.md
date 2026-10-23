# Mophun GPU Presentation - 2026-10-23

## Scope and Availability

Unreleased source and local Debug app/test builds. No public APK, release asset,
tag, Nextcloud upload or GitHub Actions change. The live release listing was not
rechecked in this checkpoint. Effects remain Debug-only, opt-in and off by default.
Symbian rendering and Mophun VM scheduling/audio/input are unchanged.

Mophun's optional GLES2 surface uses the existing Java EnhancementPipeline rather
than a separate set of effects. Touch controls and LCD effects remain Android
View overlays. Original Canvas presentation is used when effects are disabled,
the GPU has not presented the requested layout yet, or a rendering error occurs.
An error disables the adapter for that Activity lifetime without changing saved
preferences. Try / Keep / Undo and profile serialization retain their contracts.

## Frame and Resource Boundaries

- Screen publishes complete pixels, serial and real completion time under one
  snapshot lock. Host redraws cannot invent new guest frames or new timestamps.
- The GL thread copies changed source frames only, then uploads/processes without
  holding that lock. UI copies are skipped while the GPU picture is ready.
- One volatile latest layout/settings state replaces older requests. There is
  no queue of game frames. Existing UI vsync drives coalesced dirty renders;
  unchanged frames without active interpolation do not request another GL draw.
- Shared source-paced interpolation, scene/motion guards and CPU/EGL pressure
  policy apply. FSR runs before rotation; final sharpening avoids an additional
  output-sized texture. Original/Fit/Stretch geometry retains game-area clipping.
- Menu pause suppresses interpolation. Activity pause or disabling effects
  releases textures, targets, snapshots and fences. Context recreation rebuilds
  source history rather than using old GL names.
- Canvas is uncovered only by a GL-thread event after the draw callback's
  buffer swap, not inside the callback itself. The initial emulator run exposed
  premature readiness as PixelCopy ERROR_SOURCE_NO_DATA (105/106 passed).
  The adapter was corrected, not merely the pixel assertion relaxed. The ordering
  was checked against [AOSP GLSurfaceView](https://github.com/aosp-mirror/platform_frameworks_base/blob/master/opengl/java/android/opengl/GLSurfaceView.java).
- Source/output texture budgets remain 1,048,576 / 4,194,304 pixels. Failure uses
  the Canvas picture, not a black screen or a profile rewrite.

The view still uses the existing UI-vsync polling for source discovery and LCD/
touch drawing. This does not claim zero UI cost, battery savings, or universal
overload detection. EGL/compositor/swap timing is not a precise GPU benchmark.
At this checkpoint, in-app Mophun screenshots/artwork retained source-image
capture; system captures included the composed enhanced picture. The subsequent
[capture checkpoint](2026-10-23-mophun-capture.md) adds enhanced/original exports.

## Verification

New instrumentation covers atomic/concurrent snapshot identity, repeated host
redraws, actual GPU quadrant pixels at 0/90/180/270 degrees, letterbox/clipping,
minification, repeated pause/resume, pressure suppression and error fallback.
The Mophun settings test now also checks image effects stay in the draft until
Try, independently of CPU budget, game speed and frame cap.

The optional MophunGpuGameTest accepts an installed game ID via `mophunGame`,
uses temporary settings previews, captures the composed screen and undoes the
preview on exit. It neither bundles games nor proves physical-button operation.
Retroid captures from Deep Abyss 1.1 T300 and 1.4 multiscreen show the portrait keypad, landscape
gamepad/LCD, game menu and restored Canvas picture. The 90-degree game-image
rotation in the landscape fixture is deliberate. This is a menu/animated-title
smoke check, not a full playthrough or a sustained gameplay benchmark. An optional
synthetic controller confirmation in 1.4 also reached the Level 1 start screen;
it is not a physical-controller or completed-level acceptance result.

Fresh JVM run: 71 tests, zero failures/errors. Debug app/test assembly and lint
passed (lint: zero errors, 400 warnings). The programmatic, callback-owned MophunGpuView explicitly suppresses the
inapplicable XML-inflater constructor warning.
No signed/R8 release build was made.

Android 15 x86_64 emulator initially passed 105/106 checks in 369.581 seconds,
exposing the first-buffer readiness race described above. After the fix, all six
Mophun presentation checks passed in 34.643 seconds, with the same strict
PixelCopy assertions (no retry/relaxation added). The unchanged other 100 cases
were not rerun on the emulator after that adapter-only correction. Its optional
Deep Abyss 1.4 composed-UI smoke check passed in 109.578 seconds before that fix.
The final APK's six Mophun presentation checks passed again in 29.024 seconds.
Its Deep Abyss 1.4 composed-UI check, including synthetic confirmation, also
passed on the final APK in 105.593 seconds.

Retroid's final APK passed all 106 selected checks in 47.184 seconds, plus the
Deep Abyss 1.4 composed-UI/level-start check in 9.807 seconds. This includes the
readiness correction and VM/audio pause before potentially waiting for GL teardown.

Physical-controller feel, listening quality, sustained gameplay/frame pacing,
thermal behavior and end-to-end input latency remain unaccepted. A passed
snapshot test is not a claim that interpolation improves every Mophun game.

## Local Evidence

Local Debug APK: `app/build/outputs/apk/fdroid/debug/AbyssME-0.4.0-beta.3-r1-debug.apk`.
The reused filename does not identify the published release APK.
SHA-256: `01366e83d823f2d9d56c9a460fb9351d2c11bc045e6ff86e8ccef1e167a0382d`.

Ignored build logs: `build/mophun-gpu-build.txt`, `build/mophun-gpu-final-build.txt`,
`build/mophun-gpu-surface-build.txt`, `build/mophun-gpu-final-unit.txt`,
`build/mophun-gpu-delivery-build.txt`.
Instrumentation: `build/mophun-gpu-retroid-targeted.txt`,
`build/mophun-gpu-retroid-full.txt`, `build/mophun-gpu-retroid-final.txt`,
`build/mophun-gpu-retroid-game.txt`, `build/mophun-gpu-retroid-game14.txt`,
`build/mophun-gpu-retroid-surface-final.txt`, `build/mophun-gpu-retroid-surface-game.txt`,
`build/mophun-gpu-emulator-final.txt`, `build/mophun-gpu-emulator-surface.txt`,
`build/mophun-gpu-emulator-game14.txt`, `build/mophun-gpu-retroid-delivery.txt`,
`build/mophun-gpu-retroid-delivery-game.txt`, `build/mophun-gpu-emulator-delivery.txt`,
`build/mophun-gpu-emulator-delivery-game.txt`.
System captures: `build/mophun-gpu-retroid-*.png`.
If local files are unavailable, treat these as dated maintainer observations,
not attached public artifacts. Commercial games and captures are not added to Git.
