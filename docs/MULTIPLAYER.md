# Multiplayer: Lobby Lite Implementation

Status: **unreleased social/room features, virtual Java Bluetooth adapter and secure peer core**, 2026-10-25. Parent [#33](https://github.com/EriArk/JavaEmulator/issues/33).
The public 0.5.0 Beta 1 APK has not changed. The new [Fluxer account screen](FLUXER_ACCOUNT.md)
and [private text chats](FLUXER_CHATS.md) / [friends and requests](FLUXER_FRIENDS.md) are reachable in development builds;
real authenticated exchange is recorded in the [live pair checkpoint](FLUXER_LIVE_PAIR.md). [Room metadata and admission](LOBBY_ROOMS.md)
now connect to account/UI/DM delivery; game transport and running MIDlets do not.
Do not advertise online multiplayer, virtual
Bluetooth gameplay or cross-play as available. The existing shared directory was
[updated in place](FLUXER_PUBLIC_DIRECTORY_CHECKPOINT.md); no second bot/server
was added. The [virtual adapter checkpoint](VIRTUAL_BLUETOOTH.md) records isolated
API/IO checks, not a production peer transport or a real game match.
The subsequent [secure peer checkpoint](SECURE_PEER_TRANSPORT.md) adds Android
ICE/DTLS/SCTP, authenticated fragmented signaling and a real Retroid-to-emulator
packet exchange. It does not yet bind the room UI or game Bluetooth backend to
that transport at that checkpoint. The later [Bluetooth peer bridge](BLUETOOTH_PEER_BRIDGE.md)
connects discovery, RFCOMM/L2CAP and room preparation to that core, with live game-API
exchange in both host roles. Production game capabilities remain disabled until
the separate MIDlet process and actual launch/return flow are connected.

## Reuse TrainerOS Behavior

The owner confirmed reuse of TrainerOS's existing solution for non-friends,
not a new friendship system. The reference is `TemporaryContacts.cpp` at
TrainerOS commit `d69e50dcdd90247720f6c479ed85f0e7e98397d9`, together with
`FluxerLobbyChat.cpp` and `docs/LOBBY.md` (temporary contacts section).
The Android adaptation retains GPL-3.0-only provenance in its source header.
The TrainerOS checkout, including its unrelated local changes, was not edited.

The new [TemporaryContacts](../app/src/main/java/ru/playsoftware/j2meloader/multiplayer/TemporaryContacts.java)
actor implements the account-local contact lifecycle:

- Only validated, admitted host/member pairs can negotiate temporary friendship.
  Browsing a directory, receiving an invite or awaiting approval is insufficient.
- Existing friends, blocks and pending requests are not owned by automation.
  Hosts link to members; members do not all add each other pairwise.
- Preserve TrainerOS's `org.traineros.lobby`, version 1, `contact` envelope with
  room/lease UUID, short expiry and the same offer/agree/requested/accepted/
  complete/keep/release/cleanup acknowledgement operations. The transport supplies
  the authenticated sender; an arbitrary `peer` field is not trusted identity.
- Write intent before provider mutation. Confirm returned relationship identity,
  type and timestamp before treating it as owned. Lost or malformed mutation
  replies leave an uncertain relationship for manual review, not blind retries.
- Keep as friend is bilateral; the guest waits for the host's acknowledgement.
  Cleanup waits for both clients, holds active match contacts and rechecks the
  exact owned type/timestamp. Changed relationships and blocks are retained.
- Switching a game does not release a contact. Reusing a pair in another room
  also defers cleanup until the current shared room no longer needs it.
- One provider request is in flight per actor. Duplicate/late replies are fenced;
  rate-limit responses postpone retry. The actor and callbacks run on one owner
  thread. Closing the actor fences callbacks but does not delete relationships.

[FileContactJournal](../app/src/main/java/ru/playsoftware/j2meloader/multiplayer/FileContactJournal.java)
uses per-account atomic replacement, a 512 KiB bound and a maximum of 256 lease
records. Its supplied root must be an existing app-private directory under
`Context.getNoBackupFilesDir()`, never shared game storage. It resolves Android's
trusted system path aliases, then rejects account/journal symlinks. Journal write
failure stops automatic provider changes. Credentials are not stored here.

Important inherited limits remain:

- Initial contact negotiation requires the existing authenticated private
  signaling route. It does **not** bypass Fluxer DM/friend/group permissions.
- Fluxer does not provide an atomic conditional delete by relationship timestamp.
  A read followed by DELETE cannot exclude changes made by another client in
  between. Local serialization is not a server-side compare-and-delete guarantee.
- Returning offline clients may be needed to finish cleanup. No immediate removal
  of an offline participant's contact is promised.
- Android-to-TrainerOS interoperability has **not** been exercised live. Matching
  envelope fields alone are not acceptance of complete provider/signaling behavior.
- [Room contacts](LOBBY_CONTACTS.md) now bind the actor to production account/HTTP/
  DM paths, My room Keep UI and manual-friend interlocks. Live authenticated
  acceptance and automatic group-chat retry remain open. The outer wire prefix
  is AbyssME-only; no TrainerOS cross-play is advertised.

## Host Session and Packet Channel

[LobbySession](../app/src/main/java/ru/playsoftware/j2meloader/multiplayer/LobbySession.java)
is a host-side authority model, not a server or complete network protocol:

- A room may exist without a game. Only its host admits members/selects the game;
  members can leave themselves, not remove other people.
- Preparation binds the exact content hash, engine, mode, adapter version and
  supported capacity to the current room generation. No title-name-only match.
- Preparation and explicit Ready are separate. Membership/game/connection changes
  invalidate old preparation and readiness. All-current-ready yields one launch
  attempt with stable ordered seats, never repeated UI-triggered launches.
- Match completion returns to the same membership with fresh readiness required.
  Closing a room does not claim to have terminated or saved its running game.

The caller must still implement invitation/access policy, authenticated signaling,
local content/runtime checks and the actual runtime lifecycle. No engine capability
is enabled merely by constructing this model. It does not yet serialize the
TrainerOS room protocol or resume a lobby after process death.

[FramedL2capConnection](../app/src/main/java/ru/playsoftware/j2meloader/multiplayer/FramedL2capConnection.java)
implements the existing JSR-82 `L2CAPConnection` interface over a supplied reliable,
ordered stream. It adds a two-byte big-endian payload length, negotiated bounded
MTUs, packet assembly, empty packets and the required truncation/discard behavior.
`ready()` buffers at most one packet and never treats a partial packet as ready.
Close interrupts blocking IO through the owning transport. Invalid size, truncated
read or write failure closes the channel.

This is framing, **not** ICE, STUN, TLS, identity verification, session admission
or transport negotiation. Only a future authorized encrypted P2P channel may
supply production streams. Tests use loopback sockets, not internet transport.
The existing physical Android Bluetooth route does not use this framing:
adding it to a physical Bluetooth connection would break compatibility.
The later [virtual adapter](VIRTUAL_BLUETOOTH.md) implements discovery, service
registration and RFCOMM behind the existing JSR-82 classes, activated only by an
explicit session installation. The later [process bridge](MIDLET_NETWORK_BRIDGE.md)
attaches it before MIDlet startup for an explicit one-use lease. Production room
selection/launch/return is not connected yet; capabilities remain disabled.

## Foundation Verification (Before Account UI)

Local Windows/JDK 21, combined Debug build:

```powershell
.\gradlew.bat :app:testFdroidDebugUnitTest --rerun :app:assembleFdroidDebug :app:assembleFdroidDebugAndroidTest -PsymbianRelease=true -PsymbianSourceDir=build/symbian-source-check
```

- **111 JVM tests passed**, zero failures/errors/skips. Includes 20 temporary
  contact cases, 7 room-authority cases and 9 framed-channel cases. The remaining
  75 are existing unit regressions. This is not 111 game compatibility tests.
- Paired contact fixtures exercise actual journal saves/reloads and a deterministic
  provider/signaling simulation: admission, pre-existing relationships, Keep,
  timeout, interrupted mutations, 429, late callbacks, corruption, offline cleanup,
  recovery, active-game holds and changed rooms. No live Fluxer API requests.
- Two loopback TCP endpoints exchange distinct packets and verify that close
  unblocks a reader. Fragmented/coalesced input, MTU bounds, empty packets and
  short destination buffers are covered separately.
- Retroid Pocket Classic, Android 14: **4/4 instrumentation tests passed** for
  Android journal replacement/account isolation, pre-mutation persistence,
  rejection of account/file symlinks and loopback L2CAP packet exchange.
  The first device pass caught the system-path alias error; it was fixed and
  the expanded suite rerun successfully in this same implementation checkpoint.

Device command:

```text
adb shell am instrument -w -r -e class ru.playsoftware.j2meloader.multiplayer.MultiplayerCoreDeviceTest io.github.eriark.abyssme.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Local evidence (ignored, not release assets): `build/multiplayer-core-build-final.log`,
`build/multiplayer-core-retroid-final.txt`, and JUnit XML under
`app/build/symbian-release/test-results/testFdroidDebugUnitTest/`.
The local Debug APK retains version label 0.5.0-beta.1 / code 310 but includes
this unreleased source; it is **not** the official release APK. SHA-256:
`13f592d12fdf791570f6330c17df67a15125bcd9c410890be2f355c7ca04e226`.
Installed only to the separate Debug package; no release app uninstall or data clear.

No new emulator, physical-controller, two-device, separate-network, live Fluxer,
real-game discovery/gameplay or Android-to-Linux test was performed. No game,
ROM, account token or private test payload was committed. No UI changes or new
screenshots are represented by this internal checkpoint.

## Next Implementation Order

The [Debug room-to-game workflow](ROOM_GAME_LAUNCH.md) now connects verified
installed-game selection, the attempt-bound IPC port, explicit Ready, host
launch, process acknowledgements and return. Release capabilities remain empty.

1. Exercise the complete path with a real Bluetooth multiplayer Java game,
   including discovery, gameplay, normal exit and another match in the same room.
   Startup/return with a single-player MIDlet is not match compatibility proof.
2. Improve same-edition lookup and first-run setup from the room; support a
   specifically agreed MIDlet entry for multi-application archives.
3. Check separate networks/NAT and decide on relay provisioning before enabling
   Release capabilities. Symbian/Mophun require separate capability proof.

Issues #34-37 remain open; this checkpoint does not satisfy their end-to-end criteria.
