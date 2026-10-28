# French, German and Responsive Localized Panels

Date: 2026-10-28. Availability: development source and local combined Debug APK,
not a new signed release. Issue #43 remains open for other languages, remaining
diagnostics and broader journey acceptance. No games, firmware or account data
are included in this record.

## Changes

- Add French and German to Settings > Language and Android's per-app list,
  with native names. Each advertised translated locale now contains all 1517
  canonical resources: strings, plurals and arrays. This is resource coverage,
  not independent native-speaker approval or a claim of all diagnostics being
  translated. English remains canonical; other inherited partial locales remain.
- Review inherited translations and current About/Help, safety warnings,
  firmware guidance and control terminology. Preserve XLIFF/format arguments,
  user data, resource aliases, stored values, legal attribution and links.
- Extract 12 network-game preparation failures into stable notice identities.
  File verification, content hashes and supported adapters are unchanged.
  Unexpected filesystem/converter errors use a localized recovery message;
  the original exception is logged, not matched by its English wording.
- Ignore late game-picker failure callbacks after account changes or Activity
  destruction, matching the successful-result guard.
- Stack a settings choice's label above its control when a word cannot fit the
  available label column. Measure line-break segments in the current locale,
  not hard-coded language names or screen categories alone. Wider layouts can
  restore columns without changing the selected value or invoking callbacks.
- Size lobby initials for the actual font metrics and text width. Android 11's
  linear 200% font scaling exposed clipping in the previous fixed-size badge.
- Keep archive summaries scrollable within a bounded area, leaving the file
  list usable with long translations and large fonts. Preserve all summary text,
  queue actions and import behavior. Compact queue headings use smaller type.
- Extend the resource audit to reject reordered resource aliases in arrays.
  Inline translated labels remain valid. Existing placeholder and locale
  completeness gates remain enabled; no GitHub automation is added.

## Verification

Targets: Retroid Pocket Classic (Android 14, `MC94516AQF040305380`) and isolated
Android 11/API 30 emulator (`emulator-5580`, 720 x 1280, density 320). Only the
separate Debug app is updated. The emulator uses the host renderer; unlike the
previous SwiftShader attempt, its system process did not crash in this run.
UI tests do not establish native engine performance or full-game compatibility.

- Final combined Debug/instrumentation assembly and lint: successful,
  `build/language-europe-verified-build.log` (59 seconds).
- Fresh JVM run: 419 tests, zero failures/errors/skips, 39 result XML files.
- Lint: zero errors, 698 warnings. Warnings include inherited and translation
  typography/resource findings; this is not a clean-warning claim.
- Resource audit and self-tests pass: 1517 canonical resources; no missing
  advertised entries, structural errors or visible XML literals. Nine direct
  Java candidates remain outside protected extraction boundaries.
- Layout matrices cover 240 lobby-seat and 384 archive-queue cases across
  English, Spanish, Brazilian Portuguese, Russian, French, German, expanded
  pseudo-English and pseudo-RTL, font sizes and light/dark palettes. These are
  measured fixture layouts, not hundreds of actual network/import sessions.

- Retroid final run: **55 passes, no skips**. Log:
  `build/language-europe-retroid-final.log`. Both cold-update stages pass
  separately: `build/language-europe-retroid-final-cold-set.log` and
  `build/language-europe-retroid-final-cold-verify.log`.
- Android 11 final build: **55 passes, no skips**. Log:
  `build/language-europe-api30-verified.log`. Both cold-update stages pass:
  `build/language-europe-api30-verified-cold-set.log` and
  `build/language-europe-api30-verified-cold-verify.log`. This replaces the
  unfinished final-build API 30 acceptance from the preceding language batch.
- Cold-update checks force-stop and reinstall the same Debug APK in place,
  verify the saved choice, then restore the original language. The process
  fixture also verifies updated labels without restarting its Activity and
  keeps the device/Java guest language unchanged.
- Final local Debug APK SHA-256:
  `a037b69eeac50ee382f6a788431db54bed49c1358bf40cc5f4f392b6d5161ba1`.
- [Three dark Retroid screenshots](../../screenshots/development/2026-10-28-localization-europe/README.md)
  show the native-name picker, German display settings and French controls.
  Local captures: `build/language-europe-retroid-final-screens/` and
  `build/language-europe-api30-verified-screens/`. No gameplay is implied by
  the `Example game` menu fixture. The isolated emulator was stopped and the
  official Retroid app brought back to the foreground after testing.

## Findings Resolved During Verification

- A German compound word wrapped its final letter onto another line in the
  two-column settings panel. The shared choice layout now adapts to measured
  word width; a regression test also checks restoring columns after widening.
- The first broad API 30 run found two real large-font layout failures (lobby
  badge text and too little archive-list space), repaired above. Three other
  failures were tests assuming English while the saved app language was Spanish.
  Tests now use the active resource text and assert the intended action exists
  before clicking. They do not force the user's language or clear preferences.
- An initial local runner misread a test method containing `Failures` as a
  failed run despite `OK (54 tests)`. Its failure parser now matches anchored
  instrumentation result lines. The raw logs remain available locally.
- Lint rejected the older `Layout` break-strategy constant alias. Use the
  equivalent API-29 `LineBreaker` constant; no lint suppression was added.

## Limits

- No actual game, live multiplayer match or active native import is exercised
  by these locale fixtures. Saved-language cold-update checks do not certify a
  complete library migration. No user libraries, firmware or account vaults
  are cleared.
- Chinese, Japanese, Indonesian, Hindi and the second-wave languages are still
  not advertised as complete. Remaining app-owned/backend diagnostic extraction,
  old raw journals and native-speaker review remain open.
- The GitHub release page was checked and remains `v0.6.0-beta.1`, Revision 1,
  published 2026-10-26. Release assets/checksums were not re-downloaded. No signed
  APK, version code, release asset or production package was changed.
