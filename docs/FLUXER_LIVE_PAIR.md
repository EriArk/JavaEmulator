# Live Fluxer Chat and Room Checkpoint

2026-10-24. Unreleased source and local combined Debug APK only. One physical
Retroid Pocket Classic (Android 14) and one Android 35 emulator ran independent
accounts against the real Fluxer service. The emulator is not a second physical
handheld. No production account, public APK or shared service was modified.

## Observed

- An authorized second test account completed ordinary browser-approved Android
  sign-in. Its mailbox remains under provider review; email verification is still
  pending. Fluxer disallows initiating friend requests from that account, but
  normally permits accepting an incoming request and messaging the resulting friend.
  No registration, verification or provider restriction was bypassed.
- The first account sent a friend request from AbyssME on Retroid. The second
  accepted in the official web client. Text sent from the web reached Retroid;
  the reply from Retroid appeared in the web client and in the app conversation.
- With both accounts signed into AbyssME, Retroid hosted an invite-only room and
  the emulator accepted. Both showed two members, survived activity recreation,
  and completed guest leave / host close without removing the existing friendship.
- Roles were reversed: the emulator hosted and Retroid joined. Both members
  returned to connected state after recreation. The guest remained for 45 seconds,
  covering periodic heartbeat exchange, before leaving. The host observed departure
  and closed its room. This is not a long-duration or adverse-network soak test.
- Ready remained disabled throughout: there is no registered online game adapter.
  These checks establish account/chat/private-room behavior, not playable multiplayer.

## Defect Found and Repaired

The first paired run failed to deliver an invitation to the room UI even though
the private message reached Fluxer. Retroid's clock was approximately 22 seconds
ahead of the emulator. A message created with a 90-second lifetime was rejected
because the receiver required its expiry to be no later than its own `now + 90`.
The invitation's 300-second deadline had the same assumption.

`LobbyExpiry` now permits up to 60 seconds of additional future clock skew in
room envelopes, invitation offers, directory records, publication results and
temporary-contact envelopes. Sender lifetimes are unchanged. Messages whose
expiry has already passed are still rejected, as are excessive future deadlines;
there is no expired-message grace period. Replay serials, room incarnation,
authenticated sender and recipient-bound offer checks remain intact. Directory
tombstones still parse without resurrecting an expired room.

This is bounded tolerance, not clock synchronization. Larger clock errors or
delivery delays that exhaust the remaining lifetime can still prevent admission.
JVM cases exercise positive/negative skew, future bounds, expiry, and the existing
replay/authority checks. The same strict assumption was observed in TrainerOS
source during read-only comparison; that checkout was not changed here.

The Friends tab also now displays contact status independently of the chat list.
An empty conversation list no longer makes Friends say `No conversations yet.`

## Verification and Reproduction

- Combined Debug app/test builds and `lintFdroidDebug`: passed. All **287 JVM tests** passed with zero
  failures, errors or skips (212 in the multiplayer package).
- Retroid chat/friends native fixture suite: **12 tests passed**, no skips.
- Assisted real browser approval on the emulator: **1 test passed**.
- Real paired room checks: **4 role runs passed** across two configurations.
  First pair: host 49.949s / guest 35.838s. Strengthened reverse pair: host
  92.918s / guest 75.987s, including connected-member and heartbeat assertions.
- UI checks use fixture HTTP; the paired test uses production actors and real HTTP.
  Room commands are invoked through the model, not a claim of physical-button
  acceptance. The chat/friend exchange above was separately exercised through UI.
- A fresh ordinary app launch after the role runs still displayed the test friend
  in Friends, with `Contacts up to date.` and the provider friendship state.

Build with the existing local toolchain:

```text
gradlew.bat :app:testFdroidDebugUnitTest --rerun :app:assembleFdroidDebug :app:assembleFdroidDebugAndroidTest -PsymbianRelease=true -PsymbianSourceDir=build/symbian-source-check --console=plain
```

Use two explicitly authorized test accounts that are already friends and signed
in normally. Start the following on separate devices, substituting their exact
friend usernames/tags. Do not target an arbitrary account. The test creates,
invites, accepts and closes its own private room; it refuses to replace an
existing room and does not create/delete a friendship.

```text
adb -s HOST shell am instrument -w -r -e fluxerPairRole host -e fluxerPairPeer GUEST_NAME -e class ru.playsoftware.j2meloader.multiplayer.FluxerLivePairDeviceTest io.github.eriark.abyssme.debug.test/androidx.test.runner.AndroidJUnitRunner
adb -s GUEST shell am instrument -w -r -e fluxerPairRole guest -e fluxerPairPeer HOST_NAME -e class ru.playsoftware.j2meloader.multiplayer.FluxerLivePairDeviceTest io.github.eriark.abyssme.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Without opt-in/saved accounts, these checks skip rather than establish acceptance.
The optional `fluxerApproveSignIn=true` account test requires an operator to approve
the short-lived displayed code normally. It reports only that user code, never
the session token/poll secret; keep its transient logs private. It never replaces
an existing saved account.

Local ignored evidence: `build/fluxer-skew-build.log`,
`build/fluxer-pair-final-build.log`, `build/fluxer-pair-chat-ui.txt`,
`build/fluxer-pair-host.txt`, `build/fluxer-pair-guest.txt`,
`build/fluxer-pair-reverse-host.txt`, `build/fluxer-pair-reverse-guest.txt`,
and `build/fluxer-pair-*-before-skew-fix.txt` (original failures).

App SHA-256: `1ee917688a787526550edb5cab28df89282b4b79b90e8776462ebe39d330d0e8`.
Label remains `0.5.0-beta.1-debug`, code 310, separate Debug package, not a release
replacement. [Screenshots and provenance](../screenshots/development/2026-10-24-fluxer-live-pair/README.md).

## Still Open

Public advertisement/acknowledgement and product isolation against the deployed
shared service, non-friend admission and automatic temporary-contact cleanup,
Keep over real accounts, group chat, live logout, physical gamepad acceptance,
game preparation and runtime network adapters. The shared service extension
has not been deployed by this work. Do not describe this as released netplay.
The public release listing was rechecked on 2026-10-24: `v0.5.0-beta.1`, published
2026-10-23 21:32:23 UTC, remains the newest listed prerelease. Assets were not
redownloaded or replaced in this checkpoint.
