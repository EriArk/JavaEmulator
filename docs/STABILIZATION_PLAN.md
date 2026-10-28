# Stabilization Queue

Maintainer-approved order, 2026-10-28: **#41 -> #42 -> #45 -> #43**.
New platforms in **#44 are deferred**. This is not a release promise or a claim
that all acceptance checks are complete. See [current status](PROJECT_STATUS.md)
for availability and the dated evidence behind each result.

**Publication update, 2026-10-28:** the implementation batches for #41/#42/#45/#43
are included in [0.7.0 Beta 1](releases/v0.7.0-beta.1-checks.md), together with
#47's partial-update installer repair. Earlier "unreleased"/"started" paragraphs
below are historical checkpoints, not current availability. Exact quiet-music
editions, Treasure Towers and full translation review remain open. **#46 centered
Grid/Gallery card names is included in Revision 1**, after being missed in the
original code-313 APK. Keep that distinction separate from centered game-menu
confirmations. [Revision checks](releases/v0.7.0-beta.1-r1-checks.md). #44 stays deferred.

## 1. Java audio (#41)

**Further unreleased work, 2026-10-28:** shared stream/load/prefetch/seek/replay
repairs, retained duration, and original-rate ADPCM conversion. 20 Retroid audio
checks plus two additional private MIDI comparisons pass. Three game samples
match direct Android MIDI output closely; this does not establish original-phone
synth balance or fix the exact reported editions. Five more games installed in
Debug only. [Earlier evidence](checks/2026-10-28-audio-pipeline.md).

**Follow-up implemented:** direct MIDI instance/output isolation and channel/
master gain semantics, plus native 16-bit IMA/Yamaha ADPCM conversion without
replacing the existing libraries or instruments. 402 JVM / 29 Retroid audio
checks pass. Exact reported editions and listening/routing acceptance remain
open. [Evidence](checks/2026-10-28-audio-isolation.md).

**Lifecycle follow-up implemented:** ordered per-player callbacks, finite-repeat
replay/pause bookkeeping, retained end position, safe deallocation and Nokia
replacement/repeat notifications. 408 JVM and 35 Retroid audio checks pass.
[Evidence and unchanged acceptance limits](checks/2026-10-28-audio-lifecycle-settings.md).

- Repair API-level gain/mute defects without game-name exceptions or a global
  boost that changes every title's sound balance.
- Keep Nokia Sound, MMAPI MIDI files, direct MIDI, WAV and tone paths distinct.
- Compare actual requested levels and measured output before changing curves or
  synthesizer balance. Preserve intentional fades and independent players.
- Reported Avatar 1.1.6 and Cops L.A. Police 1.2.3 remain separate acceptance
  targets from the locally available 1.0.6 and 1.2.1 editions.

## 2. Phone menu handle and opacity (#42)

- Move the existing down-arrow in a detached visual editor, independently for
  portrait and landscape. Clamp it inside the usable screen after resizing.
- Allow hiding it while retaining Android Back as a touch-only menu path.
- Separate shortcut opacity from the existing touch-button opacity; retain
  defaults and High contrast readability. Do not fade the game renderer.
- Reuse Try/Keep/Undo. Preserve existing global Display/Screenshot visibility,
  transient five-second shortcuts, controller mappings and other engine menus.

## 3. Per-game touch gamepad mapping (#45)

**Implemented in unreleased source, 2026-10-28:** graphical Buttons tab,
Native/Numpad templates, stable identities, explicit diagonals, per-game
serialization and reversible trials. 398 JVM / 72 Retroid Android passes.
The Phone keypad and 123 bank remain literal recovery surfaces. Treasure Towers
is not yet reproduced; physical touch-phone/accessibility acceptance remains
open. [Evidence](checks/2026-10-28-touch-gamepad-mapping.md).

- Extend the existing editor with stable semantic button identities, a visible
  phone-key target picker and simple Native/Numpad templates.
- Keep mappings separate from physical controllers and per-orientation geometry.
  One mapping applies in both orientations; placement stays independent.
- Support arrows, fire, soft keys, digits, star/hash and explicit diagonal
  overrides. Preserve current two-key diagonals when no override is selected.
- Release held inputs safely on remap, layout switch, cancellation and pause.
  Keep draft, preview, accept and reset operations reversible.
- The Treasure Towers report motivates this work; it does not establish that
  touch mapping is the game's only compatibility problem.

## 4. Localization (#43)

**Started, English source extraction:** general Settings, library actions,
shared game menus/display settings, transfer/installer UI and physical/touch
mapping now use Android string, array and plural resources. Localized labels no
longer determine resolution values, transfer idle state, Play styling or shader
swatches. Account/chats/friends, lobby directory/room actions and system-file
requirements now use resources too. Notice enums and explicit content kinds
keep behavior and user text independent of translated labels. No language picker
or new translated language is enabled. Native host/queue/backup/component menus,
uninstall warnings and 55 app-owned backup/removal errors now have resource-backed
presentation too. Persisted archive details, remaining firmware/component backend
errors, room/publication/contact backend notices and helpers remain open,
followed by process-safe language selection and reviewed language batches.
[Extraction scope and contributor glossary](LOCALIZATION.md).

- Extract user-visible strings from shared screens and engine hosts before
  adding reviewed language batches. English remains the source/fallback.
- Provide an app language choice with consistent behavior in separate runtime
  processes. Do not translate game text, user content or saved identifiers.
- Check long strings, compact handhelds, portrait phones and error messages,
  not only the main library. Keep existing profiles and accounts intact.

## Working Boundaries

Issues stay open until their specific acceptance checks are supported by evidence.
A repaired API stub is not proof that a reported game is fixed. Source changes,
local Debug APKs, signed release assets and manual device acceptance are recorded
separately. Firmware, commercial games, extracted media and private traces remain
outside the repository. GitHub Actions stays disabled.
