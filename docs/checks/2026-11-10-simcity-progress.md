# SimCity constructed-building restore — 2026-11-10

Local-only continuation of the [save/backup checks](2026-11-10-real-game-backups.md)
on the unchanged combined Debug candidate, SHA-256
`afaab3a0ac6ada2877279cc2440a80f188a5c1073e6a83afda1ea8ef94eee53b`.
Dedicated Android 11/API 30 emulator, x86_64 with ARM64 translation, manual
320×240 profile. No handheld, listening test, public-source push or APK publication.
Public release assets and live services were not rechecked.

## Actual progress and result

Exact private edition: SimCity Metropolis 1.23.87, JAR SHA-256
`a4ac62d7d241cf06c1b39f92dafc5ea937ddd93dd458dcd47127dc3d089761cd`.
The UUID conversion/profile/save fixture starts fresh; no owner's game is replaced.

- In Sunny Glen, buy a **Townhouse for 1,680**, move its footprint to a valid
  green plot and confirm placement. Budget changes from 30,000 to **28,320**.
- The game acknowledges the completed habitation. A screenshot shows the new
  house on the block north of the central construction site (`manual-09/10`).
- Pause → Menu → Yes explicitly saves the game (`manual-11/12`). Stop the game
  process, export/import through the production backup helper, compare both RMS
  files, reject duplicate restore, regenerate DEX and launch a fresh game process.
- Reopen the saved district. Budget is **28,320** and the same house remains on
  the same block (`manual-14/15`). The tutorial prompt repeats, so tutorial flags
  are not accepted as advanced. Continued input/simulation later reaches April,
  population 186 and budget 28,326 (`manual-16`); this is not a completed district.

**One instrumentation scenario passes in 324.174s**, including bounded manual
input windows. The visible constructed object and budget were reviewed manually;
JUnit checks process survival, archive/hash/profile/catalog behavior, not semantic
recognition of a building from pixels. This establishes a concrete early-game
progress restore beyond merely reopening an empty district.

## Evidence and harness

`ProvidedJavaSweepTest` now supports opt-in `gate:name:seconds` actions (1–900s).
A gate waits for a simple named marker in the current capture directory, then
removes it; timeout fails and the normal UUID-fixture cleanup runs. This permits
bounded manual gameplay before backup and after relaunch without changing runtime.
One early manual key overlapped the scripted introductory inputs; exact input
order and screenshots are retained, rather than claimed as a deterministic replay.

Ignored evidence: `build/checks/2026-11-10-game-progress/`, including
`input.json`, `manual-input.jsonl`, captures, instrumentation/logcat and runner.
The two RMS hashes are recorded in `captures/backup.json`. Archive SHA-256:
`a85f80029f0ead934622ee3e51fdc4f1f6529a9be668a2c0eb1a002c1eacdb21`.
Test APK SHA-256:
`2e6b70c2e836fe91688693964d3cfd314fb1164b5db0ef36d1994bba0dbc96ae`.

No production repair or broad JVM/lint rerun. This result does not expand Diamond
Rush or Deep Abyss progress acceptance, establish full-game compatibility or
replace physical-device, audio/performance and signed-release acceptance.
