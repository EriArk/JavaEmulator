# Compact handheld interface

The short-landscape library uses one header and a game-focused body, not a scaled
version of the full toolbar. It activates below 480 dp height in landscape.
Portrait Phone browsing and the large-screen detail pane keep their existing layout.

## Interaction

- Header: AbyssME, current collection, Search, Library menu.
- Collection opens All games / Recent / Favorites.
- Start or Menu opens the library menu. Import, folders, sorting, settings and
  Gallery / List / Grid live here instead of permanently taking space above games.
- L/R still changes views; A launches, X opens game actions, Y toggles a favorite.
- Handheld initially focuses the first game after the list is laid out. Later
  size/data updates do not pull focus away from the header or search.
- Search expands only when requested; B closes it and clears the query.
- Handheld hides Android system bars. An edge swipe can reveal them temporarily.
- Short-screen game actions use the same dark menu as the rest of the app.
- List uses compact rows; Grid keeps large icons and Gallery keeps covers.
  Artwork height responds to the actual available area, including system insets.
  See [automatic artwork and custom images](ARTWORK.md).

## Mapper

Short landscape removes redundant headings and reduces outer spacing, not the
readability of phone keys. Below 560 dp width, Keypad / Navigation tabs expose
the two parts of the phone. At wider landscape sizes they are shown side by side.
Controller source groups, named profiles, identification, diagonals and draft
save/cancel semantics are unchanged. Portrait retains the complete phone layout.

## Design references

[MinUI](https://github.com/shauninman/MinUI) is the primary interaction reference:
little persistent chrome, clear selection and an action menu. We do not copy its
no-artwork policy. [muOS content browsing](https://muos.dev/tour/modules/muxplore)
and [ES-DE](https://es-de.org/) provide secondary references for controller-first
library navigation with artwork. No code or visual assets were copied from them.

This document describes development source, not a new public APK release.
