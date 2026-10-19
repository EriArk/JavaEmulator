# Library Navigation and Card Editing - 2026-10-19

## Availability

Local source and debug builds after 0.4.0 Beta 2, not a new public release.
No version bump, Git push, website/Nextcloud deployment or release upload in this
checkpoint. Public release state was not re-queried. Production installs were
not replaced; tests use the separate base and combined debug packages.

## User-facing Changes

- Collection combines All games / Recent / Favorites with an independent
  All platforms / Java ME / Mophun / Symbian / N-Gage filter. N-Gage includes
  the original and 2.0 catalogs. Search applies within the chosen collection.
- The collection and platform survive a cold open. Each combination remembers
  its first visible game and scroll offset by stable path, not a fragile row
  index. View changes, recreation and library refreshes preserve the anchor;
  a missing anchor falls back to the beginning. Search does not overwrite it.
- Library menu offers **Play last: [title]** across platforms and independently
  of the active filter. This is a normal launch, not resume/save-state support.
- Java/Mophun options now expose Add/Remove favorite directly on touch devices,
  using the same styled menu as handhelds. Existing recent/favorite data is reused.
- **Edit card > Title / Artwork** groups existing artwork actions with title
  editing across platforms. The title dialog follows the active palette and
  rejects empty names without dismissing the editor.
- Symbian/N-Gage title overrides are keyed by virtual phone and game UID;
  rescanning does not overwrite them. No guest filenames, saves or launch IDs
  change. As with native favorite/history presentation preferences, these title
  overrides are local to the installation and are not included in whole-phone
  backups in this checkpoint. Existing custom-art backup support is unchanged.

## Systemic Corrections

Filter results carry a generation covering the source list, search, collection
and platform. An older worker result cannot replace a newer selection. Search
uses Locale.ROOT rather than the phone's casing rules. Late native catalog
arrival does not discard the remembered native selection. Scroll offsets include
RecyclerView item margins, avoiding a small repeated-open position shift.

## Verification

- Base and combined debug application/test APKs built; both lint tasks passed.
  A fresh base JVM run passed all 39 tests. Logs below distinguish build passes
  from installed UI evidence.
- Android 15 x86_64 phone emulator: collection intersections, recent order,
  locale-independent search, rapid request replacement, late native selection,
  cold-open filter/scroll persistence, title cancel, favorite removal, recreation
  and library row layout passed. Play last's outgoing launch intent was checked
  against the expected fixture game; this is not a gameplay test.
  Final library suite: eight passes and one opt-in capture skip (runner total nine).
- Retroid Pocket Classic / Android 14: controller-focus regression, shared
  filtering/layout checks and six native artwork/title tests passed. Real Snakes
  artwork selection/reset through Edit card, including picker result delivery
  after activity recreation, passed and restored the prior custom artwork.
  Combined suite: fourteen passes and two inapplicable/opt-in skips (runner total
  sixteen). Capture was also run separately and passed on both devices.
- Real installed-library capture flows passed on both devices. Gallery, Grid,
  List, collection/platform menus and card/title editors were captured. They do
  not prove complete game compatibility or physical-controller comfort.

Intermediate failures: the first synthetic catalog used ready entries without
installed payloads, which the existing reconciliation correctly removed. The
fixture now uses indexed entries. The next run exposed the real missing-margin
scroll offset above; it was fixed and the cold-open test passed. Neither failed
run is counted as acceptance.

Local evidence (ignored build directory):

- `build/library-session-base-checks.log` (fresh JVM run and initial lint)
- `build/library-session-base-final-checks.log`
- `build/library-session-combined-final.log`
- `build/library-session-combined-final-checks.log`
- `build/library-session-emulator-final-tests.log`
- `build/library-session-retroid-final-tests.log`
- `build/library-session-emulator-capture.log`
- `build/library-session-retroid-capture.log`

[Selected unretouched captures](../../screenshots/development/2026-10-19-library-session/README.md).
Games and firmware remain local; no payloads were added to the repository.
