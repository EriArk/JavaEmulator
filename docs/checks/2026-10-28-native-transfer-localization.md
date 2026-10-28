# Native Transfer and Recovery Localization Preparation

2026-10-28, main source after `ad6d5fa4`. Public source, unreleased combined
Debug work. No public APK/release update or new supported language. Installed
owner phones, games, saves and accounts were not cleared or replaced.

## Implementation

- Resource-backed Symbian host tasks/actions, archive queue states/roles,
  saved-component menus, import summaries, phone backup/restore and uninstall
  confirmations. Version 1/2/3 backup warnings preserve exactly which contents
  can be restored and that existing phones are not replaced.
- `SystemFailure` gives 55 app-owned backup/removal failures stable identities.
  It remains an `IOException` and preserves English diagnostics and causes.
  Android presentation resolves resources, never guesses identity from English
  error text. Unknown native/OS diagnostics and paths remain verbatim.
- Six typed backup-progress events retain the existing functional callback and
  cancellation behavior. UI formats numeric arguments explicitly. No archive,
  phone identity, profile, journal or backup schema change.
- Queue actions and role selectors stack on narrow/large-font layouts. Buttons
  can wrap instead of being clipped by a fixed height. Import stays disabled
  while work is running, including a selection-summary refresh. State labels
  do not determine enum values or callbacks. Count summaries use label/value
  wording, avoiding malformed singular/plural phrases.
- Extend the protected literal audit to the native host and archive view,
  including the actual label arguments of their helper calls. Resolve lint's
  enum-equality findings in the preceding chat-state diff without changing
  its equality semantics.

## Verification

- Combined Debug and instrumentation APKs built and installed on Retroid Pocket
  Classic / Android 14 (`MC94516AQF040305380`). Only the existing Debug packages
  were updated; the official app was reopened after stopping Debug.
- JVM: **410 passed, zero failures/errors/skips**.
- Final Retroid suite: **61 passed, one skipped, zero failures**. Runner reports
  `OK (62 tests)`, which includes the skipped optional real archive fixture.
  Classes: SystemTransferLocalizationTest (8), SystemRequirementsLocalizationTest
  (2), PhoneBackupTest (15), GameRemovalTest (11), ArchiveBatchTest (20 including
  one skip), SocialLocalizationTest (6).
- All 55 error keys resolve in English/en-XA/ar-XB. Tests compare full English
  backup/uninstall warnings with the existing semantic fallback, preserve
  versions, names, raw diagnostics and causes, and exercise cancellation and
  original queue callbacks. Existing synthetic backup/removal tests retain
  integrity, path traversal, staging, recovery and publication coverage.
- Queue layout: **144 combinations**, comprising English/en-XA/ar-XB, font
  scales 1/1.5/2, 320x640/393x780/540x540/720x400 dp, both themes and pre-import/
  partially completed states. Explicit RTL layout direction is applied to the
  fixture. Tests check containment, full action text and a usable list viewport.
  The scrolling list may show a partial last row; that is not action clipping.
- Offline queue fixtures were rendered on Retroid and inspected: ordinary dark
  square, expanded 200%-font light portrait and dark RTL pseudolocale. These
  are synthetic view renders, not evidence of a real package being installed,
  native game performance, full RTL navigation or a completed translation.
- Fresh lint: **zero errors / 425 warnings**. An initial run found two
  enum-equality diagnostics and one varargs-format diagnostic; corrected before
  the successful final run. This supersedes the earlier stalled lint attempt
  for current source; it does not rewrite that older verification record.
- Manual audit/self-tests pass: **199 Java files, 41 direct UI literal
  candidates, zero in protected screens, 1197 canonical translatable resources,
  zero translation-structure errors**. The inventory is not full translation
  coverage and includes some technical literals.

## Commands and Local Evidence

```powershell
java tools/LocalizationAudit.java --self-test
java tools/LocalizationAudit.java --check
.\gradlew.bat :app:assembleFdroidDebug :app:assembleFdroidDebugAndroidTest :app:testFdroidDebugUnitTest :app:lintFdroidDebug --rerun -PsymbianRelease=true -PsymbianSourceDir=build/symbian-source-check --offline --console=plain --max-workers=2
```

Ignored logs: `build/localization-transfer-final-build.log`,
`build/localization-transfer-retroid.txt`, `build/localization-transfer-lint.log`
(initial failed lint), `build/localization-*.tsv`.
Final fixture captures from the last device pull:
`build/localization-transfer-fixtures/localization-transfer-fixtures/`.
JVM XML: `app/build/symbian-release/test-results/testFdroidDebugUnitTest/`.
Lint XML: `app/build/symbian-release/reports/lint-results-fdroidDebug.xml`.

| Installed local artifact | SHA-256 |
| --- | --- |
| Combined Debug APK | `61a69c13f3669d16199273b05ee8db9a6ddd1fd9d49fa635183e6d6239186091` |
| Instrumentation APK | `5b675ff4b23704cc785444175e6828df3ec7980f697f5cbf57610821a0959306` |

The Debug filename still contains `0.6.0-beta.1-r1`; this does not represent a
new public revision. No APK was distributed to GitHub or Nextcloud here.

## Remaining Scope

Persisted archive problem/detail strings, remaining firmware/component backend
errors/progress, room/publication/contact notices, other helpers and inherited
About/Help still need extraction or review. Unknown external diagnostics should
not be translated by matching their English content. Safe multi-process language
selection and reviewed language batches remain outstanding. No full localization
or new supported language is claimed; #43 stays open.

Native firmware-picker installation, a real-game uninstall/restore, live Social,
physical controller input and public release downloads were not revalidated in
this batch. Backup/removal regressions use isolated synthetic data, not owner
firmware or saves. Source and test evidence are distinct from release acceptance.
