# AbyssME Changelog

User-facing development history, reconstructed from commits, release notes and
local/private build records through **2026-10-26**. This is not a claim that all
listed work is in a public APK. See [current status](docs/PROJECT_STATUS.md) for
availability, verification, known limits and screenshot guidance.

Labels: **Public source** means implemented on `main` but unreleased unless
stated otherwise. **Experimental** in older checkpoints means local/private
combined builds; released experimental features are explicitly identified.
**Released** means an actual GitHub release asset.

## 2026-10-26 - Native Library Settings and Console Selectors (#40, Unreleased)

- Add Display and Controls to Symbian/N-Gage game actions without opening system
  tools or initializing the native engine just to edit a draft.
- Share native phone/game profile loading between library and runtime, retaining
  controller defaults, custom profiles and phone/UID isolation. Try in game uses
  the existing one-launch draft with Keep/Undo; confirmed settings and saves stay
  separate from unconfirmed changes.
- Reuse the native display editor before and during play. Offer supported size,
  rotation, orientation, FPS, speed and LCD effects, without unsupported Java
  compatibility, Mophun CPU-budget or GPU-enhancement controls.
- Restyle shared edge-panel selectors as molded keys in light/dark themes.
  Labels open the selector by touch without adding a second controller focus
  stop. Pin Try in game / Try changes below long settings lists.
- Retain system tools, launch-file export, artwork and uninstall actions. No
  emulator-core, game-save format, public APK or release changes.
- Verification: 379 JVM tests; final Android suites pass 51 on Retroid and 49 on
  the phone emulator, plus two focused 480x320dp / 150% text checks. Snakes starts
  from its library trial and Undo restores the saved picture without config writes.
- [Verification and development screenshots](docs/RETRO_LIBRARY_REFRESH.md#native-library-settings-2026-10-26-unreleased).

## 2026-10-26 - Library-to-Room Flow and Lighter Console Materials (#40, Unreleased)

- Add Debug-only Play Together for verified installed Java entries referencing
  Bluetooth. Carry the selected game into the existing room workflow; explicit
  Create prepares it, without automatically pressing Ready or replacing a room.
- Add Return to active room to shared Social, using a read-only main-process
  projection so Java's separate process cannot create another room owner.
  Preserve the original room below the game when navigating back from Social,
  so game process death still has a room screen to return to.
- Show responsive player seats, initials, host and preparation state, with existing
  invite/remove/friend actions. Keep game selection above players and Ready/Start
  visible outside scrolling content. Preserve admission and launch authority.
- Show Game in progress during a running match instead of unavailable Ready/Start.
  Confirm End game session; canceling the confirmation keeps the session running.
- Scope selected-game browsing to matching game editions/adapters plus unassigned
  rooms. Keep Release online capabilities disabled and the existing unassigned-room
  publisher unchanged; this is not selected-game publishing.
- Brighten the light console shell/cards, soften recess edges and gray gradients,
  and retain restrained colored keys. Dark/High contrast and game artwork remain
  intact. [Implementation and checks](docs/RETRO_LIBRARY_REFRESH.md).
- Verify two paired BT Biplanes launches in one private room on Retroid and phone
  emulator, including Social return, canceled session ending and process-death
  recovery. This is menu/session lifecycle evidence, not complete-match acceptance.
- Final checks: 379 JVM passes, 22 Android UI passes on each target, with a
  separate Retroid rerun of the visible-create regression. Add nine labeled
  development screenshots; no public APK or release asset update.

## 2026-10-26 - Live Picture Presets and Phone Keypad Presentation (#40, Unreleased)

- Preview supported image presets immediately from visual tiles over the paused
  game. Keep/Undo stay visible below the scroll area; Back or interruption rolls
  back the trial. Save only picture fields, not another page's pending controls
  or performance settings. Retain legacy advanced Try/Keep/Undo controls.
- Support Java/Mophun live size/rotation and capability-gated effects. Native
  Symbian offers LCD previews; size/rotation stay apply-on-resume in Advanced.
  Fix LCD preview unnecessarily reconfiguring the paused native surface/timing.
- Add per-game Separate/Overlay presentation for Phone Keypad, with independent
  placement by mode/orientation, a full game viewport and adjustable transparency
  in Overlay. Preserve default Separate, Gamepad/Legacy and physical mapping.
- Refine molded keycap highlights/press appearance and fix moved-group membership
  during layout. Add transaction, failure, focus, fixed-footer, native-host and
  overlay geometry/multitouch regression coverage. Debug only, not a public APK.
  Pass 374 JVM and 89 Android checks on each target (six optional skips each).
  Verify paused-frame effects on Retroid and phone keypad trial/save in emulator;
  restore the fixture profile and add labeled development screenshots.
  [Verification and limits](docs/RETRO_LIBRARY_REFRESH.md).

## 2026-10-26 - Contextual Actions and Shared Game Panels (#40, Unreleased)

- Give game actions a small artwork header and primary Play, Display, Controls,
  information and favorite commands. Move card editing and relevant legacy
  install/repair/export/remove commands into More without dropping engine guards.
  Java and Mophun settings open inside the same panel; unprepared games retain
  their existing preparation route. Symbian system actions remain engine-owned.
- Extend Soft Console side/bottom panels to the Java, Mophun and Symbian game
  menus. Keep Resume, Display, Controls, Social and Exit prominent; retain capture,
  fast-forward, restart and engine settings in More. Canceling restart returns to
  More, and returning between action pages restores controller focus and scroll.
- Reuse the existing Lobbies, Friends, Chats and account destinations from the
  library and all three game menus. Keep the panel underneath Social so Back
  returns to the previous game menu. No new account or room actor is introduced.
- Preserve solo pause ownership and clear gameplay input on menu transitions.
  Java network launches distinguish a local panel/Social transition from normal
  backgrounding instead of unconditionally pausing the guest on focus loss.
- Fix first-tap game launch in Gallery/List/Grid without turning long holds or
  canceled touches into launches. Verify 374 JVM tests and 44 Android checks per
  target: 43 pass/1 skip on Retroid, 42 pass/2 skips on the phone emulator. Check
  solo menu/Social return in all three engines and compact/portrait panels;
  paired network menu acceptance remains open.
- Keep detached settings drafts and existing Try/Keep/Undo behavior. Instant
  visual previews, active-room shortcuts, compatible Play Together entry and
  phone-control redesign remain later #40 work. No public release asset changes.
  [Verification and limits](docs/RETRO_LIBRARY_REFRESH.md).

## 2026-10-26 - Soft Console Library Materials (#40, Unreleased)

- Keep the approved four-platform structure and give the library a matte molded
  shell, recessed content well and gently rounded, subtly glossy platform keys.
  Use muted platform colors, shallow contact shadows and clear focus/press states.
  Existing artwork, game identities and view preferences are unchanged.
- Restyle temporary library panels with the same materials and quieter action
  rows. Preserve Light/Dark/System and all three palettes; High contrast stays
  flat with explicit edges. Remove Android's duplicate rectangular focus highlight.
- Combine tools into one header on short landscape viewports, retaining all view
  controls and leaving Add game in Menu. Restore the full header on taller screens.
- Add material-state and compact-layout regression coverage. Fix the rotation
  test's early assertion by waiting for the dialog configuration traversal, and
  restore palette preferences after the rendering test.
- Combined Debug only, installed on Retroid and the Android phone emulator. No
  public APK/release change; game-menu restructuring, Display previews, phone
  controls and lobby polish remain separate work. [Evidence](docs/RETRO_LIBRARY_REFRESH.md).

## 2026-10-26 - Platform Library Refresh (#40, Unreleased)

- Give Java ME, Mophun, Symbian and N-Gage their own tabs. Keep all three library
  views and cover formats, remove the permanent game-details sidebar, and retain
  existing import and card actions. Show the N-Gage generation filter only when
  both Classic and 2.0 are present.
- Remember each platform's view, collection, query, position and selection, with
  one-time migration of previous preferences. Fix stale focus resetting the
  scroll position after reopening and reject launch from an outdated filter.
- Add circular L2/R2 navigation with key/axis deduplication and guarded horizontal
  swipes. Put Search, Add, Social and Menu directly in the header, with a compact
  view switcher and temporary side/bottom library menus.
- Verify the combined Debug build with 372 JVM tests and Android UI suites on
  Retroid and a phone emulator. This is the first library slice, not completion
  of #40; game menus, Display previews and Phone-control redesign come later.
  No public APK/release update. [Evidence and limits](docs/RETRO_LIBRARY_REFRESH.md).

## 2026-10-26 - Bluetooth Game Selection and Signaling Order (Unreleased, Debug)

- Test three more unmodified, locally supplied Bluetooth games on Retroid and
  an emulator. Dogfight and Constructo reach two-device L2CAP gameplay;
  Constructo returns normally to the room. Absorbed reaches paired RFCOMM
  gameplay when its server is opened before guest discovery. Full matches and
  sustained synchronization are not yet accepted.
- Fix room snapshots being moved behind pending peer negotiation messages by
  queue coalescing. Preserve authority-before-offer order and control barriers,
  with a regression test that fails on the old implementation.
- Add bounded manual gameplay duration and sanitized test diagnostics. Keep
  room/process assertions separate from actual game acceptance. Graphical
  artifacts, vendor-key detection and remote-network coverage remain open.
- No public APK, release, Nextcloud upload or enabled Release multiplayer.
  [Results, build identity and limits](docs/BLUETOOTH_GAME_SELECTION.md).

## 2026-10-25 - Bluetooth Game Return and Repeat Sessions (Unreleased, Debug)

- Fix transport overflow from many small Bluetooth writes. Coalesce adjacent
  stream data without requiring flush, preserve ordering and wait for queue
  capacity while keeping space for control messages.
- Fix normal game exit creating a replacement connection during cleanup.
  Require fresh preparation and Ready for another session in the same room.
  Correct a late callback that incorrectly told returning players to rejoin.
- Connect the unmodified BT Biplanes twice between Retroid and an emulator,
  returning normally after host exit and then guest exit. No room recreation or
  app restart between sessions. This does not establish complete gameplay,
  audio/save compatibility or correct rendering; graphical artifacts remain.
- Pass 381 JVM checks and 65 Android checks per target on the final Debug
  source/artifact. Installed hashes match. Keep Release capabilities disabled;
  no new public APK, release or upload. [Evidence](docs/BLUETOOTH_GAME_CHECK.md).

## 2026-10-25 - Room-to-Game Workflow (Unreleased, Debug)

- Connect installed Java game selection to the room. Verify actual files and
  the single MIDlet entry, reject another edition, and prepare code off the UI
  thread. Games need their own Bluetooth multiplayer mode; selection does not
  turn a single-player game into multiplayer.
- Add host Start after both players press Ready. Wait for both game processes
  to attach before starting their MIDlets, with bounded cancellation and stale
  attempt protection.
- Keep the room alive during gameplay and return with fresh readiness after
  exit/disconnect. Fix the guest-return lifecycle failure found in live testing.
- Pass 378 JVM checks, 60 Android checks on each target and live two-account
  Retroid/emulator startup/return scenarios in both host roles with a real MIDlet.
  Keep Release capabilities disabled pending actual
  multiplayer-game and different-network checks. No new public APK or upload.
  [Evidence and limits](docs/ROOM_GAME_LAUNCH.md).

## 2026-10-25 - Isolated Game-Process Network Bridge (Unreleased)

- Add a private, one-use connection from the room process into a separate game
  process. Bind it to the admitted account, attempt and verified installation;
  reject repeated tickets, changed files and mismatched paths.
- Wait for network attachment before constructing a network-launched MIDlet.
  Keep ordinary single-player startup/restart unchanged. A network restart must
  return to the room instead of silently becoming a disconnected game.
- Bound both IPC queues, interrupt blocked IO on shutdown, and revoke the exact
  connection when Android kills the game process. Keep a failed virtual adapter
  closed rather than falling back to the phone's Bluetooth radio.
- Keep slow installed-file verification outside the packet lock so it cannot
  stall the room or delay cancellation of an obsolete launch.
- Pass 361 JVM tests and 59 Android checks each on Retroid and an emulator,
  including a separate-process encrypted 256 KiB RFCOMM fixture and process-death
  cleanup. Verify ordinary Sudoku launch/restart/exit on Retroid via ADB.
- Keep production multiplayer disabled until verified game selection, coordinated
  Ready/launch/return and a real match are connected and checked. Debug installs
  only; no public release/upload. [Evidence and limits](docs/MIDLET_NETWORK_BRIDGE.md).

## 2026-10-25 - Bluetooth Peer Bridge and Room Preparation (Unreleased)

- Connect virtual Java Bluetooth to the encrypted peer transport: device/service
  discovery, custom attributes, independent RFCOMM streams and L2CAP packets.
  Add bounded receive credits so a slow game reader does not cause unbounded buffering.
- Close blocked operations on disconnect, reject stale stream identities and
  invalid metadata, and distinguish normal stream EOF from a failed peer session.
- Bind network preparation to the host's current admitted room/game/attempt and
  explicitly verified local content. Only a successful peer handshake can mark
  Prepared; Ready stays manual. Fence old callbacks and revoke stale readiness.
- Verify actual Java Bluetooth APIs between Retroid and an emulator through real
  Fluxer signaling, in both host roles: 256 KiB stream echo, custom service data,
  L2CAP negotiation, truncation and empty packets. Fix orderly-close/flush behavior
  and initial service-attribute preservation during this implementation.
- Pass 353 JVM tests and 50 focused Android checks on each target. Keep production
  capabilities disabled: the separate `:midlet` process still needs an IPC bridge,
  verified game selection, coordinated launch and return handling before real matches.
  Debug installs only; no public release or upload.
  [Exact evidence, artifacts and limits](docs/BLUETOOTH_PEER_BRIDGE.md).

## 2026-10-25 - Android Secure Peer Transport (Unreleased)

- Add an Android-native encrypted peer channel using pinned libdatachannel and
  Mbed TLS, without Qt or sending game data through chat. Bind a connection to
  the admitted players, room generation, exact game and fresh attempt.
- Fit connection signaling into the normal private-message limit; handle reordered
  fragments and reject wrong senders, stale bindings, conflicting repeats and
  certificate changes. Bound native queues and close explicitly on overload.
- Fix connection rejection when device clocks differ: use a local monotonic
  attempt lifetime and fresh room-bound nonce, with regression tests.
- Connect Retroid and an Android emulator through real dedicated Fluxer accounts
  for signaling; exchange 100 ordered binary test packets in each direction.
  Keep ordinary chats readable by masking the signaling payloads.
- Include dependency notices in the APK and add a corresponding-source archive
  to release preparation. Enable crypto-library locking for native worker threads.
- Pass 327 JVM tests and 33 Android checks on each target, plus the live two-account
  exchange. Keep emulator/native-device and actual-game acceptance distinct.
- Leave production game capabilities disabled. This is a tested transport core,
  not a playable Java match: Bluetooth service/stream multiplexing and room/game
  launch integration remain. Debug installs only; no public APK or cloud upload.
  [Detailed checks, artifacts and limits](docs/SECURE_PEER_TRANSPORT.md).

## 2026-10-24 - Virtual Java Bluetooth Adapter (Unreleased)

- Add a transport-independent JSR-82 backend for device/service discovery,
  custom service metadata, RFCOMM streams and L2CAP packets through the APIs
  Java games already call. Leave the physical Bluetooth backend selected normally.
- Fence cancelled searches and old sessions, bound/copy service attributes, check
  negotiated MTUs and preserve packet boundaries. Honor existing stream lifetime
  after connection close while allowing session shutdown to interrupt blocked IO.
- Pass 312 JVM tests and 23 API/core checks on each of Retroid and an Android
  emulator. Install only the separate Debug package. No new public APK or upload.
- Keep production multiplayer capabilities disabled: encrypted P2P, room/game
  launch integration and a real two-device game are still unimplemented.
  [Evidence, build hash and limitations](docs/VIRTUAL_BLUETOOTH.md).

## 2026-10-24 - Public Rooms and Shared Directory Rollout (Unreleased App)

- Deploy the existing shared directory with independent limits: 32 TrainerOS
  listings and one AbyssME public room per account. Preserve original data and
  keep a matching source/database backup; no new bot or community.
- Verify public discovery and admission on Retroid plus an emulator, then guest
  departure, invite-only withdrawal, republishing and close. Concurrent TrainerOS
  entries remain separate; quota/product conflicts are rejected.
- Repair stale-room joins with a correlated unavailable response, without leaking
  another room or canceling a current membership. Fix a merged service-source
  regression that let legacy meeting fields alias an AbyssME room.
- Pass 294 JVM, four Retroid core and 26 service checks; both final live pair roles
  pass. Save labelled development captures. Public APK/Nextcloud unchanged.
  [Evidence, failed attempts and limits](docs/FLUXER_PUBLIC_DIRECTORY_CHECKPOINT.md).

## 2026-10-24 - Non-Friend Rooms and Directory Coexistence (Unreleased)

- Preserve TrainerOS's deployed meeting features and 32-listing allowance in the
  separate shared-service PR. Keep one independent AbyssME public room per account
  and prevent cross-product overwrite. Pass 22 service tests; no server deployment.
- Repair slow inbox polling starving queued invitations/close messages. Keep
  provider errors bounded and show useful verification/privacy/rate-limit notices.
- Stop replayed host admission or terminal messages from leaving an already-used
  invitation visible after reopening the app; they never restore old membership.
  Cover authenticated sender, incarnation and replay boundaries with regressions.
- Verify automatic temporary friendship, cleanup and Keep using real accounts on
  Retroid and an Android emulator (four successful role runs). Combined Debug
  builds and 292 JVM tests pass. Public
  release files are unchanged. [Evidence and limits](docs/FLUXER_NONFRIEND_CHECKPOINT.md).

## 2026-10-24 - Live Two-Account Chat and Private Rooms (Unreleased)

- Verify normal friend request/acceptance and two-way web/Retroid messages with
  authorized test accounts. Complete browser-approved sign-in on a second Android
  instance, without bypassing its pending email-verification restrictions.
- Repair discarded room invitations caused by independent device clocks. Share a
  bounded future-deadline tolerance across room, directory, publication and contact
  messages; retain strict expiry, replay, authenticated sender and offer ownership.
- Give Friends its own contact status instead of the empty chat-list message.
- Pass 287 JVM tests, 12 Retroid chat/friend UI checks, one assisted live sign-in
  and four live private-room role runs. Retroid and the emulator exchange host/guest
  roles, recreate screens, maintain heartbeats, leave and close. Save labelled
  live-account screenshots. [Evidence and limits](docs/FLUXER_LIVE_PAIR.md).
- Public APK and shared service unchanged. Non-friend contact cleanup, public
  publication and game adapters still need their own live acceptance.

## 2026-10-24 - Live Fluxer Sign-in and Community Access (Unreleased)

- Complete browser-approved sign-in with an email-verified test account on
  Retroid. Verify session restore in a new process and after activity recreation.
- Verify automatic membership in the shared lobby community and authenticated
  directory loading; independently observe membership in the Fluxer web client.
  No compatible rooms were available during this check.
- Add two explicitly opted-in Android live checks; both passed. No production
  runtime changes, public APK update or server deployment in this checkpoint.
  Chat exchange, friends, room publication and playable multiplayer still need
  their own acceptance. [Evidence and limits](docs/FLUXER_LIVE_ACCOUNT.md).

## 2026-10-24 - Temporary Room Contacts and Keep (Unreleased)

- Connect admitted room members to the account-private temporary-contact journal
  and authenticated private-message transport. Invitations and directory browsing
  never create friendships; existing relationships remain outside automatic ownership.
- Add Keep as friend, connection/cleanup status and Open Friends to My room.
  Resume unfinished cleanup from the journal without restoring stale room readiness.
- Coordinate manual friend mutations with automatic ownership through a shared
  lease. Keep persistence/host acknowledgement precedes manual changes; late
  callbacks release the lease without mutating another account. Prevent premature
  Keep acknowledgement while a provider creation is still pending.
- Prioritize current participants and directory replies, rotate historical contacts,
  and retain eligibility of active DMs beyond the recent-conversation cache.
- Pass 279 JVM tests, 34 Retroid Android checks, combined Debug app/test builds
  and lint. Save two native fixture-labelled captures. [Evidence](docs/LOBBY_CONTACTS.md).
  Ordinary registration with a new disposable mailbox was rejected by Fluxer;
  live authenticated two-account acceptance remains open. Public APK/server unchanged.

## 2026-10-24 - Rooms, Invitations and Confirmed Publication (Unreleased)

- Add My room: create invite-only/open/approval rooms, invite a friend, accept or
  decline invitations, approve requests, remove players and leave/close with confirmation.
  Bind the native screen to authenticated private-message delivery and host authority.
- Add shareable room locators and directory-to-room navigation with explicit
  confirmation. A link is not a secret invitation and cannot bypass host policy.
- Preserve room ownership across rotation and navigation in the app process;
  fence old account callbacks, expire stale joins and invalidate readiness when
  membership or connections change. Ready remains disabled without a real adapter.
- Persist publication intent/revision before writes; add private correlated bot
  results in TrainerOS PR #260 and explain the cross-product one-room conflict.
  Do not treat a sent message as successful publication or lost replies as success.
- Use the existing styled game-menu dialogs for room actions, compact the screen,
  and distinguish disabled Ready. Repair stale admission/reconnection status,
  coalesce obsolete queued snapshots and bound the full-room wire payload.
- Pass 265 JVM tests, 32 Retroid Android checks, combined Debug builds/lint and
  19 shared-service tests; save five labelled screenshots. [Evidence and limits](docs/LOBBY_ROOMS.md).
  The shared bot is not deployed, live authenticated acceptance remains open,
  and no public APK or playable-online-game claim is added.

## 2026-10-24 - Compatible Lobbies and Community Access (Unreleased)

- Add Lobbies to the Library menu, with search, free-seat filtering, refresh,
  expiry and direct access to Chats/Account. Hide unrelated TrainerOS and unknown
  runtime advertisements rather than offering a false Join action.
- Automatically attempt to join the verified shared community when first needed.
  Persist the attempt per account, reconcile uncertain replies without replaying
  the join, and offer explicit retry after an error. Ordinary offline play is unchanged.
- Prepare a versioned extension in [TrainerOS PR #260](https://github.com/EriArk/TrainerOS/pull/260),
  keeping one public room per account across both products and preserving old readers.
  The shared service is not deployed; the original dirty TrainerOS checkout is untouched.
- Pass 222 JVM tests, 28 Retroid Android checks, combined Debug app/test builds and
  lint; the shared service passes 15 Python tests. Save five fixture-labelled device
  screenshots. [Evidence, device checks and limits](docs/LOBBY_DIRECTORY.md).
  No public APK update, actual room joining or playable online multiplayer.

## 2026-10-24 - Fluxer Friends and Requests (Unreleased)

- Add Friends beside Chats and to the Library menu. Search friends, incoming/
  outgoing requests and blocked accounts; add someone by full Fluxer tag.
- Accept, decline or cancel requests; remove friends and block/unblock with
  confirmation. Recheck the relationship before acting, reject stale actions
  and reconcile uncertain replies without automatically repeating a write.
- Open a friend's direct conversation while keeping its draft; Back returns to
  Friends. Opening a conversation does not send a message. Account changes
  dismiss pending actions and clear private state.
- Pass 187 JVM tests and 21 Retroid Android checks, plus combined Debug app/test
  builds and lint. Fix numeric tag suffix normalization and reject mismatched
  relationship/user identities. Check portrait, landscape, light/dark themes,
  request acceptance, confirmation, tag validation and lost-reply reconciliation.
- [Evidence and limits](docs/FLUXER_FRIENDS.md),
  [six fixture-labelled screenshots](screenshots/development/2026-10-24-fluxer-friends/README.md).
  Tests use simulated contacts; real two-account friend/chat acceptance remains
  open. Only a separate local Debug APK was installed. No public APK, server,
  bot or Nextcloud update. Compatible rooms and online gameplay remain later stages.

## 2026-10-24 - Private Fluxer Chats (Unreleased)

- Add Chats to the Library menu and Open chats to a securely signed-in account.
  Browse/search existing direct and group conversations, read earlier messages,
  keep separate drafts in memory and send text from a native Android screen.
- Use two panes on wide landscape screens and a list/conversation flow on narrow
  screens. Keep the composer and latest messages visible with the keyboard, and
  preserve drafts across rotation. Light/dark themes and controller focus follow
  the existing UI; account changes clear private conversation state.
- Handle blocked accounts, expired sessions, inaccessible conversations and rate
  limits. Unknown delivery is not silently resent; an explicit retry reuses its
  nonce within a bounded window. Late responses cannot restore the wrong account
  or erase a successful send. Recheck Account when returning from chats.
- Pass 161 JVM tests and 16 Retroid Android checks, including fixture chat UI,
  keyboard, Unicode draft rotation, actual Retry popup and the live anonymous
  account handoff. Real authenticated chat/restore/logout still need acceptance.
  [Evidence and limits](docs/FLUXER_CHATS.md),
  [fixture-labelled screenshots](screenshots/development/2026-10-24-fluxer-chats/README.md).
- Install only a separate local Debug APK. No public APK, bot or server update.
  Friends, compatible live lobbies and playable online games remain later stages.

## 2026-10-24 - Fluxer Account Screen (Unreleased)

- Add Fluxer account to the Library menu and Settings. Sign-in uses an approval
  code on the official Fluxer website, with no password form inside AbyssME.
- Add verified-session restoration, encrypted Android Keystore storage excluded
  from backups, sign-out and explicit local removal when the service is offline.
  Expiry, rate limits, cancelled/late responses and failed saving are handled
  without silently claiming success. Rotation preserves an active sign-in.
- Keep ordinary offline play unchanged. Chats, friends and compatible public
  lobbies remain subsequent stages, not completed features.
- Pass 133 JVM checks and ten Retroid Android checks, including live anonymous
  handoff creation/pending polling/cancellation and light/dark account layouts.
  Account approval and authenticated requests still need live acceptance.
  [Evidence and limits](docs/FLUXER_ACCOUNT.md),
  [development screenshots](screenshots/development/2026-10-24-fluxer/README.md).
- Install only a separate local Debug APK. No new release asset or server change.

## 2026-10-24 - Lobby Lite Foundation (Unreleased)

- Adapt TrainerOS's temporary lobby-contact lifecycle to Android Java, including
  account-local atomic journaling, Keep as friend, bilateral cleanup, recovery,
  pre-existing relationship protection and late-reply/rate-limit handling.
- Add the host room/preparation/Ready model with exact game/adapter identity,
  generation checks and one launch attempt; keep lobby and game lifetimes separate.
- Add bounded L2CAP packet framing over a supplied ordered stream. Preserve
  partial/coalesced packet boundaries, negotiated MTUs and close behavior without
  modifying the existing physical Bluetooth backend.
- Pass 111 JVM tests and four Retroid Android checks. Fix Android's trusted
  system-directory alias handling found during the device pass while retaining
  account/journal symlink rejection.
- This is an internal foundation, not playable online multiplayer: authentication,
  Lobby UI, P2P transport and virtual game-network integration remain unwired.
  No public APK or server update. [Details and evidence](docs/MULTIPLAYER.md).

## 2026-10-24 - 0.5.0 Beta 1 (Released)

- Publish 0.5.0-beta.1, code 310, as a new release approved by the maintainer,
  preserving older APKs and immutable source tags. Published 2026-10-23 at
  21:32:23 UTC (2026-10-24 in the maintainer's timezone).
- Enable the verified Java/Mophun image presets and experimental enhancements
  in Release: Pixel sharp, Smooth, FSR, xBRZ, CRT EasyMode, optional frame
  interpolation and Mophun CPU budget. Defaults remain unchanged. Imported
  profiles with explicitly enabled experimental settings now apply in Release.
- Include Integer picture size, aligned Mophun LCD geometry, processed Mophun
  capture, Java cadence/input fixes and interrupted-import recovery developed
  since r1. Preserve existing games, saves and per-game settings on upgrade.
- Symbian/N-Gage remains included, but does not gain these new GPU effects.
  No claim of universal 60 FPS, full-game acceptance or proportional speedup.
  [Release notes](docs/releases/v0.5.0-beta.1.md).
- Verify the signed upgrade on Retroid, with 327 readable external files
  unchanged, actual shader trials and native Snakes gameplay. Publish five
  matching APK/source/checksum assets; anonymous APK/checksum downloads match.
  Add [signed-app screenshots](screenshots/releases/v0.5.0-beta.1/README.md) and
  [verification boundaries](docs/releases/v0.5.0-beta.1-checks.md).

The development entries below retain their original checkpoint labels. Their
Debug-only/unreleased descriptions are superseded by this release where listed
above; local audition tools and private game captures are not runtime features.

## 2026-10-23 - Faster xBRZ Without Lowering Resolution (Unreleased)

- Analyze xBRZ edges once per guest pixel, then resolve the original ordered
  blends at output resolution. Cache the decision texture through output-only
  resizes and invalidate it for new guest frames, size changes and mode resets.
- Keep the original single-pass path for small scales. Do not add interpolation,
  FSR, reduced-resolution processing or different edge thresholds.
- Verify the selected captured frame against the original shader with exact
  pixel agreement on Retroid. Paired processing cost falls from about 44.2 to
  6.9 ms in that fixture; this is not a whole-game FPS measurement.
- Update adaptation provenance, regression fixtures and evidence. Public APKs,
  defaults and Symbian are unchanged; this remains opt-in Debug functionality.
  [Evidence and limits](docs/checks/2026-10-23-xbrz-two-pass.md).

## 2026-10-23 - xBRZ and CRT EasyMode Presets (Unreleased)

- Integrate the two owner-selected shaders as standalone per-game Java/Mophun
  image presets in the experimental Debug menu, with live Try / Keep / Undo.
- Disable FSR, smoothing, interpolation and LCD decoration when selecting either
  preset; preserve game geometry, rotation, speed, CPU budget and controls.
- Reuse the bounded shared GPU pipeline, crop texture padding before filtering,
  cache repeated frames and retain ordinary rendering as a failure fallback.
- Include original upstream shader notices and pinned provenance. Verify output,
  menu state, serialization and surface recovery on Retroid; do not change the
  public beta, Symbian rendering or existing profile defaults.
  [Evidence and limits](docs/checks/2026-10-23-retro-shader-integration.md).

## 2026-10-23 - Retro Shader Selection (Local Audition)

- Compare ten standalone retro filters and an unfiltered control using the same
  captured game frame on Retroid. Include SuperEagle, HQ2x/3x/4x, xBR no-blend,
  xBRZ, ScaleFX, Advanced Cartoon, CRT EasyMode and CRT Geom.
- Export individually numbered images without FSR, interpolation or an extra
  LCD overlay. Preserve opaque capture for RGB-only shaders and adapt a CRT
  macro/local-name collision without changing its formulas.
- Add a reproducible, opt-in test harness and staging helper; shaders and game
  captures remain local, outside APK assets. All 11 Retroid cases pass; the
  emulator attempt ends before testing in a startup ANR. No production menu,
  saved profile or public release changes. Owner selection is pending.
  [Evidence and limits](docs/checks/2026-10-23-retro-shader-audition.md).

## 2026-10-23 - Same-Frame Visual Review (Development Only)

- Export labelled Pixel sharp, Smooth and FSR 0/50/100% comparisons from one
  captured game frame through the actual Retroid GPU, with matching zoomed crops.
  Keep LCD and interpolation off for the spatial comparison.
- Capture a separate three-frame synthetic interpolation comparison. These are
  composed diagnostics, not five live screenshots or proof of perceived smoothness.
- Pass 10 targeted Retroid checks. Runtime code and public APKs are unchanged;
  visual acceptance is pending. [Evidence](docs/checks/2026-10-23-visual-review.md).

## 2026-10-23 - Small-Object Interpolation and GPU Surface Recovery (Unreleased)

- Recover intermediate positions for small sprites on verified uniform
  backgrounds, including motion across neighboring flow tiles. Preserve static
  HUD details and prefer real pixels for uncertain matches or occlusion.
- Reject conflicting neighbor motion and never override a locally rejected
  match. Keep guest speed, the 50 ms interpolation window and image presets intact.
- Invalidate Mophun GPU presentation/capture when its surface is destroyed;
  fence old acknowledgements across pause and recreation. This repairs a stale
  capture request reproduced during the extended device checks.
- Verify 102 selected Retroid checks, 49 emulator GPU checks and 75 JVM tests; keep effects Debug-only
  and off by default. No public APK update or Symbian rendering change.
  [Evidence, cost and limits](docs/checks/2026-10-23-sparse-motion.md).

## 2026-10-23 - More Reliable Intermediate Frames (Unreleased)

- Fix inconsistent pixel selection during odd-pixel motion in the shared Java
  and Mophun GPU interpolator. Snap the displacement once for both frames.
- Reject ambiguous opposite-direction matches in repeating patterns, retaining
  current real pixels instead of trusting search order or distance bias.
- Refresh the live interpolation phase after motion-pass preparation so CPU-side
  stalls do not replay an already-expired transition. Keep guest timing and the
  existing maximum 50 ms interpolation window unchanged.
- Reproduce both shader defects before repair on Retroid; verify 94 selected
  device checks, 75 JVM tests and the Deep Abyss first-level introduction.
  Effects remain Debug-only/off by default; no public APK update.
  [Evidence and limits](docs/checks/2026-10-23-motion-rounding.md).

## 2026-10-23 - Integer Scaling and LCD Alignment (Unreleased)

- Add Integer picture size for Java and Mophun: use the largest whole-pixel
  magnification that fits, with Fit downscaling when the original is too large.
  Keep source resolution, speed and image filters independent.
- Align Mophun Canvas/GPU output and the LCD mask to the same pixel boundaries,
  including odd viewport/image sizes and all four rotations. Preserve the
  existing Original/Fit/Stretch modes and LCD appearance.
- Expose the mode through the in-game Try / Keep / Undo workflow and Java's
  advanced scale selector; disable the inapplicable percentage input there.
- Reuse Mophun drawing bounds instead of allocating a rectangle every frame.
- Verify 75 JVM tests and 44 selected Retroid checks, including pixel comparisons
  and real-game touch-layout captures. No public APK or Symbian scale change.
  [Evidence and limits](docs/checks/2026-10-23-integer-scaling.md).

## 2026-10-23 - Image Presets and Sharpness Comparison (Unreleased)

- Add Pixel sharp, Smooth and Enhanced (FSR) presets to the Debug Enhancements
  page; move manual filtering/sharpness into Fine tuning. Preserve Custom values
  and the existing Try / Keep / Undo and profile saving workflow.
- Keep interpolation, CPU budget, emulation speed/FPS, LCD effects and geometry
  independent from spatial presets. Do not replace custom shaders or force a
  different Java renderer. Leave existing profiles and release defaults intact.
- Compare identical source frames with the shared GPU pipeline. Pixel sharp
  preserves exact source colors; FSR and linear filtering soften high-frequency
  detail. Static interpolation did not add blur in the regression fixture.
  No title-specific patch or unverified shader change was made.
- Unreleased source/local Debug builds only; no public APK update.
  [Comparison, checks and limits](docs/checks/2026-10-23-image-presets.md).

## 2026-10-23 - Mophun Enhanced Image Capture (Unreleased)

- Save Mophun screenshots and automatic missing covers from the processed GPU
  image, including FSR/sharpening and intermediate frames. Add a per-game
  Enhanced / Original capture choice using Try / Keep / Undo; retain source
  resolution in Original mode. Exclude controls and preserve image rotation.
- Preserve interpolation history during capture, bound readback resources and
  fall back to an at-shutter source snapshot if GPU capture is unavailable.
  Keep PNG encoding/storage off the UI thread and capture missing art before
  releasing GPU resources on Activity pause.
- Fix automatic artwork taking priority over a user-selected icon. Use the
  library's shared artwork precedence; do not overwrite existing custom images.
- Unreleased source/local Debug checks, not a public APK update. GPU effects
  remain Debug-only and off by default. [Evidence and limits](docs/checks/2026-10-23-mophun-capture.md).

## 2026-10-23 - Shared GPU Enhancements for Mophun (Unreleased)

- Add optional Mophun GLES presentation using the same FSR 1.0, sharpening,
  motion interpolation and load protection as Java. Keep Canvas output when
  effects are off or GPU rendering fails; do not change guest timing or saves.
- Preserve touch keypad/gamepad overlays, LCD effects, rotation and game-area
  clipping. Expose image effects beside CPU budget in the Debug per-game
  Enhancements page, using the existing Try / Keep / Undo workflow.
- Stamp completed Mophun frames atomically and avoid copying/uploading duplicate
  host redraws. Keep shader work outside the guest snapshot lock, bound retained
  frames and release resources on Activity pause or disabling effects.
- Debug-only, opt-in and off by default. No public APK, tag or release update.
  At this checkpoint, in-app screenshots still exported source pixels; the
  subsequent capture change above removes that limitation.
  [Checks and remaining limits](docs/checks/2026-10-23-mophun-gpu.md).

## 2026-10-23 - Yield Interpolation Under Rendering Pressure (Unreleased)

- Temporarily stop optional motion search/intermediate redraws when CPU
  submission repeatedly exceeds the refresh budget or GPU work remains pending
  past that budget. Finish the current pair with real pixels instead of freezing
  an intermediate image. Keep incoming guest frames and FSR presentation.
- Add a bounded EGL 1.5 completion probe with zero-timeout polling and CPU-only
  fallback. No GPU timer, requested GPU wait, frame readback or extra guest-buffer
  lock. Recover after 500 ms and healthy submissions rather than oscillating.
- Keep saved settings, guest clocks/input and FPS limits intact; reset transient
  protection on pause, context recreation or disabling interpolation. Add policy,
  actual-pixel, optional-fence and Canvas integration checks.
- Debug-only and off by default, with no public APK/release change.
  [Evidence and limitations](docs/checks/2026-10-23-interpolation-load.md).

## 2026-10-23 - Pace Intermediate Frames From Guest Submissions (Unreleased)

- Timestamp completed pixels in Canvas repaint, GameCanvas flush and extended
  image submission. Carry that timestamp with the same image/serial snapshot;
  a delayed GPU upload no longer starts another full presentation delay.
- Spread interpolation over the actual source interval at stable 20/25/30 FPS.
  Raise the maximum window from 33.3 to 50 ms, trading up to 16.7 ms more
  presentation delay at 20 FPS for steadier motion. Skip an overdue interval's
  prefix rather than replaying its entire motion faster.
- Keep presentation time monotonic; cover variable cadence, late draws, clock
  discontinuities, multiple host refresh rates and actual GPU pixel positions.
  Preserve input processing and the guest-buffer unlock boundary.
- Effects remain Debug-only, opt-in and off by default. No guest clock, ordinary
  rendering, public APK or release change. [Evidence and limits](docs/checks/2026-10-23-interpolation-cadence.md).

## 2026-10-23 - Guard Interpolation Against False Motion (Unreleased)

- Preserve newly appeared/disappeared pixels when a neighboring block's motion
  cannot explain both frame endpoints. Include diagonal detail in matching and
  reject templates that rely on repeated border pixels.
- Add a coarse GPU-only scene-cut guard so an accidental local match does not
  leave a ghost across a broad image transition. Keep normal horizontal,
  vertical and diagonal scrolling, and recover on the following frame.
- Reproduce four defects on the previous Retroid Debug build before applying
  shared shader fixes. Add seven regression tests, including scrolling and
  reset/recovery controls. [Evidence and limits](docs/checks/2026-10-23-motion-guards.md).
- No guest timing/input, ordinary rendering or effect-default change. Effects
  remain Debug-only and opt-in; no public APK or release update.

## 2026-10-23 - Reduce EASU Work on Flat Regions (Unreleased)

- Reuse EASU sample coordinates and avoid eight outer texture fetches plus
  filter arithmetic when the four nearest RGB colors exactly agree. Preserve
  guest resolution, full filtering at edges and the existing output clamp.
- Keep branchless evaluation on software renderers after detecting a large
  SwiftShader control-flow regression. Shader selection needs no user setting
  and does not depend on game names.
- Add a frozen pre-optimization shader in test assets, full-image comparisons
  across synthetic scenes/phone resolutions and paired content-dependent cost
  measurements. [Evidence and limits](docs/checks/2026-10-23-easu-flat-regions.md).
- Effects remain Debug-only, opt-in and off by default; no public APK update.

## 2026-10-23 - Fused Sharpening and Presentation (Unreleased)

- Apply experimental FSR sharpening directly during final Canvas presentation,
  instead of writing and then copying a separate output-sized texture. Keep the
  same RCAS equations and restore the normal shader state for surface callbacks.
- Save one output-sized RGBA target (about 2.64 MiB at 720x960) on the Canvas
  path. This does not change guest resolution, image defaults or emulation speed.
- Retain the texture-output path for same-GPU quality and cost comparisons;
  cover rotations, padding, fractional scale, sharpness, interpolation, cached
  draws and disabling/resetting effects. See
  [verification and measured costs](docs/checks/2026-10-23-enhancement-presentation.md).
- Keep effects Debug-only and opt-in. No public APK or release asset update.

## 2026-10-23 - Separate Guest Drawing From Enhancement Passes (Unreleased)

- Release the Java guest-buffer lock immediately after texture upload and
  geometry capture. Motion estimation, FSR, sharpening and final presentation
  now run without blocking new guest-buffer writes for their entire duration.
- Keep a GL-owned vertex snapshot and the uploaded frame's identity. Concurrent
  resize/rotation does not mutate the vertices being presented, and the FPS
  counter cannot count a newer submission before it is drawn.
- Preserve bounded textures, idle caching and reset/fallback behavior without
  an extra CPU frame copy. Expired interpolation skips unnecessary motion search.
- Reproduce the old locking problem on Retroid, then verify concurrent guest
  flush/input, uploaded-pixel ownership and colored corners through rotation.
  See [evidence and remaining limits](docs/checks/2026-10-23-enhancement-upload.md).
  Enhancements remain Debug-only and off by default; no public APK change.

## 2026-10-23 - Interpolation and GPU Refinement (Unreleased)

- Cap the experimental interpolation window at 33.3 ms instead of 100 ms.
  Slow games hold the completed image afterward; this is not universal 60 FPS.
- Protect unchanged HUD/text pixels and viewport edges from motion warping;
  reject inconsistent color channels instead of blending a ghost image.
- Skip motion search for already-fast streams and static blocks; reuse template
  samples and cached shader uniform locations. Skip completed-frame resolve.
- Fix ambiguous thin-edge motion matching using adjacent pixel samples and
  pixel-center sampling; verify horizontal and vertical motion in both directions.
- Fix FSR changing pixels at native size with zero sharpness. Run EASU only for
  real upscaling and skip RCAS at zero strength, including padded Canvas input.
- Keep the features Debug-only, opt-in and unreleased. See
  [checks and limits](docs/checks/2026-10-23-enhancement-refinement.md).

## 2026-10-23 - Experimental Image Enhancements and Mophun CPU Budget (Unreleased)

- Add opt-in, Debug-only Java OpenGL ES FSR 1.0 upscaling with adjustable RCAS
  sharpness and a separate motion-estimated frame-interpolation prototype.
- Align intermediate redraw requests with Android animation/vsync callbacks;
  cache completed unchanged output. Count guest frame submissions rather than
  synthetic redraws in the FPS counter.
- Reset interpolation history on menu/background transitions, resize and context
  recreation; restore original rendering after shader/FBO errors. Bound texture
  allocation and do not silently replace custom shaders or renderers.
- Add per-game Mophun CPU budgets of 100/150/200/300%, changing VM instructions
  per scheduling slice without changing SDK time or the frame cap. This is not
  a calibrated CPU frequency or a guaranteed speed increase.
- Use the existing Try / Keep / Undo and profile serialization paths. Existing
  profiles default off/100%; Release builds hide and ignore the experimental
  options. Preserve AMD's MIT notice and document the exact shader provenance.
- Retroid and emulator validation, GPU cost and remaining quality/latency limits
  are recorded in [the evidence report](docs/checks/2026-10-23-enhancements.md).
  No public APK or release tag was changed; Symbian behavior is unchanged.

## 2026-10-20 - Java Sample: Input, Screen Cleanup and Import Recovery (Unreleased)

- Sampled 20 supplied Java variants across older MIDP, Nokia APIs, Canvas,
  GameCanvas and M3G, recording the actual checkpoint reached for each.
- Preserve complete button taps between GameCanvas polls; handle held aliases
  independently and reset stale input across hide/resume.
- Rebuild missing/empty prepared files when reopening their original JAR,
  retaining the installed entry, settings and RMS data.
- Remove obsolete Canvas controls and graphic layers when leaving for a native
  LCDUI menu; restore them when returning to the game.
- Added regression coverage with before-fix failures. Remaining auto-resolution
  and startup findings are documented, not hidden behind per-title patches.
  Emulator evidence only; public APKs unchanged.
  [Results and limits](docs/checks/2026-10-20-java-game-sweep.md).

## 2026-10-20 - Java Frame Cadence Corrections (Unreleased)

- Keep the shared frame limiter's cadence through small scheduler oversleeps,
  without accumulating catch-up work after a pause or a missed period.
- Preserve redraw requests arriving during parallel software/hardware Canvas
  presentation. Bursts still coalesce to the latest frame.
- Identified the Retroid collection's Metal Slug Mobile Impact and confirmed its
  own 100 ms game loop. No title-specific patches, fake CPU setting or claim of
  converting that game to 60 FPS.
- Fresh JVM checks: 49 passes; Android 15 emulator: 37 passes; Debug build/lint
  passed. Retroid unavailable; release APKs unchanged.
  [Evidence and limits](docs/checks/2026-10-20-java-frame-cadence.md).

## 2026-10-19 - Beta 3 Revision 1 Published: Configurable Fast-forward

- Add temporary per-game 150/200/300/400% acceleration for Java, Mophun and
  Symbian/N-Gage, with on-screen toggle, direct game-menu action and controller
  Hold/Toggle mapping. Triggers support both axes and digital events without
  leaking the reserved input into the game.
- Restore the previous speed/frame cap on stop, menu/focus loss and controller
  disconnect. Keep activation out of saved profiles and backups; fast-forward
  settings participate in Try/Keep/Undo. Extend shared clocks/audio to 400%.
- **Released:** 0.4.0-beta.3-r1, code 309, on the existing Beta 3 release page.
  Original assets/tag retained; no new release page. Matching source tag points
  to `0b22cb63`. All five uploaded revision asset digests/sizes matched; public
  APK/checksum downloads were verified. GitHub Actions remains disabled.
- Final signed build, production guards and lint passed (zero lint errors).
  Fresh JVM run: 44 passes. Final Retroid shared UI/live Java, Mophun and N-Gage
  check: six passes; final phone-emulator UI rerun: six passes. Earlier clock,
  audio and controller suites are detailed in the evidence record below.
  Signed upgrade from 308 preserved all 329 readable external-file hashes;
  the library and Bounce Tales menu/fast-forward smoke check passed.
  No full-game, physical-button comfort or listening acceptance is claimed.
  [Behavior and limits](docs/FAST_FORWARD.md),
  [verification](docs/releases/v0.4.0-beta.3-r1-checks.md).

## 2026-10-19 - 0.4.0 Beta 3 Published

- **Released:** [0.4.0 Beta 3](https://github.com/EriArk/JavaEmulator/releases/tag/v0.4.0-beta.3),
  code 308, at 03:54:59 UTC. Includes the LCD effects/export, library navigation
  and portable native titles described in the preparation entries below.
- Source tag `6245cad0`; final clean signed build, APK guards and lint passed.
  Fresh JVM run: 39 passes. Installed the final APK on Retroid over the candidate;
  all 329 readable external-file hashes matched and the library reopened.
- Exported and restored all 49 pinned engine archives; resolved sources for 118
  runtime artifacts. Uploaded APK, matching app source, dependency/native source
  bundles and checksums. All five remote digests/sizes matched. Downloaded APK
  and checksums before publication, then verified an unauthenticated public APK
  download after publication.
- Updated README, download pointers and status for announcement/site handoff.
  No APKs added to Git, no Actions enablement, website or Nextcloud deployment.
  [Verification and limits](docs/releases/v0.4.0-beta.3-checks.md).

## 2026-10-19 - Portable Symbian titles and Beta 3 preparation (historical checkpoint)

- Custom Symbian/N-Gage card titles now belong to virtual-phone data and travel
  with phone backups, including subsequent edits and re-exports. Existing local
  title preferences migrate without replacing newer edits.
- Archive format 3 validates title metadata before restoring a separate phone.
  Formats 1 and 2 remain readable; new archives need Beta 3 or newer at the
  destination. Android row IDs, favorites and play history remain local.
- Failed title saves leave the editor open. Retroid passed 21 backup/artwork
  checks, a complete restore-to-launcher/native-startup flow, 20 shared
  library/LCD checks and a separate artwork UI recreation check. Conditional
  skips and evidence limits are listed in the
  [checkpoint](docs/checks/2026-10-19-phone-titles.md).
- Next candidate is 0.4.0-beta.3, code 308. Public GitHub release is still
  0.4.0-beta.2 as checked on 2026-10-19; no publication or site/cloud deployment
  is implied by this preparation entry.
- Signed candidate built with clean release/guards/lint and 39 fresh JVM passes.
  Installed over Beta 2 on Retroid; all 329 readable external-file hashes were
  retained and the 40-game library opened. Phone emulator: 21 passes and one
  optional capture skip. [Candidate details](docs/releases/v0.4.0-beta.3-checks.md).

## 2026-10-19

- **Local, unreleased after 0.4.0 Beta 2:** improve library navigation with a
  persisted platform filter combined with Recent/Favorites, per-collection scroll
  restoration and Play last. Expose favorites consistently on touch, group title
  and artwork under Edit card, and retain native custom titles across rescans.
  Correct stale asynchronous filter results, locale-dependent search, late native
  selection loss and scroll-margin drift. Capture light phone and dark handheld
  interfaces; keep release packages and existing game files unchanged.
  [Verification and limits](docs/checks/2026-10-19-library-session.md).

- **Local, unreleased after 0.4.0 Beta 2:** add per-game Clean / With LCD effect
  screenshot export for Java, Mophun and Symbian/N-Gage. PNGs exclude app controls;
  LCD export size is bounded, and old profiles keep Clean by default. Correct
  uneven LCD grid bands at fractional scales and use logical Mophun pixels even
  with HD rendering. Mophun exports now honor image rotation; Java raw capture
  no longer risks sharing its live bitmap with screenshot processing.
  [Verification and availability](docs/checks/2026-10-19-lcd-export.md).

- **Local, unreleased after 0.4.0 Beta 2:** add Off / LCD Soft / LCD Classic
  screen effects to Java, Mophun and Symbian/N-Gage. Shared Display controls
  expose intensity and reversible per-game trials. Grid geometry follows guest
  pixels and rotation, fades at low scale and leaves touch controls/menu pixels
  alone. Existing profiles remain Off. Verified mode changes, Undo and saved
  selection after relaunch on Retroid and the emulator; 34 focused checks passed
  on each, plus 39 JVM tests. A short Mophun menu sample showed no extra jank;
  this is not a whole-game FPS guarantee. Fresh screenshots and documentation
  added. Removed Android's unwanted full-frame focus tint on the Mophun game
  surface without changing menu focus. Production app and published Beta 2
  unchanged; no release/upload.
  [Verification](docs/checks/2026-10-19-lcd-effects.md).

## 2026-10-18

- **Released:** [0.4.0 Beta 2](https://github.com/EriArk/JavaEmulator/releases/tag/v0.4.0-beta.2),
  code 307, at 20:22 UTC. Publishes the three visual styles, per-game touch layout
  editor, compact library, native custom artwork and version-2 phone backups
  described below. Source tag `ac698c24`; final signed rebuild and production
  guards passed, then installed on Retroid without uninstalling. All five
  uploaded asset sizes/digests matched; APK and checksums were downloaded again
  and matched before publication. Updated README, status, download pointers and
  five signed-device screenshots. GitHub Actions remains disabled. No website
  or Nextcloud deployment. [Verification](docs/releases/v0.4.0-beta.2-checks.md).

- **Release preparation, 0.4.0-beta.2 (307):** combine the visual styles, touch
  layout editor, compact library and native artwork/backup changes below.
  Clean signed build, production guards and lint passed. Upgrade over Beta 1
  preserved all 329 readable external files. Verified signed touch placement
  persistence after game relaunch and captured five fresh interface screenshots.
  Fresh checks: 39 JVM passes, 66 Retroid and 36 emulator instrumentation passes;
  one optional capture skipped per device. Source inventory and fresh native
  source restoration passed. Not yet published at this checkpoint.
  [Evidence and limits](docs/releases/v0.4.0-beta.2-checks.md).

- **Local, unreleased after 0.4.0 Beta 1:** include custom Symbian/N-Gage covers
  and icons in whole-phone backups. Restore artwork atomically with the new
  phone, keeping the original unchanged; support re-export after artwork edits
  or resets. Continue reading version-1 backups; new version-2 backups require
  this implementation. Validate image sizes, decoding, paths and file hashes.
  No new release, upload or production-app replacement.
  [Verification](docs/checks/2026-10-18-symbian-artwork-backup.md).

- **Local, unreleased after 0.4.0 Beta 1:** extend the existing Artwork menu to
  Symbian, N-Gage and N-Gage 2.0 cards. Choose separate cover/icon images or
  restore each automatic image independently. Keep overrides separate from
  generated art and isolated by system/UID. Fix picker callbacks rejecting
  native cards' negative IDs; refresh both the library and selected detail art.
  Rescan, recreation, invalid image and cross-system isolation checks added.
  At this checkpoint custom art was not in phone backups; the follow-up above
  adds it. No release or upload.
  [Verification](docs/checks/2026-10-18-symbian-artwork.md).

- **Local, unreleased after 0.4.0 Beta 1:** add Classic / Retro LCD / High
  contrast styles separately from brightness, with themed cards, menus, mapper,
  installer and touch controls. Add a per-game visual touch layout editor with
  drag/nudge/resize, separate orientation/layout placements and the existing
  reversible preview/save workflow. Improve square-screen gallery density,
  compact list rows, filter access, large-font header layout and controller focus
  retention. Refresh engine appearance from the main process. Debug builds and
  UI checks only; no new public APK or distribution.
  [Evidence, screenshots and limits](docs/checks/2026-10-18-visual-polish.md).

- **Released:** [0.4.0 Beta 1](https://github.com/EriArk/JavaEmulator/releases/tag/v0.4.0-beta.1),
  code 306, at 15:33 UTC. One signed adaptive APK replaces separate Phone and
  Handheld downloads and includes the unreleased-after-beta.6 improvements below.
  All five uploaded assets matched local hashes/sizes; APK and checksums were
  downloaded and verified. Source tag `d6f42706`. Final signed Retroid checks
  covered migrated Java launch, Always touch preview and automatic rollback,
  plus native Snakes startup/main menu. Source/dependency bundles accompany it.
  Updated README, migration guide and three signed-build screenshots. Actions
  stays disabled. No website or Nextcloud deployment in this checkpoint.
  [Verification and limits](docs/releases/v0.4.0-beta.1-checks.md).

- **Release preparation, 0.4.0-beta.1:** signed unified APK built and installed
  over Handheld beta.6 on Retroid. All 324 readable external files retained
  their hashes before launch. Exported two games through the old signed Phone
  app's menu; restored one new game through the unified migration workflow,
  leaving the existing-game conflict unchecked. Both RMS hashes and all 55
  previous profile field values matched; existing game data stayed intact.
  Corrected the migration help path. Updated download/migration documentation
  for one APK. Clean release build, production guards, zero-error lint and
  118-artifact source inventory passed. No website deployment.
  [Release verification](docs/releases/v0.4.0-beta.1-checks.md).

- **Public source, unreleased 0.4.0-beta.1 (code 306):** unify the primary APK
  for phones and handhelds. Unlock portrait/landscape library and settings;
  retain controller focus and per-game orientation/mapping choices. Add shared
  Auto / Touch / Controller priority and Auto / Always / Off screen buttons.
  Disconnecting releases held input and pauses; reconnecting does not resume
  without user action. Restore full game space when controls are hidden.
  Fix legacy keypad cancellation releasing guest keys. Add Phone migration
  actions through existing save-preserving backup workflows; keep the old app
  intact and explain separate Symbian backups and non-transferred app preferences.
  Keep the old Handheld package ID; retain the Phone flavor only for migration
  tests. No new release, uploaded APK or website deployment.
  [Evidence and remaining release checks](docs/checks/2026-10-18-unified-apk.md).

- **Public source, unreleased after beta.6:** replace the full-width Keep/Undo
  strip with compact shared preview controls for Java, Mophun and Symbian.
  Keep 48 dp touch targets, accessible icon names/tooltips and visible focus;
  use a dark palette in either app theme. Stable timer width avoids layout
  shifts. Preserve game bounds, shortcut-row clearance, preview lifetime and
  existing menu actions. Retroid and emulator each passed 32 UI/settings checks;
  two real Java/Mophun display-trial workflows passed with inspected captures.
  Both combined debug variants built. No new beta or distributed APK.
  [Evidence and limits](docs/checks/2026-10-18-compact-preview.md).

- **Public source, unreleased after beta.6:** deliver the current Java screen
  size before an already-queued paint, suppress stale resize notifications, and
  defer hidden-game resize callbacks until foreground return. Retain GameCanvas
  cached-graphics recovery. Fix black GLES screenshots by rendering before pixel
  readback and using captured dimensions for bitmap conversion. Four focused
  regressions failed before their fixes. Retroid and emulator each passed 61
  checks; a real Java workflow passed four resolution/Home/return/Undo cycles
  without restarting. No new release or APK distribution.
  [Evidence and limits](docs/checks/2026-10-18-resize-resume.md).

- **Public source, unreleased after beta.6:** preserve directly submitted Java
  frames across host-only scale, rotation, smoothing and surface changes.
  Avoid unnecessary guest paint callbacks that could replace the current frame
  with stale/empty graphics; retain callbacks for actual logical size changes.
  Two regressions failed before the fix, then passed with software, GLES and
  hardware surface coverage. Retroid and the Android emulator each passed 56
  checks; two live Java/Mophun display-trial workflows also passed on Retroid.
  Both combined debug variants built; 38 JVM tests per variant and zero-error
  lint passed. This does not resolve every startup black screen. No new APK
  distribution. [Evidence and limits](docs/checks/2026-10-18-display-presentation.md).

- **Public source, unreleased after beta.6:** import Mophun MPN games from ZIP/7z
  through the existing installer. Mixed JAR/MPN archives show one game chooser;
  single-game archives skip it. Correct the combined build's 7z routing and
  retain original source/entry identity. Reimports preserve saves, settings and
  installed cards. Bound extraction and content staging; reject unsafe paths,
  duplicates, invalid or oversized payloads. Retroid passed 56 focused/regression
  checks; the Phone emulator passed 17. A real archive-to-game-menu workflow
  passed on Retroid. No new beta or distributed APK.
  [Evidence and limits](docs/checks/2026-10-18-game-archives.md).

- **Public source, unreleased after beta.6:** Add folder now finds loose Mophun
  MPN, KJX and standalone JAD files, without duplicating same-name JAR/JAD pairs.
  Count only committed additions; report blocked subfolders, loading providers
  and traversal limits. Preserve installed entries and metadata on rescans.
  Mophun installation replaces its discovered placeholder, retains favorites
  and remembers validated source copies without touching saves or profiles.
  Both base/combined debug variants built; 26 focused checks passed on Retroid
  and 26 on the Phone emulator. A real emulator system-picker/import/launch/
  rescan workflow passed. No new beta or APK distribution.
  [Evidence and scope](docs/checks/2026-10-18-game-folders.md).

- **Public source, unreleased after beta.6:** audio follows per-game emulation
  speed through shared Java media/tone players, Mophun session MIDI and Symbian's
  common output. Tempo and pitch change together; no extra setting. Preserve
  paused/prepared state, normal-speed recovery after backend rejection, and
  media-position continuity across native rate changes. Java MMAPI time uses
  the guest clock. Existing Mophun sound enablement is unchanged. Both combined
  debug builds, 32 JVM checks per variant and zero-error lint passed; Retroid
  and the Android emulator each passed 36 shared audio/timing/settings checks.
  Native waveform/rate tests and five live UI checks passed on Retroid, including
  Deep Abyss/Snakes speed trials and Snakes restart/background return.
  Listening quality, Android 16 and complete-game sync are not certified.
  [Evidence and limits](docs/checks/2026-10-18-audio-speed.md).

- **Released:** [0.3.0-beta.6](https://github.com/EriArk/JavaEmulator/releases/tag/v0.3.0-beta.6),
  code 305, published at 09:48 UTC. Both signed APKs and three matching source
  bundles are public. All six uploaded asset digests matched; APKs and checksum
  list were downloaded and verified before publication. Final signed Retroid
  checks covered Java startup/live speed trials and native Snakes launch.
  Handheld upgraded in place; all 323 readable external files retained their
  hashes before launch. Protected/private files were not independently hashed.
  Handheld beta.6 remains installed. Both release builds, production guards and
  lint passed (zero errors, warnings remain). GitHub Actions remains disabled.
  [Release checks](docs/releases/v0.3.0-beta.6-checks.md),
  [three new screenshots](screenshots/releases/v0.3.0-beta.6/README.md).

- **Released in beta.6:** implemented separate per-game
  50-200% emulation-speed presets for Java, Mophun and Symbian, with live Try /
  Keep / Undo. Guest clocks remain continuous across changes; host UI and FPS
  limits stay independent. Java timing conversion uses private cache and leaves
  installed files/saves untouched. Retroid and Android emulator each passed 29
  shared timing/settings checks; Retroid live trials passed in Java, Deep Abyss
  and Snakes. Audio is not time-stretched; not every game's pacing follows
  percentage speed. Includes the earlier native FPS and file-transfer changes.
  [Evidence](docs/checks/2026-10-18-emulation-speed.md).

- **Released in beta.6:** added per-game frame-rate presets to Mophun
  and Symbian/N-Gage through More and Display, with live Try / Keep / Undo.
  Old native profiles retain 60 FPS; Java limits are unchanged. Mophun uses
  the shared monotonic limiter; Symbian covers swaps, window redraws and direct
  framebuffer polling, including safe Unlimited handling. Retroid checks in
  Deep Abyss and Snakes passed alongside shared settings/pacing checks on
  physical hardware and the Android emulator. This is not a percentage-speed
  control or a universal fix for fast-running games.
  [Evidence](docs/checks/2026-10-18-native-frame-rate.md).
- **Released in beta.6:** hardened the shared Symbian file server's
  read, section-read and write bounds; removed an unsafe beyond-EOF write,
  preserved partial-read lengths and supported wide write offsets. Protected
  application lookup and launch with one kernel lock. No per-title exceptions
  or changes to installed game/firmware files. Retroid native checks, both
  combined debug builds, 29 JVM tests per variant and lint passed. Snakes passed
  a title-screen/restart/exit check. Asphalt 3 reached a race but still produced
  a blank screen after restart; the stronger runtime check now fails on that
  result. This is not a complete Asphalt compatibility fix.
  [Evidence](docs/checks/2026-10-18-symbian-file-transfers.md).
- **Released:** [0.3.0-beta.5](https://github.com/EriArk/JavaEmulator/releases/tag/v0.3.0-beta.5),
  code 304, published at 07:00 UTC. Signed Handheld/Phone APKs and matching app,
  dependency and native source bundles are public. All six asset hashes matched;
  both APKs and the checksum file were downloaded and verified before publication.
  Final signed Retroid checks covered Deluxe Quick play/portrait/restart, Mophun
  gameplay, Snakes menus and the retained 39-game library. Both release builds,
  29 JVM tests per variant, APK guards and lint passed (zero errors, warnings remain).
  [Checks](docs/releases/v0.3.0-beta.5-checks.md),
  [seven fresh captures](screenshots/releases/v0.3.0-beta.5/README.md).
  Retroid's official variant is Handheld beta.5; temporary production Phone was
  removed with data retention. GitHub Actions remains disabled.
- **Released in 0.3.0-beta.5:** fixed a shared startup deadlock affecting
  MIDlets that wait for their screen inside startApp(), reproduced with Age of
  Empires II Deluxe 02.00.35. Retained surface-independent rendering and guarded
  pause/resume, declined startup and obsolete queued start requests. No per-game
  exception or changed JAR. [Evidence](docs/checks/2026-10-18-java-startup.md).
- **Verified locally:** Deluxe reached Quick play on Retroid and the Phone
  emulator; Retroid touch navigation, pause/resume and exit worked. Six startup
  regression cases plus 21 Canvas cases passed on the emulator; a preceding
  36-case combined Phone run passed on Retroid. Real Mophun Phone appearance and
  native Snakes shared-control UI tests also passed. Android 16 / Deluxe 02.00.39
  are not claimed verified. Final signed-APK checks are recorded separately below.
- **Released in beta.5:** Phone's upper game shortcuts now open from a
  small edge arrow and collapse after five idle seconds. They overlay the game
  without reserving a portrait row or resizing the image. Normal game/keypad
  touches do not reveal the panel; Handheld access remains unchanged.
- **Released in beta.5:** Phone's Controls page adds a per-game Button
  theme (Follow app / Dark / Light), allowing dark touch controls with light app
  menus. The shared Java/Mophun/Symbian touch deck and top game shortcut buttons
  use the selected palette;
  normal settings preview/Undo/Keep preserves confirmed values.
- **Released in beta.5:** refreshed the Phone number pad with raised key
  faces, clearer digit/letter typography and tactile marks on 5. Landscape uses
  a connected navigation cross. Hit targets, key codes and multitouch behavior
  remain unchanged; the custom Legacy keyboard is not restyled.
  [Appearance checks](docs/checks/2026-10-18-phone-touch-appearance.md).
- **Verified locally:** the Phone control/chrome batch built in both base and
  combined variants with zero lint errors. The final Phone emulator suite passed
  43 selected checks; Retroid passed 34 shared-component/input checks. These do
  not establish physical-phone ergonomics or full-game compatibility.
  A real Phone UI case also passed theme/layout/orientation changes, relaunch
  persistence and auto-hide; seven inspected development screenshots are included.
- **Released in beta.5:** fixed stale Java Graphics targets after buffer
  growth, GameCanvas buffer resizing and size-notification ordering. Hardened
  GLES context/geometry updates and ignored obsolete surface callbacks. These
  address shared resize failure classes, not every game's black screen.
  Stable-size GLES frames reuse texture storage; no measured FPS gain is claimed.
- **Released in beta.5:** recognize explicit paired screen dimensions
  such as LG TargetLCD, and prefer original over target-display resolution.
  No per-title rules or resetting of existing settings.
- **Released in beta.5:** restore transfer source/save/settings folders,
  subfolder access and pending picker state after Android process recreation.
  A returned selection cannot be lost to an automatic rescan; interrupted
  imports/exports are not silently repeated.
  A real emulator process-death/DocumentsUI round trip returned to review with
  the selected game's saves included, without committing an import.
- **Released in beta.5:** protect Symbian replacement sessions from stale
  exits/surfaces and prevent background surface/menu events from resuming games.
- **Verified locally:** both base and combined variants built; zero lint errors,
  29 JVM tests per variant, 98 final emulator checks and 98 earlier Retroid checks.
  A separate Snakes UI/pixel check passed on Retroid. Added Canvas/GameCanvas
  frame-cap coverage; no universal simulation-speed claim. Physical testing was
  initially paused because another app appeared; concurrent device use was an
  unconfirmed inference, subsequently corrected by the owner. Public beta.4 is unchanged.
  Final Phone emulator checks also covered Bounce Tales size trials/Undo,
  restart and exit, with inspected game images rather than a full playthrough.
  [Evidence and limits](docs/checks/2026-10-18-runtime-transfer.md).
- **Physical follow-up:** Retroid passed 100 selected checks with the final
  runtime/transfer fixes, plus two Snakes UI cases. The new opt-in native test
  covers three consecutive restarts and Home/return cycles, inspecting captured
  guest pixels rather than Android menu chrome. This adds regression coverage,
  not a new runtime fix or a full-game/audio/save acceptance claim.
  Bounce Tales also passed 128x160 / 480x800 / 240x320 live trials, Undo,
  restart and exit on Retroid; captured game images were inspected.

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
