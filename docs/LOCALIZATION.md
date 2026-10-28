# Localization Workstream

Issue [#43](https://github.com/EriArk/JavaEmulator/issues/43), checkpoint 2026-10-28.
English remains the complete source/fallback and the current advertised UI.
Existing inherited locale folders are partial translations, not proof that the
whole application supports those languages. No language picker or new translated
language batch has shipped in this checkpoint.

## Extracted Source

- `values/settings_strings.xml`: Settings sections/actions, theme selection,
  system-file entry, Fluxer account entry, folder scan results/access errors,
  shared Back/Close/Keep/Undo controls and percentage/picture-preset labels.
- `xml/preferences.xml`: all titles are resource references; preference keys,
  stored values and navigation destinations are unchanged.
- Settings and library folder scans share the same resources. Folder counts use
  Android plurals; numbered arguments preserve counts and complete error messages.
- `appearance.xml` already supplies brightness entries. Palette display names
  now use a resource array; the stable `classic`/`retro`/`contrast` IDs do not change.

This is a first extraction slice, not a whole-app translation. Main library
actions, game settings pages, transfers, detailed firmware onboarding and Social
still contain literals. The menu widget's shared labels are extracted, but its
caller-provided page/action names still require extraction. No locale is enabled
or guest language modified as a side effect.

## Terminology for Contributors

| English source | Meaning / boundary |
| --- | --- |
| Library | Games the user can browse; not a system firmware package |
| Game folders | User-authorized folders scanned for game files |
| Library transfer | Backup/restore and migration workflows |
| System files | User-supplied phone system/firmware, not included games |
| N-Gage 2.0 platform installer | Separate dependency; not the phone firmware |
| Controls / Display | Input configuration / game picture configuration |
| Try / Keep / Undo | Temporary preview / accept / restore previous settings |
| Resume / Restart | Continue the running game / launch it again |
| Fluxer / Symbian / N-Gage / RH-29 / RM-320 | Product/platform/device identifiers; preserve them |

Add translations as ordinary Android resource files using existing keys. Keep
full sentences together and numbered format arguments (`%1$s`, `%2$d`, `%%`)
intact. Supply appropriate plural categories; do not concatenate translated
sentence fragments. Do not translate preference IDs, filenames, user content,
game/vendor names or protocol identifiers. New strings fall back to English
until a reviewed translation is present.

Do not bulk-copy inherited About/Help text: review its product identity and legal
attributions first. Validate destructive-action wording, firmware requirements,
long strings, accessibility labels, RTL direction and small-screen focus before
advertising a locale. Translation coverage must be tracked by user journey,
not the number of locale directories or keys alone.

## Next Boundaries

1. Finish English extraction, particularly firmware onboarding and blocking errors.
2. Add one safe, persistent language choice shared by app-owned processes without
   killing active games or changing the emulated OS language.
3. Review language batches and layouts before exposing them in the picker.

`SettingsResourcesTest` checks resource-backed titles, English fallback in
isolated locale contexts, plural counts, argument preservation and palette
array/ID alignment. It does not prove translations or runtime locale switching.
See the dated [verification record](checks/2026-10-28-audio-lifecycle-settings.md).
