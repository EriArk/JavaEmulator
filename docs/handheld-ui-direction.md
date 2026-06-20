# Handheld UI Direction

## Product feel

The app should feel like a small handheld console launcher, not like an Android file list.
The emulator core stays quiet and reliable; the shell around it becomes visual, controller-first,
and quick to operate with one thumb.

Core words: compact, warm, tactile, readable, fast.

## Portrait

- Top: compact app bar with search, sort, and import.
- Main: game library as rich rows or a two-column grid, depending on width.
- First item: "Continue" strip for the last played game, with cover art and a large Play action.
- Game card: extracted cover when available, icon fallback, title, vendor, version, active preset.
- Bottom/FAB: import JAR/JAD, but also available from controller-focused actions.
- Detail panel opens as a bottom sheet: Play, Settings, Controls preset, Edit artwork, Delete.

Portrait is for touch browsing and quick setup.

## Landscape

- Left rail: Library, Recent, Profiles, Settings.
- Center: controller-focusable grid/list of games.
- Right detail pane: large cover, title, metadata, Play, Controls, Display, Keymap.
- Focus states must be obvious: 2-3 px accent ring, slight scale, no layout jump.
- Default selected item should be the last played game.
- No tiny overflow-only actions; every common action gets a visible controller path.

Landscape is for handhelds and Android gaming devices.

## In-game overlay

- Default handheld mode hides touch controls and uses physical gamepad mappings.
- Menu/Back opens a pause overlay, not Android's old overflow menu.
- Pause overlay actions: Resume, Controls, Display, Save screenshot, Exit.
- Overlay should be navigable entirely with D-pad/A/B.
- Touch-only virtual keyboard remains available for phones, but is secondary.

## Visual system

- Dark console theme by default: charcoal, off-white text, amber action color, teal secondary.
- Light theme exists but should still feel like a device UI, not a settings form.
- Cards: max 8 dp radius, no nested cards.
- Cover aspect: 16:9 or 2:1 crops for landscape banners; icon fallback centered on color field.
- Typography: dense and readable; no oversized marketing hero text inside tools.
- Motion: short focus transitions, no decorative background blobs.

## Artwork extraction

Already available:
- `Descriptor.getIcon()` reads `MIDlet-Icon` or the icon field from `MIDlet-1`.
- `AppInstaller` extracts that entry into `icon.png` and stores it in `AppItem.imagePath`.

Add next:
- Add `coverPath` to `AppItem` and a Room migration.
- Scan JAR resources for PNG/JPG/JPEG using bounds-only decode.
- Score cover candidates:
  - boost paths containing `title`, `splash`, `menu/background`, `background`, `cover`, `logo`, `loading`;
  - boost landscape ratios around 1.5-2.5 and large image area;
  - penalize `font`, `sprite`, `button`, `icon`, `tile`, `level`, `digits`, tiny assets.
- Save the best candidate as `cover.png` in the installed app directory.
- If no cover is found, use icon + generated gradient/color field.
- Later optional fallback: user-triggered "Use current screenshot as cover" from the running game.

## Tech direction

Recommended path:
- Keep emulator runtime native Android.
- Move launcher/settings UI gradually to Jetpack Compose or a modern RecyclerView/Material approach.
- Do not rewrite the emulator shell in Flutter yet.

Why:
- Flutter add-to-app is possible, but adds a second runtime and build stack for UI only.
- The emulator uses Android `SurfaceView`/`GLSurfaceView`, Android input, Room, file pickers, and native Android lifecycle.
- Compose can be embedded into existing View screens with `ComposeView`, so migration can be incremental.

First implementation slice:
1. Replace `ListView` with a controller-friendly library screen.
2. Add `coverPath` extraction and card UI.
3. Add landscape detail pane.
4. Add pause overlay for gamepad use.
5. Polish settings into grouped handheld/display/input pages.

