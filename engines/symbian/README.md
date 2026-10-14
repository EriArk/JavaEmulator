# Symbian / N-Gage Host Experiment

Embedded experimental engine for the 0.3 beta. Use `-PsymbianRelease=true`
for the combined Java/Mophun/Symbian build; the property-free Java/Mophun build
remains available for development. See [distribution materials](DISTRIBUTION.md)
and the product status for actual publication, not this build description.

## Source and Build

EKA2L1 is pinned to `11f5b6cd141c0884a130ee45743f1f5e17bb1783` from
<https://github.com/EKA2L1/EKA2L1>. Prepare that checkout, including recursive
submodules, in the ignored `local-games/eka2l1-audit/upstream` directory.
The wrapper rejects another root revision; no upstream checkout edits are
required. `core/patches.cmake` generates narrowly patched translation units in
the build directory and rejects missing or ambiguous source anchors. Original
source headers are retained. A dirty checkout or changed submodules still
require manual review.

The module uses Android NDK `28.2.13676358`, CMake `3.22.1`, C++20 and arm64-v8a.
The application's existing Java/Mophun toolchain is not globally upgraded.
On Windows, FFmpeg also needs MSYS2 Bash and GNU Make. The default MSYS root is
`$HOME/msys64`, overridden by `-PsymbianMsys=...`. Put its `usr/bin` on the
build-process PATH. Keep `CCACHE_DIR` on the workspace disk.

```powershell
.\gradlew.bat -PsymbianExperiment=true :app:assembleFdroidDebug :app:assembleFdroidDebugAndroidTest --max-workers=2
```

The isolated experiment enables Handheld and Phone debug. For Phone, use `assemblePhoneDebug` and
`assemblePhoneDebugAndroidTest`. Their application IDs are
`io.github.eriark.abyssme.symbian.debug` and
`io.github.eriark.abyssme.symbian.phone.debug`; neither replaces the production
app or inherits its library. `-PsymbianRelease=true` enables signed combined
release builds with normal application IDs. `-PsymbianReleaseCheck=true` instead
uses isolated rehearsal IDs and debug signing with release optimization.

## Host Boundary

- The shared library + and existing URI-install entry point use the optional
  `SymbianImportDialog` router. Direct Java/Mophun files keep their old installers;
  ZIP contents distinguish native games/platforms and system inputs. Mixed
  game/system archives fail explicitly. Inspection does not initialize native
  libraries in the main process. A cancellable ViewModel owns the staged input
  until it passes a validated private filename token to the native activity.
  Pre-journal process interruption can require reselecting the original file.
- Native multi-installer ZIPs and several files selected in Add game use
  `ArchiveBatch` / `ArchiveQueue`: preview, selection, component caching,
  per-file results and resume. Completed pending receipts prevent replay;
  uncertain native installations still require explicit retry. Legacy SIS and
  modern SIS/SISX use the existing installer. Each completed native installation
  publishes its phone's shared-library snapshot before the next phone opens.
  Done removes only retained queue copies. Batch limits are 256 candidates,
  20,000 ZIP entries and 1 GB; mixed Java/native/system collections are excluded.
  [Device checks and limits](../../docs/SYMBIAN_INTEGRATION.md#archive-queue-and-sissisx-import-2026-10-14).
- Native game RAR/7z inputs are normalized to a private ZIP through the existing
  pinned libarchive, in the Symbian process only. Content signatures route input;
  prepared-phone 7z retains the system importer. Game limits are 1 GB source /
  expanded data and 20,000 entries, with path/link/collision/encryption checks,
  cancellation cleanup and deterministic conversion. Nested compressed inputs
  must contain one recognized game. Mixed-platform collections are excluded.
  A queue can remember a phone per package type, without overriding prior game
  bindings or creating a global default.
  [RAR/7z and phone-choice checks](../../docs/SYMBIAN_INTEGRATION.md#rar7z-and-archive-phone-choice-2026-10-14).
- Installed apps now appear in the shared Gallery/List/Grid, search, Favorites
  and Recent. Per-system atomic snapshots and PNG artwork are read without
  loading the native engine in the main process. Stable negative IDs keep these
  engine-owned entries separate from the Java database and directory operations.
  Original N-Gage cards and writable-drive apps use native registrations; N-Gage
  2 games without a registration are identified through installed-package records,
  a platform game directory and a matching executable UID. No game names are
  hardcoded and no executable/rights checks are modified.
- Native icons include Symbian masks. N-Gage 2 square resources are preferred
  over its "large" banner for List/Grid; full banners are used in Gallery and
  details, fitted without cropping. Missing covers use the existing icon fallback.
- Play opens the internal, non-exported `:symbian` activity with an exact system
  identity and game UID. Stale entries fail without selecting another game or
  phone. Leaving a direct session returns to the shared library. The temporary
  System files page is available in Settings and the library menu.
  Native entries offer Play, Favorite and System files, not Java reinstall/delete,
  shortcuts or Java per-game settings. Native whole-phone backups use a separate
  phone-aware archive; external-launcher integration remains future work.
- System files and Library transfer offer Phone backups. Export includes the
  stopped phone's supplied firmware, writable drives, games/saves, portable
  settings and game profiles. Restore previews a bounded/hash-checked ZIP and
  promotes a new isolated identity; existing phones are not replaced. The shared
  import router recognizes backup ZIPs before ordinary system/game routing.
  Host paths, network secrets and executable engine resources are not restored.
  [Contract, checks and remaining limits](../../docs/SYMBIAN_PHONE_BACKUPS.md).
- The optional app source set owns `SymbianActivity`, `SymbianSessionUi` and the
  shared-input adapter. The core library does not depend on the app UI. Per-game
  profiles are isolated by system identity and unsigned app UID. In-game Display,
  controller mapping/presets/diagonals, short/held Select, reversible settings
  trials, screenshots and exit confirmation reuse the existing app components.
  Native rendering uses a TextureView so image rotation also rotates the visible
  pixels, not only a SurfaceView's layout; no unimplemented
  smoothing control is offered. Screenshots use the pinned engine's guest-frame
  capture, with host rotation and the shared gallery writer. They exclude menus,
  touch buttons and viewport borders. Capture is serialized with UI lifecycle
  operations; public-gallery writes run off-thread. The discarded PixelCopy path
  was unreliable after surface transitions on the test device.
  Phone uses the existing Phone/Gamepad touch decks with reserved game space;
  Handheld has no touch deck. Both variants forward direct touches inside the
  rendered picture, inverse-transforming the host view before native viewport
  conversion. Virtual keys and guest fingers retain separate ownership. Host
  pointer IDs use eight bounded guest slots; cancellation/pause/layout changes
  release them. Letterbox presses are ignored and captured drags are clamped.
  Open the menu with its icon or Back/Menu/Select; tapping the picture no longer
  pauses the guest. This does not add touch support to keypad-only games.
- System requirements distinguish Java/Mophun (no firmware), original N-Gage,
  legacy/modern S60 and N-Gage 2. The details name phone models and supported dump
  formats, separate the N-Gage platform installer from games, and offer the next
  missing input. Added files are not labelled universally compatible. The native
  setup/choice/pending dialogs use the shared dark menu; guest-originated text
  input and questions remain guest prompts. Modern SIS/SISX declared dependencies
  use the bounded metadata reader and version-checked installer cache. Legacy
  and conditional dependencies remain open; matching a UID/version does not
  guarantee ABI or gameplay compatibility.
- `core/host.cpp` adds prepare/start/pause/stop operations to upstream JNI.
  Preparing without firmware does not start graphics/audio/OS threads.
  Working-directory changes are confined to the separate Android process.
  Worker-owned session identity prevents late activity destruction from stopping
  a successor's prepared runtime during sequential imports.
- Android document-picker files are staged into private app storage with a
  1 GB game limit and a 4 GB system-archive limit. ROM/RPKG installation uses isolated device drives. Games
  and firmware are never APK assets or tracked fixtures.
- A system ZIP may contain one ROM and its optional RPKG. Extraction checks
  paths, entry count, expanded size and CRC, never merging archived writable
  drives or settings. The System files catalog retains validated ROM/RPKG sets
  by content hash with an isolated runtime for each set. Reimporting identical
  system files reuses the existing runtime; changed firmware creates a new one.
  Incomplete imports are staged privately and never published as ready sets.
- Prepared ZIP/7z collections may contain up to 64 phone sets under
  `data/roms/<phone>/SYM.ROM` and `data/drives/z/<phone>/...`, optionally wrapped
  in a parent folder. Only ROM and Z are imported: archived C/E drives,
  `devices.yml`, host settings and bundled executables are excluded. Preflight
  rejects traversal, links, case collisions, conflicting paths and excess sizes;
  extracted files are size/CRC checked and content hashed before installation.
  The archive entry limit is 250,000 and the expanded-size limit is 8 GB.
  Commons Compress reads headers; the existing native libarchive decodes 7z,
  including BCJ2. Native writes are bounded by the preflight entry sizes.
  Incomplete phone pairs are reported without blocking complete pairs.
  Version-2 catalog records identify ROM plus normalized Z content; version-1
  ROM/RPKG records remain readable. Each successful phone is promoted atomically,
  failed/pending environments are removed, and existing environments are not
  merged. A prepared dump and a ROM/RPKG pair have different identities even if
  their firmware code is the same. Unknown system versions are not guessed.
  Leaving the import screen cancels remaining work; already published complete
  sets remain available. Reopening cleans staging left by process interruption.
- Game import inspects SIS headers, N-Gage MIME headers and ZIP contents. Card
  archives may contain embedded SIS files beside their APPs. System generation
  filters candidate sets; saved package bindings win, otherwise a unique QD
  (RH-29) or N95 (RM-320) is preferred for the relevant N-Gage generation.
  If several recommended sets exist, the active one is reused; remaining
  ambiguity requires selection. The choice is retained by package hash. This does not yet check
  all SIS dependencies. Shared cards keep the exact selected environment identity.
  RAR/7z game archives and multi-game archives are not handled by this new automatic router.
- A supported English variant is selected from the phone's language list.
  Where available, Android Roboto is copied into the engine's fallback-font
  directory, outside guest drives; ROM fonts are preserved. Regional-dump
  font fidelity is not fully resolved.
- SIS/SISX and original N-Gage card archives use native installers. A `.n-gage`
  package is copied to the selected environment's E:\n-gage directory, without
  overwriting another package. The guest N-Gage 2.0 platform must finish its
  installation; staging is not a successful game launch.
- `PendingGameImport` retains one private input and an atomic progress record;
  `GameInstallFlow` owns system/dependency decisions separately from dialogs and
  native calls. Missing system files, deferred selection and picker cancellation
  retain the game. Successful warning-free system import resumes automatically.
  The selected phone is locked for the flow. An existing N-Gage platform is
  reused. If absent, a preloaded user-supplied installer is tried; otherwise the
  flow requests its SIS/ZIP. Platform launcher registration is checked before
  staging the game. The guest platform then opens automatically.
  Continuing a handed-off import never re-stages a consumed package. On return,
  the user confirms completion or keeps the import; guest installation success
  is not inferred from copying a file or launching the platform. Interrupted
  native installs require explicit retry. Cleanup only removes app-owned pending
  input, not guest data. `PlatformStore` retains user-supplied installers up to
  256 MB by SHA-256, with an atomic selected-version pointer. Preloading checks
  package UID `0x20003b78`; installation must register launcher UID `0x20007b39`.
  Hash/header validation is not universal phone compatibility. Previous immutable
  installer blobs are retained; a cache/version-management UI and general SIS
  dependency resolution are not implemented yet.
- The file-service adapter normalizes the legacy Spanish `sp` language tag to
  `es` only in supported achievement fields in the guest platform's temporary
  `C:\private\20007b38\import\achievements.xml`. The supplied Asphalt package
  fails in this language resolver without normalization. Original game archives,
  executable code and rights checks are not changed. XML is size/depth/node
  bounded; DTDs, conflicting translations and unsafe backups abort the repair.
  Original XML is retained by SHA-256 under the environment's
  `runtime/compatibility-backups`, outside guest drive cleanup. Replacement is
  atomic and valid/unrelated metadata is not reserialized.
- Direct-screen presentation no longer applies the extra physical-panel 180
  degree turn to a logical-mode buffer. Upstream read-back orientation handling
  remains separate; its array allocation/deallocation mismatch is corrected.
  This is not a user-selected screen-rotation feature or all-orientations proof.
- Window-group key captures now support cancellation for both normal keys and
  up/down events. Cancellation is scoped to the requesting window, handle and
  capture type; closing a group removes its remaining captures without touching
  other windows. Standard capture routing selects one recipient by event type,
  scan/key code, modifier mask, signed priority and newest matching handle.
  Normal key captures do not steal raw up/down events, and repeat events use
  the same normal-capture selection. A cancelled pending repeat is discarded
  before delivery. Long-key capture and platform-specific protected-key policy
  are not implemented by this adapter.
- D-pad, stick, action buttons and soft keys have basic scan-code bindings,
  shared held-key ownership, pause cleanup and disconnect cleanup. This is
  not yet the shared AbyssME mapper or Phone touch overlay.
- Camera/location/vibration callbacks explicitly provide no capability. No
  new Android permissions are requested by this module. The TLS callback is
  retained from upstream, including its copyright and license header.
- Lua scripting is disabled; built-in native patch support remains enabled.
  Do not infer compatibility parity from a successful native build.

## Checks

`tests/.../SymbianRuntimeTest.java` contains asset/bootstrap, ZIP-integrity,
fallback-font and card-staging checks. `installProvidedPhoneAndCard` is conditional on private
instrumentation arguments `rom`, optional `rpkg`, and `card`, resolved only
under the target app's external-files `symbian-input` directory. A skipped
fixture check is not a game-compatibility pass. It selects a stable `firmware`
code (RH-29 by default) before importing, rather than using whichever phone was
last active. `inspectProvidedPhoneArchive` accepts `phoneArchive` and `inputCount`;
the modern platform check accepts `modernRom`, `modernRpkg`, `platform` and
optional `package`. Reuse flags explicitly skip reinstalling existing firmware
or the platform. The game session check captures frames for manual inspection;
its assertion only covers foreground/session/return behavior, not gameplay.
Optional `stepScreens=true` saves a frame every five test-loop ticks to locate
transitions; the additional screenshot work changes test timing and is not an
identical-timing replay.

`SystemFileCatalogTest` covers duplicate reuse/save preservation, environment
isolation, persistent bindings, interrupted import cleanup and package routing.
`catalogProvidedSystemsAndGames` uses `catalogFixtures=true` with private
`ngage.zip`, `n95.zip`, `snakes.zip`, `asphalt3.n-gage`; optional
`catalogModern=true` also installs `ngage2.sis` and stages the modern package.
`selectPreloadedSystemThroughUi` uses `catalogUi=true` after fixture provisioning
to check selection/reopening and capture the temporary System files UI. These
are not complete Android document-provider or backup/restore acceptance tests.

`NgageMetadataTest` has seven checks for scoped normalization, unchanged valid
metadata, conflicts/malformed/DTD/oversized input, Unicode/BOM, original-byte
retention, backup failure, idempotence and bounded XML tree complexity. These
tests do not establish game stability or compatibility with every N-Gage package.

`PreparedSystemArchiveTest` covers ZIP/7z parity, content-based reuse, changed-Z
isolation, retained saves/bindings/selection, partial collections, path/link/CRC
rejection, cancellation cleanup, legacy ZIP fallback and native metadata errors.
`importProvidedCollection` requires `collection=true` and the private
`symbian-input/collection.7z`; it records import results and compares existing
drive-file hashes before/after. `importPreparedArchiveThroughPicker` requires
`preparedUi=true` and an external test driver to select the prepared fixture in
the real Android picker. Neither check proves that every imported phone boots
or every game is compatible.

`tests/run-native-tests.ps1 -Serial <adb-serial>` compiles the capture-cleanup
and capture-routing executables with the pinned NDK and runs them on an arm64
Android device. The 12 cleanup assertions cover owner/type/handle isolation,
priority preservation, repeated cancellation, group destruction and empty
queues. The 27 routing assertions use upstream's heap container and cover
scan/key separation, focus fallback, missing focus, modifiers, signed priority,
equal-priority recency, cancellation and repeat flags. These check shared helpers,
not a complete guest window-server protocol session or the repeat timer itself.

`GameInstallFlowTest` covers durable input/recovery, missing or ambiguous systems,
locked dependencies, platform validation/reuse, explicit interrupted retries,
payload changes, completed-journal cleanup and link-safe removal. Its
`realPlatformDependencyInIsolatedEnvironment` check uses `installFlow=true` with
private `n95.zip`, `ngage2.sis` and `asphalt3.n-gage` inputs in a fresh test catalog.
It does not mutate the user's existing catalog or finish the guest game install.
`pendingImportCanReopenCancelSelectionAndRemoveThroughUi` exercises actual menu
reopening/deferral/removal when multiple modern systems exist.
`retainedNgageImportOpensPlatformWithoutRestaging` uses `handoffUi=true` to resume
an already consumed Asphalt import on its bound system, verify platform handoff
and return, and restore the previous system selection. It never reinstalls the
game. These checks are not full process-kill/power-loss or gameplay acceptance.

Follow [integration status](../../docs/SYMBIAN_INTEGRATION.md) for actual build
and device observations. Gameplay, audio, durable save/relaunch, interrupted
installs and whole-environment backups need separate acceptance.

`SymbianLibraryTest` covers snapshot identity, atomic rejection, stale-entry
replacement, icon masks and banner fitting. `library=true` refreshes existing
bound phone indexes without reinstalling games, verifies both supplied game
UIDs/artwork and separate stable launcher IDs. `SymbianLauncherTest` uses
`libraryUi=true` with those installed fixtures to exercise Gallery/List/Grid,
search, safe options, Favorites/Recent, controller-triggered direct launches,
return to the main library and rejection of an absent UID. It restores the
previous presentation preferences and selected system. Its timed game captures
are launch/UI evidence, not a full-game, listening or save/relaunch check.

`UnifiedImportTest` covers content routing, mixed/unsafe archives, saved-platform
integrity/backup recovery, link rejection and cancelled-input cleanup.
`UnifiedImportUiTest` uses `importUi=true` and the existing private fixture/system
catalog to exercise the real library +, import result pipeline, Java chooser,
error dismissal, platform preload, deferred native-game choice and system reuse.
Only the OS picker result is stubbed in this automated test. The separate real
DocumentsUI selection and the isolated saved-platform native installation are
recorded in the integration status, not inferred from these synthetic cases.

## Distribution Boundary

New host code uses the repository's GPL-3.0-only scope. The pinned EKA2L1
source has GPL-3.0-or-later notices; inherited terms are not replaced.
[Distribution materials](DISTRIBUTION.md) document the pinned source export,
curated guest patches/shaders, separate GeneralUser GS bank and component
notices. Initialized submodules and a build alone are not release acceptance.
Never publish firmware, private games or debug/rehearsal APKs as release assets.
