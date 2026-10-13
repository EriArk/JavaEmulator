# Scene references and duplication, 2026-10-13

Research branch only. These are common M3G fixes, not title-specific overrides.
The mainline renderer, root license and public APKs are unchanged.

## Problems and Fixes

- Direct references were stored as a set. The API counts each reference slot,
  including a texture in two units or a camera that is both a child and active.
  Clearing one slot could therefore remove a still-used object from `find`,
  `getReferences` and animation traversal. Preserve each non-null reference.
- `duplicate()` cloned animation tracks instead of retaining their identities.
  Copies now have independent track lists referencing the same tracks.
- Duplicating an AnimationTrack added its sequence reference to the original
  object, not the copy. Correct the destination without cloning the sequence.
- Node copies discarded alignment settings. A two-pass identity map restores
  links after the complete tree has been copied, including SkinnedMesh skeletons,
  sibling/cross-branch links and active cameras. External references stay external.
- `World.setActiveCamera(null)` accepted an invalid argument. It now throws
  before changing the existing camera. An initially camera-less World still
  duplicates and loads correctly: the file loader skips a null camera index
  instead of passing it to the setter.

The contract comes from the JSR-184 API documentation for
[Object3D](https://nikita36078.github.io/J2ME_Docs/docs/jsr184/javax/microedition/m3g/Object3D.html),
[Node](https://nikita36078.github.io/J2ME_Docs/docs/jsr184/javax/microedition/m3g/Node.html)
and [World](https://nikita36078.github.io/J2ME_Docs/docs/jsr184/javax/microedition/m3g/World.html).
Implementation is original helper code plus guarded preparation patches against
the existing pinned FreeJ2ME-Plus source. No EPL implementation was copied.

## Verification

`M3gCopyTest` uses the public API on both engines, not candidate internals.
The initial nine cases all reproduced failures on the old candidate. Comparing
against the native engine also caught an invalid null-camera assumption in the
first test draft; the test was corrected and rejection is now covered explicitly.
The final suite contains eleven cases, including an original minimal M3G file.

- Native baseline on Retroid Pocket Classic / Android 14: **11/11 passed**.
- Candidate before the final loader guard: **139/139 on both Retroid and the
  Android 15 x86_64 Phone emulator**, no skips.
- Final Handheld candidate: **140/140 on Retroid**, no skips.
- Final Phone candidate: **62/62 shared API/host/raster cases on the emulator**,
  including all eleven new cases, no skips. The 78 unchanged candidate-only
  cases were not repeated after the final loader guard; do not report a final
  140-case Phone run.

[Raw test output and artifact hashes](scene-copies-20261013/) distinguish the
pre-loader-guard Phone run from final artifacts. Baseline hashes were taken from
the installed APKs because Gradle replaced previous output locations while
switching experiment variants. Neither ordinary user package was replaced or
cleared. No physical-control, audio or new full-game run was performed here.

Commands use the isolated `m3gExperiment` application IDs from the host README.
The tests are included in `verify.ps1`; its current expected candidate total is
140 (62 shared API/host/raster cases and 78 candidate-only cases).
Private game inputs are not packaged. API fixture passes are not a claim of
complete-game parity, physical-control acceptance or production readiness.

## Independent Contributor Build Fix

The signing fix is also on `main` in `b7501f17` (issue #1). In a checkout without
`keystore.properties`, ordinary Handheld and Phone debug APKs built successfully,
both JVM suites passed 14 tests each, and both lint tasks completed. `help` works
both normally and with Bitrise enabled without a signing path. An explicit
release assembly stops before packaging with a setup message. Existing package
IDs and release-key fields are unchanged. No public release was produced.
