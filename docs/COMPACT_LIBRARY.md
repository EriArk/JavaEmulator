# Compact library and menus

> Development update, 2026-10-26: the library description below is historical.
> The [#40 library slice](RETRO_LIBRARY_REFRESH.md) replaces it with four platform
> tabs and an always-accessible view switcher, removes the permanent details pane
> at every width, and adds temporary edge panels for library actions. The adaptive
> APK supports both portrait and landscape. In-game menus, settings and transfer
> are not redesigned by that slice. Do not describe the historical library layout
> below as current development behavior.

The compact library uses one 48 dp header and a game-focused body, not a scaled
version of the full toolbar. It uses the laid-out viewport in dp, including
insets, rather than a device model or physical pixel resolution. It activates
below 600 dp width, below 480 dp height, below a 1.45 width/height ratio, or when
the width is below 800 dp multiplied by the font scale (at least 1).
Square and near-square handhelds therefore use the single header too. The detail
pane is hidden in compact mode; game actions remain available through X / long
press. Larger wide screens retain the detail pane. Phone portrait keeps its
larger list icons and Handheld remains landscape-only.

## Interaction

- Header: AbyssME, current collection, Search, Library menu.
- Collection opens All games / Recent / Favorites.
- Start or Menu opens the library menu. Import, folders, sorting, settings and
  Gallery / List / Grid live here instead of permanently taking space above games.
- The same menu offers **Cover format: Automatic / Wide / Portrait**, including
  on larger screens. It changes Gallery presentation, not the guest-game screen.
- L/R still changes views; A launches, X opens game actions, Y toggles a favorite.
- Handheld initially focuses the first game after the list is laid out. Later
  size/data updates do not pull focus away from the header or search.
- Search expands only when requested. The close icon, Back or B clears the query
  and returns the space to the library. Search and collection survive activity
  recreation; asynchronous library loading retains the requested filter.
- Handheld hides Android system bars. An edge swipe can reveal them temporarily.
- Compact-screen game actions use the same dark menu as the rest of the app.
- List uses compact rows; Grid keeps large icons and Gallery keeps covers.
  Artwork height responds to the actual available area, including system insets.
  See [automatic artwork and custom images](ARTWORK.md).

## Menus

Phone portrait menus appear at the bottom; landscape and Handheld menus stay
centered. A fixed header holds Close or Back while the actions below scroll.
The Phone library keeps an open menu during rotation and updates its placement.
Touch targets remain at least 48 dp tall, with wrapping action labels and dynamic
choice/toggle heights for larger fonts. Display and Controls use the header's
Back arrow rather than a duplicate full-width row. Draft/try behavior is unchanged.
The first action receives controller focus after layout/window focus, not the
header's Close button; subsequent navigation is left alone.

Regression tests cover search/collection recreation, touch and controller menu
navigation, bounded menu height, large-font action labels and a non-scrolling
header. Emulator checks are not physical-device acceptance.

## General settings and transfer

Settings now has three short sections: Library, Playing and Support. It uses a
48 dp Back/title header and wrapping preference rows; saved preference keys and
defaults are unchanged. Handheld hides system bars on both Settings and Library
transfer. A/B and directional navigation remain available without touch.

Library transfer shows one workflow at a time instead of keeping every import
action above the game list. See [transfer behavior](LIBRARY_TRANSFER.md).

## Installer and confirmations

The MIDlet installer keeps its title/icon and Close control above a scrolling
body, with Install/Reinstall/Update/Start below it. Actions use flexible widths
and at least 48 dp touch targets. Archive choices form a vertical list, with
full entry paths to distinguish identically named JARs in different folders.
Dialog bounds adapt to rotation and short landscape screens; Handheld hides
system bars. Close receives initial focus on confirmation/error states.

Installation errors remain visible until Close or Back/B instead of disappearing
with a toast. During conversion, Close is hidden and Back/B is ignored; this is
not a new cancellation mechanism. JAD pairing, conversion and save behavior are
unchanged. Preparation errors use the shared menu with Retry/Exit where applicable.

Files copied from document providers now distinguish a manifest-bearing JAR
from a ZIP collection and recognize the 7z signature. Previously every ZIP
stream was staged as `tmp.jar`, bypassing archive selection. Classification
does not depend on a provider exposing a usable filename. Original files and
source identity are retained; malformed containers still report an error.

Library deletion uses the shared menu, identifies the selected game and warns
that installed settings/saves are removed. Cancel is the first controller action;
Close/Back/B also dismiss without deletion. Expert-editor dialogs are outside
this batch.

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
