# Localization Workstream

Issue [#43](https://github.com/EriArk/JavaEmulator/issues/43), checkpoint 2026-10-28.
English remains the canonical source/fallback and the public APK's advertised UI.
Unreleased source now includes Settings > Language, System default, English,
Spanish, Brazilian Portuguese, Russian, French, German, Japanese and Simplified
Chinese. Other inherited locale folders remain
partial translations; their presence is not whole-app support. This work has not
been published in a new signed APK.

| Locale | Canonical resource coverage | Availability |
| --- | --- | --- |
| English | 1517 source entries | Released fallback; current source includes newer labels |
| Spanish | 1517 / 1517 | Selectable in unreleased source / local Debug |
| Brazilian Portuguese | 1517 / 1517 | Selectable in unreleased source / local Debug |
| Russian | 1517 / 1517 | Selectable in unreleased source / local Debug |
| French | 1517 / 1517 | Selectable in unreleased source / local Debug |
| German | 1517 / 1517 | Selectable in unreleased source / local Debug |
| Japanese | 1517 / 1517 | Selectable in unreleased source / local Debug |
| Simplified Chinese (`zh-CN`) | 1517 / 1517 | Selectable in unreleased source / local Debug |
| Other inherited locales | Partial | Not advertised by the picker |

Counts include strings, plurals and arrays; they do not certify translation
quality or every low-level diagnostic. The [first language batch record](checks/2026-10-28-app-languages.md)
separates source, device/emulator checks and remaining acceptance.
The [French/German batch](checks/2026-10-28-french-german-localization.md) adds
network-preparation messages and shared long-word/large-font layout repairs.
The [Japanese/Chinese batch](checks/2026-10-28-japanese-chinese-localization.md)
extends the selectable list and CJK layout/lifecycle checks. On 2026-10-28 the
maintainer paused further language expansion after these two: Indonesian,
Hindi and the second wave remain deferred, not delivered or removed.

## Language Selection

Native language names remain visible in every UI language. The selected option
has a check mark and checked accessibility state, distinct from gamepad focus.
Settings choices stack when a word cannot fit the label column; wider rows can
return to two columns. Archive summaries scroll rather than consume the list,
and lobby initials grow with the actual font metrics.
Android 13+ uses the platform LocaleManager and declares the same supported
list in `xml/app_locales.xml`; Android Settings and the in-app picker share it.
Android 10-12 uses AppCompat 1.6.1 plus a non-exported, single-writer provider for
cross-process persistence. Application and plain native Activity contexts are
updated as well, because AppCompat alone only handles AppCompat Activities on
these versions. No external translation service or runtime download is needed.
Bundle language splitting is disabled so a future bundle install also keeps
picker languages available offline; this batch does not publish an App Bundle.

Java, Mophun and Symbian game Activities handle locale changes without locale-
driven Activity destruction. Idle Symbian setup refreshes its labels; open
native forms/import selections are not rebuilt just to change their text.
They use the new locale when reopened. Java's default `microedition.locale`
comes from the device language, not the app UI language; an explicit game
profile override still takes precedence. Guest phone language and user/chat
content are not translated.

This does not claim every low-level diagnostic is localized. Unknown OS/native
errors and old persisted raw error messages can still appear verbatim under a
localized explanation. Do not treat
resource coverage as full network-session or native-game acceptance.

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
- `values/network_preparation_strings.xml`: 12 typed game preparation/library
  failures, including changed files, different game editions, missing setup and
  unsupported adapters. Room state retains a notice identity; rendering follows
  the current UI locale. Unknown filesystem/converter exceptions use a localized
  recovery message while the original exception is retained in the diagnostic
  log. No English-message matching is used. Late picker failures are ignored
  after the account changes or the Activity is destroyed.
- `values/system_files_strings.xml`: system-files entry, generation-specific
  requirements, phone-dump help and pending-import decisions. Rendering uses
  import kind/readiness, not English title comparisons. Technical identifiers
  in help are protected with XLIFF placeholders.
- `values/native_transfer_strings.xml`: Symbian host actions, saved components,
  phone backup/restore and uninstall confirmations, archive queue labels/states,
  import summaries and transfer progress. Complete warnings preserve the
  distinctions between backup versions, removal and keeping installed phones.
- `values/native_error_strings.xml`: 55 typed app-owned backup/removal failures.
  Core errors remain `IOException` subclasses with English diagnostics and
  preserved causes; presentation uses keys, not matching English messages.
  Unknown/native/OS errors and user-provided paths stay verbatim.
- `values/native_import_error_strings.xml` and `native_import_strings.xml`:
  another 163 typed errors for archives, firmware and SIS/component imports,
  14 additional progress events, 11 persistent queue notices and native/Mophun
  installer labels. The total typed native error set is now 218 keys.
- New queue journals retain optional notice/error identities alongside the old
  English fallback fields. Old version-1 journals remain readable; their old
  raw messages are not guessed or rewritten into translation keys. Invalid or
  oversized display metadata falls back to the raw diagnostic. Journal writes
  cannot replace the previous queue with an unreadably large record.

Display resolution is selected by numeric dimensions, not by parsing translated
labels. Phone profile IDs and stored mapping names remain stable; built-in
mapping names have separate display aliases. Transfer idle state is semantic,
not an English string comparison. Play styling and image-preset swatches are
explicit properties, not label comparisons.

Room/publication/mailbox notices and temporary-contact errors now carry typed
display identities. New contact journal records store optional error keys;
old, unknown or malformed keys retain their raw text. No display key changes
network packets, contact decisions or account ownership. Switching accounts
clears the previous mailbox status. Game-information labels use resources while
game versions and vendor names remain verbatim. About/Help now describe AbyssME,
preserve upstream attribution and link to this project's support and sources.

The first selectable translation batch covers all canonical Android resources,
but is not a claim that every diagnostic is translated. Remaining backend
messages (including Mophun and external diagnostics)
and helper-returned text still need extraction or review. Guest language is not
modified as a side effect. English actor fallback fields are retained for diagnostics and
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

1. Finish remaining backend/helper diagnostics.
2. Keep Indonesian, Hindi and the wider language list deferred until the
   maintainer resumes expansion. Do not infer support from inherited folders.
3. Verify long-running native/network sessions and translated journeys before
   publishing a multilingual APK. Arabic/Hebrew still require explicit RTL QA.

## Manual Regression Checks

Run with JDK 21 from the repository root:

```powershell
java tools/LocalizationAudit.java --self-test
java tools/LocalizationAudit.java --check
```

The AST inventory reports direct UI literal candidates in app-owned Java sources;
a structured XML scan also checks visible labels in layouts, menus and preferences.
These are not an exhaustive scan of helper-returned text or native code. The
check rejects new direct literals in extracted screens and XML labels, missing canonical keys
for languages advertised by `app_locales.xml`, and structural translation errors
(format argument types and array lengths). Reports go to ignored `build/`.
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
`LobbyPresentationTest` exercises seat layout across English, Spanish, Brazilian
Portuguese, Russian, French, German, Japanese, Simplified Chinese and expanded/RTL
pseudolocales, three font scales, five widths and both themes (300 cases). This is bounded
layout evidence, not full RTL navigation or live network acceptance.
See the [Social/system verification record](checks/2026-10-28-social-system-localization.md).

`SystemTransferLocalizationTest` checks every typed backup/removal error,
version-specific warnings, raw paths/causes, component versions, cancellation,
queue callbacks and layout across 480 locale/font/viewport/theme/state cases in
the Japanese/Chinese batch (144 in the earlier native-transfer record).
Archive controls stack for narrow/large-font layouts and keep their actions
independent of display labels. This does not prove all firmware/backend errors
are localized or all RTL navigation is ready.
See the [native transfer checks](checks/2026-10-28-native-transfer-localization.md).

`NativeImportLocalizationTest` covers typed error persistence, locale-time
rendering, old/unknown/malformed queue metadata, bounded journal writes and
legacy progress callbacks. `PreparedSystemArchiveTest` also verifies that
cancelling a phone installation stops the collection, removes staging and
preserves already imported phones. See the
[native import checks](checks/2026-10-28-native-import-localization.md).

`LobbyNoticeStateTest` protects typed room/mailbox/publication state, account
reset, old contact journals and wire-format separation. `SocialLocalizationTest`
also checks contact keys, raw rejection codes and player names. `InfoContentTest`
checks current help, attribution/support links, unchanged game metadata,
60 locale/font/viewport/content layouts and About/More/Help close callbacks.
These are offline checks, not live multiplayer or full translation acceptance.
See the [room/help checks](checks/2026-10-28-social-help-localization.md).
