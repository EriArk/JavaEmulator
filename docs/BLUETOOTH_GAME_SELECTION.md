# Additional Bluetooth Game Checks

Checkpoint started 2026-10-25 and continued after local midnight on 2026-10-26.
This is unreleased Debug evidence, not a public multiplayer release or complete
game compatibility certification. See [previous check](BLUETOOTH_GAME_CHECK.md).

## Targets and Fixtures

Retroid Pocket Classic (Android 14, physical hardware) hosts an Android 35
emulator through private Fluxer rooms and encrypted direct peer transport.
Both are on the same local network path; different-network/NAT behavior is not
accepted. ADB drives the game's original menus, not physical controller input.
The existing dedicated test friends are reused. No game files, account secrets,
commercial screenshots or release APKs are published.

Unmodified user-supplied JARs from the local Kevtech archive:

| Game | Version / vendor | SHA-256 |
| --- | --- | --- |
| 1916 Dogfight | 1.0.0 / HeroCraft | `230ae69eca147d56bb7a5b6d53e9d4497c15606691628b413769f478c6f0e57e` |
| 3D Constructo Combat | 1.0 / Concrete Software | `e3205350f86ababb06361886ef214f85f34530258569fffb4fa7d2fc98947e13` |
| Absorbed | 1.0 / Plastic Cow Games | `85039908335d90fabf5a43aa462f787e73be8e5e96f626a4fcf8793aefeb1abe` |

## Shared Repair

The initial Absorbed preparation attempts sometimes timed out before launching
the game: both peers had a running network timer and drained signaling queue,
but neither had accepted a remote description. A direct encrypted control test
passed (100 ordered 1,024-byte messages in each direction).

Code inspection found that replacing an unsent room snapshot removed it from
its original position and appended its replacement after pending peer offers.
The receiver can then see an offer before the room state that authorizes it.
Do not weaken admission or accept authority from an offer to compensate.

The shared mailbox now coalesces only the latest pending delivery to that peer,
without crossing peer signals, correlated admissions or terminal controls. A
regression test fails on the old ordering and passes after the change. Queue
bounds, expiration, authenticated authors and no automatic ambiguous-post retry
remain intact. This is not a game-name exception.

The manual live fixture now accepts `-e gameplaySeconds 900` (30-900 seconds,
default 300), and emits bounded local phase/queue flags while waiting. It does
not print credentials, SDP or provider message content. It also surfaces a
failed scheduled network task instead of hiding that failure behind a timeout.
Its automated pass means process/room lifecycle only, not successful gameplay.

## Observations

- **1916 Dogfight:** L2CAP discovery finds Player 1, the host roster contains
  both players, and both devices enter the network flight. Host team editing
  is required before Start becomes available. Direction/fire input produces
  changed scenes, radar/ammunition and projectiles. The first manual run exceeded
  the old five-minute window before normal exit: its fixture fails, despite
  the observed connection/gameplay. Do not count that timeout as a normal return
  or as a proven transport disconnect. Menus/flight show graphical artifacts.
- **3D Constructo Combat:** L2CAP discovery, join and gameplay reach both devices.
  Timer and view changes are visible. Normal host Exit game produces the guest's
  Game aborted screen and returns both room seats with fresh readiness. Both
  lifecycle fixtures pass (349.818 / 332.541 seconds). Dark geometry is visible;
  visual correctness and completed-match acceptance remain open.
- **Absorbed:** ordinary launch and room return work. After the mailbox repair,
  three successive preparations reached encrypted readiness and launched both
  processes. The first was exited through a key-layout mismatch; the second
  reached Host Game / Join Game but did not establish a match. The latter run
  returned normally through the application's Exit game (295.242 / 282.168
  seconds). These earlier runs are lifecycle passes, not Bluetooth gameplay passes.
  In the final host-first run, start Host Game before selecting Join Game on the
  guest. Both devices enter the same two-player level with moving objects,
  countdown and both player characters. Digit direction input changes positions.
  This confirms RFCOMM game startup, not sustained synchronization or a full match.
  Normal host Exit game returns both seats with fresh readiness; final lifecycle
  checks pass (194.173 / 181.607 seconds). The earlier guest-first search remains
  a failed attempt, not evidence of a packet failure or a completed match.

Absorbed inspects the presence of the Siemens Image class and chooses Siemens
soft-key codes (-1 / -4). With native arrows, Right is interpreted as Exit.
Digits 4/6 navigate its menus; -1 selects Host/Join. This is a vendor-detection
compatibility issue, not a changed JAR or Bluetooth packet failure. Its animated
screen also exhibits retained image fragments. These observations are not fixed
by the mailbox patch.

## Evidence and Availability

Initial Dogfight/Constructo observations used the prior Debug APK
`29f6f990edc7850ad3d0a60601b29f54088880352290db7cd1cd3c8dd5916332`.
The mailbox repair installs on both targets as
`afea7a83c4bacbec08bd5d3a452b68ef280b5340cbdc2672824ae7d4dd0c8786`;
installed base APK digests were checked. Both retain the development label
0.5.0-beta.1/code 310, distinct from the public release artifact.

The build and 370 app JVM tests pass; the unchanged peer-core suite has 12
passing tests (Gradle up-to-date in this build, not a newly executed run).
All 65 selected Android network/bridge/lifecycle checks pass on each target
after the mailbox repair; emulator time is 15.937 seconds. Logs are
`build/bt-selection-regression-{retroid,emulator}.log`.
Local ignored evidence:

- `build/bt-mailbox-order-before.log`: expected regression failure.
- `build/bt-mailbox-order-build.log`: successful app/test build and app suite.
- `build/bt-selection-transport-{retroid,emulator}.log`: direct peer control.
- `build/bt-dogfight-{retroid,emulator}.log`, `dogfight-flight-*.png`,
  `dogfight-input-*.png`: connection/gameplay followed by fixture timeout.
- `build/bt-constructo-{retroid,emulator}.log`, `constructo-gameplay-*.png`,
  `constructo-exit-guest.png`: gameplay and normal return.
- `build/bt-absorbed-final-*.log`: pre-repair preparation failures.
- `build/bt-absorbed-fixed-*.log`, `bt-absorbed-numpad-*.log` and
  `absorbed-*.png`: post-repair preparation, menus and return; no match pass.
- `build/bt-absorbed-host-first-*.log`, `absorbed-last-gameplay-host.png`,
  `absorbed-last-result.png`, `absorbed-last-input-guest.png`: final paired gameplay.

No new release, public APK, Nextcloud upload or capability enablement. Live
GitHub release metadata was not rechecked. Audio, saves, full matches, sustained
synchronization and remote-network traversal require separate acceptance.
