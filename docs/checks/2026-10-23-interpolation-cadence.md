# Source-Paced Interpolation - 2026-10-23

## Scope and Availability

Unreleased Java GLES presentation changes. FSR/interpolation remain Debug-only,
opt-in and off by default. No public APK, release, tag, Nextcloud deployment,
GitHub Actions or Mophun/Symbian renderer change. The live release listing was
not rechecked in this checkpoint. Guest clocks, FPS limits, input processing
and game logic are unchanged; this is not CPU overclocking or a 60 FPS patch.

## Shared Causes Fixed

Previously the interpolation clock started at GPU upload and compressed every
eligible source interval into at most 33.3 ms. A stable 20 FPS game supplies a
frame every 50 ms: its generated motion finished early, then held the current
image until the next submission. Host upload delays were also mistaken for the
game's cadence and could restart a full delay after the image was already old.

All three shared submission paths now timestamp completed pixels: Canvas paint,
GameCanvas rectangular flush, and extended-image flush. The completion time is
written under the pixel buffer lock and uploaded with that image's serial and
geometry. GPU processing remains outside the lock. No extra CPU copy or frame
queue was added.

The presentation window is now the source interval, capped at 50 ms:

| Stable source rate | Window | Change from the prior cap |
| --- | ---: | --- |
| 30 FPS | About 33.3 ms | Same nominal window |
| 25 FPS | 40 ms | About 6.7 ms longer, no early finish/hold |
| 20 FPS | 50 ms | About 16.7 ms longer, no early finish/hold |

For intervals over 50 ms, presentation skips the overdue prefix, rather than
accelerating the entire interval. A 150 ms pair begins two-thirds of the way
through its source timeline and finishes within the remaining 50 ms. Draw time
advances this timeline at its real rate; delayed uploads/draws consume the window
instead of restarting it. Repeated or earlier draw timestamps cannot rewind it.
Non-increasing source timestamps reset temporal eligibility, including a valid
negative `nanoTime` origin. Startup, intervals below 20 ms and pauses over 200 ms
still use real frames.

The extra delay at low frame rates is an explicit smoothness/latency tradeoff.
It applies only with experimental interpolation enabled. This is a bounded
algorithmic window, not an input-to-photon guarantee: host/GPU stalls add delay,
and no optical or physical-controller latency measurement was made.

## Verification

Before changing production code, three new JVM regressions failed against
`ca237a9c`: early finish/hold at 20/25 FPS, accelerated replay of a long source
interval, and backwards phase on an earlier draw time. The final clock suite
adds seven tests to the previous five and changes the old cap/midpoint assertions
to the new deliberate timing contract.

- Five-second synthetic timelines at source 20/25/30 FPS and host 60/90/120 Hz
  check evenly advancing represented time (within floating-point rounding).
- Variable intervals, fast streams, pauses, late rendering, reset, negative
  origins and non-increasing timestamps check bounds and monotonicity.
- GPU tests verify exact quarter/half/three-quarter/current pixel positions at
  source 20/25/30 FPS, plus a deliberately late first draw of an uploaded pair.
- Canvas instrumentation verifies timestamps in all three submission paths.
  The existing stalled-presentation test now also checks that the clock received
  the uploaded frame's timestamp, not a newer producer's time. While presentation
  is held, another frame can be submitted and an input callback is processed.
- Existing artifact guards, rotation, padding, resize, context recreation,
  profile preview/restore, FSR comparisons and Mophun budget tests remain covered.

Debug app and test APK builds passed. Fresh JVM run: 61 tests, no failures/errors.
Retroid Pocket Classic: all 96 selected instrumentation tests passed in 40.656
seconds. The Android 15 x86_64 emulator passed the same 96 in 405.936 seconds.
Lint: zero errors, 400 warnings. No fresh signed/R8 release build.

The existing Retroid synthetic 240x320 -> 720x960 texture-output benchmark
reported interpolation-only median/P95 of 2.912/3.241 ms and both effects of
11.442/11.760 ms. This is submission plus `glFinish` over 30 measured iterations
after warmup, not sustained game FPS or a paired before/after speedup. Longer
interpolation windows can produce more synthetic draws per source frame and
therefore more total GPU work; no thermal/battery improvement is claimed.

## Installed Preview and Remaining Limits

On Retroid, Bounce Tales was opened from the Debug library. Both enhancements
were enabled via Try changes at 50% FSR sharpness; ADB A-button input reached the
first level introduction with the preview active. Its captured image and menu
were inspected; expiry restored ordinary rendering without keeping settings.
This smoke check is not a sustained smoothness, audio, complete-
game, physical-controller or latency acceptance test.

Exact source-time pacing does not guarantee equally spaced visible motion in
every game. The two-texture history cannot recover dropped frames or invent
future motion. Variable cadence can still cause holds/forward jumps; the
four-pixel motion search, integer pixel sampling, conservative artifact guards,
GPU load and display scheduling remain limits. There is no extrapolation or
claim of universal 60 FPS. Frame counters still count guest submissions, not
synthetic redraws.

Local APK: `app/build/outputs/apk/fdroid/debug/AbyssME-0.4.0-beta.3-r1-debug.apk`

SHA-256: `c58c8c644dddfccb4a331c9e564f30a31dbd9f980b2f854891ab8a00019fe060`

This reused Debug filename is not the published revision APK. Ignored evidence:
`build/interpolation-cadence-before.txt`, `build/interpolation-cadence-build.txt`,
`build/interpolation-cadence-final-jvm.txt`, `build/interpolation-cadence-lint.txt`,
`build/interpolation-cadence-retroid.txt`, `build/interpolation-cadence-emulator.txt`,
`build/cadence-settings.png`, `build/cadence-game-preview.png`, `build/cadence-restored.png`.
If unavailable, these are dated maintainer observations, not public attached
artifacts. No commercial-game payloads or screenshots are published in Git.
