# Paired Absorbed game check — 2026-11-10

Availability: local-only test source and private combined Debug APK. No production
runtime change, source push, APK distribution, release signing or live-service
check. Physical handheld testing remains deferred at the owner's request.

## Scope and artifacts

`ProvidedPairedGameTest` launches the same permitted Absorbed 1.0 / Plastic Cow
Games edition in two separate Android VMs. It uses production native encrypted
peer transport, `BluetoothPeerSession`, `GameNetworkLease`, `GameNetworkService`
and separate MIDlet processes. Admission and signaling are local fixtures with
two synthetic identities, not Fluxer accounts or room UI. A loopback-only Python
relay forwards signaling/control messages; game packets use the native peer
transport over the emulators' shared virtual Wi-Fi.

| Input | SHA-256 |
| --- | --- |
| Combined candidate `AbyssME-0.7.0-beta.2-debug.apk` | `afaab3a0ac6ada2877279cc2440a80f188a5c1073e6a83afda1ea8ef94eee53b` |
| Final instrumentation `paired-3-tests.apk` | `afff7dc0becc69c80a6f7b7389362d485fd62f9f061583a0f76a649454af6bee` |
| Absorbed JAR | `85039908335d90fabf5a43aa462f787e73be8e5e96f626a4fcf8793aefeb1abe` |
| Final test source before commit | `c63b6a4b833c5db01d21993366d617e9bda05b4755521b0116e072fd844852ad` |

The candidate is unchanged from the [functionality checkpoint](2026-11-10-functionality-candidate.md).
Instrumentation was rebuilt after adding assertions that the peer is prepared,
the lease is attached and no close callback occurred immediately before the
intentional host process death. The final test APK build succeeded in 14s.

Environment: Android 11 / API 30 x86_64 with ARM64 translation, emulator
37.2.13.0, two 1080×1920 AVDs in one isolated container, each with 2 GiB RAM and
two virtual cores; container limited to 8 GiB / six CPUs. The game profile is
explicitly 240×320. Audio is disabled. The final run uses console ports 5680/5682,
ADB endpoints `127.0.0.1:5681` / `127.0.0.1:5683` and a separate ADB server on
5040, with each AVD name checked before installation. Wi-Fi addresses and full
properties are retained in `run-7/identity.json`.

## Procedure and evidence boundary

Each endpoint converts its own UUID-named copy, verifies the game identity,
prepares encrypted peers and waits for both game adapters before release. The
operator selects Multiplayer → Host Game on the host, then Multiplayer → Join
Game on the guest, checking captures before each selection. In this edition,
Android Up maps to the Siemens Select code; on-screen 4/6 navigate horizontally.
The second round also adds a resume item, changing the initial menu position.
Some earlier injected numeric keys did not reliably move the guest menu; stepwise
on-screen input avoids treating an accidental single-player launch as a pass.

Each of two rounds uses a distinct generation/attempt and a fresh game process.
After manual gameplay observations, a bounded host marker authorizes the fixture
to check bidirectional packets and a still-live peer/lease, then kill its own
MIDlet. Both sides must observe closure and lease revocation before the next
round. The fixture removes only its own game/profile/save directories.

Packet counts include discovery/protocol traffic; they do not prove synchronized
positions or a completed match. Captures are manually reviewed; JUnit does not
recognize game pixels. Host process death is not Wi-Fi loss or NAT handover.
Neither synthetic admission nor fresh binding tests actual room UI rejoining.

## Results

**Run 7 passes one scenario per endpoint: host 246.246s, guest 242.226s.**
Both rounds reach the two-character level in reviewed host/guest captures. The
native peer and attached lease remain live immediately before each intentional
host MIDlet death; both sides then report closure and revoked leases. Round two
uses a new attempt and fresh processes. These timings include manual navigation.

| Round | Host sent / received | Guest sent / received |
| --- | --- | --- |
| 1 | 355 / 363 | 365 / 355 |
| 2 | 457 / 460 | 460 / 457 |

The counters are snapshots around closure, not packet-delivery equality checks.
Host closure reports game network termination; guest closure reports peer
disconnection and a rejoin instruction. Each endpoint records the same attempt
within a round, with distinct attempts across rounds. The relay reports no errors.
The final round-two pre-disconnect capture shows a changed character position;
partial redraws remain visible. This is bounded launch/exchange/termination and
fresh-session evidence, not a synchronized-match verdict.

## Failed attempts and unresolved observations

- Initial compilation used an inaccessible helper; the fixture now explicitly
  selects its profile. Run 1 stopped before testing because the runner attempted
  to grant an obsolete permission; it now uses the app's external-storage app-op.
- Run 2 used two containers sharing `/tmp`; both emulator processes exited 139.
  The exact cause was not established. A single container with two AVDs replaced
  that setup. The retained `abyssme-pair-emulators.py` describes the failed setup,
  not the final environment.
- Run 3 reached encrypted startup, but mistaken guest navigation entered
  single-player and exited. Host traffic and peer-ended assertions failed.
- Run 4 passed the earlier fixture on both endpoints (428.317s / 428.343s), with
  paired levels in both rounds and packet exchange. It lacked the stronger
  alive-before-disconnect assertions and is superseded by the final run.
- Run 5 passed round-one live-peer/closure checks, but the guest instrumentation
  aborted with `System has crashed` during round two; ending the host attempt
  failed its traffic assertion (337.766s). A concurrent project had started an
  emulator on the same 5582 port. A diagnostic read returned that other AVD's
  records, so run 5 diagnostics cannot establish our guest's crash cause. Run 6
  was aborted after detecting the port conflict and is not acceptance evidence.
  Only our Debug package was stopped and our container shut down; the other
  project's emulator was left running. The final run uses distinct ports and
  an isolated ADB server with explicit AVD identity checks.
- Earlier Absorbed captures contain retained menu/image fragments and a partial
  `Problem with bt connection` banner. These observations are not explained by
  packet counts alone and are not declared fixed. Different positions/timers in
  asynchronous captures also do not establish accurate synchronization. See the
  prior [edition-specific observations](../BLUETOOTH_GAME_SELECTION.md).

Remaining work: diagnose Absorbed rendering/input and the connection banner,
verify sustained synchronized play and a completed match, then actual room
exit/rejoin, different networks, audio and physical controls. Mophun/Symbian
network adapters remain separate unproven work. No universal game compatibility
or public-release acceptance follows from this check.

## Local reproduction material

Ignored evidence lives in `build/checks/2026-11-10-paired-game/`: per-run host/guest
instrumentation logs, manual input journals/captures, relay metadata without
signal payloads, round JSON results, identity and process records. The final
Python runner/key helper copies are retained there; they require private game
input and dedicated AVDs. Test APKs are under
`build/checks/2026-11-10-library-journey/`; no games or generated binaries are
added to Git. After the final run, five UUID-named fixture directories left by
aborted runs were removed from our two AVDs only, after checking the exact game
hash and absence of a MIDlet process; the cleanup journal is retained. Our paired
emulator container was stopped. Other project containers and the handheld were
not stopped. For manual reproduction, invoke both endpoints concurrently:

```sh
adb -P 5040 -s <endpoint> shell am instrument -w -r \
  -e class ru.playsoftware.j2meloader.multiplayer.ProvidedPairedGameTest \
  -e pairedGame Absorbed-paired.jar -e pairedRole <host-or-guest> \
  -e pairedRoom <same-fresh-uuid> -e pairedSignalPort 5590 \
  io.github.eriark.abyssme.debug.test/androidx.test.runner.AndroidJUnitRunner
```

The local relay must already be listening. Supply the input only at the Debug
app's `files/sweep/java/Absorbed-paired.jar`. After both endpoints report the
current gameplay window, perform and review guest input before creating the
host's `files/paired-game-<room>-host/round-<n>.continue` marker. Each marker has
a 900-second timeout. Do not run on a personal device or concurrently used AVD.
