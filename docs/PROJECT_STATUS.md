# AbyssME - Current Product and Development Status

**Last updated: 2026-10-28.** Verification dates are recorded per section.
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

AbyssME runs old mobile Java games, Mophun and experimental Symbian/N-Gage
games on Android phones and gaming handhelds.
It is an independent J2ME Loader fork focused on browsing a game collection,
using physical controllers, and adjusting each game's controls and picture
without a long setup process. It requires Android 10 or newer. Games are not
bundled. The app UI is English.

- **One adaptive APK:** portrait and landscape, physical controllers and touch.
- Automatic controller detection with per-game priority/visibility overrides.
- Former Handheld installs update in place. The old Phone app remains separate;
  transfer its games and saves through the in-app backup/restore workflow.
- Experimental Mophun support is integrated in the same app, publicly since
  0.2.0-beta.1; the historical 0.1.0 beta.2 has no Mophun.
- Embedded Symbian / N-Gage has been included since the combined 0.3 beta. Snakes reaches
  gameplay on Retroid. Asphalt 3 now installs and has reached a moving race with
  upright graphics, but cold relaunch remains unreliable. The property-free
  developer build remains Java/Mophun-only. This does not establish audio/save or
  full-game compatibility.

## Availability: Do Not Mix These Channels

**Current public beta: [0.6.0-beta.1-r1](https://github.com/EriArk/JavaEmulator/releases/tag/v0.6.0-beta.1)**,
code **312**, uploaded **2026-10-26 at 18:01 UTC** to the existing Beta 1 page.
It centers confirmations
and adapts ordinary panels to screen shape instead of forcing all handheld
menus to the right. Signed Retroid and emulator UI checks are recorded in
[revision verification](releases/v0.6.0-beta.1-r1-checks.md).
All nine release asset digests/sizes match, including preserved original assets;
unauthenticated r1 APK/checksum downloads also match. The r1 source tag and
app-source ZIP identify `98dc7095`; subsequent documentation commits do not
change the APK. 395 JVM tests pass, with 34 focused Android passes and seven
optional room skips on Retroid and the emulator retry. New
[dark screenshots](../screenshots/releases/v0.6.0-beta.1-r1/README.md)
include library views across four viewport shapes and both touch layouts.
[Revision notes](releases/v0.6.0-beta.1-r1.md) and
[checksums](releases/v0.6.0-beta.1-r1/SHA256SUMS-r1.txt).

**Original Beta 1 base: [0.6.0-beta.1](https://github.com/EriArk/JavaEmulator/releases/tag/v0.6.0-beta.1)**,
code **311**, published **2026-10-26 at 17:19:24 UTC**. It combines the
cumulative #40 redesign with early experimental multiplayer.
Common settings, account, transfers and native setup/archive pages now share
Soft Console materials. The existing encrypted two-player Java Bluetooth path
is enabled in Release and labeled low compatibility. No new networking service,
engine or universal game adapter was introduced in this final slice. One adaptive
APK includes Java, Mophun and experimental Symbian / N-Gage, Android 10+,
arm64-v8a. Games and firmware are not included. The former Phone app remains a
separate migration source, not a second download.
[Release notes](releases/v0.6.0-beta.1.md) and
[verification/publication record](releases/v0.6.0-beta.1-checks.md).

**Editorial direction after 0.6:** focus on polish, ordinary user journeys,
stability and compatibility instead of adding more major features. Online is
not a universal multiplayer solution: exact game versions are required, some
networks cannot connect, and long sessions/different-network acceptance remain
open. This is the wording boundary for website and announcement copy.

For the original code-311 base, all five asset digests and sizes matched. Unauthenticated public APK
and checksum downloads also matched. Runtime tag `v0.6.0-beta.1` points to
`664def5f`; the app-source ZIP uses `c63c4a0d`, whose sole subsequent change
corrects a source-build documentation command. No runtime differs. Later
handoff commits do not change the APK. The signed upgrade retained all 329
readable external files on Retroid; inaccessible/internal data was not hashed.
391 JVM checks passed. Retroid UI/lifecycle checks and the emulator retry,
paired launch/recovery and their limits are recorded in the release checks.
GitHub Actions remains disabled. No website or Nextcloud deployment here.
[Checksums](releases/v0.6.0-beta.1/SHA256SUMS.txt),
[sixteen signed-app captures with provenance](../screenshots/releases/v0.6.0-beta.1/README.md).

The previous [0.5.0-beta.1](releases/v0.5.0-beta.1-checks.md), code 310,
was published 2026-10-23 at 21:32:23 UTC with runtime tag `9c01d685`.
Its assets and all earlier tags remain unchanged.

Historical **0.3.0-beta.2** was withheld before publication after final provisioning
exposed a mixed SIS/N-Gage catalog defect; **0.3.0-beta.3** fixed it. These are
older 0.3 builds, not the current release series.

| Channel | Current identifier | Availability |
| --- | --- | --- |
| Public download | `0.6.0-beta.1-r1`, code 312 | One signed adaptive APK on the existing Beta 1 page; centered confirmations, shape-adaptive panels and 29 dark screenshots. Corresponding r1 app source reuses unchanged dependency/peer and Symbian bundles. Early experimental Java Bluetooth multiplayer retains its limits. |
| Development source | Unreleased audio, Phone chrome and per-game touch mapping after r1 | Nokia Sound gain, move/hide handle and independent opacity; graphical Gamepad button mapping with templates and reversible per-game saving. Debug Retroid checks only, not a new public APK. Exact #41 quiet-music reports and Treasure Towers remain unverified. [Audio/chrome](checks/2026-10-28-audio-and-phone-controls.md), [mapping](checks/2026-10-28-touch-gamepad-mapping.md) |
| Private historical snapshot | `0.1.0-beta.2-experimental.15`, 2026-10-11 | Old Java/Mophun test build, not the current beta |
| Local-only material | Game fixtures, firmware, diagnostics, device data and signing keys | Not in Git or public release assets; describing testing does not authorize redistribution |

## Stabilization Queue (2026-10-28)

Approved order: **#41 audio -> #42 menu handle/opacity -> #45 touch gamepad
mapping -> #43 localization**. **#44 new platforms is deferred**.
[Scope and acceptance boundaries](STABILIZATION_PLAN.md).

The first development batch repairs Nokia Sound gain and implements movable,
hideable Java Phone chrome plus independent opacity. It preserves existing
defaults, game pixels, physical mappings and Try/Keep/Undo. 395 JVM tests pass;
Retroid's focused run has 59 passes and one optional capture skip. Eight audio
tests include a real Android session-output measurement. These are not complete
game, subjective audio or gesture-navigation acceptance.

Locally available Avatar 1.0.6 and Cops 1.2.1 use MMAPI, not the repaired Nokia
path. Their captured music starts at level 100. The reported 1.1.6/1.2.3 quiet
music therefore remains open; no global boost or game-name workaround was added.
The next batch implements **#45's general virtual Gamepad mapper** in the existing
Touch layout editor: select a drawn button, choose a phone key, or use the Native
arrows / Numpad template. Directions, fire, soft keys, action buttons and explicit
diagonal overrides are per game, shared across orientations. Literal Phone keys
and the Gamepad's 123 bank stay unchanged; physical bindings stay separate.
Positions and assignments reset independently. Try/Keep/Undo and profile reload
are covered, along with Java guest callbacks and native input adapters. Final
checks: **398 JVM passes, 72 Android passes on Retroid**, lint zero errors / 447
warnings. ADB-driven UI verifies Keep and relaunch, plus Avatar menu navigation;
this is not a complete-game or physical-button acceptance claim.
Treasure Towers was not found in the local filename search and is **not yet
reproduced**. #45 stays open for that acceptance gap and touch-phone accessibility
checks. #43 remains queued. New mapping labels use English resources; this is
not whole-app localization. No new signed APK or public release assets.
[Mapping evidence and local captures](checks/2026-10-28-touch-gamepad-mapping.md).
[Changes, measurements, fixture identities and remaining checks](checks/2026-10-28-audio-and-phone-controls.md).

Public release asset names were rechecked on 2026-10-28; the signed r1 APK and
release tags were not changed. Asset hashes were not reverified in this pass.

## Library Refresh (#40, Released Scope and History)

The cumulative main library, game-menu, touch-deck and Social refresh below is
included in 0.6.0-beta.1. Final common settings/account/transfer/native-setup
polish and two lifecycle/layout fixes are documented in the release record.
The dated entries retain their original development status: "unreleased" and
"Release disabled" describe those earlier checkpoints, not current availability.
The epic remains open for remaining usability/accessibility validation and
inherited expert/engine dialog polish; this release does not claim every
acceptance box or long multiplayer session has been completed.

**Friends and Chats, 2026-10-26:** both use the Soft Console light/dark surfaces,
recessed lists, molded tabs/filters and contextual contact/delivery panels.
Incoming requests have a count; Social options retain room/account navigation and
chat history commands. Short-screen controls leave more space for contacts and
messages, with large-text-aware split panes and bounded composer growth. Drafts,
account fencing, explicit retry and destructive confirmations remain intact.
This is unreleased source and a separate local Debug installation, not a new
public APK. [Checks and captures](RETRO_LIBRARY_REFRESH.md#friends-and-chats-2026-10-26-unreleased).

**Game-aware public lobbies, 2026-10-26:** public Debug rooms now advertise the
verified Java game's title, exact edition/adapter, free seats and match state.
Privacy, closure and newer selections supersede pending updates; directory
confirmation still does not grant membership or press Ready. Room browsing now
uses Soft Console surfaces and contextual details, distinguishes loading/errors
from an empty result, and does not offer joining a full or playing room. Social
options keep narrow headers usable. Release online capabilities stay disabled.
391 JVM checks pass, plus 14 Android checks on each target and two focused
480x320dp / 150% text checks. A live Retroid/emulator pair verified BT Biplanes
publication, admission, seat changes, privacy/republishing and close, alongside
unchanged independent TrainerOS listings. No new gameplay/NAT acceptance, server
deployment, public APK or release update.
[Checks and captures](RETRO_LIBRARY_REFRESH.md#game-aware-directory-2026-10-26-unreleased).

**Native game settings, 2026-10-26:** Symbian/N-Gage cards now offer Display and
Controls in the same contextual panel as Java/Mophun. The engine-owned profile
remains isolated by phone identity and game UID. Try in game starts a reversible
trial; Keep confirms it, while Undo or an unconfirmed exit preserves the saved
configuration. Native display options match the runtime's actual capabilities,
not Java/Mophun-only GPU effects. Shared settings selectors use molded console
surfaces and tappable labels; Try stays pinned below scrolling edge-panel content.
This is unreleased source/development APK work, not an update to the public beta.
379 JVM checks pass; final Android suites pass 51 on Retroid and 49 on the phone
emulator, plus two focused 480x320dp / 150% text checks. Actual Snakes startup,
rotation trial, Undo and exit were checked on Retroid, not full-game acceptance.
[Implementation, checks and limits](RETRO_LIBRARY_REFRESH.md#native-library-settings-2026-10-26-unreleased).

**Social entry and light materials, 2026-10-26:** add Debug-only Play Together
from eligible Java cards into the existing room owner, and a cross-process-safe
Return to active room shortcut. Prepare the selected game after explicit Create;
keep Ready and launch explicit. Replace flat player rows with responsive seats,
initials and backed state; keep Ready/Start outside scrolling content. Light
Soft Console now uses near-white shell/cards, cleaner recess edges and softer
colored platform keys. No new public APK, online Release capability or change
to the physical mapper. Selected-game browsing accepts exact editions or rooms
still choosing; publishing was added in the later game-aware directory batch above.
Retroid and phone-emulator paired testing covers two BT Biplanes launches in one
private room, Social return without ending the session, canceled End confirmation
and recovery after game-process death. Preserve the original room screen beneath
the game; this does not establish full-match or different-network acceptance.
Final Debug verification: 379 JVM passes and 22 Android UI passes on each target,
plus the paired lifecycle run. [Nine labeled development captures](../screenshots/development/2026-10-26-social-console/README.md).
[Implementation, verification and limits](RETRO_LIBRARY_REFRESH.md#social-entry-player-seats-and-light-materials-2026-10-26-unreleased).

**First implementation slice, 2026-10-26:** replace the platform dropdown and
permanent details sidebar with Java ME / Mophun / Symbian / N-Gage tabs. Retain
Gallery/List/Grid and cover formats; remember view, collection, search, position
and selection per platform. Add circular, deduplicated L2/R2 switching, horizontal
swipes, direct Social access and temporary library action panels. Preserve
existing game identities, imports, artwork and engine settings.

**Soft Console materials, same date:** implement the approved softer molded
library treatment, inspired by TrainerOS dark materials without copying its
navigation. Add a recessed content well, muted glossy platform keys, shallow
contact edges and matching temporary library panels. Light and dark variants
retain readable focus; High contrast stays flat. Short landscape screens combine
tools into the header without dropping view controls. Artwork is not filtered or
replaced. Phone-control follow-up is described below.

**Contextual actions and shared game panels, same date:** extend the treatment
to Java, Mophun and Symbian game menus. Group primary commands separately from
More, add a small game-art header to library actions, restore page focus/scroll,
and reuse existing Social destinations with return to the panel. Java/Mophun
pre-game Display and Controls stay within the action panel; native system actions
retain their existing route. Fix the first tap merely selecting a library card
instead of launching it, while preserving hold and canceled-touch behavior.

The final combined Debug build passes 374 JVM checks. Each Android target runs
44 tests: Retroid 43 pass/1 optional capture skipped, phone emulator 42 pass/2
skipped (capture and physical-controller-only focus). Installed hashes match.
Retroid solo navigation covers Bounce Tales, Deep Abyss and Snakes, including
Social return and Resume/Exit. These are instrumented checks, not manual button
comfort or full-game acceptance. Java's local/network pause policy has regression
coverage, but paired network menu/Social acceptance remains open.

**Live picture and phone controls, same date:** add instantaneous supported preset
trials over the paused game, sticky Keep/Undo, visual-only saving and interruption
rollback. Add Phone Keypad Separate/Overlay with independent positions and refined
keycaps. Preserve physical mapping, Gamepad/Legacy and default Separate geometry.
Fix native LCD preview reconfiguring the paused surface; Symbian instant previews
are LCD-only, with size/rotation kept in Advanced for apply-on-resume. Java/Mophun
support live size/rotation. Tile motifs are illustrative, not game thumbnails.
Play Together/active-room shortcuts and lobby seats are covered by the later
social-entry batch above; wider acceptance remains open.
This follow-up passes 374 JVM tests and 89 Android tests per target, with six
optional fixture/capture scenarios skipped on each. Installed Debug hashes match.
Retroid verifies paused Mophun xBRZ and native LCD; the phone emulator verifies
Java CRT, keypad Separate/Overlay and persisted selection through the game menu.
No public APK or release update.
[Scope, screenshots, evidence and remaining work](RETRO_LIBRARY_REFRESH.md).

## Multiplayer Foundation (Unreleased)

**Additional Bluetooth games and signaling order, 2026-10-26:** test locally
supplied 1916 Dogfight, 3D Constructo Combat and Absorbed on Retroid/emulator.
Dogfight and Constructo reach paired L2CAP gameplay; Constructo also returns
normally to the room. Absorbed exposes a shared signaling-order defect: snapshot
coalescing could move game authority after the peer offer. Fix that queue order
with a failing-before/passing-after regression test; three subsequent Absorbed room
preparations launch both games. Opening its server before guest discovery reaches
paired RFCOMM gameplay. Graphics/vendor-key compatibility remains open. This is local
Debug testing, not different-network or full-game acceptance. Pass 370 app JVM
tests and 65 Android network/bridge/lifecycle checks per target; installed Debug
hashes match. No new release.
[Exact fixture results and evidence](BLUETOOTH_GAME_SELECTION.md).

**Real Bluetooth game connection and repeat session, later 2026-10-25:** use
the unmodified, locally supplied BT Biplanes on Retroid and an emulator. Fix
shared small-write backpressure, consumed preparation surviving reentrant room
callbacks, and a late misleading Rejoin notice. The final pair connects through
the game's own Bluetooth menu twice in one room; normal exit by host, then guest,
returns both seats and requires fresh Ready. Pass 381 JVM tests and 65 Android
checks per target; installed APK hashes match. This is connection/return proof,
not complete-game or sustained movement synchronization acceptance. This edition
still has graphical artifacts. Release capabilities remain disabled; no public
APK/upload. [Fixture, exact checks and limits](BLUETOOTH_GAME_CHECK.md).

**Room-to-game workflow, later 2026-10-25:** Debug now connects installed-game
selection, exact-edition checks, Prepared/Ready, host launch, process attachment
and return. The room remains active while the game runs in another process.
Fix a live guest-return failure in the shared lifecycle rather than in a game.
378 JVM checks and 60 Android checks on each target pass. Paired dedicated-account
runs open Bounce Tales on Retroid and an emulator, in both host roles, and return
to the same room after process death. The final Debug artifact repeats the
Retroid-host scenario and both Android suites; installed hashes match.
This is startup/return proof, not a Bluetooth game match. Release capabilities
remain disabled; no public APK/upload. [Scope, artifacts and checks](ROOM_GAME_LAUNCH.md).

**Isolated game-process bridge, earlier 2026-10-25:** add a private, one-use launch
lease and bounded packet pipe into the separate game process. Verify installed
game bytes before attachment; wait before constructing the MIDlet; revoke the
connection on process death and keep a failed virtual adapter from falling back
to physical Bluetooth. Pass 361 JVM tests and 59 Android checks on each target,
including encrypted RFCOMM exchange with a separate-process fixture. Ordinary
Sudoku launch, restart and exit also work on Retroid through ADB input. This is
not a real multiplayer match: at this checkpoint the room's game picker,
coordinated launch and return were still missing. The later Debug workflow above
connects them; Release capabilities remain disabled. Debug
installs only; no public release/upload. [Evidence and limits](MIDLET_NETWORK_BRIDGE.md).

**Bluetooth peer bridge, later 2026-10-25:** connect the Java Bluetooth backend
to encrypted peer transport, including service metadata, multiple streams,
L2CAP framing, bounded receive credits and cancellation. Retroid and an emulator
exercise the actual game-facing APIs through two real Fluxer accounts in both
host roles, with a 256 KiB stream echo and packet/metadata checks. Room preparation
now requires an admitted current binding, exact local-content verification and
a successful transport handshake; Ready remains explicit. 353 JVM tests and
50 Android checks on each target pass. This is still not a real game match:
at that checkpoint the main-process room owner still needed the IPC bridge
recorded above, plus game selection, launch and return handling. Production
capabilities stay disabled. No release,
new screenshot or upload. [Implementation and device evidence](BLUETOOTH_PEER_BRIDGE.md).

**Secure peer core, 2026-10-25:** add a pinned Android ICE/DTLS/SCTP transport,
room/game/account/attempt binding, bounded fragmented signaling and explicit
disconnect/timeout handling. Retroid and an Android emulator exchange 100 ordered
binary packets each way using real dedicated Fluxer accounts for signaling only.
No gameplay packets pass through chat. Fix a real connection failure caused by
21-second device clock skew: use local monotonic attempt deadlines, not matching
wall clocks. 327 JVM tests and 33 Android checks on each target pass; native checks
also reject forged certificate fingerprints. Source preparation includes the dependency bundle and
APK license notices. This is a transport fixture, not a Java match: the production
room-to-game coordinator and virtual Bluetooth backend were not connected at
that checkpoint; the later bridge work is recorded above.
Production capabilities remain empty; no public release or Nextcloud upload.
[Exact build, tests and remaining limits](SECURE_PEER_TRANSPORT.md).

**Virtual Bluetooth adapter, later 2026-10-24:** implement Java-side discovery,
service metadata, RFCOMM streams and L2CAP packets behind the existing game APIs.
Add cancellation/lifetime fencing, bounded SDP copies and correct stream ownership.
312 JVM tests and 23 API/core checks on each of Retroid and an emulator pass.
At that checkpoint secure P2P and room-to-game launch were still missing. The
following transport work is recorded above; playable Internet multiplayer is still unavailable.
[Implementation, exact verification and remaining work](VIRTUAL_BLUETOOTH.md).

**Public directory deployment, later 2026-10-24:** the existing service now runs
v2 with a matching source/SQLite rollback snapshot; all 12 original records remain
unchanged. Public discovery/admission, guest departure, withdrawal, republishing
and close passed on Retroid plus an emulator, alongside two separate TrainerOS
test meetings. Fix stale-room joins and a merged service-source v1/v2 field leak.
294 JVM, four Retroid core and 26 service tests pass. Public release unchanged.
[Current evidence and captures](FLUXER_PUBLIC_DIRECTORY_CHECKPOINT.md).
The earlier checkpoint statements below about an undeployed service are historical.

**Directory coexistence and non-friend checkpoint, 2026-10-24:** the owner now
retains 32 TrainerOS listings and one independent AbyssME public room per account.
The isolated service patch preserves the actual deployed v1 meeting metadata and
passes 22 Python tests; it is not yet deployed. AbyssME also repairs slow inbox
polling starving queued controls and retains only safe provider error codes for
actionable delivery notices. After both accounts accessed the shared community,
the real non-friend pair passed admission, automatic friendship, cleanup and Keep
on Retroid plus an emulator (four successful role runs). Replayed admission/terminal history also consumes old
invitations without restoring membership. 292 JVM tests and four native core
checks passed.
[Current evidence and remaining gates](FLUXER_NONFRIEND_CHECKPOINT.md).

**Live pair checkpoint, 2026-10-24:** two authorized accounts exchanged messages
and established a friendship through the normal UI. Retroid and an Android
emulator then hosted/joined private rooms in both directions, retained membership
across activity recreation, exchanged heartbeats and completed leave/close.
The first run exposed a clock-skew defect: a roughly 22-second difference caused
valid invitations to be discarded. A shared bounded future-time tolerance fixes
room, directory, publication and contact deadline checks without accepting already
expired packets. Friends now displays its own contact status. **287 JVM tests,
12 Retroid chat/friend UI checks, one assisted sign-in and four live room role runs
passed.** No public APK/service update or playable-game claim.
[Evidence and remaining limits](FLUXER_LIVE_PAIR.md),
[real-account screenshots](../screenshots/development/2026-10-24-fluxer-live-pair/README.md).

**Live account checkpoint, 2026-10-24:** browser-approved sign-in succeeded on
Retroid with an email-verified test account. Two opt-in Android checks passed:
encrypted session restore in a new process/across activity recreation, and real
directory access. Opening Lobbies automatically joined the shared community;
membership was also visible in Fluxer's web client. The directory contained no
compatible rooms. This does not verify room publication, chat exchange, friends,
logout, physical gamepad input or online gameplay. The public APK and shared
service were not updated. [Evidence and reproduction](FLUXER_LIVE_ACCOUNT.md).

**Temporary room contacts** now connect the existing journal/negotiation actor to
admitted members, verified accounts and private messages. My room offers Keep as
friend and cleanup status. Manual Friends actions coordinate with automatic
ownership before their fresh GET/write, including delayed callbacks and full-tag
requests. A Keep acknowledgement cannot race a pending creation. Current-room
messages and directory replies have priority over unrelated recent conversations.
The final checkpoint passes **279 JVM tests and 34 Retroid Android checks**, plus
combined Debug builds/lint. [Implementation, two captures and limits](LOBBY_CONTACTS.md).
An initial disposable-mail registration failed. A later ordinary-mail test
account completed verification and the later pair checkpoint above covers real
friends/private rooms; the later non-friend checkpoint above verifies cleanup and Keep.
No release, server deployment, credentials or
private account material published.

**My room** now follows the directory: create an invite-only/open/approval room,
invite friends, accept/decline, approve requests, remove participants, leave/close
and share a credential-free locator with explicit join confirmation. The native
screen is bound to the room authority and provider DM transport. Readiness is
exact-game/generation gated; production online adapters remain empty, so Ready
is disabled rather than pretending a game is prepared. Public publication waits
for a correlated bot result and respects the AbyssME account quota. The server
result extension remains in PR #260, not deployed. The earlier room checkpoint passed 265 JVM,
32 Retroid Android and 19 shared-service checks, plus combined Debug builds/lint.
Room/provider data are fixtures; real two-account and physical-controller acceptance
remain open. [Implementation and limits](LOBBY_ROOMS.md),
[five labelled captures and exact artifacts](../screenshots/development/2026-10-24-lobby-rooms/README.md).

**Lobbies** now has a native directory screen with search, free-seat filtering,
expiry and account/error handling. First denied access automatically attempts to
join the verified shared lobby community; retry is explicit after a failed or
uncertain attempt. TrainerOS v1 and AbyssME v2 rooms are isolated. The updated
owner decision preserves 32 TrainerOS listings and one independent AbyssME room
per account, superseding the original shared single-room quota. The shared bot extension is
[TrainerOS PR #260](https://github.com/EriArk/TrainerOS/pull/260), not deployed.
Production game-adapter capabilities remain empty: game-free v2 rooms can appear,
with admission now handled by My room; no playable-game claim is enabled. The
initial directory checkpoint passed 222 JVM and 28 Retroid Android checks plus
15 shared-service tests. That suite used fixture memberships/rooms; the separate
live checkpoint above covers actual admission and an empty directory only.
[Evidence and limits](LOBBY_DIRECTORY.md),
[five device captures](../screenshots/development/2026-10-24-lobby-directory/README.md).

**Fluxer account** is now reachable from the Library menu and Settings in development
builds. It uses the official browser approval-code flow, remembers a verified
session in Android Keystore-encrypted, no-backup storage, and provides retry,
sign-out and explicit device-only removal. No app password form, embedded web
login or account requirement for single-player. Light/dark layouts and rotation
were checked on Retroid. [Account implementation and verification](FLUXER_ACCOUNT.md).

**Private text chats** are implemented in development: existing DM/group lists,
search, history paging, per-conversation memory-only drafts and explicit sending/
retry. Narrow screens use list-to-conversation navigation; wide landscape uses
two panes. Blocks, expired sessions, lost send responses and keyboard resize are
handled. [Chat implementation, evidence and limits](FLUXER_CHATS.md),
[fixture-labelled device screenshots](../screenshots/development/2026-10-24-fluxer-chats/README.md).

**Friends** now share the same account and chat screen: searchable Friends,
Requests and Blocked lists; add by full tag; accept/decline/cancel requests;
remove, block/unblock and open a direct conversation without sending a message.
Destructive actions require confirmation and a fresh relationship check. Lost
mutation replies trigger reconciliation, never automatic replay. Chat drafts
survive switching to Friends and back. [Implementation and limits](FLUXER_FRIENDS.md),
[fixture-labelled screenshots](../screenshots/development/2026-10-24-fluxer-friends/README.md).

The first Android Lobby Lite foundation reuses TrainerOS's temporary-friendship
protocol: admitted host/member pairs, account-private recovery journal, Keep as
friend and cautious bilateral cleanup. The host session model fences stale Ready
and preparation callbacks and produces a single launch attempt. A separate
L2CAP packet channel preserves message boundaries over a supplied ordered stream.

The room metadata and temporary-contact workflow are bound to account/UI/DM
delivery with Keep and manual-friend interlocks. See [contact evidence and limits](LOBBY_CONTACTS.md).
Virtual Bluetooth discovery and real online games remain unwired.
**Online multiplayer is not available.**
Existing physical Bluetooth and single-player launch paths are unchanged.
187 JVM tests and twenty-one Android instrumentation checks passed in the friends
checkpoint on Retroid, including a live anonymous Fluxer handoff/poll/cancel path.
Chat/friend device tests use an in-memory provider, not real contacts or message recipients.
Actual account approval and authenticated session restore now have the separate
live evidence above. Logout, chat exchange, friends and two-account gameplay have
**not** been verified live. Earlier account state-machine checks use fixtures;
the packet tests use loopback. A local Debug APK was installed separately; no public APK, bot,
server or Nextcloud deployment was changed. [Implementation, evidence and next
steps](MULTIPLAYER.md). Issues #34-37 remain open.

## Released in 0.5 Beta 1

The 0.5 release promotes the verified Java/Mophun enhancements from Debug-only
development to optional Release controls, while preserving existing defaults.
This includes xBRZ, CRT EasyMode, FSR, frame interpolation, Mophun CPU budget,
enhanced capture, Integer scaling and the shared Java cadence/input/import fixes.
Explicit experimental settings imported from a Debug profile now apply in
Release too. Symbian/N-Gage is retained without these new GPU options.

The following development sections record checks made before this promotion;
their Debug-only and unreleased wording describes those dated checkpoints,
not an additional feature gate in the 0.5 release. Signed upgrade verification
preserved all 327 readable external files on Retroid; internal data and two
inaccessible Mophun directories were not hashed. Java xBRZ, Mophun CRT and native
Snakes were checked in the signed app. No universal 60 FPS or complete-game claim.
See [release notes](releases/v0.5.0-beta.1.md).

## Development Evidence: Pixel-Aligned Scaling

Java and Mophun now offer **Display > Picture size > Integer**, also available
in Java's advanced scale selector. It uses the largest whole source-pixel
multiple that fits the available game area; if 1x does not fit, it falls back to
Fit downscaling instead of cropping. Filters, FSR, LCD effects, guest resolution
and emulation speed remain separate. Percentage scaling is inactive in Integer
mode. Existing modes/defaults and Symbian's native scale selector are unchanged.

Mophun's Canvas/GPU picture and LCD mask now share pixel-aligned geometry in
Integer mode, including odd sizes and rotations. Retroid checks include exact
GPU/Canvas pixel comparison, LCD boundaries, Java geometry/lifecycle, profile
serialization and live Deep Abyss touch layouts. This is unreleased source and
a locally installed Debug APK, not a public release update.
[Evidence and limits](checks/2026-10-23-integer-scaling.md).

## Development Evidence: Experimental Enhancements

xBRZ now performs expensive corner analysis at guest resolution and reuses the
decisions during enlargement, instead of repeating that work for every output
pixel. Retroid's paired captured-frame cost fell from about 44.2 to 6.9 ms;
this is GPU processing cost, not a claim of six times the game FPS. The selected
Deep Abyss frame matches exactly; synthetic comparisons differ by at most one
RGB code value. Defaults, CRT EasyMode and the Debug-only boundary are unchanged.
[Optimization checks and limits](checks/2026-10-23-xbrz-two-pass.md).

The owner selected **xBRZ and CRT EasyMode** from the standalone audition.
Both are now per-game presets in the Debug Java/Mophun Enhancements page, with
Try / Keep / Undo. Selecting either disables FSR, interpolation, smoothing and
LCD decoration without changing size, rotation, CPU/speed or controls. The
selected shader sources/notices are bundled; other audition shaders and game
captures remain local. Retroid verification covers GLES2 output, crop/rotation,
surface recovery, menu previews and persistence. Symbian and public release
APKs are unchanged; this is not a full-game compatibility or smoothness claim.
[Integration evidence and limits](checks/2026-10-23-retro-shader-integration.md),
[original 11-case audition](checks/2026-10-23-retro-shader-audition.md).

An owner visual-review set now compares the same frozen Deep Abyss first-level
frame through five Retroid GPU configurations: Pixel sharp, Smooth and FSR at
0/50/100% sharpness. Labelled full-frame sheets and matching crops remain local;
no commercial artwork is added to Git. Ten targeted Retroid checks passed.
These are diagnostic composites, not evidence of complete-game acceptance or
motion quality. Runtime/release gates are unchanged; owner visual review remains
open. [Reproduction and limits](checks/2026-10-23-visual-review.md).

Small sprites on verified uniform backgrounds can now occupy their intermediate
positions, including across flow-tile boundaries. Static HUD details remain fixed;
ambiguous directions, conflicting neighbors and occlusion prefer current real
pixels. This is bounded sparse-motion recovery, not general object segmentation.
Mophun GPU surface destruction also invalidates presentation/capture readiness and
old frame acknowledgements, preventing stale requests across recreation. The local
Debug build passed 102 selected Retroid checks, 49 emulator GPU checks and 75 JVM tests. No public APK
update. [Evidence, cost and limits](checks/2026-10-23-sparse-motion.md).

The shared Java/Mophun interpolator now uses one pixel-rounding decision for
both source frames, fixing wrong intermediate pixels on odd displacements.
It rejects nearly equal opposite-direction matches in repeating patterns instead
of guessing a direction. Live presentation also refreshes its interpolation
phase after motion-pass preparation, skipping a transition that has expired.
Both shader defects reproduced on Retroid before repair. The final local Debug
build passed 94 selected Retroid checks and 75 JVM tests; Deep Abyss reached its
first-level introduction with effects active. No end-to-end latency or complete-
game acceptance is claimed. [Evidence and limits](checks/2026-10-23-motion-rounding.md).

Debug builds now have a per-game **Display > Enhancements** page using Try /
Keep / Undo. Java OpenGL ES presentation supports an AMD FSR 1.0 EASU/RCAS
adaptation and a separate motion-estimated intermediate-frame prototype. Mophun
now has an optional GLES presentation adapter using the same pipeline and load
protection, with its existing Canvas output retained as the fallback.
The Debug Enhancements page now offers **Pixel sharp / Smooth / Enhanced (FSR)**
image presets and a separate Fine tuning page. Existing custom values are inferred,
not migrated or overwritten; motion interpolation and CPU/speed remain independent.
Same-frame GPU comparisons confirm that FSR can soften pixel art, so it is not
automatically enabled as a universal visual upgrade.
Mophun screenshots and automatic missing artwork can now capture that enhanced
image, or retain source pixels through the per-game Enhanced / Original choice.
Rotation is preserved without capturing control overlays; existing user-picked
artwork keeps priority. Capture does not advance interpolation history. Unavailable
GPU capture falls back to a completed source snapshot taken at the shutter.
Mophun supports an independent 100-300% instruction-slice budget; this is not a
calibrated phone CPU frequency. Java/ART has no emulated phone CPU clock.

All image effects default off. Release builds hide the page and ignore these
experimental runtime values, including imported profiles. No new APK was
published. No Symbian CPU change or Symbian GPU enhancement path is included.
Interpolation uses a source-paced presentation window capped at 50 ms and can introduce artifacts;
it does not improve logic/input rate. No universal 60 FPS or automatic game
enhancement profile is claimed. Retroid is available again. The existing public
Beta 3 Revision 1 release page was rechecked on October 23; no release mutation.

[Implementation and limits](EXPERIMENTAL_ENHANCEMENTS.md),
[initial device evidence](checks/2026-10-23-enhancements.md),
[latency, artifact and GPU refinement](checks/2026-10-23-enhancement-refinement.md),
[guest-buffer unlock and coherent submission checks](checks/2026-10-23-enhancement-upload.md),
[fused final sharpening](checks/2026-10-23-enhancement-presentation.md),
[EASU flat-region optimization and software-renderer fallback](checks/2026-10-23-easu-flat-regions.md),
[interpolation artifact guards](checks/2026-10-23-motion-guards.md),
[source-paced interpolation and submission timestamps](checks/2026-10-23-interpolation-cadence.md),
[interpolation load protection](checks/2026-10-23-interpolation-load.md),
[Mophun GPU presentation integration](checks/2026-10-23-mophun-gpu.md),
[enhanced/original screenshot and artwork export](checks/2026-10-23-mophun-capture.md),
[image presets and sharpness comparison](checks/2026-10-23-image-presets.md).

The enhanced Java path now releases the guest-buffer lock after texture upload;
motion/FSR work and presentation no longer hold it. Submitted geometry and frame
identity are captured together, without an extra CPU image copy. This allows
guest drawing/input to continue during effect processing, but does not remove
driver upload stalls or prove a game-wide FPS/input-latency gain.

Final sharpening now shares the Canvas presentation draw rather than using an
extra full-output texture and copy. The equations, guest resolution and effect
defaults are unchanged; quality is checked against the retained texture-output
path. This saves about 2.64 MiB of RGBA target storage at 720x960, not a promise
of an equivalent FPS increase.

EASU now avoids redundant sample-coordinate work and skips full filtering where
the nearest four colors are exactly identical. The hardware shortcut preserves
the original deringing result; software renderers use a branchless equivalent
to avoid a measured SwiftShader slowdown. Gains depend on scene content, and
detailed images can have a small overhead. This remains Debug-only development,
not a claim of higher guest FPS or universal GPU performance improvement.

Interpolation now checks diagonal patch details, rejects unsupported border
matches and verifies both motion endpoints before warping. A small GPU-only
scene-cut guard prevents accidental local matches from leaving ghosts after
broad transitions. New/disappearing pixels retain the current real image when
the block motion cannot explain them. These are shared image-based rules, not
game-specific fixes. Conservative fallbacks and the four-pixel search limit
remain; this is still an opt-in Debug prototype, not artifact-free frame generation.

Intermediate timing now follows the completed guest frame, not its later GPU
upload. At stable 20/25/30 FPS, interpolation spans the source interval instead
of accelerating then holding. The delay budget is now up to 50 ms rather than
33.3 ms; this is an explicit smoothness/latency tradeoff, not faster input or
game logic. Late rendering consumes the existing window, and variable cadence
does not rewind the presentation timeline. Real GPU stalls, sparse motion and
frame drops remain limits; no measured input-to-photon improvement is claimed.

Under detected rendering pressure, interpolation now temporarily yields to real
frames and stops its extra redraw chain. It returns after a cooldown and healthy
submissions, without changing the user's FSR/sharpness or saved settings. A
zero-timeout EGL completion probe supplements CPU submission timing where EGL
1.5 is available; unsupported devices keep CPU-only protection. This is not a
precise GPU timer or a guarantee against every slowdown. Effects remain opt-in
Debug development; no public APK update.

## Development Evidence: Twenty-game Java Sample

An Android-emulator sample of 20 supplied Java variants exposed shared problems:
short GameCanvas button taps could disappear between polls, interrupted prepared
installations could be reused despite empty files, and Canvas controls could
remain over native LCDUI menus. These paths have been corrected without
game-title exceptions. Reimporting the original source preserves the existing
game's settings and RMS data.

The sample includes MIDP 1.0, ordinary Canvas, GameCanvas, Nokia APIs and four
M3G-reference games. Checkpoints range from startup menus to running scenes;
this is not a claim that all 20 games are fully compatible. Fixed-resolution
variants still expose auto-size limitations. Retroid is unavailable, and audio,
full-game saves and physical-controller acceptance were not established.
See the [fixture matrix, fixes, checks and remaining findings](checks/2026-10-20-java-game-sweep.md).
No public APK update; these improvements are unreleased source.

## Development Evidence: Java Frame Cadence

Corrected accumulated scheduler oversleep in the shared Java frame limiter and
a lost-redraw race in parallel Canvas presentation. No new CPU-frequency setting,
default renderer change or guest-clock acceleration. The Retroid collection's
Metal Slug Mobile Impact has its own approximately 10 Hz loop; these corrections
do not remove that game-owned limit or claim 60 FPS support.

Fresh checks: 49 JVM passes and 37 Android 15 phone-emulator passes, including
live frame pacing, Canvas lifecycle and guest clocks. Retroid is unavailable.
See [fixture identity, before/after evidence and limits](checks/2026-10-20-java-frame-cadence.md).
No public APK update in this checkpoint; the release availability above was last
verified on 2026-10-19, not rechecked live in this investigation.

## Released in Beta 3 Revision 1: Fast-forward

Published as an update to Beta 3: temporary per-game 150/200/300/400% speed,
controller Hold/Toggle mapping including trigger axes, and optional touch
shortcut/direct game-menu command across all three engines. Original speed and
frame cap return on stop; activation is not persisted. Settings use Try/Keep/Undo.
Native clock/audio and Retroid shared checks passed, with live Java/Mophun/N-Gage
shortcut flows and phone-emulator checks. Final signed APK upgraded Retroid from
308 to 309; all 329 readable external-file hashes matched and the library reopened.
See [revision verification](releases/v0.4.0-beta.3-r1-checks.md) and
[behavior and limits](FAST_FORWARD.md). Requested 4x is not guaranteed gameplay
acceleration; Little Big City and Green Farm were not individually verified.

## Released in 0.4.0 Beta 3

**Published 2026-10-19:** signed `0.4.0-beta.3`, code 308,
combines the library, LCD/export and portable-title work below. Clean release,
APK guards and lint passed; 39 fresh JVM checks passed. Installed over signed
Beta 2 on Retroid, with all 329 readable external-file hashes preserved before
launch; the 40-game library reopened. Fresh phone-emulator checks: 21 passes,
one optional capture skip. The final committed-source APK also passed build,
guards, lint and 39 JVM checks, then installed over the candidate with all 329
readable file hashes retained. Published with three matching source bundles and
checksums. [Release notes](releases/v0.4.0-beta.3.md),
[evidence and limits](releases/v0.4.0-beta.3-checks.md).

**Portable native titles:** custom Symbian/N-Gage
titles now travel in phone backups, stay independent between restored phones,
and survive rescans and subsequent exports. Existing local names migrate;
older backup formats still restore. New version-3 archives require the updated
app on the receiving device. Retroid: 21 backup/artwork checks and the complete
restore/launcher-title/native-startup UI flow passed. See
[transfer evidence](checks/2026-10-19-phone-titles.md). These checks preceded
publication and used the isolated debug package, not the signed APK.

**Library navigation and card editing:**
Recent/Favorites now combine with a persisted platform filter. Library navigation
remembers a stable game/scroll anchor; Play last starts the latest recorded game
across platforms without claiming save-state resume. Java/Mophun favorites are
available directly in touch game options. Edit card groups title and artwork
actions across engines; native title overrides survive rescans without changing
guest files. Async filtering and late native-selection restoration were corrected.
The later [title transfer follow-up](checks/2026-10-19-phone-titles.md) adds native
title overrides to phone backups; both changes are included in Beta 3.
Retroid and phone-emulator checks/captures are documented in the
[library checkpoint](checks/2026-10-19-library-session.md).

**LCD effects and screenshot export:** included in Beta 3, not in Beta 2.
LCD Soft / LCD Classic effects now work on Java Canvas, Mophun and Symbian/N-Gage
presentation, with Off by default. Display offers one intensity slider, live
Try / Keep / Undo and per-game persistence. The original GPU optical mask follows
guest pixel size and rotation, excludes controls/letterboxing, and fades its grid
at small scales. No renderer replacement, frame history or GPU readback.
Retroid Mophun/native and emulator Java UI trials passed; each environment passed
34 focused checks. A short Mophun menu presentation sample showed no additional
jank, not a universal performance guarantee. A follow-up replaces the minified
repeating texture with pixel-aligned lines to remove uneven grid bands, and adds
per-game Clean / With LCD effect screenshot export. See the separate
[export and grid correction checkpoint](checks/2026-10-19-lcd-export.md) for its
verification and limits. No website or Nextcloud deployment in this release.
[Behavior](SCREEN_EFFECTS.md), [evidence](checks/2026-10-19-lcd-effects.md),
[captures](../screenshots/development/2026-10-19-lcd-effects/README.md).

## Released in 0.4.0 Beta 2

**2026-10-18: published in 0.4.0-beta.2, code 307.**
The signed APK passed an in-place Retroid upgrade and touch-layout persistence
checks. All 329 readable external files retained their hashes before first launch.
Fresh checks: 39 JVM passes, 66 Retroid and 36 emulator instrumentation passes
(one optional capture skipped on each device). Signed and debug evidence remain
separate. [Release verification](releases/v0.4.0-beta.2-checks.md) and
[fresh screenshots](../screenshots/releases/v0.4.0-beta.2/README.md).
No website or Nextcloud deployment. Earlier local-only checkpoints below are
historical evidence; these changes are now included in the public APK.

- **Visual styles:** Classic, Retro LCD and High contrast, independently of
  Light / Dark / System brightness. Shared palette roles cover the library,
  menus, mapper, installer and modern touch controls. Retro adds LCD colors and
  inset borders; High contrast uses flat controls and stronger outlines. Game
  pixels and imported artwork are unchanged. Engine activities refresh the main
  process appearance snapshot instead of relying on stale process preferences.
- **Touch layout editor:** Controls > Edit touch layout opens a detached preview.
  Choose Phone/Gamepad, preview either orientation, drag or nudge control blocks,
  resize them, and select button theme/opacity. Layout and orientation placements
  are separate and per-game. Cancel discards the draft; Use layout feeds the
  existing Try changes / Keep / Undo workflow. Controls remain in their lanes,
  outside the game area. Existing profiles without placements retain their layout.
- **Compact library:** near-square galleries can fit two complete artwork rows;
  header filters adapt to font size and remain available through Collection in
  the menu. Compact list rows and controller-only hints reduce wasted space.
  Selection is retained when rebuilding the view. The editor also adapts its
  split layout to available space and the user's text size.
- **Symbian/N-Gage artwork:** native game actions now expose the same Artwork
  menu with separate Icon / Cover selection and Use automatic. Overrides are
  stored by phone-system identity and game UID, outside generated artwork, so
  index refreshes and renaming do not replace them. Picker results support native
  negative IDs and activity recreation.
  [Implementation and checks](checks/2026-10-18-symbian-artwork.md).
- **Artwork in phone backups:** custom Symbian/N-Gage covers and icons now
  travel with each virtual phone. Restore keeps them inside the new phone's
  staged directory and binds them to its new identity; originals are unchanged.
  Re-export preserves edits and resets. New version-2 archives require the new
  implementation; existing version-1 backups still restore without artwork.
  Images and archive contents are validated before publication.
  [Verification and limits](checks/2026-10-18-symbian-artwork-backup.md).

Builds, automated checks and Retroid/emulator UI evidence are recorded in
[the visual polish checkpoint](checks/2026-10-18-visual-polish.md).
These results do not establish physical phone acceptance, full-game compatibility
or manual controller comfort. The old Java legacy keypad editor is unchanged.

## Released in 0.4.0 Beta 1

The following implementation checkpoints were originally recorded before
publication; their "no release" statements refer to those individual checks.
All changes in this section are now included in 0.4.0-beta.1.

October 18: prepare **0.4.0-beta.1**, code **306**, as one adaptive APK rather
than separate Handheld and Phone downloads. The primary `fdroid` artifact keeps
`io.github.eriark.abyssme`; library/settings screens no longer lock landscape.
Existing per-game orientation, mapping and presentation choices remain intact.
Connected controllers retain library focus behavior and compact fullscreen menus.
The legacy `phone` flavor remains for migration/testing, not public distribution.

All three engines share per-game **Control mode: Auto / Touch / Controller** and
**On-screen buttons: Auto / Always / Off**. Automatic visibility follows attached
gamepad/joystick devices, including built-in controls, without treating a mouse
or keyboard-only device as a gamepad. Hiding controls returns their screen area
to the game. Reconnection never automatically resumes a disconnected session;
removal releases input, cancels Select tracking and opens/defers the game menu.
Legacy Java touch cancellation also releases guest keys without activating its
menu key. Existing Java MIDlet lifecycle pause semantics remain unchanged.

**Library transfer > Move from AbyssME Phone** links the backup migration paths.
Java/Mophun and Symbian whole-phone backups remain separate. Original installs
are left intact; app-wide preferences and old Phone launcher shortcuts do not
transfer. No automatic access to another Android package's private files.

Combined and base unified debug builds, controller/overlay regressions, backup
round trips and real Java/Mophun/Snakes menu workflows were checked. The signed
release rehearsal now also passed the in-place code-305 upgrade and menu-driven
Phone Java migration: 324 pre-launch file hashes retained; both migrated RMS
files and all 55 previous profile values preserved. Physical Bluetooth/USB
unplug/replug remains unverified. Publication is recorded above.
[Signed release rehearsal](releases/v0.4.0-beta.1-checks.md).
[Verification, captures and limits](checks/2026-10-18-unified-apk.md).

October 18: replace the full-width settings preview strip with a compact
approximately 176x52 dp overlay shared by Java, Mophun and Symbian. A stable
countdown sits beside save/undo icons with 48 dp touch targets, accessible names,
tooltips and controller focus styling. The panel stays dark in either app theme,
does not resize the game or steal its focus, and leaves the shortcut row clear.
Keep/Undo remain available through the game menu as well.

Retroid and the Android emulator each passed 32 UI/settings checks, including
240x240 layouts and font scales up to 2x. Two real Java/Mophun display-trial
workflows passed on Retroid, with portrait/landscape captures inspected.
Both combined debug variants built; no new beta or APK distribution.
[Evidence and limits](checks/2026-10-18-compact-preview.md).

October 18: fix Java resize notification ordering when a frame is already queued,
coalesce stale resize notifications to the current dimensions, and defer pending
size callbacks while the game is in the background. On return, the game receives
its new size before show/paint, including GameCanvas buffer resizing.
Also fix GLES screenshots that could be black despite a visible game frame:
capture now explicitly renders before reading pixels and retains the captured
dimensions through conversion.

Three queue regressions and one screenshot regression failed before their fixes.
Afterward, Retroid and the Android 15 emulator each passed 61 checks. A real
Age of Empires II Deluxe workflow passed four resolution trials, Home/return,
game-image capture and Undo without restarting. Inspected captures show startup
and in-game pause screens, not gameplay acceptance. Both combined debug variants
built; no beta or APK distribution was performed.
[Evidence and limits](checks/2026-10-18-resize-resume.md).

October 18: preserve the last Java game frame when changing scale, rotation or
smoothing without changing the game's logical resolution. These host-only
changes previously called the game's paint callback and could overwrite a frame
submitted through direct-flush APIs with stale or empty graphics. Actual guest
resolution changes still receive resize/repaint callbacks. This is a shared
renderer fix, not a title-specific workaround or a claim that all black-screen
startup problems are solved.

Both new regressions failed before the fix. Afterward, 56 checks passed on
Retroid and 56 on the Android 15 emulator, covering software, GLES and hardware
surface rendering. Two real Java/Mophun display-trial workflows passed on
Retroid; the Java capture shows its own pause screen, not gameplay acceptance.
Both combined debug variants built, 38 JVM tests passed per variant and lint
reported no errors. No new release or APK distribution.
[Evidence and limits](checks/2026-10-18-display-presentation.md).

October 18: open Mophun games directly from **ZIP or 7z**, without extracting
them manually. Archives containing several JAR/MPN games use the existing game
chooser; only the selected payload is written out. Single-game archives skip
the chooser. The combined Symbian build now routes Java/Mophun 7z archives to
this installer instead of the native-game importer. Reimporting the same MPN
reuses its installed card and leaves saves/settings intact.

Bounded extraction rejects unsafe/duplicate paths, damaged inputs and oversized
payloads. Retroid passed 25 import/routing checks plus 31 existing folder/transfer/
source-alias regressions; the Android 15 emulator passed 17 import checks.
A real mixed ZIP on Retroid reached the selected Mophun game's rendered menu.
Both combined debug variants built; this is not full-game compatibility or a
public APK update. Nested archives, bulk installation and password-protected
archives are not added. [Evidence and limits](checks/2026-10-18-game-archives.md).

October 18: **Add folder** now discovers loose Mophun `.mpn`, Java `.kjx` and
standalone `.jad` files alongside JAR/ZIP/7z. Same-name JAR/JAD pairs produce one
entry. Scanning reports inaccessible subfolders and traversal limits instead of
quietly reporting an empty/successful scan. The completion count comes from the
committed database transaction, distinguishing new games from existing entries.
Rescans preserve installed cards, favorites, artwork and play history. Mophun
installation promotes its discovered card and remembers validated source copies;
it does not leave a second pending card or change installed saves/profiles.

Both Java/Mophun and combined debug variants built. Retroid and the Android 15
Phone emulator each passed 26 focused folder/import checks. The emulator also
passed system-picker folder selection, recursive MPN discovery, installation,
visible game-menu launch and repeat selection without a new pending card.
That earlier folder-only batch did not add archive extraction (now covered above), Symbian loose-file indexing, firmware
discovery, automatic folder watching or duplicate detection before reading game
bytes. [Evidence and boundaries](checks/2026-10-18-game-folders.md).
No new beta or distribution APK was published.

October 18: shared Java media/tone playback, session-owned Mophun MIDI and
Symbian's common PCM output now follow per-game emulation speed. Tempo and pitch
change together; this is not pitch-preserving time stretching. Changing settings
does not resume paused/prepared audio, and a rejected backend rate cannot block
return to normal. Java's MMAPI time base follows the guest clock; native media
positions account for previous speeds and paused output rather than jumping.
Normal 100% playback is preserved. No extra setting, title-specific timing rule,
newly enabled Mophun PCM support, save mutation or replacement release asset.

Both combined debug variants built; 32 JVM checks per variant passed and lint
reported zero errors (403/410 warnings remain). Retroid and the Android emulator
each passed 36 audio, guest-clock, FPS and settings checks; native helper suites
also passed on Retroid. Five live UI checks passed, including Deep Abyss/Snakes
speed trials and Snakes restart/background return; two opt-in fixtures were skipped.
Audio evidence is
measured playback position/duration and waveform output, not listening approval.
[Implementation, runtime checks and limitations](checks/2026-10-18-audio-speed.md).
These changes are not in the signed beta.6 downloads.

## Changes Released in Beta.6

October 18: Mophun and Symbian/N-Gage now expose **Frame rate limit** in
**Game menu > More** and **Display**, matching Java's presets: Unlimited,
15, 20, 25, 30, 50 and 60 FPS. Changes apply live through Try / Keep / Undo
and persist per game (also per virtual phone for Symbian). Old native profiles
retain 60 FPS. Java's existing saved limit remains independent.

Mophun uses the shared monotonic pacer instead of a second hidden 60 FPS cap.
Symbian applies the limit to swaps, window redraws and direct-framebuffer
polling; Unlimited avoids division by zero and unnecessary guest sleeps.
This controls frame pacing, **not emulation speed in percent**, and does not
promise that every game will reach the selected FPS or fix every fast game.
Both debug variants built, shared tests passed on Retroid and the Android
emulator, and Retroid live-menu checks passed in Deep Abyss and Snakes.
[Evidence and exact boundaries](checks/2026-10-18-native-frame-rate.md).
Included in beta.6; the older beta.5 APKs expose this setting only for Java.

October 18: separate per-game **Emulation speed** presets
50 / 75 / 100 / 125 / 150 / 200% are implemented for Java, Mophun and Symbian.
More and Display use live Try / Keep / Undo. Java's original installed files
and saves stay intact while guest timing hooks are prepared in private cache.
Resource-only imports need the original JAR to enable this control.
Retroid and Android 15 emulator each passed 29 timing/settings checks; Retroid
live menu trials passed in Java, Deep Abyss and Snakes. Native clock/FPS tests
also passed. **Audio remains at normal tempo**, and busy-loop/frame/audio-paced
games need not follow the percentage exactly. This is not a universal speed
fix. [Implementation evidence](checks/2026-10-18-emulation-speed.md).
Signed Beta 6 publication and upgrade checks are tracked in the
[release record](releases/v0.3.0-beta.6-checks.md). Final signed Handheld and
Phone launch/settings checks passed on Retroid. Its official Handheld is now
code 305; the temporary Phone installation was removed with its data retained.
All 323 readable external Handheld files kept their pre-upgrade hashes before
launch; protected/private files were not independently hashed.

October 18: bounded native Symbian file reads, section reads and writes by the
guest buffer and file length; removed an unsafe beyond-EOF write and retained
partial-read byte counts. Application lookup and launch now share the guest
kernel lock. These are shared engine changes, not title-specific settings.

Both combined debug variants built; 29 JVM tests per variant and lint passed
(zero errors, existing warnings remain). Retroid passed 86 native transfer
boundary cases, byte-transfer checks and the existing capture/card checks.
Snakes retained a visible title screen across a menu restart and returned to the
library. Native Asphalt 3 reached a race, but the same test caught a blank frame
after restart. **Asphalt cold/repeated launch stability is still unresolved.**
Audio, save round trips and complete-game compatibility are not claimed.
[Exact changes, checks and limitations](checks/2026-10-18-symbian-file-transfers.md).
Included in the combined beta.6 APKs. Debug artifacts remain local.

## Changes Released in Beta.5

Beta.5 repairs a reproduced Java startup deadlock: games
waiting for Canvas visibility inside startApp() can now proceed. Age of Empires
II Deluxe 02.00.35 reached Quick play on the Phone emulator and Retroid; physical
Retroid touch navigation, pause/resume and exit were checked. Six startup cases
and 21 existing Canvas cases passed on the emulator. Real Mophun Phone appearance
and Snakes shared-control UI checks passed on Retroid. This is not Android 16,
Deluxe 02.00.39, audio/save or full-campaign acceptance.
[Cause, regression coverage and limits](checks/2026-10-18-java-startup.md).

October 18: fixed shared Java drawing-buffer and GLES lifecycle problems during
live screen changes. Improved automatic setup for explicit LG width/height and
Nokia original-versus-target resolution declarations. Existing saved settings
stay unchanged. Transfer now retains chosen folders and pending access requests
across Android process recreation, without silently repeating an import.
Symbian session callbacks and background pause handling were hardened.
Stable-size Java GLES frames now reuse texture storage instead of reallocating
it every frame; a measured performance improvement has not been established.

Both base and combined developer variants built with zero lint errors. The final
Phone emulator suite passed 98 selected checks. A subsequent physical Retroid
run passed 100 checks including the final picker-result and texture-reuse changes.
Snakes passed two native UI checks, including three consecutive restarts and
Home/return cycles with guest-picture capture. These are development results,
not new release APKs or full-game
compatibility claims. The existing frame limiter gained additional actual
Canvas/GameCanvas-path checks, not a universal game-speed setting.
The emulator also recovered a real DocumentsUI save-folder selection after
process death and returned to review with saves included, without auto-importing.
Bounce Tales completed live size trials/Undo, restart and exit in the final Phone
emulator build and combined Handheld debug build on Retroid; inspected game
images remained visible. No full-playthrough claim.
[Details, local fixtures and limitations](checks/2026-10-18-runtime-transfer.md).

Phone touch controls now offer **Button theme: Follow app / Dark / Light** per
game, independently of the app's appearance. Top game shortcut buttons follow
the same selection; settings dialogs retain the app theme. The standard Phone keypad has
raised key faces, larger digit labels, alphabet legends and tactile marks on 5;
landscape navigation uses a connected D-pad. Gamepad controls share the theme
choice. Existing profiles keep Follow app; Legacy keyboard colors and Handheld
defaults are unchanged. These changes are included in beta.5, not beta.4 APKs.
[Verification](checks/2026-10-18-phone-touch-appearance.md).

Phone's top game shortcuts now open from a small edge arrow, overlay the game
and collapse after five seconds without use. The previous portrait shortcut-row
reservation is removed; normal gameplay taps do not open the panel. Handheld
behavior is unchanged. This is included in beta.5.
Final verification: 43 selected Phone emulator checks, 34 shared-component
checks on Retroid, and a real Phone settings/relaunch/auto-hide UI case passed.
[Fresh development captures](../screenshots/development/2026-10-18-phone-controls)
show both touch layouts and orientations; they are not public-beta screenshots.

## Changes Released in Beta.4

October 17: **Library menu > Cover format** offers Automatic, Wide and Portrait.
Gallery uses aligned cards and fits complete covers, including wide banners,
without rewriting artwork files. Square and near-square screens use the compact
header and a game-focused layout without a squeezed detail pane. This changes
the launcher, not the emulated game's aspect ratio.

**Restart game** is available in the shared Java, Mophun and Symbian game menu.
It asks for confirmation, uses saved settings and discards unconfirmed previews.
It is a recovery shortcut, **not a fix for every black screen after display
changes**. Restart can lose unsaved progress. An old Symbian menu callback that
paused the replacement session was fixed during verification.

Both developer variants built; base JVM suites passed 29 tests each and lint
reported zero errors. Gallery/menu checks ran on Android emulators and the
physical Retroid Pocket Classic. Bounce Tales, Deep Abyss and Snakes returned to
their game menus after restarting on Retroid. This does not establish full-game,
audio or save-roundtrip compatibility. No physical RG Cube was available.
[Checks and limits](checks/2026-10-17-gallery-restart.md),
[development screenshots](../screenshots/development/2026-10-17-gallery-restart/).
The development checks above preceded release packaging. Beta.4 is now published
as a new release; no older APK was overwritten. Final signed-build checks and
their distinct limits are recorded below.

## Changes Released in Beta.3

This is the concise handoff for website copy and announcements. The linked
development checks preserve their original dates, variants and limitations;
their counts are not new final-release test results.

| User-visible change | Evidence |
| --- | --- |
| Light, Dark and Follow system appearance; more readable narrow mapper | [Appearance](checks/2026-10-15-light-theme.md) |
| Phone can hide Display and Screenshot independently while retaining Menu | [Shortcuts and feedback](checks/2026-10-15-phone-shortcuts-feedback.md) |
| Tactile feedback for Phone/Gamepad touch keys across three engines, with Try / Keep / Undo | [Shared touch policy](checks/2026-10-15-phone-shortcuts-feedback.md); human perception remains unverified |
| Java screen startup, switching and pause/resume fixes without per-title exceptions | [Canvas lifecycle](checks/2026-10-15-canvas-lifecycle.md) |
| Original JAR filename size/phone hints supplement metadata without resetting existing profiles | [Automatic setup](checks/2026-10-15-source-hints.md) |
| Per-game FPS presets and monotonic frame pacing | [Java controls](checks/2026-10-15-frame-rate.md), [native controls released in beta.6](checks/2026-10-18-native-frame-rate.md); not a universal speed fix |
| Identical Java imports can reuse existing games/saves or create an intentional separate copy | [Duplicate imports](checks/2026-10-15-source-aliases.md) |
| Better external-launcher cancellation/exit paths and native `.abyssme` launch files | [Beacon](checks/2026-10-15-beacon-launcher.md), [other frontends](checks/2026-10-15-import-launchers.md); ES-DE unverified |
| ROM/RPKG ZIP/7z import, persistent errors and Java version/vendor information | [Import and information](checks/2026-10-15-import-launchers.md) |
| Guarded SIS/SISX removal from the exact virtual phone | [Native uninstall](checks/2026-10-15-symbian-uninstall.md); card-only/N-Gage 2.0 removal unsupported |
| N-Gage cards remain visible beside ordinary SIS games on the same phone | [Mixed-library fix and signed-device check](releases/v0.3.0-beta.3-checks.md) |

## Final Release Evidence and Post Assets

- Current 0.4.0 Beta 2 signed upgrade, touch persistence, build/source and
  download evidence is in the [verification record](releases/v0.4.0-beta.2-checks.md).
  [Five captures](../screenshots/releases/v0.4.0-beta.2/) show Retro Grid, visual
  styles, touch editor, mapper and the Java main menu above the numeric keypad.
  Retroid now has the unified code 307. Original game configuration was restored.
  The [Beta 1 migration evidence](releases/v0.4.0-beta.1-checks.md) remains valid
  as a dated earlier checkpoint, not a newly repeated migration test.
- [Beta 6 evidence](releases/v0.3.0-beta.6-checks.md) and its
  [captures](../screenshots/releases/v0.3.0-beta.6/) remain historical records.
- The following beta.5 checkpoints are retained as historical evidence, not
  relabeled as new beta.6 tests.
- Both signed beta.5 variants built cleanly and passed APK-content guards,
  29 JVM tests each and lint with zero errors (404/411 warnings remain).
- Both final signed variants installed on Retroid. Phone Deluxe 02.00.35
  reached Quick play, accepted touch navigation and portrait preview/Keep,
  and restarted in a new Java process. Its top shortcuts auto-hide without
  moving the game picture. Handheld Deep Abyss reached gameplay; native Snakes
  reached its Single Player menu. Full-game/audio/save acceptance is not claimed.
- At the beta.5 checkpoint, Retroid had Handheld code 304. The
  temporary production Phone package was removed with data retention; isolated
  developer packages were not purged. The Handheld library retained 39 entries
  and all 323 readable external files matched hashes across the upgrade. Two
  protected Mophun directories were not readable; this is not a full save audit.
- [Final APK checks and limits](releases/v0.3.0-beta.5-checks.md) distinguish
  signed-device checks from earlier debug/emulator regression coverage.
- On October 16, 30 more user-supplied Java games were installed on Retroid;
  the reopened library shows 39 entries. Gravity Defied reached its track and
  is favorited. This is device-only provisioning, not a release or a full-game
  compatibility claim. [Collection checks](checks/2026-10-16-retroid-collection.md).
- [Fresh beta.5 screenshots](../screenshots/releases/v0.3.0-beta.5/) contain seven
  real, unretouched final-APK captures for posts and the website. Old promotional
  folders were removed from the current tree; historical links use pinned commits.
  Earlier release/development evidence remains explicitly dated, not relabeled.

Remaining limits: intermittent Ghost Recon startup, native Asphalt cold relaunch,
automatic compatibility choices, broader Symbian support, ES-DE, Samsung Android
16 and human tactile/controller acceptance. Do not announce universal compatibility.

## Historical 0.1.0 Beta.2 Baseline

The older beta has Gallery/List/Grid, search, favorites, folders, per-game
settings, controller mapping, artwork, first-launch compatibility preparation,
and separate Handheld/Phone APKs. Phone provides virtual keys and direct touch.
It accepts JAR/JAD and archive sources through Android's file picker.

An Android launch-intent entry point exists for external frontends. Do not say
ES-DE or Beacon is fully verified: real cold/warm launch and return testing is
still tracked in [#7](https://github.com/EriArk/JavaEmulator/issues/7).
See [beta.2 release notes](releases/v0.1.0-beta.2.md) for the released scope.

## Included in the Current Beta

The 0.4 beta combines phone and controller use in one APK, retaining the shared
Java/Mophun/Symbian menu and embedded native integration. It requires arm64-v8a and
Android 10+. System files can be provided before games; setup explains missing
requirements, retains imports and reuses compatible components. Native games
join the common library. Numeric/Gamepad touch decks, controller mapping,
display controls, screenshots and whole-phone backups share the same app.
See [release notes](releases/v0.4.0-beta.2.md) for user-facing details and limits.

The current feature summary includes retained 0.2/0.3 features and the 0.4/0.5 additions:

| Area | Current development behavior | Evidence / boundary |
| --- | --- | --- |
| Image presets and scaling | Released opt-in Java/Mophun Pixel sharp, Smooth, FSR, xBRZ and CRT EasyMode; separate Integer picture size and enhanced/original Mophun capture | [Options](EXPERIMENTAL_ENHANCEMENTS.md), [signed 0.5 checks](releases/v0.5.0-beta.1-checks.md); Java GLES required, no Symbian GPU integration |
| Experimental motion and CPU budget | Optional Java/Mophun frame interpolation; independent Mophun 100-300% instruction budget, original defaults retained | [Boundaries](EXPERIMENTAL_ENHANCEMENTS.md); not universal 60 FPS or calibrated CPU overclocking |
| Adaptive APK | Auto / Touch / Controller, Auto / Always / Off screen buttons, disconnect pause and menu-based Phone migration | [Implementation](checks/2026-10-18-unified-apk.md), [signed upgrade and migration](releases/v0.4.0-beta.1-checks.md); physical unplug/replug remains unverified |
| Visual styles and touch editor | Classic / Retro LCD / High contrast, brightness independently; visual control-block editing with reversible per-game trials and separate orientation placements | [Implementation](checks/2026-10-18-visual-polish.md), [signed persistence and captures](releases/v0.4.0-beta.2-checks.md); legacy keypad editor unchanged |
| Native custom artwork | Separate covers/icons by virtual phone and UID; overrides survive refresh and whole-phone backup/restore | [Artwork](checks/2026-10-18-symbian-artwork.md), [backup coverage](checks/2026-10-18-symbian-artwork-backup.md); new version-2 archives need Beta 2 or newer |
| Library and small screens | Compact layouts, reachable menus, Gallery covers separate from List/Grid icons, controller focus, custom art and conservative automatic selection | [Compact UI](COMPACT_LIBRARY.md), [artwork](ARTWORK.md); not every tiny-screen/device combination is accepted |
| Controller mapping | Graphical phone keypad, per-game named schemes/presets, button identification, D-pad/stick directions and diagonal-to-single-key bindings, compact mapper pages | `c49fef57`, `ba252272`; physical-controller/disconnect acceptance remains open in #9 |
| Phone controls | Phone numeric keypad or virtual Gamepad, portrait game area above buttons, landscape side controls, size/opacity/position settings. Beta.3 adds per-game tactile feedback in the shared Controls page and feedback from new touch layouts | [Settings](SETTINGS_TRIALS.md), [haptic checks and limits](checks/2026-10-15-phone-shortcuts-feedback.md); perceived phone haptics remain unaccepted |
| Picture settings | Shared Display/Controls pages in library and game, live Java image rotation, size/smoothing, reversible Try/Keep/Undo preview | [Settings trials](SETTINGS_TRIALS.md); expert compatibility settings remain under Advanced |
| Quick presets | More > Screen presets applies virtual resolutions live; More > Phone profile offers Generic/vendor presets through Restart & try, with Keep / Undo | [Implementation and checks](GAME_PRESETS.md); restart loses unsaved game progress, presets are not full phone emulation |
| Release profile/import safety | Stable touch/preparation JSON keys prevent R8 field loss and restart-trial crashes; framed Java exception handlers no longer undercount stack during import | [Fixes and checks](RELEASE_PROFILE_SAFETY.md), [signed Phone upgrade](releases/v0.2.0-beta.1-checks.md); no recovery claim for already-lost values |
| Screenshots and shortcuts | Optional camera button and menu action; PNGs in Pictures/AbyssME. Beta.3 adds More / Playing > On-screen shortcuts, independent Java Phone Display toggle, Menu retained | [Shortcut checks](checks/2026-10-15-phone-shortcuts-feedback.md); existing defaults and camera preference retained |
| Transfer and backup | J2ME Loader import, selection/preview, AbyssME backup/restore through menus, copied saves and supported profiles, non-destructive migrations | [Transfer](LIBRARY_TRANSFER.md); source files remain intact, access restrictions still apply |
| Nested-folder permissions | Import pauses for the requested folder grant and resumes; wrong-folder/cancel recovery, readable messages, save-folder validation | `d39ed742`; pending access survives screen recreation, not process death |
| Installer | Compact scrolling details, persistent errors, archive recognition and mismatched JAD/JAR continuation. In beta.3, identical JARs from unfamiliar URIs offer reuse or a separate copy; confirmed choices are remembered | `696da084`, `7ba384b2`; [#20 checks and limits](checks/2026-10-15-source-aliases.md); no automatic merge of existing duplicates |
| Symbian uninstall | Remove a supported SIS/SISX package from its exact phone, with backup access, dependency/ownership checks and interrupted-operation reconciliation | [Checks and conservative limits](checks/2026-10-15-symbian-uninstall.md); not a hide-entry operation; no automatic rollback |
| Automatic preparation | Weak or conflicting vendor hints retain Generic; anonymous images alone no longer set display size. Existing settings and explicit display declarations remain preserved | [Preset checks](GAME_PRESETS.md), [checks](LOCAL_TESTING.md); static heuristics, not running multiple models or guaranteeing the best profile |
| Automatic game fonts | New profiles follow the prepared virtual resolution; Advanced font presets include Automatic. Existing explicit values are preserved | `9430cb0a`; four Android font tests, readable Constructo tutorial on Retroid |

## Experimental Mophun

The scope below was originally recorded from private builds on 2026-10-11.
On 2026-10-13 the runtime and 25 fixture-free checks were selectively promoted
to `engines/mophun`. Games, preview activities, keys and private diagnostics were
not promoted. Signed-build import and startup checks are recorded in the new
[production report](releases/v0.2.0-beta.1-checks.md). This is not broad platform
compatibility or a claim that the older beta.2 download contains Mophun.

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

Current open work, checked on GitHub on 2026-10-15:

- [#28](https://github.com/EriArk/JavaEmulator/issues/28): shared touch feedback is released in beta.3; perceived vibration on the reporter's phone remains unverified. Shortcut request #27 is closed and its released availability is recorded.
- [#22](https://github.com/EriArk/JavaEmulator/issues/22): frame-rate controls/pacing are improved in beta.3, but the exact fast-game report is not reproduced.
- [#24](https://github.com/EriArk/JavaEmulator/issues/24): Java ME VM research, not an integrated replacement runtime.
- [#15](https://github.com/EriArk/JavaEmulator/issues/15): historical intermittent Ghost Recon black screen; a shared Canvas lifecycle fix is implemented, but the original failure is not yet conclusively resolved.
- [#14](https://github.com/EriArk/JavaEmulator/issues/14): compatibility heuristics need broader validation.
- [#17](https://github.com/EriArk/JavaEmulator/issues/17), [#9](https://github.com/EriArk/JavaEmulator/issues/9), [#3](https://github.com/EriArk/JavaEmulator/issues/3): implemented UI/input/orientation changes still have acceptance or release follow-up; open does not mean no implementation.
- [#12](https://github.com/EriArk/JavaEmulator/issues/12): genuine Light appearance is included in beta.3; reporter/device acceptance remains separate.
- [#7](https://github.com/EriArk/JavaEmulator/issues/7): real external-launcher checks.
- [#4](https://github.com/EriArk/JavaEmulator/issues/4): broader regression coverage. Debug signing setup [#1](https://github.com/EriArk/JavaEmulator/issues/1) was fixed and closed on 2026-10-13.
- [#13](https://github.com/EriArk/JavaEmulator/issues/13): experimental Mophun; [#11](https://github.com/EriArk/JavaEmulator/issues/11): broader themes/skins/customization, not a shipped theme editor.

## Symbian Integration History

On 2026-10-11 the maintainer selected embedded Symbian / N-Gage support inside
the existing application, prioritizing a simple user experience over a quick
external-app handoff. The [source audit and staged plan](SYMBIAN_INTEGRATION.md)
pin EKA2L1 to `11f5b6cd141c0884a130ee45743f1f5e17bb1783`.

**Original planning checkpoint (superseded by the 0.3 integration above):**
The inspected Android bridge already exposes device setup, app lists/icons,
UID launch and input. It needs lifecycle, storage and UI adaptation; our optional
engine hook also needs to support more than one provider without breaking Mophun.
The proposed experience is one-time virtual-phone setup, a shared game library
and backups through the menu. Firmware is user-supplied, not bundled.

**Early checkpoint, 2026-10-13 (superseded by the current availability row):** the arm64 embedded-engine debug APK builds and
installs on Retroid Pocket Classic (Android 14). Four firmware-free checks pass:
asset preparation, repeated native prepare/stop without firmware, separate-
process environment-page reopen/return and safe card-package staging. The fifth,
private firmware/card check is skipped because firmware was not provisioned
during that run. Suitable-format system archives were located afterward in
the user's private Nextcloud folder, without installing them yet.
This is not graphics/audio/gameplay or save acceptance. Original N-Gage and
N-Gage 2 need separate compatible environments and checks; neither is shipped.
The old EPL M3G implementation is now removed in the production-switch batch;
EKA2L1 and its own dependencies still need a separate integration review.

The user supplied Snakes (original N-Gage), Asphalt 3 (`.n-gage`) and an N-Gage
2.0 v1.40.1557 SIS installer. They are ignored local inputs; the platform SIS is
not phone firmware. N-Gage/QD data trees and N95/5320 ROM/RPKG pairs were then
found in the user's Nextcloud `NGAGE` folder. The next step at that point was to import those inputs, prioritize
Snakes and then Asphalt gameplay/input/save checks, followed by shared-library
integration and dependency/asset review. Current work has moved to common setup,
controls and storage mechanics; see the latest integration evidence above.
#20 subsequently gained explicit existing-copy choices and remembered URI
associations in beta.3 on 2026-10-15; see the current availability
section and checks above. Existing duplicates are not merged. The maintainer
confirms each next implementation batch.

## M3G Development History

The dated entries below describe earlier isolated experiments, not the current
production configuration. On 2026-10-11 the maintainer retained the old engine
while developing a replacement separately. On 2026-10-13 the maintainer approved
removing it entirely. The ordinary build now uses `engines/m3g`; the old engine
remains only in Git history. See the production report above for current checks.

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

### Mutable Geometry and Skeletal Deformation: 2026-10-13

Code checkpoint `139aa143` corrects cached morph/skin poses that could ignore
changed vertex data, colors or layouts. Bone queries now combine overlapping
ranges, repeated ranges share one bind pose, invalid overflowing ranges are
rejected, and scaled bones use correct normal transforms for lighting.
Twenty-one original API cases also cover animated ancestors, attached meshes,
independent duplicate poses and loaded morph/skin records.

Final Retroid candidate: **184 passed, no skips**. Final Phone emulator:
**106 shared/animation/deformation checks passed, no skips**. The remaining
78 implementation/GPU checks were not rerun on Phone this checkpoint. The
unchanged native comparison has six failures in a bounded 15-case subset and
crashed on an overflowing range in an earlier interrupted run; its separate
normal-transform comparison passed. These are recorded differences, not a
claim of complete replacement parity.

The final GPU-enabled Retroid build reached actual **Deep: Submarine Odyssey
(Java)** gameplay, with fresh intro/game screenshots. Music was off: no audio,
mission-completion, sustained-FPS or physical-controller acceptance is implied.
Ordinary app packages were untouched. No APK was published, and main's engine
and root licensing remain unchanged.

[Implementation, raw evidence, screenshots and APK identities](https://github.com/EriArk/JavaEmulator/blob/139aa143/experiments/m3g-host/deformation-20261013.md).
Next: scene-wide light/camera/culling and complex-model loading contracts,
then broader parity and production-switch acceptance.

### Scene, Loader and Raster Compatibility: 2026-10-13

Research code checkpoint:
[`a9f51dbe`](https://github.com/EriArk/JavaEmulator/commit/a9f51dbe).
The candidate preserves objects when scene queues grow, respects camera ranges,
corrects spotlight animation and disabled light ancestors, and consistently
handles animated colors and fragment transparency. Pixel-center sampling now
agrees across Java/native/GPU fixtures, including clipped triangle edges.
Nested references have isolated path context; section framing, checksums,
compression and record bounds are checked before interpreting model data.

Both final research variants pass **260 checks without skips**, on Retroid
Pocket Classic and the Android 15 Phone emulator. This includes prior animation
and deformation coverage, 76 additional scene/loader/alpha checks and actual GPU
draw assertions. A final test-only root-order correction passes the 21 loader
checks on both targets. The unchanged native engine passes 40 of the 47 new
shared fixtures; the seven differences include nonvisual API return values and
file-loading behavior, not seven proven visual defects.

All **70 standalone M3G resources from eight supplied games** load through the
host. This is not eight fully working games. Fresh Retroid runs reached Asphalt
races on CPU and GPU-enabled builds, plus DiRT racing and Deep: Submarine
Odyssey gameplay on the GPU-enabled build. No full playthrough, audio-quality or
sustained-FPS claim. GPU fallback remains possible. Main's engine/license and
public APKs are unchanged; performance after these fixes and publication
readiness remain open.

[Implementation, limits, hashes, logs and screenshots](https://github.com/EriArk/JavaEmulator/blob/a9f51dbe/experiments/m3g-host/scene-contracts-20261013.md).

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

## GPU Submission and Dependency Checkpoint, 2026-10-13

Research implementation: [`81c8b2d8`](https://github.com/EriArk/JavaEmulator/commit/81c8b2d8).
Bulk vertex packing and scoped GPU state reuse reduce synthetic geometry/many-draw
frame costs by roughly 14-21% in repeated Retroid measurements. All 30 before/after
image checksums are unchanged. New mixed-material tests exposed a color-mask
state regression on Retroid that the emulator and benchmark images missed;
the implementation was fixed before this checkpoint.

Both final variants pass **262 checks without skips**, on Retroid and the Phone
emulator. An earlier exhaustive-prefix emulator attempt timed out and is retained
as an incomplete run. Two attempted sprite optimizations did not help and were
removed; rebuilt APKs match the accepted artifacts byte for byte.

Constructo Combat and Tower Bloxx reached gameplay on candidate CPU and GPU
paths; Tower Bloxx dropped a block, counted occupants and produced the next
floor. These are bounded game checks, not full-playthrough, audio or FPS claims.
The original engine also reached Constructo gameplay. Oversized tutorial fonts
in its detected small-screen profile remain a separate host/profile concern.

The portrait sprite fixture remains about 6% slower than the preceding candidate,
and the original native engine is still substantially faster. Do not describe
this as complete performance parity or a universal speedup. A larger GPU
geometry/presentation redesign is under discussion, not implemented.

The inherited compact MobileFFmpeg AAR was matched exactly to a pinned upstream
binary/source repository; inspected native configuration identifies its codec
set and LGPLv3-or-later marking. Complete dependency/source/notice packaging and
signed release checks remain open. Production engine, root license and public
APKs are unchanged; no new beta was published.

[Timings, checks and screenshots](https://github.com/EriArk/JavaEmulator/blob/81c8b2d8/experiments/m3g-host/submission-20261013.md).
[Dependency provenance and remaining scope](https://github.com/EriArk/JavaEmulator/blob/81c8b2d8/experiments/m3g-host/dependencies-20261013.md).

## Hardware Geometry Checkpoint, 2026-10-13

Research implementation: [`0dbea9db`](https://github.com/EriArk/JavaEmulator/commit/0dbea9db).
An early GPU stage now handles vertex transforms, clipping and smooth lighting,
instead of receiving only CPU-prepared triangles. Immutable geometry snapshots
are cached within a fixed memory budget, and target release no longer copies
depth data that the next bind will clear. CPU fallback still preserves depth.
The original engine was studied for architecture, not copied into the replacement.

Both final variants pass **301 checks, zero skips**, on Retroid and the Phone
emulator. Repeated same-APK measurements show 60-66% less time for dense lighting,
23-29% for multi-object/material scenes and 14-17% for sprites. These are
synthetic frame costs, not measured game FPS. The earlier portrait sprite
regression is absent in this fixture. Native reference timings are still lower.

Fresh Retroid runs reach an Asphalt race, a Constructo round and the next floor
in Tower Bloxx. Logs confirm actual hardware geometry use. This is not full-game,
manual-controller or audio acceptance. Small rounding differences and the
up-to-four-channel-level dense-lighting difference are documented, not hidden
behind a claim of pixel-identical output.

At this checkpoint hardware geometry remained separately opt-in. Flat/two-sided
shading, four textures and depth-offset cases kept their existing preparation
path; CPU posing and color readback also remained. Indexed/reused buffers and
four-texture coverage are addressed by the follow-up below. Main's engine, root license
and release process have not changed; no public APK was published. Live GitHub
release state was not re-queried during this renderer checkpoint.

[Architecture, measurements, artifact hashes and screenshots](https://github.com/EriArk/JavaEmulator/blob/0dbea9db/experiments/m3g-host/hardware-20261013.md).

## Indexed GPU Geometry Checkpoint, 2026-10-13

Research implementation:
[`0697d76c`](https://github.com/EriArk/JavaEmulator/commit/0697d76c).
Meshes now reuse compact indexed buffers in video memory across frames, bounded
to 4 MiB / 64 entries, instead of re-uploading expanded triangles on every
submission. Immutable revisions preserve queued mutations and context recovery.
A separate normal attribute allows all four texture units on the hardware path.

Both variants build; **311 checks pass with zero skips** on Retroid and the
Android 15 Phone emulator. Two before/after pairs retain all 30 fixture images
exactly. Dense-lighting fixture cost drops 13.7-14.6%; multiple objects and
alternating materials drop 6.5-8.2%, relative to the immediately preceding GPU
checkpoint, not the original native engine. One fixture reused a single upload
across 3584 draws.

No universal speedup is claimed. Portrait depth-offset fallback was 8.4% slower
with short warmup; with the same longer-warmup harness on both builds, the stable
repeat was about 1-2% slower. One long run had unexplained timing excursions and
is preserved in the evidence. These are synthetic costs, not measured game FPS.

Fresh ADB-driven Retroid checks reached the Asphalt race, Constructo gameplay
and Tower Bloxx construction, with actual hardware-stage execution recorded.
Screenshots are genuine device captures; audio, physical controls and full-game
acceptance were not established. Existing Constructo tutorial-font/side-face
issues are not claimed fixed.

The implementation and evidence are on the research branch; APKs stay local.
Main's engine and root license remain unchanged. Next engineering targets are
flat/two-sided lighting coverage and the remaining fallback/bitmap-transfer
costs, not per-game special patches. Live public-release state was not rechecked.

[Code, measurements, limitations, hashes and screenshots](https://github.com/EriArk/JavaEmulator/blob/0697d76c/experiments/m3g-host/indexed-20261013.md).

## Hardware Shading Checkpoint, 2026-10-13

Research implementation:
[`64c4f866`](https://github.com/EriArk/JavaEmulator/commit/64c4f866).
Flat and two-sided lighting now use indexed GPU geometry instead of CPU
preparation. Flat colors retain the third vertex through clipping, including
unlit vertex colors that the inherited renderer previously interpolated.
Separate ordinary/extended shaders avoid most of the overhead introduced by
an intermediate combined shader. Immutable uniform groups are reused; oversized
transient meshes no longer evict reusable small meshes.

A Phone-emulator failure exposed inconsistent face selection after GL winding
changes. The renderer now uses a fixed GL convention with explicit M3G facing
and culling, without device-name workarounds. A separate nearest-texel boundary
comparison was made unambiguous and supplemented with exact expected-color
checks; cross-driver boundary identity is not claimed.

Final Retroid Pocket Classic: **325 checks passed, zero skips**. Android 15
Phone emulator: **13 lighting checks and one winding/culling check passed**;
the full Phone suite was not completed in this batch. Both APK variants build.
Retroid remains the primary hardware verification target.

Two specialized-shader benchmark repeats cut flat/two-sided fixture costs
51-63% relative to the preceding CPU-prepared lighting path. The final portable
renderer repeat retains that improvement. This is synthetic frame cost, not
game FPS: ordinary fixtures show small mixed changes, not universal acceleration.
The 30 pre-existing images match the control; four newly accelerated fixtures
have small color differences and one high-contrast edge sample. The final
portability correction preserves all 34 specialized-shader images exactly.

Fresh Retroid checks reached Asphalt racing, Constructo gameplay and Tower
Bloxx construction. Asphalt was checked again after the portable-facing fix,
with road/car/HUD visible and the speedometer at 95 km/h. These are ADB-driven
device checks, not manual-controller, audio or complete-game acceptance.

Code and evidence are public on the research branch; game archives and local
APKs are not published. Main's engine, root license and release APKs are unchanged;
live release state was not rechecked. Remaining engineering work includes
CPU-prepared depth-offset draws and the LCDUI bitmap transfer, followed by
native-engine parity and release acceptance.

[Changes, artifact hashes, raw tests, timings and screenshots](https://github.com/EriArk/JavaEmulator/blob/64c4f866/experiments/m3g-host/lighting-20261013.md).

## Retroid Gameplay and Font Defaults: 2026-10-13

The replacement and original M3G engines were compared on the physical Retroid
in Asphalt races (40 seconds each), Constructo's first level and Deep: Submarine
Odyssey Java's opening gameplay (35 seconds each). All six captures delivered
about 60 surface buffers/s without presentation gaps over 100 ms. This is not
60 unique game frames or logic ticks: repeated surface contents and multiple
game flushes remain possible. Captures and host timings are recorded separately.

Asphalt selected different tracks, and Constructo's candidate player was down
while other helicopters remained active. Deep was checked at the same opening
location with matching ADB turn inputs, not a frame-identical replay. Candidate
host-update throughput remains lower in Constructo and Deep; no overall native
speed parity is claimed. Black Constructo cliff sides also occur on the original.
Audio, manual controller use, sustained thermal behavior and full-game acceptance
were not established by these runs.

Fixed oversized fonts at their shared cause: newly prepared profiles now use
the existing resolution-derived sizing. Old explicit sizes remain untouched;
Advanced provides an Automatic preset. A Retroid before/after capture shows
Constructo's tutorial text fitting. This independent fix is on `main`
(`9430cb0a`), with both ordinary debug variants built and 14 unit tests passed
per variant. Those APKs were not distributed or installed over the normal app.

Research GPU diagnostics now use application context instead of requiring an
initialized Activity. Non-debuggable research APKs can export cadence through
app-specific external storage. Final measured GPU build passes 332 Retroid
checks (325 renderer/host plus seven meter checks). Focused font, compatibility
and meter checks pass 31/31 on Retroid and 31/31 on the Phone emulator; this is
not a full Phone renderer-suite pass. The pre-fix failure remains documented.

No production engine switch, root license change or release APK. Live release
state was not rechecked. Next work targets remaining framebuffer transfer and
CPU-prepared draw costs, followed by matched gameplay/native-parity acceptance.
[Fixes, limits, timings, screenshots and raw checks](https://github.com/EriArk/JavaEmulator/blob/01a8837b/experiments/m3g-host/gameplay-20261013.md).

## M3G Throughput and Compatibility: 2026-10-13

The research renderer now avoids unnecessary LCDUI copies, reads opaque Android
bitmaps through an NDK bridge and submits warmed GPU frames in one scope. Full
solid clears avoid framebuffer uploads; larger bounded small-mesh caches and
conservative off-camera rejection reduce repeated setup. Transparent pixels,
partial clips and CPU fallback retain their preservation/conversion behavior.

Read-only comparison with the original engine informed these changes and
exposed compatibility fixes: truthful RGB888 quality hints, immutable/background
validation, meaningful depth-offset units and independent depth testing/writing
for both meshes and sprites. No EPL implementation was copied into the candidate.

On Retroid, 34 synthetic fixtures cost 17-34% less than the preceding candidate,
with identical before/after pixel CRCs. Candidate frame costs remain 19-83%
higher than the original engine in these synthetic fixtures; native
parity is not reached, particularly in extended lighting and sprites.

Deep: Submarine Odyssey Java's final gameplay sample records a largest-window
target P95 of 4.186 ms versus 4.010 ms in a fresh original sample. Bitmap import
falls from 0.370 to 0.096 ms/update against the intermediate candidate. These
are different camera/particle states, not a controlled game-FPS ratio. Asphalt
also reaches the second lap with visible track movement and smoke. Both final
35-second captures maintain about 60 surface buffers/s without >100 ms gaps;
repeated buffers prevent treating that as 60 unique game frames.

Final measured research APK passes 361 checks on physical Retroid with no skips.
Phone emulator passes all 36 focused new renderer/meter checks; its earlier full
attempt exceeded 600 seconds and is not reported as a full pass. Both app/test
variants build. Manual buttons, audio, sustained thermal and full-game acceptance
remain open. No APK distribution, production engine switch or license change;
live public release state was not rechecked.

[Implementation, raw checks, artifact identities, timing tables and screenshots](https://github.com/EriArk/JavaEmulator/blob/525ab8a4/experiments/m3g-host/throughput-20261013.md).

## Specialized Shaders and Partial Readback: 2026-10-13

The research renderer now reuses compact GPU programs for the actual texture
count and sprite/mesh path instead of always executing the generic four-texture
program. Color readback covers only the union of color-writing scissors;
depth-only passes do not copy color. Sprite lookup packing uses bulk copies
while preserving exact fractional sampling. These are automatic renderer changes,
not extra user settings, resolution reductions or per-game patches.

Same-APK Retroid comparison records 22-34% lower flat/two-sided lighting costs,
9-14% lower sprite costs and usually 5-13% lower ordinary-scene costs. All 34
fixture pixel CRCs match the reference and preceding checkpoint. Four-texture
fixtures change little. Original-engine cost is still lower: candidate overhead
ranges from 12% to 57% in the 32 comparable synthetic fixtures. Sequential
timings are not unique game FPS or native-parity acceptance.

Final Handheld research suite passes 370 checks on Retroid with no skips.
Phone emulator passes 22 focused lighting/transfer/variant checks across two
test runs, not a full Phone suite. Coverage includes all ten mesh variants,
sparse texture slots, program reuse, disjoint rectangles, alpha/color masks,
depth-only updates and real context-loss recovery. Both app/test variants build.
The initial recovery-test setup failure and corrected run remain documented.

Fresh Retroid captures show Deep Java opening gameplay with camera movement and
Tower Bloxx dropping/stacking blocks through its result screen. Deep's separate
sample is not faster than the preceding checkpoint; no general game-FPS gain
is inferred from synthetic improvements or 60 surface buffers/s.

No ordinary engine switch, root license change or APK distribution. Live release
state was not rechecked; full-game, manual controller/audio and sustained thermal
acceptance remain open. [Implementation, measurements and captures](https://github.com/EriArk/JavaEmulator/blob/8d43830f/experiments/m3g-host/shaders-20261013.md).

## DiRT and PGR on Retroid: 2026-10-13

Two additional Java M3G games reach moving races on the physical Retroid Pocket
Classic with both the replacement and original engines. DiRT 3D runs at about
16.6 surface buffers/host updates per second on both; PGR 3D runs at about 13.3
on both. These are short ADB-driven captures, not independently counted unique
game FPS or full-game/controller/audio acceptance. PGR still costs more renderer
time on the candidate; equal observed update rates do not prove universal parity.

Testing also exposed and fixed PGR's cropped menus: anonymous 128x128 car/city
textures were being mistaken for the display size. The general preparation fix
is on main as `a82c0378`, separately from the renderer experiment. Explicit
screen declarations and named screen artwork remain supported; existing profiles
are preserved. Fresh research preparation now keeps the readable 240x320 default.
Four new regression cases bring focused compatibility checks to 24/24 on Retroid
and 24/24 on the Phone emulator. Ordinary main Handheld/Phone builds and 14 fresh
JVM tests per variant pass. The full renderer suite was not rerun for this fix.

Mophun uses its own framebuffer, not this M3G renderer, so it was intentionally
excluded from these checks. No engine/license switch or APK distribution.
See [racing evidence and screenshots](https://github.com/EriArk/JavaEmulator/blob/e9cea5c6/experiments/m3g-host/racing-20261013.md).

## Images and Writing Guidance

- **Use for current posts:** [0.6.0 Beta 1 captures](../screenshots/releases/v0.6.0-beta.1/README.md), 2026-10-26: sixteen signed-build Retroid screenshots covering light/dark library and settings, style picker, account, transfer, experimental Social and Java/Mophun/native game menus. Captions identify the exact build and device. Older 0.4/0.5 packs remain historical evidence, not current redesigned UI.
- [0.2.0-beta.1 release captures](releases/v0.2.0-beta.1.md#captures), 2026-10-13: signed Handheld Asphalt/Deep Abyss on Retroid and signed Phone Deep Abyss in the emulator. Short ADB-driven checks, not whole-game/audio acceptance.
- [Archived development screenshots, 2026-10-11](https://github.com/EriArk/JavaEmulator/blob/23da5d4b/screenshots/post-2026-10-11/README.md): 12 real emulator PNGs, ZIP, captions and hashes. Label as **development / experimental.15**.
- [Archived beta.2 screenshots](https://github.com/EriArk/JavaEmulator/blob/23da5d4b/screenshots/website-beta2/manifest.json): older UI, not current captures.
- Gallery covers in the archived October 11 / website-beta2 packs were manually curated. Grid/List show native icons or fallbacks. No games are bundled; artwork is not evidence of gameplay compatibility.
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
