# Graphics profiles, control profiles and extra buttons

**Released in 0.7.0 Beta 7 (November 14, 2026):** graphics and controls now have separate
catalogs, global defaults, named profiles and assignments. Published Beta 6 still
has the earlier combined touch/LCD profiles. This separation requires a newer
build; install Beta 7 over the official app.
[Release verification](releases/v0.7.0-beta.7-checks.md).
[Implementation checks](checks/2026-11-14-graphics-profiles.md).

## Independent global defaults and named profiles

Open **Settings → Graphics profiles** or **Settings → Control profiles**. Each
category has its own **Global default**, named profiles and bulk assignment.
A game can use, for example, graphics profile **CRT** and control profile
**Standard Gamepad**, or global defaults for either category independently.
Editing, selecting, deleting or resetting one category does not alter the other.

In a game's **Display → Graphics profiles** or **Controls → Control profiles**,
choose a base and inspect where each setting comes from. Resolution order is:

1. That category's global defaults.
2. The assigned named profile, for fields it defines.
3. Explicit game overrides.

Profiles can be edited, renamed, duplicated, deleted, or created from a game's
settings. Assignments remain linked; shared edits apply on reopening a game.
Game edits create overrides and retain Try / Keep / Undo. A live picture preview
keeps or undoes only graphics, including its selected profile, without saving a
pending control trial. Shared-definition edits have their own Save confirmation.

**Assign profiles to games** supports prepared games in bulk. Assignment retains
overrides unless **Also reset graphics game overrides** or **Also reset control
game overrides** is explicitly enabled. Failed/unprocessed selections remain for
retry. Deleting a named profile falls back to that category's global defaults,
retaining game overrides.

## Graphics contents and engine support

Graphics profiles include picture sizing, scale percentage, position, rotation,
orientation, background color, smoothing, built-in xBRZ/CRT shaders, FSR and its
sharpness, frame interpolation, LCD filter/intensity, screenshot filtering and
Mophun's original/enhanced capture preference.

- Java GPU and Mophun support the built-in xBRZ/CRT, FSR and interpolation options.
  Retro shaders use their own filtering; FSR, interpolation and LCD overlays are
  inactive while a retro shader is selected.
- Java software/custom-renderer paths retain their existing capability limits.
  A profile never switches the renderer to enable an unsupported effect.
- Scale percentage, position and background color apply to Java.
- Symbian uses supported host size, rotation, orientation and LCD settings.
  Integer scaling falls back to Fit. It does not gain Java/Mophun GPU shaders.

Guest resolution, engine/compatibility settings, custom external shader files,
frame-rate limits, emulation speed and CPU budgets remain per game.

## Control contents and upgrade preservation

Control profiles cover modern Phone/Gamepad layouts, keypad placement, button
size/theme/opacity, portrait/landscape reach and positions, touch key mappings,
extra buttons, visibility and tactile feedback. Physical-controller bindings and
legacy keypad files retain their existing per-game editors.

The first migration splits Beta 6's combined named profiles and global defaults:
LCD values move to graphics, while touch values remain in controls. Existing LCD
inheritance and exceptions are preserved. Other existing graphics settings become
explicit game overrides, so migration does not silently change the picture.
The two assignments can then be changed independently. Pre-profile game settings
remain overrides; new games inherit both global defaults.

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

Java/Mophun library backups include both catalogs, including unassigned
named profiles in each category. Each backed-up game config also carries its current resolved
base definition, assignment and explicit overrides. Export resolves the current
profile without rewriting the source game settings. Symbian phone backups use
the same portable per-game definition through the host's backup callback.

Restore preserves destination definitions and saves. Matching identities and
values reuse a profile; conflicting identities receive a new ID and conflicting
names receive a suffix. Distinct named profiles remain distinct even if their
settings happen to match. Imported global defaults become a named profile when
needed, rather than replacing defaults for unrelated destination games.

The library's `shared-profiles.json` (controls) and `graphics-profiles.json`
(graphics) are written atomically. The LCD catalog is persisted before a
controls-only write replaces a Beta 6 combined catalog. Field names and
profile/button identities are stable; explicit null overrides remain distinct
from absent inherited fields. Unknown profile fields are excluded. Each catalog
is bounded to 200 profiles / 1 MiB and each layout to twelve extra buttons.

[Actual device UI captures](../screenshots/development/2026-11-14-profiles/README.md).
