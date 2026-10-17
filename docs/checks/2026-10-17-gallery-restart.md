# Gallery formats, square library and game restart

Date: 2026-10-17. Unreleased development source after `04ad49df`.
Related: [#30](https://github.com/EriArk/JavaEmulator/issues/30),
[#31](https://github.com/EriArk/JavaEmulator/issues/31).
The latest published beta was rechecked: `v0.3.0-beta.3`, October 15.
This change does not replace its APKs or publish games/firmware.

## Behavior

- Library menu offers Automatic / Wide / Portrait for Gallery. A single dp
  calculation supplies columns and card dimensions, with title/font space
  reserved. Images use fit-center; original covers, custom art and banners are
  not cropped, rewritten or stretched. Fallback art follows the chosen shape.
- Square and near-square available viewports use the 48 dp compact header and
  no detail pane. Game actions remain available through X / long press.
  List/Grid remain icon views; game screen settings are unrelated.
- Restart game is in the common menu, not buried in advanced settings. Cancel
  has initial focus; Back returns without restarting. Confirmation retains the
  paused menu until the host handles teardown. Saved settings remain in use;
  unconfirmed previews are discarded and unsaved progress may be lost.
- Java waits for the previous MIDlet process to exit. Mophun joins the stopped
  worker before recreating the activity. Symbian stops/prepares the same virtual
  phone and launches the same UID. No second engine is started over a live one.
- A native restart initially stayed paused because a dismissed old menu later
  called its host. Closed Symbian menus now detach dismissal listeners and ignore
  stale callbacks; the regression records pause calls at restart and checks that
  the old UI cannot pause its replacement.

## Checks

| Surface | Result |
| --- | --- |
| Base Handheld and Phone | Debug APK/test APK builds passed; 29 JVM tests per variant, zero failures |
| Base lint | Zero errors; existing warning totals 393 Handheld / 400 Phone |
| Combined Handheld and Phone | Debug APKs including Symbian built successfully with `-PsymbianRelease=true` |
| Physical Retroid Pocket Classic | Android 14, 1240x1080 landscape at density 320 (620x540 dp); final targeted run: 9 passed, 1 Phone-only test skipped |
| Square emulator | Android 15, 720x720 at density 240 (480x480 dp); all three Gallery formats passed at font scales 1.0 and 1.3 |
| Short-landscape emulator | 960x640 at density 320 (480x320 dp); Gallery and card-layout tests passed |
| Wide-landscape emulator | 1280x720 at density 213 (about 961x541 dp); Gallery formats and controller-focus checks: 2 passed |
| Phone emulator | 720x1280 at density 320 (360x640 dp); Gallery, library chrome and menu tests: 9 passed, including rotation |

Gallery instrumentation uses generated portrait, wide and extra-wide banner
fixtures, plus missing-art fallbacks. It checks saved preference/recreation,
dimensions, image scaling, scrolling and source-byte preservation. Geometry JVM
tests cover explicit-format precedence and larger fonts. Controller focus/menu
tests check initial game focus, header focus preservation, cancel/Back and
explicit restart confirmation. Tests do not establish human controller acceptance.

On physical Retroid, manual UI checks observed:

- Bounce Tales (Java): Restart game terminated the previous MIDlet process
  (PID 18343) and launched a new one (18487), returning to its main menu.
- Deep Abyss (Mophun): Restart game returned to the game's New game menu.
- Snakes (N-Gage): restart replayed startup and reached Start Game after fixing
  the stale pause callback. No extra Resume command was required after the fix.
- Gallery Wide and Portrait, compact header and visible controller focus were
  captured in the isolated debug app, separate from the owner's release library.

These are restart/startup checkpoints, not full-game, audio or save-roundtrip
acceptance. The user's black screen after display changes remains a separate
investigation. Real RG Cube, split-screen resizing and broad accessibility
acceptance remain unverified. No release build was produced for publication.

## Evidence

[Inspected development captures](../../screenshots/development/2026-10-17-gallery-restart/)
include physical Retroid Wide/Portrait/menu captures and an emulator 1:1 capture.
They show debug data, not a bundled game collection or the currently released UI.

Ignored local logs under `build/`: `gallery-base-final-build.log`,
`gallery-base-checks-build.log`, `restart-final-build.log`,
`restart-final-tests-build.log`, `gallery-restart-retroid-final.log`,
`gallery-square-verified.log`, `gallery-short-verified.log`,
`gallery-wide-verified.log`, `gallery-phone-verified.log`. Failure logs from earlier test-fixture mistakes
remain local; they are superseded by the named final runs, not counted as passes.
The large-font rerun is `gallery-square-large-font.log` (1 passed).

After checks, the temporary debug/test packages were removed from Retroid,
retaining debug app data for future tests. Its signed Handheld beta.3 installation
was not upgraded or overwritten. Emulator size/density/font overrides were reset.
Final package/UI readback showed only `io.github.eriark.abyssme` on Retroid,
version `0.3.0-beta.3` / code 302, with 39 games still listed in its library.
