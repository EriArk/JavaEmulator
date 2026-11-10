# Synthetic Java Multiplayer Lifecycle — 2026-11-10

Local verification of the existing #33–#37 implementation, after the approved
transfer/UI/style sequence. No new engine adapter or protocol change was needed
for the exercised cases. This does not certify live Fluxer service availability,
different-network connectivity or real-game behavior.

## Fresh Android execution

**52 tests pass, zero failures**, API 30 Google APIs x86_64 emulator with ARM64
translation, 119.242 seconds. Selected classes:

- `LobbyRuntimeLifecycleDeviceTest`: host-local and guest-remote termination,
  reentrant room callbacks, no implicit replacement transport, fresh preparation.
- `GameNetworkBridgeDeviceTest`: exact-content and single-use process authority,
  coordinated start barrier, revocation, worker shutdown, obsolete lease fencing,
  and encrypted cross-process Bluetooth exchange followed by actual process death.
- `VirtualBluetoothConnectorDeviceTest` (including shared contracts): JSR-82
  discovery, streams, L2CAP, invalid input and closure through the actual Connector.
- `SecurePeerDeviceTest`: native ICE/DTLS/SCTP, ordered binary round-trip,
  wrong binding/fingerprint rejection, bounded queues, repeated connect/close.
- `LobbyRoomDeviceTest`: invite/admission, recreation, return-to-room shortcut,
  explicit join confirmation, temporary-contact retention/cleanup, sign-out.
- `MultiplayerCoreDeviceTest`: journal isolation, write-before-provider mutation,
  framing and filesystem validation.

The Debug-only `GameNetworkProbeService` acts as a synthetic Java game process.
It uses normal MIDlet network attachment and Connector discovery/streams to echo
64 deterministic 4096-byte packets through the encrypted peer path. No commercial
game, account credentials or external service is involved. Room HTTP and partner
responses are fixtures; both native peer endpoints run on the same emulator.

The earlier fresh **424 JVM test** result includes `LobbyLaunchTest`,
`LobbyPeerCoordinatorTest` and `LobbyRoomTest`: wrong-edition preparation,
Ready/start, cancellation, backgrounding, disconnect, stale callbacks, retained
room and a fresh next attempt. Multiplayer source is unchanged since that run;
no additional JVM execution is claimed here.

## Availability and limits

APK/test APK snapshot: local style commit `a43ddcdf`. Matching artifacts are in
ignored `build/checks/2026-11-10-palettes/`; this run's log and hashes are in
`build/checks/2026-11-10-multiplayer/`. Nothing was published or released.
The debug probe is excluded from Release. No physical controller, live account,
NAT traversal, mobile-network recovery or complete-game acceptance is claimed.
The connected handheld still awaits ADB authorization. Existing Java-only
multiplayer scope remains experimental; Symbian/Mophun adapters stay deferred.
