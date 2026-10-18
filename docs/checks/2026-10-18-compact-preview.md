# Compact Settings Preview - 2026-10-18

## Availability

Unreleased development after `bcb8f911`. Local debug APKs were installed for
verification only. No public release, test-build upload, Nextcloud distribution
or website deployment was performed. The public release listing was not
rechecked in this checkpoint; existing beta APKs were not changed.

## User-visible Change

The old preview strip occupied the entire screen width with a text caption and
two text buttons. The shared `SettingsPreviewBar` now occupies approximately
176x52 dp: a fixed-width countdown and save/undo icons. Both controls retain
48x48 dp touch targets, accessible names (`Keep`, `Undo`), long-press/hover
tooltips and a visible focus outline. Icons reuse the app's existing Android
save/revert symbols, also used by the common game menu.

The panel uses the dark in-game palette even when the app theme is light. The
timer can adjust text size within its fixed cell for larger accessibility fonts.
It does not request focus when appearing. Touches outside its actual bounds go
to the game; its timer/background intercept touches so they do not leak through.

All three engines use the same overlay attachment method, with start/end margins
and clearance below the shortcut row. No game layout inset is added. The existing
20-second preview, suspension/resumption, keep/undo callbacks and menu alternatives
are unchanged. This does not remap gameplay buttons to save/undo actions.

## Verification

| Check | Result | Local evidence |
| --- | --- | --- |
| Base Phone debug and instrumentation build | Passed | `build/preview-base-final.log`, `build/preview-test.log` |
| Combined Handheld/Phone debug and Phone instrumentation builds | Passed | `build/preview-combined-final.log` |
| Android 15 emulator UI/settings suites | 32 passed | `build/preview-emulator-final.txt` |
| Retroid Pocket Classic, same suites | 32 passed | `build/preview-retroid.txt` |
| Retroid real Java/Mophun presentation trials | 2 passed | `build/preview-live-retroid.txt` |
| Combined JVM tests, explicitly rerun | 38 per variant, no failures/errors | `build/preview-checks.log` and test-result XML |
| Combined lint | No errors; 406 Handheld / 413 Phone warnings remain | `build/preview-checks.log` and lint-result XML |

The four instrumentation suites are SettingsPreviewBarTest, GameChromeTest,
GameMenuLayoutTest and SettingsTrialTest. New layout tests cover 240x240,
320x240, 360x640, 640x360 and 480x320 in both app themes with 1x, 1.5x and 2x
font scales. They check bounds, text fit, touch targets, shortcut clearance,
game size preservation, theme contrast, touch routing, focus traversal and
D-pad-center activation in an attached Android activity. Lifecycle checks
cover start/suspend/resume/stop; the existing preview timing logic was not
rewritten. This is automated focus/input evidence, not physical-controller
acceptance by a person.

Early test runs exposed test-harness assumptions about independently rounded
dp dimensions and focus on an unattached view. The final checks use summed
component dimensions and an actual activity for focus/activation; those earlier
failed runs are not counted as passes. Buttons explicitly remain focusable.

## Captures and Limits

Real-game trials exercise Fit/Stretch, smoothing, both Phone orientations, all
four image rotations, Try changes, saved game-image readback and Undo. The test
uses icon accessibility names, so changing from text to icons did not bypass
interaction checks.

Inspected captures are local under `build/preview-captures/`:

- `mophun-portrait.png`: upright Mophun menu with compact preview and Phone deck.
- `mophun-landscape.png`: landscape with an intentionally rotated game image.
- `java-portrait.png`: Age of Empires II Deluxe's own pause screen with preview.
- `java-landscape.png`: additional captured Java landscape trial.

The Mophun fixture is locally named `DeepAbyss.mpn`, but the observed content is
a bubble-themed version 1.1 menu; it is not claimed as verified Deep Abyss 1.4.
These are UI/rendered-menu checkpoints, not full gameplay, audio or save tests.
Native Symbian uses the same component and built successfully, but no native
game session was run in this checkpoint. Commercial game fixtures and captures
are not added to Git by this UI patch.

The official Handheld package on Retroid was not replaced. Its library activity
was restored to the foreground after the isolated Phone debug checks. No games,
saves, profiles or firmware were cleaned up.
