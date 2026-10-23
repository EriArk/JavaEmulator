# Interpolation Artifact Guards - 2026-10-23

## Scope and Availability

Unreleased Java GLES enhancement work. FSR/interpolation remain Debug-only,
opt-in and off by default. No public APK, release asset, tag, Nextcloud upload
or GitHub Actions change. The live release listing was not rechecked in this
checkpoint. Ordinary rendering, guest timing/input and Mophun/Symbian rendering
are unchanged. There are no game-name exceptions.

## Reproduced Defects and Repairs

Four new tests were first run against the previously installed `a2d90c2b`
Retroid Debug app. All four failed before the production changes:

- A newly appeared red pixel became black when neighboring motion was borrowed.
- A disappeared pixel pulled unrelated blue background into its current black area.
- The nine-sample cross accepted motion despite changed diagonal details.
- A locally matching moving patch left a ghost during a broad red-to-blue cut.

The estimator now samples a full 3x3 patch, still nine samples per candidate.
Candidates whose patches extend outside the logical viewport are rejected,
rather than matching repeated clamped pixels. Before intermediate warping,
resolve verifies the previous/current colors at both full-motion endpoints.
Disagreement retains the current real pixel. These are photometric endpoint
checks, not a second optical-flow field or forward/backward flow consistency.

The flow texture's alpha now stores the best raw RGB residual. Once per source
pair, a 1x1 GPU pass samples 16 distributed flow positions; at least 12 residuals
>= 0.12 mark a broad cut. Resolve then retains current pixels for that transition.
The next source pair replaces this state. Resources are released with the other
pipeline targets/programs. No CPU image scan/readback or guest-buffer lock is added.

## Regression Coverage

Seven new tests cover the four original defects plus:

- Textured horizontal, vertical and diagonal shifts in both directions, checking
  exact interior intermediate positions and the completed current frame.
- A localized new object while unaffected blocks continue interpolating.
- Recovery on the frame following a cut, then history reset on viewport resize.

Existing HUD, border, padding, small dimensions, color disagreement, paused/fast
streams, FSR, fused presentation, Canvas lifecycle, settings preview, profile
serialization and Mophun budget checks remain in the selected suite.

The first candidate passed 26/27 pipeline tests; the scrolling test incorrectly
included a border block whose full matching patch was out of bounds for a
four-pixel shift. Its exact-interpolation assertions now cover interior blocks
only. Existing edge tests still require current-real-pixel fallback; the shader
was not weakened to interpolate unsupported border data.

Debug app and instrumentation APK builds passed. Fresh JVM run: 54 tests, zero
failures/errors. Lint: zero errors and 400 warnings, not warning-free.
Retroid Pocket Classic: all 93 selected instrumentation tests passed in 41.113
seconds. The same 93 passed on the Android 15 x86_64 emulator (Google SwiftShader)
in 228.307 seconds. No fresh signed/R8 release build.

## Cost Boundary

The existing synthetic benchmark alternates shifted 240x320 checker images,
targets 720x960 and measures submission plus `glFinish`, after five warmups,
over 30 samples. It uses the texture-output reference path, including its
separate RCAS target, not the fused Canvas final presentation.

Retroid final run (milliseconds):

| Mode | Median | P95 |
| --- | ---: | ---: |
| Both effects off | 0.127 | 0.154 |
| FSR only | 9.716 | 10.083 |
| Interpolation only | 2.817 | 3.121 |
| Both effects | 11.470 | 12.324 |

The guard adds a small GPU pass and endpoint texture reads; it is a correctness
change, not a measured speedup over the old shader. There was no paired old/new
cost experiment or locked device clocks. These synthetic costs do not establish
game FPS, input latency, thermals or all-scene performance.

SwiftShader's interpolation-only median/P95 were 7.820/14.438 ms; both effects
were 42.738/58.042 ms. Host-dependent software timings are not handheld evidence.

## Installed Preview and Limits

Bounce Tales was opened on the final Retroid Debug build. Through Display >
Enhancements > Try changes, FSR at 50% sharpness and interpolation were enabled.
ADB A-button input reached the first level introduction with the preview active.
The image and UI rendered; the preview expired and restored ordinary rendering
without keeping game settings. Captures were inspected. This is not sustained
gameplay, audio, physical-controller or end-to-end latency acceptance.

Interpolation still has a four-pixel motion radius and a 33.3 ms capped
presentation window. Repeated textures, mixed motion and thin objects against
unchanged backgrounds can remain unsmoothed or artifact-prone. Conservative HUD,
endpoint and border guards prefer real pixels. The cut guard is coarse and may
miss transitions with extensive shared imagery. No artifact-free or universal
60 FPS claim; guest logic/input rates are not increased.

Local APK: `app/build/outputs/apk/fdroid/debug/AbyssME-0.4.0-beta.3-r1-debug.apk`

SHA-256: `c716e76d85e6cd6596ed0916b3f9b689c370d2f50de127599b7e94b57ef4c8e6`

The reused filename is not the published revision APK. Ignored evidence:
`build/motion-guards-before-retroid.txt`, `build/motion-guards-final-build.txt`,
`build/motion-guards-final-tests-build.txt`, `build/motion-guards-retroid-targeted.txt`,
`build/motion-guards-retroid-final.txt`, `build/motion-guards-emulator-final.txt`,
`build/motion-guards-start.png`, `build/motion-guards-settings.png`,
`build/motion-guards-preview.png`, `build/motion-guards-restored.png`.
If unavailable, these are dated maintainer observations, not public attached
artifacts. No game payloads or commercial-game captures are added to Git.
