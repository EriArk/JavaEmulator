# Symbian and N-Gage Integration Plan

**Updated: 2026-10-14. Status: published in combined 0.3 beta; Asphalt relaunch remains unreliable.**
The selected product direction is one AbyssME application and library, with
an embedded engine rather than requiring users to install and configure a
second emulator. Existing Java and experimental Mophun workflows stay intact.

## Evidence and Decision

The inspected EKA2L1 revision is
[`11f5b6cd141c0884a130ee45743f1f5e17bb1783`](https://github.com/EKA2L1/EKA2L1/tree/11f5b6cd141c0884a130ee45743f1f5e17bb1783).
The ignored local checkout is `local-games/eka2l1-audit/upstream`. Submodules
are exported with their exact revisions in the release source bundle. The Android
host is included by `-PsymbianRelease=true`; `-PsymbianExperiment=true` remains
available for isolated development packages. See [build and host boundary](../engines/symbian/README.md)
and [current release verification](releases/v0.3.0-beta.1-checks.md).
Snakes reaches gameplay; Asphalt 3 now installs and has
reached a moving race, but cold relaunch remains unreliable. Neither result is
full-game, audio or save/relaunch acceptance.

## Combined Beta Promotion (2026-10-14)

The following dated local checkpoints describe evidence at the time of each
implementation, not today's publication state. Their host/import/backup work is
included in the published 0.3 beta, alongside the shared Java/Mophun/Symbian
menu. See [current availability](PROJECT_STATUS.md) for the published state.
Native sources were restored from the exported source bundle and rebuilt; final
signed APKs use that restored tree. System files and private games remain local.

## RAR/7z and Archive Phone Choice (2026-10-14)

Local-only, uncommitted work on `codex/symbian-integration`. Both experimental
variants were rebuilt and installed on Retroid Pocket Classic. No publication,
license change or bundled games/firmware. GitHub release state was not rechecked.

- Native game RAR and 7z inputs now use the same single-game or batch preview
  as ZIP. Content signatures, not filename extensions, select the decoder.
  The existing pinned libarchive streams into one private temporary ZIP in the
  Symbian process; the library router does not initialize the native engine.
  Prepared-phone 7z archives retain their separate system-import path/limits.
- Limits: 1 GB source and expanded game data, 20,000 entries and a free-space
  reserve. Unsafe paths, case collisions, file/directory conflicts, links,
  encrypted entries and malformed/truncated input are rejected. Cancellation
  and failure remove partial output; startup removes orphaned decoder outputs.
  Source archives remain intact. These guards are not a full decoder audit.
- Nested RAR/7z containing one native game is supported in a batch. Nested
  multi-game archives and mixed Java/native/system collections are not a
  universal importer. Java/Mophun compressed collections show an explicit
  message to select JAR/MPN files or a ZIP. No multipart/encrypted support is
  claimed. Large compressed games temporarily need both source and decoded ZIP.
- The phone chooser offers **Reuse for this package type in this archive**.
  The optional choice survives queue recovery, applies only to that package
  type and archive, and does not change a global default. Existing game bindings
  and unique recommended matches take precedence. Missing/incompatible choices
  ask again; turning the toggle off clears that preference.

Evidence (ignored local files, 2026-10-14):

- `build/symbian-compressed-build.txt`: experimental app/test builds pass.
  Installed app SHA-256: Handheld
  `37ebf05b2ee9211c099c72331d8e25a149bf049157d7b84bba23eea3d35a8342`;
  Phone `7d9ec7f3006f48f754d3ef20db6125a0952480aaad2150de5c95f968f21b0c8c`.
- `build/symbian-compressed-debug-tests.txt` and
  `build/symbian-compressed-phone.debug-tests.txt`: **83 passed, two skipped
  per variant**, zero failures. Includes supplied Asphalt RAR, solid 7z SIS/SISX
  collection, pinned upstream RAR5/encrypted fixtures, cancellation/path guards,
  durable phone choice, binding precedence and stale-choice recovery. The older
  opt-in prepared-phone collection and platform-native cases were skipped.
- `build/symbian-compressed-native-ui.txt`: **two passed**. Actual queue UI
  installs Lock N Load 2, 3D miniGolf and Frozen Bubble from solid 7z onto
  disposable RH-29/RM-320 copies, skips the alternate Frozen Bubble resolution,
  and publishes all three library entries. One explicit phone choice is reused
  for both modern installers. Separate staged-import preview/selection passes.
  Original phones/bindings are preserved and temporary copies removed.
- `build/symbian-compressed-phone-ui.txt`: **one passed**, Phone staged 7z
  import/preview and retained selection; not another native installation run.
- `build/symbian-compressed-plus-debug.txt` and
  `build/symbian-compressed-plus-phone.debug.txt`: **one passed per variant**,
  real library + button/result routing to the four-row 7z preview and back.
  Android picker results are stubbed, not the import pipeline. The first attempt
  failed before import because UI automation was attached after Activity start;
  attaching before launch, as in the existing tests, passed in both variants.
- Screens: `build/symbian-archive-phone-choice.png` and
  `build/symbian-compressed-results.png`; per-file native results in
  `build/symbian-compressed-native-result.txt`.
- `build/symbian-compressed-ordinary-build.txt`: ordinary Java/Mophun compilation
  and freshly rerun JVM suites pass, **14 tests per variant**.

This verifies import and installation mechanics, not gameplay, audio, saves or
individual-game compatibility. Prior touch, rotation and portrait layout checks
remain dated evidence below, not fresh acceptance in this block.

## Archive Queue and SIS/SISX Import (2026-10-14)

Local-only, uncommitted opt-in work on `codex/symbian-integration`. Both isolated
experimental variants were rebuilt and installed on Retroid Pocket Classic.
No public APK, push, bundled games/firmware or license change. GitHub release
state was not rechecked in this follow-up.

- A ZIP containing multiple native installers opens a scrolling preview with
  game names, source filenames, checkboxes and Game/Component choices. Add game
  also accepts several selected SIS/SISX/game files. Single installers retain
  their existing flow. The shared library importer routes native batch ZIPs to
  this preview; Java archives keep their existing importer.
- Supports legacy SIS, modern SIS/SISX, `.n-gage`, and nested ZIPs containing
  one recognized native game. Source filenames are not enough to validate a
  package. Exact duplicates and alternative modern packages with the same UID
  are deselected by default; users can explicitly change the selection.
- Declared dependencies suggest Component instead of Game. Selected component
  and N-Gage platform installers are cached first, then the game queue uses
  the existing phone/component setup flow. No firmware or component download
  is introduced, and ambiguous phone choices still require confirmation.
- App-owned copies and atomic journals retain selection, per-file results and
  the selected phone. Completed receipts prevent replay when installation has
  finished but the queue journal has not caught up. An interrupted native
  install still requires explicit retry; partially written guest files are
  not claimed to be rolled back.
- Individual failures leave other queued files available. Missing requirements
  can be supplied or deferred with Continue others, then retried from the row.
  Guest-driven N-Gage installation must be finished or skipped before deferral.
  Back requests a pause between queued files; it does not interrupt a native
  filesystem write. Remove queue and Done remove retained import copies only,
  never source archives or installed games/saves.
- Each native installation updates its phone's shared-library snapshot before
  the queue switches phones. Completed games are not limited to the last
  phone shown by the setup screen. Editing selection is disabled while running.
- Flat owned payload paths, SHA-256 identity checks, ZIP path/CRC/size checks,
  256 candidates, 20,000 archive entries, a 1 GB batch limit and free-space
  reserve bound staging. Unsafe paths reject the archive; an individually
  unreadable/unsupported candidate remains visible but unselected.

Evidence (ignored local files):

- `build/symbian-batch-build.txt`: experimental app/test APKs build successfully.
  Installed app SHA-256: Handheld
  `621cef96239ea54c77b26b12426c507a4888515978468ba48000f60b288db474`;
  Phone `7bc32ccabf9db304cb44a2949f761b27663662af56ebcb6671837d606db9eacd`.
- `build/symbian-batch-debug-tests.txt` and
  `build/symbian-batch-phone.debug-tests.txt`: **59 passed, one skipped per
  variant**, zero failures. Archive, component, pending-import, shared routing,
  phone catalog and backup coverage. The skip is the earlier optional N-Gage
  platform-native scenario, not a pass. Supplied SIS/SISX metadata is included.
- `build/symbian-batch-native-ui.txt`: **two passed** on Retroid. The real queue
  UI installed supplied Lock N Load 2 (legacy SIS), 3D miniGolf (modern SIS)
  and Frozen Bubble (SISX) on disposable RH-29/RM-320 copies. The second Frozen
  Bubble resolution variant was skipped by default. Verified all three in the
  shared index, modern package registration, and Done clearing only the queue.
  A separate staged-import route test checks selection persistence. Copied
  phones and test queues are cleaned; prior active phone/bindings restored.
- `build/symbian-batch-phone-ui.txt`: **two passed**. Real Phone staged-import
  route/selection test plus the actual queue View hosted in a test activity
  with forced portrait/landscape. Assertions check real viewport orientation
  and footer containment. The layout-host test is not another native install
  or an end-to-end sensor-rotation test.
- Screens: `build/symbian-archive-results.png` (actual installation results),
  `build/symbian-archive-phone-portrait.png` and
  `build/symbian-archive-phone-landscape.png` (layout-host captures).
  `build/symbian-archive-native-result.txt` records per-file outcomes.
- `build/symbian-batch-ordinary-build.txt`: ordinary Handheld/Phone compilation
  and freshly rerun JVM suites pass, **14 tests per variant**.

Limits at this earlier checkpoint: one retained queue at a time; outer batch
format was ZIP only. RAR/7z support is recorded in the later section above.
Mixed Java/native/firmware collections and multi-game card-directory archives
are not a universal batch format. Legacy SIS dependency discovery, conditional
modern dependencies and general device/ABI selection remain open. No gameplay,
sound, save/relaunch or complete-game acceptance is inferred from installation.
Previous direct-touch and individual game stability checks were not rerun.

## Component Installers and Resumable Setup (2026-10-14)

Local-only, uncommitted opt-in work on `codex/symbian-integration`. Both isolated
experimental variants were rebuilt and installed on Retroid Pocket Classic.
No release, push, firmware distribution or licensing change. Public release
state was not rechecked in this follow-up.

- Modern SIS/SISX metadata is inspected before native installation. A bounded
  controller-only reader handles plain/deflated metadata, package names and
  versions, declared dependencies and unconditional embedded controllers.
  It does not unpack executable payloads or evaluate install expressions.
  Target-device UIDs are not mistaken for missing runtime installers.
- Missing requirements show the component name, required version range and
  accepted input formats. `Add installer` uses Android's document chooser.
  `Later` keeps the original import and selected phone. A wrong UID/version
  cannot replace the pending game or be silently installed.
- `Saved components` accepts full modern installers (SIS/SISX or a ZIP with
  one installer) ahead of time. A private hash-verified copy is reused across
  imports. Individual/all cached installers can be removed without uninstalling
  components or changing games/saves. The cache is capped at 128 installers,
  256 MB each and 1 GB total. Interrupted staging is cleaned on the next save.
- Preflight resolves cached transitive dependencies before changing the guest.
  Overlapping requirements within a package/embedded bundle are intersected;
  compatible cached versions are preferred. Cycles/conflicts are errors, not
  permission to install arbitrary versions. Newer installed components are
  never automatically downgraded. Missing/corrupted cache entries request a file
  again. This is not a full backtracking package solver.
- Each dependency installation is journaled and followed by a query of the
  selected phone's native package registry, not the application list. Retry
  rechecks the registry and skips components already successfully installed.
  Registry lookup follows the pinned core's installed-package semantics; its
  `completely_present` field is not populated by host installs. Progress names
  the component being installed. N-Gage 2.0 platform dependencies use this same
  flow without replacing the retained `.n-gage` game.

Evidence (ignored local logs/captures):

- `build/symbian-components-final-build.txt`: Handheld/Phone app and test APKs
  build successfully. Installed app SHA-256:
  Handheld `ee3b57f73f5fbcf485712995caa497adad55d1b306315695e386e41418f84dd5`;
  Phone `e6fd9ae3e191b50ee32ba9ccb1973ee7fbc7fe96e44d0d8548b88d3436d4d5e5`.
  `build/symbian-components-saf-build.txt` records the final UI test build.
- `build/symbian-components-debug-tests.txt` and
  `build/symbian-components-phone.debug-tests.txt`: 44 cases each, **43 passed,
  one skipped**, zero failures (2.551 / 2.605 seconds). The skipped case is the
  earlier opt-in real N-Gage platform installation; it is not reported as a
  pass. These runs include actual supplied N-Gage installer metadata parsing,
  bounded/corrupt data, cache identity/version checks, dependency order,
  overlapping/conflicting ranges, cycles, interrupted/recreated imports,
  failed registration, platform dependencies, shared import/catalog/backups.
- `build/symbian-components-native-ui-tests.txt`: **one passed**, 11.910 seconds.
  `SisComponentsUiTest` copies the supplied RM-320 phone, retains a generated
  test package requiring a generated component, uses `Later`, reopens the
  request, selects the SIS through the **real Android Downloads document
  chooser**, and verifies native registry versions for both installed packages.
  It also opens Saved components. Test inputs, copied phone, index/cache entries
  and document-provider row are cleaned up; prior selection/bindings restored.
  These generated packages test installation, not game execution.
- Screens: `build/symbian-component-needed.png`,
  `build/symbian-component-installed.png`, `build/symbian-saved-components.png`.
  They show a clearly named test runtime, not evidence for a commercial game.

Limits at this earlier checkpoint: automatic prerequisite handling is for modern SIS/SISX only. Legacy
SIS, conditional embedded installers/expressions, undeclared DLL requirements
and general multi-installer archives are not covered. Full installers are
required for the cache, not augmentation/partial-upgrade packages. UID/version
matching does not prove device ABI compatibility or correct gameplay; users
must supply components intended for that Symbian phone. No components are
downloaded automatically. Ordinary Java/Mophun JVM suites, previous touch
checks, Asphalt relaunch and complete games were not rerun in this follow-up.

## Direct Touch and Visible Rotation (2026-10-14)

Local-only follow-up, installed in the isolated experimental packages on Retroid
Pocket Classic (Android 14). No public APK, GitHub push or license change.

- Both variants now forward direct guest touches. The numeric/Gamepad decks
  still send phone keys. A finger belongs to whichever surface accepted its
  initial press; crossing between game and buttons never creates a new press.
- Host rotation is inverted before forwarding surface coordinates. Native
  window-server bounds handle Original/Fit/Stretch without applying scale twice.
  Letterbox presses are ignored; captured drags stay within the picture, and
  pause, focus loss, resize, settings changes and teardown release all pointers.
  Android pointer IDs are remapped to the engine's eight supported slots.
- Replaced SurfaceView with TextureView: real-device screenshots showed that
  SurfaceView rotation changed dimensions but left the game pixels upright.
  Rotation now changes the actual image and its touch targets together. Native
  window references acquired on resize are released instead of accumulating.
- Picture taps are guest input, not pause shortcuts. The menu icon and existing
  Back/Menu/Select paths remain. Keypad-only games do not gain touchscreen UI.
- Also fixed exporting a never-booted phone: missing config uses portable
  defaults without booting or writing to the source. Invalid existing config
  remains an error, not a silent reset.

Evidence (ignored local logs/captures):

- `build/symbian-touch-build.txt`: both experimental app/test variants build.
  Installed SHA-256: Handheld
  `1a61dd1062583ca3e42ac957a4cdfb01f0032cc3a1d9d33697a40f82f8f74bdf`;
  Phone `992229ea01c56604be63e7c7cd93af0105047cd0d12b47e05d162a1b8c58b59a`.
- `build/symbian-touch-{fdroid,phone}-final-tests.txt`: **24 passed per variant**,
  no skips (6.083 / 5.653 seconds). Includes 48 layout/scale/rotation combinations
  per variant, pointer-slot/cancel/ownership checks, saved profiles/Select,
  catalog and backup regressions including the never-booted-phone case.
- `build/symbian-touch-native-tests.txt`: **one native check passed**, 23.151
  seconds. A private copy of the supplied Nokia 5800 phone runs its Calculator;
  injected touches change its result area at 0/90/180/270 degrees. Screenshots
  `build/symbian-touch-native-{before,0,1,2,3}.png` were visually inspected to
  verify actual picture rotation. This is guest touch evidence, not game
  compatibility or manual finger acceptance. The temporary phone is removed.
- `build/symbian-touch-phone-game-tests.txt`: **one Phone session check passed**,
  32.394 seconds. Real Snakes startup, shared Display/mapper, portrait numeric
  and Gamepad decks, landscape, Try/Undo and published game screenshot capture.
  Captures: `build/symbian-touch-phone-{phone-portrait,gamepad-portrait,gamepad-landscape,display,game}.png`.

No complete-game, audio, sustained performance or touchscreen-only game acceptance
is claimed. Ordinary builds were not re-tested in this follow-up; the earlier
14-per-variant JVM result below belongs to the backup checkpoint. General SIS
dependencies, setup-cache management, external launchers and distribution review
remain open; individual game stability is deferred.

## Common Controls and Setup Guidance (2026-10-14)

Follow-up: [whole-phone backups](SYMBIAN_PHONE_BACKUPS.md) now implement the
stopped-phone export/restore workflow in both local experimental variants.
The common-controls evidence below describes its earlier checkpoint. Direct
touch and visible rotation are superseded by the follow-up above.

Local, opt-in work in the same experimental application, not a public release.
Both Handheld and Phone debug variants now include the host. The Android
activity moved from the engine library into its optional app source set, so
it can reuse AbyssME UI without a core-to-app dependency.

**Setup:** Settings and the library menu open System files. The first screen
shows compact platform states; details name suitable phones, accepted files
and the next missing input. Original N-Gage and N-Gage 2.0 are separate.
Phone operating-system dumps are distinguished from the N-Gage application
installer and game packages. The main import still retains the game while
dependencies are added, and reuses already prepared inputs. No firmware is
downloaded or bundled. Cache presence is labelled as setup files added, not
proof of guest installation or game compatibility.

The phone-dump explanation names ROM, matching RPKG and prepared ZIP/7z
archives. The format distinction was checked against the
[upstream installation guide](https://eka2l1.github.io/quickstart/basic/installdevice/).
That guide also describes upstream VPL firmware installation; this host does
not expose that path. N95 is this host's existing N-Gage 2 import preference,
not a claim that it is upstream's universally preferred phone.
System import, missing-file prompts, deferred game selection and pending-import
actions now use the shared dark menu. Guest-generated installation questions
and text-entry prompts remain guest interactions.

**Game controls:** Display, graphical controller mapping and named presets,
diagonal bindings, screenshots and return confirmation use existing app
components. Short Select remains a phone-key pulse; held Select opens the mapper
without sending that pulse. Touch/controller ownership is combined before
Symbian scan-code translation. Focus loss, pausing and device removal release
held keys. Settings are stored per system identity and unsigned game UID.
Try/Keep/Undo preserves the confirmed profile, including mapper cancellation.
An engine-specific Display page exposes only supported size/rotation/orientation
options; the controls page cannot accidentally lead to a nonfunctional smoothing
toggle. Screenshot capture waits for focus/surface readiness and uses the pinned
engine's `saveScreenshotTo` guest-frame API, then applies image rotation and the
shared gallery writer. It excludes host controls and viewport borders. The
temporary PNG is app-private and removed after decoding; gallery writes run
off-thread. Native capture stays on the UI thread, like the upstream API, so
pause/teardown cannot interrupt GPU readback. PixelCopy was tried and discarded
after repeated capture failures on the Retroid; retries alone did not fix it.

**Phone:** numeric Phone and virtual Gamepad decks reserve space below the
image in portrait and on its sides in landscape. Button size, opacity and
reach settings reuse the shared controls editor. Handheld remains landscape
with no touch deck. These are virtual phone keys, not guest stylus events:
touchscreen-only Symbian games are not yet supported by the host input path.

Remaining at this checkpoint: stopped-phone backup/restore (implemented in the
linked follow-up), direct guest pointer input with inverse
rotation/viewport mapping, general SIS dependency resolution, cache/version
management, native external-launcher acceptance and distribution review.
Per-game stability work is deferred at the maintainer's request. The earlier
Asphalt `USER 44` issue, audio/save acceptance and full-game compatibility are
not closed by this interface work. No public APK, push or licensing change.

Verified on Retroid Pocket Classic (Android 14), isolated experimental packages:

- `build/symbian-common-both-build.txt`: both debug APKs and test APKs build.
  Installed Handheld SHA-256:
  `1cc2d59bc22c5976b3fa9889168c6d119e56b0050cfdb9ea399f5c73a8c0ed59`;
  Phone SHA-256:
  `c8af5031560f8f01b8b939f31486d78ba4cbccc67c99d6dcc650b8be0f79cea3`.
- `build/symbian-common-full-tests.txt`: **68 passed, nine conditional skips**
  (77 discovered), 142.244 seconds. Enabled `library`, `libraryUi`, `installFlow`,
  `handoffUi`, `importUi`, `sessionUi`. Includes existing import/native lifecycle
  checks plus scan-code ownership, diagonal mapping, setup requirements, separate
  phone/game profiles, short/held Select, mapper cancellation, live image rotation,
  trial undo and a gallery-written, nonblank screenshot.
  This broad run preceded the final engine-native screenshot path and status
  wording polish; it is not a claim that the discarded PixelCopy path was reliable.
- `build/symbian-common-phone-provision.txt`: one explicit private-fixture check
  installed original N-Gage and Snakes in the separate Phone test app, without
  copying the owner's guest saves.
- Final installed APK checks: `build/symbian-common-handheld-final-polish-tests.txt`
  and `build/symbian-common-phone-final-polish-tests.txt`, **eight passed and one
  fixture-only skip per variant**, 38.883 and 46.911 seconds. Actual Snakes rendering,
  Phone/Gamepad layouts in portrait/landscape, settings trials, requirements UI,
  profile isolation and menu screenshot-to-gallery flow. Input is automated;
  this is not owner acceptance of physical controls or a full game playthrough.
- `build/symbian-capture-repeat-{1,2,3}.txt`: three consecutive Phone runs of the
  native game UI/screenshot test passed before the final two-variant check.
  Each checks rotation, settings undo, touch layouts and a nonblank gallery PNG.
  `build/symbian-native-screenshot.png` was also visually inspected: upright guest
  image without the host's menu or touch controls.
- `build/symbian-common-ordinary-checks.txt`: ordinary Handheld/Phone Java
  compilation and fresh JVM execution, **14 tests passed per variant**. The
  experimental module is not required by these builds. Production apps were
  not replaced, and public release state was not rechecked in this batch.
- Shared UI regressions on the experimental packages:
  `build/symbian-common-handheld-shared-tests.txt` (**35 passed, one Phone-only
  skip**) and `build/symbian-common-phone-shared-tests.txt` (**36 passed**).
  Covers compact settings, profile trials, graphical mapper, menu focus/layout,
  gallery writes and touch-deck geometry/multi-touch. These are automated
  device checks, not human playtesting.
- Private captures: `build/symbian-common-{system-files,required-files,file-formats,
  menu,mapper,display,trial,game}.png` and
  `build/phone-symbian-common-{phone-portrait,gamepad-portrait,gamepad-landscape,
  required-files}.png`. These are real device captures, not concepts; firmware
  and commercial game images remain local.

## Unified Import and Saved Platform (2026-10-14)

Implemented locally behind `symbianExperiment=true`. The shared library **+**
and existing URI-install entry point now dispatch through a content inspector.
JAR/JAD/KJX and direct Mophun inputs keep their previous installers. ZIP contents
distinguish Java archives, one Symbian installer/card game, ROM/RPKG inputs and
prepared system collections. Mixed game/system archives are rejected instead of
guessing. Prepared ZIP/7z collections still use the isolated system importer;
7z game archives and native multi-game archives are not supported.

Inspection uses a cancellable, lifecycle-retained worker and the launcher's dark
installer panel. It passes only an app-owned staging token to the non-exported
native activity, not arbitrary paths or a second copy of a large provider input.
Unsafe tokens/links, cancelled copies, damaged ZIPs and excess sizes are rejected.
The existing durable pending-game journal begins after inspection; interruption
before that point can require selecting the source again.

The user's N-Gage 2.0 SIS or ZIP can be added before any game through **+** or
**N-Gage platform**. `PlatformStore` checks the supplied installer family's
package UID (`0x20003b78`), retains a bounded content-addressed copy and atomically
records its selection. Later N-Gage imports first reuse the installed platform;
if absent, they try the saved installer on the game's locked phone. Actual native
launcher registration (`0x20007b39`) is required before game handoff. Header/hash
checks on a preloaded installer are not a claim that it installs on every phone.
An interrupted or unregistered installation requires explicit Retry and keeps
the game pending. No firmware/platform download, DRM change or game bundling.

Sequential-import testing exposed a native lifecycle race: a destroyed activity's
queued shutdown could destroy its successor's prepared engine before catalog
refresh. Worker-owned session identity now prevents stale shutdown, and preparing
a new host stops the previous session first. The sequential UI regression covers
this transition without adding sleeps to hide the race.

Verified on Retroid Pocket Classic, separate experimental package:

- Installed build: `build/symbian-unified-import-lifecycle-build.txt`; APK SHA-256
  `7bf9cc50345c2432f8ca201195f7f4b797bfb6d17f7a29883aa8fb11f6d60852`.
- `build/symbian-unified-import-full-tests.txt`: **61 passed, eight conditional
  skips**, 115.094 seconds. Enabled `library`, `libraryUi`, `installFlow`,
  `handoffUi` and `importUi`. Includes saved-platform installation in a fresh
  isolated N95 environment, damaged-cache/retry checks, source retention, native
  launch/return, retained imports and shared-library regressions.
- `UnifiedImportUiTest` presses the real library + and exercises the result
  callback/router, Java archive chooser, mixed-archive error, platform preload,
  deferred native-game selection, identical-system reimport and library return.
  Only the OS picker result is stubbed in this automated case. Synthetic game
  input is never installed, and existing game environments are not reinstalled.
- Separately selected `AbyssME-platform-import-test.sis` from Downloads through
  the real Android DocumentsUI. Observed **N-Gage platform saved**, verified the
  saved pointer and returned to the library. This supplied installer remains
  preloaded in the experimental package, without modifying installed phones.
- Ordinary Handheld/Phone Java compilation passes without the experimental
  module: `build/symbian-unified-ordinary-compile.txt`. This is not fresh installed
  Java/Mophun gameplay acceptance. The production packages remain untouched.
- Private screenshots: `build/symbian-unified-import-error.png`,
  `build/symbian-unified-platform-saved.png`, `build/symbian-unified-game-choice.png`,
  `build/symbian-unified-system-import.png`, `build/symbian-unified-library-return.png`,
  `build/symbian-real-picker.png`, `build/symbian-real-picker-result.png`.

Still local and uncommitted; no public APK/push or licensing changes in this
batch. At that checkpoint, general SIS dependency solving, cache-version management UI, fully unified
native setup dialogs, Phone overlays, shared controls/display, backups and
external-launcher acceptance remained open; see the newer common-controls section above.
This does not resolve the earlier
Asphalt `USER 44` startup issue or establish new audio/save/full-game acceptance.

## Shared Library and Direct Launch (2026-10-14)

Implemented locally in `SymbianLibraryIndex`, `SymbianLauncherLibrary`, the host
JNI/activity and the shared launcher adapter. Installed games are merged with
the existing library for Gallery/List/Grid, search, Favorites and Recent. An
atomic per-system snapshot and PNG artwork keep native discovery out of the main
process. Identities include the system hash and unsigned UID; negative stable
adapter IDs do not enter the Java Room database. Native game actions deliberately
exclude Java directory deletion, reinstall, artwork edits, shortcuts and backups.

Writable-drive application registrations and identified N-Gage cards are indexed;
ROM utilities, arbitrary hidden services and the N-Gage platform are excluded.
Asphalt is installed as a package without an application registration, so N-Gage
2 discovery also checks installed-package records, a platform game directory and
an unambiguous executable with the package's UID. Direct execution uses the
existing engine launch API. This is not a title-specific executable patch or a
change to rights checks. Native icons retain their Symbian masks. Square N-Gage
resources are preferred for List/Grid; full banners appear in Gallery/details
without cropping, with the existing icon fallback when no cover exists.

Play carries the exact system identity and UID into the non-exported host.
Missing/stale games stop with a message rather than falling back to another
phone or application. The environment-management controls are not shown during
direct startup; leaving a direct session returns to the shared library. Existing
bound systems are indexed when opening the experimental environment, and the
selected environment is refreshed after installation/session return. This is
not background boot-testing of every imported phone set.

Verified on Retroid Pocket Classic, separate experimental package:

- Build: `build/symbian-shared-library-final-build.txt`; installed APK SHA-256
  `a9c2a40d5710d489c7fd75d467d49d09b640e4260b14ccae24c0850ec28c02d0`.
- `build/symbian-shared-library-final-tests.txt`: **53 passed, eight conditional
  skips**, 100.764 seconds. Enabled `library`, `libraryUi`, `installFlow` and
  `handoffUi`. Coverage includes index identity/replacement/rejection, masks,
  square icons, banner scaling, all three views, search, Favorites/Recent,
  controller-triggered launches and return, stale-UID rejection, retained imports,
  isolated platform installation and existing-platform handoff. Existing game
  environments were not reinstalled; test preferences/selection were restored.
- Follow-up `build/symbian-shared-library-ui-regression.txt`: **four passed**,
  44.346 seconds. Repeated direct-launch/stale-UID checks after artwork refresh,
  plus existing short-viewport card-layout and cold-focus regressions. These
  are not four additional unique Symbian cases beyond the suite above.
- Snakes and Asphalt both opened directly from their shared cards. Manual
  captures show Snakes' startup/title sequence and Asphalt's language menu;
  these are launch observations, not a new race/full-game/audio/save result.
- Ordinary `:app:compileFdroidDebugJavaWithJavac` and
  `:app:compilePhoneDebugJavaWithJavac` pass without the experimental module:
  `build/symbian-shared-ordinary-compile.txt`. Production packages were untouched;
  this compilation is not a fresh installed Java/Mophun gameplay regression.
- Private captures: `build/symbian-shared-gallery.png`,
  `build/symbian-shared-list.png`, `build/symbian-shared-grid.png`,
  `build/symbian-shared-options.png`, `build/symbian-shared-direct-snakes.png`,
  `build/symbian-shared-direct-asphalt3.png`, `build/symbian-shared-return.png`.

The later unified-import batch above adds ordinary Add routing and a reusable
N-Gage platform installer. **Still open:** Phone overlays, shared controller/display
menus, native whole-environment backup/restore and external launch intents.
The earlier intermittent Asphalt `USER 44` is not declared fixed by these
successful launches. No public release, source push or licensing change was made.
Games, system files and all captured game art remain private/ignored.

## Resumable Game Import (2026-10-14)

Implemented locally in `PendingGameImport`, `GameInstallFlow` and the host
activity. One app-owned game input is retained outside the cache with a synced
payload, SHA-256 identity and atomic progress record. Missing system files,
cancelled pickers and deferred system selection no longer discard the game.
The pending action remains available after reopening the activity. Successful
system import without warnings resumes the game flow automatically; partial
system results retain their report and offer **Continue import**.

Once selected, a game's system stays locked throughout dependency installation,
even if the active system changes. N-Gage 2.0 imports reuse an installed platform
or request the user's platform SIS/ZIP on that same system. Native catalog
registration of the platform is checked before staging the game. The platform
opens automatically after staging. Continuing a previously handed-off import
does not copy a consumed package back into the guest's installation folder.
No platform installer or game is bundled or downloaded automatically.

Native installation progress is journaled before mutation. An interrupted
installer requires an explicit retry; a completed install is not replayed on
recovery. Removing a pending import atomically detaches its private folder before
cleanup, never deleting original sources, installed apps or saves. Payload
changes and a missing selected system stop the flow without choosing a different
phone. File reading responds to leaving the activity; a native installer already
in progress is not forcibly interrupted. The retained record supports recovery.

Final local APK on Retroid Pocket Classic:

- Build/install: `build/symbian-import-flow-final-build.txt`; SHA-256
  `aa643a89e6cb1abd472545b4631a0c72d5fb41921aac1b8da736a1f82d5459ff`.
- `build/symbian-import-flow-final-tests.txt`: **44 passed, eight conditional
  skips**, 55.959 seconds. This run explicitly enabled `installFlow=true` and
  `handoffUi=true`; these two private-fixture checks are included in the 44.
  Coverage includes missing/wrong systems, ambiguous/deferred selection,
  locked dependencies, wrong platform input, registration failure, interrupted
  retry, modified payloads, cleanup isolation and completed-journal recovery.
- A fresh isolated test N95 accepted the supplied platform SIS and staged the
  supplied Asphalt package byte-identically. Simulated package consumption then
  survived flow reconstruction without re-staging. This test did not install
  over either existing game environment or finish a fresh guest game install.
- Real UI checks reopened a retained import, deferred selection twice, and
  removed only its private input. Another check resumed the already handed-off
  Asphalt import: N-Gage opened automatically, displayed the existing Asphalt
  entry, and returned to AbyssME while keeping the import. No package was
  requeued. The previous selected system was restored after that check.
- Captures: `build/symbian-import-system-choice.png`,
  `build/symbian-import-pending.png`, `build/symbian-import-ngage-handoff.png`,
  `build/symbian-import-ngage-return.png`. These show the experimental environment
  UI and guest platform, not final launcher designs or a new gameplay run.

**Limits:** guest N-Gage installation confirmations remain interactive. On
return, the user can mark the import finished or keep it; this is not automatic
proof of successful game installation. Generic dependency resolution, retaining
a reusable platform installer across phone sets and multi-game import remained
open at this checkpoint; shared library entries are implemented in the later
batch above. Reopening/durable-record tests are not a physical
power-loss test. No new gameplay, audio, save/relaunch or all-phone acceptance is
claimed. Ordinary Java/Mophun builds and production app data were not modified;
no public release, source push or license change was made.

## Prepared System Collections (2026-10-14)

Implemented locally in `PreparedSystemArchive`, `SystemFileCatalog`, the host
activity/JNI adapter and generated native patches. Prepared ZIP/7z collections
can be selected once through **Import phone**. Each `data/roms/<folder>/SYM.ROM`
and `data/drives/z/<folder>` pair becomes an isolated content-addressed system;
the actual firmware identity comes from Z metadata, not the archive folder label.
Missing display names fall back to the identified firmware code. Archived
settings, `devices.yml`, C/E data, saves and executables outside Z are not copied.
Version-1 ROM/RPKG entries remain readable; version-2 entries add the Z hash.
Identical prepared content is reused across archive names, wrappers and formats;
ROM/RPKG and prepared collections remain distinct identities, never merged saves.

Preflight bounds input/expanded sizes and entries, rejects unsafe/link/colliding
paths, checks free space, and retains only complete phone pairs. CRC and SHA-256
checks precede registration. Native writes cannot exceed their declared size.
The initial Commons Compress decoder could not decode this archive's BCJ2 ROM
streams; 7z decoding now uses the existing native libarchive with a validated
destination plan. Java keeps header checks and verifies decoded bytes. Product
INI probes and short software-version records no longer dereference absent data;
prepared imports reject unknown OS versions instead of assuming Symbian 9.4.
Incomplete phones are reported separately, and each complete set is published
atomically. Cancellation/interruption removes staging without replacing ready
systems. The importer preserves the selected environment; a first multi-phone
collection does not automatically open the first phone alphabetically.

Saved game bindings win over recommendations. Otherwise a unique RH-29 or
RM-320 is preferred for original N-Gage or N-Gage 2 respectively. If several
recommended variants exist, the currently selected one can be reused. Remaining
ambiguity still asks for a system; this is not complete SIS dependency matching.

Retroid Pocket Classic evidence, separate experimental package:

- Full original 1,470,632,176-byte 7z read, decoded and verified. First native
  pass registered 19 sets. After correcting folder aliases and empty model names,
  the next complete pass returned **2 added, 19 already available**. All 21
  complete sets are now registered; only the leftover `temp` Z folder was skipped
  because it has no ROM. This includes the RH-4-folder developer variant, whose
  own metadata identifies RH-29, and SGH-i450 with a blank original model file.
- The final collection run preserved the selected environment, game-binding
  bytes and all **588 pre-existing writable-drive files** by SHA-256 comparison.
  The earlier pass also compared existing Z files. No guest game was reinstalled.
  `build/symbian-collection-retroid.txt` passes in 272.949 seconds;
  `build/symbian-collection-result.txt` records every resulting identity.
- The Android SAF picker was exercised through **Downloads** with a ZIP subset
  of the same N-Gage firmware. It reported **0 added, 1 already available**;
  selection and the existing Snakes entry were retained. The UI harness initially
  matched status text instead of the similarly named button; exact clickable
  matching fixed that test-driver issue. Final check:
  `build/symbian-collection-picker.txt`, one pass. Screenshots:
  `build/symbian-collection-library.png`, `build/symbian-collection-import.png`,
  `build/symbian-collection-catalog.png` (temporary prototype UI, not release art).
- Final baseline: **33 passes, eight conditional skips** in
  `build/symbian-collection-baseline.txt`. Coverage includes ZIP/7z identity,
  changed-Z isolation, save/binding retention, interrupted/cancelled imports,
  partial collections, traversal/case/link/CRC rejection, and native malformed,
  unknown-version and blank-model metadata cases. The collection and picker
  checks above are separate opt-in runs, not hidden passes inside the baseline.
- Final APK assembled and installed; SHA-256
  `b5f2b46df59618ad13f57d11e29389e2ec33759712d103cdff3d6177ed519004`.
  Full collection run used preceding build
  `c7c7f3d6152234c19f901b1da409c7a0d801575d48c5cce78b0176f53ea800cb`;
  the subsequent host-only change avoids activating the first system after an
  initial multi-phone import. The importer/native code is identical; picker and
  final baseline ran on the final APK. Build: `build/symbian-collection-final-build.txt`.

**Limits:** registration is not successful boot or gameplay acceptance for all
21 phones. Some supplied systems were labelled problematic by the archive's
author; none is promoted to compatible on that basis. This batch did not replay
gameplay, establish audio/save acceptance, resolve Asphalt's intermittent startup,
integrate the shared game library, or automate the guest N-Gage 2 platform's
installation/dependency workflow. Public release state was not refreshed here.
All firmware, games, diagnostic images and APKs stay ignored/local; no release,
push, source publication or licensing change was made.

## Current Prototype and Fixtures

- A dedicated native module builds the pinned Android bridge as `abysssymbian`.
  Its internal activity runs in `:symbian`, in an isolated debug package. The
  temporary environment/app list is reached from the AbyssME library menu;
  shared game cards and backup integration are not implemented yet.
- Implemented host paths cover ROM/RPKG selection, device selection, SIS/SISX
  and original N-Gage card archive installation, basic controller scan codes,
  surface lifecycle, text/dialog callbacks and controlled stop operations.
  Snakes now has separate runtime evidence below; other paths need their own checks.
- Private user inputs now include original N-Gage **Snakes v0.2.1.9**, N-Gage
  2.0 **Asphalt 3 Street Rules**, and the **N-Gage 2.0 v1.40.1557** SIS installer.
  The newly supplied platform ZIP contains one SIS, not a phone firmware.
  Snakes and Asphalt require separate compatible generations/environments.
- `.n-gage` files are staged to the selected device's memory card and must
  then be installed by its guest N-Gage 2.0 platform. No successful install or
  launch is inferred from that copy. Existing card packages are not overwritten.
- Later in the same session, the user pointed to their private Nextcloud
  `NGAGE` folder. Read-only archive inspection found `N95.zip` and `5320.zip`
  containing ROM/RPKG pairs, and `N-Gage.zip` / `NGageQDv4.60Dev.zip` containing
  prepared legacy EKA2L1 data trees and `SYM.ROM`. Additional 5530, 5800, E50
  and 6650 archives are present but were not inspected internally. These are
  private inputs. N-Gage and N95 were subsequently copied locally and installed
  on Retroid; the remaining archives have not been imported. Legacy data trees
  contribute only their ROM, not their writable drives or configuration.
  No cloud files or server configuration were changed.
- Bootstrap tests cannot establish graphics, audio, gameplay or save
  compatibility. Games, platform installers and firmware remain private
  inputs, never APK assets or repository fixtures.

## Key Capture Routing: 2026-10-14

Later local work in the same isolated Retroid package. Generated adapters leave
the pinned checkout unchanged. Standard key capture now selects a single target
instead of always sending to focus and then duplicating to capture owners.
Raw up/down events match scan codes; normal and repeat events match key codes.
Modifier masks, signed priorities and most-recent equal-priority requests are
respected. Registering a priority-zero request no longer silently raises it
above existing captures. Invalid modifier combinations, minimum signed priority
and short capture payloads are rejected. Cancelled pending repeat callbacks
return before sending an event. Long-key capture and protected-key policy remain
outside this patch.

The routing contract was checked against the original Symbian
[key-event router](https://github.com/SymbianSource/oss.FCL.sf.os.graphics/blob/master/windowing/windowserverplugins/keyeventrouting/src/keyrouter.cpp)
(`RouteKey` and capture validation). The new helper implements that selection
without copying platform key-policy tables or importing another engine.

Build/install: `build/symbian-routing-build.txt`, `assembleFdroidDebug` and matching
instrumentation APK, successful in 2m 7s. App APK SHA-256:
`3CFB6F6DF1DB0F0E77B8D6E4A60B86A88E2C14CAF663A18B97C95A94C3997FB8`.
Installed with `-r`, without reinstalling guest games or clearing data.

- `build/symbian-routing-native-tests.txt`: **27 routing and 12 cleanup
  assertions passed** on Retroid. Routing checks use the pinned upstream heap
  container, including mixed event types/priorities, missing focus and normal
  repeat flags. This does not simulate the full guest IPC or timer lifecycle.
- `build/symbian-routing-session.txt`: 120 loop ticks, inspected moving race,
  D-pad up acceleration to 25 km/h, guest pause and resume. Images:
  `symbian-routing-live.png`, `symbian-routing-driving.png`,
  `symbian-routing-menu.png`, `symbian-routing-resume.png`, all under `build/`.
- `build/symbian-routing-cold-session.txt`: a second 90-tick launch reached a
  race without periodic screenshot sampling or any guest reinstall. Inspected
  `symbian-routing-cold-live.png` and automatically saved
  `symbian-routing-cold-end.png`. Both session logs contain no guest panic.
- `build/symbian-routing-system-ui.txt`: switching to the existing RH-29 system
  and reopening its catalog passed. Firmware and guest data were not replaced.
- Snakes reached gameplay with score 200 and visibly turned after injected
  D-pad left: `build/symbian-routing-snakes-game90.png` and
  `symbian-routing-snakes-turn.png`. A preceding manual run ended on a black
  frame and the short scripted replay only reached the tutorial; those are not
  gameplay passes. A slower staged run is recorded separately in
  `symbian-routing-snakes-game-session.txt`. The first black frame's exact cause
  was not established; it must not be silently relabeled as a known transition.
- The final 180-tick Snakes run returned cleanly after gameplay. Its automatically
  saved `symbian-routing-snakes-game-end.png` still shows gameplay; the matching
  `symbian-routing-snakes-game-log.txt` has no guest panic. Baseline instrumentation
  on this APK: **21 passed, six conditional skips**, recorded in
  `build/symbian-routing-baseline.txt`. The runner's "27 tests" summary includes
  skipped private-fixture checks and must not be reported as 27 passes.

Two successful starts are not a proven fix for rare `USER 44` or all unexpected
backgrounding. Instrumentation success means the Android session/return checks
passed; race evidence comes from the inspected frames. Buttons were injected
through Android on real hardware, not exercised by a person. Audio, measured FPS,
durable saves and complete-game acceptance remain open. No public source push,
release or test-build distribution was performed.

## Capture Cleanup and Relaunch Checks: 2026-10-14

Later local/uncommitted work, same isolated Retroid package. The pinned checkout
is not edited; generated header/source adapters add the missing operations.

- Implemented `CancelCaptureKey` and `CancelCaptureKeyUpsAndDowns`, validating
  the payload and matching the calling window, capture handle and event type.
  Destroying a window group removes all of its captures. Other owners and the
  priority order of retained requests are preserved. This repairs a concrete
  missing protocol operation; it is not proven to be the cause of every Asphalt
  startup problem, nor does it complete upstream key-capture event routing.
- `tests/run-native-tests.ps1` passes all 12 cleanup assertions on Retroid;
  local output: `build/symbian-capture-native-tests.txt`. The helper tests do not
  exercise a complete guest IPC session. The former unimplemented `0x2B` warning
  no longer appears in the checked patched game logs.
- The first patched game run still produced guest `USER 44` while loading race
  assets (`build/symbian-capture-log.txt`, `symbian-capture-race.png`). Three
  subsequent diagnostic-build launches reached race frames without reinstalling
  the game or deleting saves. Inspected evidence:
  `symbian-capture-panic-frame.png`, `symbian-capture-panic-driving.png`,
  `symbian-capture-panic-frame2.png`, `symbian-capture-session3-end.png`, all under
  ignored `build/`. Session logs are `symbian-capture-panic-session*.txt`.
  Instrumentation success asserts foreground/return, not guest-game success;
  the race observation comes from those inspected images.
- No root cause/fix for intermittent `USER 44`, measured FPS, human controller,
  audio or durable save acceptance is claimed. Temporary focus/panic tracing
  was removed for the final APK, built and installed with matching test APK:
  `build/symbian-capture-final-build.txt`. Final APK SHA-256:
  `D0E3150B2ECFF2AB7A26511378CEC9F6035DE3ABB1C4E0F950BEC78DD98033CA`.
  No release, source push or test-build distribution was performed.
- Final-build observations remain mixed: `build/symbian-capture-final-race.png`
  actually shows the platform's **Back to Game** screen, not a race (the filename
  is not evidence). No guest panic was recorded in that run. A subsequent run of
  the same app APK, with optional `stepScreens=true` in the test harness, shows
  language selection, the menu, loading and a race at step 45:
  `build/symbian-step-{20,30,35,45}.png`, `symbian-step-session.txt` and
  `symbian-step-log.txt`. Thus unexpected backgrounding and `USER 44` both remain
  open; successful sampled runs do not establish deterministic cold startup.
  Final baseline: **21 passes, six conditional skips**,
  `build/symbian-capture-final-tests.txt`. No Snakes gameplay rerun in this batch.

## Supplied System Collection: 2026-10-14

The MEGA download completed after the earlier transfer quota expired. Private
file: `local-games/ngage/2026-10-14/mega/EKA2L1 Pre-Config - Almost All The Rom Available.7z`.
Size: 1,470,632,176 bytes. SHA-256:
`f1b54b69a943260e1662d644fca102f03c1943dd652cc8b60fce0ba298fa5435`.

7-Zip integrity test reports **Everything is Ok**: 154,694 files, 11,166 folders,
2,747,612,403 expanded bytes. Evidence: `build/symbian-mega-integrity.txt` and
`build/symbian-mega-inventory.txt`. Inspection found 21 `data/roms/*/SYM.ROM`
entries, corresponding prepared Z drives and `data/devices.yml`, with no RPKG
files. Examples include N-Gage, N70, N95, N85, 5320 and Samsung i450. The archive
author marks several configurations as problematic; those labels are not
independent AbyssME compatibility results.

The collection is retained locally, not bulk-extracted into a live environment,
installed on Retroid or included in Git/APKs. It also contains writable-drive
state. Future import must derive and validate isolated system sets, not trust
the archived configuration or merge its C/E contents into existing saves.
The current single-phone ROM/RPKG ZIP importer does not yet import this multi-
system 7z collection directly.

## Installation and Display Fixes: 2026-10-14

Later local/uncommitted work on Retroid Pocket Classic, Android 14, isolated
`io.github.eriark.abyssme.symbian.debug`. No public source push or APK distribution.

- **Installation root cause:** guest tracing identifies `sp` in the supplied
  `achievements.xml` as the failing language lookup, through `playserverach.dll`
  into `ngiplaycommon.dll`; its table supports `es`, not `sp`. Local evidence:
  `build/symbian-language-trace.txt` and `build/symbian-leave-log2.txt`. The new
  adapter normalizes only supported achievement language fields in the guest
  platform's temporary import path. It does not modify executable code, source
  game archives or rights checks. See the [host boundary](../engines/symbian/README.md).
- **Real installer success:** inspected `build/symbian-metadata-asphalt-ready.png`
  and a subsequent reinstall in `build/symbian-asphalt-trace2.png` both show
  "Installation complete". Original XML survives guest import cleanup in
  `runtime/compatibility-backups`; the device backup's actual SHA-256 matches
  its filename, `889061bde623c07edff7f73c65c9af23fccae6a6c17ed1f6a168487520d772f2`.
  This is original-metadata preservation, not a game-save backup test.
- **Upright gameplay observed:** removed an extra 180-degree rotation in direct
  framebuffer presentation. The intermediate diagnostic build reached a moving
  Asphalt race after reinstall: `build/symbian-asphalt-trace-session.txt` and
  inspected `build/symbian-asphalt-trace6.png`, `build/symbian-asphalt-trace7.png`
  (69 km/h on the HUD) and `build/symbian-asphalt-upright-driving.png`. Android
  key injection was used, not human controller acceptance or measured FPS.
- **Snakes regression replay:** `build/symbian-snakes-rotation-session.txt`
  (95-second session), `build/symbian-snakes-rotation-gameplay.png`, with score
  and lives visible. That build changed read-back as well as presentation;
  the final build restores upstream read-back handling and fixes its mismatched
  array delete. Do not label intermediate captures as final-build acceptance.
- **Open runtime defects:** an earlier attempt exited with guest `USER 44`
  after entering Instant Play (`build/symbian-asphalt-upright-error.txt`).
  Subsequent cold launches can remain on the platform's "Back to Game" screen
  instead of visible gameplay (`build/symbian-asphalt-cold-session.txt`,
  `build/symbian-asphalt-cold3.png`). Diagnostic tracing did not reproduce the
  same panic. Restoring upstream read-back handling did not resolve it either:
  final-build `build/symbian-asphalt-readback-session.txt` (120 seconds) and
  `build/symbian-asphalt-readback4.png`. Passing session-return assertions are
  not passing gameplay checks. The success after reinstall does not establish reliable relaunch
  or justify a destructive reinstall workaround. Guest typography and partial
  redraw also need work. No audio listening, durable save, FPS or complete-game
  acceptance is claimed.
- **Final source/build:** generated exact-anchor patches leave the pinned
  checkout unchanged. Temporary IPC/leave/panic tracing has been removed.
  `build/symbian-readback-isolation-build.txt`: APK build succeeds and the debug
  package was installed on Retroid. APK SHA-256:
  `FEB1077D32127C39D95A046273981AC897B85FBE7E8E75E437B5E880F14BF55C`.
  `build/symbian-metadata-final-tests.txt`: 21 passes, six conditional skips
  (`OK (27 tests)` from the runner), including seven metadata checks.
  An earlier depth-test failure ran the new test against the preceding app
  payload; the rebuilt/installed matching pair passes. Ordinary Java/Mophun
  builds were not rerun in this batch. Games, ROMs and diagnostic dumps remain
  ignored/private; publication/dependency review and unified UX remain open.

## Earlier System Catalog Verification: 2026-10-14

This later batch remains local and uncommitted on the experiment branch. It
does not change the public beta. Implemented in `SystemFileCatalog`, `GameImport`
and the temporary `SymbianActivity` host:

- Preload system ZIPs or raw ROM/RPKG pairs through the picker. Retain validated
  inputs, identified model/firmware/OS and a separate writable runtime per set.
  Identity uses ROM/RPKG SHA-256, not the archive filename. Reimport reuses an
  existing set without reinstalling its phone or replacing saves. Different
  firmware is not an in-place upgrade. Pending sets are promoted atomically only
  after the native installer and device identification succeed.
- Recognize legacy/modern SIS headers, N-Gage 2 packages and ZIP wrappers. A
  card game's embedded SIS beside its APP is part of the card, not a second
  standalone installer. Reject ambiguous installers, path escapes, duplicate
  paths and expanded game ZIPs over 1 GB. Automatic routing currently covers ZIP,
  not RAR/7z or multi-game bundles.
- Match candidates by platform generation, automatically use one candidate,
  ask if several remain, and persist a package-hash binding. This is not full
  SIS dependency analysis. Selected system survives reopening. Missing platform
  SIS is distinguished from a staged N-Gage 2 game; guest installation remains
  required. Missing-system imports are not queued/resumed automatically yet.
- Retroid checks: `build/symbian-catalog-fixtures2.txt` verifies real N-Gage/N95
  archive import/reuse, RH-29 versus RM-320 routing and Snakes native installation.
  `build/symbian-catalog-modern1.txt` verifies platform install and exact package
  staging in the managed N95. `build/symbian-catalog-ui1.txt` verifies System files
  selection and reopening. Inspected captures: `build/symbian-system-catalog.png`
  and `build/symbian-system-snakes.png`. This remains a temporary environment UI,
  not the final shared-library/settings design or a full SAF-provider test.
- Managed RH-29 Snakes replay reaches its main and single-player menus and
  returns cleanly: `build/symbian-catalog-snakes1.txt` (55 seconds),
  `build/symbian-catalog-snakes2.txt` (80 seconds), with inspected matching PNGs.
  This replay checks the catalog-to-runtime path; the earlier gameplay evidence
  below remains separate. No new sound, save/relaunch or human-controller
  acceptance is inferred from these menu/session checks.
- `build/symbian-catalog-tests2.txt`: 14 baseline passes and six conditional skips
  (the runner reports `OK (20 tests)`). Coverage includes retained save bytes on
  duplicate import, changed-system isolation, binding persistence, interrupted
  import cleanup, missing files, unsafe archive paths and embedded SIS routing.
- Fresh N95 guest installation of Asphalt 3 still reports Error -1. This rules
  out reuse of the old partially installed environment as the sole cause, but
  does not identify the engine defect. `build/symbian-catalog-asphalt1.txt` passes
  only the 180-second foreground/return check; diagnostic log is
  `build/symbian-catalog-asphalt-error.txt`. No successful Asphalt gameplay claim.
- Final build/install: `build/symbian-catalog-build6.txt`; local debug APK SHA-256
  `4CBA947D2B66E9922E3792A7DF5CA1A36BB5BCC8B4B542886CB756A9C0235803`.
  No release, push or distribution. Ordinary Java/Mophun builds were not rerun
  in this catalog-only batch. Firmware and game inputs remain ignored/private.

## Earlier Runtime Verification: 2026-10-14

All results below are local to `codex/symbian-integration`, on Retroid Pocket
Classic / Android 14, package `io.github.eriark.abyssme.symbian.debug`. The
production app/library was not replaced. No Symbian APK was distributed.

- **Snakes v0.2.1.9:** installed on the supplied RH-29 EPOC6 environment and
  reached real 3D gameplay with score/lives visible. Captures:
  `build/symbian-snakes-launch6.png` and `build/symbian-snakes-play2.png`.
  `build/symbian-snakes-play2.txt` records the 55-second session and clean
  return to the catalog. Final-build replay also reaches gameplay and returns:
  `build/symbian-snakes-play3.txt`, `build/symbian-snakes-play3.png`.
  Inputs were injected through Android; this is not
  human controller, sound-listening, save/relaunch or full-level acceptance.
- Fixed hidden alternate card-build discovery without exposing all hidden OS
  apps. Initialize the mounted ROM before registry icon enumeration starts
  FBS/font services. Fixed shutdown ordering and explicitly wake an idle CPU
  before joining the OS worker. These changes are host-level, not Snakes patches.
- **N95:** the supplied files identify as RM-320 / N95 8GB, EPOC93FP1. The
  supplied N-Gage 2.0 platform installs and opens. Prefer an English language
  actually present in the dump, including regional English (158 here), rather
  than leaving this dump's Chinese default. This removes the observed launch
  failure; it is not a claim about every regional firmware.
- Added the Android device's Roboto regular font as an engine fallback when
  available, outside guest drives. No Android font is bundled in the APK and
  no ROM font is overwritten. Missing letters now render, but the N-Gage 2.0
  UI still mixes digit/symbol and fallback faces. Typography needs further work.
  Capture: `build/symbian-ngage2-fonts.png`.
- **Asphalt 3 `.n-gage`: not playable yet.** Fixed Java/native relative-path
  disagreement when staging the memory-card package. The guest platform sees
  its artwork and starts installation, but reports `Unable to install game
  (Error -1)`. Capture: `build/symbian-ngage2-navigate5.png`; diagnostic log:
  `build/symbian-ngage2-install-error.txt`. The installer/registry path needs
  diagnosis; do not interpret a passing host lifecycle test as game acceptance.
- System ZIP import extracts a single ROM and optional RPKG, rejects ambiguous
  sets, unsafe paths, oversized content and damaged payloads, and removes failed
  partial extractions. It does not copy archived C/E drives, config or saves.
  Real `N-Gage.zip` and `N95.zip` extraction plus native RPKG-requirement checks
  pass: `build/symbian-ngage-zip.txt`, `build/symbian-n95-zip2.txt`. Firmware was
  originally installed from the extracted raw pair, not through a picker UI test.
- Build/package/install pass: `build/symbian-app-build23.txt` (same app payload
  as build22; build23 fixes the private archive test's missing native prepare).
  APK SHA-256:
  `1F3B237F5BBB76E33BF1E2A3D8F01A65DEBE0E7E5A48C81270B4529A77B7C872`.
- Seven baseline instrumentation cases pass; four private-input cases skip
  without arguments, despite the runner saying `OK (11 tests)`.
  Log: `build/symbian-retroid-tests4.txt`. Separate private checks confirm
  firmware selection/card enumeration and N95 platform reuse/package staging:
  `build/symbian-ngage-install6.txt`, `build/symbian-n95-platform5.txt`.
- At this earlier checkpoint system-file preloading and automatic matching were
  still requirements; the later catalog batch above supersedes that limitation.
  Shared library, Phone overlays, durable backups and dependency/asset publication
  review remain open. Diagnostics and firmware/game inputs remain ignored/private.

## Earlier Bootstrap Verification: 2026-10-13

- Native arm64 build plus APK/AndroidTest packaging pass. Final build log:
  ignored `build/symbian-app-build7.txt`. Fixed the pinned Boost/libc++ build
  compatibility, Lua-disabled script staging and asset task ordering.
- APK `AbyssME-0.2.0-beta.1-symbian-experiment-debug.apk`, under
  `app/build/symbian-experiment/outputs/apk/fdroid/debug/`, SHA-256:
  `ECA6C8C06F0BA811C74210036201B980816595AF6195E7FF6588E1EEC7A97456`.
  It installs alongside production as `io.github.eriark.abyssme.symbian.debug`.
- Retroid Pocket Classic / Android 14: four firmware-free cases pass, including
  three native prepare/stop cycles and three separate-process page open/return
  cycles. One private firmware/card test is skipped; the runner's `OK (5 tests)`
  includes that assumption skip and must not be described as five passes.
  Log: `build/symbian-retroid-tests3.txt`. Verified full-content screenshot:
  `build/symbian-retroid-environment.png`. This temporary setup screen is not
  a game screenshot or final shared-library UI.
- Ordinary Handheld APK builds and all 14 JVM tests execute/pass. Log:
  `build/symbian-default-regression.txt`. Its ZIP has no Symbian native library
  or engine patch/compat assets. No production APK was installed or published
  during this batch; no Phone or x86_64 runtime result is claimed.
- At that bootstrap checkpoint, system files had only been located. Import, game launch,
  rendering/audio, session shutdown with a running guest and save/relaunch
  were still unverified. The later runtime results above supersede this checkpoint;
  dependency/asset review remains open.

**Decision:** continue the isolated, opt-in host. EKA2L1 is not a drop-in Android
library; one game's visible gameplay does not establish platform-wide support.

| Inspected source | Finding and implication |
| --- | --- |
| [Android JNI API](https://github.com/EKA2L1/EKA2L1/blob/11f5b6cd141c0884a130ee45743f1f5e17bb1783/src/emu/android/app/src/main/java/com/github/eka2l1/emu/Emulator.java) | Device installation/selection, app enumeration, icons, UID launch, keys, touch and screenshots already have entry points. Java helpers still depend on upstream UI, storage and Android services. |
| [Native bridge](https://github.com/EKA2L1/EKA2L1/blob/11f5b6cd141c0884a130ee45743f1f5e17bb1783/src/emu/android/app/src/main/cpp/src/native-lib.cpp) | Global engine/window state, process-wide working-directory changes and hardcoded Java class names require a host adapter. Surface destruction pauses threads; a complete host pause/resume/shutdown contract is not exposed. |
| [Launcher](https://github.com/EKA2L1/EKA2L1/blob/11f5b6cd141c0884a130ee45743f1f5e17bb1783/src/emu/android/app/src/main/cpp/src/launcher.cpp) | Launch uses a guest app UID. Device selection uses an array index; do not store that index as persistent game identity. Firmware installation currently chooses variant zero and supplies no progress/cancel callbacks. |
| [Engine build selection](https://github.com/EKA2L1/EKA2L1/blob/11f5b6cd141c0884a130ee45743f1f5e17bb1783/src/emu/CMakeLists.txt) | Android uses its own bridge, not the Qt frontend. Reusing the Android core does not require embedding Qt. |
| [Package/card installation](https://github.com/EKA2L1/EKA2L1/blob/11f5b6cd141c0884a130ee45743f1f5e17bb1783/src/emu/system/src/epoc.cpp) | Original N-Gage card folders and archive staging have installation paths. This is source evidence, not tested format/game compatibility in AbyssME. |
| [Device storage](https://github.com/EKA2L1/EKA2L1/blob/11f5b6cd141c0884a130ee45743f1f5e17bb1783/src/emu/system/src/devices.cpp) | Isolated C/D/E drives exist, with firmware-specific paths and additional OS state. A game's folder alone is not a reliable save backup. |

Upstream `Emulator.exitInstance()` kills its Android process. Keep that behavior
out of AbyssME's library process. Guest exit must become a session result, with
storage shutdown/flush verified before returning to the library.

## User Experience

The owner confirmed the target on 2026-10-13/14: one automatic import entry point
for games and system files, including archives. The temporary separate phone
and game pickers are diagnostic scaffolding, not the final product workflow.
Implement a content-based inspection and install plan before changing storage:

- Recognize standalone packages, original card trees, phone ROM/RPKG pairs,
  and supported packages inside ZIP/7z/RAR. An archive extension alone must
  never select the runtime. Show a selection only for genuinely mixed inputs.
- Reuse an appropriate installed environment; request missing system inputs
  once, and associate the resulting game with a stable environment identity.
- Provide Settings > System files before any game import. Users can add several
  system archives in advance; inspect and retain validated sets with content
  hashes, detected phone/OS generation and installation status. Subsequent game
  imports reuse these sets automatically rather than asking for the same files
  again. Keep the source archive and a prepared writable environment conceptually
  distinct; sharing system files must never accidentally share or overwrite saves.
- Match from actual package requirements and installed compatibility metadata,
  not just archive filenames. Ask only for a missing set or an ambiguous match;
  keep a per-game override available when automatic selection is wrong.
- Install dependencies in order, then verify the app registration and add its
  title/icon to the same library as Java and Mophun. Copying a `.n-gage` file
  to a guest drive is only staging, not completion.
- Keep progress truthful, preserve existing saves, and support cancellation,
  duplicate detection, interrupted-import recovery and rollback. If a format
  or phone is unsupported, say so instead of claiming universal compatibility.
- Do not require manual archive extraction, drive-letter selection or Android
  system-folder access in the normal user path. Firmware stays user-supplied.

This automatic workflow is an agreed implementation target, not a released
feature or a claim that every archive format is already supported end to end.

1. Import a game through the existing Add games flow. Detect its format from
   content where possible; do not route every ZIP to Java or trust an extension.
2. If no suitable environment exists, show a short **Set up Symbian** wizard.
   Request the user's firmware/dump files, explain what is missing, validate
   them and show real install progress. Do not bundle or automatically download
   proprietary firmware or games. Upstream supports ROM/RPKG and firmware
   installation paths; exact accepted combinations need prototype tests.
3. Reuse a compatible installed environment on subsequent imports. Suggest an
   environment using package/device requirements when known; ask only when
   ambiguous. Do not claim one firmware works for every Symbian generation.
4. Show the installed game's name and native icon in the shared library. Use
   the existing fallback art policy; do not invent a cover or duplicate an icon
   over artwork. Hide OS utility apps from the main library without deleting them.
5. Play opens the game directly. Display, controls, screenshots and return to
   library should use AbyssME's existing vocabulary and interaction patterns.

Keep virtual-phone management under Settings, with friendly device names and
storage usage. Ordinary launches should not ask for drive letters, app UIDs,
CPU modes or a phone model every time. Never hide an unresolved compatibility
choice behind a fake automatic test/progress bar.

## Host Architecture

- Use a dedicated native Android library module and narrow host bridge. Start
  with a pinned upstream revision; maintain small, explicit integration patches.
- Run the Symbian session in an internal `:symbian` process in the same APK.
  This is lifecycle/crash isolation, not a separate user-facing app and not a
  licensing workaround. Keep its activities/services non-exported by default.
- Define initialize, install, enumerate, launch, pause, resume, release-input,
  stop/flush and error-result operations. Reconcile interrupted jobs after
  process death. Do not treat a surface pause as a durable saved game.
- Preserve or deliberately adapt upstream JNI class/callback names. Storage,
  text entry, dialogs, screenshots and Android service callbacks need explicit
  host implementations; simply copying a `.so` will not work.
- Reuse the current [AdditionalGames](../app/src/main/java/ru/playsoftware/j2meloader/catalog/AdditionalGames.java)
  boundary. It currently loads **one** optional engine. Before adding another,
  introduce a small dispatcher with stable engine IDs and backward-compatible
  reading of existing `additional-game.json` entries. Never replace the Mophun
  provider with Symbian or route every managed folder to the same runtime.
- Store a stable environment UUID plus app UID and package relationship in
  versioned metadata. An environment's directory isolates OS state; one package
  may expose several apps. Resolve native device indices at runtime. Handle
  duplicate imports, upgrades and missing environments explicitly.
- Map AbyssME controls to Symbian scan codes through an adapter, not raw MIDP
  key values. Include soft keys, phone keys, diagonals and key releases. Preserve
  short/held Select ownership and controller-disconnect cleanup. Touch must
  use inverse screen rotation/scaling and the guest viewport, not screen pixels.

## Storage and Backup

Implemented local workflow and verification: [Symbian phone backups](SYMBIAN_PHONE_BACKUPS.md).
The requirements below remain the scope boundary; this is not per-game save export.

Use SAF grants and bounded staging into app-owned storage, not upstream's
all-files permission as a default. Validate archive paths, sizes and available
space; stage new environments before committing their library entries. Failed
imports must not delete or silently replace an existing virtual phone.

First reliable backup scope: **a stopped virtual phone and its installed games**,
including writable drives, registry/configuration, environment identity and host
library bindings and `symbian-profiles/<system-id>/<game-uid>` settings.
Define whether firmware is included in a private backup or
must be reselected by hash; make that requirement visible before export/restore.
Restore as a new validated environment, then switch library references. Test
interruption, corruption, low space and rollback before exposing the workflow.

Do not claim per-game save export until shared OS files and ownership have been
audited. Reusing Java's per-game folder archive without an engine-specific
backup contract would miss Symbian state. Do not force users to move files into
Android system folders.

## Build, Permissions and Publication Gates

- Upstream's actual root CMake requires **C++20**, despite older C++17 wording.
  Its Android project is `src/emu/android`, not the stale `src/android` path
  mentioned in BUILDING.md. Android configuration uses NDK `25.1.8937393` and
  CMake `3.22.1`; current AbyssME uses NDK `22.1.7171670` with ndk-build.
  Test module-specific toolchains and packaged native dependencies before any
  global upgrade. Keep AbyssME's Android 10 minimum.
- The prototype uses module-specific NDK `28.2.13676358`, CMake `3.22.1` and
  arm64-v8a. Windows FFmpeg builds use MSYS2 Bash/Make. A local compatibility
  definition enables removed libc++ unary/binary function types for the pinned
  Boost dependency. Lua scripting is off; built-in native patches remain on.
  x86_64 is not enabled yet. Check C++ runtime packaging, library name collisions,
  memory use and Android native page-size requirements before distribution.
- Inventory resolved submodules and their licenses, not only root LICENSE.
  The pinned tree references CPU/JIT, audio, FFmpeg, LuaJIT, archive, graphics
  and other dependencies. Their contents were not audited here.
- Native asset staging includes guest patches, shaders, compatibility files,
  scripts and sound banks. All need provenance and distribution review. The
  Android bridge still links Catch2 with tests disabled; separate test-only
  entry points/assets from a distributable build deliberately.
- Do not merge upstream's manifest wholesale. Camera, microphone, location and
  broad storage access are not baseline requirements for our chosen UX. Define
  denied/unsupported behavior and request any later capability only when needed.
- EKA2L1 uses GPLv3-or-later source notices and a GPLv3 license text. The old
  EPL-1.0 M3G/tone components were removed from AbyssME on 2026-10-13; ordinary
  builds now use the compatible replacement. This removes that earlier design
  obstacle, but is not a completed review of EKA2L1 dependencies/assets.
  See [LICENSING](LICENSING.md).
  No root license changes, combined public source import or APK publication are
  authorized by this research document.

The M3G replacement is already merged and included in 0.2.0-beta.1. Keep that
working Java 3D implementation and Mophun unaffected by the opt-in Symbian work.

## Prototype Stages and Acceptance

| Stage | Deliverable | Gate |
| --- | --- | --- |
| 0: source audit | This plan and source evidence | Complete as a source-only review; dependency/license audit remains open |
| 1: isolated build | Pinned dependency inventory, native Android build, bootstrap and controlled shutdown without user firmware | Build logs, ABI/package inspection, repeat initialization/stop, no impact on existing Java/Mophun builds |
| 2: environment and import | Firmware wizard prototype, app catalog/icon enumeration, SIS/SISX and original N-Gage card import paths | Permitted fixtures, truthful progress/cancel, failed-install rollback, no duplicate environments |
| 3: game session | Unified launch, screen, audio, controller/touch input and return | Visible gameplay, listening check, input release, pause/resume, game save followed by cold relaunch |
| 4: product workflow | Phone/Handheld UX, whole-environment backup/restore and external-launcher integration | Small-screen checks, real device controls, interrupted restore tests, Java/Mophun regressions |
| 5: distribution | Reviewed dependencies/notices/source materials and licensing resolution | Explicit release decision; no claim of platform-wide compatibility |

Track original N-Gage (EKA1), other Symbian generations and N-Gage 2 separately.
Upstream exposes N-Gage 2 license helpers, but this audit did **not** establish
a complete `.n-gage` import workflow. Do not promise N-Gage 2 in the first slice
or bypass rights checks. Choose a small representative set of permitted games,
not a growing collection of one-off title patches.

Record source/build evidence, Android emulator observations and physical-device
acceptance separately. No performance estimate, APK size estimate, game
compatibility percentage or release date has been established by this audit.
