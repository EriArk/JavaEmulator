# xBRZ and CRT EasyMode integration - 2026-10-23

## Scope and availability

The owner selected audition modes 06 (xBRZ) and 09 (CRT EasyMode). Both now
appear under Game menu > Display > Enhancements > Image preset in Debug builds.
The shared Java GLES/Mophun pipeline applies one shader to the original guest
image. Selecting a retro preset disables FSR, linear smoothing, interpolation
and LCD decoration. Size, rotation, CPU/speed, controls and custom Java shader
files are not changed. Conflicting controls return when another mode is chosen.

`RetroShader` is an optional profile integer: 0 off, 1 xBRZ, 2 CRT EasyMode;
missing/unknown values are off. Existing copy, Try / Keep / Undo and backup
serialization handle it. Java software/custom-shader configurations do not
offer these presets. Release builds ignore the experimental mode, and Symbian
does not use this renderer. This is source and a locally installed test APK,
not a release or a public APK update. Live release state was not rechecked.

## Rendering and provenance

- Crop padded Java guest textures before applying a shader, then rotate during
  host presentation. Mophun capture reuses the processed texture.
- Keep the existing one-million-source / four-million-output-pixel limits,
  cache static frames and invalidate output/history on mode, source or size
  changes. No motion work or deferred FSR sharpening remains in a retro mode.
- Use existing GPU failure fallback to ordinary rendering. Handle surface
  destruction/recreation through the existing context-owned resource lifecycle.
- Shader bodies/defaults match Libretro GLSL revision
  `cba01a48b0974a1232bea68d999a7c21bc4c233e`, ignoring line endings. Original
  headers remain in assets, with provenance and license selection recorded in
  [LICENSING](../LICENSING.md). No game images or ROMs are committed.

## Verification

Retroid Pocket Classic, Android 12, serial `MC94516AQF040305380`:

- Final selected instrumentation run: **81 passed**, no failure/skip statuses.
  `ImagePresetsTest` (4), `GamePresetsUiTest` (13), `CanvasLifecycleTest` (40),
  `EnhancementPresentationTest` (11), `MophunPresentationTest` (13).
- Cover actual GLES2 compilation, padded versus cropped equality, cache/switch
  invalidation, incompatible-effect suppression, rotated output and recovery
  after destroying/recreating a Java SurfaceView. Verify menu Try boundaries,
  persistence, exact Undo and unsupported renderer choices.
- Optional captured-frame tests were enabled with `retroShaderReview=true`
  and `imageFixture=retro-review/source.png`, not silently skipped.
- An additional installed Deep Abyss menu test passed on Retroid with all five
  presets, GPU readiness checks, portrait/landscape layouts and exact profile
  rollback. One-confirm and two-confirm runs reached the first-level introduction
  and a subsequent life-loss screen respectively. No automated navigation or
  full-game acceptance is claimed. Capture waits for the dialog exit animation;
  its assertion accepts both a dismissed dialog and the activity clearing it.
- The production xBRZ output exactly matches the selected audition image at
  384x480. CRT differs in 14,730 of 184,320 pixels, by at most **1/255 per RGB
  channel**, between the GLES2 runtime adapter and GLES3 audition adapter.
- 75 JVM tests passed. Debug app/test APK assembly passed. Lint completed with
  0 errors and 399 warnings; this is not a warning-free codebase. The final
  unsupported-renderer LCD guard was rebuilt and covered in the device rerun
  after that lint checkpoint.

Bounded synthetic cost on Retroid, 128x160 to 576x720, 4 warmups + 16 measured
uploads/draws with `glFinish`: xBRZ median **14.96 ms**, max **15.97 ms**;
CRT EasyMode median **3.95 ms**, max **4.32 ms**. The fixture includes a large
flat region. This measures upload plus shader completion, **not whole-game FPS**,
latency, sustained thermal behavior or equal quality. xBRZ is substantially
heavier and is not enabled by default.

## Local evidence

Ignored maintainer files in `build/`:

- `retro-integration-build-verified.txt`, `retro-integration-test-build.txt`:
  successful app/test assembly.
- `retro-integration-regression-final.txt`: raw instrumentation statuses,
  timings and 81-test final result.
- `retro-integration-build-final.txt`: JVM test/build result.
- `retro-integration-lint.txt`: lint checkpoint.
- `retro-integration-mophun-final.txt`, `retro-integration-mophun-gameplay.txt`:
  the additional Deep Abyss test, passing with one and two confirmations.
- `retro-review-retroid/mophun-xbrz.png`, `mophun-crt.png`, `menu.png`:
  live device screenshots with touch controls and the settings sheet.
- `retro-review-retroid/gameplay-xbrz.png`, `gameplay-crt.png`: the subsequent
  two-confirm run; scene timing differs, not a frozen-frame quality comparison.
- `retro-review-retroid/production-1.png`, `production-2.png`: production
  renders of the same private source as the earlier audition, not live captures.

Installed development APK SHA-256:
`1f23be4ed14813ab2be6aa9cf619165213bbc1ed8ca42d7cab4cdb61f6aa0e4c`.
Its filename retains the existing development base version; it is not the
published revision APK and was not uploaded as a release asset.

## Remaining acceptance

No emulator result is claimed in this checkpoint; the earlier audition's
emulator startup ANR remains separate evidence. No physical controller input,
whole-game completion, audio/save acceptance or 60 FPS guarantee follows from
these automated checks. Owner review of live moving scenes and broader GPU
coverage remain open before promoting experimental settings into a public beta.
