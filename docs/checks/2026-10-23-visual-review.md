# Same-Frame Visual Review - 2026-10-23

## Scope

Capture-only Android test helpers; no production renderer, saved profile, release
gate, version or public APK change. Experimental enhancements remain Debug-only
and opt-in. This is an owner review package, not a beta publication.

Retroid Pocket Classic rendered a frozen Deep Abyss 1.4 first-level introduction
frame, 128 x 160, at 384 x 480. The source was copied directly from the guest's
presented pixels after automated confirmation, not cropped from an enhanced
screenshot. The existing live test restores its settings preview afterward.

## Labels

- A: Pixel sharp, nearest sampling, FSR off.
- B: Smooth, linear sampling, FSR off.
- C: FSR EASU/RCAS, sharpness 0%.
- D: FSR EASU/RCAS, sharpness 50%, equivalent to the Enhanced preset.
- E: FSR EASU/RCAS, sharpness 100%.

All five use the same source pixels, exact 3x size and no rotation, LCD or
interpolation. The detail sheet magnifies an identical crop of each GPU result
using nearest sampling. Labels and sheets are composed by the test harness:
they are not screenshots of an application comparison page. Source/captures
remain ignored local artifacts, not bundled commercial game assets.

The separate synthetic three-panel interpolation capture shows previous,
intermediate and current frames. It verifies an intermediate position, not
perceived smoothness, end-to-end latency or a universal 60 FPS result.

## Reproduction and Results

- `:app:assembleFdroidDebugAndroidTest`: BUILD SUCCESSFUL. Only the test APK was
  reinstalled; production/runtime sources and the installed Debug app were unchanged.
- `MophunGpuGameTest#installedGameKeepsTouchLcdAndPreviewAcrossGpuModes`, with
  `mophunGame` pointing to the maintainer's installed fixture, `mophunPlay=true`
  and `reviewSource=true`: 1 passed. Exports `visual-review-source.png` to app
  external files. Log: `build/visual-review-live.txt`.
- `EnhancementPresentationTest`, `imageFixture=visual-review-source.png`:
  8 passed, including `exportOptionalFiveModeVisualReview`. Log:
  `build/visual-review-gpu.txt`.
- `EnhancementPipelineTest#smallSpritesKeepTheirAreaAcrossPartialTilesAndOddMotion`,
  `enhancementCapture=true`: 1 passed. Log: `build/visual-review-motion.txt`.
- Pulled local images: `build/visual-review-level-sheet.png`,
  `build/visual-review-level-detail.png`, `build/visual-review-level-a.png`
  through `-e.png`, and `build/visual-review-motion.png`.

FSR visibly resamples text and textured boundaries relative to nearest output;
more RCAS sharpening does not recover the original pixel structure. The sheets
are provided for owner preference review, not an assertion that one filter is
best for every game. No fresh emulator, audio or physical-controller acceptance
is claimed in this capture session. No live GitHub release audit was performed.
