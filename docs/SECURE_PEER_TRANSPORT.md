# Secure Peer Transport Checkpoint

Unreleased work, 2026-10-25. This page describes transport, not a playable online
game or a public APK. The existing public release has not been changed here.

Historical checkpoint: the later [Bluetooth bridge](BLUETOOTH_PEER_BRIDGE.md)
adds the backend multiplexer and room preparation. This page retains its own
artifact hashes and test evidence; it is not the latest local build record.

## Implemented

- Native Android libdatachannel + Mbed TLS backend, data-only ICE/DTLS/SCTP.
  Reliable ordered binary messages; no custom encryption and no gameplay relay
  through Fluxer. [Pinned sources and notices](../engines/peer/README.md).
- A two-player binding includes room, generation, fresh connection attempt,
  ordered admitted account IDs, exact game hash, engine, mode and adapter version.
  The authenticated signaling path selects the expected DTLS fingerprint; an
  additional encrypted handshake checks the binding and opposite seat.
- Bounded, expiring signaling fragmentation fits the existing 2,000-character DM
  limit. Out-of-order fragments, identical repeats and early ICE candidates are
  supported. Wrong authors/bindings and expired signals are ignored; conflicts,
  queue overflow, renegotiation and malformed current-session traffic fail closed.
- `LobbyMailbox` atomically queues a complete fragment batch without replacing
  earlier fragments. Its existing authenticated-author/channel/block checks and
  provider backoff remain in effect. AbyssME Chats masks the signaling contents
  with its existing game-session message label; it does not remove the chat row.
- Native handles are registry-owned, callback lifetimes use weak references and
  shutdown wakes polling. Per-peer queues are bounded to 256 events / 1 MiB;
  the send buffer to 256 KiB, each native message to 16 KiB, and native instances
  to eight. Backpressure/error is explicit, not silently dropped game data.
  Cross-thread close waits for current listener delivery and fences subsequent
  connected/data callbacks before returning.
- Monotonic 120-second handshake timeout; no implicit reconnection, certificate
  replacement or fallback to an unrelated room. New attempts need new bindings.
  Signaling uses this local attempt lifetime, not a comparison of two phones'
  wall clocks. Fixed phase labels expose negotiation progress without addresses,
  SDP, provider messages or credentials.

## Scope and Limits

The core has no production caller creating a game connection yet. Room-to-peer
preparation, Ready/launch integration and a `VirtualBluetooth.Backend` service/
stream multiplexer remain to be attached. Production capabilities stay empty.
An admitted binding fixture is not evidence of a production room launching a game.

Only two players and one reliable ordered data channel are supported. A caller
may supply a STUN URL; no public STUN service is silently selected and no TURN
credentials/server are embedded. TURN, relay fallback, NAT diversity, Wi-Fi/mobile
handover, long-session throughput and complete Java-game compatibility are not
accepted by this checkpoint. Failure closes the session rather than inventing a
reconnection. Account and room lifecycle cancellation must be supplied by the
future coordinator. The model has no power to authenticate a sender on its own.

## Verification

Final combined Debug app/instrumentation build and the native corresponding-source
archive succeed. **327 JVM tests pass**, zero failures/errors/skips: 315 app tests
and 12 peer signaling/binding contracts, explicitly rerun. **33 Android checks pass
on each target**: Retroid Pocket Classic / Android 14 (4.305 seconds reported by
the runner), and `J2ME_Portable_Preview` / Android 35 (8.752 seconds). The emulator
is x86_64 running the arm64 APK through its native bridge, not a second physical
handheld or proof of a native x86 build.

The 33 checks comprise ten real native peer cases plus the previous 23 virtual
Bluetooth/core checks. Native cases cover ordered binary exchange (672-byte and
16,383-byte game payloads), wrong binding/timeout, failed signaling, forged DTLS
fingerprint rejection, polling interrupted by close, repeated handle teardown,
the eight-peer resource limit, a stalled consumer overflowing the receive queue,
peers with different monotonic clock origins, and cross-thread close during a
listener callback. Mailbox tests verify authenticated
envelope routing and atomic batch admission; Chats preserves ordinary text while
masking signaling contents.

**Live dedicated-account check:** real Fluxer private DM delivery connects Retroid
and the emulator, with 100 ordered 1,024-byte binary payloads sent and verified in
each direction plus completion acknowledgements. No payload goes through the DM
transport. The final run has Retroid as host (22.419 seconds, two signaling
fragments) and the emulator as guest (11.340 seconds, four fragments). Both pass. These
are end-to-end test durations including provider polling, not latency/FPS figures.
The reversed-role run after the clock fix also passed: emulator host in 33.858
seconds and Retroid guest in 16.282 seconds, before the final close serialization.

The reversed-role check initially failed. Device wall clocks differed by about
21 seconds, exposing an overly strict five-second signaling clock allowance. That
check was replaced with a per-attempt monotonic deadline and fresh authority-bound
nonce. The same devices then passed without changing their clocks. JVM and native
regressions cover independent clock origins. This is a shared protocol correction,
not a device/game exception. Crypto pthread locking was also explicitly enabled.

An emulator installation run failed to attach its test process while Android was
overloaded; a cold emulator restart without wiping data resolved the platform
failure. The final 33-check run and live exchange above are after recovery.

No existing friendship, public listing, game/save or release package was changed.
The live fixture supplies a prearranged admission snapshot; it does not exercise
room creation/Ready/launch. Separate-network/NAT acceptance and real-game testing
remain open. No new UI screen was added and no new screenshot is claimed.

### Artifacts and Commands

Installed only `io.github.eriark.abyssme.debug`, label `0.5.0-beta.1-debug`, code
310. It is an unreleased test-only Debug APK, not the signed public beta. The
installed base APK hashes on both targets match the local build:

```text
APK SHA-256: 58290797be4fe6a749eed8bb5d195c2057c16b508fc7e4f64e7cb2a7e75aebc2
Peer source ZIP SHA-256: a6fe805327f3366d7416df09fde18a9c7cab3cfcebde2142f50cf96d57186122
```

The ZIP contains 5,510 source entries with no `.git` metadata. Required upstream
and wrapper source paths were checked. The APK was inspected for the native library
and all five component license assets. These are local ignored artifacts only.

```text
gradlew :peer-core:testDebugUnitTest --rerun :app:testFdroidDebugUnitTest --rerun :app:assembleFdroidDebug :app:assembleFdroidDebugAndroidTest :peer-core:peerSourceBundle -PsymbianRelease=true -PsymbianSourceDir=build/symbian-source-check -Pandroid.injected.build.abi=arm64-v8a
adb -s DEVICE install -r -d -t APK
adb -s DEVICE shell am instrument -w -r -e class io.github.eriark.abyssme.peer.SecurePeerDeviceTest,javax.bluetooth.VirtualBluetoothConnectorDeviceTest,ru.playsoftware.j2meloader.multiplayer.MultiplayerCoreDeviceTest io.github.eriark.abyssme.debug.test/androidx.test.runner.AndroidJUnitRunner
```

With this ABI-injected build, use `app/build/symbian-release/intermediates/apk/`
and its `output-metadata.json`, not an older APK left in `outputs/apk/`. The app
is under `fdroid/debug`, instrumentation under `androidTest/fdroid/debug`.
Source bundle: `build/release-sources/abyssme-peer-corresponding-source.zip`.

The opt-in `FluxerLivePeerDeviceTest` requires `peerRole`, the designated existing
test friend in `peerAccount`, and matching fresh `peerRoom`/`peerAttempt` UUIDs on
the two devices. It only permits accounts named `abyssmetest*`, reuses normal
encrypted app credentials, and does not create friends or publish rooms.

Ignored local evidence: `build/peer-final-build.log`,
`build/peer-retroid-final.log`, `build/peer-emulator-final.log`,
`build/peer-live-retroid-verified.log`, `build/peer-live-emulator-verified.log`,
and reversed-role `build/peer-live-retroid-fixed.log`, `build/peer-live-emulator-fixed.log`, plus
JUnit XML under the app's combined-build and peer module test-results directories.
No public release, Nextcloud upload or shared-service deployment was performed.

## Reference

TrainerOS's `tools/remote-play-stream/transport` was reviewed read-only for its
pinned libdatachannel version and separation of authenticated signaling from
encrypted peer data. This Android module uses its own JNI/Java lifecycle rather
than linking the Qt helper. Upstream [build options](https://github.com/paullouisageneau/libdatachannel/blob/6b1e2e620f1e37f0eafeee702eaea0043cb305fd/BUILDING.md)
and headers were checked against the exact pinned source before implementation.
