# AbyssME - Current Product and Development Status

**Last updated: 2026-10-13.** Verification dates are recorded per section.
This is the starting point for website copy,
announcements, progress posts and project handoffs. It includes work that exists
only in local experiments or private test builds. Describing that work here
does not publish its implementation or make it a released feature.

Read this page for the current state, [CHANGELOG](../CHANGELOG.md) for dated
changes, and the linked evidence for detail. This is a maintained snapshot,
not a live feed. Verify GitHub Releases, recent commits and build notes before
calling a version "latest" after this date. If local evidence is inaccessible,
attribute its results to the dated maintainer record below; do not infer a pass.

## Product in Plain Language

AbyssME runs old mobile Java games on Android phones and gaming handhelds.
It is an independent J2ME Loader fork focused on browsing a game collection,
using physical controllers, and adjusting each game's controls and picture
without a long setup process. It requires Android 10 or newer. Games are not
bundled. The app UI is English.

- **Handheld:** controller-first, landscape-only, no on-screen keypad.
- **Phone:** portrait and landscape, touch input and on-screen controls.
- Both variants have independent app data and can coexist.
- Experimental Mophun support is being developed inside the same app, not as
  a separate end-user product. It is not in the public release or source tree.
- Embedded Symbian / N-Gage support is the next research direction. The EKA2L1
  source audit is documented; no integration has been built or shipped.

## Availability: Do Not Mix These Channels

| Channel | Current identifier | What a reader can obtain |
| --- | --- | --- |
| Public download | [v0.1.0-beta.2](https://github.com/EriArk/JavaEmulator/releases/tag/v0.1.0-beta.2), published 2026-07-20 | Handheld and Phone release APKs; Java games, not Mophun |
| Public development source | `main`; latest build change: [b7501f17](https://github.com/EriArk/JavaEmulator/commit/b7501f17) | Unreleased Java/UI/storage improvements listed below; debug builds no longer need a private signing configuration. Not all changes are in the downloadable beta |
| Private combined test snapshot | `0.1.0-beta.2-experimental.15`, 2026-10-11 | Handheld and Phone debug APKs containing Java plus experimental Mophun; supplied separately to testers, not a GitHub release |
| Local-only implementation | Ignored optional-engine source and diagnostics | Mophun integration, game-specific experiments and test fixtures; public descriptions below, implementation not published |
| Separate research branch | [`codex/m3g-replacement`](https://github.com/EriArk/JavaEmulator/tree/codex/m3g-replacement), code checkpoint `beff7fc9` | Corrected animation curves, loop transitions, long rotations, weighted tracks and sequence loading, following scene-copy and GPU work. Final Retroid: 163 checks. Final Phone: 85 shared/animation checks. Fresh Retroid Asphalt race capture. GPU stays opt-in; production switch and beta remain pending |

The experimental number is a distribution label: build 15 still reports
version code **102** and `0.1.0-beta.2` / `0.1.0-beta.2-phone` internally.
Identify a test APK by the dated filename and hash, not Android's version label.
Release packages are `io.github.eriark.abyssme` and
`io.github.eriark.abyssme.phone`; test packages append `.debug`.

Build 15 SHA-256:

```text
8c3fae3ec67f5991bf0ab684b8607a5339a9f0d8e09cf217852f7edc709075e3  AbyssME-phone-test-2026-10-11-15.apk
9c61f344ff307d2e901226f964eefb82098ccdf1abff4b3798cabb893dc0cb73  AbyssME-handheld-test-2026-10-11-15.apk
```

## Public Beta Baseline

The published beta has Gallery/List/Grid, search, favorites, folders, per-game
settings, controller mapping, artwork, first-launch compatibility preparation,
and separate Handheld/Phone APKs. Phone provides virtual keys and direct touch.
It accepts JAR/JAD and archive sources through Android's file picker.

An Android launch-intent entry point exists for external frontends. Do not say
ES-DE or Beacon is fully verified: real cold/warm launch and return testing is
still tracked in [#7](https://github.com/EriArk/JavaEmulator/issues/7).
See [beta.2 release notes](releases/v0.1.0-beta.2.md) for the released scope.

## Implemented Since Beta.2: Public Source, Unreleased

| Area | Current development behavior | Evidence / boundary |
| --- | --- | --- |
| Library and small screens | Compact layouts, reachable menus, Gallery covers separate from List/Grid icons, controller focus, custom art and conservative automatic selection | [Compact UI](COMPACT_LIBRARY.md), [artwork](ARTWORK.md); not every tiny-screen/device combination is accepted |
| Controller mapping | Graphical phone keypad, per-game named schemes/presets, button identification, D-pad/stick directions and diagonal-to-single-key bindings, compact mapper pages | `c49fef57`, `ba252272`; physical-controller/disconnect acceptance remains open in #9 |
| Phone controls | Phone numeric keypad or virtual Gamepad, portrait game area above buttons, landscape side controls, size/opacity/position settings | [Settings](SETTINGS_TRIALS.md); released beta.2 uses the older workflow |
| Picture settings | Shared Display/Controls pages in library and game, live Java image rotation, size/smoothing, reversible Try/Keep/Undo preview | [Settings trials](SETTINGS_TRIALS.md); expert compatibility settings remain under Advanced |
| Screenshots | Optional on-screen camera button and menu action; PNGs in Pictures/AbyssME | `4f4d1468`; visibility can be disabled |
| Transfer and backup | J2ME Loader import, selection/preview, AbyssME backup/restore through menus, copied saves and supported profiles, non-destructive migrations | [Transfer](LIBRARY_TRANSFER.md); source files remain intact, access restrictions still apply |
| Nested-folder permissions | Import pauses for the requested folder grant and resumes; wrong-folder/cancel recovery, readable messages, save-folder validation | `d39ed742`; pending access survives screen recreation, not process death |
| Installer | Compact scrolling details with reachable actions, persistent errors, archive recognition through pickers, mismatched JAD/JAR continuation retaining source identity | `696da084`, `7ba384b2`; URI-alias duplicates remain #20 |
| Automatic preparation | Conservative metadata/API/resource hints, preservation of existing settings and explicit display declarations | [Checks](LOCAL_TESTING.md); static heuristics, not running multiple models or guaranteeing the best profile |

## Local and Private Work: Experimental Mophun

This summary was checked against the maintainer's local integration notes and
dated test-build READMEs on 2026-10-11. The source is intentionally absent from
the public checkout; a reader does not need access to private files to understand
the scope. Do not describe these items as public-release features.

- Direct `.mpn` import, game launch, per-game profiles and controls use the same
  AbyssME library and mapper. Reimport preserves the existing game data.
- Runtime work includes executable decoding, rendering, pause/resume, native
  game dialogs and input cleanup. Original game executables are not patched.
- Phone uses the shared numeric keypad and Gamepad layouts in both orientations.
  Physical and touch input ownership is tracked separately to avoid cancelling
  another held input. Handheld remains controller-only.
- Missing artwork can be captured from a presented game frame. Optional-engine
  folders are preserved even when the engine is absent from a later build.
- Deep Abyss 1.1 and the inspected 1.4 binary have targeted compatibility work.
  The known 1.4 invalid Level 0 state has a narrowly scoped, backed-up repair;
  it is not a universal save-file converter.
- Game-controlled quit/save/resume, readable system dialogs, sprite disposal
  and tile-map writes received fixes and focused checks. Leaving through the
  host Library action warns about progress; it is not automatic saving.
- MIDI playback is implemented for the verified Deep Abyss 1.4 profile.
  Playback preparation/start and lifecycle were checked programmatically.
  General Mophun audio and listening quality on a physical handheld are not accepted.
- The optional screenshot button captures Mophun frames without pausing its VM.

**Observed:** Deep Abyss menus, populated gameplay, movement/jumping, collection,
life loss, game-controlled save/resume and return to the library in recorded
emulator checks. Earlier Retroid checks cover startup, rendering and selected
instrumentation, not final physical-button ergonomics or the latest build.
The 2026-10-11 post pack contains a fresh first-level Deep Abyss 1.1 frame.

**Not confirmed / not implemented:** next-level completion/transition, full
playthrough, broad Mophun compatibility, general audio, robust model detection
for arbitrary files, `.mpn` folder indexing or compressed-container import,
and parity with Java's live picture settings. Do not turn one working game into
a platform-wide compatibility claim.

Maintainer evidence locators, relative to the local project or its sibling
test repository (not public download links):

- `local-games/wolphun-prototype/README.md`: dated implementation and runtime checks.
- `../testfiles/AbyssME/2026-10-07_v0.1.0-beta.2-experimental/README.md`: first combined snapshot.
- `../testfiles/AbyssME/2026-10-07_v0.1.0-beta.2-experimental.3/README.md`: Mophun touch snapshot.
- `../testfiles/AbyssME/2026-10-11_v0.1.0-beta.2-experimental.15/README.md`: current build evidence.

## Verification and Remaining Work

Build 15's recorded checks: **132 selected Android cases reported OK per
combined variant**, including 27 optional-engine checks, with variant-specific
skips. JVM suites passed 14 tests per variant. This is not every project test
and does not mean 132 physical-device or game-compatibility checks.

A genuine J2ME Loader 1.8.2-open library was imported through Android DocumentsUI
on the emulator after seven folder grants. Both imported RMS files matched
their original hashes and source files were unchanged. The reporter's Samsung
phones and every storage provider have not been verified.

Current open work, checked on GitHub on 2026-10-11:

- [#20](https://github.com/EriArk/JavaEmulator/issues/20): different Downloads URIs can duplicate one source; existing duplicates are not merged.
- [#15](https://github.com/EriArk/JavaEmulator/issues/15): Ghost Recon 2 SE 128x160 still has a recurring black screen.
- [#14](https://github.com/EriArk/JavaEmulator/issues/14): compatibility heuristics need broader validation.
- [#17](https://github.com/EriArk/JavaEmulator/issues/17), [#9](https://github.com/EriArk/JavaEmulator/issues/9), [#3](https://github.com/EriArk/JavaEmulator/issues/3), [#12](https://github.com/EriArk/JavaEmulator/issues/12): implemented UI/input/orientation/contrast changes still have acceptance or release follow-up; open does not mean no implementation.
- [#7](https://github.com/EriArk/JavaEmulator/issues/7): real external-launcher checks.
- [#4](https://github.com/EriArk/JavaEmulator/issues/4): broader regression coverage. Debug signing setup [#1](https://github.com/EriArk/JavaEmulator/issues/1) was fixed and closed on 2026-10-13.
- [#13](https://github.com/EriArk/JavaEmulator/issues/13): experimental Mophun; [#11](https://github.com/EriArk/JavaEmulator/issues/11): broader themes/skins/customization, not a shipped theme editor.

## Next Direction: Symbian and N-Gage

On 2026-10-11 the maintainer selected embedded Symbian / N-Gage support inside
the existing application, prioritizing a simple user experience over a quick
external-app handoff. The [source audit and staged plan](SYMBIAN_INTEGRATION.md)
pin EKA2L1 to `11f5b6cd141c0884a130ee45743f1f5e17bb1783`.

The inspected Android bridge already exposes device setup, app lists/icons,
UID launch and input. It needs lifecycle, storage and UI adaptation; our optional
engine hook also needs to support more than one provider without breaking Mophun.
The proposed experience is one-time virtual-phone setup, a shared game library
and backups through the menu. Firmware is user-supplied, not bundled.

**Current evidence: source review only.** No EKA2L1 native build, imported
firmware, Symbian game session or new APK resulted from this batch. Original
N-Gage and N-Gage 2 need separate acceptance; do not advertise either as shipped.
EKA2L1's GPL terms require a separate review of the existing EPL M3G combination.

The Symbian prototype's next step remains an isolated native build/bootstrap
and dependency inventory, followed by environment import and one controlled
game session. The maintainer then prioritized the separate M3G alternative
experiment below. #20 source-identity aliases remains open; it has not been
fixed or silently dropped. The maintainer confirms each next implementation batch.

## Separate Experiment: Alternative M3G

On 2026-10-11 the maintainer explicitly retained the current engine in `main`
while developing an alternative only in `codex/m3g-replacement`. Licensing
discussions remain a possible route; no engine removal or root-license change
has taken effect.

The initial branch work produced two standalone Android test APKs: one using pinned
FreeJ2ME-Plus M3G software-renderer sources, the other using the unchanged
tree-hash-pinned existing EPL Java/native engine. Neither APK contains the other
engine, updates either AbyssME app, or connects to the real MIDP game host.
Desktop-M3G/Java-M3G were inspected first but deprioritized after finding
unimplemented core immediate-rendering methods.

**Verified:** both diagnostic APKs built; Android lint finished with 0 errors
(candidate 5 warnings, baseline 23). The candidate passed 18 synthetic checks
on each of Retroid Pocket Classic (Android 14, arm64) and an Android 15 x86_64
emulator. The existing engine passed the 10 shared cases on Retroid. New cases
cover raw/zlib `.m3g` loading, multiple sections, offsets/truncation, translation,
rotation, morphing and single-bone animation. A generated-source loader fix
resolved premature multi-section termination in the candidate on both devices.

Seventeen Retroid frame pairs differ in about 0.70-0.86% of pixels, primarily
simple triangle edges; this is not exact parity or a game compatibility score.
The baseline emulator attempts stalled/exited without a completed report; the
cause remains unresolved and that target is not accepted. The emulator was
restored without wiping data. No current-engine changes were made to hide this.
Those initial probes did not test real games. Controllers, audio, sustained
performance and complete M3G coverage remain unverified. No replacement or
probe APK was released.

See the [dated reports, frames and hashes](https://github.com/EriArk/JavaEmulator/blob/9e758abe/docs/m3g-scenes-2026-10-11/README.md)
and [reproducible probe](https://github.com/EriArk/JavaEmulator/tree/9e758abe/experiments/m3g-probe).

### Real-host follow-up: 2026-10-11

The branch now also has opt-in AbyssME research builds with separate package
IDs, libraries and output directories. The candidate connects to the real
LCDUI bitmap, MIDlet resources and session-reset hook. Ordinary app builds
retain their existing engine; no production Java/native M3G source was edited.

**Verified:** six shared host checks pass for the candidate on Retroid and the
emulator, and for the unchanged baseline on Retroid. A regression test reproduced
Image2D corruption from reusing texture storage for the Android framebuffer;
a separate scratch buffer fixes it. Both full-host debug/test APKs build. The
standalone candidate still passes 18/18 emulator checks after build-script
refactoring; its rerun lint reports 0 errors and 6 warnings. Full-host lint and
the entire application suite were not rerun in this batch.

Deep: Submarine Odyssey 1.0.2 (Java, **not Mophun Deep Abyss**), Asphalt 3 3D
1.1.5 and Colin McRae DiRT 3D 1.0.6 reached gameplay on Retroid with both engines.
Asphalt also reached a moving race with the candidate in the emulator. These
are fixed-variant smoke checks, not complete-game or broad compatibility claims.
DiRT's auto-profile selected 128x128 on both engines; manual 320x240 rendered
the full race layout. The auto-profile heuristic remains unchanged, with no
per-title code patch. Initial Deep pause/resume observations belong to the
earlier candidate build, as distinguished in the capture report.

[Source, exact variants, artifact hashes and eight screenshots](https://github.com/EriArk/JavaEmulator/blob/d91321b0/docs/m3g-host-2026-10-11/README.md)
are public on the experimental branch; the APKs and game archives are not.
There is no mainline engine or licensing change.

### Runtime follow-up: 2026-10-11

Added identical opt-in host observation hooks and a bounded collector in the
experimental branch. Both measured builds pass six host checks on Retroid;
candidate also passes six on the emulator. Six collector parser checks pass.

One matched Retroid Asphalt pair (Honolulu, Mustang GT, 240x320) completed about
91 seconds per engine without collector rejection or process changes. The median
of window paint medians was 3.66 ms for the current engine and 8.74 ms for the
candidate. This measures host paint cost, not a game-speed ratio; it is one short
pair with uncalibrated observation overhead. Candidate shadows and filtering
visibly differ. Short PSS traces cannot establish absence of memory leaks.

The emulator candidate resumed Asphalt three times through Home/Recents in the
same process, then reached a DiRT race after confirmed host exit and switching.
DiRT still needs the manual 320x240 research profile. Emulator surface timing
was rejected because a polling delay could lose frames; that result is not an
accepted FPS figure. No native-baseline emulator rerun, full app suite, new lint,
physical control/audio acceptance or release APK in this batch.

[Report, raw windows, artifact identities and screenshots](https://github.com/EriArk/JavaEmulator/blob/08f38c78/docs/m3g-runtime-2026-10-11/README.md).
That batch proposed longer observations and isolation of rendering differences;
the following entry records the next bounded step, not complete acceptance.

### Filtering follow-up: 2026-10-11

An original public-API scene reproduced incorrect blending at clamped texture
edges. A guarded generated-source fix now clamps the continuous texel position
before interpolation; nearest/repeat behavior is covered. No per-game patch,
upstream checkout edit or mainline engine change was made.

Both isolated host builds compile. Six host plus eleven raster checks pass on
Retroid for both engines, and on the emulator for the candidate; six collector
checks pass. Ordinary alpha cases passed before the fix, so the darker Asphalt
shadow is still unresolved. Standalone probes, full app suite and lint were
not rerun in this batch.

DiRT and Deep reached gameplay in both approximately 90-second Retroid runs.
DiRT candidate presentation was 16.54 Hz; baseline surface timing was rejected
for a polling gap. Both Deep runs presented at 60.00 Hz, but measured paint
callbacks were absent, so renderer cost cannot be inferred. These are not
game-logic FPS, deterministic replays or broad compatibility results. Candidate
Deep returned through Home/Recents to its own pause menu in the same process;
continued gameplay after that return was not verified in this batch.

[Fix, tests, exact builds, limitations and screenshots](https://github.com/EriArk/JavaEmulator/blob/86d79068/docs/m3g-filtering-2026-10-11/README.md).
That batch proposed shading isolation and direct-flush timing; the following
entry records those checks without claiming complete replacement readiness.

### Shading and direct-flush follow-up: 2026-10-11

Two original scenes reproduced differences against the native baseline:
materials were ignored with no active lights, and parallel fog treated constant
clip W as camera distance. Generated-source fixes preserve emission/diffuse
alpha and recover parallel eye depth independently of depth-buffer state.
Neither fix is a per-game patch, and production engine sources remain unchanged.

Both measured host builds pass **32/32** checks on Retroid; candidate also passes
32/32 on the emulator. The standalone candidate retains 18/18 on the emulator;
six collector checks pass. Full app suite and lint were not rerun. New hooks
measure successful bind-to-release intervals for direct-flush games, including
intervening game code and release/readback, not isolated renderer cost.

Both Deep opening-gameplay runs completed about 45 seconds on Retroid. Median
of window target-session medians was 3.24 ms for baseline and 8.46 ms for the
candidate, despite 60.00 Hz surface presentation on both. These are moving,
non-synchronized scenes, not a renderer-speed ratio or game-logic FPS. Candidate
Deep resumed actual gameplay after Home/Recents in the same process, with two
post-resume captures showing progress. This is one cycle, not save/reload or
process-death acceptance. Asphalt reached a moving race but still shows the
hard black shadow; its cause remains unresolved.

[Source, failed fixtures, final reports and screenshots](https://github.com/EriArk/JavaEmulator/blob/044d9a28/docs/m3g-shading-2026-10-11/README.md).
That batch proposed tracing the remaining shadow and separating renderer/copy
cost. The following checkpoint records the outcome.

### Beta preparation and tone replacement: 2026-10-11

Maintainer authorization now covers finishing the engine, merging when ready,
choosing appropriate licensing and publishing a beta. The earlier per-batch
confirmation restriction is superseded; readiness and redistribution checks
remain required. Ordinary builds still use the existing engine.

The trace reproduced RGB texture replacement losing vertex/default alpha.
The alternative renderer now preserves it, handles color/alpha write masks
independently, and clips sprites outside camera near/far planes. Asphalt reached
a moving race with translucent shadows on Retroid in the combined host.
No game-specific rendering override was added. Six scene-graph/picking cases
and bounded phase timings were added; bitmap copying was not the dominant
measured cost in the recorded Asphalt sample.

An additional EPL dependency was found in the inherited tone compiler. The
candidate replaces it with original Apache-2.0 code for notes, rests, tempo,
resolution, volume, repeats and blocks. Direct game ToneControl calls and tone
streams now use timed Android MIDI playback, including completion and replay.

**Verified:** 61 focused candidate checks pass on Retroid and Phone emulator;
native Retroid baseline has 52 passes and two candidate-only skips. Independent
JDK MIDI parsing passes 14 checks, shared JVM tests pass 14 per variant, and the
standalone candidate passes 18 emulator cases. The full combined app suite has
132 cases per device with variant-specific assumptions, not 132 unconditional
passes. Thirteen blocking XML lint findings were corrected; warnings remain.

[Detailed results, APK identities, failures and screenshots](https://github.com/EriArk/JavaEmulator/blob/codex/m3g-replacement/docs/m3g-beta-readiness-2026-10-11/README.md).
The current combined build remains local: private Mophun sources and ROMs are
not committed. Production source arrangement, complete dependency/source/notice
review and signed upgrade/save checks still precede the merge and public beta.
No root-license change or release has occurred at this checkpoint.

### Sparse textures and controlled performance: 2026-10-12

Fixed crashes when a game uses a later texture unit without an earlier one:
texture coordinates, transforms, wrapping and mip state now follow the same
packed layer order. Two regressions failed before the fix and pass on both
engines. The external angle approximation was replaced by platform `Math.atan2`,
with tests for ordinary and special values. This resolves that source-inventory
item, not the whole licensing/release review.

**Verified:** the final Java-only, non-debuggable candidate passes 58 focused
checks on Retroid and the Phone emulator, with zero skips; native Retroid passes
49 shared checks. Asphalt reaches a moving race and host exit. These are not
new combined-Mophun acceptance results. A repeatable eight-scene benchmark now
records images, wall/CPU timing, build identities and thermal status.

The matched non-debuggable runs still show roughly 3.3-4.8 ms per synthetic frame
for the candidate versus 0.85-0.94 ms for native. These costs are not game FPS or
total GPU time. Removing debug execution explains much of the earlier overhead;
it is not a newly delivered production speedup. A slower sampler experiment was
discarded, and no rendering-quality setting was reduced. All eight candidate
image CRCs remain unchanged across the compared builds.

[Code checkpoint, raw checks, benchmark data and screenshots](https://github.com/EriArk/JavaEmulator/blob/codex/m3g-replacement/docs/m3g-performance-2026-10-12/README.md).
Performance parity remained open. That checkpoint proposed GPU rasterization;
the implementation below instead accelerates CPU rasterization while preserving
the shared software buffers. Dependency, packaging, signed-upgrade and
save-preservation gates remain.
No production merge, root-license change or release APK was made in this batch.

### Native raster acceleration: 2026-10-12

Moved supported scanline/pixel work into a C++ implementation derived from the
pinned GPL FreeJ2ME-Plus renderer. Scene setup remains Java; unsupported drawing
states keep the existing Java path and see the same color/depth buffers. Fixed
mixed repeat/clamp texture axes in both host and standalone candidate preparation.
No legacy EPL rendering code was incorporated into the new native implementation.

**Verified:** 63 checks pass on each of Retroid Handheld arm64 and Phone emulator
x86_64, including exact native/Java pixels across 40 combinations, eight mixed-path
scenes and malformed-packet rejection. Asphalt reaches a moving Retroid race.
Same-APK non-debuggable benchmark comparisons reduce eight synthetic frame costs
from 3.23-4.67 ms to 1.69-2.56 ms, with identical CRCs and no quality reduction.
This is a rendering improvement, not a measured 1.74-2.02x game-FPS claim.

The earlier legacy GLES baseline remains faster (0.85-0.94 ms). Multitexturing,
mipmaps and exponential fog still use Java; full replacement acceptance and the
existing publication gates remain open. Ordinary builds, main's engine and
release APKs are unchanged. Next implementation work is to accelerate the remaining
common drawing paths without weakening their rendering behavior.
[Code, provenance, reproduction and artifact identities](https://github.com/EriArk/JavaEmulator/blob/1b302bb9/experiments/m3g-host/native/README.md).

### Layered textures and exponential fog: 2026-10-12

Extended native drawing to four texture layers with independent coordinates,
transforms, wrapping/filtering and blend colors, including ADD/BLEND/DECAL.
Exponential fog no longer forces Java rendering. Zero-density fog now leaves
both models and sprites unchanged, instead of introducing a tint. Mipmaps and
sprite rasterization still use Java. Shared-image arrays are pinned once;
unsupported or invalid native input leaves the Java fallback available.

**Verified:** 66 checks pass on Retroid Handheld and Phone emulator, including
400 layered combinations, 12 shared-image cases and zero-density mesh/sprite
regressions. Twelve same-APK benchmark scenes have identical pixels. Newly
accelerated fog/layered scenes cost about 1.7-1.9x less than Java. Specialized
single-layer loops and direct JNI reduce expansion overhead, but simple-scene
cost remains slightly above the narrower earlier checkpoint; legacy GLES is
still faster. Deep: Submarine Odyssey reaches gameplay on Retroid, responds
to direction input and returns from its pause menu. Audio and a full playthrough
are not accepted by this observation.

Next implementation work is mipmap selection and sprite acceleration, while
retaining their rendering behavior. Main's engine, licensing and release APKs
remain unchanged. [Implementation, checks and measured limits](https://github.com/EriArk/JavaEmulator/blob/3605df3a/experiments/m3g-host/native/README.md).

### Mipmaps and sprites: 2026-10-12

Moved mipmapped texture sampling and Sprite3D pixel rendering to native code,
preserving shared depth/color buffers, alpha, crop, filtering and Java fallback.
Fixed fractional-coordinate loss, width-only scaling of rectangular/NPOT levels,
and stale mipmaps after image edits or rendering into a texture. The pinned
upstream checkout remains unchanged; patches are applied during candidate preparation.

**Verified:** final Handheld Retroid and Phone emulator each pass 71 checks,
zero skips. Added 64 mipmapped scene comparisons, 160 sprite combinations,
fractional-sampling/cache invalidation regressions and malformed-packet checks.
All 18 same-APK benchmark scenes have identical pixels. Mipmap workloads cost
about 1.27-1.38x less than Java, sprites 1.57-1.65x less; these are synthetic
rendering costs, not game FPS. Asphalt 3 reaches a running race on Retroid.

The existing spatially dithered mip-level approximation is retained; this is
not true trilinear filtering. Legacy GLES remains faster, and complete-game,
audio and production-replacement acceptance are not established by this batch.
Next: improve remaining filtering/scene-processing gaps with fixes and focused
comparisons, not a per-title patch collection. Main's engine, licensing and
release APKs are unchanged.
[Implementation, artifact hashes and timings](https://github.com/EriArk/JavaEmulator/blob/b624a71e/experiments/m3g-host/native/README.md).

### Trilinear filtering and level selection: 2026-10-12

Replaced the mipmap checkerboard approximation with interpolation of neighboring
levels, including alpha, before material blending. Both Java and native paths
use the corrected calculation: strong compression on one axis no longer collapses
the requested detail level to the base image. Nearest-level filtering now selects
the closest level. No new end-user setting is needed; each game's requested
level/image filters remain independent.

**Verified:** final Retroid Handheld and Phone emulator each pass 74 checks,
zero skips. Added analytical/known-color regressions, not just agreement between
implementations. All 18 same-APK benchmark scenes match exactly; fourteen
non-mip workloads keep their preceding checkpoint pixels. Standalone probe builds.
This batch adds renderer evidence, not new complete-game/controller acceptance.

True trilinear workloads cost 5.017/6.286 ms natively versus 6.345/7.822 ms in
Java. They are more expensive than the old approximation; precomputed level
scales reduce overhead without reverting the quality improvement. Q8 LOD and
isotropic filtering remain explicit precision limits. This is not legacy-GLES
performance parity, anisotropic filtering or a game-FPS claim.

Next implementation target is geometry/lighting preparation and the remaining
renderer performance gap. Main's engine, licensing and release APKs are unchanged.
[Implementation, reference specifications and measurements](https://github.com/EriArk/JavaEmulator/blob/610302b1/experiments/m3g-host/native/README.md).

### Geometry and lighting preparation: 2026-10-12

Fixed shared vertices receiving the wrong side's lighting on two-sided polygons.
Colors are now reused across a draw with separate front/back entries, including
flat shading; subsequent material, light and transform changes invalidate them.
Packed transforms stop at the active vertex count instead of processing old
scratch-buffer tails. Sparse texture slots retain reusable coordinate buffers
without retaining inactive texture state; unlit models skip unused light setup.

**Verified:** final non-debuggable research APKs pass 81 checks each on Retroid
Handheld and Phone emulator, zero skips; both variants and standalone probe build.
Twenty same-APK native cache-on/off scenes retain exact pixels; all eighteen
preexisting workloads also keep the preceding checkpoint's pixels. A new
1,089-vertex lit grid costs 5.315/5.955 ms per frame with caching versus
6.276/7.379 ms without it (240x320 / 320x240). That is 15-19% less frame cost
than fully uncached lighting, **not** a comparison against the previous
adjacent-only cache or a game-FPS claim. Asphalt 3 reaches a moving Retroid
race with track, car and HUD; sound and full-game acceptance were not tested.

The larger remaining speed gap calls for GPU rasterization rather than further
small CPU-only tweaks. Next proposed implementation: a bounded GPU drawing path
with the software renderer retained as fallback/reference, preserving rendering
semantics and mixed 2D/3D output. No production switch, license change or release
is implied by this checkpoint.
[Implementation, regression scope and artifact hashes](https://github.com/EriArk/JavaEmulator/blob/197f9955/experiments/m3g-host/native/README.md).

### First GLES3 rendering path: 2026-10-12

Implemented opt-in hardware rasterization for prepared lit geometry and one
RGB/RGBA texture: perspective coordinates, nearest/bilinear filtering, alpha
rejection, write masks, viewport clipping and depth. Unsupported effects retain
the native-CPU/Java path. Both color and depth are exchanged transactionally,
so hardware and software draws can alternate without losing earlier content.
The caller's EGL context is restored. Root licensing and main's engine are
unchanged; GPU is off by default even in these research APKs.

Fixed an observed transfer bottleneck during implementation: bulk copies and
shader channel/depth packing replace Java per-pixel conversion. Linear-filter
samples fell from 4.935/4.718 ms to 2.107/2.052 ms without changing GPU pixels.
Compared with CPU in the final **same APK**, linear filtering costs
2.107/2.052 versus 2.802/2.241 ms, and the lit grid costs 5.122/5.642 versus
5.322/5.932 ms (240x320 / 320x240). Nearest-filter scenes remain slower on GPU.
This is not a general speedup or game-FPS claim: per-submesh buffer round trips
remain, and hardware coverage/interpolation differs from software scanlines.

**Verified:** both variants build and pass 87 checks each on Retroid and Phone
emulator, zero skips. The six GPU-specific checks require actual GPU execution;
the 81 earlier checks use the default CPU path. Six benchmark scenes record
Adreno GPU draws, while fourteen unsupported scenes retain exact CPU pixels.
Perspective-interior mean/max RGB differences are about 2.0/4 byte levels on
the two devices; this is a bounded fixture result, not global conformance.
No new real-game GPU, audio or physical-controller acceptance is claimed.

Next implementation: retain consecutive GPU draws until CPU access or target
release, reducing readback overhead, then extend effects such as blending/fog
while preserving mixed rendering. No release or engine switch yet.
[Code, driver evidence, limits and comparison images](https://github.com/EriArk/JavaEmulator/blob/5b777ae2/experiments/m3g-host/gpu/README.md).

### Resident GPU Frames and Effects: 2026-10-12

Implemented the next step above on the separate research branch. Consecutive
supported meshes share GPU color/depth until CPU rendering, sprites, clear,
bind/release or reset requires synchronization. Image2D targets commit immediately
to preserve image/texture visibility. Added ALPHA/ALPHA_ADD and linear/exponential
fog for both projection types. Other unsupported effects still use native CPU.

**Verified:** Handheld on Retroid and Phone on the emulator each pass 92 checks,
zero skips. In the same non-debuggable Retroid APK, the 16-mesh fixture drops
from 21.622/21.354 to 5.421/5.692 ms (240x320 / 320x240) when residence is enabled;
native CPU costs 5.930 ms at both sizes. Upload/readback pairs drop from 16 per
frame to one. All 22 residence-on/off images match exactly; all eight CPU-fallback
scenes match native reference images. Newly GPU-rendered alpha/fog cases have
bounded fixture differences, not general pixel-exact parity.

GPU remains off by default. Nearest filtering and landscape alpha still lose to
CPU in these samples. Context loss with pending GPU work aborts explicitly rather
than silently drawing on stale CPU buffers; transparent recovery/replay is not
implemented. No real-game GPU, audio or controller acceptance was added. Main's
engine, license, public APKs and private combined-build availability are unchanged.

Next proposed work: GPU mipmapping and multiple texture layers, followed by
representative real-game comparison and fixes. These are still implementation
tasks, not release readiness.
[Source, exact measurements and artifact hashes](https://github.com/EriArk/JavaEmulator/blob/88d48084/experiments/m3g-host/gpu/README.md).

### GPU Texture Layers and Mipmaps: 2026-10-12

Implemented four independent RGB/RGBA layers and all five texture combiners on
GLES3, including sparse original texture slots. Added base-level, nearest-mip
and trilinear sampling using the existing software-generated levels. Mutable
images and individual mip edits invalidate a bounded upload cache. Cached uniform
locations and unchanged uploads remove repeated work. Constant UV coordinates no
longer depend on undefined logarithm behavior in the shader.

**Verified:** final Handheld/Phone research builds pass 98 checks each, zero skips,
on Retroid Pocket Classic and the Android emulator. Twenty of 22 synthetic cases
now exercise GPU draws; both sprite fallback cases retain exact native-CPU pixels.
Same-APK texture-heavy cases cost about 1.7-2.9x less than native CPU, but simple
nearest-filter and lit-grid cases can still lose. All 22 before/after-cache images
match. These are bounded renderer timings, not overall game FPS or old-engine
performance parity. Timing runs preceded the final constant-UV guard; final
correctness checks include it.

A separate GPU-enabled Retroid build reaches Asphalt 3's moving race, including
lap 2/3, and Deep: Submarine Odyssey's first playable area after setup/intro.
Bounded diagnostic files confirm over 125,000 GPU draws per game session.
Unsupported draws still use CPU. Deep captures change after injected input;
audio was disabled and physical-button ergonomics/full playthroughs were not
tested. This Java Deep game is not Mophun's Deep Abyss. Screenshots, artifact
hashes and reproducible checks are recorded in the research documentation.

GPU stays off by default. Next implementation: depth offsets and remaining
framebuffer blend modes, plus reducing CPU/GPU transitions observed in games.
Context-loss recovery and broader performance/compatibility acceptance remain.
Main's engine, root license, public APKs and private combined build are unchanged.
[Source, screenshots and measured limits](https://github.com/EriArk/JavaEmulator/blob/5433c574/experiments/m3g-host/gpu/README.md).

### GPU Depth Bias and Framebuffer Blending: 2026-10-12

Added bounded depth offsets and MODULATE/MODULATE_X2 to the GPU backend, preserving
the CPU candidate's depth-bias formula, channel masks and alpha/fog behavior.
These effects can remain in the resident GPU frame without intermediate CPU
drawing. Unsupported ranges and nonfinite bias still fall back, not silently clamp.

Fixed two defects in this batch: fully clipped models no longer force readback
or access a nonexistent first renderable triangle; negative signed depth written
by extreme CPU bias no longer becomes incorrect unsigned GPU depth. The latter
was reproduced as a failing Retroid regression and repaired. Affected buffers
remain on CPU until their depth is safe again, including after clear. Ordinary
buffers do not incur the added signed-depth scan.

**Verified:** final Handheld/Phone research builds pass 104 checks each, zero skips,
on Retroid and the emulator. Added six focused checks and extended existing
fog/alpha/mask cases to both multiplication modes. Six visible draws separated
by clipped models and blend changes retain one upload/readback pair. All 22
preceding GPU-run benchmark images are unchanged; 26 of 28 current scenes use
GPU, and both sprite fallbacks retain exact native CPU images. Isolated effects
are not uniformly faster than CPU; no new whole-game speedup is claimed.

A fresh GPU-enabled Retroid build reaches a moving Asphalt race with visible
car, track, shadow and HUD. The recorded session reaches 64,513 GPU draws,
including 5,740 biased draws; 56,101 empty meshes skip the CPU barrier. These
are diagnostic checkpoints, not FPS. Sound was disabled and input injected;
RTX media decoding errors in the log remain outside this renderer verification.
No complete-game, audio or physical-controller acceptance is implied.

Next implementation: remaining texture formats and GPU Sprite3D, then context
loss recovery and wider compatibility/performance checks. GPU remains opt-in;
main's engine, license and release APKs are unchanged.
[Source, fresh screenshot, checks and timing limits](https://github.com/EriArk/JavaEmulator/blob/333dd8a0/experiments/m3g-host/gpu/README.md).

### GPU Sprites and Image Formats: 2026-10-12

Source checkpoint: [`285d26ce`](https://github.com/EriArk/JavaEmulator/commit/285d26ce),
**research branch only; GPU off by default**.

- Sprite3D now shares the GPU color/depth buffers with meshes. Invisible sprites
  no longer force a frame readback; CPU fallbacks and image-target visibility are
  preserved. Exact row/column indices retain software crop/flip and fractional
  scaling behavior without a CPU pixel-render loop or sprite mipmap generation.
- Added ALPHA, LUMINANCE and LUMINANCE_ALPHA texture combiners. Sprite images
  preserve raw image/zero-alpha behavior rather than using mesh combiner rules.
- Final isolated Handheld and Phone builds each pass **112 checks, zero skips**
  on Retroid arm64/Android 14 and emulator x86_64/Android 15. Tests cover mutable
  textures, transparency/depth, aliases, oversized fallback, invisible sprites
  and 160 combinations of projection, crop, reflection, fog and blending.
- All **28 benchmark scenes** now require real GPU calls. The other 26 scene
  images are unchanged from the previous GPU checkpoint. Sprites differ from
  CPU by at most four channel levels in these benchmark images due to repeated
  normalized blending. This does not claim general pixel identity.
- Standalone three-sprite scenes are **slower on GPU**: 2.781/2.618 ms versus
  native CPU 2.175/1.780 ms at portrait/landscape sizes. Their three draws share
  one upload/readback pair. Removing mixed-backend barriers is useful coverage,
  not proof that complete games are faster.
- A fresh GPU-enabled Deep session on Retroid reaches an in-game tutorial
  dialogue over visible moving 3D scenery. Its bounded log records 128,001 GPU
  calls but zero Sprite3D calls, so game-level sprite usage is not claimed.
  Controls were ADB-injected; audio/full-game acceptance remains unverified.

Evidence, exact APK hashes and screenshots:
[GPU report](https://github.com/EriArk/JavaEmulator/blob/285d26ce/experiments/m3g-host/gpu/README.md).
No APKs or games are committed. Remaining engine work includes context-loss
recovery, lower per-draw overhead and broader real-game/performance acceptance.
Main's engine, root licensing and public beta APKs are unchanged.

### GPU Context Recovery: 2026-10-12

Source checkpoint: [`52ec72d0`](https://github.com/EriArk/JavaEmulator/commit/52ec72d0),
**research branch only; GPU off by default**.

The renderer now reconstructs its pending frame after loss of its private GPU
context, preserving texture versions, geometry, transparency, fog and depth.
Color and depth commit together only after successful readback and restoration
of the caller's EGL context. Repeated loss is bounded and explicit, not silent
continuation with missing objects. A journal checkpoints at 8 MiB of accounted
payload or 256 draws; the direct-buffer reuse pool is capped at 4 MiB/64 buffers.
These are not application-wide memory limits.

Removed redundant framebuffer, vertex-layout and sampler setup. Fixed an initial
allocation regression in the journal: the 16-mesh fixture fell from 6.179/6.373
to 5.626/5.823 ms at portrait/landscape sizes. A same-device rerun of the previous
checkpoint gave 5.573/5.798 ms. All 28 benchmark images remain unchanged and
require real GPU calls. This is not a general speedup or game-FPS claim.

Seven added regression cases destroy the private context before/after drawing
and during readback, comparing complete color/depth with uninterrupted output.
They cover changed textures/mips, reused geometry, fog, sprites, journal limits,
caller EGL state, persistent failure and reset. This does not reproduce a real
system-wide graphics-driver reset. Whole-game/audio/controller acceptance and
an automatic choice of the fastest renderer remain open.

Final Handheld and Phone artifacts each pass **119 checks, zero skips**, on
Retroid Pocket Classic arm64/Android 14 and the Phone emulator x86_64/Android 15.
The final setup/error-classification guards are included in these verified builds;
timed and final artifacts are identified separately in the report.

[Implementation, artifact hashes, measurements and verification](https://github.com/EriArk/JavaEmulator/blob/a7b5d204/experiments/m3g-host/gpu/README.md).
Next implementation target: batch GPU submission to reduce per-object context
switching, then compare representative game scenes and fix observed mismatches.
Main's application code, root licensing and published APKs are unchanged.

### Batched GPU Submission: 2026-10-13

Source checkpoint: [`492d3bc0`](https://github.com/EriArk/JavaEmulator/commit/492d3bc0),
**research branch only; GPU off by default**.

The renderer now sends queued meshes and sprites together, sharing a graphics
context switch and vertex upload. The first draw remains immediate for safe
startup fallback. Target changes, CPU rendering, clear/release and reset commit
pending work in order; context recovery replays submitted and queued commands
without doubling transparency or losing depth.

Added five focused checks for queued mutable state, actual submission counts,
target changes, reset, repeated failure and translated/clipped LCDUI/3D paint
order. Final Handheld/Retroid and Phone/emulator builds each pass **124 checks,
zero skips**. Same-APK immediate/batched comparisons on Retroid preserve all
**28 image CRCs**. For the 16-mesh scene, graphics-context scopes fall from 17
to 2 and geometry uploads from 16 to 2 per frame. Measured cost drops from
5.929 to 4.394 ms at 240x320 and 5.808 to 4.498 ms at 320x240. Sprite scenes
also improve modestly; single-mesh CPU lighting work does not. This is not a
general game-FPS gain or performance parity with the original engine.

A separate GPU-enabled build reaches moving Asphalt 3 gameplay with car,
shadow, track and HUD, and Deep's animated 3D menu/player setup. Original-engine
sessions were captured too. These are visual smoke checks at different tracks
or camera positions, not frame-matched comparison. Deep tutorial progression,
audio, camera and physical button operation were not accepted in this follow-up;
input was injected through ADB. The earlier Deep tutorial evidence remains a
separate dated result. Normal app data was not changed.

[Source, raw benchmark reports, exact APK identities and fresh screenshots](https://github.com/EriArk/JavaEmulator/blob/33619e43/experiments/m3g-host/gpu/README.md).
Remaining work includes production-source/build packaging, common rendering
costs and game parity, dependency/source notices and signed upgrade/save checks.
Main's application code, root licensing, private combined build and public APKs
are unchanged. This does not announce a new beta or finished replacement.

### Texture Reuse and Reproducible Source Inputs: 2026-10-13

Source checkpoints: `b79fb6b4` and `3f2feddd`, **research branch only**.
The GPU path retains textures across material switches instead of overwriting
one slot on every draw. Per-layer ownership preserves independent filtering and
queued mutable images. Retained texture storage is capped at 8 MiB across four
units; large images use transient slots. This is not a total process-memory cap.
An Asphalt smoke run exposed the initial eight-image limit as too restrictive;
the final cache allows 32 entries per unit within the same byte limit, and the
regression fixture now covers twelve alternating images.

On Retroid, the same-APK material-switching benchmark drops from 4.750 to
4.294 ms at 240x320 and 5.115 to 4.556 ms at 320x240. Texture-level uploads fall
from 3,584 to four across each fixture's run. All 30 images match with the cache
on/off; this is not a general FPS claim. Final Handheld passes 129 checks with
zero skips. Phone emulator passes 129 on the initial cache build and all five
affected cache checks after capacity tuning; the latter was a focused rerun.
A slow emulator attempt hit the runner timeout, then completed with a larger
limit. No timed-out run is counted as a pass.

A separate GPU-enabled APK reached a moving Asphalt race on Retroid. The final
bounded diagnostic sample has 64,513 accepted GPU commands, 30 texture uploads,
zero evictions and about 177 KiB of retained textures. The earlier small-cache
session used a different track, so these are not controlled game-FPS comparisons.
No new full-game, audio or physical-button acceptance is claimed.

Candidate builds now fetch a revision-pinned archive with SHA-256 verification,
extract only allowlisted engine source/license files, and embed the source lock.
Both variants build offline after caching dependencies. All 34 prepared Java
files match the checkout workflow after newline normalization. Bad hashes and
missing offline archives fail explicitly. At this checkpoint ordinary builds
still had signing issue #1; the subsequent fix is recorded below.

[Source, measured results, APK identities and fresh screenshot](https://github.com/EriArk/JavaEmulator/blob/a7a54bc3/experiments/m3g-host/gpu/README.md).
Next proposed work: remaining original-engine compatibility/performance gaps
and the production build transition, including signing setup and complete
source/dependency notices. GPU is still opt-in; main's engine, root licensing
and public APKs are unchanged. No new beta or completed replacement is announced.

### Scene Copies and Contributor Builds: 2026-10-13

Research checkpoint `68866a93` fixes shared resources disappearing from reference
traversal after one slot is cleared, unnecessary animation-track cloning, lost
alignment links in copied scenes (including skeletons), and camera validation.
Initially camera-less worlds still load and duplicate correctly. No title-specific
override was added. Eleven new API checks also pass against the original engine.

Final Retroid: **140/140**, no skips. Phone emulator: **139/139** before the last
loader guard, then **62/62 shared checks** including all eleven new cases on the
final APK. The unchanged candidate-only GPU cases were not repeated on final
Phone. These are API/rendering checks, not new physical-control, audio or full-game
acceptance. [Evidence and exact artifacts](https://github.com/EriArk/JavaEmulator/blob/68866a93/experiments/m3g-host/scene-copies-20261013.md).

Independent build fix `b7501f17` is on **main**: no release credentials are needed
for debug builds. Both ordinary debug APKs, both 14-case JVM suites and both lint
tasks completed without `keystore.properties`. Release assembly without credentials
fails explicitly before packaging; issue #1 is closed. Existing keys and package
IDs are unchanged. No release APK was published.

User-provided N-Gage Asphalt 3 and Snakes inputs were archived and extracted into
ignored local test storage, not Git or APKs. They were not executed; embedded
Symbian/N-Gage support remains unimplemented. Next renderer work is animation,
transform and remaining loader compatibility, with fixes in the same batch.
Production merge, dependency/license review and upgrade/save acceptance remain
separate gates.

### Animation Compatibility: 2026-10-13

Research checkpoint `beff7fc9` fixes missing smooth-curve calculations, broken
loop transitions and circular key ranges, incorrect long rotations and unwanted
normalization of weighted animation tracks. Edited curves invalidate their
caches. Reverse/fractional playback no longer reports an unsafe long pause
between updates, and invalid sequence files are rejected at load time.
These are shared engine fixes, not per-game exceptions.

Final candidate: **163/163 on Retroid**, and **85/85 shared/animation checks on
the Phone emulator**, no skips. The 23 new public-API cases also ran on the
unchanged native engine: 19 passed, four comparison differences remain recorded.
This is not a native-baseline pass. The candidate's 78 other implementation/GPU
cases were included on Retroid but not repeated on the final Phone build.

A separate GPU-enabled candidate reached a moving Asphalt 3 Java race on Retroid,
with two fresh captures showing the starting camera and later gameplay. Sound
was disabled; no new audio, physical-control, full-race or FPS acceptance is
claimed. Ordinary packages and saves were not replaced or cleared.
[Fixes, raw checks, screenshots and exact artifacts](https://github.com/EriArk/JavaEmulator/blob/beff7fc9/experiments/m3g-host/animation-20261013.md).

Next implementation work: animated hierarchy/deformation and remaining loader
contracts, followed by real-game comparison. Production merge, dependency/license
review and upgrade/save acceptance remain separate gates. Main's renderer,
root license and public APKs are unchanged.

## Distribution and Licensing Status

The Wolphun author welcomed use of the project and, in a later response,
reaffirmed GPL 3.0. The linked thread does not yet record an explicit resolution
of the requested additional permission for the existing M3G combination.
See the [latest reply](https://github.com/jamisson2006/wolphun-a-Mophun-emulator/issues/1#issuecomment-6100316305)
and [maintained licensing record](LICENSING.md).

Public Java/UI source development continues. Combined-source/APK publication
remains gated by the recorded scope and release review. Do not say "GitHub is
not updating", "all permissions are settled", or "Mophun is in the public beta".
This page records project status, not new license terms or a legal opinion.

## Images and Writing Guidance

- [Fresh development screenshots, 2026-10-11](../screenshots/post-2026-10-11/README.md): 12 real emulator PNGs, ZIP, captions and hashes. Label as **development / experimental.15**.
- [Published beta.2 screenshots](../screenshots/website-beta2/manifest.json): use for the public download's existing UI, not as current development screenshots.
- Gallery covers in both packs were manually curated. Grid/List show native icons or fallbacks. No games are bundled; artwork is not evidence of gameplay compatibility.
- For a website, lead with the product and user experience; distinguish **available now** from **in development**. Do not link a public beta download as though it contained the experimental features pictured.
- For a progress post, select the dated changes since the previous report. Use plain language about user benefits, not test harnesses or implementation details. Include meaningful limitations without overwhelming the post.
- Never equate "built", "automated tests passed", "seen in an emulator", "tested on a device" and "accepted by users". Keep evidence attached to its build/date.
- This page may be summarized publicly, including the local-only progress. Do not copy private ROMs, source experiments, credentials, server details or private download links into posts.
- Repository updates do not automatically update another chat or website. That workflow must read this file again; stale cached context is not the source of truth.

## Maintenance Contract

After each meaningful feature/fix, test-build distribution, acceptance result
or release: update this snapshot, append a dated CHANGELOG entry, and keep the
release/source/private distinction accurate. Add evidence links and update the
verified date only for facts actually rechecked. Preserve dated history rather
than rewriting a failed check into success. Follow [contributor guidance](../CONTRIBUTING.md).
