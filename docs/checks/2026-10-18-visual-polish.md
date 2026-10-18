# Visual polish checkpoint - 2026-10-18

## Availability

Local implementation after `v0.4.0-beta.1` (code 306). No commit/push, new
version, release APK or deployment is implied by this record. Isolated debug
packages were used; the signed production installation was not replaced.
Games, firmware and signing material remain outside Git.

## Design and scope

- Classic keeps the established neutral/amber appearance. Retro LCD uses
  graphite and LCD greens, cyan focus, inset card borders and a restrained
  monospace brand label. High contrast uses black/white surfaces, lime/cyan
  accents, flat keys and stronger outlines. All have light and dark variants.
- Brightness and visual style are independent. Per-game dark/light touch
  controls remain independent of app brightness. No filters affect game pixels.
- Shared theme attributes replace fixed UI color roles. Native activities and
  dialogs use the same palette; an internal provider supplies current appearance
  to engine processes. No IPC is added to the per-frame touch drawing path.
- Touch layout editing uses a detached profile. Phone portrait has one keypad
  block; Gamepad and landscape layouts have direction/action blocks. Moves stay
  inside their existing control lanes. Scaling supports 70-120%, clamped to the
  lane; smaller controls do not automatically reduce the reserved lane size.
- Use layout updates the settings draft, not the saved profile. Existing Try
  changes / Keep / Undo controls persistence. Cancel is non-destructive.
  Optional serialized placements survive normal profile copies/backups and are
  keyed by orientation, layout and block. No placement means old layout behavior.
- Editor tabs, fullscreen layout, controller activation and directional nudge
  buttons complement dragging. Activity recreation preserves the editor draft.
- Gallery density is capped only where two complete rows fit on near-square
  screens; explicit art formats remain respected. Narrow headers retain filters
  through the Collection menu. Controller hints are not shown on touch-only UI.

## Verification

- Base and combined `assembleFdroidDebug` / `assembleFdroidDebugAndroidTest`.
  Combined build uses `-PsymbianExperiment=true
  -PsymbianSourceDir=build/symbian-source-check`.
- 39 JVM tests passed: frame pacing, library layout, catalog information/source
  identity, controller input, emulation clock and game archive handling.
- `VisualPolishTest` checks six theme/brightness combinations, text contrast
  (at least 4.5:1 for checked roles), drawable states, placement bounds and no
  game/key overlap, detached copying, cancellation, and appearance snapshots.
- Additional device suites cover existing touch input/feedback, device controls,
  game menu geometry, settings previews, library chrome and controller focus.
- Emulator square stress configuration: 720x720, density 240, font scale 1.5.
  The 19-test run completed without failures. The rotation assertion explicitly
  skips a physically square display, which has no distinct portrait/landscape
  dimensions. Earlier failure was this test assumption, not accepted app behavior.
- Screenshot review found truncated spinner labels and crowded large-font
  editor tabs. Custom wrapping spinner rows and a font-aware split threshold
  correct those shared layout problems.

Final installed-build runs:

| Environment | Check | Result / local log |
| --- | --- | --- |
| Retroid Pocket Classic, Android 14, combined debug | Library focus, visual polish, device controls, game menu, touch feedback/deck, settings preview | 40 passed; `build/ui-polish-retroid-final.log` |
| Retroid, final palette selector revision | Visual polish and light-theme captures | 6 passed; `build/ui-polish-retroid-light.log` |
| Retroid, Mophun fixture | Edit to 80%, apply/keep, exit/relaunch, verify persisted value, change to landscape Gamepad | 1 passed; `build/ui-polish-mophun-final.log` |
| Retroid, native Snakes | Existing shared display/mapper/phone-controls workflow | 1 passed; `build/ui-polish-symbian-menu.log` |
| Android 15 emulator, base debug | Sudoku J2ME, complete editor/apply/keep/relaunch/landscape workflow | 1 passed; `build/ui-polish-java-flow.log` |
| Android 15 emulator, square and 150% text | Final font-aware editor and palette captures | 6 passed; `build/ui-polish-square-captures-final.log` |

Combined lint: **0 errors, 414 warnings**. Both final debug builds succeeded.
The signed production package was not replaced. Emulator display overrides were
reset to physical 1080x2340 / density 440 / font scale 1.0 after the checks.

Earlier workflow failures were resolved in the test setup/helpers: a raw Download
URI did not have read permission, so the Java fixture was provided under the debug
app's external files directory and linked to an existing installed copy. The
slider action now accepts an already-selected 80% value, and the helper scrolls
to Exit game on short landscape menus. The complete Java and Mophun workflows
were rerun successfully; partial runs are not counted as passes.

Native Snakes exercised the existing common menu, not the new editor's full
save/relaunch workflow. The editor is shared and compiled into Symbian, but
that specific native end-to-end acceptance remains unverified.

[Curated unedited screenshots](../../screenshots/development/2026-10-18-visual-polish/README.md).
Raw logs and full image sets remain local under `build/ui-polish-*` and
`build/visual-polish-*`; they are not public artifacts.

Local APK SHA-256 (debug code 306, not a new public version):

```text
combined 942B3A7857415E5FBFD41688033F841C5619A0DFD5CAB6E761D289F0BE17D045
base     DA7A52D0AE8BCDBA53FB4EE7AE7DE61058593662011A0E47A81E9AB057C1CE61
```

## Limits

Automated input on real hardware is not manual physical-controller acceptance.
No physical phone or RG Cube was used. These checks do not establish full-game,
audio or save-game acceptance. Game fixtures are used for settings/UI workflows,
not title-specific emulator fixes. Legacy Java keypad editing remains separate.
No new FPS, renderer or compatibility behavior is part of this UI batch.
