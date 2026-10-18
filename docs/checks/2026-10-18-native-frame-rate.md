# Frame-rate controls across engines

Date: 2026-10-18. Baseline: `195f3a8c`. Unreleased after beta.5.
No release assets, game files, firmware or saved games were published or replaced.

## Behavior

- Java, Mophun and Symbian/N-Gage share the Frame rate limit selector in
  Game menu > More and Display. Presets: Unlimited, 15, 20, 25, 30, 50, 60.
- Try applies live, Undo restores the baseline, Keep persists per game.
  Symbian profiles remain keyed by virtual-phone identity and application UID.
- A nullable NativeFpsLimit field distinguishes old profiles (60 FPS) from
  explicit Unlimited (0). Java's FpsLimit is not repurposed. No database or
  save-file migration is needed. Imported native values are bounded to 0..240.
- Mophun delegates frame submissions to the existing monotonic FramePacer.
  The standalone Wolphun limiter is bypassed only when the host supplies its
  pacer, so selecting Unlimited does not leave a second 60 FPS throttle.
  Pause, resume and live changes reset the pacing deadline.
- The Symbian adapter uses an atomic session limit independently of guest
  refresh-rate metadata. Both vsync waits and window redraw scheduling use it.
  Direct-framebuffer polling honors lower caps without exceeding its existing
  host polling cadence. Zero does not divide, queue a pacing sleep or change
  guest clocks. The former unsigned future-vsync subtraction is replaced with
  monotonic frame-boundary scheduling.
- Patches are generated against pinned EKA2L1
  `11f5b6cd141c0884a130ee45743f1f5e17bb1783`; its checkout is unchanged.

## Verification

- Both combined debug APKs and both instrumentation APKs built successfully.
  Fresh JVM tests: 29 per variant, zero failures. Lint: zero errors;
  existing 403 Handheld / 410 Phone warnings remain.
- Physical Retroid Pocket Classic, Android 14: 27 Phone settings/pacing tests
  passed. These cover old defaults, explicit Unlimited, independent Java/native
  limits, draft isolation, save/load, cancel/undo, native JNI readback and
  per-phone/per-game isolation. The Mophun framebuffer test checks actual
  elapsed frame intervals and absence of the old hidden cap.
- Retroid Handheld debug: the same suite completed with 26 passes and one
  expected skip for a Phone-only tactile-feedback case; no failures.
- Android 15 x86_64 emulator: 26 Java/Mophun settings/pacing tests passed.
  This emulator build does not contain the arm64 Symbian engine.
- Retroid native runner: 66 new pacing/boundary/concurrent-update checks passed,
  as did the existing capture, card and file-transfer suites. A separate fresh
  host JUnit run passed the six deterministic FramePacer cases.
- Deep Abyss on Retroid Phone debug: four live trials (15, 30, Unlimited, 60),
  guest frame-submission counts over 1.5-second windows, Undo and menu exit
  passed. The game continued presenting and capped runs stayed below the
  selected upper bound, with a small measurement-boundary allowance. Captures
  show the game menu, not a full level walkthrough or unique-animation FPS.
- Snakes on Retroid Phone debug: the same four live trials, selected-value
  readback in More, Undo, rotation and touch-layout trials, screenshot content
  and exit passed. Captures show the title screen. This does not measure unique
  gameplay FPS, audio or complete-game compatibility.

An initial Snakes test incorrectly queried JNI from the instrumentation process
instead of the separate `:symbian` process. The corrected test observes the
actual session UI; JNI readback is checked by the in-process host test. This
was a test-boundary error, not evidence that the live setting failed.

## Evidence and availability

Local ignored evidence: `build/fps-final-build.txt`, `fps-checks-build.txt`,
`fps-retroid-phone.txt`, `fps-retroid-handheld.txt`, `fps-emulator-tests.txt`, `fps-mophun-live.txt`,
`fps-symbian-live-final.txt`, and the captured `*-frame-rate-*-menu/game.png`
files in the isolated debug app's external-files directory.

Local combined debug APK hashes:

| Variant | SHA-256 |
| --- | --- |
| Handheld | `85584f89d1ce369d4cf70789919933946172c36cd5043335f65539e644e8b44a` |
| Phone | `97f684d41ea5acb6cfce9a9cb97cb076ea3f18e8b7d03ef2997b15f4163a22d3` |

Only isolated debug packages were updated. The official Retroid Handheld beta.5
installation and its data were not replaced. GitHub Actions remains disabled.
Live release state was not rechecked in this task; the documented public
beta.5 artifacts do not contain this batch.

FPS caps do not guarantee a particular game speed or remove guest-imposed waits,
display refresh limits or hardware bottlenecks. Percentage emulation speed is
a separate proposed task, not an implemented feature. The previously recorded
Asphalt cold/repeated-launch problem is not claimed fixed here.
