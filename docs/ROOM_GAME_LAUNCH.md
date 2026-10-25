# Room-to-Game Launch

Unreleased checkpoint, 2026-10-25. Builds on the
[isolated process bridge](MIDLET_NETWORK_BRIDGE.md).

The later [Bluetooth game checkpoint](BLUETOOTH_GAME_CHECK.md) adds small-write
backpressure and normal-exit/repeat-session repairs, with two real-game
connection cycles on Retroid/emulator. The startup-only results below remain
the evidence for this earlier implementation stage, not its current test limit.

## Implemented

- Debug rooms can select an installed Java game. Selection reads the actual
  archive, verifies its SHA-256 and installed code/descriptor, and prepares the
  executable cache without constructing the MIDlet or changing its saves.
  A guest explicitly chooses the same edition; matching names are not evidence.
- Require completed local settings setup and one MIDlet entry. Verify that the
  installed descriptor selects the same class as the archive manifest. A local
  JAD cannot silently select a different entry from a multi-application JAR.
  Other platforms and ambiguous multi-entry suites are not offered here.
- Connect the room coordinator to a one-use process lease instead of an adapter
  in the main process. Keep credentials, room admission and signaling outside
  the game process. The existing encrypted transport carries game API traffic.
- Keep Prepared, manual Ready and process attachment distinct. The host starts
  only after both players are prepared and ready. Use the authenticated peer
  attempt for the launch, not an unrelated nonce.
- The game process installs its virtual Bluetooth adapter and acknowledges it
  through a private Binder call. MIDlet construction waits for the host's
  authenticated all-player attachment barrier. Duplicate, foreign, stale and
  mismatched acknowledgements cannot release it.
- Bound pending launches to 30 seconds using local monotonic time. A running
  match is not subject to the launch timeout. Completion, cancellation, process
  death, membership changes and disconnects invalidate the attempt and require
  fresh preparation/Ready; an old host snapshot cannot revive a cancelled game.
- Keep room polling/account checks alive while the separate game process covers
  the room. During return, retain a bounded 15-second owner grace period until
  an app activity resumes. On network failure, finish the game activity and give
  its destroy callback a bounded opportunity to run, returning to the room
  instead of stranding the user on a disconnected game/error dialog.
- Stop the fast network pump and discard late selection results after leaving
  the room. Returning players are told to prepare again, not to rejoin an intact
  room; successful preparation clears the earlier connection notice.
- Disabled actions remain visibly disabled in light and dark themes. Picker
  hashing/conversion runs off the UI and room/packet owner threads. Account,
  room and generation changes reject late selection results.

## Availability and Limits

**Debug only:** the normal Debug room exposes this experimental Java Bluetooth
path. Release capabilities remain empty. There is no new public APK, release,
Nextcloud upload or server change. Existing released graphics/defaults are not
modified. GitHub Actions remains disabled.

Selecting a Java game does not add multiplayer to a single-player game. Its
own built-in Bluetooth multiplayer mode is still required. Automatic matching
in a large library, multi-MIDlet selection, game-specific network compatibility,
a real two-player match and separate-network/NAT/relay behavior remain open.
Mophun/Symbian networking is not enabled by this work.

## Verification

- 378 JVM checks pass (366 app, 12 peer), zero failures/errors/skips. New coverage
  includes 11 room-launch protocol cases and six installed-catalog cases.
- Combined F-Droid Debug app and instrumentation APK build successfully. The
  APK retains version name `0.5.0-beta.1`, code 310, in the separate
  `io.github.eriark.abyssme.debug` package; it is not the public release binary.
- The first live run launched a real Bounce Tales MIDlet on both Retroid and
  the emulator through their existing dedicated Fluxer accounts. It exposed a
  guest return failure: polling stopped while the disconnected game/error
  activity still covered the room. This was fixed in this checkpoint, not left
  as a title-specific workaround.
- The subsequent paired run passed on Retroid as host (98.682 seconds) and the
  emulator as guest (86.730 seconds). It covers private invitation/admission,
  local edition verification, encrypted peer preparation, explicit Ready,
  coordinated process attachment, real MIDlet startup, host game-process death,
  return to the same room with readiness cleared, and orderly room departure.
  The test invokes the normal model actions and native room activity; it is not
  a physical-button test or manual picker acceptance.
- Bounce Tales is only a startup/return fixture here, **not a multiplayer match**.
  Its resource archive SHA-256 matches on both devices:
  `d69de01b5e45267820b00386e45db2296b4ca761708dcca6ca4ab03f3c604e39`.
  Captures show its menu on Retroid and splash on the emulator. No game payload,
  save, credential or ROM is committed. No friendship creation/data clear or
  release-package replacement was performed.

The reverse-role run also passed: emulator as host (101.612 seconds), Retroid as
guest (86.901 seconds). That artifact includes stale-host cancellation, duplicate
selection and early network-load failure guards. Its 60 Android checks passed on
each target (Retroid 11.006 seconds; emulator 14.213 seconds), including a new
separate-process barrier test proving the client stays waiting until release.
Those checks used SHA-256
`f36d10452a097c2bd372b251f7f5f16c5087e323931f5222766dfb3a9fb52a77`.

The delivery build additionally corrects the return notice, clears it after new
preparation and stops the fast packet timer on room departure. Debug APK SHA-256:
`cb847a0a5fe35453f88d1ffebf11137a79ed019dcbbae8c3ab868ec401672c4e`.
Both installed base APKs match that hash. On this exact artifact the live paired
scenario passed again: Retroid host 108.440 seconds, emulator guest 89.223 seconds,
including the corrected return notice. All 60 Android regressions passed again
on Retroid (11.091 seconds) and the emulator (13.994 seconds); no skips or failed
instrumentation starts in these delivery runs. The 378 JVM tests were rerun too.

## Reproduction

```text
gradlew :app:testFdroidDebugUnitTest --rerun :peer-core:testDebugUnitTest --rerun :app:assembleFdroidDebug :app:assembleFdroidDebugAndroidTest -PsymbianRelease=true -PsymbianSourceDir=build/symbian-source-check -Pandroid.injected.build.abi=arm64-v8a
```

Use the APK metadata under `app/build/symbian-release/intermediates/apk/`.
Paired instrumentation is explicitly opt-in on two already signed-in dedicated
test friends, with the same locally installed game on each target:

```text
adb -s HOST shell "am instrument --user 0 -w -r -e class ru.playsoftware.j2meloader.multiplayer.FluxerLiveRoomLaunchDeviceTest -e peerRole host -e peerAccount GUEST_ACCOUNT_ID -e launchGame 'Bounce Tales' io.github.eriark.abyssme.debug.test/androidx.test.runner.AndroidJUnitRunner"
adb -s GUEST shell "am instrument --user 0 -w -r -e class ru.playsoftware.j2meloader.multiplayer.FluxerLiveRoomLaunchDeviceTest -e peerRole guest -e peerAccount HOST_ACCOUNT_ID -e launchGame 'Bounce Tales' io.github.eriark.abyssme.debug.test/androidx.test.runner.AndroidJUnitRunner"
```

Ignored evidence: `build/room-launch-delivery-build.log`, earlier failed guest
`build/room-launch-live-emulator.log`, corrected paired
`build/room-launch-return-{retroid,emulator}.log`, reverse-role
`build/room-launch-final-{emulator-host,retroid-guest}.log`, regression
`build/room-launch-regression-{retroid,emulator}.log`, delivery paired
`build/room-launch-delivery-{retroid,emulator}.log`, delivery regressions
`build/room-launch-delivery-regression-{retroid,emulator}.log`, captures under
`build/room-launch-*-captures/`, and the current JUnit XML. Live release metadata
was not rechecked; these are development results, not a beta announcement.
