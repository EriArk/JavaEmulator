# Fluxer Chats - Development UI Captures

Captured on Retroid Pocket Classic / Android 14 on 2026-10-24 from the separate
AbyssME Debug app. **Unreleased**, not screenshots of public 0.5.0 Beta 1 features.

These are real Android UI screenshots with **in-memory test conversations**,
named `UI fixture`. No real person received a message and no account login or
live chat exchange is implied. The fixture provider exists only in androidTest.
No compositor, generated screen or image editing was used.

| Capture | View |
| --- | --- |
| [Conversation list](chats-list-portrait-fixture.png) | Portrait, dark theme |
| [Conversation](chats-conversation-portrait-fixture.png) | Portrait, focus ring and composer |
| [Two panes](chats-landscape-fixture.png) | Landscape list and conversation |
| [Keyboard](chats-keyboard-fixture.png) | Composer and latest message remain visible |
| [Light theme](chats-light-fixture.png) | Same landscape layout in light theme |
| [Uncertain delivery](chats-delivery-unknown-fixture.png) | Simulated lost send response; explicit retry only |

The test suite also exercised the actual Retry popup, rotation, synthetic
controller-A send and account switching. It does not establish physical-button
or real-network acceptance. See [implementation, checks and APK fingerprint](../../../docs/FLUXER_CHATS.md).
