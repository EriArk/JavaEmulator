# Rooms, Invitations and Publication (Unreleased)

2026-10-24. Unreleased Android source, now with a deployed shared directory and
[live public/private two-account evidence](FLUXER_PUBLIC_DIRECTORY_CHECKPOINT.md).
The original counts below are historical; no public APK update or playable
online game is implied by room admission.

## User Flow

- Lobbies -> My room -> Create room: Invite only, Open or Host approval.
- Invite a current Fluxer friend from the picker, or enter another player's user
  ID. The recipient can accept/decline; a pending invitation is not membership.
- Directory -> Open room leads to a confirmation before requesting admission.
  Open rooms admit through their host; approval rooms wait for Approve/Decline.
  A private room requires a current offer bound to that exact recipient.
- Share a room locator with the Android share sheet. `abyssme://lobby?v=1&host=...&room=...`
  contains no credential or invitation secret. Opening a link never joins silently
  and cannot bypass private-room policy. Malformed/ambiguous links are rejected.
- The room shows host, players and connection/preparation states. The host can
  remove a player or close the room; guests can leave. Destructive actions confirm.
- Room ownership survives rotation and navigation inside the app process. Chats
  do not create a separate room authority. Sign-out/account change clears it.

**Ready is capability-gated.** The underlying session and room protocol bind
preparation/Ready to the exact game hash, engine, mode, adapter and membership
generation. Changing membership/connection clears old readiness. No production
online game adapter is registered yet, so the UI correctly keeps Ready disabled
and shows no online game selected. There is no fake game selector or launch promise.

## Existing TrainerOS Approaches Reused

Reviewed `LobbyCoordinator.cpp/.h`, `LobbySession`, `LobbyWire.cpp`,
`FluxerLobby.cpp`, the directory service and the existing temporary-contact
foundation in the owner's TrainerOS checkout. The dirty original checkout remains
untouched. The Java room actor adapts host authority, recipient-bound expiring
offers, correlated joins, incarnation/serial replay protection, heartbeat and
generation-based preparation. It composes the existing AbyssME `LobbySession`.

Private control packets use `org.abyssme.lobby`, version 1, and an AbyssME prefix,
not TrainerOS room packets. The small Android contract supports at most 16 players
and fits one provider message. Game engine/mode/adapter tokens are limited to 32 ASCII characters
in this room contract so a full snapshot still fits; broader session-model
capabilities are rejected before selection rather than creating undeliverable rooms.
It does not copy TrainerOS's larger fragmented runtime/streaming protocol or claim
cross-play. Provider-authenticated authorship,
not a packet's claimed peer ID, supplies sender identity. Directory scope remains
AbyssME v2. Same-account boot replacement is rejected rather than silently taking
over an admitted participant; that participant must reconnect through a new room.

## Provider and Lifecycle Boundaries

`LobbyMailbox` reuses existing fixed-origin Fluxer account/channel/message routes.
It checks relationship blocks, verifies a direct channel's exact recipient, and
accepts control messages only from that recipient with no webhook/guild identity.
The follow-up [contact integration](LOBBY_CONTACTS.md) binds automatic temporary
friendship to admitted pairs and coordinates it with manual friend actions.
No group membership is introduced. Provider DM privacy/permissions still apply;
directory/community membership does not override them.

Reads and writes are bounded, rate-limited on 429, and fenced across account changes
and application backgrounding. Recent DMs plus active peers are polled while the
app is foregrounded. Only 50 latest messages per selected DM are read per pass;
very busy conversations may require retry when a control message falls outside
that window. Ambiguous POST results are not automatically replayed. No push or
background-service guarantee, no gameplay traffic over chat, no P2P transport yet.

Periodic host snapshots acknowledge ordinary heartbeats without a redundant reply
to every ping. New unsolicited snapshots replace older unsent snapshots for the
same recipient and room incarnation. Correlated admission, invitations and
terminal controls never coalesce; they preserve their order. Repeated send failures
do not generate an unbounded rebroadcast loop.

Room membership is process-local at this stage. Process death does not restore
Ready or pretend the host is still present. Public publication intent/revision is
atomic, per-account and excluded from backup; on restart the old recorded listing
must be closed before another public room can be published. Offline closure may
remain visible until the directory TTL expires. Public updates stop in background.

## Publication Is Not Admission

`LobbyPublisher` writes intent before sending each scoped bot command. The updated
2026-10-24 owner decision allows one AbyssME room independently of TrainerOS's
32-listing allowance; see [service coexistence](FLUXER_NONFRIEND_CHECKPOINT.md).
Private rooms are never advertised. Changing visibility withdraws the listing;
closing or recovering a prior process sends a product-bound close.

[TrainerOS PR #260](https://github.com/EriArk/TrainerOS/pull/260) now adds private
command result messages with account, request UUID, room, revision, operation,
result and expiry. The client accepts only the known bot and the exact pending
request. `accepted` means durable server acceptance, **not** a visible advertisement
or host admission. Quota conflicts identify another active AbyssME room, not a
TrainerOS meeting; absent replies offer retry rather than claiming success. Higher revisions
and the saved close record handle uncertain writes conservatively.

The shared extension is deployed; see [rollout evidence](FLUXER_PUBLIC_DIRECTORY_CHECKPOINT.md).
This checkpoint advertises game-free rooms only, matching the empty production
online capability registry.

## Verification and Availability

265 JVM tests passed, including paired room actors for admission/approval/private
offers, spoofed/replayed/expired packets, disconnects, close/remove and exact-game
readiness; mailbox tests for channel identity, blocks, account/pause fencing,
ambiguous delivery, snapshot coalescing and rate limits; publisher intent/recovery/quota/privacy tests;
and strict link parsing. The shared service passes 19 Python tests.

Combined Debug app/test builds and lint passed. All 32 Android checks passed on
Retroid Pocket Classic, including four new room workflows, rotation, approval,
publication fixtures, link confirmation and signed-out isolation. The anonymous
live login handoff check also ran, without an approved account. See the
[five captures, exact APK hashes and test manifest](../screenshots/development/2026-10-24-lobby-rooms/README.md).
No real two-account, physical-button or game acceptance is inferred from fixtures.
Public release assets remain unchanged.

## Remaining Work

Public/private two-account admission and the controlled shared-service rollout
have passed in the later checkpoints. Group chat, broader network recovery,
process recovery beyond closing old publications and contact journals,
local game preparation, virtual JSR-82 and encrypted peer transport remain open.
The Ready protocol is not the runtime adapter. Symbian/Mophun online capability
must be proved independently. Continue through issues #34-37 without marking
online gameplay complete from room metadata tests.

Provider contracts reviewed: [channel identities](https://docs.fluxer.app/http-api/channels/), and the existing
TrainerOS implementation. GPL-3.0-only source adaptation, no new binary dependency.
