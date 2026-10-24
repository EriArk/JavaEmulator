# Room Workflow Development Captures

2026-10-24, Retroid Pocket Classic, Android 14. Actual native Views in the separate
`io.github.eriark.abyssme.debug` app. Accounts, DM responses, directory replies
and the second participant are **test fixtures**, not live Fluxer users or rooms.
The paired room actor and Android mailbox/UI are production implementations.

| Capture | State |
| --- | --- |
| [Portrait](room-portrait-fixture.png) | Private room after accepted invitation and admission; reconnect settled |
| [Landscape](room-landscape-fixture.png) | Same room after recreation/rotation; disabled Ready and visible Close |
| [Approval](room-approval-fixture.png) | Pending player request with host Approve/Decline |
| [Light](room-light-fixture.png) | Light theme, fixture directory acknowledgement and reconnecting participant |
| [Link confirmation](room-link-confirmation-fixture.png) | Styled confirmation; opening the locator has sent no join request |

No online game adapter is registered. Ready is deliberately disabled; neither
these images nor the paired actor prove playable multiplayer. The light-theme
image captures reconnecting after theme recreation, not a successful internet
session. Physical button acceptance and real two-account exchange remain open.

## Verification

- Combined Debug app and test APK builds, `lintFdroidDebug`: passed.
- 265 JVM tests, zero failures/errors: passed. Includes the full 16-player wire
  bound, replay/expiry, host admission, exact game/generation readiness, publication
  recovery, delivery failures, queue coalescing, account fencing and strict links.
- 32 Android instrumentation checks on Retroid: passed, no skips/failures.
  The four new room checks run alongside 28 account/chat/friend/directory/storage
  and packet checks. A focused four-room pass also succeeded before the final
  wire/queue changes; the final 32-test pass covers the installed APK below.
- Shared-service extension at TrainerOS `59dce1a`: 19 Python tests and compilation
  passed; PR #260 remains open and undeployed.
- The live anonymous sign-in handoff was initiated, recreated and cancelled by
  the existing device test. No real account was approved or authenticated by it.

Final local logs (ignored): `build/lobby-room-complete-build.log` and
`build/lobby-room-retroid-complete.log`. The earlier failed UI-test pass is retained
locally in `build/lobby-room-retroid.log`; dialogs, stale status and reconnect
notification were repaired before the successful passes.

Debug app SHA-256:
`4e807996aec505285073001e4d335d8cff64f475a90fbd7390d72e312c5906cd`.

Test APK SHA-256:
`003b809b465fd9f430a1973e0818a75aa5091e8d2147000bb7e5de56fddd1cbd`.

Version label remains `0.5.0-beta.1-debug` (development package, not a new release).
No public APK replacement, release-app data reset, credentials or games in Git.
[Implementation and remaining work](../../../docs/LOBBY_ROOMS.md).
