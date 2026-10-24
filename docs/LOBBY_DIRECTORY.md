# Compatible Lobby Directory (Unreleased)

The later [room workflow checkpoint](LOBBY_ROOMS.md) adds room admission, links and
the publisher/result contract. Counts and capture evidence below describe the
original directory-only checkpoint, not the newer room APK.

2026-10-24. Native Android browsing and automatic community admission are source
implementation, not a public APK update or playable online multiplayer.

## User Flow

Library menu -> Lobbies -> sign in with the existing Fluxer account if needed.
The foreground screen reads the existing shared directory, with search, free-seat
filtering, refresh and links to Chats/Account. No separate service account is created.

If directory access is denied, the first attempt for that account automatically
joins the known lobby community: read the fixed invite, verify its code, guild ID
and guild-invite type, accept it, then read the directory again. The fixed invite
currently belongs to the **TrainerOS** community shared by both products.
Already-readable channels do not cause a membership write. Sign-in alone and
offline single-player do not join a community.

Before attempting admission, persist a per-account attempt marker in no-backup
storage. It contains no token and is not proof of membership. An interrupted or
failed attempt is not automatically replayed after reopening/restarting; the
screen offers **Retry community access**. An uncertain POST response causes a
read-only access check. Explicit retry revalidates the invite and respects rate
limits; provider restrictions are not bypassed. Late callbacks cannot change a
different account's screen. Markers are local to this app installation, not a
cross-device or uninstall-proof preference.

## TrainerOS Reuse and Isolation

Inspected the owner's TrainerOS checkout at `e7b24ba6`, particularly
`LobbyDirectory.h`, `FluxerLobby.cpp`, `FluxerSession.cpp`, `SocialLobby.cpp`,
`SocialTests.cpp` and `services/lobby-directory/`. The unrelated dirty checkout
was left untouched. The shared bot's extension was developed from `origin/main`
in an isolated worktree: [TrainerOS PR #260](https://github.com/EriArk/TrainerOS/pull/260),
commit `442bee5`, source only, not merged or deployed at this checkpoint.

- Reuse the same bot, community, thematic channels and authenticated account.
- TrainerOS currently accepts only advertisement `v: 1`; it checks bot/expiry/
  owner/revision and filters by topic/search/free seats, not by a product registry.
  Runtime preparation happens separately. Its checked membership flow is manual,
  including a test against silent joining. AbyssME's automatic attempt is the
  owner's newer requirement, not a claim about existing TrainerOS behavior.
- AbyssME requires `v: 2`, `product: abyssme`, `protocol: 1`. Legacy TrainerOS
  readers ignore v2, and AbyssME rejects v1/unknown products/protocols. Adding a
  product field to v1 would not provide safe isolation.
- The service retains **one active public room per account across both products**.
  A second UUID returns `already-listed`; it cannot replace the existing room.
  UUID ownership also binds the product, including close/withdraw tombstones.
- Selected-game listings require exact platform/mode/adapter capability, not
  a similar title or locally installed game. Content identity is SHA-256. Public
  projection never includes ROM paths, credentials, arbitrary join URLs or libraries.

The production Android capability set is deliberately empty until a real online
adapter is bound. Game-free AbyssME rooms can be displayed; selected-game rooms
are hidden rather than advertised as playable. Device fixtures supply one test-only
capability to exercise the UI. No real room publishing, invitation, admission,
Ready, transport or game launch is enabled by this change. Details provide Close,
not a misleading Join action.

## Reader Boundaries

Only known channels, trusted bot messages and valid public/approval advertisements
are accepted; webhook messages and unexpected guild/channel IDs are rejected.
Bounded pagination verifies descending IDs and uses at most 50 pages. Highest
revisions survive refreshes, expired higher revisions suppress old advertisements,
and owner/same-revision conflicts hide the room. Wall and monotonic expiry prevent
clock rollback from extending a listing. Errors clear stale rows; account changes
clear private state. Polling runs only while foregrounded, every 30 seconds.

HTTP stays on the existing fixed HTTPS origin with no redirects or credential
logging. Only GET/POST for the exact known invite were added to the authenticated
route allowlist; arbitrary invitations and anonymous membership writes remain denied.

## Verification

Combined Debug app/test APKs and lint passed. **222 JVM tests** passed, including
35 directory cases covering scope, pagination, stale revisions, conflicts, expiry,
account changes, pause, rate limits and automatic admission/recovery.
The isolated shared service passed **15 Python tests**: eight legacy and seven v2
cases, including SQLite migration, global quota and product-bound tombstones.

**28 Retroid Android instrumentation checks passed** (7 directory and 21 existing
account/chat/friend/storage/packet checks). The seven directory checks passed again
after a capture-only wait adjustment. These cover automatic fixture admission,
retry, durable attempts, transport allowlisting, layouts and keyboard behavior.
Device checks and screenshot provenance are recorded in the
[development captures](../screenshots/development/2026-10-24-lobby-directory/README.md).
Local build evidence: `build/lobby-directory-build.log`, JUnit XML under
`app/build/symbian-release/test-results/testFdroidDebugUnitTest/`, and
`build/lobby-directory-retroid.log`. These ignored logs are not release assets.

Only the separate Debug package is updated. The label remains 0.5.0-beta.1 / code
310 for this local development APK; it is not the signed public beta. SHA-256:
`5f1dac75be7e6be36ff9b25d02ce41ce06a6b14d58d8a4dad770094ff928e3f2`.

The public invite lookup was checked read-only and matched the expected community.
No authenticated live join, two-account room acceptance, live bot deployment,
gameplay, physical controller press or new emulator acceptance is claimed.
The [official invite API](https://docs.fluxer.app/http-api/invites/) was checked;
device tests use fixture memberships/rooms, not real people.

## Next Boundary

Bind own-room publication and explicit host admission/invitations to the existing
contact/session foundation, including service result acknowledgements and the
cross-product quota message. Complete live account/community acceptance and a
controlled shared-service rollout with backup/rollback before claiming a working
public directory ecosystem. Room readiness and an actual game/network adapter
remain separate from seeing an advertisement. Issues #34-37 and TrainerOS #257
remain open.
