# Phone-Specific Symbian Screen Choices

Date: 2026-11-01. Follow-up to the [native orientation trials](2026-11-01-symbian-screen-orientation.md), #50.

## Implementation

- The native window server snapshots the primary phone screen's supported
  orientations before applying a game's trial. Discovery and application use
  the same native validation rule. A trial cannot rewrite the available choices.
- Library and running-game display settings use this snapshot, not an OS-name
  guess. Missing orientations are omitted without changing stored preset IDs.
  EKA1 remains Original-only; unknown modern capabilities offer Original until
  the phone is prepared. The pending explanation covers all eight UI languages.
- Discovery runs on the serialized engine worker during phone preparation,
  library refresh and game launch. Opening library settings makes no native
  calls and does not boot a virtual phone.
- A separate atomic cache is bound to the phone identity and hashes of its
  metadata, registration, host configuration and WSINI. Changed, missing,
  ambiguous, oversized or corrupt configuration/cache data falls back to
  unknown, not a guessed orientation. Same-size/same-timestamp changes are
  detected. The cache policy has an explicit version for future changes.
- Screen capabilities are disposable host data, outside the phone runtime,
  game profiles and saves. Failure to save the cache does not block game launch.
- Native game menus now resolve their title from the matching phone/UID library
  entry and portable custom-title file on the worker. This also covers N-Gage
  2.0 entries missing from the ordinary native application list. Title-read
  failures retain the existing fallback rather than blocking launch.

## Verification and Availability

Retroid Pocket Classic, Android 14, separate `io.github.eriark.abyssme.debug`.
The release package was not replaced or cleared; no public APK was uploaded.
Both combined Debug APK and instrumentation APK build and install successfully.
The final incremental build reports `BUILD SUCCESSFUL in 17s`.

- Native tests pass on Retroid: shared orientation validation covers both modes,
  portrait-only, landscape-only, invalid/empty and secondary-screen cases,
  together with the existing capture/input/timing/audio checks.
- `PhoneScreenOptionsTest`, `SymbianGameSettingsTest`, `GamePresetsUiTest`:
  **22 passed**, no skips. Coverage includes missing/corrupt/version-mismatched
  cache, phone identity isolation, configuration replacements with unchanged
  size/timestamp, registration changes, legacy exclusion, stable preset IDs in
  filtered lists and draft-versus-confirmed settings.
- Real `SymbianActivity` launch > Game menu > Display > advanced > exit passes
  for both N95/Asphalt3 and N-Gage/Snakes. The UI reads discovered capabilities
  from the separate process; those remain readable on return to the library.
  Final builds additionally assert the real library title in each game menu.
- Disposable native phone copies pass Original > Portrait > Landscape > Original
  on N95: available mask stays 7 while panel/active mode changes between 320 x
  240 and 240 x 320 (row pitch 1280 / 960 bytes). EKA1 mask stays 1, both overrides
  reject before launch, and Original remains 176 x 208. Original phone files
  have identical hashes before and after each copy-based test.
- Final dark-theme menu screenshots were visually inspected: correct title,
  readable labels, and no native-screen selector on EKA1. Other languages compile
  but were not each visually inspected in this follow-up.

Ignored local evidence: `build/screen-options-final-build.log`,
`build/screen-options-native-tests.log`, `build/screen-options-settings-tests.log`,
`build/screen-options-n95-ui.log`, `build/screen-options-ngage-ui.log`,
`build/screen-options-n95-native.log`, `build/screen-options-ngage-native.log`,
`build/screen-options-n95.png`, `build/screen-options-ngage.png`.

The copy-based N95 captures remain startup-screen evidence, not race/FOV
acceptance. The ordinary UI runs verify settings and exit paths, not complete
gameplay or controller acceptance. No game/phone payloads or cache are published.

## Limits

This filters the already bounded native-orientation feature. It does not add
arbitrary sizes, make a fixed-layout game adaptive or establish widescreen FOV.
Native preparation still validates every requested override independently of
the cache. #50 remains open for the broader work described in the earlier report.
