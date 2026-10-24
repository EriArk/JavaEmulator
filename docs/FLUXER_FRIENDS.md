# Fluxer Friends Checkpoint

2026-10-24. **Unreleased development source and a local combined Debug APK.**
The public 0.5.0 Beta 1 APK is unchanged; its live release state was not rechecked
in this checkpoint. Part of [#34](https://github.com/EriArk/JavaEmulator/issues/34).
This follows [Account](FLUXER_ACCOUNT.md) and [Chats](FLUXER_CHATS.md), not playable multiplayer.

## Available in Development

- Library menu > Friends, or the Friends tab beside Chats. Both use the same
  verified account and actor, with no second credential store or friend graph.
- Searchable Friends, Requests and Blocked lists. Requests distinguish incoming
  from outgoing. Names, full tags and relationship state are shown, not presence.
- Add by full `username#1234` tag; accept/decline incoming requests; cancel
  outgoing requests; remove friends; block and unblock. Destructive actions show
  the contact and require confirmation. Existing contacts are not silently
  accepted or unblocked through the Add dialog.
- Message opens an existing or new direct conversation for a current friend,
  never an automatic message. It retains the existing in-memory draft; Back
  returns to Friends. Group creation is not included.
- Portrait/landscape layouts, light/dark palette, controller-focusable rows,
  filter retention across rotation and dialogs styled to match the application.
  Account changes clear private state and dismiss pending dialogs/menus.

## Mutation Boundaries

Every action carries its account and observed relationship snapshot. Before a
write, a fresh relationship list must still match the identity, type and timestamp
and permit that action. Duplicate, malformed or inconsistent identities disable
actions. Writes are serialized with relationship refresh. Late callbacks from a
different account or closed actor are discarded.

Results are reconciled against the provider instead of optimistic list edits.
Unknown replies are not automatically replayed. A timeout can mean the action
was applied; the UI keeps that uncertainty visible while a GET refresh checks
current contacts. Rate limits postpone further actions; expired credentials
clear private state. Privacy/verification requirements are not bypassed.

The HTTPS transport adds only authenticated relationship POST/PUT/DELETE and
private-channel POST routes to its existing fixed-origin allowlist. No bulk
relationship changes, arbitrary user endpoints, redirects, background requests,
new credentials or persistent contact cache are introduced.

Protocol checked against official [relationships](https://docs.fluxer.app/http-api/users/relationships/)
and [private channels](https://docs.fluxer.app/http-api/users/private-channels/).
TrainerOS `FluxerSession.cpp` was consulted read-only for tag requests, relationship
actions and DM opening; no TrainerOS files were changed.

**Limits:** a preflight GET is not an atomic server-side conditional mutation.
Another client can change the relationship between GET and PUT/DELETE/POST.
Declining an incoming request does not promise to clear the sender's outgoing
record. No CAPTCHA flow, background notifications, avatar download or presence
is implemented here. Temporary lobby contacts are not enabled: their journal
ownership and manual friend actions must be coordinated before automatic cleanup
is connected. See [next stages](MULTIPLAYER.md#next-implementation-order).

## Verification

Windows / JDK 21:

```powershell
.\gradlew.bat :app:testFdroidDebugUnitTest --rerun :app:assembleFdroidDebug :app:assembleFdroidDebugAndroidTest :app:lintFdroidDebug -PsymbianRelease=true -PsymbianSourceDir=build/symbian-source-check
```

- **187 JVM tests passed**, zero failures/errors/skips, including 26 new friend
  cases. Cover stale type/timestamp, account changes, duplicate actions, preflight
  errors, unknown replies, 429/401, tag validation, numeric suffix normalization,
  existing contacts, blocked users, DM validation/reuse and malformed identities.
- Combined app/test builds and lint passed. Existing programmatic English UI lint
  warnings remain; this is not a zero-warning claim.
- **21/21 Android tests passed** on Retroid Pocket Classic / Android 14:
  11 chat/friend checks, six account checks and four foundation checks.
- Friend checks use the real Activity, menus, dialogs and RecyclerViews with an
  in-memory provider in the test APK. They exercise rotation/filter retention,
  light/dark layouts, accept/block confirmation, invalid/valid tags, draft-preserving
  navigation and an applied mutation whose reply is lost, without duplicate writes.
- Popup interaction uses Android accessibility window enumeration and injected
  touch events. The suite also includes synthetic controller input from the chat
  checks, not human physical-controller acceptance. A failed popup lookup in the
  first pass was corrected in the test helper; production actions were not bypassed.
- The live anonymous account test created, polled and cancelled a pending browser
  handoff. Actual account approval, authenticated restore/logout and two-account
  friend actions/chat exchange remain **unverified**. No emulator run this time.
- [Six device screenshots](../screenshots/development/2026-10-24-fluxer-friends/README.md)
  show named test contacts, not real users or production network acceptance.

Device command:

```text
adb shell am instrument -w -r -e fluxerLive true -e class ru.playsoftware.j2meloader.multiplayer.FluxerChatsDeviceTest,ru.playsoftware.j2meloader.multiplayer.FluxerAccountDeviceTest,ru.playsoftware.j2meloader.multiplayer.MultiplayerCoreDeviceTest io.github.eriark.abyssme.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Local ignored evidence: `build/fluxer-friends-final-build.log`,
`build/fluxer-friends-retroid-final.log`, `build/fluxer-friends-final-captures/`
and JUnit XML under `app/build/symbian-release/test-results/testFdroidDebugUnitTest/`.
Installed only to the separate `.debug` package; official app/data retained.
Debug APK still carries 0.5.0-beta.1 / code 310, SHA-256:
`e298e30028b09786972070416ef92e112850d772c546b4b440547a130b3857e8`.
This is not the public release binary. No APK upload, bot/server, Nextcloud or
GitHub Actions change. No game, ROM, token or private account data committed.
