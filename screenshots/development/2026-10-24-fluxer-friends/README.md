# Fluxer Friends - Development Screenshots

2026-10-24, Retroid Pocket Classic / Android 14, native portrait 1080x1240
and landscape 1240x1080. Actual application UI captured by instrumentation;
all contacts and mutations use the test APK's in-memory provider. Names explicitly
say UI fixture. These are not real contacts, live-network proof or release screenshots.

| Capture | State |
| --- | --- |
| [Friends](friends-portrait-fixture.png) | Portrait, dark theme, friend list |
| [Requests](friends-requests-landscape-fixture.png) | Landscape, incoming and outgoing requests |
| [Light theme](friends-requests-light-fixture.png) | Same requests, light palette |
| [Add friend](friends-add-fixture.png) | Full-tag entry dialog, before simulated submission |
| [Block confirmation](friends-block-confirmation-fixture.png) | Explicit confirmation naming the contact |
| [Unknown result](friends-unknown-result-fixture.png) | Lost mutation reply; refreshed list without automatic write replay |

Amber underline marks the selected tab. Cyan outlines indicate controller focus,
which may be on a different tab. No image editing, account tokens or private chats.

Source: this commit's app and `FluxerChatsDeviceTest`. Debug APK SHA-256:
`e298e30028b09786972070416ef92e112850d772c546b4b440547a130b3857e8`.
The local APK label remains 0.5.0-beta.1, but these unreleased features are not
in that public release. [Checks and limitations](../../../docs/FLUXER_FRIENDS.md).
