# Shared touch profiles and extra buttons

Implemented on November 14, 2026 for #53 and #54. **Unreleased source and a
separate maintainer Debug build; these features are not in the Beta 5 APKs.**
See [verification](checks/2026-11-14-shared-profiles.md) for the tested scope.

## Global defaults and named profiles

Open **Settings → Profiles / Global defaults**. Set **Global default** for new
games, or create named profiles such as Standard Gamepad and Full Keypad.
Profiles can be edited, renamed, duplicated or deleted. The per-game profile
panel also offers **Create from this game's settings**.

Open a game's **Controls → Profiles / Global defaults** to choose its base.
The Display panel also provides a management entry. Settings resolve in this order:

1. Global default.
2. The assigned named profile, for fields it defines.
3. Explicit game overrides.

Assignment links a game to a profile; it does not make an independent settings
copy. Shared edits take effect the next time a game opens. The per-game panel
lists the source of each setting. Clear an override to return to the currently
assigned base, or explicitly reset all touch/LCD overrides. Game edits never
modify the shared definition. Per-game changes retain Try / Keep / Undo.

**Existing game settings are preserved as overrides on upgrade.** To adopt a
shared setup across an existing collection, use **Assign profiles to games**,
select the prepared games, choose the profile, and explicitly enable resetting
touch/LCD overrides if wanted. Assignment alone retains exceptions. Bulk writes
run in the background; failed/unprocessed selections remain available for retry.
Deleting a profile removes known assignments and falls back to Global default,
keeping per-game overrides. Missing definitions also fall back safely.

Profiles cover modern Phone/Gamepad layouts, placements, size, opacity, touch
key mappings, extra buttons, visibility, tactile feedback and LCD filters.
Java, Mophun and Symbian use the shared supported touch/LCD fields. Legacy keypad
files, physical-controller bindings, guest resolution/compatibility and
engine-specific shaders/performance options stay outside this profile schema.

## Extra Gamepad buttons

Open **Controls → Edit touch layout → Extra** with the Gamepad layout selected.
Use **+ Add button**, choose its guest key, and drag it in the preview or use the
movement arrows. Each button has its own label, opacity, size and position.
Position and size are independent for portrait and landscape. Up to twelve
buttons can coexist with the built-in controls, including in 123/Actions mode.

Supported bindings are directions, OK/Fire, soft keys, digits 0–9, `*` and `#`.
Press/release/repeat and simultaneous touches share the existing guest input
path. Layout rebuilds and cancellation release held keys. Extra buttons do not
change physical-controller mappings or move the built-in controls.

**Use layout** accepts the editor draft; the game's Try / Keep flow confirms it.
Cancel leaves the previous layout intact. Remove one button or reset the Extra
tab to remove the added controls. Shared profiles can carry these buttons too.

## Backups and storage

Java/Mophun library backups include the shared catalog, including unassigned
named profiles. Each backed-up game config also carries its current resolved
base definition, assignment and explicit overrides. Export resolves the current
profile without rewriting the source game settings. Symbian phone backups use
the same portable per-game definition through the host's backup callback.

Restore preserves destination definitions and saves. Matching identities and
values reuse a profile; conflicting identities receive a new ID and conflicting
names receive a suffix. Distinct named profiles remain distinct even if their
settings happen to match. Imported global defaults become a named profile when
needed, rather than replacing defaults for unrelated destination games.

The library's `shared-profiles.json` is written atomically. Field names and
profile/button identities are stable; explicit null overrides remain distinct
from absent inherited fields. Unknown profile fields are excluded. The catalog
is bounded to 200 profiles / 1 MiB and each layout to twelve extra buttons.

[Actual device UI captures](../screenshots/development/2026-11-14-profiles/README.md).
