# Virtual Java Bluetooth Adapter

Status: **unreleased, transport-independent implementation**, 2026-10-24.
Part of [#37](https://github.com/EriArk/JavaEmulator/issues/37); encrypted peer
transport is tracked separately in [#36](https://github.com/EriArk/JavaEmulator/issues/36).
This is not playable Internet multiplayer and does not enable a Lobby Ready mode.

## Implemented

- Route the game's existing `LocalDevice`, `DiscoveryAgent`, `RemoteDevice` and
  `Connector.open("btspp://..." / "btl2cap://...")` APIs to an explicitly installed
  virtual session. No Android Bluetooth radio, scan or pairing permission is
  requested for that route. Without an installed session, the existing physical
  backend remains selected. A missing physical radio now reports power off.
- Asynchronous device/service discovery, cached devices, UUID intersection,
  service selection, listener-owned cancellation and single terminal callbacks.
  A cancelled operation cannot deliver results into a new search using the same
  listener. User callbacks do not hold the session shutdown lock.
- Local service records, names, custom SDP attributes, numeric connection ports,
  updates and withdrawal. Copy metadata before crossing the transport boundary;
  reject cyclic/deep trees, excessive attributes/strings and invalid IDs. Search
  matches UUIDs in nested attributes, not just a hard-coded game identifier.
- RFCOMM byte streams and L2CAP packet connections/notifiers. L2CAP uses the
  existing bounded framing implementation over an ordered stream, including MTU
  negotiation checks, empty packets, truncation and discard of the unused tail.
  Physical Bluetooth wire traffic is **not** given this framing.
- Honor IO modes and CLDC stream lifetime: closing a connection prevents new
  streams but retains already-open streams until they close. Closing the entire
  session forcibly interrupts blocked reads/accepts and rejects subsequent IO.
- Reject peers/records from another backend instance, mismatched returned channel
  identities, malformed URLs, duplicate parameters and excess live connections.
  A closed installed session stays selected and fails closed until its owner
  detaches it after stopping the MIDlet. It never falls back to the physical radio
  while the old game is still running. Virtual sessions keep a separate local
  device cache, preserving an existing physical device/listener when switching.

Implementation: [VirtualBluetooth](../app/src/main/java/javax/bluetooth/VirtualBluetooth.java),
[bounded attributes](../app/src/main/java/javax/bluetooth/VirtualServiceAttributes.java),
and small delegations in the existing JSR-82 classes and connection factories.
These are original GPL-3.0-only adapter sources; no new binary dependency.

## Transport Contract and Remaining Work

`VirtualBluetooth.Backend` is scoped to **one authorized launch attempt**. Its
implementation must provide admitted peer identities, discovery/service metadata,
dedicated reliable ordered channels, negotiated MTUs and cancellation of all IO
on close. These are requirements of the missing production backend, not claims
that this Java class establishes encryption or authenticates an arbitrary socket.
Security-query methods describe the already-authorized virtual channel; they do
not implement radio pairing or make the test fixture encrypted. PREKNOWN does
not invent Bluetooth bonds.

The current production capability set remains empty. There is **no production
caller of `install()`**, no transport registration, room-to-MIDlet launch binding,
ICE/signaling adapter, NAT traversal or gameplay data carried in Fluxer messages.
Discovery and streams are exercised with an injected test backend only.

Supported subset: RFCOMM and connection-oriented L2CAP, maximum 672-byte MTU,
one active service search, up to 31 remote peers/32 live connection handles,
256 attributes and a bounded 16 KiB metadata budget. OBEX, vendor Bluetooth APIs,
physical-to-virtual cross-play, full SDP protocol negotiation and radio role or
service-class emulation are not implemented. Backend serialization of custom
attributes and honoring discoverability still require production transport work.
No individual commercial game has been verified with this adapter yet.

Reviewed TrainerOS reference (read-only):
`tools/remote-play-stream/transport/README.md` and its pinned libdatachannel
ICE/DTLS/SCTP design. Its Qt/Linux helper is **not** an Android dependency.
The subsequent [2026-10-25 secure peer core](SECURE_PEER_TRANSPORT.md) now supplies
an Android encrypted channel and live signaling/packet evidence. It is not yet
connected to this adapter's backend or the production game launch workflow.
At this adapter checkpoint, next work was an Android-compatible peer backend with authenticated
room/generation/seat/launch-attempt binding, followed by game preparation,
synchronized launch, an actual two-device Java game and separate-network checks.
Do not enable an engine capability merely because the adapter tests pass.

API references: Oracle's [JSR-82 discovery and connection overview](https://www.oracle.com/technical-resources/articles/javame/bluetooth-wireless-technology-part1.html),
[service lifecycle](https://www.oracle.com/technical-resources/articles/javame/bluetooth-wireless-technology-part2.html),
and [CLDC connection/stream close contract](https://docs.oracle.com/javame/config/cldc/ref-impl/cldc1.1/jsr139/javax/microedition/io/Connection.html).

## Verification

Final combined Debug app and instrumentation APK builds pass. **312 JVM tests**
pass with zero failures, errors or skips (294 previous cases plus 18 adapter
contracts). **23/23 Android checks pass on each target**: Retroid Pocket Classic
Android 14 (0.464 seconds reported by the runner) and the Android 35
`J2ME_Portable_Preview` emulator (0.649 seconds). Each run includes the 18 shared
contracts, the native Connector route and four previous multiplayer core checks.
These are short API/IO tests, not a timed gameplay benchmark.

The first passes also succeeded (306/17, then 310/21 before additional contracts).
Review of cancellation and the CLDC stream-close specification exposed lifecycle
gaps during implementation; those were fixed and covered before the final run.

The same [shared contract suite](../app/src/sharedTest/java/javax/bluetooth/VirtualBluetoothTest.java)
runs on JVM and Android. The [device extension](../app/src/androidTest/java/javax/bluetooth/VirtualBluetoothConnectorDeviceTest.java)
also exercises the ordinary `Connector` route for both protocols. Its test-only
backend uses real TCP loopback streams inside one process; it does not relay game
traffic between devices or require account credentials.

Installed only to `io.github.eriark.abyssme.debug`, keeping the release package,
library and accounts intact. Label `0.5.0-beta.1-debug`, code 310, is an unreleased
development build, not the signed public beta. APK SHA-256:

```text
546f8c0d934487113ef24a9b0ea7807d6506cb8d5e6470901d883c40ecee77e8
```

Local ignored evidence: `build/virtual-bluetooth-verified-build.log`,
`build/virtual-bluetooth-retroid-verified.log`,
`build/virtual-bluetooth-emulator-verified.log`, and JUnit XML under
`app/build/symbian-release/test-results/testFdroidDebugUnitTest/`.
No real-game discovery/gameplay, two-device traffic, physical-radio pairing,
controller input, separate-network/NAT or full-MIDlet lifecycle acceptance is
claimed. The emulator run is an additional platform check, not the other half
of a Retroid multiplayer match.

Commands:

```text
gradlew.bat :app:testFdroidDebugUnitTest --rerun :app:assembleFdroidDebug :app:assembleFdroidDebugAndroidTest -PsymbianRelease=true -PsymbianSourceDir=build/symbian-source-check
adb -s DEVICE shell am instrument -w -r -e class javax.bluetooth.VirtualBluetoothConnectorDeviceTest,ru.playsoftware.j2meloader.multiplayer.MultiplayerCoreDeviceTest io.github.eriark.abyssme.debug.test/androidx.test.runner.AndroidJUnitRunner
```

No public release, Nextcloud upload, game/firmware publication, account mutation
or directory-service deployment belongs to this checkpoint. No app screen was
redesigned, so no new UI screenshots are represented by these adapter checks.
