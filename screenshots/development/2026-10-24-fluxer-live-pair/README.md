# Live Fluxer Pair Captures

2026-10-24. Unreleased native Android UI, real Fluxer service and two authorized
test accounts. Unlike earlier social UI captures, these are not provider fixtures.
Only test usernames and deliberately written test messages are shown. No login
codes, passwords, tokens, private conversations or game files are included.

| Image | Device and state |
| --- | --- |
| [Chat](retroid-chat-live.png) | Retroid Pocket Classic, Android 14, two-way web/app text exchange |
| [Friends](retroid-friends-live.png) | Retroid, fresh app launch after both room scenarios; existing friendship retained |
| [Host](retroid-host-live.png) | Retroid, accepted private invitation, two members |
| [Guest](retroid-guest-live.png) | Retroid as guest in reverse test, both connected after recreation and heartbeat interval |
| [Phone host](emulator-host-live.png) | Android 35 emulator, portrait/light theme, host with Retroid guest |

Unmodified native captures. The two Retroid room images are different sessions
with opposite host/guest roles, not simultaneous views of one room. Portrait is
emulator evidence, not a physical phone test. Ready is disabled because no online
game adapter is registered. These images do not establish playable multiplayer.

Chat was captured before the final clock-skew patch; room captures use the final
Debug APK with SHA-256 `1ee917688a787526550edb5cab28df89282b4b79b90e8776462ebe39d330d0e8`.
Both use the existing development label `0.5.0-beta.1-debug`, not the public APK.
[Checks, reproduction and limits](../../../docs/FLUXER_LIVE_PAIR.md).
