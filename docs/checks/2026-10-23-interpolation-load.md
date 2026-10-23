# Interpolation Load Protection - 2026-10-23

## Scope and Availability

Unreleased Java GLES enhancement work. Effects remain Debug-only, opt-in and
off by default. No public APK, release asset, tag, Nextcloud upload or GitHub
Actions change. The live release listing was not rechecked in this checkpoint.
Mophun/Symbian rendering, guest logic, input, speed and frame limits are unchanged.

Previously Canvas requested intermediate redraws while the interpolation clock
was active regardless of rendering pressure. The new policy prioritizes real
frames when optional generation is likely adding excessive work. It does not
change FSR/sharpness, lower image resolution or rewrite profile preferences.

## Signals and Policy

- CPU wall time covers enhanced draw submission, including upload, processing,
  final presentation commands and completion-probe submission. It includes
  driver/lock stalls; it is not pure shader execution time.
- At surface setup, display refresh determines the budget: 30-240 Hz are
  accepted, with 60 Hz as the unknown/invalid-rate fallback.
- An EGL 1.5 fence after submitted work is polled with flags=0, timeout=0 once
  it is at least one budget old. Still-pending work suppresses interpolation
  immediately. At most one fence is owned; replacing it does not cancel GPU work.
- CPU over-budget samples accumulate a pressure score; three suppress optional
  work. A submission below 75% of budget reduces that score. One isolated compile
  spike does not cause CPU-only suppression.
- Recovery needs a 500 ms cooldown and three cheap submissions. New pressure
  clears recovery evidence and restarts cooldown. No idle polling/recovery timer
  is installed: subsequent actual render requests provide the samples.

Suppression completes the current source pair with real current pixels, clears
pending motion search and ends animation. A previously partial output is not
kept forever, and the same pair cannot rewind on recovery. The next source pair
can interpolate again. GL-owned source textures/history are retained, avoiding
reallocation caused by repeatedly toggling the user's interpolation setting.

If a costly draw starts suppression at its end, one final callback may be needed
to replace that intermediate image with current pixels. Real guest frame requests
continue normally. FSR still runs when selected, so this is not a solution to
FSR-alone overload. Guest submissions remain the FPS counter's unit.

## API and Lifetime Boundaries

[Android EGL15](https://developer.android.com/reference/android/opengl/EGL15)
provides the sync APIs from API 29, matching this project's minimum SDK.
The [EGL 1.5 specification, synchronization section](https://registry.khronos.org/EGL/specs/eglspec.1.5.pdf)
defines timeout=0 as a status test, not a requested wait. The probe uses
`glFlush`, not `glFinish`, and performs no GPU image readback. Driver calls can
still stall internally; this is not a promise of zero CPU cost or wait-free code.

The monitor runs outside the guest buffer lock. EGL below 1.5, a missing display
or a failed probe uses CPU-only protection. A probe failure is independent of
the image-effect error fallback. Pause, disabling interpolation, ordinary-path
fallback and context recreation release the fence and reset the policy.

This is a sampled completion/backpressure signal, not a GPU-duration query.
It does not measure compositor/scanout or the GLSurfaceView swap after the draw
callback, and cannot detect every overloaded interval. Refresh-rate changes
without a surface change are not tracked in this checkpoint. No guaranteed FPS,
thermal/battery improvement or measured input-to-photon gain is claimed.

## Verification

Fresh JVM run: 71 tests, zero failures/errors. The ten added checks cover
CPU/GPU pressure policy, isolated spikes, cooldown/recovery hysteresis, 30/60/90/
120/240 Hz budgets, invalid rates, negative clock origins, reset and completing
a pair without preventing the next pair from interpolating.

GPU checks verify that a suppressed new pair does not allocate its motion-search
program, retains the current real pixels, and recovers on the next pair. Finishing
an active pair preserves source texture IDs and FSR output against an independent
current-frame reference. Completion probes are reused/closed repeatedly without
GL/EGL errors; a detached context exercises the unavailable-probe path. Test-only
`glFinish` establishes the completed state, not production synchronization.

Canvas instrumentation injects policy pressure and verifies current-frame display,
no synthetic redraw chain, unchanged enhancement flags and no renderer-error
fallback. Existing unlocked-upload/input, resize, rotation, context lifecycle,
preview/profile, FSR quality and Mophun budget tests remain covered.

Debug app/test builds and lint passed; lint has zero errors and 400 warnings.
Retroid Pocket Classic passed all 100 selected instrumentation tests in 42.219
seconds. EGL completion probing was available. Android 15 x86_64 emulator passed
the same 100 tests in 309.546 seconds. Its EGL completion probe was unavailable,
exercising the CPU-only path. No signed/R8 release build.

The pressure transitions are deterministic injected-policy evidence, not a claim
that sustained gameplay overload or recovery was benchmarked on every GPU.
Physical-controller, thermal and end-to-end latency acceptance remain open.

## Local Artifacts

Local Debug APK: `app/build/outputs/apk/fdroid/debug/AbyssME-0.4.0-beta.3-r1-debug.apk`.
This reused filename is not the published revision APK.

SHA-256: `bb127a2cc4033384fb8892659df1e93474de0126d9b80ce27119a17f26b3ff72`.

Ignored logs: `build/interpolation-load-build.txt`,
`build/interpolation-load-retroid-targeted.txt`, `build/interpolation-load-final-build.txt`,
`build/interpolation-load-retroid-final.txt`, `build/interpolation-load-emulator-final.txt`.
If unavailable, treat these as dated maintainer observations, not attached public
artifacts. No commercial-game payload or screenshot is published in this checkpoint.
