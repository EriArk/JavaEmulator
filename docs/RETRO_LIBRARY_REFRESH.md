# Retro-Modern UI Refresh (#40)

## 0.6 Release Checkpoint

2026-10-26: the cumulative refresh is published in `0.6.0-beta.1`. Common
settings, brightness/style choices, library transfer, Fluxer sign-in and native
system-file/archive pages now share Soft Console materials. These changes keep
the existing import, account, game identity and saved-profile logic intact.
Multiplayer is enabled with an explicit early-experimental / low-compatibility
warning; only the existing Java Bluetooth adapter is advertised.

The main library-to-play-to-settings/social journey is the release scope. This
does not claim every inherited expert editor or engine-owned installer dialog
has been replaced, nor that all #40 acceptance scenarios are complete. The
dated "unreleased" entries below describe their original checkpoints.
[Release checks and remaining limits](releases/v0.6.0-beta.1-checks.md).

## Scope and Baseline

2026-10-26, first implementation slice on top of `08e90023`.
This is development work, not a released APK or completion of the entire epic.

Baseline inventory:

| Surface | Existing behavior retained |
| --- | --- |
| Library | Gallery/List/Grid, Automatic/Wide/Portrait covers, favorites, recent history, sorting, global Play last |
| Java/Mophun card | Play, favorite, edit title/artwork, settings, information, supported shortcut, reinstall and confirmed delete |
| Symbian/N-Gage card | Play, information, title/artwork overrides, export launch file, favorite, system files and supported uninstall review |
| Library menu | File/folder import, system files, sorting, cover format, settings and existing social/account entry points |
| In-game menu | Resume, Fast-forward, Display, Controls, Screenshot, Keep/Undo draft, More, Restart and Exit; unchanged in this slice |
| Phone input | Phone/Gamepad/Legacy layouts and existing editor; unchanged in this slice |
| Social | Existing account, directory, friends and chats; no new actor, authentication or room authority |

Retroid baseline capture: `.local/ux40/retroid-before.png` (local only).
The former header hides platform choice inside Collection and exposes a permanent
game-details pane on sufficiently wide screens. The new library removes that pane
entirely; it does not merely hide it at selected widths.

## Library Slice

- Four first-class platform tabs: Java ME, Mophun, Symbian, N-Gage. There is no
  permanent All platforms tab. Existing game/phone identity and import routes stay intact.
- Collection filters remain within each platform. N-Gage generation filtering is
  offered only when the native catalog contains both Classic and 2.0 entries.
- Remember view, collection, query and selection separately per platform. Migrate
  the previous view/category preferences once, preserving legacy position anchors.
- Per-collection/query/generation/view scroll anchors use original game paths.
  Deleted or filtered-out anchors fall back to the first remaining item.
- Circular L2/R2 platform navigation uses a per-device key/axis latch with
  hysteresis; L1/R1 still selects views. Touch taps launch and holds open actions.
  A short horizontal swipe switches platforms without claiming vertical scrolling,
  long holds or multitouch gestures.
- Search, import, Social and Menu occupy the header; narrow layouts keep import
  available in the menu. All three view controls stay directly accessible.
- Context/library menus opt into an edge panel: side on controller/landscape,
  bottom on touch portrait. The later shared-panel slice below extends this to games.
- Social is a direct library entry into the existing Lobbies, Friends, Chats and
  account flows. No fabricated unread counts or unsupported online capability.

## Remaining Epic Work

- Broader game-specific multiplayer acceptance.
- Wider paired multiplayer acceptance of menus/background transitions beyond the
  paired BT Biplanes room/process check recorded below.
- Wider live-game/renderer matrix for Display previews; native size/rotation remain apply-on-resume.
- Final phone-control comfort/accessibility acceptance across orientations and larger fonts.
- Wider accessibility, animation, upgrade and physical-button acceptance matrix.

Do not mark #40 complete or describe these remaining items as implemented.

## Friends and Chats (2026-10-26, Unreleased)

- Reuse the established Soft Console materials for chat/contact lists, tabs,
  filter selector and composer. The selected conversation remains visibly marked
  even when focus moves to its messages. Light/dark colors follow the host theme.
- Use shared edge panels for contact actions, Add friend, destructive
  confirmations and uncertain-message actions. These replace stock popup menus
  and dialogs without a second contact graph, credential store or transport.
- Incoming request count comes from the existing relationship snapshot, not
  presence or fabricated unread-message counts. Social options retain Account,
  My room and Browse lobbies. A conversation also exposes Conversations and
  Earlier/Latest there, preserving its in-memory draft when returning to the list.
- Below 400dp content height, move history navigation into Social options and
  hide only known quiet successful-sync notices. Errors remain visible. At least
  440dp width combines contact filter/search into one row. Bound the composer to
  two lines, or one with the keyboard open; text remains scrollable, not truncated.
  The two-pane threshold grows with font scale; compact devices use one pane.
- React to IME inset changes as well as resize callbacks. When a large keyboard
  leaves less than 240dp for the app, temporarily hide the screen header too;
  restore it on keyboard dismissal instead of leaving a thin unreadable history.
  Reapply the latest-message anchor after the message viewport finishes resizing;
  do not force the tail when the reader was already browsing older messages.
- Preserve account/relationship fencing, explicit send/retry and same-nonce
  delivery handling. Local notice dismissal requires confirmation and never
  deletes a remote message. Pending panels close on Activity stop/account change;
  message actions also close when switching conversations.

Verification and screenshot provenance are recorded with the
[development captures](../screenshots/development/2026-10-26-friends-chats/README.md).

- Combined arm64 Debug app `0.5.0-beta.1` / 310, SHA-256
  `ea4a2b3619350e0ce715b0d11fe7cb87f58e8e1ad454aaa17676f1a23ac90f42`.
  Installed on Retroid and Android 35 emulator with `adb install -r -d -t`.
  Both installed APK hashes match the build above.
  Reuse unchanged native engine outputs; this is not a clean native rebuild.
- JVM rerun during this slice: 391 tests, zero failures/errors/skips
  (`.local/ux40/social-delivery-unit-build.txt` and Gradle XML results).
  Subsequent changes concern Android viewport handling and its instrumentation.
- Final `FluxerChatsDeviceTest`: Retroid 14/14 (33.665 s), emulator 14/14
  (72.337 s). Logs: `.local/ux40/social-retroid-scroll-final.txt` and
  `social-emulator-scroll-final.txt`. Shared ConsoleSurface/GameMenu checks
  passed earlier in this slice; their implementation did not change here.
- Final compact checks: 2/2 (9.073 s), 480x320dp with 150% text. Log:
  `.local/ux40/social-small-final.txt`. Restore emulator dimensions/density/font
  afterward and confirm 1080x2340, 440dpi, 1.0 font scale.
- The first keyboard checks exposed insufficient history height and a tail
  anchor lost during rotation before opening the keyboard. Fix both; retain
  strict send visibility, history-height and last-message assertions. Replace
  fixed focus timing with a bounded wait, not weaker layout assertions.
- Earlier emulator runs were also obstructed by System UI/Messages ANR dialogs.
  Cold-start the same AVD without wiping data and dismiss those system dialogs.
  Failed/interrupted attempts are not counted as passes. Final logs above are
  the acceptance evidence for this slice; no app ANR is inferred from system ANRs.

The deterministic Android provider fixtures never send real messages or mutate
real contacts. This checkpoint does not extend live-network, game compatibility,
manual physical-button comfort or full accessibility acceptance.

No public APK/release, Nextcloud upload, new dependency, engine or server change.
The public release page was not rechecked during this UI checkpoint.

## Game-Aware Directory (2026-10-26, Unreleased)

- Project only the room's selected Java Bluetooth identity into v2 directory
  fields: SHA-256, bounded title, `j2me/bluetooth/1`, two seats and availability.
  Reserved/disconnected seats are not advertised as free. Pending launches
  advertise zero free seats; only attached/running launches say playing.
  No room members, file paths, peer attempts or tokens enter the advertisement.
- Durable intent and correlated bot acknowledgement remain separate from actual
  directory visibility and host admission. Coalesce changes during pending
  requests, then send the latest selection/seat state. Old acknowledgements do
  not confirm a newer choice. Clearing a selection removes its game projection.
  Private metadata changes do not repeatedly withdraw; closing/recovery keep
  their existing revision/ownership rules. A lost/rejected upsert reply does not
  swallow a requested privacy/close removal. Never replay the uncertain upsert;
  missing/rejected removal replies still require explicit Retry, without a loop.
- Retain Debug-only runtime registration and exact JAR preparation. Release has
  no selected-game capability. Reject oversized rooms before starting two-player
  preparation, with a second guard after asynchronous file verification.
- Reuse the existing Soft Console surfaces and edge panels for catalog/details.
  Distinguish loading, failures, filter misses and empty results; expose Retry
  without bypassing provider backoff. Full/playing rooms remain inspectable but
  do not offer a new join. The host can return to My room. Every open action
  rechecks the current revision and expiry; advertisements never grant admission.
- Compact Social options retain My room, Chats and Account without crowding the
  title. Room screens show checking/connection progress and Cancel request before
  admission. Existing membership, Ready, temporary contacts and transport remain
  authoritative; single-player requires no account.

Verification:

- Combined Debug app/test builds pass, reusing unchanged native engine artifacts.
  391 JVM tests pass without failures/errors/skips. New tests cover public-field
  allowlisting, exact-reader compatibility, selection coalescing, stale replies,
  privacy after missing/rejected replies, launch/return, title bounds, unsupported
  adapters and load-error state.
- Final native UI/lifecycle suites pass 14 on Retroid (21.387 s) and 14 on the
  Android 35 emulator (48.366 s). Two additional 480x320dp / 150% text cases pass
  (7.562 s); restore the emulator's original size/density/font afterwards.
- Early test runs exposed harness timing: IME startup and an accessibility client
  first attached after a dialog was already open. Use bounded IME-state waits
  and attach accessibility before launching the fixture. Retain failed logs;
  they are not counted as passes. App/game state is not reset to fix the tests.
- Dedicated live accounts pass `FluxerLiveDirectoryDeviceTest` with
  `fluxerDirectoryGame="BT Biplanes"`, role host/guest and the exact reported room
  UUID. Final APK: Retroid host 207.919 s; emulator guest 86.773 s. The same owner retained
  two v1 TrainerOS meetings and one v2 AbyssME room; the guest saw only AbyssME's
  selected game. Verified free seats 1 -> 0 -> 1, privacy withdrawal, republishing,
  final close, rejection of another AbyssME room and cross-product withdrawal.
  Test advertisements were removed. No service deployment or user-account changes.
- The final live pair includes the compact headers, early oversized-room guard
  and privacy/close reply handling. Playing/return publication transitions have
  actor tests, not a new live game match. No claim about separate
  Internet connections, manual physical-button comfort or complete-game acceptance.
- Final installed development APK SHA-256:
  `f8307d08975af94e1cfbbdd24e6293ad75dcdde5c4e975faa0a3e1c5e005374f`.
  Debug label remains `0.5.0-beta.1` / 310, not a public beta replacement.
- Ignored evidence: `.local/ux40/directory-retroid-delivery.txt`,
  `directory-emulator-delivery.txt`, `directory-small.txt`, `directory-live-host-delivery.txt`
  and `directory-live-guest-delivery.txt`; JUnit XML in the combined build's test-results.
  [Seven labeled captures](../screenshots/development/2026-10-26-game-directory/README.md).

No release, Nextcloud upload, new dependency or licensing change. Reviewed the
existing TrainerOS `LobbyDirectory.h` and `services/lobby-directory/ABYSSME_V2.md`;
the v1/v2 split and 32 TrainerOS / one AbyssME quota are unchanged. The public
release page was not rechecked during this source-only checkpoint.

## Native Library Settings (2026-10-26, Unreleased)

- Symbian/N-Gage game actions expose Display and Controls through an optional
  engine-owned settings adapter. The main process only reads the host profile;
  it does not initialize EKA2L1 or interpret a `symbian:` identity as a Java path.
- `SymbianGameSettings` is shared by the adapter and runtime. Keep the existing
  phone-hash/unsigned-UID directory, Symbian controller defaults and Phone/Gamepad
  selection. Merely opening/editing a panel does not save the configuration.
- Try in game stages the existing atomic `SettingsTrial`. Runtime consumes it
  once, applies the draft and exposes Keep/Undo with the existing preview timeout.
  Keep saves; Undo, timeout or leaving without confirmation preserves the baseline.
  A malformed trial falls back to saved settings with an error instead of aborting
  game launch. No game data, original system files or save format is changed.
- One native display page is reused by library and runtime advanced settings:
  Original/Fit/Stretch, rotation, adaptive orientation, native FPS/speed and LCD
  effects. Integer scaling, Java phone profiles and Mophun GPU/CPU features are
  not offered for this renderer. Instant paused native previews remain LCD-only;
  size/rotation still apply when returning to the game.
- Shared edge-panel selectors use molded keys with light/dark palette, an arrow
  and existing focus states. Tapping a label opens its selector; controller focus
  visits only the selector. Try is pinned outside the scroll area, like Keep/Undo.
- System files, launch-file export, artwork, favorite, rename and uninstall
  remain reachable. The physical mapper itself is reused, not redesigned.

Verification and availability are recorded with the
[development captures](../screenshots/development/2026-10-26-native-settings/README.md).
The new regression tests cover defaults, rejected identities, drafts, Keep/Undo,
cross-phone/UID isolation, supported options and fixed-footer visibility. Opt-in
installed-catalog checks exercise light/dark panels and Snakes launch/Undo on
Retroid; these do not establish full-game, audio or manual-button acceptance.

No release, public APK replacement, Nextcloud upload or multiplayer capability
change accompanies this work. The current public beta was not reverified here.

## Social Entry, Player Seats and Light Materials (2026-10-26, Unreleased)

- Library actions offer **Play Together** only in Debug for an installed,
  single-entry Java game whose bounded class-file probe finds Bluetooth API
  references. Hash/descriptor/library-boundary checks run off the UI thread;
  stale callbacks cannot insert an action into a dismissed or replaced page.
  API detection is a candidate filter, not a promise that every multiplayer game
  works. Other engines and Release do not gain an online adapter.
- Open the existing room screen with the selected local game. Explicit Create
  prepares it through the existing verified-game path, without Ready or launch
  automation. A second create request cannot replace a room. Existing/joined rooms
  offer Use selected game explicitly; a mismatched JAR still fails verification.
- Browse unassigned rooms or selected-game advertisements with the exact JAR
  identity and adapter. Keep the selected game when entering a room or returning
  through My room. Directory protocol uses `j2me`, while the local runtime uses
  `java`; the Debug capability mapping is explicit. At this checkpoint the publisher
  still sent unassigned advertisements; the later game-aware directory section
  above adds selected-game publishing.
- Shared Social offers Return to active room from a read-only, non-exported
  main-process projection. Reading from `:midlet` cannot instantiate a second
  room actor. The shortcut reflects state at page opening; the destination
  always resolves current room state. No persisted membership claim is used.
- Preserve the original room Activity beneath the MIDlet. Social opens a new
  presentation of the same main-process owner instead of reordering the original
  Activity above the game: Back then closes only that presentation. A paired
  process-death test caught the earlier reordered Activity being destroyed and
  leaving no room screen to resume; do not extend background polling to mask it.
- The existing room screen now uses Soft Console surfaces and responsive player
  seats with real initials, P1/P2 labels, host/preparation/connection state and
  relevant player actions. Empty seats invoke the existing invite flow. No fake
  avatars, presence, unread counters or new chat permissions. Keep admission,
  temporary-contact consent/cleanup, explicit Ready and host-only launch.
- Keep game selection above seats and Ready/Start outside the scroll area.
  Restore focus/scroll during state refresh; wrap long names and use one column
  at larger text sizes. Room submenus use the shared edge-panel treatment.
- During a running game, replace unavailable Ready/Start with Game in progress.
  End game session requires confirmation; canceling that confirmation preserves
  the running session. Pending starts retain Cancel game launch.
- Refine light Soft Console materials: near-white shell and cards, a cool recessed
  work area, restrained colored platform keys, lighter contact edges and reduced
  gray gradients. Preserve dark/High contrast, source artwork and controller
  mappings. Fix opaque-color blending rounding down alpha at certain ratios.

Verification of this batch:

- Combined arm64 Debug build, development label `0.5.0-beta.1` / 310. SHA-256:
  `367fc86ee0d9e63425aecdba225a84c5bfebe1bb270f68b5e1d654e2f9ab226b`.
  Installed hashes match on Retroid Pocket Classic and Android 35 phone emulator.
  Reuse unchanged native Symbian artifacts; this is not a native-engine rebuild.
- 379 JVM tests pass, zero failures/errors/skips. New coverage includes bounded
  Bluetooth API probing and exact edition/adapter directory filtering.
- Final Android suites: 22 pass on Retroid (73.319 s), 22 on the emulator
  (124.360 s), zero skips. The revised visible-create case also passes separately
  on Retroid (2.350 s). Cover material states, seat sizes/fonts, existing
  actions, room admission/authority, cross-process status and shared panel return.
  Seat checks span 320/393/480/540/720dp widths and font scales 1.0/1.5.
- Fix two failures found during implementation: opaque color blends rounding
  down alpha, and equal-height seats clipping in an unbounded ScrollView.
  Measure wrapped content first, then equalize seat heights; retain regression tests.
- Make the duplicate-create device regression use the visible Create/Invite only
  path, avoiding a model-only command during an Activity orientation transition.
  Keep the overwrite assertion. Live diagnostics report a busy owner without
  replacing the enclosing prepare/launch deadline with a shorter diagnostic timeout.
- Interrupted attempts are not passes: the phone AVD later failed to attach its
  test process and displayed a System UI ANR after reboot. Restart that AVD without
  wiping data and dismiss the system dialog before the clean verification run.
- Local logs: `.local/ux40/MC94516AQF040305380-social-return-fixed.txt`,
  `.local/ux40/retroid-social-create-ui.txt` and
  `.local/ux40/emulator-social-clean.txt`. Capture tests restore appearance
  and navigation settings; encrypted test sign-in files restored byte-for-byte.
- Clean paired live run: Retroid host passes (154.885 s), phone emulator guest
  passes (161.427 s), with two BT Biplanes launches in the same private room.
  Dedicated existing test friends verify the installed edition, prepare, Ready,
  launch real MIDlet processes, visit Social -> Return to active room, cancel
  End game session, return and Resume. Force-ending the host game process returns
  both players to fresh readiness; the second launch and room cleanup also pass.
  Logs: `.local/ux40/social-live-host-clean.txt` and `social-live-guest-clean.txt`.
  This verifies room/process/menu lifecycle on the same network, not a full
  multiplayer match, remote NAT traversal or manual physical-button comfort.
- [Labeled screenshots and provenance](../screenshots/development/2026-10-26-social-console/README.md)
  include actual light/dark libraries, action panels and a clearly labeled room
  fixture. Games, firmware and credentials are not included.

No release APK or Release multiplayer enablement accompanies this batch.

## Live Picture and Phone Controls (2026-10-26, Unreleased)

- Display now offers immediate visual trials with Original / Pixel sharp / Smooth /
  FSR / xBRZ / CRT tiles where the renderer supports them. Picture size and image
  rotation are live for Java/Mophun. Keep and Undo remain outside the scrolling
  content. Preserve focus when rebuilding the selected preset page.
- A visual-only transaction keeps the current profile object, restores on Back,
  dismissal or interruption, and persists only picture fields on Keep. Pending
  controls/speed trials are not accidentally confirmed. Failed saves stay retryable;
  synchronous rendering failure restores the entry picture and autosave guard.
  Existing advanced settings keep their detached Try/Keep/Undo workflow.
- Preview panels do not dim the picture. Handheld panels narrow to 300dp; phone
  portrait previews use at most 62% of the screen height. Tile swatches are
  illustrative motifs, not rendered game thumbnails. The game behind the panel
  is the actual live host-filter preview, including while solo gameplay is paused.
- Native Symbian exposes only its LCD effects. The initial device trial caught
  a black paused frame when LCD reused the general apply path. Use an overlay-only
  refresh instead, retaining native timing and surface layout. Native size/rotation
  remain in Advanced and apply on resume; do not promise unsupported instant
  native scaling, FSR or xBRZ.
- Phone Keypad supports saved Separate (existing/default behavior) and Overlay.
  Overlay uses the full game viewport, draws no opaque reserved-area background,
  and allows 20-100% opacity. Existing Separate, Gamepad and Legacy geometry stays
  unchanged. Keep independent placement IDs per orientation and presentation;
  portrait moves the keypad together, landscape retains its two control blocks.
- Add restrained keycap highlights/contact depth to Phone/Gamepad controls while
  retaining High contrast, mappings, hit boxes, multitouch and existing feedback.
  Fix group membership being recomputed after a group's coordinates moved.

Verification:

- Combined arm64 Debug build, development label `0.5.0-beta.1` / 310, SHA-256
  `8fdc16b10bc64bf8c73e35f115c78cd442c9fc1a18a834c684cc1241e2b6c061`.
  Build app/unit/Android-test targets with the same flags listed below, reusing
  unchanged native outputs. Installed on Retroid Pocket Classic and Android 35
  emulator; this is not the signed public beta APK.
- Installed base APK hashes match on both targets.
- 374 JVM tests, zero failures/errors/skips. Final Android selection contains 95
  tests on each target: 89 pass and six optional caller-supplied-game/capture
  scenarios skip, no failures. Retroid 58.360 s; emulator 158.743 s. Include
  DisplayPreviewTest, DisplayPanelTest, TouchDeckTest, existing settings/menu/
  palette suites and the native host no-reconfiguration regression.
- Repeat all five DisplayPanelTest cases at 480x320dp (960x640 pixels, density
  320): pass in 13.786 s, including fully visible fixed decision buttons. Reset
  both emulator size and density overrides after this run. Log:
  `.local/ux40/display-small-tests.txt`.
- Retroid Deep Abyss: xBRZ re-presents the paused frame; Back restores its sharp
  picture. Snakes: LCD Classic overlays the paused splash without losing the
  native frame after the repair. Exit via the common confirmation. These are
  solo menu/render checks, not new gameplay, audio or multiplayer acceptance.
- Phone emulator 2048: portrait Separate with dark controls under a light app
  theme, Overlay selected through Controls at 60% opacity, Try/Keep and persisted
  profile value, trial timeout rollback, and CRT applied to the stopped Java frame.
  Back restores the prior picture. Seed only the fixture's portrait/dark setup;
  restore its original config byte-for-byte afterward (matching SHA-256).
- Logs: `.local/ux40/display-build.txt`, `display-retroid-final-tests.txt` and
  `display-emulator-final-tests.txt`. An earlier run used the wrong package for
  CompactSettingsTest; correct it and rerun, rather than counting that as a pass.
- [Actual development screenshots](../screenshots/development/2026-10-26-display-controls/README.md).
  The phone emulator needed a non-wiping restart after ADB/system commands stalled
  during subsequent screenshot work; its completed test run is recorded separately.
- Remaining limits: manual controller/touch comfort, larger-font accessibility,
  all GPU-driver failure paths, native instantaneous size/rotation and paired
  network menu acceptance. Existing pending trials retain their own baseline;
  visual Keep must not confirm their controls or speed.

No public release, signed APK update, firmware/game upload or enabled Release
multiplayer accompanies this work. #40 remains open.

## Contextual Actions and Shared Game Panels (2026-10-26, Unreleased)

- Library actions have a small original-art header and primary Play, Display,
  Controls, information and favorite commands. More retains edit-card and the
  existing engine-specific install, repair, export and remove guards. Java and
  Mophun open their settings in the same panel; unprepared games use their
  preparation flow. Symbian pre-game system actions remain engine-owned, without
  pretending that the Java profile editor configures a native phone.
- Java, Mophun and Symbian now use Soft Console side/bottom game panels. Resume,
  Display, Controls, Social and Exit are primary; capture, fast-forward, restart
  and engine extras are in More. Canceling restart returns to More. Returning
  between pages restores the focused action and scroll position.
- Share the existing Lobbies, Friends, Chats and account chooser between library
  and game menus. Destination activities retain the underlying panel for Back.
  This is navigation reuse, not a second account, room authority or inline chat.
- Solo pause ownership and Try/Keep/Undo drafts are retained. Java blocks menu
  input from reaching the game and distinguishes network local UI from ordinary
  backgrounding. The explicit Social transition keeps a network guest running
  until return; backgrounding from Social needs paired-session acceptance and is
  not claimed to be a complete foreground/background service policy.
- Fix first-touch activation across Gallery/List/Grid. A touch acquires focus
  before normal click dispatch, rather than consuming the first tap solely for
  selection. Long holds and canceled touches must not launch a game.

Verification:

- Combined arm64 Debug app, unchanged development label `0.5.0-beta.1` / 310.
  SHA-256 `d6e3b5b28b372705606c24ccaca83c7cbc31dc80a98c01e64461beb4080a6a78`.
  Installed base APK hashes match on Retroid Pocket Classic and Android 35
  emulator. Full native build succeeded; the final UI-only rebuild reused those
  unchanged native outputs. This is not the public signed beta binary.
- 374 JVM tests, zero failures/errors/skips. Final ten-suite Android run: Retroid
  43 passed / one optional capture skipped (58.323 s); emulator 42 passed / two
  skipped (capture and physical-controller-only focus, 137.025 s). The initial
  library rename test used the former action path; update it to More > Edit card
  and retain identity/favorite/scroll assertions. New regression coverage checks
  contextual engine guards, draft isolation, Social destinations, focus return,
  first tap, long hold and canceled touch in every library view.
- On Retroid, open Display/Controls/Social, visit Friends and return to the panel
  in Bounce Tales (Java), Deep Abyss (Mophun) and Snakes (Symbian/N-Gage). Resume
  progresses the game/splash; use the shared confirmation to leave. Java's MIDlet
  process survives the Social round trip. These are solo navigation checks, not
  new full-game, multiplayer, sound or physical-button comfort acceptance.
- Local logs: `.local/ux40/panels-retroid-final-tests.txt` and
  `.local/ux40/panels-emulator-final-tests.txt`. Retroid captures include
  `panels-retroid-actions.png`, `panels-java-game.png`, `panels-mophun-game.png`,
  `panels-mophun-more.png`, `panels-symbian-game.png` and the respective
  `panels-*-social-return.png` files in the same folder. Those game captures
  precede only the final library first-tap repair; game-panel code is unchanged.
- Final-APK emulator captures: `panels-phone-library.png`,
  `panels-phone-actions.png`, `panels-small-actions.png` and
  `panels-small-actions-scrolled.png`. Inspect portrait bottom placement and
  480x320dp side-panel scrolling; restore size/density overrides afterward.
- An emulator install initially stalled with the virtual device unresponsive.
  Restart the same AVD without wiping its data, reinstall successfully and run
  the final suite. Do not count that install attempt as verification.

No new release, public APK, Nextcloud upload, game/firmware redistribution or
Release multiplayer enablement. The remaining epic work above stays open.

## Soft Console Materials (2026-10-26, Unreleased)

The owner approved softer, rounder molded materials while retaining #40's
structure. TrainerOS dark chassis/keycap styling was inspected read-only as a
material reference; its navigation and QML were not transplanted.

- `ConsoleSurface` draws a matte chassis, recessed library well, colored platform
  keys, quiet cards and temporary panels. Thin highlights and contact edges give
  depth without a thick decorative bezel, blur, bitmap layers or animation loop.
- Existing Classic / Retro LCD / High contrast and light/dark settings remain.
  High contrast deliberately uses flat black/white surfaces with distinct edges.
  Focus, selection and pressed states do not resize the card or key. Disable
  Android's extra rectangular focus fill where custom focus already renders.
- Gallery/List/Grid and source-art fitting are unchanged. Remove the extra frame
  around artwork instead of tinting, cropping or regenerating the image.
- At width >=440dp, height <400dp and font scale <=1.2, reparent view/filter tools
  into the header. Hide the brand and Add shortcut there; import remains in Menu.
  Taller/larger-text layouts restore the normal rows. Hit targets remain 48dp.
- Only library/context panels opt into these materials. In-game menus, pause,
  renderers, touch mapping and multiplayer authority are untouched in this slice.

Verification of this follow-up:

- Combined arm64 Debug build, development identifier `0.5.0-beta.1` / 310.
  APK SHA-256 `274325711cf10a1377ef6869ec0b8af2418ee2e3eb0ae78c7ea270e8c39d39ff`.
  Installed hashes match on both targets. Same build flags and intermediate APK
  locations as below; no signed public APK.
- 372 JVM tests pass, zero failures/errors/skips. Add `ConsoleSurfaceTest` to the
  six existing UI suites: material rendering across three palettes and two
  brightness modes, stable bounds, visible focus/press/selection and repeated
  compact/tall reparenting. Test preferences are restored, including palette.
- Final Android suites: Retroid 22 passed / 1 optional capture skipped (24.623 s);
  emulator 21 passed / 2 skipped (capture and controller-only focus, 53.126 s).
- The rotation suite initially exposed a test timing race: Activity resources
  changed before the dialog's configuration traversal. Wait up to five seconds
  for the exact expected window gravity; retain the assertions and timeout.
- Local screenshots: `.local/ux40/soft-retroid-gallery.png`,
  `soft-retroid-grid.png`, `soft-retroid-menu.png`, `soft-phone-light.png` and
  `soft-small-light.png`. These are actual Debug UI captures, not concept art or
  release evidence. Small viewport is 480x320dp; reset emulator overrides afterward.
- Device automation is not manual physical-button comfort, full accessibility,
  game compatibility or final owner visual acceptance. The short-screen
  three-row limitation in the original slice below is addressed by this follow-up.

## Original Library Slice Verification (2026-10-26)

- Combined arm64 Debug APK, development identifier `0.5.0-beta.1` / 310;
  not a replacement for the signed public APK. SHA-256:
  `cb26b2e27311a75c46667566d61c53599e062e68c3979cab139755d3700108e1`.
  Installed APK hashes match on Retroid Pocket Classic and Android 35 emulator.
- Build `:app:assembleFdroidDebug` and `:app:assembleFdroidDebugAndroidTest` with
  `-PsymbianRelease=true -PsymbianSourceDir=build/symbian-source-check`
  and `-Pandroid.injected.build.abi=arm64-v8a`. Injected-ABI artifacts are under
  `app/build/symbian-release/intermediates/apk`, not stale `outputs/apk` files.
- `:app:testFdroidDebugUnitTest --rerun`: 372 passed, zero failed/skipped.
- Android suites: LibraryTabsTest, LibraryChromeTest, LibrarySessionTest,
  LibraryFocusTest, LibraryLayoutTest and GameMenuLayoutTest. Retroid:
  20 passed, one optional capture skipped (23.781 s). Emulator: 19 passed,
  optional capture and physical-controller-only focus skipped (47.859 s).
- Catch and fix cold-return scrolling to an obsolete selected row. Add regression
  coverage for preference migration, dual-generation filtering, per-tab state,
  gestures/held triggers and stale-card/controller launch suppression.
- Initial emulator attempts failed before app attachment. After reboot, a System
  UI ANR dialog intercepted accessibility and focus. Dismiss that system dialog
  and rerun the full suite successfully; do not treat those attempts as app passes.
- Retroid Gallery/Grid/List/actions captures are under `.local/ux40/`; phone
  portrait capture is `emulator-portrait.png`. These are local development
  captures, not signed-release screenshots or redistribution of game fixtures.
- `emulator-small.png`: light-theme 480x320 dp viewport. Header/tabs/view controls
  fit without overlap, but the three-row chrome still takes substantial height;
  further short-screen compaction remains part of the epic. Restore emulator
  size/density overrides after inspection.
- Physical Retroid checks are automated UI/input checks. Manual physical-button
  comfort, full accessibility/theme matrix, release upgrade/save validation,
  full-game compatibility and remaining #40 stages are not established here.

No public APK, release asset, Nextcloud upload, engine update or enabled Release
multiplayer accompanies this slice. Existing public release assets were not
reverified during this UI task.
