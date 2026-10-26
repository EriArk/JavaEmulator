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

## Soft Console Materials (2026-10-26, Unreleased)

The owner approved softer, rounder molded materials while retaining #40's
structure. TrainerOS dark chassis/keycap styling was inspected read-only as a
material reference; its navigation and QML were not transplanted.

- `ConsoleSurface` draws a matte chassis, recessed library well, colored platform
  keys, quiet cards and temporary panels. Thin highlights and contact edges give
  depth without a thick decorative bezel, blur, bitmap layers or animation loop.
- Existing Classic / Retro LCD / High contrast and light/dark settings remain.
  High contrast deliberately uses flat black/white surfaces with distinct edges.
  Focus, selection and pressed states do not resize the card or key. Disable
  Android's extra rectangular focus fill where custom focus already renders.
- Gallery/List/Grid and source-art fitting are unchanged. Remove the extra frame
  around artwork instead of tinting, cropping or regenerating the image.
- At width >=440dp, height <400dp and font scale <=1.2, reparent view/filter tools
  into the header. Hide the brand and Add shortcut there; import remains in Menu.
  Taller/larger-text layouts restore the normal rows. Hit targets remain 48dp.
- Only library/context panels opt into these materials. In-game menus, pause,
  renderers, touch mapping and multiplayer authority are untouched in this slice.

Verification of this follow-up:

- Combined arm64 Debug build, development identifier `0.5.0-beta.1` / 310.
  APK SHA-256 `274325711cf10a1377ef6869ec0b8af2418ee2e3eb0ae78c7ea270e8c39d39ff`.
  Installed hashes match on both targets. Same build flags and intermediate APK
  locations as below; no signed public APK.
- 372 JVM tests pass, zero failures/errors/skips. Add `ConsoleSurfaceTest` to the
  six existing UI suites: material rendering across three palettes and two
  brightness modes, stable bounds, visible focus/press/selection and repeated
  compact/tall reparenting. Test preferences are restored, including palette.
- Final Android suites: Retroid 22 passed / 1 optional capture skipped (24.623 s);
  emulator 21 passed / 2 skipped (capture and controller-only focus, 53.126 s).
- The rotation suite initially exposed a test timing race: Activity resources
  changed before the dialog's configuration traversal. Wait up to five seconds
  for the exact expected window gravity; retain the assertions and timeout.
- Local screenshots: `.local/ux40/soft-retroid-gallery.png`,
  `soft-retroid-grid.png`, `soft-retroid-menu.png`, `soft-phone-light.png` and
  `soft-small-light.png`. These are actual Debug UI captures, not concept art or
  release evidence. Small viewport is 480x320dp; reset emulator overrides afterward.
- Device automation is not manual physical-button comfort, full accessibility,
  game compatibility or final owner visual acceptance. The short-screen
  three-row limitation in the original slice below is addressed by this follow-up.

## Original Library Slice Verification (2026-10-26)

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
