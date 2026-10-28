# Japanese and Simplified Chinese

Date: 2026-10-28. Availability: unreleased development source and local combined
Debug builds, not a new signed APK. The maintainer limited this batch to Japanese
and Simplified Chinese and deferred further language expansion. Issue #43 stays
open for remaining diagnostics and broader acceptance; the deferred languages
are not silently added to the picker.

## Changes

- Add `ja` and `zh-CN` to Settings > Language and Android's per-app language
  list, with native names `日本語` and `简体中文`. English remains canonical.
- Complete 1517 canonical entries in 22 XML files per new locale, reviewing
  the inherited settings resources as well as current library, game menu,
  controls, system-file guidance, transfer, account and room messages.
- Preserve formatting arguments, plurals, XLIFF technical identifiers, resource
  alias order, support links and legal attribution. Review restart/resume,
  preview/keep/undo, saved-installer removal and firmware/platform distinctions.
  Translate the original N-Gage generation label without changing its value.
- Extend tests for CJK choice wrapping at three widths and three font scales,
  scrollable native-name picker entries, real cross-process label updates,
  locale-independent action callbacks, native import notices and system help.
- Parameterize the opt-in cold-update test so each new language is persisted
  through force-stop and an in-place Debug update, then the original choice is
  restored. Tests retain the guest/device language and do not clear libraries,
  profiles, saved games, firmware or account data.
- Fix shared menu window updates after the host Activity is recreated. Keep
  pending layout attributes before attachment, but do not dispatch a resize
  to a detached window. Bind menus to the owning LifecycleOwner so they close
  before Activity teardown removes their windows. Reapply placement on attachment;
  regression coverage checks that repeated dismissal finishes preview cleanup
  only once.

## Verification

Final combined Debug / instrumentation assembly and lint succeed:
`build/language-cjk-lifecycle-build.log` (1 minute 45 seconds), followed by
`build/language-cjk-layout-harness-build.log` (39 seconds, test-harness adjustment
only) and `build/language-cjk-final-tests-build.log` (37 seconds, localized Back
lookup in a test). The application APK is unchanged by the latter two builds.
No public release task was run.

- Fresh JVM run: **419 tests**, zero failures/errors/skips, 39 XML result files.
- Lint: **zero errors / 802 warnings**. Remaining warnings include inherited
  findings and translation typography suggestions; this is not warning-free.
  Remove unused Chinese `one` quantities, retaining the language's `other` form.
- Resource audit and self-tests pass: all 1517 entries in each advertised
  translated locale; zero structural errors, missing entries or visible XML
  literals. Nine Java candidates remain outside protected extraction boundaries.
- Retroid Pocket Classic, Android 14: **75 passes**, no skips;
  `build/language-cjk-retroid-release-check.log` (94.528 seconds). Japanese and
  Chinese each pass both cold-update stages in
  `build/language-cjk-retroid-release-check-{ja,zh-CN}-cold-{set,verify}.log`.
  The log label means a development readiness check, not a public release task.
  The final resource-based Back lookup also passes separately on Retroid:
  `build/language-cjk-retroid-back-final.log` (one test).
- Android 11 / API 30 emulator, 720 x 1280 at density 320: **75 passes**, no
  skips; `build/language-cjk-api30-verified.log` (117.023 seconds). Japanese
  and Chinese each pass both force-stop/in-place-update stages in
  `build/language-cjk-api30-verified-{ja,zh-CN}-cold-{set,verify}.log`.
- Measured fixture matrices: 300 lobby-seat, 480 archive-queue, 60 About/Help
  and 18 CJK-choice layouts. These cover narrow, square and wide bounds,
  large fonts and, for lobby/queue, both palettes; they are not live sessions.
- Final Debug APK SHA-256:
  `a6e136a15460123adc522d93d1dcb4ae026704cf561c3ed494e305d26801841d`.
- [Dark development captures](../../screenshots/development/2026-10-28-localization-cjk/README.md)
  show native names, Japanese controls/import and Chinese display settings.
  Retroid menu captures are 1240 x 1080; the isolated queue view is 1080 square.

## Verification Findings

- The first Retroid run had 61 passes and one test-harness failure. The new
  scroll test requested focus before the dialog window received focus; pending
  initial focus then returned to the first row. Wait for layout/window focus
  before exercising user input. All 62 tests pass on retry; no production focus
  behavior was altered to mask the result.
- The first Android 11 attempt encountered a system process-start timeout before
  the test process attached. Recovery subsequently started instrumentation, but
  that run was interrupted and is not counted as a passing run. Evidence:
  `build/language-cjk-api30-start-timeout.log`, `build/language-cjk-api30-final.log`.
- A later Android 11 run exposed an ANR in the new test harness: it created all
  18 CJK choice-layout fixtures inside one main-thread callback. Split the
  fixtures into individual callbacks with idle barriers; do not change the
  application's layout behavior to hide the harness stall.
- Repeating the Retroid suite found a real window-lifecycle failure: dismissal
  after Activity recreation could resize, then remove, an already detached
  window. The first resize guard alone was insufficient; an explicit regression
  reproduced the removal failure. Close menus on owner destruction and unregister
  the lifecycle observer on normal dismissal. Repeat dismissal must not rerun
  preview cleanup. Older label-assertion fixtures now use isolated English
  contexts instead of assuming the user's app language is English. Rotation
  fixtures retain the real Activity context: overriding its locale in the
  harness could leave its orientation stale. The intermediate 74/75 result
  is recorded in `build/language-cjk-retroid-complete.log`; all 75 pass after
  correcting the fixture context.
- The first final-APK Android 11 run completed 71/75 checks. Three focus checks
  were blocked by the emulator's foreground **System UI isn't responding**
  dialog, confirmed through WindowManager and UI hierarchy output. Dismiss that
  system dialog before repeating input checks. The fourth failure was a legacy
  test looking for literal `Back` with Spanish selected; use its resource label.
  Evidence: `build/language-cjk-api30-complete.log`. All 75 checks and all four
  cold-update stages pass on the final retry after those corrections.

## Limits

- Resource presence and structural checks are not independent native-speaker
  approval. Remaining low-level/backend diagnostics and old raw journal text
  can still be English. Game assets and user messages are intentionally untouched.
- UI fixtures are not live games, active phone installations or multiplayer
  matches. They do not establish performance or complete-game compatibility.
- Indonesian, Hindi and the second-wave languages are deferred at the
  maintainer's request. Existing partial resource folders are retained, but are
  not advertised as complete languages.
- The public page remains `v0.6.0-beta.1`, Revision 1, published 2026-10-26,
  checked through GitHub CLI on 2026-10-28. Its assets were not downloaded or
  rehashed here. No signed APK, release tag, version code or production package
  is changed by this batch. GitHub Actions remains disabled.
