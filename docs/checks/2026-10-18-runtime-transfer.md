# Display resizing, automatic setup and transfer recovery

Date: 2026-10-18. Source commit `12b2b0e4`, after `e551e05a`; not a new public APK.
Public beta.4 / code 303 was rechecked on GitHub and remains unchanged.

## Shared fixes

- A growing Java image allocated a new bitmap, but previously obtained Graphics
  objects still painted the old bitmap. Resize now rebinds live cached graphics,
  preserves pixels and updates clipping. GameCanvas grows its backing image
  before delivering the guest size-change callback.
- Live logical screen changes now notify the guest before the automatic repaint,
  including Immediate mode. Hidden screens defer notification until resume.
  Presentation-only rotation does not invent a guest size change.
- GLES geometry/filter updates are applied on the rendering thread with a live
  context. Vertex memory stays locked through the draw; replaced Android surface
  callbacks cannot detach the current view. This repairs specific shared causes,
  not every possible black screen or games that cannot resize their own assets.
- Stable-size GLES frames update the existing texture instead of redefining its
  storage every frame. Allocation is repeated only after bitmap size changes or
  context recreation. This reduces redundant driver work; no measured FPS gain
  is claimed.
- Automatic setup recognizes paired manifest width/height declarations, including
  LGE TargetLCD. Original-display metadata outranks target-display scaling hints.
  Strict numeric/bounds validation remains; uncommon explicit sizes do not expand
  the artwork-guess allowlist. Existing manual/saved profiles are not reset.
- Library transfer saves source/game/save/settings folders, granted subfolders,
  backup URI and pending access requests across process recreation. Returning
  picker results take priority over automatic rescanning. Cancelling a restored
  picker returns to a read-only preview. Import/export is never replayed without
  confirmation; existing transactional rollback remains in use.
- Symbian ignores obsolete surfaces and queued exits from a previous session.
  Surface attachment or menu dismissal while the activity is backgrounded cannot
  resume the guest. Closing marks the activity inactive before dismissing menus.

No game-title-specific branches, database migration, new game/firmware bundle,
license change or release-version change is part of this batch.

## Verification

| Check | Observed result |
| --- | --- |
| Base Phone and Handheld debug APKs | Built; lint passed with zero errors |
| Combined Phone and Handheld debug APKs | Built with `symbianExperiment=true`; lint passed with zero errors |
| JVM suites | 29 tests per variant, no failures/errors |
| Android 15 Phone emulator | Final selected suite: 98 passed, including four transfer-recovery cases |
| Physical Retroid Pocket Classic, Android 14 | 98 selected checks passed; this preceded the final picker-result follow-up, texture reuse and exit-generation read ordering adjustment |
| Retroid native Snakes UI | One explicit fixture test passed: shared menu, mapper, rotation preview/Undo and screenshot containing rendered game pixels |
| Actual GLES presentation | PixelCopy found guest pixels after logical resize, 0/90/180/270 presentation rotations, filtering changes and surface recreation on emulator and Retroid |
| Frame pacing | Canvas repaint/serviceRepaints and GameCanvas flush paths obeyed the configured budget; concurrent-producer and independent-timer checks also passed |
| Real DocumentsUI/process-death workflow | Emulator: PID 22411 was killed after the transfer activity stopped with saved state; PID 22968 consumed the returned save-folder selection, restored the game and showed Saves included / Import |
| Bounce Tales, final Phone emulator APK | Live 128x160 / 480x800 / 240x320 trials and Undo, Restart game, then Exit game completed; inspected resized menus and restarted title screen were rendered |

The final texture-reuse run passed the same 98 emulator cases, with additional
checks that successive equal-sized frames change to the expected colors. This
guards against a stale-but-nonblank texture being mistaken for successful output.

The pacing checks extend evidence for the existing limiter; this batch does not
add a universal game-speed control, frame generation or a new upscaler. The
unknown original fast-game report (#22) is not claimed resolved.

The process-death UI check uses a copy of the local Sudoku J2ME converted/save
folders, with no import committed and no original saves overwritten. The first
kill attempt was too early, before Android had stopped/saved the activity; it is
not counted as a recovery pass. Final automation explicitly waited for STOPPED
and saved-state confirmation before killing the process. Serialized regression
tests separately cover pending whole-library subfolder grants and cancellation.

## Local game sample

These are user-provided private fixtures, not files distributed with AbyssME.

| File | Manifest evidence | SHA-256 |
| --- | --- | --- |
| `2012_Apocalypse.jar` | LG 240x400 | `ba9cf3e0d6e2a6a582de5efa88b7d56e456f1bb2a34fd50ed1776475722fb78d` |
| `bombermans_71hlp22b_nmj_joc.jar` | Original 240x320, target 360x640 | `893cc24a00628944baf731a5efc58119e568c8a4414ff2064ba3db99a370dfe9` |
| `KENKEN.JAR` | Original 240x320, target 240x400 | `03a7eee6f710cd4ad1247c9f5744e383fa324026038354c59046ba6103dd852c` |
| `KrishCricketChallange.jar` | Original 176x208, target 240x320 | `6de8975d08bf59024a48b58a778ae9520eeda484393799dc16a4e8b6729fc441` |
| `BounceTales.jar` | No matching display declaration | `d69de01b5e45267820b00386e45db2296b4ca761708dcca6ca4ab03f3c604e39` |

2012 Apocalypse reached its language screen on Retroid with a 240x400-shaped
game area. Bounce Tales passed the emulator UI flow above. Manifest inspection
of the other rows is not runtime acceptance.
Synthetic metadata regressions cover original/target precedence, all supported
paired-key forms, malformed/missing dimensions and out-of-range values.

## Boundaries and local evidence

The owner's signed Handheld beta.4 and its library were not upgraded or cleared.
Retroid checks used `io.github.eriark.abyssme.symbian.debug`. Device interaction
was initially stopped after a different app appeared. Concurrent use by another
chat was an inference, not an established fact; the owner subsequently confirmed
that the device was available. The unfinished multi-game UI run is not counted
as a pass. Temporary debug packages/data and five copied test JARs remain local.

No new broad gameplay, sound, save-roundtrip, physical-controller or Asphalt 3
cold-launch acceptance is claimed. Intermittent Ghost Recon #15 remains open.

Ignored local logs under `build/`:
`overnight-base-verified-build.log`, `overnight-transfer-final-build.log`,
`overnight-combined-verified-build.log`, `overnight-emulator-verified-tests.txt`,
`overnight-retroid-final-tests.txt`, `overnight-snakes-ui-tests.txt`.
The process-death UI result is `overnight-transfer-verified-ui.txt`.
Texture-reuse follow-up: `overnight-texture-build.log`,
`overnight-texture-combined-build.log`, `overnight-texture-verified-tests.txt`.
Bounce Tales UI: `overnight-bounce-resize-ui.txt`; an earlier automation timeout
during first conversion is not included as a gameplay result.
Private captures are under `build/overnight-captures/`.
Earlier fixture mistakes and interrupted runs are retained separately and are
not included in the final pass counts.

## Physical follow-up after owner clarification

Retroid Pocket Classic / Android 14 was used exclusively for this follow-up.
The signed beta and its data were not replaced. The combined Handheld debug APK
with the final October 18 runtime fixes was installed in the existing isolated
debug package. APK SHA-256:
`b6d6d90024624b437753376f05babc5c3073215aa5c22869f41532797829cb2c`.

- `retroid-followup-runtime.txt`: 100 selected Android checks passed in 16.487 s,
  including final transfer-result recovery, texture reuse with changing frame
  colors, logical resize, presentation rotation and surface recreation.
- `retroid-followup-native-final.txt`: two opt-in Snakes checks passed in 77.920 s.
  The new scenario repeats Restart game and Home/return three times, checks the
  reopened menu and obtains nonblank guest-only screenshots after each step.
  Inspected captures show the Nokia startup logo after restart and the Iomo
  screen after return; this is not a gameplay, sound or save-roundtrip claim.
- Both combined Handheld and Phone instrumentation APKs compile with the added
  fixture checks (`retroid-followup-test-build.log`). Phone execution was not
  repeated during this physical follow-up.
- `retroid-followup-java-trials-final.txt`: the opt-in Java fixture flow passed
  in 31.531 s using the supplied Bounce Tales JAR. It selected 128x160, 480x800
  and 240x320, tried/undid each, restarted and exited. All three trial captures
  and the restarted menu were inspected: game graphics remained present. Small
  logical sizes crop fixed-size artwork, while large sizes leave it smaller;
  this is not a claim that arbitrary logical resolutions suit every game.

The external shell UI helper could not obtain an accessibility root for this
Java run. A debugger snapshot showed the UI thread idle, not a Java lock cycle;
the debugger was detached and its ADB forward removed. The final check uses
Android instrumentation input/accessibility and handles scrolling preset choices
on the small screen. The failed shell run and the initial offscreen-choice test
failure are not counted as application compatibility failures or passing checks.

The first new test attempted same-process Activity access, which Android rejects
because Symbian runs in its own process. It now uses the UI boundary. A second
fixture assertion incorrectly rejected the monochrome Nokia startup logo; the
restart check accepts multiple guest colors while the existing later-game UI
check retains its stricter color threshold. Neither failed attempt is counted
as a product failure or a passing run.

No new production-code fix or public release was made in this follow-up.
Commercial games, firmware, debug APKs and captures remain outside Git.
