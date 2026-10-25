# Isolated Game-Process Network Bridge

Unreleased checkpoint, 2026-10-25. Extends the
[encrypted Bluetooth adapter](BLUETOOTH_PEER_BRIDGE.md). This is runtime plumbing,
not an enabled multiplayer game launch or evidence of a complete match.

## Implemented

- Add `PeerPacketPort` so the secure peer can exchange packets with either the
  existing in-process backend or a separate Android game process. Account
  credentials, signaling and native peer ownership remain in the main process.
- Add an unexported bound service and a one-use local launch lease. Bind the
  lease to the admitted account, room generation, peer attempt, seats, exact
  installed game and canonical path. A repeated ticket, wrong path, stale
  authority or changed installation cannot claim it.
- Hash the installed `res.jar`, converted code and descriptor before preparation;
  recheck their bytes before handing over the pipe. Require the resource archive
  hash to equal the room selection. Saves are excluded. This is identity evidence
  for an installation, not a sandbox for hostile code in the same Android UID.
  Slow verification runs outside the packet/lease lock, so the room can continue
  polling and revoke a launch before verification completes.
- Use a private descriptor socket pair rather than blocking game operations in
  Binder or the room/UI thread. Two bounded queues per endpoint allow at most
  256 packets / 1 MiB each; packets are at most 16,383 bytes. Overload, malformed
  framing and EOF fail explicitly. Socket shutdown interrupts both IO workers.
- `MicroActivity` waits for explicit network attachment before creating a MIDlet.
  An ordinary launch has no ticket and follows the normal path. Failed network
  preparation does not silently open a single-player game or physical Bluetooth.
  Late attachment respects an open pause menu, and late failure does not display
  a dialog on an already destroyed activity.
- Install the virtual adapter in the game process. Keep it installed but closed
  after disconnection until that process ends. Bind client death to the exact
  old lease, including cases where Android kills the process before `onDestroy`.
  An old service/lease cannot remove a newer registered launch.
- Reject the ordinary in-place restart path for a network game; it must return
  to its room for a new coordinated attempt. Ordinary restarts remain available.

A lease waits at most 120 seconds before being offered and 30 seconds after its
launch offer, using local monotonic time. The owner must continue polling and
revalidate/close it on authority changes. No ticket persists across main-process
death. The service is not a replacement for room admission or the host's launch
protocol; the production room coordinator does not issue these leases yet.

## Verification

- **361 JVM tests passed**, zero failures/errors/skips: 349 app and 12 peer.
  Eight new cases cover exact installed-file checks, wrong paths/hashes,
  changed code/resources/descriptor and save-data independence.
- **59 Android checks passed on each target:** Retroid Pocket Classic / Android
  14 in 10.513 seconds, and the Android 35 emulator in 13.295 seconds. Includes
  nine bridge cases plus the existing 50 backend/API/native/core regressions.
  The emulator runs the arm64 APK through its native translation bridge.
- On **each** target, two real encrypted native peer endpoints connect locally.
  A debug-only service in a separate `:networkProbe` process runs the actual
  `LocalDevice` discovery and `Connector` RFCOMM APIs through the same
  `MidletNetworkClient` used by `MicroActivity`. A deterministic 256 KiB echo
  passes; the fixture asserts a different PID. Killing that process revokes its
  lease. Revoking the lease from the owner leaves a closed virtual adapter,
  without falling back to the Android radio. Queue overflow and malformed
  packets stop both socket workers. A deterministic slow-verification fixture
  proves packet polling and revocation remain available, and no descriptor is
  granted after cancellation.
- The first device attempt exposed a test-manifest integration mistake: Mophun's
  production Gradle overlay replaces the generic Debug manifest. The isolated
  fixture is now registered in the F-Droid/Phone Debug variant manifests only.
  It is not in Release. The final suites pass after that correction.
- The final emulator install's first instrumentation start returned `failed to
  attach` before any test ran. A retry on the same installed APK passed all 59
  checks. Both the failed start and successful retry logs are retained; this
  result is not presented as uninterrupted first-attempt acceptance.
- Earlier in this checkpoint on Retroid, launch the existing open-source Sudoku J2ME from the library,
  inspect its rendered board, restart through Game menu and exit back to the
  library. This checks the ordinary `:midlet` path after the startup change.
  Input was injected through ADB, not a physical-controller acceptance test.
  This visual smoke used the preceding Debug build (hash starting `16c20bf8`),
  before the late-callback/menu and verification-lock guards. The final artifact
  below has the full repeated automated suites, not a repeated visual smoke.

The encrypted IPC fixture is **not a two-device match**, a loaded MIDlet, or a
production room launch. It uses locally constructed admission identities and
does not contact Fluxer. The previous real-account Retroid/emulator API exchange
remains documented in the earlier checkpoint, but was not rerun here. No account
reset, new friendship, server deployment, release-package replacement or data
clear was performed. Games/firmware/private credentials are not added to Git.

## Remaining Work

Historical boundary below. The subsequent [Debug room workflow](ROOM_GAME_LAUNCH.md)
connects selection, coordinated process launch and return; Release game
capabilities remain disabled until actual-match/network acceptance.

1. Connect the verified installed-game picker and exact-match lookup to the room.
   Replace the coordinator's local adapter with the process port for real launches.
   Complete normal preparation before taking the file snapshot and agree on the
   selected MIDlet entry when a JAR contains multiple applications.
2. Implement authenticated Ready/launch/attachment acknowledgements and preserve
   room polling/admission while its activity is covered by a game in another
   process. Account switch, timeout, backgrounding and cancellation must revoke
   the current attempt, not a later one.
3. Coordinate completion and return to the same room with fresh readiness. Then
   exercise an actual two-device Java match and different-network/NAT behavior.

Production capabilities remain empty. Symbian/Mophun networking, STUN/TURN
deployment, relay fallback, physical gameplay and full-game support are not
established here. No new public APK, release or Nextcloud upload. Public release
metadata was not rechecked in this checkpoint.

## Reproduction and Local Artifacts

```text
gradlew :app:testFdroidDebugUnitTest --rerun :peer-core:testDebugUnitTest --rerun :app:assembleFdroidDebug :app:assembleFdroidDebugAndroidTest -PsymbianRelease=true -PsymbianSourceDir=build/symbian-source-check -Pandroid.injected.build.abi=arm64-v8a
adb -s DEVICE shell am instrument -w -r -e class ru.playsoftware.j2meloader.multiplayer.GameNetworkBridgeDeviceTest,io.github.eriark.abyssme.peer.SecurePeerDeviceTest,javax.bluetooth.VirtualBluetoothConnectorDeviceTest,javax.bluetooth.PeerBluetoothBackendTest,ru.playsoftware.j2meloader.multiplayer.MultiplayerCoreDeviceTest io.github.eriark.abyssme.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Installed only the separate `io.github.eriark.abyssme.debug` test-only APK,
file `AbyssME-0.5.0-beta.1-debug.apk`, Android version name `0.5.0-beta.1`, code 310.
Both installed base APK hashes
match the local artifact:

```text
203edd42f050dcddc5c5163d7d77d62d91bf48ef244168a2e66cec76c8593849
```

Use `app/build/symbian-release/intermediates/apk/` with `output-metadata.json`,
not old `outputs/apk` files. Ignored evidence: `build/game-ipc-build.log`,
`build/game-ipc-final-repack.log` (final Java lifecycle and lease fixes, reusing the
unchanged native outputs from the successful combined build),
`build/game-ipc-retroid-final.log`, `build/game-ipc-emulator-retry.log`, the failed
emulator-start log `build/game-ipc-emulator-final.log`, JUnit XML, and
`build/game-ipc-smoke-{sudoku,restart}.png`. These local smoke captures are not
new public product screenshots. No native dependency or source-bundle change.
