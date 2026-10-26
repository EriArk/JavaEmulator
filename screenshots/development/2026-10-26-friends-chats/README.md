# Soft Console Friends and Chats

2026-10-26. Authentic Android captures from the separate Debug installation,
not mockups or public-release screenshots. Contacts/messages are clearly labeled
offline UI fixtures; no real people, private conversations or game files are included.

| File | Device and scope |
| --- | --- |
| `chat-dark.png` | Retroid Pocket Classic, dark conversation, portrait |
| `chat-light.png` | Retroid, light landscape, split list/conversation |
| `friends-light.png` | Retroid, light Friends tab and incoming request count |
| `add-friend-dark.png` | Retroid, tag entry and shared edge panel |
| `delivery-confirmation.png` | Retroid, confirmation before removing a local notice |
| `social-options.png` | Retroid, conversation/history and room/account destinations |
| `phone-keyboard.png` | Android 35 emulator, landscape keyboard and composer |
| `small-chat.png` | Emulator, 480x320dp, 150% text, compact conversation |
| `small-friends.png` | Emulator, 480x320dp, 150% text, combined filter/search |

Development app: `io.github.eriark.abyssme.debug`, `0.5.0-beta.1` / 310.
APK SHA-256: `ea4a2b3619350e0ce715b0d11fe7cb87f58e8e1ad454aaa17676f1a23ac90f42`.
This is a local combined arm64 test build, not an update to the signed public beta.
Install with `adb install -r -d -t`; the injected-ABI build is marked test-only.

The UI reuses the existing native engine artifacts. No credentials, libraries,
games or saved data were erased for these checks. Emulator viewport overrides
are temporary and restored after the compact-screen checks.

[Implementation, verification and limits](../../../docs/RETRO_LIBRARY_REFRESH.md#friends-and-chats-2026-10-26-unreleased).
No new live message delivery, remote-network, gameplay or manual controller
comfort acceptance is implied by these fixture captures.
