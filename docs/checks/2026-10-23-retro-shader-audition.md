# Standalone Retro Shader Audition - 2026-10-23

## Scope

Owner-requested comparison of all proposed shader families without FSR, motion
interpolation, extra sharpening, LCD overlay or CPU changes. This adds an opt-in
Android test harness, not production menu entries or a preset library. No public
APK/release update. The installed game application and saved settings are unchanged.

Each result uses the same frozen Deep Abyss 1.4 first-level introduction frame
from the previous Retroid capture. Source: 128 x 160; output: 384 x 480. Source
SHA-256: `98bd175605284c3908d0bcfffbdb4c726b1ec73703f39594978180cd53f502fd`.
Game images and upstream shader checkouts stay
under ignored `build/`; they are not shipped in either APK or committed.

## Compared Variants

| Number | Algorithm | Rendering |
| --- | --- | --- |
| 00 | Original | Nearest 3x control |
| 01 | SuperEagle | Native 2x, nearest fit to 3x |
| 02 | HQ2x | Native 2x with original lookup texture, nearest fit to 3x |
| 03 | HQ3x | Native 3x with original lookup texture |
| 04 | HQ4x | Native 4x with original lookup texture, nearest fit to 3x |
| 05 | xBR level 2, no blend | 3x |
| 06 | xBRZ freescale | 3x |
| 07 | ScaleFX | Five passes, native 3x; floating-point intermediate targets |
| 08 | Advanced Cartoon | 3x, upstream default parameters |
| 09 | CRT EasyMode | 3x, upstream default mask/scanlines |
| 10 | CRT Geom | 3x, upstream default curvature/mask/scanlines |

The 2x/4x variants require a nearest resampling to share the 3x viewport; this
can produce uneven subpixel widths. SuperEagle's usual final linear stock pass
is deliberately omitted: this audition excludes additional smoothing. CRT's own
mask, scanlines and gamma are part of the selected algorithm, not added effects.

## Upstream and Adapter

Source: [libretro/glsl-shaders](https://github.com/libretro/glsl-shaders/tree/cba01a48b0974a1232bea68d999a7c21bc4c233e),
revision `cba01a48b0974a1232bea68d999a7c21bc4c233e`. A local sparse checkout supplies
17 files including the stock copy shader and three HQ lookup PNGs. Original
headers remain intact. No upstream shader is added to app or test APK assets.

The test adapter selects GLSL ES 3.00, supplies the standard texture/size/MVP
and previous-pass uniforms, and runs the original algorithm bodies. HQ scale
defines match the selected upstream presets; lookup PNGs are decoded without
alpha premultiplication because all four channels encode weights.

Two compatibility adjustments were needed and verified during the audition:

- xBR no-blend and AdvCartoon write RGB only. Preserve opaque output alpha in
  their final framebuffer, rather than interpreting undefined alpha as a black
  image when exporting an Android bitmap. Other passes retain their alpha data.
- CRT Geom's non-uniform default `lum` macro collides with a local variable in
  its saturation function. Rename only that local variable during shader load;
  formulas and parameter values remain unchanged.

Header inventory: HQx is LGPL-2.1-or-later; xBR/ScaleFX carry MIT notices;
xBRZ also includes GPLv3 terms; CRT Geom is GPL-2.0-or-later. SuperEagle,
AdvCartoon and EasyMode have GPL headers without a version stated there. This
is an inventory, not clearance to bundle every file; audit selected sources and
retain all notices before production integration/distribution.

## Verification and Limits

On Retroid Pocket Classic, Adreno A12, OpenGL ES 3.2:

- Test APK assembled successfully; only instrumentation APK was installed.
- One opt-in instrumentation test completed all 11 cases in 4.281 seconds,
  including shader compilation, readback and PNG writing. This is not a frame-time benchmark.
- Original output matches every source pixel at exact 3x nearest sampling.
- Every shader produces a nonblank result that differs from the original.
- Repeated draws of the frozen frame produce identical pixels in every case.
- Local log: `build/retro-review-retroid.txt`.
- Repeated after the private-files staging option was added; all 11 cases passed
  again. Log: `build/retro-review-retroid-final.txt`.
- Numbered, labelled images: `build/retro-review-retroid/00-original.png`
  through `10-geom.png`; separate `NN-raw.png` files contain GPU pixels only.

These are composed, labelled GPU readbacks, not screenshots of a new app UI.
No claim of animated stability, full-game compatibility, measured FPS or exact
pixel equivalence with RetroArch. Owner preference and production integration
remain open. No live GitHub release audit was performed.

An optional emulator run did not reach the tests: Android terminated the app for
a startup ANR before renderer/test output. `build/retro-review-emulator.txt`
contains `Process crashed`; ActivityManager records `failed to complete startup`.
It is not an emulator pass or evidence that a specific shader failed. No emulator
image is included in the owner comparison, and unrelated emulator apps were not
stopped or reset to obtain a result.

## Reproduce

1. Prepare the pinned upstream checkout locally; sparse folders are `eagle`,
   `hqx`, `xbr`, `xbrz`, `scalefx`, `cel`, `crt`, plus root `stock.glsl`.
2. Run `tools/prepare-retro-shader-review.ps1 -SourceFrame <local-128x160-PNG>`.
3. Build/install `:app:assembleFdroidDebugAndroidTest` against an installed Debug
   app. Stage `build/retro-review` in that app's external-files `retro-review`
   directory. If shell access to external storage is blocked, stage in private
   `files/retro-review` through `run-as` and pass `-e reviewPrivate true`.
4. Run `am instrument -w -e class javax.microedition.lcdui.graphics.RetroShaderReviewTest
   -e retroShaderReview true io.github.eriark.abyssme.debug.test/androidx.test.runner.AndroidJUnitRunner`.
5. Pull the numbered PNGs for review. Do not add commercial frames to Git.
