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

- `values/game_menu_strings.xml`: shared Java/Mophun/Symbian game menus,
  display controls, preview decisions, restart/exit warnings, and Social entry
  actions. Full messages and choice arrays replace English fragments.
- `values/library_ui_strings.xml`: library views, actions, artwork, collections,
  title editing, empty states and accessibility labels.
- `values/transfer_ui_strings.xml`: transfer menus, instructions, result summaries,
  progress states, counts and installer actions. Backend error extraction remains
  open; those errors still fall back to their original text.
- `values/control_ui_strings.xml`: physical mapper, touch layout editor,
  preview countdowns and game chrome accessibility labels.
- `values/social_ui_strings.xml`, `social_auth_strings.xml` and
  `social_state_strings.xml`: account approval/recovery, chats, friends, lobby
  directory, room actions and seat accessibility. Account/chat/directory actors
  expose stable notice enums; the UI renders resources without translating
  protocol payloads, account identifiers or user messages.
- `values/system_files_strings.xml`: system-files entry, generation-specific
  requirements, phone-dump help and pending-import decisions. Rendering uses
  import kind/readiness, not English title comparisons. Technical identifiers
  in help are protected with XLIFF placeholders.

Display resolution is selected by numeric dimensions, not by parsing translated
labels. Phone profile IDs and stored mapping names remain stable; built-in
mapping names have separate display aliases. Transfer idle state is semantic,
not an English string comparison. Play styling and image-preset swatches are
explicit properties, not label comparisons.

This is still source preparation, not whole-app localization. System archive,
backup/component subflows, room/publication/contact backend notices,
game-information helpers, backend errors and inherited About/Help still need
extraction or review. No new locale is enabled or guest language modified as a
side effect. English actor fallback fields are retained for diagnostics and
non-Android tests; UI decisions must use typed keys, not translated labels.

Messages and channel names carry explicit content/fallback kinds: a user who
writes `[Attachment]` or names a chat `Group conversation` keeps that exact text.
Only app-generated placeholders are localized. Chat timestamps use the UI
locale's short date/time format; transport timestamps remain unchanged.

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

## Manual Regression Checks

Run with JDK 21 from the repository root:

```powershell
java tools/LocalizationAudit.java --self-test
java tools/LocalizationAudit.java --check
```

The AST inventory reports direct UI literal candidates in app-owned Java sources;
it is not an exhaustive scan of helper-returned text, native code or XML. The
check rejects new direct literals in extracted screens and structural translation
errors (format argument types and array lengths). Reports go to ignored `build/`.
Coverage counts resource presence, not translation quality or approval.

Debug builds include Android pseudolocales. `LocalizationJourneyTest` uses isolated
expanded/RTL contexts, preserves dynamic user text, checks array positions,
numeric resolution selection, mapper IDs, confirmation callbacks and a 200% font
dialog. It does not switch the device locale or prove full RTL support. Shared
menu headings wrap; compact/large-font choice rows stack vertically.

`SettingsResourcesTest` also checks fallback, plural counts and palette alignment.
These checks do not prove translations or runtime locale switching. Full language
acceptance still requires reviewed strings and ordinary phone/handheld journeys,
including errors and accessibility. Never mirror the guest game or keypad as a
side effect of app RTL layout.
See the [journey verification record](checks/2026-10-28-localization-journeys.md)
and the [earlier settings checks](checks/2026-10-28-audio-lifecycle-settings.md).

`SocialLocalizationTest` checks every account/chat/directory notice and protects
user text, URLs and identifiers. `SystemRequirementsLocalizationTest` checks
phone generations and distinguishes firmware from the N-Gage 2.0 installer.
`LobbyPresentationTest` exercises seat layout across English, expanded and RTL
pseudolocales, three font scales, five widths and both themes. This is bounded
layout evidence, not full RTL navigation or live network acceptance.
See the [Social/system verification record](checks/2026-10-28-social-system-localization.md).
