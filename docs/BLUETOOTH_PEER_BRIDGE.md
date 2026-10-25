# Bluetooth Over the Secure Peer Channel

Unreleased source checkpoint, 2026-10-25. This extends the
[secure transport](SECURE_PEER_TRANSPORT.md) and [JSR-82 adapter](VIRTUAL_BLUETOOTH.md).
It is not a released online-play feature or evidence of a commercial game match.

## Implemented

- `PeerBluetoothBackend` carries service discovery, custom SDP attributes,
  independently addressed RFCOMM streams and framed L2CAP over one authenticated,
  encrypted reliable peer channel. Gameplay bytes never use Fluxer DMs.
- Explicit bounded binary metadata encoding, including nested sequences, UUIDs,
  integer types, strings and byte arrays. No Java object deserialization. A query
  barrier makes prior service updates visible before discovery returns.
- Discovery visibility is separate from admission. Turning off inquiry visibility
  does not invalidate an already admitted peer or its open stream.
- Odd/even connection IDs allow simultaneous connections in both directions.
  IDs are not reused within a session. MTUs are negotiated and checked on both
  sides. Unsupported service/MTU requests are rejected without changing peers.
- Per-stream receive credits stop a writer when its reader is slow. Reading
  releases credit. Close wakes blocked read, write, connect, service-query and
  accept operations. A normal remote stream close drains queued bytes before EOF;
  a failed peer session interrupts IO instead of presenting a successful EOF.
- `BluetoothPeerSession` binds the backend to `SecurePeer` on its owner thread.
  Blocking game IO never runs on that owner. Virtual addresses expose Player 1/2,
  not the provider's account identifiers. Revalidating a different account,
  game/generation/attempt or missing local preparation closes the old adapter.
- `LobbyRoom` supplies a fresh, host-selected peer attempt for an admitted,
  connected two-player `java/bluetooth/1` selection. Guests derive the same binding
  only from authenticated room state, not an invitation or incoming ICE offer.
- `LobbyPeerCoordinator` requires separately verified, exact local content before
  creating a native peer. It marks Prepared only after the encrypted handshake;
  Ready remains an explicit player action. Failed attempts do not retry endlessly.
  Late callbacks are fenced, and changing authority or losing local verification
  closes the previous link and revokes readiness.
- Bounded early signaling can wait for local verification, but cannot authorize
  it. Old-attempt messages are discarded. Account reset, mailbox routing and
  an opt-in game-network timer are connected in `LobbyRoomModel`; ordinary social
  browsing does not start a 50 Hz network timer or load the native peer library.

## Bounds

There is one admitted remote peer, at most 16 advertised services and 16 active
logical streams per side, eight pending accepts per service, and 1,024 stream
attempts per session. Each stream has a 32 KiB receive window; data frames carry
at most 8 KiB. Outbound admission is limited to 256 frames / 1 MiB. Exhaustion
fails explicitly rather than silently dropping game bytes.

An encoded service must fit a 16,383-byte native payload; excessive metadata is
rejected on registration/update. SDP snapshots also retain their existing depth,
attribute and memory bounds. Connect/query and stalled-write waits have a
30-second inactivity deadline; ordinary reads/accepts can wait until cancellation.
Early signaling is limited to 128 distinct envelopes of at most 2,000 characters,
with a local monotonic 120-second lifetime and a single authority binding.

There is no fallback to physical Bluetooth after a virtual session fails. No
socket address, service attribute or display name grants room membership.

## Verified

- **353 JVM tests passed**, zero failures/errors/skips: 341 app tests and 12 peer
  tests. This includes 17 shared backend contracts and nine new coordinator cases.
- **50 Android checks passed on each target**: Retroid Pocket Classic / Android
  14 in 5.080 seconds, and `J2ME_Portable_Preview` / Android 35 in 6.541 seconds.
  These comprise 17 backend, 19 virtual-API, ten native peer and four existing
  multiplayer core checks. The emulator uses the arm64 translation bridge on
  x86_64; it is not native x86 or second-handheld acceptance.
- **Two real Fluxer accounts, both host roles:** the actual `Connector.open`,
  `LocalDevice`, inquiry/service search, custom SDP, RFCOMM and L2CAP paths ran
  between Retroid and the emulator over the encrypted native transport.
  RFCOMM echoed 256 KiB of deterministic bytes; L2CAP negotiated 512/200-byte
  MTUs and verified truncation, subsequent packet boundaries and empty packets.
  Retroid host/emulator guest passed in 20.470/10.584 seconds. Reversed roles
  passed in 22.297 seconds for emulator host and 13.397 for Retroid guest.
  These are test durations including signaling, not latency measurements.
- The first live run finished the data exchange but the guest fixture closed
  its output after the host had already torn down the whole peer. The fixture
  now waits for logical EOF before host teardown. Review also corrected empty
  stream flush after an orderly remote channel close, preserved initial SDP
  attributes, and made indefinite waits independent of `nanoTime`'s origin.
  Focused regressions and both final live roles pass.

The live fixture uses a prearranged admission binding and native game APIs, not
the production room UI or a running MIDlet. Coordinator authority/Ready tests use
deterministic room peers and a fake transport. Neither category substitutes for
the missing end-to-end room-to-game test. Existing authenticated accounts, games,
saves and release packages were preserved; no friendships/listings were mutated.

## Remaining Runtime Boundary

Production capabilities remain empty, and no UI calls the internal verified-local-
content hook yet. `MicroActivity` runs in Android process **`:midlet`**, while the
room/account owner runs in the main process. A `VirtualBluetooth` static instance
in the main process cannot make gameplay work in the other process.

The next implementation must provide a non-exported, attempt-bound IPC bridge,
exact installed-game verification, explicit Ready/launch coordination, attachment
before MIDlet startup, process-death/disconnect cleanup and return to the same room.
Do not move the MIDlet into the social process merely to share static state.
Afterward, verify a real Java match and different-network/NAT behavior. STUN/TURN
deployment, relay fallback, physical controller play and complete-game support
are not claimed here. Symbian/Mophun networking is not implemented by this change.

No new UI or screenshots, release APK, Nextcloud upload or service deployment.
Room UI automation was not rerun against the signed-in test accounts; the new
room coordinator has JVM coverage and the actual transport has device evidence.

## Reproduction and Artifacts

```text
gradlew :app:testFdroidDebugUnitTest --rerun :peer-core:testDebugUnitTest --rerun :app:assembleFdroidDebug :app:assembleFdroidDebugAndroidTest :peer-core:peerSourceBundle -PsymbianRelease=true -PsymbianSourceDir=build/symbian-source-check -Pandroid.injected.build.abi=arm64-v8a
adb -s DEVICE install -r -d -t APK
adb -s DEVICE shell am instrument -w -r -e class io.github.eriark.abyssme.peer.SecurePeerDeviceTest,javax.bluetooth.VirtualBluetoothConnectorDeviceTest,javax.bluetooth.PeerBluetoothBackendTest,ru.playsoftware.j2meloader.multiplayer.MultiplayerCoreDeviceTest io.github.eriark.abyssme.debug.test/androidx.test.runner.AndroidJUnitRunner
```

The opt-in live method is
`FluxerLivePeerDeviceTest#authenticatedSignalsThenBluetoothGameApis`. Pass opposite
`peerRole` values, the existing dedicated friend as `peerAccount`, and identical
fresh UUIDs for `peerRoom` / `peerAttempt` on both devices. It refuses ordinary
accounts and uses normal encrypted app credential storage.

Installed only `io.github.eriark.abyssme.debug`, version `0.5.0-beta.1-debug`,
code 310, an unreleased test-only build. Both installed base APK hashes matched:

```text
APK SHA-256: 3683d58f4ccf20cd7e36775d44375e44550a2f683ef81949026774b0705073b1
Peer source ZIP SHA-256: 8cd9434188e6b562f8a73e983f70153d5b4fb9ede7fd928718950a5c35a22eec
```

Use `app/build/symbian-release/intermediates/apk/` and `output-metadata.json`,
not an old `outputs/apk` artifact. The local source bundle is
`build/release-sources/abyssme-peer-corresponding-source.zip`; the Bluetooth and
coordinator implementation additionally belongs in the matching app source tree.
Ignored evidence: `build/bluetooth-peer-build.log`,
`build/bluetooth-peer-{retroid,emulator}-final.log`,
`build/bluetooth-peer-live-{retroid,emulator}-{final,reverse}.log`, and JUnit XML.
No credentials, proprietary games or generated binaries were added to Git.
