# Library artwork

Development builds use the same conservative selection rules for every Java game.
There are no game-name overrides.

- A valid icon explicitly declared in MIDlet-Icon / MIDlet-1 wins, even if tiny.
  Its visible pixels are enlarged with nearest-neighbor scaling in List/Grid.
- Without a declared icon, only an unambiguous icon-named resource is used.
  Square textures and publisher logos are not substitutes for a game icon.
- Covers require positive filename evidence: title/cover or menu/background.
  Dimensions alone are not evidence. Intro/splash/logo names alone are also
  insufficient: even game-named resources can be instruction panels.
- Fonts, sprites, atlases, tiles, buttons, text panels and publisher/credits
  resources are excluded from automatic ranking. Close-scoring alternatives
  produce a fallback rather than depending on ZIP entry order.
- Gallery/detail show a cover or an icon-based fallback. Missing icons use a
  generated initial tile, not a random image from the game.

Filename heuristics do not recognize image content. They deliberately miss some
valid art, especially with short/obfuscated filenames. They are not a guarantee
that every title screen will be identified.

## Gallery format

Open **Library menu > Cover format** and choose **Automatic**, **Wide** or
**Portrait**. This is a saved global Gallery preference, not a fourth library
view. List and Grid continue to use icons. Gallery fits the entire cover inside
a consistent 16:9 or 2:3 artwork area; it does not stretch or crop the image.
Wide banners are letterboxed in a portrait area. Missing covers keep the
icon/initial-based fallback. The original image files are never rewritten.

Automatic chooses portrait when the available game area is at least 300 dp tall
and its width is less than 1.55 times its height; otherwise it chooses wide.
Columns and art dimensions share one calculation. Column targets are 140 dp
for portrait and 220 dp for wide, enlarged for larger fonts (up to 1.5x).
Art height is capped at 300/180 dp respectively and further limited by the
available height after reserving title space. Explicit Wide/Portrait choices
are retained on window resizing, navigation and activity recreation.

Included in beta.4. [Development verification](checks/2026-10-17-gallery-restart.md)
and [signed-release checks and captures](releases/v0.3.0-beta.4-checks.md).

## Change or refresh artwork

Open a game's actions (X / long press), then **Artwork**:

- **Icon / Cover > Choose image** opens Android's image picker.
- **Icon / Cover > Use automatic** removes that custom image and uses automatic art.
- **Refresh automatic art** rescans the installed Java resources without
  reinstalling the game. Its confirmation replaces old legacy art but keeps
  images chosen through the new menu.

Picked images are validated and written atomically to `user-icon.png` and
`user-cover.png`, separate from generated `icon.png` / `cover.png`. They survive
automatic refresh, Java reinstall and menu-based backup/import. No Room schema
change is required. Selection work happens off the UI thread.

Older builds used the same `cover.png` filename for both picked and generated
covers. Existing libraries are therefore **not silently rescanned on upgrade**.
On reinstall an unmarked legacy cover is preserved as custom art. Use the
explicit reset/refresh actions to replace it. Games, settings and saves are not
changed by artwork actions. Mophun can use custom artwork; Java resource scans
only apply when `res.jar` is present.

## Symbian / N-Gage

From **0.4.0 Beta 2**, native game cards expose the same **Artwork > Icon / Cover**
actions. Each image can be chosen or reset independently. Custom art is isolated
by virtual phone and game UID, takes priority over generated art and survives
library refresh. Java resource scanning is not offered for native games.

Custom images are included in [whole-phone backups](SYMBIAN_PHONE_BACKUPS.md).
Restoring creates a separate phone with its own artwork; the original is not
modified. New version-2 backups require Beta 2 or newer to restore. Version-1
backups remain readable, without custom artwork. See the
[native artwork checks](checks/2026-10-18-symbian-artwork.md) and
[backup checks](checks/2026-10-18-symbian-artwork-backup.md).

## Regression coverage

Runtime-generated PNG/ZIP fixtures cover tiny manifest icons, corrupt resources,
unrelated textures and text, candidate ambiguity, title evidence, custom image
validation, refresh and reinstall preservation. Backup/import tests cover the
new artwork files alongside the existing save-transfer checks. No commercial
game files were added as fixtures.

## Handheld library cards

| Platform | Automatic source for the game list |
| --- | --- |
| WIPI / SKVM | The carrier game JAR's MIDlet manifest and named artwork. KTF launcher PNGs named `big.icon`, `middle.icon` or `small.icon` supply the icon in that order. |
| Dingoo A320 / Gemei A330 | Named icon or cover in the distribution ZIP. Packages without one can acquire an in-game preview. |
| Aquaplus P/ECE | The embedded 32 × 32 grayscale icon in a PEX executable or a PFS distribution. An older installed game image can recover the icon when its added executable can be identified. |
| GamePark GP32 | The 32 × 32 indexed bitmap in a standard FXE header. Raw executables, SmartMedia and other formats can use packaged art or a runtime preview. |
| Watara Supervision / Mega Duck | Named art in a cartridge ZIP, or a frame from playback for a bare cartridge. |
| Cybiko | Original grayscale `root.ico` in a CyOS application, including compressed resources. Both game editions keep their own artwork. |

The shared importer checks ZIP artwork before unpacking the game. Existing cards can recover embedded art from their installed files. A ZIP wrapper discarded by an older import can be selected again to recover its cover. The preview comes from a populated framebuffer after 240 presented frames when both icon and cover are missing. It can show an intro or a menu. Cybiko omits this fallback because a cold boot opens the system desktop. User-selected images retain priority.

Java ME retains the MIDlet rules above. Mophun retains its existing runtime capture. Symbian and N-Gage retain the native catalog's artwork and per-game overrides. [Retroid checks](checks/2026-11-20-cybiko.md)
