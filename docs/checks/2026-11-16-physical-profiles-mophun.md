# Physical controller profiles and Mophun audio — 2026-11-16

## Availability

Local source and a private debug build, not a new public beta. The signed ARM64
Beta 9 application remains installed separately. Release preparation is pending
reproduction of the reported Mophun games and final dual-architecture checks.

## Physical controller profiles

A third independent catalog, `physical-profiles.json`, stores global defaults
and named physical-controller profiles. Settings and each game's Controls page
open the manager. The existing mapper edits buttons, D-pad, both sticks and
diagonals. Shared changes apply when a linked game opens again. Existing games
retain their own bindings until explicitly reset; bulk assignment can reset
only physical mapping overrides. Touch and graphics selections remain separate.

The mapping preset list, selected preset and effective bindings form one atomic
setting. Try/Keep/Undo includes the new assignment. Library backups include the
catalog; portable per-game definitions preserve mappings across restore without
overwriting destination profiles. A legacy-library import regression was found
and corrected: imported settings without the new inheritance marker must become
explicit physical overrides, rather than being replaced by global defaults.

## Mophun audio

The old session gate enabled sound only for one Deep Abyss 1.4 executable hash.
That game-specific restriction is removed. Every game can now use the existing
MIDI/resource and PCM paths. PCM follows host pause/resume and emulation speed;
initializing while paused cannot start playback, and teardown wakes/joins the
mixer and releases AudioTrack. Output failures stop the worker instead of spinning.
This is an engine change, not an allowlist of tested games.

The report names Boulder Dash and Lemtris as too fast/silent, and V-Rally 2 and
1849 Gold Rush as silent. Those files have not yet been located in accessible
local folders; the owner requested their own archive instead of the screenshot's
Google Drive link. Its location is still needed. Existing `vWaitVBL` is a no-op,
but that alone does not establish the cause of the reported speed. No speculative
timing change or game-specific frame cap has been applied.

## Verification checkpoint

- Debug build and 434 fresh app JVM tests pass, no failures/errors/skips.
- 25 Android profile checks pass: new physical catalog semantics and existing
  graphics/touch profile behavior.
- A later 37-test Android run passes: physical/UI profiles, real mapper editing,
  settings trials, right-stick integration and PCM lifecycle/mixing.
- All 27 library importer tests pass after the legacy physical-binding migration
  fix, including backup round-trip with an unassigned shared profile.
- Four existing MIDI tests pass (preparation, pause/resume, replacement, speed).
- The new shared mapper initially showed the old “Save & resume” action; changed
  it to “Use mapping”, then reran the UI tests successfully. UI captures were
  inspected on Retroid Pocket Classic / Android 14.
- Deep Abyss 1.4 and 1.1 each pass bounded launch, input, pause/resume, reimport
  and cold activity relaunch checks. Version 1.4 creates two save files; 1.1
  creates none during the sampled sequence. Captured gameplay frames from both
  versions were visually inspected. This is not full-game or audible
  output acceptance, and does not substitute for the four reported games.

Private evidence: `build/checks/2026-11-16-physical-profiles/` and
`build/checks/2026-11-16-mophun/`. Commercial files are not published.
