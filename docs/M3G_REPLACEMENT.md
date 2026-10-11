# M3G Replacement Experiment

## Isolation

- Experiment branch: `codex/m3g-replacement`.
- Starting revision: `a0daaf2a96443f01f531e21c10f629efc161b638` from `main`.
- The main application continues development with its existing M3G engine.
- This branch is an investigation, not an approved engine replacement or release.
- No automatic merge into `main`, license removal or release APK publication.

Use a separate Git worktree for the experiment. Keep developer signing settings,
SDK paths, game archives, saves and diagnostic output local and ignored. Existing
private Mophun experiments in the main worktree are not moved or published here.

## Objective

Determine whether a replacement for the inherited EPL-1.0 M3G implementation
can preserve Java game compatibility and acceptable performance while allowing
a reviewed licensing path for future Wolphun integration.

The maintainer reaffirmed on 2026-10-11 that the current engine must stay in
`main` while licensing discussions continue. Develop an alternative here;
do not interpret this experiment as approval to remove the current engine.

A standalone Android candidate probe now passes 18 synthetic checks on an
x86_64 emulator and a Retroid Pocket Classic. A separate APK with the unchanged
existing engine passes the 10 shared cases on Retroid; its emulator run remains
unverified after emulator stalls/exits. The follow-up now also provides opt-in
real-host AbyssME research builds and a three-game smoke comparison. This is
not full game acceptance or approval to replace the engine. See the dated results below.

## Candidates

1. [FreeJ2ME-Plus M3G](https://github.com/TASEmulators/freej2me-plus/tree/baca76a4dda6f30abac67fb4a3db009eed05f91c/src/javax/microedition/m3g):
   selected for the first Android probe; Java software renderer, GPLv3-or-later
   headers in all 33 inspected M3G Java files. Speed and game compatibility remain
   open; this is not a full source-history or dependency license clearance.
2. [Desktop-M3G](https://github.com/vadosnaprimer/desktop-m3g/tree/fa04787e8609cd0f4e63defc7f2c669c8cc78d1f)
   plus [Java-M3G](https://github.com/bryan10328/java-m3g/tree/571baeae7be590b78a0f73a5d5e58506b5525446):
   both have MIT root licenses, but the inspected C++ Graphics3D throws
   `NotImplementedException` for bindTarget, clear, immediate rendering and
   multiple related methods. Java bindings use `org.karlsland.m3g`, not the
   MIDP namespace. Deprioritized before porting the legacy Android build;
   no claim that it cannot be improved, but not a drop-in compatibility choice.

Repository-level license labels alone are not a completed provenance audit.
Preserve original notices and record exact revisions for any imported files.

## Investigation Order

- [ ] Audit the candidate's source provenance, individual license notices and
      required dependencies; record any unresolved questions.
- [ ] Establish a baseline using the current engine on a fixed set of permitted
      JSR-184 test scenes and game variants. Keep commercial game files out of Git.
- [x] Build a minimal Android prototype with current project SDK/NDK settings,
      initially for arm64 and the x86_64 emulator.
- [ ] Exercise loading, immediate rendering, scene graphs, textures, transparency,
      depth, lights, fog, transforms, picking, morphing and skeletal animation.
- [ ] Compare representative games with the baseline, covering loading screens,
      gameplay, scene transitions, pause/resume and 2D/3D composition.
- [ ] Measure frame time, memory and stability on both an emulator and a physical
      handheld. A rendered demo alone is not compatibility acceptance.
- [ ] Decide whether to continue, try the fallback candidate or retain the existing
      engine and wait for suitable upstream permission.

## 2026-10-11: First Runnable Candidate

- [x] Inspect three pinned repositories and select an initial test candidate.
- [x] Build an isolated [Android probe](../experiments/m3g-probe/README.md),
      including a minimal Bitmap/MIDP host, without existing EPL code or native
      libraries. Normal app build settings and sources are unchanged.
- [x] Verify eight synthetic checks on Retroid Pocket Classic (Android 14,
      arm64-v8a) and an Android 15 x86_64 emulator; inspect both screenshots.
- [x] Verify the source-revision guard rejects the wrong checkout and the APK
      contains the GPL notice and no native libraries.
- [ ] Establish matching current-engine baseline scenes and pixel comparisons.
- [ ] Integrate with the real MIDP host on this branch and test permitted games.
- [ ] Complete provenance review, performance/lifecycle coverage and merge gates.

Checks: inverse transform, immediate geometry, retained/immediate pixel parity,
depth occlusion, textured geometry, translated clipping, PNG loading and 20
target release/rebind cycles. **8/8 passed on each target**, including a fresh
reinstall/launch via the checked-in verifier. This is synthetic device execution,
not physical-control, audio or full-game acceptance. No `.m3g` scene was loaded.

Build: `:probe:assembleDebug :probe:lintDebug` with Gradle 8.7/JDK 21/SDK 34.
Lint completed with 0 errors and 5 warnings (upstream indentation/deprecated
boxing, backup metadata, missing probe icon and a diagnostic string literal).
No NDK is used by this Java renderer probe. No release APK was published.

Tested APK SHA-256:
`21444f1ad71333536c9f97d6b5f407786cc9a5c0b23c4f55d7b517789841a2bb`.
The artifact remains local under the probe's ignored build directory.

Evidence: [Retroid JSON](m3g-probe-2026-10-11/retroid.json),
[emulator JSON](m3g-probe-2026-10-11/emulator.json),
[Retroid screenshot](m3g-probe-2026-10-11/retroid.png),
[emulator screenshot](m3g-probe-2026-10-11/emulator.png).
Recorded timings include cold initialization/allocations and are not an FPS
comparison. The screenshot is a diagnostic scene, not an AbyssME interface change.

The follow-up below broadens scene loading/animation and adds an independent
current-engine baseline. These first eight checks remain historical evidence.

## 2026-10-11: Scene Loading and Animation Comparison

- Added original raw/zlib `.m3g` fixtures, multiple object sections, embedded
  offsets/trailing bytes and truncated-input rejection.
- Added translation/rotation tracks, morphing and single-bone skinning, with
  numerical and visible-pixel assertions. Animation tracks are API-created,
  not yet loaded from files.
- Built a separate baseline APK using tree-hash-pinned, unchanged EPL Java/native
  M3G. Candidate and baseline do not contain each other's engines.
- Fixed the candidate loader's premature multi-section termination in the
  generated copy only. Both devices failed 2 cases before the patch and pass
  18/18 after it; the upstream checkout is untouched.
- Existing-engine baseline: 10/10 shared cases on Retroid. Across 17 frame pairs,
  0.70-0.86% of pixels differ; this is not exact parity or a compatibility score.
- Baseline emulator attempts stalled/exited the emulator without a fresh report.
  The cause is unresolved; the emulator was restored and boots normally. Do not
  describe that target as passed or change the baseline to hide the failure.
- Both APK builds and lint completed (0 errors; candidate 5 warnings, baseline
  23). Inherited native warnings remain. No public APK or mainline engine change.

[Reports, hashes, frame comparisons and device screenshot](m3g-scenes-2026-10-11/README.md).

## 2026-10-11: Real Host and Three-Game Smoke Comparison

- Added opt-in candidate and baseline AbyssME debug builds with isolated app
  IDs, build directories and data; ordinary builds keep their existing engine.
- Adapted the candidate to the actual LCDUI bitmap, active MIDlet archive and
  session-reset hook. The existing Java/native M3G and LCDUI sources are unchanged.
- Added six shared host checks. A reproduced Image2D corruption regression was
  fixed by separating the Android scratch framebuffer from texture storage.
- Candidate: 6/6 checks on Retroid and emulator; baseline: 6/6 on Retroid.
- Deep: Submarine Odyssey, Asphalt 3 3D and Colin McRae DiRT 3D reached gameplay
  on Retroid with both engines. Asphalt also reached a moving race on the
  candidate emulator. This is a fixed-variant smoke test, not full compatibility.
- DiRT's automatically selected 128x128 screen is wrong for this variant on
  both engines. Manual 320x240 gives a complete race screen; 240x320 still clips.
  The auto-profile code was not changed, and no per-game patch was introduced.
- Shared-source preparation refactoring preserves the standalone candidate's
  build/lint and its 18/18 emulator checks.

[Host adapter/build instructions](../experiments/m3g-host/README.md) and
[reports, exact variants, hashes and screenshots](m3g-host-2026-10-11/README.md).

Next proposed batch: measure sustained frame time/memory and exercise repeated
pause/resume/game switching on this same fixed sample, with current-engine
baselines. Keep auto-profile dimensions as a separately tracked host problem.
Physical controls, audio, full playthroughs and broad API coverage remain open.

## Merge Gates

Before proposing a merge into `main`:

- Document the tested game versions, device configurations and remaining failures.
- Explain meaningful visual or performance regressions rather than hiding them.
- Verify that both the native EPL engine and its EPL Java bindings can be removed
  from the resulting distribution; replacing only the renderer is insufficient.
- Review the complete resulting dependency/license combination, not just M3G.
- Preserve library data, saves, control profiles and current user-facing workflows.
- Obtain an explicit maintainer decision after presenting the comparison results.

Regular UI, input, importer and compatibility work continues independently on
`main`. Bring mainline changes into this experiment deliberately; do not move
experimental engine changes in the opposite direction before these gates pass.
