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

## Change or refresh artwork

Open a game's actions (X / long press), then **Artwork**:

- **Choose icon / Choose cover** opens Android's image picker.
- **Reset icon / Reset cover** removes that custom image and uses automatic art.
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

## Regression coverage

Runtime-generated PNG/ZIP fixtures cover tiny manifest icons, corrupt resources,
unrelated textures and text, candidate ambiguity, title evidence, custom image
validation, refresh and reinstall preservation. Backup/import tests cover the
new artwork files alongside the existing save-transfer checks. No commercial
game files were added as fixtures.
