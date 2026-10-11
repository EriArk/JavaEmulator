# AbyssME - Current Product and Development Status

**Last verified: 2026-10-11.** This is the starting point for website copy,
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
| Public development source | `main`; latest app-code change at this review: [d39ed742](https://github.com/EriArk/JavaEmulator/commit/d39ed742) | Unreleased Java/UI/storage improvements listed below; not all are in the downloadable beta |
| Private combined test snapshot | `0.1.0-beta.2-experimental.15`, 2026-10-11 | Handheld and Phone debug APKs containing Java plus experimental Mophun; supplied separately to testers, not a GitHub release |
| Local-only implementation | Ignored optional-engine source and diagnostics | Mophun integration, game-specific experiments and test fixtures; public descriptions below, implementation not published |
| Separate research branch | [`codex/m3g-replacement`](https://github.com/EriArk/JavaEmulator/tree/codex/m3g-replacement), `08f38c78` | Synthetic M3G probes, isolated real-host builds, three-game smoke checks, opt-in runtime measurement and a bounded Asphalt comparison; not merged, released or accepted as a replacement |

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
- [#1](https://github.com/EriArk/JavaEmulator/issues/1): clean-clone debug signing setup; [#4](https://github.com/EriArk/JavaEmulator/issues/4): broader regression coverage.
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
Next proposed batch: repeat longer matched runs across Deep / Asphalt / DiRT,
complete Deep lifecycle checks, and isolate the visible rendering differences.
Main retains the current engine. Await maintainer confirmation.

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
