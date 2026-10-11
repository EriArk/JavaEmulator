# Game settings in development builds

These changes are on `main`, not in the published beta.2 release.

## Everyday settings

Open a game's Settings in the library, or Display / Controls in the game menu.
Both places use the same editor and keep edits in a draft:

- Display: Original / Fit / Stretch, smoothing, image rotation and Phone orientation.
- Controls: graphical controller mapper; Phone additionally has Phone / Gamepad /
  Legacy layouts, size, opacity and portrait/landscape positioning.
- Advanced: virtual phone resolution. The library also links to the full expert
  compatibility editor. Handheld remains landscape-only without a touch keypad.

Try in game launches the draft. Try changes applies it to the running game.
Keep commits it; Undo restores the confirmed configuration. A roughly 20-second
preview timer also restores it automatically. Opening the game menu, mapper or
backgrounding the app suspends that timer. Keep / Undo are also in the game menu
for controller navigation. Closing the editor without trying discards its draft.

The graphical mapper edits this same draft: Use mapping returns to Controls; it
does not bypass Try / Keep. The hold-Select shortcut previews its changes directly.

## Safety boundary

- Canonical `config.json` is written atomically only on Keep. Runtime autosave
  requests during a preview do not commit it.
- A library trial is a one-shot `settings-trial.json`, consumed before engine
  initialization. A failed launch or process death therefore leaves the confirmed
  configuration for the next launch, without replaying the trial.
- This is configuration rollback, not a game-state checkpoint. Game saves are
  not restored or rolled back; normal gameplay can still change them.
- Reverting a trial of renderer/compatibility properties ends that game session;
  the next launch uses the confirmed settings. Live picture/control changes do
  not need that restart. Save progress before testing compatibility changes.
- An unopened Java game still runs automatic preparation before showing settings.
- The legacy layout editor and ordinary edits in the full expert editor retain
  their existing direct-save behavior. Only its Fix game action uses the trial.
- A draft survives recreation of the library settings activity. Unfinished edits
  inside an open mapper are discarded on recreation; confirmed settings remain.

## Acceptance checklist

1. Change Display from the library, try it, then Keep. Relaunch and check it persists.
2. Change Display in-game and Undo; repeat and let the timer expire.
3. Start a library trial, terminate the test process before Keep, then relaunch.
   Check that the last confirmed configuration is used.
4. On Phone, preview Gamepad / Phone layout changes and restore them. Rotate the
   device while editing; verify touch targets and game placement.
5. Open the mapper while a preview is active; the timer must not expire over it.
6. On Handheld, navigate Display / Controls and Keep / Undo with the controller.
7. Compare saved game data before/after a settings-only trial. Do not clear app data.

Use the local Android regression suite in [Local testing](LOCAL_TESTING.md).
Physical devices, all games and external launchers require separate acceptance.
