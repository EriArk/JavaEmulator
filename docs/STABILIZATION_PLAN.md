# Stabilization Queue

Maintainer-approved order, 2026-10-28: **#41 -> #42 -> #45 -> #43**.
New platforms in **#44 are deferred**. This is not a release promise or a claim
that all acceptance checks are complete. See [current status](PROJECT_STATUS.md)
for availability and the dated evidence behind each result.

## 1. Java audio (#41)

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
