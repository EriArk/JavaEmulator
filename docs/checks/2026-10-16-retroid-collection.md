# Retroid collection provisioning - 2026-10-16

## Scope

Physical Retroid Pocket Classic, existing Handheld 0.3.0-beta.3 installation.
Only `io.github.eriark.abyssme` is installed among AbyssME package variants.
No APK rebuild or app implementation change. Supplied games remain private;
they are not part of Git or release downloads.

## Result

- Installed 30 additional Java games through the public import/launch entry point.
- The reopened library shows 39 entries: 35 local Java/Mophun records and four
  native Symbian/N-Gage entries.
- All 30 new catalog source hashes match the selected user-supplied JARs.
- The five original local records retain their IDs, titles, paths and source
  hashes. This does not constitute a save-roundtrip test.
- Gravity Defied (the archive's modified `Gravity Defied 3.jar`) reached its
  motorcycle track and is marked as a favorite.
- Doodle Jump was attempted but crashed in its game thread with a null Graphics
  reference during `setClip`. Only this newly added game and its staged device
  JAR were removed; the original supplied archive remains intact. No root cause
  or compatibility fix is claimed.

The other selected games were imported and their game hosts opened. Initial
captures are not proof that every title is playable through completion.

## Added Titles

Gravity Defied, Bounce Tales, Ancient Empires II, Tower Bloxx, Doom RPG, Worms,
Rayman 3, Sonic the Hedgehog Part One, Prince of Persia: The Two Thrones,
Splinter Cell 2, Bobby Carrot 3, Call of Duty 2, Townsmen 6, Block Breaker Deluxe,
Bubble Bash 2, Zuma, Need for Speed Most Wanted, Crash Nitro Kart,
Soul of Darkness, N.O.V.A., Metal Slug Mobile Impact, SimCity Metropolis,
Assassin's Creed Brotherhood, Real Football 2005, Snake III, Darkest Fear,
Tetris, The Sims 3 World Adventures, Spider-Man Toxic City, Colin McRae Rally 05.

Titles describe the supplied archive variants; some have modified branding or
non-English game text. The emulator UI was not translated.

## Local Evidence

Private ignored artifacts, not public downloads:

- `build/retroid-popular-installed/verified-final-new.db`: checkpointed catalog,
  35 local entries, 30 additions, Gravity favorite, no Doodle Jump.
- `build/retroid-popular-before/abyssme.db`: comparison snapshot; already includes
  newly imported Bounce Tales, so the original local set is IDs 1, 2, 4, 5, 6.
- `build/popular-verified.png`: reopened 39-entry library.
- `build/gravity-game.png`: Gravity Defied track.
- `build/retroid-popular-installed/`: per-import catalog/capture evidence.

These are maintainer observations on one device, not a compatibility matrix.
