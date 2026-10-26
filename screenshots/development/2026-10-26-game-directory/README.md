# Game-Aware Directory - Development Captures

2026-10-26. Authentic Android captures, not concepts or public-release screenshots.
No image edits or bundled game files. UI fixture rows are explicitly labeled;
they are not evidence of real people or active community rooms.

| File | Device and scope |
| --- | --- |
| `lobbies-light-fixture.png` | Retroid, light landscape catalog, free-seat filter |
| `lobbies-portrait-fixture.png` | Retroid, dark portrait catalog, fixture rows |
| `lobbies-full-detail-fixture.png` | Retroid, full-room contextual panel, no join action |
| `lobbies-social-options-fixture.png` | Retroid, compact Social options with molded actions |
| `lobbies-offline-fixture.png` | Retroid, simulated provider failure and Retry |
| `small-social-options.png` | Android 35 emulator, 480x320dp, 150% text, light theme |
| `live-selected-game.png` | Android 35 emulator, real test-account BT Biplanes advertisement, final development app |

Final development app SHA-256:
`f8307d08975af94e1cfbbdd24e6293ad75dcdde5c4e975faa0a3e1c5e005374f`.
Separate `io.github.eriark.abyssme.debug` installation, development version
`0.5.0-beta.1` / 310, not a refreshed public release.

Fixture and small-screen captures precede only the final publisher reply/removal
guard; their UI code is unchanged. The live capture is from the final APK above.

391 JVM checks pass; final Android UI/lifecycle suites pass 14 per target,
plus two short-screen / large-text cases. The live directory pair verified
selected-game discovery, admission, seats, privacy and removal, not gameplay.
No new Internet/NAT, audio, manual controller or full-game acceptance.
[Checks, earlier attempts and limits](../../../docs/RETRO_LIBRARY_REFRESH.md#game-aware-directory-2026-10-26-unreleased).
