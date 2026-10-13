# Screen and phone presets, 2026-10-13

Implemented in public development source, not the downloadable beta.2.
No APK was distributed for this batch. Public release state was not rechecked;
the existing release identifiers in PROJECT_STATUS are carried forward.

## User workflow

- During a Java game, open More > Screen presets. Choose Auto or a common
  virtual resolution and Try changes. Keep saves it; Undo or the preview
  timeout restores the confirmed resolution. Auto falls back to 240 x 320
  without a reliable detected size. It is not a promise that every game resizes.
- More > Phone profile offers Current settings, Auto, Generic, Nokia,
  Sony Ericsson, Motorola, Siemens and Samsung. Restart & try warns about
  unsaved progress. The next game process consumes the trial once, before
  initialization. Undo/timeout restarts with the confirmed configuration.
- Opening a preset page does not overwrite custom settings. Vendor switching
  replaces managed vendor properties while preserving MIDP/CLDC versions,
  unrelated user properties, resolution and controls. Save files are not moved
  or rolled back. These presets do not emulate an entire phone model.

## Automatic preparation

Explicit target declarations remain strong evidence. A vendor API mention or
publisher name alone is insufficient; weak or conflicting hints retain Generic.
Screen-named assets and explicit display metadata can suggest a resolution;
anonymous image dimensions alone cannot. Existing/imported configuration stays
intact. This is static analysis, not background gameplay or guaranteed selection
of the best phone. Old weak vendor suggestions are not reused by Auto.

## Checks and limits

Physical Retroid Pocket Classic (Android 14): 27 compatibility, 13 settings-trial
and 3 preset UI checks pass. Phone emulator (Android 15): the same 43 checks plus
one focused GPU regression pass. These instrumented checks ran in isolated
research packages, not over user installations. Both ordinary main debug
variants build; 14 JVM cases pass per variant.

Manual ADB-driven Retroid checks cover opening More, a live 128 x 128 trial and
Undo, a Sony Ericsson profile restart and Undo. Phone portrait checks cover the
profile page, restart and automatic timeout rollback. They are not physical
controller, audio, full-game or every-phone acceptance.

[Research evidence, logs and screenshots](https://github.com/EriArk/JavaEmulator/blob/codex/m3g-replacement/experiments/m3g-host/presets-20261013.md)
also covers the separately developed GPU optimization. That renderer remains
off main; no engine/license switch or public APK release is claimed here.
