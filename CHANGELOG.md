# AbyssME Changelog

User-facing development history, reconstructed from commits, release notes and
local/private build records through **2026-10-18**. This is not a claim that all
listed work is in a public APK. See [current status](docs/PROJECT_STATUS.md) for
availability, verification, known limits and screenshot guidance.

Labels: **Public source** means implemented on `main` but unreleased unless
stated otherwise. **Experimental** in older checkpoints means local/private
combined builds; released experimental features are explicitly identified.
**Released** means an actual GitHub release asset.

## 2026-10-18

- **Public source, unreleased:** fixed stale Java Graphics targets after buffer
  growth, GameCanvas buffer resizing and size-notification ordering. Hardened
  GLES context/geometry updates and ignored obsolete surface callbacks. These
  address shared resize failure classes, not every game's black screen.
  Stable-size GLES frames reuse texture storage; no measured FPS gain is claimed.
- **Public source, unreleased:** recognize explicit paired screen dimensions
  such as LG TargetLCD, and prefer original over target-display resolution.
  No per-title rules or resetting of existing settings.
- **Public source, unreleased:** restore transfer source/save/settings folders,
  subfolder access and pending picker state after Android process recreation.
  A returned selection cannot be lost to an automatic rescan; interrupted
  imports/exports are not silently repeated.
  A real emulator process-death/DocumentsUI round trip returned to review with
  the selected game's saves included, without committing an import.
- **Public source, unreleased:** protect Symbian replacement sessions from stale
  exits/surfaces and prevent background surface/menu events from resuming games.
- **Verified locally:** both base and combined variants built; zero lint errors,
  29 JVM tests per variant, 98 final emulator checks and 98 earlier Retroid checks.
  A separate Snakes UI/pixel check passed on Retroid. Added Canvas/GameCanvas
  frame-cap coverage; no universal simulation-speed claim. Physical testing was
  stopped when another task began using the device. Public beta.4 is unchanged.
  Final Phone emulator checks also covered Bounce Tales size trials/Undo,
  restart and exit, with inspected game images rather than a full playthrough.
  [Evidence and limits](docs/checks/2026-10-18-runtime-transfer.md).

## 2026-10-17

- **Released:** [0.3.0-beta.4](https://github.com/EriArk/JavaEmulator/releases/tag/v0.3.0-beta.4),
  code 303, published at 20:32 UTC. Both signed combined APKs, matching app
  source, dependency/native sources and checksums are available. All six
  uploaded digests matched; both APKs and the checksum list were downloaded
  again and verified. Source tag: `7b4742f8`.
- **Included in beta.4:** Gallery now offers Automatic / Wide / Portrait
  cover formats through Library menu. Covers fit without cropping, cards stay
  aligned, and the choice survives activity recreation. Artwork files and
  List/Grid icon behavior are unchanged.
- **Included in beta.4:** square and near-square handhelds use a compact
  library header without the detail pane crowding games. Layout responds to
  available dp, aspect ratio and font scale, not a device-specific rule. Phone
  portrait and short-landscape layouts remain supported.
- **Included in beta.4:** added Restart game to the shared game menu for
  Java, Mophun and Symbian. Confirmation preserves saved settings and discards
  unconfirmed previews. Fixed a stale Symbian menu-dismiss callback that could
  pause the new session. This is not a general black-screen compatibility fix.
- **Verified locally:** both developer variants built; 29 base JVM tests each,
  zero lint errors, emulator layout checks and physical Retroid checks. Bounce
  Tales, Deep Abyss and Snakes restarted to their game menus. New captures and
  explicit verification limits are in the [check record](docs/checks/2026-10-17-gallery-restart.md).
  These development checks preceded the separate signed-release verification.
- **Final release checks:** 29 release JVM tests per variant, no failures/errors;
  lint passed with 403/410 warnings. Both signed variants installed on Retroid.
  Gravity Defied reached its track and restarted; Phone G.R.A.W. 2 reached its
  menu with numeric and Gamepad controls. Only Handheld beta.4 remains installed,
  with 39 library entries. [Verification and limits](docs/releases/v0.3.0-beta.4-checks.md).
- **Post assets:** added [11 fresh beta.4 screenshots](screenshots/releases/v0.3.0-beta.4/).
  Replaced obsolete promotional folders and pinned historical evidence links.
  README and status/download documentation now point to beta.4.

## 2026-10-16

- **Device-only:** added 30 user-supplied Java games to Retroid's Handheld
  beta.3 library, bringing it to 39 entries. Gravity Defied reached its track
  and was added to Favorites. A crashing Doodle Jump fixture was excluded.
  Source hashes and preservation of the original local catalog entries were
  checked; full-game compatibility was not. No app code, APK or public game
  bundle changed. [Checks](docs/checks/2026-10-16-retroid-collection.md).

## 2026-10-15

- **Released:** [0.3.0-beta.3](https://github.com/EriArk/JavaEmulator/releases/tag/v0.3.0-beta.3),
  code 302, published at 19:16 UTC with both signed APKs, matching source bundles
  and checksums. All six uploaded digests matched; both APKs were downloaded
  again and verified. The October 15 changes below are now included in this
  release; their earlier "unreleased" labels describe the development checkpoints.
  Retroid retains only the latest Handheld package, with nine game entries;
  17 old/alternate packages were removed while retaining their data.

- **Included in beta.3:** fixed N-Gage card identification
  in a mixed SIS/card phone, shared by the catalog, library and removal checks.
  Native regressions cover multiple cards and hidden-app exclusions. Both signed
  variants built, passed APK guards/lint and 25 JVM tests each, and upgraded on
  Retroid. Snakes reappeared without reimport and reached gameplay; the Phone
  Gamepad and shortcut choices survived the upgrade. Beta.2 was withheld before
  publication and its source tag is unchanged.
  [Checks](docs/releases/v0.3.0-beta.3-checks.md),
  [fresh captures](screenshots/releases/v0.3.0-beta.3/).

- **Withheld candidate:** 0.3.0-beta.2, code 301, combined signed Handheld and Phone
  APKs. Both release JVM suites passed 25 tests; production-content guards and
  lint passed (402/409 warnings retained). Both signed variants upgraded from
  code 300 on Retroid. Phone shortcuts, both decks and a haptic request were
  checked in the final APK. Matching source/dependency materials are prepared;
  this checkpoint is not yet public availability.
  [Release checks](docs/releases/v0.3.0-beta.2-checks.md).

- **Public source, unreleased:** added On-screen shortcuts to More and Playing
  settings. Java Phone can hide Display and Screenshot independently while
  retaining Menu and both menu actions; preferences survive game/UI transitions.
  Restored tactile feedback to Phone/Gamepad touch controls across Java, Mophun
  and Symbian, with one pulse per new virtual key rather than per direction or
  repeat. The shared Controls page exposes the existing per-game setting with
  Try / Keep / Undo; Android touch-feedback policy remains respected. Physical
  controllers do not gain automatic vibration. Retroid Phone passed 47 checks,
  Handheld 45 with two Phone-only skips, and the Phone emulator 43. A Retroid
  touch pulse was confirmed by Android's vibration service, not by a human
  sensation check. No public APK update.
  [Details and screenshots](docs/checks/2026-10-15-phone-shortcuts-feedback.md).
- **Public source, unreleased:** separated Java game screen startup from Android
  surface creation so early buffered frames are not discarded while the host
  prepares its view. Fixed obsolete screen callbacks, hidden repaint state,
  fullscreen-size notification on return and deferred/duplicate game alerts.
  Lifecycle work stays queued in immediate-event mode to avoid a Display lock
  deadlock. Added 15 shared-mechanism regression cases, with no game-specific
  branches or settings. Both Retroid variants and the Android 15 Phone emulator
  passed 43 focused checks each. Ghost Recon reached the first level on Retroid;
  its historical intermittent report remains open. Public APKs are unchanged.
  [Checks and limits](docs/checks/2026-10-15-canvas-lifecycle.md).
- **Public source, unreleased:** automatic Java setup now retains filename
  hints such as `128x160`, `176x220` and `S40v3` through file-provider imports,
  ZIP/7z selection, recompilation and backup/restore. Explicit game metadata
  has priority; existing/manual settings and launcher identities are preserved.
  Paths, outer archive names and advertising addresses do not select a phone.
  Both Retroid variants and the Android 15 Phone emulator passed 67 focused
  checks each. Four fresh archive JARs on Retroid reached intro/title/menu
  screens with the recorded automatic setup. This does not establish full-game
  compatibility or resolve the intermittent Ghost Recon failure. No release
  APK update. [Evidence and limits](docs/checks/2026-10-15-source-hints.md).
- **Public source, unreleased:** added a genuine Light appearance, selectable
  with Dark and Follow system in Settings. The library, settings, installation
  and transfer screens, shared game menu, controller mapper and both Phone touch
  layouts use paired palettes. Native engine activities honor the app override
  instead of silently reverting to the system theme. Game artwork and rendered
  game pixels are unchanged. Fixed the narrow portrait mapper's overflowing
  Identify button using a named, tooltip-equipped icon button. Both Retroid
  Phone and the Android 15 Phone emulator passed 29 focused checks each;
  Retroid Handheld passed 28 with one Phone-only skip. Checks include theme
  persistence, contrast and control layout/input checks.
  No new public APK; the reporter's Samsung Android 16 remains unverified.
  [Evidence and screenshots](docs/checks/2026-10-15-light-theme.md).
- **Public source, unreleased:** cancelled external imports and closed errors
  now return to the calling launcher instead of leaving the AbyssME library
  open. The fix covers Java choices/errors, Mophun import errors, Symbian
  inspection cancellation and invalid native launch files. Imports started
  inside AbyssME still return to its library. Real Beacon 1.8.10 launches on
  Retroid covered Java, Mophun and Snakes in both debug variants, including
  first Java imports, prepared games, ZIP selection and explicit copy reuse.
  Added exact Beacon setup instructions. Both Retroid variants passed 27
  focused Android checks; the Phone emulator passed 26 with one Symbian-only
  skip. ES-DE remains untested; no release APKs changed.
  [Evidence and limits](docs/checks/2026-10-15-beacon-launcher.md).
- **Public source, unreleased:** importing the same Java game through another
  URI now offers **Use existing** or **Install separately** instead of quietly
  creating a duplicate. Reuse keeps the original game, saves, settings and
  launcher references; the choice is remembered for that source and content.
  Changed JAD properties prompt again. Multiple matching copies remain separate
  and can be selected explicitly. Room v4 adds remembered choices without
  deleting catalog rows; reinstall preserves them. Existing duplicates are not
  automatically merged. Both Retroid variants and a portrait Android 15 Phone
  emulator passed 42 focused Android checks each; both combined variants passed
  25 JVM checks. Public release APKs are unchanged.
  [Scope, evidence and screenshots](docs/checks/2026-10-15-source-aliases.md).
- **Public source, unreleased:** Symbian game actions now includes **Uninstall
  from phone**, showing the target phone, package, affected apps and save-data
  consequences. Cancel is the default; a shortcut opens the exact phone's backup
  flow. Dependency/ownership guards block unsafe removals. Interrupted removals
  keep their library references until the actual native result is verified.
  Retroid Handheld/Phone checks covered three real SIS/SISX games, cancellation,
  removal, reinstall, dependency rejection and unchanged original phone data.
  Each variant passed 25 JVM and 39 focused Android checks, with 2 optional skips.
  Card-only and N-Gage 2.0 platform game uninstall remain unsupported; no automatic
  rollback is promised. Public APKs are unchanged.
  [Evidence and limits](docs/checks/2026-10-15-symbian-uninstall.md).
- **Public source, unreleased:** Java frame-rate limits now show their current
  value in More, offer presets in the shared menu and Display, and support
  per-game Try / Keep / Undo. Fixed limits not being saved from the old runtime
  dialog. Replaced wall-clock/integer-millisecond pacing with a monotonic,
  concurrent-safe per-Canvas budget. This is not a universal game-speed control.
  Both variants passed 25 JVM and 31 Retroid checks; Asphalt 3D retained the
  selected limit after relaunch. The exact fast-game report remains unverified
  (#22). [Evidence and limits](docs/checks/2026-10-15-frame-rate.md).
- **Public source, unreleased:** added raw ROM + RPKG import from ZIP/7z with
  nested/uppercase files, shared validation, cleanup and persistent errors.
  Existing virtual phone data is not replaced. Real N95 archive imports and
  reimports passed through both Retroid app variants; the reported N70 dump
  itself was not tested (#23).
- **Public source, unreleased:** Game actions > Game information displays the
  installed MIDlet-Version/vendor, retaining the original version string and
  existing JAD/JAR resolution. Missing/corrupt metadata is explicit; renamed
  games and saves are not changed (#21).
- **Public source, unreleased:** native games can export `.abyssme` launch
  files to start an installed game without repeating installation. Fixed
  Mophun exit returning to the AbyssME library after external launch. Added
  Handheld/Phone frontend examples. Tested real Pegasus and Daijisho routes on
  Retroid, including content URI grants, exit/return, multi-JAR selection and
  a warm Java launch. ES-DE/Beacon acceptance remains open in #7.
- Combined debug builds passed; each variant passed 19 JVM checks, 19 focused
  Android checks (2 optional skips) and 3 opt-in UI checks. No release APKs were
  replaced or new test builds distributed. Public 0.3.0-beta.1 remains unchanged.
  [Evidence and remaining boundaries](docs/checks/2026-10-15-import-launchers.md).

## 2026-10-14

- **Released: 0.3.0-beta.1**, 2026-10-14 at 16:18 UTC. Java, Mophun and experimental Symbian/N-Gage now
  share one in-game menu, with paused exit confirmation and consistent
  Display/Controls/Screenshot/More actions. Fullscreen game menus retain hidden
  system bars on both variants. The signed arm64 Handheld/Phone APKs include the
  accumulated system-file setup, native archive import, dependency guidance,
  touch/controller controls and whole-phone backups described below. Rebuilt
  the native engine from exported pinned sources; curated redistributable assets
  and bundled component notices. Handheld upgrade retained all 18 inspected
  library/save/config files. Both signed variants installed on Retroid and
  imported system files/Snakes through the public UI. Optimized rehearsals:
  175 core checks and 10 game/menu scenarios passed; 11 explicit optional skips.
  Published both arm64 APKs, app/native/dependency source bundles and checksums;
  all remote digests and re-downloaded APK hashes matched. Final device results are tracked in
  [release checks](docs/releases/v0.3.0-beta.1-checks.md).

The local-only entries below are historical implementation checkpoints. Their
code is now part of this public combined beta; private games and firmware are not.

- **0.3 beta preparation:** unified the Java/Mophun/Symbian in-game menu and
  paused exit confirmation, moved Screenshot to the home page and its button
  visibility to More, and kept Resume as the first controller action during
  trials. Retroid debug UI checks: 8 pass / 2 skip on Handheld, 9 pass / 1 skip
  on Phone. Preparing combined arm64 signed builds, pinned source materials,
  curated engine assets and GeneralUser GS attribution. Not yet a publication
  claim. [Release checks](docs/releases/v0.3.0-beta.1-checks.md).

- **Local Symbian RAR/7z import / not shipped:** native game archives now enter
  the existing preview/install flow without manual unpacking. Verified the
  supplied Asphalt RAR, solid 7z with SIS/SISX, and RAR5 decoding. A phone choice
  can be reused for the same package type within one archive; existing game
  bindings take priority. Retroid: 83 focused checks passed per variant, two
  older opt-in cases skipped; three Handheld and two Phone UI checks passed.
  Three supplied games installed from 7z on disposable phones, with one choice
  reused for the two modern packages. Ordinary JVM tests: 14 passed per variant.
  Encrypted archives and universal mixed-platform collections are not supported.
  Local builds only, no public APK or push; installation is not gameplay
  acceptance. [Evidence](docs/SYMBIAN_INTEGRATION.md#rar7z-and-archive-phone-choice-2026-10-14).

- **Local Symbian archive queue / not shipped:** added ZIP batch preview and
  selection for SIS/SISX and native game packages, with reusable component
  installers, duplicate/alternate-version defaults and durable per-file
  results. Errors no longer discard the remaining queue; waiting games can
  be deferred and retried. Completed imports do not replay after journal
  recovery. Games installed on different phones all reach the shared library;
  Done clears only temporary queue copies. Retroid: 59 focused checks passed
  per variant, one older optional case skipped; two UI checks passed per
  variant. Actual legacy SIS, modern SIS and SISX games from the supplied
  archive installed on disposable phone copies. Phone layout checked in both
  orientations; ordinary JVM suites rerun, 14 passed per variant. Installation
  is not gameplay acceptance. Local experimental builds only; no public APK
  or push. [Evidence and limits](docs/SYMBIAN_INTEGRATION.md#archive-queue-and-sissisx-import-2026-10-14).

- **Local Symbian component setup / not shipped:** modern SIS/SISX imports now
  identify declared missing components by package UID and version, show the
  required name/version, and continue the retained game after its installer is
  supplied. Saved installers can be preloaded, reused and removed without
  uninstalling games. The same flow covers dependencies of the N-Gage 2.0
  platform installer. Added bounded metadata parsing, preflight dependency
  planning, corrupt-cache rejection, registration checks and explicit retry
  after interrupted installs. Retroid: 43 checks passed per variant, one older
  opt-in scenario skipped per variant; a separate real document-picker/native
  installation workflow passed on a disposable N95 copy. Legacy SIS requisites,
  conditional dependencies and full game compatibility are not claimed.
  No public APK or push.
  [Evidence and limits](docs/SYMBIAN_INTEGRATION.md#component-installers-and-resumable-setup-2026-10-14).

- **Local Symbian direct touch / not shipped:** connected touches on the game
  picture while keeping numeric/Gamepad controls independent. Added rotated
  viewport mapping, bounded pointer IDs and release on pause/focus/layout
  changes. Replaced SurfaceView with TextureView after device captures exposed
  missing visible rotation; fixed resize reference cleanup. Also fixed phone
  backup before first boot without modifying the original phone. Retroid:
  24 focused checks pass per variant, native Nokia 5800 Calculator touch/result
  checks pass at all four rotations, and the Phone Snakes/display/mapper/deck/
  screenshot workflow passes. These are automated device checks, not complete
  touchscreen-game acceptance. No public APK or push.
  [Evidence](docs/SYMBIAN_INTEGRATION.md#direct-touch-and-visible-rotation-2026-10-14).

- **Local Symbian phone backups / not shipped:** added menu-based export and
  restore of a stopped virtual phone, including supplied firmware, games, saves
  and game settings. Restore previews the contents and creates a separate copy;
  existing phones and saves are not replaced. Backup ZIPs are recognized by the
  shared importer. Added hash/path/space checks, failed-transfer cleanup and
  native validation before publication. Backup-only navigation no longer boots
  the original phone or triggers its incidental OS writes. Tested archive
  rejection/recovery and a restored Snakes launch on Retroid; actual SAF export
  was independently hash-checked. Ordinary JVM tests remain 14/14 per variant.
  No public APK, push or licensing change.
  [Contract and evidence](docs/SYMBIAN_PHONE_BACKUPS.md).

- **Local Symbian controls and clearer setup / not shipped:** added the shared
  in-game Display, graphical controller mapper/presets, diagonal bindings,
  screenshots and reversible settings, saved per game and virtual phone. Phone
  now has numeric and Gamepad touch decks in portrait and landscape. System
  files names required phones/formats, separates firmware from the N-Gage 2
  installer and games, and offers the next missing input. Setup/pending-import
  dialogs use the app's dark menu. Replaced unreliable Android-surface capture
  with the engine's game-frame capture after rotation/menu transitions.
  Broad Retroid regression run before the final capture change: 68 checks passed,
  nine conditional skips. Final APKs: eight focused checks passed and one fixture
  skip per variant; native Phone screenshot flow also passed three repeat runs.
  Ordinary Handheld/Phone compilation and 14 JVM tests per variant pass.
  Direct guest touchscreen input, whole-phone backup and general dependency
  resolution remain open; individual game stability is deferred. No public
  APK, push or licensing change.
  [Evidence and limits](docs/SYMBIAN_INTEGRATION.md#common-controls-and-setup-guidance-2026-10-14).
- **Local unified Symbian import / not shipped:** the shared library + now routes
  Java archives, Symbian games and system archives to their appropriate importer.
  Users can preload their N-Gage 2.0 installer once; games reuse it on their
  selected phone when the platform is missing. Native registration is checked,
  failed installs require explicit retry, and pending game input is retained.
  Added a cancellable dark inspection dialog and rejected ambiguous archives.
  Fixed a native crash when a previous import screen shut down a newly opened
  session. Retroid: 61 checks passed, eight conditional skips; actual Downloads
  picker preload also verified. Ordinary Handheld/Phone compilation passes.
  No new full-game/audio/save claim, public APK, push or licensing change.
  [Evidence and limits](docs/SYMBIAN_INTEGRATION.md#unified-import-and-saved-platform-2026-10-14).
- **Local shared Symbian library / not shipped:** installed Symbian and N-Gage
  games now appear beside Java/Mophun in Gallery/List/Grid, search, Favorites
  and Recent. Native icons and N-Gage banners are extracted separately; wide
  title banners fit without cropping. Play uses each game's exact phone and UID,
  skips the temporary environment page and returns to the shared library.
  Package-only N-Gage 2 apps are detected without game-name special cases.
  Stale entries fail safely and native options exclude Java file operations.
  Retroid: 53 checks passed, eight conditional skips, including direct Snakes
  and Asphalt launches, return, artwork and retained-import regressions.
  Ordinary Handheld/Phone Java compilation passes without the Symbian module.
  This does not resolve earlier intermittent Asphalt startup or establish
  audio/save/full-game compatibility. No public APK, push or license change.
  [Evidence and limits](docs/SYMBIAN_INTEGRATION.md#shared-library-and-direct-launch-2026-10-14).
- **Local resumable Symbian import / not shipped:** game input now remains
  available while adding missing system files or the N-Gage 2.0 platform.
  Successful system import resumes automatically; dependency installation stays
  on the game's chosen phone. Existing platforms are reused and N-Gage opens
  automatically after staging, without requeuing packages already consumed by
  the guest. Added durable progress, explicit retry after an interrupted native
  install and isolated pending-input removal. On Retroid, 44 checks passed and
  eight conditional cases skipped, including a fresh isolated N95/platform
  setup, retained-import UI and handoff to the existing N-Gage installation.
  Guest confirmations and completion acknowledgement remain interactive; this
  is not a new gameplay or full-game compatibility result. No public APK/push.
  [Evidence and limits](docs/SYMBIAN_INTEGRATION.md#resumable-game-import-2026-10-14).
- **Local prepared-system collections / not shipped:** added menu import for
  multi-phone ZIP/7z collections. ROM and read-only Z files are isolated per
  content identity; archived writable drives, settings and saves are excluded.
  Added native BCJ2 decoding, bounded writes, CRC/hash verification, cancellation
  cleanup and explicit partial-import results. Fixed incomplete metadata reads,
  folder-alias handling and empty model labels; unknown OS versions are no
  longer guessed for this import path. Existing game bindings take priority,
  with N-Gage/N95 recommendations when the choice is unambiguous. Retroid
  registered all 21 complete supplied phone sets; repeat import reused 19 and
  completed two after the metadata fixes. Selection, bindings and 588 existing
  writable-drive files were unchanged. Real Android picker/reimport checks pass;
  baseline: 33 passed, eight conditional skips. These are import checks, not
  all-phone boot or game compatibility. No public APK or firmware publication.
  [Evidence and remaining work](docs/SYMBIAN_INTEGRATION.md#prepared-system-collections-2026-10-14).
- **Local Symbian key routing / not shipped:** corrected captured-button
  delivery, separating scan codes from logical keys and choosing one recipient
  by modifiers, signed priority and request recency. Removed implicit priority
  escalation and duplicate focus delivery; repeats respect normal captures and
  cancelled pending repeats are dropped before delivery. Native checks pass on
  Retroid: 27 routing plus 12 cleanup assertions. Two launches of the existing
  Asphalt installation reached races; acceleration, pause and resume were
  observed. Snakes also reached gameplay and responded to D-pad turning.
  The Asphalt runs did not reproduce `USER 44` or unexpected backgrounding,
  but do not establish their root cause or deterministic startup. No firmware,
  games or saves were replaced. Baseline instrumentation: 21 passed, six
  conditional skips. [Evidence](docs/SYMBIAN_INTEGRATION.md).
- **Local Symbian input repair / not shipped:** implemented window-group key
  capture cancellation and cleanup on group destruction. Requests no longer
  remain registered after explicit cancellation or their owning window closes.
  Twelve native regression assertions pass on Retroid. Three diagnostic-build
  launches of the already installed Asphalt reached race frames without game
  reinstallation; a preceding run still reported guest `USER 44`, so this does
  not close the intermittent stability issue. Temporary focus/panic tracing was
  removed from the final local build. Its sampled runs still show both races
  and unexpected return to the platform; neither startup issue is declared fixed.
  Final baseline: 21 passes and six conditional skips. The supplied 1.47 GB system archive has
  also downloaded successfully and passed 7-Zip integrity checks; its 21 system
  sets remain local and have not been bulk-imported or declared compatible.
  [Build and acceptance boundaries](docs/SYMBIAN_INTEGRATION.md).
- **Later local N-Gage compatibility work / not shipped:** resolved Asphalt 3's
  guest installation Error -1 by normalizing an unsupported Spanish language
  tag in temporary achievement metadata. Original archives are untouched and
  the original XML is retained outside guest cleanup. Added seven regression
  tests, including unsafe XML, backup failure and bounded tree complexity.
  Removed the extra 180-degree direct-screen presentation turn. On Retroid,
  Asphalt reached a moving race with upright graphics; Snakes gameplay was
  replayed. Cold Asphalt relaunch remains unreliable, and one earlier attempt
  reported guest `USER 44`; neither is claimed fixed. Final baseline: 21 passes
  and six conditional skips. These later observations supersede the installer
  blocker recorded below, not the remaining stability/audio/save gates.
  [Evidence and exact build boundaries](docs/SYMBIAN_INTEGRATION.md).
- **Local Symbian experiment / not shipped:** Snakes reaches 3D gameplay on
  Retroid using the supplied N-Gage system files. Fixed card discovery, startup
  font initialization and guest shutdown/return. Added bounded system ZIP
  extraction with integrity/path checks without copying archived saves or config.
  Both supplied system archives pass extraction checks; seven baseline tests
  pass, with four private-input tests skipped in that suite and tested separately
  where noted. No audio, durable-save or full-game acceptance is claimed.
- **Local N-Gage 2.0 work / incomplete:** the supplied N95 environment and
  platform install; regional-English selection fixes the observed platform
  launch failure. Android font fallback makes missing text readable, with
  typography still imperfect. Fixed memory-card package staging. Asphalt 3
  was recognized but its guest installation reported Error -1 at this earlier
  checkpoint; see the later fix above. Source remains uncommitted on the
  experiment branch, no APK published.
- **Local system catalog / not shipped:** system archives can now be added
  in advance in the experimental System files page. Identical ROM/RPKG content
  reuses its existing system without reinstalling; different firmware has isolated
  state. Game import selects a matching generation or asks when several match,
  then remembers the choice. Fixed card ZIPs containing embedded installers,
  including Snakes. Added atomic catalog writes, interrupted-import cleanup and
  path/size/duplicate checks. Retroid passes 14 baseline cases, with six conditional
  cases skipped; private fixture and system-selection UI checks pass separately.
  Full dependency matching, the shared library and game-import resumption after
  missing-system prompts remain open. At this checkpoint Asphalt also reported
  Error -1 on a fresh managed N95; package staging alone is not game installation.
  [Evidence and remaining work](docs/SYMBIAN_INTEGRATION.md).

## 2026-10-13

- **Local Symbian experiment / not shipped:** added an opt-in embedded EKA2L1
  Android host, built as a separate arm64 debug package without replacing the
  ordinary Java/Mophun app. Added private ROM/RPKG and game-picker paths,
  environment/app selection, basic controller bindings and lifecycle adapters.
  Native build and four firmware-free Retroid checks pass; the private game
  installation check is skipped. Ordinary Handheld still builds and its 14 JVM
  tests pass. Snakes, Asphalt 3 and the supplied N-Gage 2.0 platform SIS remain
  private. Subsequently located N-Gage/QD and N95/5320 system archives in the
  user's Nextcloud; not yet imported and no accepted gameplay. Shared library, backups,
  Phone controls and dependency/asset publication review remain outstanding.
  [Prototype scope and evidence](docs/SYMBIAN_INTEGRATION.md).

- **Released:** [0.2.0-beta.1](https://github.com/EriArk/JavaEmulator/releases/tag/v0.2.0-beta.1),
  the first public combined Java + experimental Mophun beta. Handheld/Phone APKs,
  matching source/dependency archives and checksums are uploaded; all five GitHub
  asset digests match local files. Clean `main` release builds, lint and APK guards
  pass. Both debug variants also build from a source ZIP without Git history or
  private signing files. Existing package IDs/signing key remain unchanged.
  [Release notes and limitations](docs/releases/v0.2.0-beta.1.md).

- **Production-switch batch / release preparation:** ordinary builds now use
  the FreeJ2ME-derived M3G replacement and independent tone compiler. Removed
  the inherited EPL Java/native M3G and tone sources; Mascot Capsule remains.
  Promoted the Mophun runtime and fixture-free checks into the same app, without
  private games or preview activities. Added GPLv3 combined-app licensing,
  retained component notices, source inventory and an APK contamination guard.
  Prepared Handheld/Phone 0.2.0-beta.1 (code 200) with the existing release key.
  Actual Phone beta.2 upgrade retains the library and RMS read/write; Retroid
  passes 363 production renderer checks and both devices pass 25 Mophun checks.
  This entry alone does not announce publication. [Report](docs/releases/v0.2.0-beta.1-checks.md).

- **Public source / unreleased:** fixed an optimized-build crash when restarting
  a phone-profile trial. Touch settings and preparation hints now have stable
  JSON names; profile copying remains writable under R8. Also fixed JAR
  conversion of Java 7/8 exception handlers by retaining stack-map information,
  and removed a test-only transitive dependency from app runtime.
  Both ordinary variants build; 14 JVM cases pass each, plus three importer
  regressions. Five optimized profile checks pass on Retroid and five on Phone;
  Retroid passes 48 debug settings checks. [Details](docs/RELEASE_PROFILE_SAFETY.md).
- **Separate experiment / not shipped:** added isolated, optimized upgrade
  rehearsals. Real MIDlet RMS values survive original-to-replacement engine
  updates and remain writable on Retroid and Phone; all 11 inspected files
  retain their hashes. Retroid passes 324 release-compatible renderer checks
  and the full 363 unmeasured debug suite. Not a production-signed upgrade or
  release clearance. [Evidence](https://github.com/EriArk/JavaEmulator/blob/codex/m3g-replacement/experiments/m3g-host/release-20261013.md).

- **Public source / unreleased:** More now provides Screen presets and Phone
  profile. Screen size can be tried live; compatibility presets explicitly
  restart the game, with Keep / Undo and automatic rollback. Current custom
  properties, controls and confirmed settings are preserved until Keep.
  Automatic preparation now requires stronger phone/display evidence instead
  of selecting a vendor from an API mention or an anonymous image alone.
  Retroid passes 43 focused settings/UI checks; Phone emulator passes the same
  43 plus one renderer regression. Both ordinary debug variants build and pass
  14 JVM cases each. [Workflow and evidence](docs/GAME_PRESETS.md).
- **Separate experiment / not shipped:** reduced redundant GPU texture bindings,
  target setup and repeated mesh index validation. Same-APK Retroid GPU A/B
  retains all 34 pixel CRCs, with roughly 4-5% lower wall cost in sprite/cache-
  churn scenes; many other cases change little. Final renderer suite passes
  371 checks. Not a universal game-FPS gain, engine switch or APK distribution.
  [Evidence](https://github.com/EriArk/JavaEmulator/blob/codex/m3g-replacement/experiments/m3g-host/presets-20261013.md).

- **Public source / unreleased:** screen preparation no longer mistakes anonymous
  square textures for a phone display. This fixes the cause of PGR 3D's cropped
  128x128 menus on fresh preparation, without a game-specific override or changing
  existing settings. Four new regression cases; 24 compatibility checks pass on
  Retroid and 24 on the Phone emulator. Both ordinary APK variants build and
  14 fresh JVM cases pass per variant.
- **Separate experiment / not shipped:** compared DiRT 3D and PGR 3D gameplay
  against the original engine on Retroid. Both sides sustain about 16.6 and 13.3
  surface buffers/host updates per second respectively. PGR remains more costly
  to render, but these short races show no sustained update-rate deficit. Not
  unique-frame, physical-controller, audio or full-game acceptance. Mophun uses a
  different renderer and was excluded. No APK distribution or engine/license
  switch. [Captures and limits](https://github.com/EriArk/JavaEmulator/blob/e9cea5c6/experiments/m3g-host/racing-20261013.md).

- **Separate experiment / not shipped:** specialized GPU programs by texture
  count and sprite/mesh path, reduced readback to color-writing rectangles and
  packed sprite lookup data in bulk. Same-APK Retroid comparison cuts flat/
  two-sided lighting costs by 22-34% and sprites by 9-14%; all 34 pixel CRCs
  match. Retroid passes 370 checks; Phone emulator passes 22 focused checks.
  Original speed parity remains open. No production switch, license change
  or APK distribution. [Changes and evidence](https://github.com/EriArk/JavaEmulator/blob/8d43830f/experiments/m3g-host/shaders-20261013.md).

- **Separate experiment / not shipped:** reduced LCDUI framebuffer copying,
  added an opaque-bitmap NDK read path, whole-frame GPU submission, hardware
  clears and conservative mesh rejection. Fixed depth offsets, write-only depth
  passes, render-target validation and RGB888 quality hints after original-engine
  review. Retroid synthetic costs fall 17-34%; all 34 before/after pixel CRCs
  match. Final Retroid suite passes 361 checks and Phone focused suite passes 36;
  the full Phone attempt timed out. Fresh Deep and Asphalt gameplay captures
  are recorded, including Asphalt lap progression. Original speed parity is
  still pending. No engine/license switch or APK distribution.
  [Implementation and evidence](https://github.com/EriArk/JavaEmulator/blob/525ab8a4/experiments/m3g-host/throughput-20261013.md).

- **Public source / unreleased:** new game profiles now choose font sizes from
  the prepared virtual resolution instead of using 240x320 sizes everywhere.
  Advanced adds an Automatic font preset; saved explicit sizes stay unchanged.
  Constructo's small-screen tutorial is readable on Retroid. Four Android font
  tests pass on Retroid and Phone emulator; ordinary Handheld/Phone debug builds
  and 14 unit tests per variant pass. No APK distribution in this batch.
- **Separate experiment / not shipped:** fixed GPU diagnostics accessing an
  uninitialized Activity and enabled cadence capture from non-debuggable research
  APKs. Compared actual Retroid gameplay with the original engine across Asphalt,
  Constructo and Deep Java. Six short captures maintain about 60 surface buffers/s
  without >100 ms presentation gaps; this is not unique game FPS or speed parity.
  Scene differences, lower candidate host throughput and thermal-data limits
  remain explicit. Final Retroid measured suite passes 332 checks; focused tests
  pass 31 on Retroid and 31 on Phone emulator. No engine/license switch or release.
  [Implementation and evidence](https://github.com/EriArk/JavaEmulator/blob/01a8837b/experiments/m3g-host/gameplay-20261013.md).

- **Separate experiment / not shipped:** moved flat/two-sided lighting to
  indexed GPU geometry, corrected unlit flat colors and portable front/back
  selection, reduced repeated shader state updates and fixed mesh-cache churn.
  Split ordinary/extended shaders after detecting extra overhead. Retroid
  passes 325 checks; Phone emulator passes 14 focused checks, not the full suite.
  Synthetic flat/two-sided lighting cost is roughly halved; ordinary-scene
  timing and pixel-rounding limits remain explicit. Fresh Asphalt, Constructo
  and Tower Bloxx captures are recorded, with a final Asphalt recheck. No
  production engine/license switch or public APK.
  [Implementation, measurements and evidence](https://github.com/EriArk/JavaEmulator/blob/64c4f866/experiments/m3g-host/lighting-20261013.md).
- **Separate experiment / not shipped:** added compact indexed mesh buffers
  retained in GPU memory, bounded cache eviction and mutation/context-loss
  recovery. Four-texture meshes can now use hardware geometry. Retroid and Phone
  emulator each pass 311 checks; all 30 before/after fixture images match.
  Repeated dense-lighting scenes cost 14-15% less and multi-object scenes 6-8%
  less than the previous GPU checkpoint. Cold fallback costs and an unstable
  long benchmark run are documented, not presented as universal acceleration.
  Fresh Asphalt, Constructo and Tower Bloxx captures are included. No production
  engine/license switch or public APK.
  [Implementation and evidence](https://github.com/EriArk/JavaEmulator/blob/0697d76c/experiments/m3g-host/indexed-20261013.md).
- **Separate experiment / not shipped:** moved supported mesh transforms,
  clipping and lighting to the GPU, added a bounded immutable geometry cache
  and removed depth readback at target release while preserving CPU fallback
  barriers. Retroid and Phone emulator each pass 301 checks. Repeated synthetic
  dense-lighting scenes cost 60-66% less, multi-object/material scenes 23-29%
  less and sprites 14-17% less. Fresh Asphalt, Constructo and Tower Bloxx gameplay
  captures are included. Hardware geometry stays opt-in; pixel-rounding
  differences, unsupported states and the remaining native performance gap are
  explicit. No production engine/license switch or public APK.
  [Architecture, checks and screenshots](https://github.com/EriArk/JavaEmulator/blob/0dbea9db/experiments/m3g-host/hardware-20261013.md).
- **Separate experiment / not shipped:** reduced CPU overhead of GPU submission
  and fixed a mixed-material color-write regression found on Retroid. Repeated
  geometry/many-draw fixtures cost about 14-21% less; all 30 image checksums are
  unchanged. Portrait sprites remain slower, so universal speedup or parity with
  the original engine is not claimed. Retroid and Phone emulator each pass 262
  checks. Fresh Constructo Combat and Tower Bloxx gameplay captures and pinned
  audio dependency provenance are recorded. No production engine/license switch
  or new public APK.
  [Implementation and evidence](https://github.com/EriArk/JavaEmulator/blob/81c8b2d8/experiments/m3g-host/submission-20261013.md).
- **Separate experiment / not shipped:** fixed disappearing objects in large 3D
  scenes, camera ranges, spotlight behavior, transparency and CPU/GPU edge
  sampling. Hardened model-file parsing and nested resource loading. Retroid
  and Phone emulator each pass all 260 checks; 70 standalone models/resources
  from eight private games load. Fresh Asphalt, DiRT and Deep gameplay captures
  are recorded. This is not full-game or performance parity; GPU stays opt-in,
  and the production engine, license and public APKs are unchanged.
  [Implementation, native differences and evidence](https://github.com/EriArk/JavaEmulator/blob/a9f51dbe/experiments/m3g-host/scene-contracts-20261013.md).
- **Separate experiment / not shipped:** fixed 3D deformations ignoring changed
  geometry and colors, corrected overlapping bone bindings and lighting on
  scaled skeletons, and rejected overflowing bone ranges. Added loaded-model
  and animated-hierarchy coverage. Final Retroid passes 184 checks; Phone
  emulator passes 106 shared/animation/deformation checks. Fresh Deep:
  Submarine Odyssey Java gameplay screenshots are recorded. Native comparison
  differences and an interrupted crash are explicit; no complete-game parity,
  engine switch, root license change or public APK is claimed.
  [Implementation and verification](https://github.com/EriArk/JavaEmulator/blob/139aa143/experiments/m3g-host/deformation-20261013.md).
- **Separate experiment / not shipped:** corrected smooth 3D animation curves,
  looping transitions, long rotations and blending of simultaneous movements.
  Edited curves refresh correctly; reverse/fractional playback updates safely,
  circular key ranges load, and invalid sequence files are rejected. Final
  Retroid passes 163 checks; Phone passes 85 shared/animation checks. A fresh
  GPU-enabled Retroid build reaches an Asphalt race, with screenshots recorded.
  No full-game/audio/FPS guarantee, production engine switch or release APK.
  [Implementation, native comparison differences and evidence](https://github.com/EriArk/JavaEmulator/blob/beff7fc9/experiments/m3g-host/animation-20261013.md).
- **Public source, unreleased:** ordinary debug builds no longer require a
  private release-signing configuration. Both variants build, pass their JVM
  tests and complete lint without it. Release assembly still requires a real
  signing setup and fails clearly when it is missing. Closed issue #1.
- **Separate experiment / not shipped:** corrected copied 3D scenes losing
  alignment links, animation tracks being cloned instead of shared, and resources
  disappearing from traversal when one of several references is removed. Fixed
  camera validation while preserving camera-less file loading. Retroid passes
  140 checks; Phone passes 139 before the final loader guard and all 62 shared
  checks afterward. Eleven new cases also pass on the original engine.
  [Scope, results and artifact hashes](https://github.com/EriArk/JavaEmulator/blob/68866a93/experiments/m3g-host/scene-copies-20261013.md).
- **Local test preparation only:** stored user-provided N-Gage Asphalt 3 and
  Snakes archives and extracted files outside Git. Neither game was executed or
  bundled; this does not introduce N-Gage support.

- **Separate experiment / not shipped:** reuse GPU textures across material
  switches with bounded memory and revision-aware updates. Increased the number
  of cached small textures after an Asphalt smoke run exposed repeated uploads,
  without raising the byte limit. Retroid's material-switching fixtures cost
  about 10-11% less; all 30 comparison images are unchanged. Final Retroid passes
  129 checks; Phone passes 129 before capacity tuning and five affected checks
  afterward. Fresh Asphalt gameplay and cache counters are recorded, not a
  general FPS or full-game guarantee.
- **Research build preparation:** fetch source from a revision-pinned,
  hash-verified archive instead of requiring a maintainer-local checkout.
  Cached offline builds and invalid-input rejection were checked; source
  identity and license are retained in APK assets. Production engine, root
  license and public APKs are unchanged.
  [Implementation, checks and screenshot](https://github.com/EriArk/JavaEmulator/blob/a7a54bc3/experiments/m3g-host/gpu/README.md).

- **Separate experiment / not shipped:** batched GPU rendering of meshes and
  sprites, reducing repeated context switches and geometry uploads while
  preserving CPU/2D drawing order and context-loss recovery. Retroid's 16-mesh
  benchmark costs 23-26% less than immediate GPU submission; all 28 benchmark
  images are unchanged. Both final variants pass 124 checks without skips.
  Added fresh Retroid screenshots: Asphalt gameplay and Deep's animated menu,
  alongside original-engine references. These are not full-game parity or FPS
  guarantees. GPU remains off by default; main's engine/license and public APKs
  are unchanged.
  [Implementation, measurements and screenshots](https://github.com/EriArk/JavaEmulator/blob/33619e43/experiments/m3g-host/gpu/README.md).

## 2026-10-12

- **Separate experiment / not shipped:** added bounded recovery of pending GPU
  frames after private-context loss, retaining textures, transparency and depth.
  Removed repeated graphics setup and fixed allocation overhead introduced by
  replay storage. Added seven checks for controlled loss, changed draw data,
  memory/draw limits, repeated failure, reset and caller-context preservation.
  Final Handheld and Phone builds each pass 119 checks without skips on Retroid
  and the Android emulator.
  All 28 benchmark images remain unchanged; final multi-mesh cost is within 1%
  of the previous checkpoint in the recorded Retroid comparison. This is not
  a game-FPS or real OS/driver-reset guarantee. GPU stays off by default; no
  main engine/license change or release APK.
  [Implementation and evidence](https://github.com/EriArk/JavaEmulator/blob/a7b5d204/experiments/m3g-host/gpu/README.md).

- **Separate experiment / not shipped:** added GPU Sprite3D and the remaining
  alpha/luminance image formats. Sprites and 3D meshes now share the same frame;
  invisible sprites no longer trigger needless transfers. Preserved exact crop,
  flip and fractional sampling, and transparent pixels do not block later objects.
  Final Retroid/Phone emulator builds each pass 112 checks; all 28 benchmark
  scenes use GPU. Standalone sprites remain slower than CPU, so GPU stays off
  by default. A fresh Deep session reaches a tutorial dialogue over 3D scenery;
  screenshots and limits are recorded. No engine/license switch or release APK.
  [Implementation and evidence](https://github.com/EriArk/JavaEmulator/blob/285d26ce/experiments/m3g-host/gpu/README.md).

- **Separate experiment / not shipped:** moved depth bias and remaining
  framebuffer multiplication modes onto GPU. Fixed needless readbacks for empty
  models and incorrect object ordering after negative CPU depth. Final Retroid
  and Phone emulator builds each pass 104 checks; all 22 earlier benchmark
  images remain unchanged. A fresh Asphalt session confirms thousands of
  GPU-biased draws with visible gameplay. Effects are not uniformly faster than
  CPU in isolation; no full-game/audio acceptance or release is claimed.
  GPU stays off by default and main's engine/license/APKs are unchanged.
  [Implementation, screenshot and remaining limits](https://github.com/EriArk/JavaEmulator/blob/333dd8a0/experiments/m3g-host/gpu/README.md).

- **Separate experiment / not shipped:** added four texture layers, all texture
  blend modes and mipmapping to the experimental GPU renderer. Cached unchanged
  uploads, preserved mutable textures and fixed constant-coordinate mip selection.
  Final Retroid/Phone emulator builds each pass 98 checks. Texture-heavy fixtures
  improve about 1.7-2.9x versus native CPU; some simpler cases remain slower.
  GPU-enabled Asphalt 3 and Deep reach gameplay on Retroid, with real GPU calls
  confirmed and screenshots recorded. This is not full-game/audio/controller
  acceptance; unsupported effects still use CPU. GPU stays off by default and
  main's engine/license/public APKs are unchanged.
  [Implementation, screenshots and limits](https://github.com/EriArk/JavaEmulator/blob/5433c574/experiments/m3g-host/gpu/README.md).

- **Separate experiment / not shipped:** keep consecutive 3D models in one GPU
  frame and synchronize before CPU drawing or screen output. Added GPU alpha/
  additive transparency and linear/exponential fog. Retroid and Phone emulator
  each pass 92 checks. A 16-model fixture falls from 21-22 to 5.4-5.7 ms compared
  with per-model GPU transfers; all 22 residency comparison images are unchanged.
  This is not an equivalent game-FPS gain: some scenes remain slower than CPU.
  Unsupported effects retain CPU fallback; GPU stays off by default. Main's
  engine/license and published APKs are unchanged.
  [Implementation, evidence and remaining limits](https://github.com/EriArk/JavaEmulator/blob/88d48084/experiments/m3g-host/gpu/README.md).

- **Separate experiment / not shipped:** added opt-in GLES3 rendering for lit
  geometry and one texture, with perspective, alpha rejection and shared
  color/depth across GPU/CPU draws. Fixed the initial transfer bottleneck using
  bulk copies and shader packing. Both devices pass 87 checks, including six
  explicit GPU checks. Linear-filter and lit-grid samples improve versus native
  CPU, while simple nearest-filter samples remain slower. Unsupported effects
  fall back unchanged; GPU stays off by default. No production switch, licensing
  transition, new release or real-game GPU acceptance.
  [Implementation, comparison images and measured limits](https://github.com/EriArk/JavaEmulator/blob/5b777ae2/experiments/m3g-host/gpu/README.md).

- **Separate experiment / not shipped:** fixed wrong back-face lighting on
  shared vertices and transforms processing unused buffer tails. Reused lighting
  across a draw, retained sparse texture scratch and skipped unused light setup.
  Both devices pass 81 checks; standalone probe builds; Asphalt 3 reaches a
  moving Retroid race. Twenty cache-on/off scenes match and eighteen older
  images remain unchanged. A new lit grid costs 15-19% less than uncached
  lighting, not a game-FPS or old-engine parity claim. Main's engine, licensing
  and public APKs remain unchanged.
  [Implementation and measured limits](https://github.com/EriArk/JavaEmulator/blob/197f9955/experiments/m3g-host/native/README.md).

- **Separate experiment / not shipped:** replaced dithered mip-level switching
  with real level interpolation, including alpha, in Java and native rendering.
  Corrected detail selection for narrow projections and nearest-level rounding.
  Precomputed native scales reduce the cost of the higher-quality filter.
  Both devices pass 74 checks, all 18 Java/native benchmark scenes match, and
  the standalone probe builds. Filtering costs more than the old approximation;
  no legacy-GLES performance-parity claim, production switch or new release.
  [Implementation and limits](https://github.com/EriArk/JavaEmulator/blob/610302b1/experiments/m3g-host/native/README.md).

- **Separate experiment / not shipped:** accelerated mipmapped textures and
  Sprite3D rendering, retaining the shared-buffer Java fallback. Fixed lost
  fractional texture coordinates, rectangular/NPOT mip scaling and stale levels
  after image changes. Both devices pass 71 checks; all 18 benchmark scenes
  retain exact Java/native pixels. New mipmap/sprite workloads improve about
  1.3x/1.6x versus Java, not a game-FPS or legacy-GLES parity claim. Asphalt 3
  reaches a running Retroid race. Production engine and public APKs unchanged.
  [Implementation and limits](https://github.com/EriArk/JavaEmulator/blob/b624a71e/experiments/m3g-host/native/README.md).

- **Separate experiment / not shipped:** expanded native raster acceleration
  to four independent texture layers, all current texture blends and exponential
  fog. Fixed zero-density fog tinting for models and sprites. Newly accelerated
  benchmark scenes cost about 1.7-1.9x less than Java with identical pixels;
  simple-scene overhead and the legacy-GLES performance gap remain.
  Both devices pass 66 checks; Deep reaches gameplay, turns and resumes on
  Retroid. No production-engine switch or new release APK.
  [Implementation and limits](https://github.com/EriArk/JavaEmulator/blob/3605df3a/experiments/m3g-host/native/README.md).

- **Separate experiment / not shipped:** added native CPU acceleration for the
  alternative M3G renderer, keeping the Java fallback and shared color/depth
  buffers. Fixed independent repeat/clamp texture axes. Same-APK synthetic
  scenes render 1.74-2.02x faster with identical pixels; this is not game FPS
  or parity with the faster legacy GLES engine. Retroid Handheld and Phone
  emulator each pass 63 checks, and Asphalt reaches a moving Retroid race.
  Production M3G, root licensing and release APKs remain unchanged.
  [Implementation and measured limits](https://github.com/EriArk/JavaEmulator/blob/1b302bb9/experiments/m3g-host/native/README.md).

- **Separate experiment / not shipped:** fixed a crash with nonconsecutive M3G
  texture units and replaced the external angle approximation with platform
  math. Added two shared raster regressions, two candidate math checks, and
  repeatable eight-scene performance/image measurements. Final candidate passes
  58 focused checks on Retroid and Phone emulator; native passes 49 shared checks.
  Asphalt reaches a moving race. Matched non-debuggable samples still show a
  software-rendering cost gap; no performance-parity claim, quality reduction,
  production-engine switch, root-license change or release.
  [Evidence and next gate](https://github.com/EriArk/JavaEmulator/blob/codex/m3g-replacement/docs/m3g-performance-2026-10-12/README.md).

## 2026-10-11

- **Separate experiment / not shipped:** fixed the traced Asphalt shadow alpha,
  independent color/alpha write masks and sprite near/far clipping; added
  scene/picking checks and bounded drawing/copy timing. Brought current mainline
  UI/storage work into combined Handheld/Phone research builds. Replaced the
  additional inherited EPL tone compiler with original Apache-2.0 code and
  repaired direct ToneControl playback/completion/validation. Candidate passes
  61 focused checks per device, plus independent MIDI parsing; both combined
  app suites complete with explicit variant skips. Corrected 13 blocking XML
  lint findings without redesigning the UI. Production/license transition and
  public beta remain pending the documented release gates.
  [Evidence and remaining work](https://github.com/EriArk/JavaEmulator/blob/codex/m3g-replacement/docs/m3g-beta-readiness-2026-10-11/README.md).

- **Separate experiment / not shipped:** reproduced and fixed material
  emission/alpha with no lights and fog distance under parallel projection.
  Added regressions and bind-to-release timing for direct-flush games: 32 checks
  pass on both Retroid engines and candidate emulator; standalone candidate
  retains 18/18. Deep provided usable target-session measurements and resumed
  gameplay after Home/Recents. Asphalt still shows a hard black shadow. Source,
  failures and screenshots are on the experiment branch; production engine,
  licensing and release APKs are unchanged.
  [Dated evidence](https://github.com/EriArk/JavaEmulator/blob/044d9a28/docs/m3g-shading-2026-10-11/README.md).
- **Separate experiment / not shipped:** reproduced and fixed bilinear texture
  edge bleeding in the generated alternative M3G renderer. Added eleven raster
  cases to six host checks: 17/17 pass on Retroid for both engines and candidate
  emulator. Completed bounded Deep/DiRT observations, preserving rejected
  timing and unavailable Deep paint-cost data. Deep returns to its own pause
  menu in the same process; gameplay after return remains unverified. Asphalt
  shadow difference is not fixed. Published evidence/screenshots only; main
  engine, licensing and release APKs unchanged.
  [Dated evidence](https://github.com/EriArk/JavaEmulator/blob/86d79068/docs/m3g-filtering-2026-10-11/README.md).
- **Separate experiment / not shipped:** added opt-in host cadence/PSS collection
  with rejected-sample reporting and six parser checks. Both measured engines
  pass six Retroid host checks; candidate also passes on the emulator. One
  matched Asphalt pair completed about 91 seconds per engine; candidate paint
  cost is higher (window-p50 median 8.74 vs 3.66 ms) and shadows/filtering differ.
  Emulator Asphalt survived three resumes and a switch to DiRT reached gameplay;
  emulator surface timing was rejected for a polling gap. Published raw windows,
  hashes and screenshots, not APKs/games. Longer three-title performance and
  replacement acceptance remain open; main engine/licensing unchanged.
  [Dated evidence](https://github.com/EriArk/JavaEmulator/blob/08f38c78/docs/m3g-runtime-2026-10-11/README.md).
- **Separate experiment / not shipped:** connected the alternative M3G renderer
  to the real AbyssME host in isolated candidate/baseline debug builds. Six host
  checks pass on Retroid for both engines and on the emulator for the candidate;
  reproduced and fixed an Image2D/framebuffer ownership regression. Deep,
  Asphalt 3 and DiRT reached gameplay in the fixed Retroid smoke sample; DiRT
  required manual 320x240 instead of its incorrect auto-profile. Published
  eight screenshots, exact variants and build/test records, not game archives
  or APKs. Main engine/licensing unchanged; performance and broad compatibility
  remain open. [Dated evidence](https://github.com/EriArk/JavaEmulator/blob/d91321b0/docs/m3g-host-2026-10-11/README.md).
- **Separate experiment / not shipped:** expanded alternative M3G checks to
  scene loading and animation, with a separate unchanged-current-engine APK.
  Fixed premature multi-section loading in the candidate's generated sources.
  Candidate: 18/18 synthetic checks on Retroid and emulator; current engine:
  10/10 shared cases on Retroid, emulator attempts stalled/exited and remain
  unresolved. Published 17 frame comparisons and before/after reports, not
  game-compatibility claims or release APKs. Main engine/licensing unchanged.
  [Dated evidence](https://github.com/EriArk/JavaEmulator/blob/9e758abe/docs/m3g-scenes-2026-10-11/README.md).
- **Separate experiment / not shipped:** built a standalone Android probe for
  a FreeJ2ME-Plus M3G alternative on `codex/m3g-replacement`. Eight synthetic
  checks passed on Retroid Pocket Classic and an x86_64 emulator; diagnostic
  screenshots and raw reports are published on that branch. Current mainline
  M3G and licensing remain unchanged; no real-game compatibility or replacement
  is claimed. [Branch results](https://github.com/EriArk/JavaEmulator/blob/8d7aafdf/docs/M3G_REPLACEMENT.md).
- **Research / not shipped:** audited EKA2L1 as an embedded Symbian / N-Gage
  engine and documented one-app setup, import, controls and backup goals,
  source findings, licensing/build gates and staged acceptance. No native
  integration, game run or new APK in this batch.
  [Integration plan](docs/SYMBIAN_INTEGRATION.md).
- **Public source / experimental.15:** J2ME Loader whole-library import can
  request access to a blocked nested folder and resume scanning. Wrong-folder
  selection and cancellation are recoverable; save folders are checked and
  the access message remains readable. Source identity survives extra grants.
  [d39ed742](https://github.com/EriArk/JavaEmulator/commit/d39ed742),
  [workflow](docs/LIBRARY_TRANSFER.md).
- **Verification:** the combined Phone/Handheld APKs reported 132 selected
  Android cases OK per variant, subject to variant skips; 14 JVM tests per
  variant. A real J2ME Loader emulator import preserved the two RMS files by
  hash. This does not certify every provider or the reporter's devices.
- **Documentation/media:** published 12 fresh development screenshots covering
  the library, gameplay, mapping, touch layouts and settings, with an unchanged
  PNG pack and capture manifest.
  [2835c239](https://github.com/EriArk/JavaEmulator/commit/2835c239),
  [archived screenshots](https://github.com/EriArk/JavaEmulator/blob/23da5d4b/screenshots/post-2026-10-11/README.md).
- **Documentation:** added the shared current-status source for website and
  post authors, including local-only work and evidence boundaries; added this
  historical changelog and an update contract. No new app build in this batch.

## 2026-10-10

- **Public source / experimental.13:** compact installer with scrollable
  details/archive choices, reachable actions and persistent errors. ZIPs
  selected through file pickers are recognized as archives.
  [696da084](https://github.com/EriArk/JavaEmulator/commit/696da084).
- **Public source / experimental.14:** accepting mismatched JAD/JAR metadata
  retains the selected source and reinstall identity instead of failing with
  a null URI. Different aliases for the same Downloads file remain a separate
  [open issue](https://github.com/EriArk/JavaEmulator/issues/20).
  [7ba384b2](https://github.com/EriArk/JavaEmulator/commit/7ba384b2).
- **Integration status:** recorded the positive Wolphun response and requested
  precise permission scope. A later reply reaffirmed GPL 3.0; no combined
  public release was created. [Licensing record](docs/LICENSING.md).

## 2026-10-09

- **Public source / experimental.8:** unified library and in-game Display and
  Controls pages, with Try/Keep/Undo trials and automatic rollback of an
  unconfirmed preview. Expert settings remain separate.
  [5a4bc726](https://github.com/EriArk/JavaEmulator/commit/5a4bc726).
- **Public source / experimental.9:** compact console-style library and mapper
  pages for small handheld screens, more space for games, on-demand actions
  and controller navigation.
  [ba252272](https://github.com/EriArk/JavaEmulator/commit/ba252272).
- **Public source / experimental.10:** library focus fixes, conservative icon
  and cover selection, custom art and explicit legacy-art refresh.
  [17ef6dcf](https://github.com/EriArk/JavaEmulator/commit/17ef6dcf).
- **Public source / experimental.11:** compact Phone library and shared menus
  with reachable Back/Close actions while content scrolls.
  [1ae5503b](https://github.com/EriArk/JavaEmulator/commit/1ae5503b).
- **Public source / experimental.12:** Settings grouped into Library, Playing
  and Support; transfer chooser, selection and results separated for small
  screens. [83b2fc2d](https://github.com/EriArk/JavaEmulator/commit/83b2fc2d).

## 2026-10-08

- **Public source / experimental.5:** menu-based library backup/restore, screenshot actions,
  Phone orientation/contrast fixes and transfer documentation.
  [4f4d1468](https://github.com/EriArk/JavaEmulator/commit/4f4d1468).
- **Experimental.4:** an optional camera button for both engines, including
  non-pausing Mophun frame capture; app-wide visibility and PNG gallery storage.
- **Public source / experimental.6-7:** more conservative first-launch
  preparation. Explicit metadata wins; ambiguous hints, advertisements and
  unusual image dimensions do not force a guessed phone profile. Existing
  settings remain intact.
  [749ea2ed](https://github.com/EriArk/JavaEmulator/commit/749ea2ed),
  [230122df](https://github.com/EriArk/JavaEmulator/commit/230122df).
- **Verification:** a six-game archive sample recorded mixed results, including
  a recurring Ghost Recon black screen. These checks did not produce a broad
  compatibility guarantee. [Smoke matrix](docs/GAME_SMOKE_MATRIX.md).

## 2026-10-07

- **Public source:** graphical per-game controller mapper, named schemes and
  presets, button identification, D-pad/stick diagonal bindings, improved Phone
  numeric/Gamepad controls, live Java image rotation and display adjustments.
  J2ME Loader import and non-destructive migrations were included in this
  accumulated batch.
  [c49fef57](https://github.com/EriArk/JavaEmulator/commit/c49fef57).
- **Experimental, first combined snapshot:** Java and Mophun in the same
  Handheld app, `.mpn` import/library integration, shared mapper, targeted Deep
  Abyss 1.1/1.4 work, game-controlled save/resume, native dialog and scene API
  fixes, and MIDI for the verified 1.4 profile. Gameplay and selected scene
  transitions were observed; next-level completion was not confirmed.
- **Experimental.2:** supplied both Phone and Handheld APKs; live Java image
  rotation and improved touch/display experience. At this snapshot, Mophun
  still required physical controls.
- **Experimental.3:** Mophun gained the shared Phone keypad and virtual Gamepad
  in portrait and landscape, independent touch/physical input ownership and
  saved per-game touch settings.
- **Publication boundary:** Java/UI source was published while optional-engine
  implementation and game fixtures remained ignored locally. Existing licenses
  were retained; no public combined release was made.
  [a0daaf2a](https://github.com/EriArk/JavaEmulator/commit/a0daaf2a).

## Earlier Milestones

- **2026-10-06, public source/docs:** contributor setup, support templates and
  public-repository cleanup; undocumented fixtures replaced. GitHub Actions
  remains intentionally disabled. New public APKs go to Releases, not the Git tree.
- **2026-10-04, media:** published the beta.2 screenshot set and source credits.
- **2026-07-20, released:** [beta.2](docs/releases/v0.1.0-beta.2.md) added a
  separately installable Phone variant with portrait/touch support while
  retaining the controller-first Handheld variant.
- **2026-07-18, released:** [beta.1](docs/releases/v0.1.0-beta.1.md), initial
  AbyssME beta release.
- **Before beta.1, public source:** retro-modern Gallery/List/Grid library,
  separated cover/icon presentation, enlarged icons, fullscreen game chrome,
  installer styling, quick display presets and early compatibility/artwork
  heuristics. These are baseline work, not newly announced October features.

## How to Add an Entry

Use the actual work/release date, a user-visible result, its availability label,
and a commit or evidence reference. Record limitations where they change the
claim. Test counts belong to the specific build and selected suites, not the
whole project. Do not erase earlier failures, invent release dates or rewrite
Git history to make a progress narrative look cleaner.
