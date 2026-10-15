# Beacon launch and return checks

Date: **2026-10-15**. Issue: [#7](https://github.com/EriArk/JavaEmulator/issues/7).
Source follows `9b377ce1`; no release or test-APK distribution in this batch.
GitHub Releases was checked today: `v0.3.0-beta.1`, published 2026-10-14,
remains the newest prerelease. Its asset hashes were not rechecked.

## Fix

Cancelling an externally opened multi-JAR archive in Beacon reproducibly left
the AbyssME library in front. Successful game launch already finished the
trampoline activity; cancel/error handlers did not honor `finishHost`.

- Java Close/controller Back now cleans staging and finishes only an external
  host. Archive and existing-copy choices share that path. Manual Start also
  uses the same launch/finish behavior as automatic Start.
- Mophun error Close/Back/system cancel honors the same explicit host flag.
- Native launch-file errors, header navigation and system cancel finish an
  external host, cancelling pending resolution. Errors remain visible until
  the user closes them.
- Symbian inspection cancellation finishes an external host and marks the
  inspection as delivered so a late result cannot open another installer.
  Normal inspection handoff is not treated as cancellation.
- Internal imports retain their library. Busy Java/Mophun operations retain
  their existing cancellation restrictions. Cancelling the JAR picker is still
  distinct from closing its parent installer.

## Physical frontend runs

Retroid Pocket Classic, Android 14, 1240 x 1080 landscape. Beacon **1.8.10**
(`com.radikal.gamelauncher`, version code 53). A separate **AbyssME-Test**
Custom platform was added; existing platforms, default home and production
AbyssME installations were not replaced. Inputs were ADB-injected buttons/taps
into the real frontend, not direct shell VIEW launches masquerading as Beacon.

Packages tested:

- `io.github.eriark.abyssme.symbian.debug` (Handheld).
- `io.github.eriark.abyssme.symbian.phone.debug` (Phone).

The [setup guide](../LAUNCHER_INTEGRATION.md#beacon) records Standard file
handling, `{file_path}`, the full activity and read grant. Android logs show
VIEW launches from Beacon UID 10110 with an external-storage-document content
URI and flags `0x14008001`. Beacon adds task flags, including CLEAR_TASK:
already-running-app checks here are **not** proof of delivery through
`MainActivity.onNewIntent`. The earlier Daijisho checkpoint separately tested
warm activity delivery. This distinction matters when reproducing another
frontend's behavior.

| Scenario | Observed result |
| --- | --- |
| Handheld prepared Asphalt 3 3D JAR, cold and already-running app | Language selection; no reinstall; Exit returned to Beacon |
| Handheld multi-JAR ZIP | Both entries visible; Sudoku selection reached its board; Exit returned to Beacon |
| Handheld archive cancel | Before: library left open. After: controller Back returned to Beacon |
| Handheld Deep Abyss MPN | In-game menu rendered; Exit returned to Beacon |
| Handheld Snakes `.abyssme` | Installed game opened directly to its title sequence; Exit returned to Beacon |
| Phone wrong-variant Snakes link | Persistent explanatory error, no wrong game launch; Back returned to Beacon |
| Phone multi-JAR ZIP, Sudoku not previously installed | Selection installed Sudoku and reached its board with the touch deck; Exit returned to Beacon |
| Phone fresh direct JAR, cold app | A distinct MIT Sudoku fixture imported and opened its board; Exit returned to Beacon |
| Phone unknown URI for existing Asphalt | Both existing copies offered; cancel returned to Beacon; selecting the first copy launched it |
| Phone prepared Asphalt repeat | Reused the remembered copy without another prompt; Exit returned to Beacon |
| Phone Deep Abyss MPN | In-game menu rendered with touch controls; Exit returned to Beacon |
| Phone exported Snakes link | Correct Phone installation opened directly; Exit returned to Beacon |

Fixtures were in a dedicated Download subfolder. The fresh direct-JAR fixture
is the repository's MIT Sudoku with only its MIDlet-Name changed to
`Beacon test Sudoku`; it is not a different compatibility sample. No private
games, firmware, device libraries or launch-file identities are committed.

Six Phone RMS files were byte-identical before and after the Asphalt
cancel/reuse/relaunch sequence. The three Handheld Asphalt RMS files also
retained their hashes. Handheld Sudoku rewrote its two RMS files during its
game session, so whole-library byte identity is **not** claimed. No save files
were deliberately edited/deleted, no existing copies were merged, and the
regression suite separately verifies save/settings markers during reuse.
This batch does not establish full-game, audio or native save acceptance.

## Regression checks

Built both combined debug variants and test APKs with:

```text
gradlew -PsymbianExperiment=true -PsymbianSourceDir=build/symbian-source-check :app:assembleFdroidDebug :app:assemblePhoneDebug :app:assembleFdroidDebugAndroidTest :app:assemblePhoneDebugAndroidTest
```

Ordinary Phone debug and its test APK were also built for Android 15 x86_64.
Installed with `adb install -r -d`; no data clearing or uninstall.

Suite: `ExternalInstallerExitTest` (8 new cases, each checking internal and
external hosts), `InstallerDialogTest`, `InstallerJadTest`,
`InstallerSourceAliasTest`, `InstallerSourceTest`.

- Retroid Handheld: **27 passed**, no skips.
- Retroid Phone: **27 passed**, no skips.
- Android 15 portrait Phone emulator: **26 passed, 1 skipped**. Symbian
  inspection is intentionally absent from this ordinary Java/Mophun build.
- Error persistence, controller/touch Close, system cancel, busy Back, JAD
  mismatch/picker retry, archive selection, source reuse and save markers are
  covered. These automated runs are separate from the frontend scenarios.

Local-only logs: `build/beacon-{handheld,phone}-tests.txt`,
`build/beacon-avd-tests.txt`, `build/beacon-launches-retroid.log`, build logs and
before/after save-hash lists. APK SHA-256, not new release version identifiers:

```text
9f85642fba86c11e35fca567cff480248135e452b3c21c443f26105cb56b1146  combined Handheld debug
373e5bc08bd95ba9bdd466ceb2c3fcfebcd18c1737cd26d9fbcf98ddd6adf824  combined Phone debug
585fe92d5cebf3c2d685ef7feda026804dbe73e601b2ad31dde830f29dcbf006  ordinary Phone debug
```

## Screenshots

Actual device captures, not mockups. Missing images on the separate Beacon
test platform are unconfigured frontend artwork, not AbyssME cover extraction.

- [Beacon custom-launch settings](2026-10-15/beacon-command.png).
- [Archive choice](2026-10-15/beacon-handheld-archive.png).
- [Correct return to Beacon](2026-10-15/beacon-cancel-fixed.png).
- [Existing-copy choice](2026-10-15/beacon-phone-reuse.png).
- [First Phone Java launch](2026-10-15/beacon-phone-new-direct.png).
- [Phone Mophun launch](2026-10-15/beacon-phone-mophun.png).
- [Handheld Snakes title](2026-10-15/beacon-snakes.png).
- [Phone Snakes rendered scene](2026-10-15/beacon-phone-snakes.png)
  (title/attract sequence; not a claim of a completed player-controlled level).
- [Wrong-variant error](2026-10-15/beacon-wrong-variant.png).

## Still open

ES-DE is unavailable here. Its actual discovery, custom-system merge, grants
and signed-APK launch remain unverified; #7 stays open. Neither Beacon nor
Pegasus/Daijisho can certify it. These debug checks do not claim that the new
cancel behavior or native launch files exist in published 0.3.0-beta.1 APKs.
