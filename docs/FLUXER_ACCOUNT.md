# Fluxer Account Checkpoint

2026-10-24. **Unreleased source and a local combined Debug APK**, not an update to
the public 0.5.0 Beta 1 download. Part of [issue #34](https://github.com/EriArk/JavaEmulator/issues/34).

## Available in Development

- Library menu / Settings > Fluxer account.
- Continue with Fluxer creates a short-lived approval code. Copy code & open
  Fluxer opens the official website in the external browser. Password/MFA stays
  there; AbyssME has no password input, embedded WebView or exported auth callback.
- The screen survives rotation and waits while the browser is open. Pending
  approval expires after the service deadline (at most five minutes). Cancel,
  denied/expired responses, connection loss and rate limits have explicit outcomes.
- A completed handoff is verified against `/v1/users/@me` before saving or showing
  the account as connected. Saved sessions are reverified on reopening the account
  screen. Offline verification does not delete a potentially valid saved session.
- Sign out requests server revocation. A failed request does not pretend to have
  signed out. The separate, confirmed Remove from device action clears local
  credentials without claiming to have revoked the server session.
- Failed secure saving is visible with a retry action. An unsaved session is
  revoked best-effort when its screen is discarded. Interrupted/cancelled handoff
  responses are fenced; late completed tokens are revoked best-effort.
- Single-player startup never instantiates this account model or requires a login.

## Storage and Transport

`FluxerCredentialStore` uses AES-256-GCM with randomized IVs, versioned authenticated
data, an Android Keystore key, and atomic ciphertext writes under `noBackupFilesDir`.
There is no plaintext fallback. Corrupt data or a missing key requires explicit
removal. Local removal deletes both the encrypted file and its key. This does not
claim hardware-backed storage on every device or protection from a compromised OS.

Requests run off the main thread and are restricted to the official HTTPS origin
and the required auth/account routes. Redirects are disabled, replies/timeouts are
bounded, and errors do not expose provider bodies, tokens or poll secrets. UI state
contains neither token nor poll secret. Only the short-lived approval code is copied
by an explicit user action; the clipboard is marked sensitive and the pending-code
screen is protected from screenshots/app-switcher captures. Secrets are not put in
Intents, saved-instance Bundles, settings, logs, the game library or exports.

The wire flow was checked against TrainerOS `FluxerSession.cpp` and the official
Fluxer API contract at `d3d3a5faaf77bb778b9982c2517f483e8d403323`:
[handoff service](https://github.com/fluxerapp/fluxer/blob/d3d3a5faaf77bb778b9982c2517f483e8d403323/fluxer_api/src/api/auth/services/DesktopHandoffService.ts),
[auth controller](https://github.com/fluxerapp/fluxer/blob/d3d3a5faaf77bb778b9982c2517f483e8d403323/fluxer_api/src/api/auth/AuthController.ts).
This is a native client implementation of that wire protocol; Fluxer server code
is not bundled, and no new binary dependency or license change was introduced.
The production account origin is fixed rather than a user-supplied server URL.

## Verification

Windows / JDK 21, combined build:

```powershell
.\gradlew.bat :app:testFdroidDebugUnitTest --rerun :app:assembleFdroidDebug :app:assembleFdroidDebugAndroidTest -PsymbianRelease=true -PsymbianSourceDir=build/symbian-source-check
```

- **133 JVM tests passed**, including 22 new account lifecycle cases: restoration,
  verification before persistence, expired/cancelled/late responses, fixed discovery,
  malformed data, 429, save/read/delete failures, offline/logout handling and closure.
- `:app:lintFdroidDebug` completed successfully. The new screen follows existing
  English-only programmatic UI conventions; lint still reports `SetTextI18n`
  warnings for those strings. This is not a zero-warning report.
- **10/10 Android tests passed** on Retroid Pocket Classic / Android 14: six account
  checks plus the four existing contact-journal/packet checks. Account checks cover
  real Keystore encryption/reopening/tamper/lost-key/removal, transport allowlisting,
  portrait/landscape recreation, visible focus and light-theme text contrast.
- An opt-in test called the real public Fluxer discovery/initiate/status endpoints,
  received a pending approval code, retained it across recreation and cancelled from
  the actual screen. No account was approved or signed in. Cancellation dispatch
  was tested; independent server-side revocation confirmation was not asserted.
- [Screenshots](../screenshots/development/2026-10-24-fluxer/README.md) are actual
  Retroid captures of signed-out light/dark layouts, not logged-in mock accounts.
  A USB modal obscured the first capture pass; it was dismissed, focus assertions
  were added and the full ten-check device suite passed again with clean captures.

```text
adb shell am instrument -w -r -e fluxerLive true -e class ru.playsoftware.j2meloader.multiplayer.FluxerAccountDeviceTest,ru.playsoftware.j2meloader.multiplayer.MultiplayerCoreDeviceTest io.github.eriark.abyssme.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Local evidence (ignored): `build/fluxer-account-build-final.log`,
`build/fluxer-account-build-manifest.log`, `build/fluxer-account-retroid-final.txt`,
`build/fluxer-account-lint.log`, `build/fluxer-account-final-captures/`, and
JUnit XML under `app/build/symbian-release/test-results/testFdroidDebugUnitTest/`.
APK installed only to the separate `.debug` package; official app/data retained.
Local APK label remains 0.5.0-beta.1 / code 310, but its SHA-256 is
`d5e7f63b26c4fa5abda56600183018e8e6936266da71fc650f64b80e1d1290a3`.
It is **not** the public release APK.

## Not Yet Accepted

Owner approval in the external browser, authenticated restore/logout on a real
Fluxer identity, physical gamepad presses and process-death recovery of a live
account have not been checked end to end. Authenticated responses use deterministic
fixtures in the JVM suite; isolated device vault tests use non-account fixture strings.
Browser launch/missing-browser handling is implemented but was not exercised in this
device pass. Unfinished approval is intentionally not persisted across process death;
the user starts again and the old code expires on Fluxer.

No chats, friends UI, public room list, invitations, gateway, P2P or real-game online
play is claimed by this increment. Account data is not yet connected to the contact
actor. [Next stages and compatibility filtering](MULTIPLAYER.md#next-implementation-order)
remain in the agreed order. No public APK, bot, server or Nextcloud deployment changed.
