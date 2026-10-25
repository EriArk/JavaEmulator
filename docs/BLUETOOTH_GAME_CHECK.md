# Bluetooth Game Session Check

Unreleased Debug checkpoint, 2026-10-25. Builds on
[room-to-game launch](ROOM_GAME_LAUNCH.md). Release multiplayer capabilities
remain disabled. No public APK, release, Nextcloud upload or game redistribution.

## Shared Repairs

- Coalesce adjacent small writes on the same Bluetooth stream into bounded DATA
  frames. Polling still sends immediately; games do not need to call `flush()`.
  Do not merge across another stream or a control frame.
- Apply backpressure before the bounded transport queue fills, reserving room
  for discovery, close and receive-credit controls. Recompute receive credit
  after each wait and wake writers when the transport drains a frame.
- Revoke consumed local preparation before finishing the room's launch. Room
  change callbacks can run synchronously: retaining that preparation created a
  replacement transport during teardown, which cleanup then closed again.
- Preserve the room after normal game exit, with fresh preparation and Ready
  required. A late transport callback after synchronous teardown no longer
  tells returning players to rejoin an intact room. New preparation clears the
  return grace period so subsequent failures retain their own diagnostic.

These changes are shared stream/session behavior, not a game-name exception or
a modified game archive. Admission, exact-edition verification, encrypted peer
binding and private process leases remain required.

## Fixture and Scope

The locally supplied `BTBiplanes_s40v3.jar` identifies itself as **BT Biplanes**,
Morpheme Ltd, version 5.0, MIDP 1.0 / CLDC 1.0. Its SHA-256 is
`356e01e2b47fb0fd8868e6729f928cad4c0ad79d43c57195f82e6be4a08ea72c`.
It uses JSR-82 discovery, SDP and RFCOMM streams through its own Two Player Game
menu. The JAR and its contents remain local and ignored.

Targets are Retroid Pocket Classic / Android 14 and an Android 35 emulator.
Two existing dedicated Fluxer test friends provide private-room signaling;
game data goes through the encrypted direct peer transport, not chat. The
release app and existing library data are not cleared or replaced.

The paired fixture invokes normal room model actions and the native activity.
ADB input drives the unmodified game's Start Bluetooth Game / Join Bluetooth
Game menus and the application's normal Exit game confirmation. It is not
physical-controller acceptance. Automated lifecycle assertions are distinct
from visual game observations.

## Regression Evidence

- On the previous installed Debug APK, 4,096 one-byte writes without draining
  the transport failed with `Bluetooth transport queue is full`.
- The old host and guest lifecycle fixtures both created two transports where
  only one was allowed, before explicit preparation of another game attempt.
- New shared tests cover coalescing without flush, a 40 KiB stream crossing the
  receive-credit window, and alternating streams waiting for queue capacity
  without losing order. Existing discovery, metadata, L2CAP, close and malformed
  input checks remain in the suite.
- New Android lifecycle fixtures cover host-local and guest-remote termination,
  synchronous room callbacks, late close notices and explicit next preparation.

Local evidence includes `build/bt-small-writes-before.log`,
`build/bt-rematch-before-corrected.log`, `build/bt-rematch-build.log`,
`build/bt-return-final-build.log` and current JUnit XML. The earlier
`bt-rematch-before.log` used invalid fixture account IDs and is not defect proof.

## Final Installed Results

- **381 JVM tests pass**: 369 app tests and 12 peer-core tests, zero failures,
  errors or skips. The final app suite was rerun after the late-close notice fix;
  the unchanged peer-core suite passed in the same implementation checkpoint.
- **65 Android tests pass on each target**, including both new lifecycle cases:
  Retroid 12.010 seconds, emulator 16.069 seconds. Logs are
  `build/bt-final-regression-{retroid,emulator}.log`.
- The final live private-room run passed on Retroid as host (270.943 seconds)
  and emulator as guest (258.670 seconds). Both rounds prepared the exact same
  JAR, required Ready, attached both game processes and opened the game's own
  Bluetooth session. Captures show both planes and the shared score field.
- The first round ended through the host's normal Exit game confirmation; the
  second ended through the guest's. Both returned to the same room with Ready
  cleared. The second launch used fresh preparation without recreating the room
  or restarting the app. Both tests then left the private room normally.
- ADB direction/fire input was sent, but the captures do not establish sustained
  synchronized player movement or a completed match. This is two real-game
  connection/return cycles, not full gameplay acceptance.

The installed Debug base APK on **both** targets matches the local artifact:
`29f6f990edc7850ad3d0a60601b29f54088880352290db7cd1cd3c8dd5916332`.
It retains the 0.5.0-beta.1/code 310 development label and is not the public APK.
Live logs: `build/bt-match-final-{retroid,emulator}.log`. Inspected local captures:
`build/bt-final-round{1,2}-{host,guest}.png`, `build/bt-return-progress.png`,
and returned-room captures under `build/bt-final-*-captures/`.
These ignored files are evidence, not published assets.

Earlier paired runs in `bt-match-initial-*` exposed failed second preparation;
`bt-match-fixed-*` exposed the late incorrect Rejoin notice. They are failures,
not acceptance runs. The final paired run and final regression suites above
include all production repairs.

## Remaining Acceptance

The game displays retained menu fragments and cloud trails. Do not describe
this edition as fully compatible or visually correct. Audio, saves, a completed
match, sustained input synchronization and physical controls are not accepted
by a short connection check. Other Bluetooth games, separate networks/NAT,
relay provisioning and Symbian/Mophun adapters still need their own evidence.
Live GitHub release metadata was not rechecked in this checkpoint.

## Reproduction

Use the paired command in [ROOM_GAME_LAUNCH](ROOM_GAME_LAUNCH.md), selecting
`BT Biplanes`, and add `-e normalExit true -e launchRounds 2` on both targets.
The manual mode waits up to five minutes per round for ordinary game exit; it
does not manufacture a gameplay pass or kill a game during the successful path.
Each round requires new preparation and Ready. Test failure cleanup may stop
only its own game process and leave its own private room.
