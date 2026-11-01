# Native LCD Preview Responsiveness (#48)

2026-11-01. Source/test work for Symbian and N-Gage. Not a replacement of the
public 0.7.0 Beta 1 Revision 1 APK. Games, ROMs and personal saves are not published.

## Shared Failure Path

`SymbianSessionUi.previewPicture()` synchronously called `displayGeometry()`
from the Android UI thread while the game-menu host had already paused native
execution. Both that JNI query and `touchBounds()` waited for the kernel and
screen mutexes. Guest/graphics work can hold those locks; waiting from the UI
prevents the same UI from closing the menu and resuming the engine. The old
frame callback repeated the blocking query while an LCD effect was enabled.

This is a concrete blocking path, not proof that it explains every reported
crash. The community report does not identify the exact game edition or Android
device. No title-based exception or filter removal is introduced.

## Repair

- Resolve and read the screen under nonblocking kernel/screen locks, preserving
  lifetime protection and lock order. Busy or invalid geometry returns no sample.
- Cache validated, owned geometry from presented frames even with LCD off, at
  most five times per second. Retain the last good sample during contention;
  invalidate it when applying display settings and stop querying after close.
- The live picture preset callback and screenshot effect dimensions consume
  the snapshot instead of querying the paused guest. A busy touch-bound query
  can use the same last valid viewport.
- Reject non-finite LCD bounds and bound line-vertex storage to 512 KiB before
  allocation. Oversized grids retain the color wash without unbounded work.
  Normal phone-resolution grids, nearest-pixel boundaries and rotation remain.

## Verification

Combined Debug and AndroidTest assembly passes (3m12s; test-only rebuild 13s).
The new Debug APK updates the separate `io.github.eriark.abyssme.debug` package
in place on Retroid Pocket Classic / Android 14. The official package is not
changed. Local evidence is kept under `build/issue48/`.

- **21 focused Android tests pass**, 10.752 seconds: LCD grids/rotation/fractional
  scaling, malformed and huge geometry, picture previews, cached native geometry,
  disabled-effect sampling, busy/invalid samples, close/resize and touch ownership.
  One existing touch fixture initially expected the now-transient shortcut row
  to remain visible. It now opens the handle and explicitly enables touch for
  its virtual-key checks; the app behavior was not changed to satisfy that test.
- **3 opt-in Snakes UI tests pass**, 125.981 seconds: twelve live preset changes
  over three menu/resume rounds, Keep/Undo and Home/return; the advanced editor's
  effect/rotation/undo/relaunch route; and clean/LCD screenshot exports.
  These scripted captures include startup transitions, not a completed game.
- A separate manual Retroid smoke check reaches the Snakes playfield. LCD
  Classic, LCD Soft and Original are selected through the live menu; the scene
  continues changing after resume and the exit confirmation remains usable.
  Captures: `build/issue48/gameplay-classic.png`, `gameplay-menu.png`,
  `gameplay-soft.png`, `gameplay-original.png`. No FPS/audio/full-game claim.
- Native ARM64 checks on Retroid pass, including a new geometry executable with
  deliberately held kernel and screen locks, immediate return, intact previous
  data, successful retry, quarter-turn geometry, missing screen and invalid/
  overflowing scale. Existing capture, input routing, card identity, transfer,
  frame-rate, emulation-speed and audio-speed executables also report passes.
- The new live-preset UI test also passes on the **old** installed Debug APK
  (20.306 seconds). The exact community freeze has **not** been reproduced on
  this original N-Gage fixture. The repair removes a real blocking failure path;
  it is not evidence that all N95/N-Gage 2.0 failures are resolved.

The earlier opt-in `nativeEffectsApplyUndoAndPersist` scenario passed on the
unmodified installed Debug build (54.299 seconds), but deliberately uses the
advanced editor, not the live preset tiles. It therefore does not clear #48.
The new UI scenario specifically exercises those tiles while the native game
is paused, Keep/Undo, returning from Home and repeated menu/resume cycles.

## Remaining Scope

Keep #48 open pending N95/N-Gage 2.0 verification of the reported failure class.
No fresh exact-reporter-title, N95 gameplay, complete-game, physical-button or
audio acceptance is claimed. Test input injection on real Retroid hardware is
not physical-button testing. No new release asset, source tag or version code
was published for this batch. Public release state was rechecked on 2026-11-01:
the existing page still contains 0.7.0 Beta 1 Revision 1 (code 314).
Retroid was returned to the unchanged official app's library after testing.
