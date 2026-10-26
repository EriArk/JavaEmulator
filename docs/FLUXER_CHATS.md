# Fluxer Chats Checkpoint

The [2026-10-26 Soft Console follow-up](RETRO_LIBRARY_REFRESH.md#friends-and-chats-2026-10-26-unreleased)
updates these screens, small-screen history navigation and large-text split-pane
behavior. The dated implementation and checks below remain historical.

2026-10-24. **Unreleased development source and a local combined Debug APK.**
The public 0.5.0 Beta 1 download is unchanged. Part of
[#34](https://github.com/EriArk/JavaEmulator/issues/34), following the owner's
account -> chats -> friends -> compatible live rooms sequence.

This is the historical chat checkpoint. The subsequent [Friends checkpoint](FLUXER_FRIENDS.md)
adds relationship actions and direct-conversation creation for friends, with
updated checks and screenshots. The build hash/counts below describe this earlier build.

## Available in Development

- Library menu > Chats, or Fluxer account > Open chats after secure sign-in.
- Existing private one-to-one and group conversations, search, text history,
  Earlier / Latest navigation and text sending. No guild/community channels.
- Two panes in landscape at 600 dp or wider; separate list/conversation screens
  on narrower screens and in portrait. Back returns to the list in that layout.
- A multiline composer, visible focus, controller A activation and B keyboard
  dismissal. Keyboard resize keeps the composer visible and preserves the tail
  when the user was already reading the latest messages.
- Separate in-memory drafts per conversation, retained across rotation. Account
  changes clear private state and fence late replies. Returning to Account now
  revalidates a remembered session without unnecessarily rewriting its credential.
- Foreground refresh: messages approximately every eight seconds and conversation/
  block lists every thirty seconds, with explicit refresh and rate-limit backoff.
  This is polling, not push notifications or a gateway connection.

## Delivery and Privacy

An explicit send gets one 32-character nonce. Failed delivery is distinguished
from a confirmed rejection. Unknown deliveries are **never automatically resent**.
The message menu offers Check delivery, an explicit retry with the same nonce
within a conservative four-minute window, or dismissal of the local notice.
Dismissal does not delete anything from Fluxer. A history acknowledgement wins
over a late failed POST; an older GET cannot erase a successful send.

Blocked direct conversations are excluded, blocked group authors are filtered,
and a newly received block applies even if the next channel-list request fails.
401 clears visible private state; revoked conversation access disables sending.
Automatic mention parsing is disabled. Media is represented by placeholders;
attachment URLs and credentials are not copied into the presentation state.

History and drafts are memory-only: leaving the Activity permanently or process
death discards them. No persistent outbox, background send, chat export or disk
cache is introduced. Memory bounds: 500 recent conversations, 24 open windows,
100 remote messages per window, 20 unresolved sends per conversation, 2,000
characters per outgoing message and 8,000 per displayed incoming message.

Transport retains the fixed official HTTPS origin, no redirects, bounded replies
and timeouts. Only private-list, relationships and message GET/POST routes were
added to the existing allowlist. The account vault remains Keystore-encrypted.
No developer account, token, server implementation or new binary dependency is
bundled. TrainerOS was consulted read-only for paging, nonce handling, block
filtering and account isolation. Protocol references checked on this date:
[messages](https://docs.fluxer.app/http-api/messages/) and
[channels](https://docs.fluxer.app/http-api/channels/).

## Verification

Windows / JDK 21, combined Debug build:

```powershell
.\gradlew.bat :app:testFdroidDebugUnitTest --rerun :app:assembleFdroidDebug :app:assembleFdroidDebugAndroidTest :app:lintFdroidDebug -PsymbianRelease=true -PsymbianSourceDir=build/symbian-source-check
```

- **161 JVM tests passed**, zero failures/errors/skips, including 27 chat cases
  and 23 account cases. Includes account/conversation switching, stale replies,
  draft revisions, paging, blocks, expiry, denied access, nonce reuse, uncertain
  retries, 429, malformed results and closure. These are not game tests.
- Combined app/test APK builds and `:app:lintFdroidDebug` passed. Lint retains
  `SetTextI18n` warnings for the existing English-only programmatic UI convention;
  this is not a zero-warning claim.
- **16/16 Android checks passed** on Retroid Pocket Classic / Android 14: six
  chat checks, six account checks and four existing multiplayer foundation checks.
- Chat checks use the real Activity/keyboard/RecyclerViews with a test-only,
  in-memory provider injected through the test lifecycle. They cover portrait/
  landscape/light layouts, keyboard visibility and retained history tail,
  Unicode drafts across recreation, synthetic controller-A sending, account
  isolation and explicit Retry through the actual message popup menu.
- The separate live anonymous handoff check initiated, polled, recreated and
  cancelled a pending browser approval. It did not approve a real account.
- The first UI pass exposed repeated layout invalidation and fullscreen search
  extraction. Both were corrected; the final full device suite passed. No test
  assertion was removed to conceal the failure.
- [Six screenshots with provenance](../screenshots/development/2026-10-24-fluxer-chats/README.md)
  show the actual device UI with clearly named fixture conversations, not a live
  chat account. The test provider is absent from the application APK.

Device command:

```text
adb shell am instrument -w -r -e fluxerLive true -e class ru.playsoftware.j2meloader.multiplayer.FluxerChatsDeviceTest,ru.playsoftware.j2meloader.multiplayer.FluxerAccountDeviceTest,ru.playsoftware.j2meloader.multiplayer.MultiplayerCoreDeviceTest io.github.eriark.abyssme.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Local evidence (ignored): `build/fluxer-chats-build-final.log`,
`build/fluxer-chats-retroid-final.log`, `build/fluxer-chats-final-captures/`, and
JUnit XML under `app/build/symbian-release/test-results/testFdroidDebugUnitTest/`.
Installed only to the separate `.debug` package; the official app/data were kept.
Local APK label remains 0.5.0-beta.1 / code 310, SHA-256:
`2053ddcefb5660f5d704bcb525544e15a858bc22e26b00fedadfb3decb914d09`.
This is not the public release binary. No APK upload, bot/server or Nextcloud change.

## Still Open

Actual browser approval, authenticated restore/logout and a real two-account
message exchange have **not** been accepted end to end. Physical gamepad input,
other phone keyboards, offline process-death drafts and background notifications
are not claimed tested or supported by these checks. No emulator run in this
checkpoint; Retroid was used instead.

Creating conversations, friends/actions, live compatible rooms/invitations,
attachments, message edits/deletes and real-game online play are not implemented
by this increment. Incoming lobby envelopes appear as Game session message;
they are not treated as authenticated actionable invitations yet. The agreed
[next stages](MULTIPLAYER.md#next-implementation-order) remain open.
