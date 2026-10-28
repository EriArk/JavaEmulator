# Localization Journey Preparation

2026-10-28, main source after `c7db7137`. Unreleased combined Debug work.
No release asset, official Retroid application, guest language, sound bank,
firmware or account data was replaced. New language support is not advertised.

## Scope

Four English resource files cover library actions/artwork, shared game settings
and confirmations, transfer/installer UI, controller/touch mapping and preview
controls. Java, Mophun and Symbian game hosts use the shared labels. The earlier
Settings extraction remains in place. See [scope and glossary](../LOCALIZATION.md).

Resolution choices retain numeric dimensions instead of parsing display text.
Phone/mapping IDs remain unchanged. Transfer idle state is explicit; primary
button styling and picture swatches no longer compare English labels. A stale
Russian work-directory message required a nonexistent second format argument;
it now uses the same single path argument as the source and has a regression test.

Long menu headings wrap. Choice labels and spinners stack for compact screens or
font scales >= 1.3. Action icons use relative placement. These changes are not a
claim that every app screen has passed RTL or every translated layout fits.

## Verification

- Combined Debug application and instrumentation APK build successfully using
  JDK 21, offline Gradle and the local prepared Symbian source tree.
- JVM: **408 tests, zero failures/errors/skips**.
- Retroid Pocket Classic, Android 14, `MC94516AQF040305380`: **53 tests pass**.
  Classes: LocalizationJourneyTest, SettingsResourcesTest, CompactSettingsTest,
  GameMenuLayoutTest, ControllerMapperTest, TouchMappingTest,
  SettingsPreviewBarTest, GameChromeTest and GalleryPresentationTest.
- Eight localization journey tests cover isolated pseudolocale contexts,
  preserved user arguments, choice-array indices, semantic import state,
  mapping profile names/key codes, plural/format behavior, numeric resolutions,
  confirmations and a rendered 200% font dialog.
- The first expanded suite had 52 passes and one gallery fixture failure: it
  inherited the active N-Gage tab while inserting Java fixtures. The fixture now
  selects Java/gallery explicitly, uses resource-backed action labels and restores
  library navigation preferences. The full rerun passes; no product workaround.
- Lint: **zero errors / 408 warnings**. No claim of warning-free code.
- `LocalizationAudit --self-test` and `--check` pass. AST inventory: 194 files,
  219 direct UI literal candidates, zero in protected extraction boundaries.
  Resource inventory: 662 translatable source resources, zero structural
  translation errors. Presence counts are not translation quality/completeness.
- A captured expanded-pseudolocale Display dialog at 200% font was inspected:
  heading, stacked choices and Try footer remain readable without overlap.
  This is an app-owned UI check, not gameplay or physical-button acceptance.

## Reproduction and Local Evidence

```powershell
java tools/LocalizationAudit.java --self-test
java tools/LocalizationAudit.java --check
.\gradlew.bat :app:assembleFdroidDebug :app:assembleFdroidDebugAndroidTest :app:testFdroidDebugUnitTest -PsymbianRelease=true -PsymbianSourceDir=build/symbian-source-check --offline --console=plain --max-workers=2
.\gradlew.bat :app:lintFdroidDebug -PsymbianRelease=true -PsymbianSourceDir=build/symbian-source-check --offline --console=plain --max-workers=2
```

Ignored local evidence: `build/localization-game-ui-build.log`,
`build/localization-test-build.log`, `build/localization-device-tests-final.log`,
`build/localization-lint.log`, `build/localization-inventory.tsv`,
`build/localization-coverage.tsv`, `build/localization-invalid.tsv`, and
`build/expanded-display-200.png`. Test reports are under
`app/build/symbian-release/test-results/testFdroidDebugUnitTest/`.

SHA-256:

| Local artifact | SHA-256 |
| --- | --- |
| Debug APK, existing `0.6.0-beta.1-r1-debug` filename | `f8f67f37f8d934b7e4b7108413c9dd3aa58d803d694fa24187b46034d19cb5cc` |
| Final instrumentation APK | `cce835e79032bdede7de8f245492bb9f7a57671d1090cf1d5427c43353088ca6` |
| Expanded Display capture | `02bff4f51bd30f14286415688edb27a7c7bbff967ee17a740b243d8df3f5a08a` |

## Still Open

Detailed firmware setup, Fluxer account/chat/lobby screens, helper-generated text,
backend errors and inherited About/Help need extraction/review. The AST audit is
deliberately limited to direct Java UI calls, not proof that all text is covered.
No app-language picker, multiprocess locale propagation, reviewed new language
batch or full RTL/phone acceptance is included. Existing locale folders remain
partial. Issue #43 stays open. No new public release was created or checked here.
