# Lobby Directory Development Captures

2026-10-24, Retroid Pocket Classic, Android 14. Actual native Android UI from
the separate Debug package, not the signed public APK. Screens use **fixture
accounts, advertisements and membership responses**, not real lobbies or players.
The fixture enables a test-only J2ME capability; production online game capabilities
remain empty until an actual adapter is bound. No playable multiplayer claim.

| Capture | State |
| --- | --- |
| [Portrait](lobbies-portrait-fixture.png) | Choosing, full and approval fixtures; dark theme |
| [Landscape](lobbies-landscape-fixture.png) | Free seats filter hides the full room |
| [Light](lobbies-light-fixture.png) | Same filtered directory in light theme |
| [Unavailable](lobbies-offline-fixture.png) | Failed refresh clears previous advertisements |
| [Community retry](lobbies-community-fixture.png) | Automatic join failed; explicit retry offered |

The combined Debug app/test APKs and lint passed, as did 222 JVM tests and
28 Retroid instrumentation checks (7 directory + 21 existing account/chat/friend/
storage/packet checks). The directory suite also passed a focused seven-test rerun
after allowing screenshot animations to settle. Covered automatic fixture join,
explicit recovery, per-account disk attempt persistence, strict invite allowlist,
search, seat filter, rotation, light/dark themes, keyboard resizing and injected
gamepad-B dismissal. This is not physical-controller or real authenticated service
acceptance. The existing live anonymous handoff check ran; no user approved a login.

Tests: `LobbyDirectoryDeviceTest`, `FluxerChatsDeviceTest`,
`FluxerAccountDeviceTest`, `MultiplayerCoreDeviceTest`.
Evidence: ignored `build/lobby-directory-retroid.log` and
`build/lobby-directory-retroid-captures.log`.

Debug APK SHA-256:
`5f1dac75be7e6be36ff9b25d02ce41ce06a6b14d58d8a4dad770094ff928e3f2`.
No release app removal/data reset, no published APK update, no live bot deployment.
[Implementation and remaining boundaries](../../../docs/LOBBY_DIRECTORY.md).
