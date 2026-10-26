# Retro-Modern UI Refresh (#40)

## Scope and Baseline

2026-10-26, first implementation slice on top of `08e90023`.
This is development work, not a released APK or completion of the entire epic.

Baseline inventory:

| Surface | Existing behavior retained |
| --- | --- |
| Library | Gallery/List/Grid, Automatic/Wide/Portrait covers, favorites, recent history, sorting, global Play last |
| Java/Mophun card | Play, favorite, edit title/artwork, settings, information, supported shortcut, reinstall and confirmed delete |
| Symbian/N-Gage card | Play, information, title/artwork overrides, export launch file, favorite, system files and supported uninstall review |
| Library menu | File/folder import, system files, sorting, cover format, settings and existing social/account entry points |
| In-game menu | Resume, Fast-forward, Display, Controls, Screenshot, Keep/Undo draft, More, Restart and Exit; unchanged in this slice |
| Phone input | Phone/Gamepad/Legacy layouts and existing editor; unchanged in this slice |
| Social | Existing account, directory, friends and chats; no new actor, authentication or room authority |

Retroid baseline capture: `.local/ux40/retroid-before.png` (local only).
The former header hides platform choice inside Collection and exposes a permanent
game-details pane on sufficiently wide screens. The new library removes that pane
entirely; it does not merely hide it at selected widths.

## Library Slice

- Four first-class platform tabs: Java ME, Mophun, Symbian, N-Gage. There is no
  permanent All platforms tab. Existing game/phone identity and import routes stay intact.
- Collection filters remain within each platform. N-Gage generation filtering is
  offered only when the native catalog contains both Classic and 2.0 entries.
- Remember view, collection, query and selection separately per platform. Migrate
  the previous view/category preferences once, preserving legacy position anchors.
- Per-collection/query/generation/view scroll anchors use original game paths.
  Deleted or filtered-out anchors fall back to the first remaining item.
- Circular L2/R2 platform navigation uses a per-device key/axis latch with
  hysteresis; L1/R1 still selects views. Touch taps launch and holds open actions.
  A short horizontal swipe switches platforms without claiming vertical scrolling,
  long holds or multitouch gestures.
- Search, import, Social and Menu occupy the header; narrow layouts keep import
  available in the menu. All three view controls stay directly accessible.
- Context/library menus opt into an edge panel: side on controller/landscape,
  bottom on touch portrait. In-game dialog placement/lifecycle is not changed yet.
- Social is a direct library entry into the existing Lobbies, Friends, Chats and
  account flows. No fabricated unread counts or unsupported online capability.

## Remaining Epic Work

- Full contextual primary/More organization and small game artwork in panel headers.
- Shared in-game side/bottom navigation, multiplayer pause/input safety and Social.
- Instant reversible Display tiles, paused-frame refresh and capability guards.
- Phone touch-control visuals and per-game Separate/Overlay keypad geometry.
- Lobby seat presentation and live active-room entry.
- Wider accessibility, animation, upgrade and physical-button acceptance matrix.

Do not mark #40 complete or describe these remaining items as implemented.

## Verification (2026-10-26)

- Combined arm64 Debug APK, development identifier `0.5.0-beta.1` / 310;
  not a replacement for the signed public APK. SHA-256:
  `cb26b2e27311a75c46667566d61c53599e062e68c3979cab139755d3700108e1`.
  Installed APK hashes match on Retroid Pocket Classic and Android 35 emulator.
- Build `:app:assembleFdroidDebug` and `:app:assembleFdroidDebugAndroidTest` with
  `-PsymbianRelease=true -PsymbianSourceDir=build/symbian-source-check`
  and `-Pandroid.injected.build.abi=arm64-v8a`. Injected-ABI artifacts are under
  `app/build/symbian-release/intermediates/apk`, not stale `outputs/apk` files.
- `:app:testFdroidDebugUnitTest --rerun`: 372 passed, zero failed/skipped.
- Android suites: LibraryTabsTest, LibraryChromeTest, LibrarySessionTest,
  LibraryFocusTest, LibraryLayoutTest and GameMenuLayoutTest. Retroid:
  20 passed, one optional capture skipped (23.781 s). Emulator: 19 passed,
  optional capture and physical-controller-only focus skipped (47.859 s).
- Catch and fix cold-return scrolling to an obsolete selected row. Add regression
  coverage for preference migration, dual-generation filtering, per-tab state,
  gestures/held triggers and stale-card/controller launch suppression.
- Initial emulator attempts failed before app attachment. After reboot, a System
  UI ANR dialog intercepted accessibility and focus. Dismiss that system dialog
  and rerun the full suite successfully; do not treat those attempts as app passes.
- Retroid Gallery/Grid/List/actions captures are under `.local/ux40/`; phone
  portrait capture is `emulator-portrait.png`. These are local development
  captures, not signed-release screenshots or redistribution of game fixtures.
- `emulator-small.png`: light-theme 480x320 dp viewport. Header/tabs/view controls
  fit without overlap, but the three-row chrome still takes substantial height;
  further short-screen compaction remains part of the epic. Restore emulator
  size/density overrides after inspection.
- Physical Retroid checks are automated UI/input checks. Manual physical-button
  comfort, full accessibility/theme matrix, release upgrade/save validation,
  full-game compatibility and remaining #40 stages are not established here.

No public APK, release asset, Nextcloud upload, engine update or enabled Release
multiplayer accompanies this slice. Existing public release assets were not
reverified during this UI task.
