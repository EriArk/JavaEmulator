# Temporary Contacts - Native Development Captures

Captured 2026-10-24 on Retroid Pocket Classic, Android 14, with the separate
`io.github.eriark.abyssme.debug` package. Unreleased combined Debug build, still
labelled 0.5.0-beta.1 / code 310. This is **not** the public release binary.

| Capture | State |
| --- | --- |
| [Keep as friend](room-temporary-contact-fixture.png) | Admitted contact established; room scrolled to Keep |
| [Cleanup](room-contact-cleanup-fixture.png) | Room closed; bilateral temporary-contact cleanup pending |

These are native application Views driven by the real room/contact/mailbox
actors with an in-memory HTTP provider and named partner fixture. They are not
real Fluxer users, a live room, physical-controller acceptance or online gameplay.
The suite verifies Keep retention and eventual cleanup, not just these frames.

APK SHA-256:
`4e78bb914080ea32cc9c17279ec5269b6c2e344ef699276037f8f5c9e6c068ef`

Instrumentation APK SHA-256:
`8797c22a1b3e630c2de8b7e58b5001c38a0a3cf753e4eb23e471f25ad380c955`

Checks and remaining limits: [LOBBY_CONTACTS](../../../docs/LOBBY_CONTACTS.md).
No APK, credentials, game or firmware is included in this directory.
