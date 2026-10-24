# Room Contacts Checkpoint

2026-10-24. Unreleased development source; no public APK or bot deployment.
Continues [rooms](LOBBY_ROOMS.md), [friends](FLUXER_FRIENDS.md) and
[#34](https://github.com/EriArk/JavaEmulator/issues/34).

## User Flow

- Admitted host/member pairs can establish temporary Fluxer contacts. Browsing
  the directory, opening a link or receiving an invitation does not add friends.
- My room shows connecting, connected, pending Keep and cleanup states. Keep as
  friend retains the relationship; Open Friends leads to the existing friend UI.
- Leaving/closing begins bilateral cleanup. Existing friends, requests and blocks
  are not adopted by automation. Unknown or changed relationships are retained
  for review rather than deleted speculatively.
- Unfinished contacts remain visible after leaving. The private account journal
  survives process death; foreground activity resumes negotiation/cleanup without
  restoring a stale room or Ready state. An offline peer can delay cleanup.

## Integration and Safety

The process-owned `LobbyRoomModel` binds the existing contact actor to verified
account identity, admitted room snapshots, fixed-origin HTTP and authenticated
direct messages. Contacts use an AbyssME-specific outer message prefix. The
inherited inner TrainerOS envelope is not a cross-product interoperability claim.
Control packets are hidden from ordinary chat history. No group chat is created.

Manual relationship mutations acquire the same owner's gate before their fresh
GET/write. Owned contacts relinquish cleanup persistently first; a guest waits
for the host's Keep acknowledgement. A lease pauses automation through the entire
request, including a late reply after logout/navigation. Plain messaging does
not mutate relationships and does not take that lease. Add by full tag takes
the lease too; an existing tag cannot silently accept or overwrite a contact.

Storage failure stops automatic changes. Unknown provider writes are not replayed.
The journal is account-scoped in Android no-backup storage, not game storage.
HTTP 429 defers contact requests. Native transport still validates exact methods,
numeric identities and its fixed API origin.

Mailbox polling prioritizes the directory bot and current participants. Historical
contact peers and pending join requests rotate through bounded slots. An active
conversation remains eligible beyond the 500 recent-DM cache; each poll reads at
most 32 conversations and 50 messages per conversation. Busy DMs can still push
control messages outside that latest-message window; this is not a background
push service or a guarantee for arbitrarily large rooms.

Provider privacy still applies. Initial DMs must be permitted before contact
negotiation can start. Fluxer has no timestamp-conditional DELETE: app-local
serialization cannot exclude a change made by another client between GET and
DELETE. The feature does not claim that stronger guarantee.

## Provenance

GPL-3.0-only Java adaptation of the owner's TrainerOS temporary-contact work.
This checkpoint additionally reviewed `TemporaryContacts::manual/keep` and the
manual friendship entry points in `FluxerSession.cpp` in the original read-only
TrainerOS checkout. Android adds an asynchronous cross-screen lease and also
guards full-tag additions. No Qt dependency, bot credential or server change.

## Verification

279 JVM tests passed, zero failures/errors/skips. This adds manual Keep persistence,
guest acknowledgement, busy/read/cleanup interlocks, failed journals, recovery
leases, delayed manual callbacks, full-tag guarding, contact-envelope isolation,
old-DM priority and rejection of premature Keep during an in-flight creation.
Combined Debug app/test builds and lint passed. All 34 Android checks passed on
Retroid Pocket Classic, including the live anonymous handoff test (no approved
account). Native contact checks exercise
the real room/contact/mailbox actors, Keep retention, manual-lease exclusion and
eventual cleanup with a fixture provider. Paired actors/native fixtures are not
real authenticated Fluxer acceptance or physical-controller/game acceptance.

[Two device captures and exact APK hashes](../screenshots/development/2026-10-24-lobby-contacts/README.md).
Ignored local evidence: `build/lobby-contacts-final2-build.log`,
`build/lobby-contacts-retroid-final.log`, and JUnit XML under
`app/build/symbian-release/test-results/testFdroidDebugUnitTest/`.

Commands (JDK 21, existing local SDK/toolchain):

```text
gradlew.bat :app:testFdroidDebugUnitTest --rerun :app:assembleFdroidDebug :app:assembleFdroidDebugAndroidTest :app:lintFdroidDebug -PsymbianRelease=true -PsymbianSourceDir=build/symbian-source-check
adb shell am instrument -w -r -e fluxerLive true -e class ru.playsoftware.j2meloader.multiplayer.LobbyRoomDeviceTest,ru.playsoftware.j2meloader.multiplayer.LobbyDirectoryDeviceTest,ru.playsoftware.j2meloader.multiplayer.FluxerChatsDeviceTest,ru.playsoftware.j2meloader.multiplayer.FluxerAccountDeviceTest,ru.playsoftware.j2meloader.multiplayer.MultiplayerCoreDeviceTest io.github.eriark.abyssme.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Only the Debug package was updated; official app/data retained. Public
`v0.5.0-beta.1` is still a prerelease published 2026-10-23 21:32:23 UTC, rechecked
2026-10-24. Its assets were not changed or redownloaded here. TrainerOS PR #260
is still open at `59dce1a`; no service deployment or new service test run here.

A user-authorized disposable mailbox was created through [Mail.tm](https://mail.tm/).
Ordinary Fluxer registration rejected its syntactically valid address with
`Invalid email address format`. No Fluxer account was created, no CAPTCHA was
bypassed, and no two-account live acceptance is claimed. Credentials remain
encrypted in ignored local storage; no mailbox/password/token is in this report.

Still open: real two-account approval/restore/chat/contact acceptance, the separate
shared-service rollout, group chat, game preparation and online runtime adapters.
Ready remains disabled without an adapter. This is not playable multiplayer.
