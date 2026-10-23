# Integer Scaling and LCD Alignment - 2026-10-23

## Scope

Unreleased source and a local Java/Mophun Debug APK installed on Retroid Pocket
Classic `MC94516AQF040305380`. No public release, version number, APK asset,
Nextcloud upload, GitHub Actions or Symbian native engine change. The Android
emulator was not used for this checkpoint. Integer geometry is not Debug-gated;
FSR and frame interpolation remain experimental Debug-only features.

## Behavior

- Add `ScreenScaleType = 3` (Integer), preserving the existing 0/1/2 values and
  current default. No profile version migration is required.
- Java and Mophun share the whole-multiple fit calculation. Use the largest
  integer factor that fits the available picture area. Below 1x, use Fit
  downscaling rather than crop the game. This fallback cannot preserve uniform
  enlarged source-pixel blocks and is deliberately not called integer downscale.
- Java resolves final Integer geometry after its native soft-key reservation
  and presentation rotation. The logical MIDlet dimensions remain those of the
  corresponding Fit calculation; native soft-key reservation still applies.
- Mophun aligns available edges and the rotated picture origin, rather than
  independently rounding a center that can land on half a physical pixel. Odd
  source dimensions may require a half-pixel pivot to put both edges on integers.
  Canvas, GLES vertices and the existing LCD overlay share that geometry.
- Keep filters, FSR, interpolation, speed, CPU budget, LCD mode/intensity and
  guest resolution independent. Integer does not automatically make FSR output
  pixel-sharp. Original/Fit/Stretch behavior is retained.
- Percentage scaling is ignored in Integer mode and disabled in Java's advanced
  editor, without discarding its stored value. Choosing a picture-size mode in
  the shared menu retains its existing reset-to-100% behavior.
- Reuse the Mophun game-bounds rectangle; do not add a per-frame geometry object.

## Checks

- `build/integer-scale-build.txt`: Debug app/test assembly, 75 freshly rerun JVM
  tests (0 failures/errors), lint. Four new JVM cases cover whole factors,
  small-area fallback, fractional limits and odd/even pixel-aligned edges.
- `build/integer-scale-verified-build.txt`: final app/test assembly and lint
  passed; 0 errors, 399 warnings (one fewer than the previous source checkpoint).
- `build/integer-scale-retroid-verified.txt`: **44/44 passed in 38.919 s** on the
  installed final artifacts:
  - 8 profile serialization/preview checks;
  - 9 LCD mask checks, including a rotated checkerboard at odd offsets;
  - 12 Mophun presentation checks, including complete pixel-array equality
    between nearest Canvas and GPU output in every quarter-turn;
  - 2 Java geometry/context-lifecycle checks;
  - 12 shared settings-menu checks;
  - 1 optional installed Deep Abyss 1.4 live-menu/geometry/capture check.
- Java geometry covers portrait, landscape, square and smaller-than-source
  areas, all four rotations, fullscreen/native soft-key modes, integral bounds,
  preserved guest dimensions and the ignored fractional size ratio.
- The real-game check selects Integer through Display/Try, verifies whole scale
  factors, switches portrait keypad / landscape gamepad / rotated GPU output,
  checks session errors, and restores the preview baseline. Countdown is stopped
  only in the screenshot fixture; timed auto-undo is not claimed by this check.

Intermediate verification found a test-fixture issue: a JSON profile without
`Version` correctly entered the legacy scaling migration and did not preserve
the new enum. The fixture now declares version 4; legacy migration code was not
changed. `integer-scale-retroid-final.txt` records that earlier 43/44 pass, and
the final 44/44 run above supersedes it. The first menu capture also caught an
orientation animation; the capture test now waits before opening the sheet.

No physical-controller feel, audio, long gameplay, FPS improvement, complete-game
compatibility or Symbian integer scaling is claimed. Screenshots were visually
inspected, but small chat previews can resample the LCD pattern themselves.

## Local Captures and Artifacts

Ignored local captures under `build/`, from the installed test fixture:

- `retroid-integer-picture-menu.png`: the actual Display sheet with Integer.
- `retroid-integer-phone-keypad.png`: 128x160 at 3x with portrait keypad/LCD.
- `retroid-integer-landscape-gamepad.png`: 128x160 at 4x with gamepad/LCD.
- `retroid-integer-gpu-rotated.png`: 3x, 90-degree presentation, FSR/LCD and gamepad.

Commercial game files are not bundled or committed. Local screenshots are not
being presented as public-release images.

- App: `app/build/outputs/apk/fdroid/debug/AbyssME-0.4.0-beta.3-r1-debug.apk`
  SHA-256: `0b9f9c97e2ab87ddb1e315db25f872b7b35940ccc878bf940d08cadcbbf8821f`
- Tests: `app/build/outputs/apk/androidTest/fdroid/debug/app-fdroid-debug-androidTest.apk`
  SHA-256: `2502a4bb90e909e393a86e24774e82643fc292daf3fb350b364d4465d041ff4f`
