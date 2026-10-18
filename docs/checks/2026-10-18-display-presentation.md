# Java Display Presentation - 2026-10-18

## Availability

Unreleased source after beta.6, based on `035de5c4`. Local debug APKs were built
and installed for verification only. No release, public test-build upload,
Nextcloud distribution or website deployment was performed. Public beta.6 APKs
were not changed. Game fixtures, logs and captures remain local and are not
redistributed with this document.

## Shared Cause and Repair

`Canvas.updateSize()` previously called `repaintInternal()` even when only the
host viewport changed. `updateRenderingSettings()` did the same for filtering.
The guest paint path copies its normal backing buffer into `offscreenCopy`.
Direct-flush paths can have submitted newer pixels directly to that presentation
buffer, so invoking guest paint can replace them with older or empty content.
Relevant paths include GameCanvas, Siemens ExtendedImage and Vodafone SpriteCanvas;
these APIs share the fixed mechanism, but were not each exercised with a real game.

Host-only updates now request presentation of the existing frame. A genuine
logical width/height change still delivers the existing resize notification and
repaint. Explicit guest repaint and display show/hide behavior are unchanged.
There are no title/hash exceptions, new settings or game-data changes.

Mophun and native Symbian have separate rendering paths. They were inspected;
their renderers were not modified by this repair.

## Regression Evidence

Two new `CanvasLifecycleTest` checks failed on the previous implementation:

- A directly flushed `0xFF123456` frame was replaced by the older green guest
  paint after a presentation change.
- The actual surface failed to retain the submitted color after host changes.

After the fix, the tests preserve exact submitted pixels through rotation,
Fit/Stretch and filter changes. The surface check covers software surface,
GLES and hardware surface modes, including surface destruction/recreation.
The buffer check also covers updates while the display is hidden.

| Check | Result | Local evidence |
| --- | --- | --- |
| Before-fix focused tests, Android 15 emulator | 2 failures, expected reproduction | `build/presentation-before.txt` |
| Emulator Canvas lifecycle + rotation | 26 passed | `build/presentation-emulator.txt` |
| Emulator startup + settings trials + presets | 30 passed | `build/presentation-emulator-settings.txt` |
| Retroid Pocket Classic, same five suites | 56 passed | `build/presentation-retroid.txt` |
| Retroid Java/Mophun live display trials | 2 passed | `build/presentation-live-retroid.txt` |
| Base Phone debug + instrumentation build | Passed | `build/presentation-base.log` |
| Combined Handheld/Phone debug + Phone instrumentation build | Passed | `build/presentation-combined.log` |
| Combined JVM tests | 38 per variant, no failures/errors | `app/build/symbian-experiment/test-results/` |
| Combined lint | No errors; 405 Handheld / 412 Phone warnings remain | `build/presentation-checks.log` and lint XML reports |

An initial Retroid installation attempt ran before packaging completed and
failed. Its subsequent old-binary result is excluded from the evidence above
(`build/presentation-retroid-stale-install.txt`). After build completion, both
new APKs installed successfully and the full 56 checks were rerun.

## Real Game UI Checks and Limits

The optional `presentationTrials=true` test exercises all four image rotations,
both Phone orientations, Fit/Stretch, smoothing, Try changes and Undo. It reads
back the game's saved screenshot as well as capturing the UI, so Android menu
chrome alone cannot satisfy the nonblank-image assertion.

- Java fixture: local `aoe-check/aoe.jar`, Age of Empires II Deluxe. The inspected
  capture shows the game's own "Pause / Press any key" screen. This establishes
  retained game output through the trial, not active gameplay or full compatibility.
- Mophun fixture: local `aoe-check/DeepAbyss.mpn`. Despite its filename, the
  inspected capture is a bubble-themed version 1.1 menu. Do not label this as
  verified Deep Abyss 1.4 gameplay.
- Inspected captures: `build/presentation-captures/java-0.png` and
  `mophun-90.png`; other captured rotations are in the same local directory.
- No native Symbian game was run in this checkpoint. Audio quality, saves,
  physical controller interaction and complete-game acceptance were not tested.
- This does not close Ghost Recon 2 issue #15 or establish the cause of every
  reported black screen. Real guest-resolution changes and startup failures
  remain distinct from the reproduced presentation-only overwrite.

The official `io.github.eriark.abyssme` Handheld package was not replaced.
Its library activity was restored to the foreground after the isolated Phone
debug tests. No game/save/profile cleanup was performed.
