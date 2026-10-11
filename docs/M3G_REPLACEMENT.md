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

A standalone Android candidate probe now builds and passes eight synthetic
checks on an x86_64 emulator and a Retroid Pocket Classic. It is **not** wired
into AbyssME or accepted for real games. See the dated results below.

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
- [ ] Build a minimal Android prototype with current project SDK/NDK settings,
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

Next: broaden synthetic scene loading/animation checks and compare the same
scenes against the current engine before attempting a branch-only host adapter.

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
