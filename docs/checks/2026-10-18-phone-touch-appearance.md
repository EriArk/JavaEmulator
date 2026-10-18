# Independent touch appearance and Phone keypad

Date: 2026-10-18. Development source after `de744918`; not a new public APK.

## Behavior

- Phone: Game menu > Controls > Button theme offers Follow app, Dark and Light.
  This changes both the touch deck and top display/screenshot/menu shortcuts,
  not the library or settings dialogs. All three engines use the same isolated
  palette. Handheld shortcut styling is unchanged.
- The selection is per-game and uses the existing Try changes / Undo / Keep
  flow. Mophun's older touch page also exposes the choice through its existing
  save/rollback path. Java, Mophun and Symbian share the renderer and profile
  field; there is no separate palette implementation per engine.
- Missing `touchTheme` defaults to zero (Follow app). No profile-version or
  database migration is needed. The custom Legacy layout keeps its existing
  color editor; Handheld does not gain on-screen controls.
- Phone numeric keys have a shallow raised edge, medium-weight digits,
  alphabet legends and two small marks on 5. Landscape Phone uses the existing
  connected D-pad geometry. Touch rectangles, key codes and multi-pointer
  ownership remain unchanged.
- Palette resources are resolved in an isolated configuration context. They
  cannot change the host application's light/dark mode. Cached palettes refresh
  when the effective button appearance changes, including preview and Undo.
- Phone's upper display/screenshot/menu row is now transient. A small arrow at
  the upper edge opens it over the game; it collapses after five idle seconds.
  The arrow has a 48dp touch target with a smaller visible face. Normal game and
  keypad touches do not reveal it. Java's old tap-to-open shortcut is retained
  only in Handheld. Existing Back/menu/controller access remains unchanged.
- Portrait no longer reserves 48dp for the upper row: it uses the same 8dp edge
  inset as landscape. Showing/hiding shortcuts never resizes or pauses the game.
  Holding a shortcut suspends the timer until release/cancel; backgrounding,
  dialogs and view detachment collapse the row and cancel pending callbacks.
  Screenshot/display visibility preferences still apply.

## Checks

- Final base Handheld/Phone and combined Handheld/Phone developer APKs built;
  all four lint tasks passed with zero errors. Logs:
  `build/touch-chrome-base-build.log` and `build/touch-chrome-combined-build.log`.
- Android 15 Phone emulator: 43 selected checks passed. This includes palette
  overrides in both app modes, live changes while holding a key, pressed-state
  rendering, old-profile defaults, serialization, saved preview/Undo/Keep,
  existing hit-target/multitouch and menu geometry tests. Three new chrome cases
  check explicit reveal/game touch routing, actual five-second expiry, held
  shortcut timing, preferences and collapse when unavailable.
  Log: `build/touch-chrome-phone-tests-final.txt`.
- Physical Retroid / combined Handheld debug: 34 selected checks passed,
  including the shared chrome component, Mophun and Symbian touch adapters,
  touch deck and saved previews. This is shared renderer/input
  verification, not a physical-phone ergonomics or full-game acceptance claim.
  Log: `build/touch-chrome-retroid-tests.txt`.
- The real Phone UI case passed with the separate Bounce Tales fixture:
  both layouts/orientations, light app with dark controls and top shortcuts,
  switching back to Light, saved Dark after relaunch, and collapse after the
  idle timeout. Log: `build/touch-chrome-real-ui-final.txt` (1 case, 130.755s).
  All seven published captures were visually inspected for visible game picture,
  readable controls and layout fit. They show a game menu, not a full playthrough.
  [Development captures](../../screenshots/development/2026-10-18-phone-controls).

The opt-in `TouchAppearanceUiTest` uses `-e touchGameUri <readable-JAR-URI>` in a
Phone debug package. It changes actual game settings, captures both layouts and
orientations, and relaunches the game to check the saved Dark selection. Games
are supplied separately and are not part of the test APK.

Mophun/Symbian's Phone activity wiring was compiled, but this batch did not
repeat real native-game Phone sessions. Retroid checks used the existing
separate combined Handheld debug package, not a new Phone installation.

No signed release, game/firmware publication or GitHub Actions change is part of
this batch. The owner's signed Retroid beta and library were not replaced.
