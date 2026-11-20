# Platform lifecycle audit on Retroid Pocket Classic, 2026-11-20

## Installed library and update

The local ARM64 0.8.0-beta.1 combined build uses `io.github.eriark.abyssme.dingoocheck.debug`. After the old APKs were removed with Android's keep-data option, the combined build had zero Java ME or Mophun catalog rows. Android had retained the old applications' separate data directories. Updating the same application ID keeps the library accessible.

Consolidated the primary catalog's 42 games and additional distinct games from the previous installations into 54 Java ME and 6 Mophun entries. The recovery used installed content, available per-game settings and saves. Three Mophun titles were reimported from matching original bytes. Restored Inotia and Metal Slug with their WIPI package data, and the N95 and N-Gage QD virtual phones with their installed games. A fresh Frozen Bubble SISX installation selected the N95 and added the game to the Symbian tab. It reached its start menu on the device. The retired data directories remain available separately.

Reinstalled the identical APK with `adb install -r`. Compared 8,893 game, settings, save, phone and firmware files by SHA-256, and compared all 60 database rows and seven source aliases before and after. All matched. The subsequent updated APK also installed over the populated library. All 8,895 files present immediately before this second update, 60 rows and seven aliases matched afterwards. After the update, 1916 Dogfight reached the HeroCraft splash and Snakes reached its N-Gage startup screen. These builds use the same database version.

## Platform coverage

Every platform showed games after a cold start. **Add game** reached Android's document picker from all nine tabs. Opened the Display and Controls pages from game cards. On first use without a saved profile, a Mophun card initially redirected to a separate settings screen. The updated build initializes the profile in the library and opens the requested page directly. Confirmed first-use Display on Boulder Dash and Controls on V-Rally 2, including the physical controller profiles and mapper. The setup and persistence columns below combine this check, the importer implementation and the linked, dated game checks.

| Platform | Installation and system setup | Gameplay and persistence evidence |
| --- | --- | --- |
| Java ME | JAR, JAD with its game archive, or supported game collections. The built-in runtime prepares the game on import. | 54 installed entries. [Real games, RMS progress and library backup](2026-11-10-real-game-backups.md). |
| Mophun | MPN or supported game archive. The runtime selects model defaults. The user can adjust compatibility settings. No separate firmware import. | Six installed games, including Lemtris, Boulder Dash, Gold Rush and V-Rally 2. [Controls, pacing and audio by game](2026-11-16-goldrush-vrally.md) · [Backup restoration](2026-11-10-real-game-backups.md). |
| Symbian | Import phone system files through Settings or the library. Compatible ROM/RPKG and prepared phone archives are recognized. The installer matches a SIS/SISX to an installed phone and can request missing components. | Frozen Bubble installed and opened on N95. [Earlier gameplay and input](2026-11-02-game-sample.md) · [System and game import](../SYMBIAN_INTEGRATION.md). |
| N-Gage | Uses the phone library with generation-specific game and platform packages. Supports restoring a prepared phone, its games and progress. | Snakes and Asphalt3 restored on separate phones. [Snakes phone backup and storage move](2026-11-20-storage-layout.md). N95/Asphalt cold-launch reliability remains open. |
| WIPI / SKVM | Select a carrier ZIP. The importer recognizes KTF, LGT and SKT descriptors. The built-in runtime and MIDI bank handle setup. | Inotia and Metal Slug restored. [Picture, audio, touch, controller mapping and cold relaunch](2026-11-20-wipi-games.md). Guest save restoration and LGT gameplay remain unverified. |
| Dingoo A320 | Import a native MIPS APP. No separate firmware step. | Digger reached the maze and resumed from a cold save state. [Device results](2026-11-20-dingoo-gemei.md). ZIPs and loose companion assets lack a package import path. |
| Gemei A330 | Import a native ARM CC, C2S or C3S. No separate firmware step. | 7 Days Salvation reached gameplay and resumed from a cold state. Two CC games showed a black screen. C2S gameplay remains unverified. [Device results](2026-11-20-dingoo-gemei.md). The same companion-asset import gap applies. |
| Aquaplus P/ECE | Add a base PFI, then a PEX or PFS-based game ZIP. The last imported base is selected automatically. The vendor updater needs conversion outside the app. | P.K.O. produced pictures and audio, accepted input and saved to writable flash across cold starts. The core has no instant save state. [Device results](2026-11-20-piece.md). |
| GamePark GP32 | Add BIOS BIN for retail cards, then SMC or a single-image ZIP. The last imported BIOS is selected automatically. FXE/FPK homebrew may boot directly. | Tomak and GP Fight reached gameplay with audio and restored cold states. GP Fight also wrote its own save files. [Device results](2026-11-20-gp32.md). |

## Remaining work across platforms

- Extend portable library backup and restore to WIPI/SKVM, Dingoo, Gemei, P/ECE and GP32. Include packages, user-supplied system data, the selected base, profiles and writable guest data. The existing library ZIP handles Java ME and Mophun. Symbian and N-Gage have phone backups. Moving an entire library to SD is a separate operation.
- Add ZIP and companion-file import for Dingoo and Gemei, retaining relative paths. Add in-app preparation for the P/ECE system updater.
- The five newer platforms use the selected tab to route imports. General file opening and folder discovery do not automatically route all their formats. Use content detection where possible and offer a platform choice for ambiguous files.
- Keep untested games available. Record the tested game, edition and actual result independently of import eligibility. Recheck game-created saves, audio and manual controller input where the individual reports leave them open.

Private local evidence is under `build/checks/retroid-library/`, including the nine-tab contact sheet, picker checks, settings captures, catalog and file comparisons. Audio and hand-operated controller input were not retested for this audit. No public build was made.
