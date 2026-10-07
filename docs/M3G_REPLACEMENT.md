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

No alternative implementation has been imported, built or accepted on this
branch yet. The initial commit records the scope and acceptance gates only.

## Candidates

1. [Desktop-M3G](https://github.com/vadosnaprimer/desktop-m3g) with
   [Java-M3G](https://github.com/bryan10328/java-m3g): first feasibility candidate.
   Review both implementations and their dependencies before importing code.
2. [FreeJ2ME-Plus M3G](https://github.com/TASEmulators/freej2me-plus/tree/devel/src/javax/microedition/m3g):
   fallback candidate if the first approach is incomplete or unsuitable.

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
