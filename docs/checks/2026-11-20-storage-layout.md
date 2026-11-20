# Storage and menu checks on Retroid Classic

Local candidate 9e5f9793, version 0.8.0-beta.1, code 325. The ARM64 and ARMv7 APKs built and passed production package guards, signing checks and 445 JVM tests each. Release lint reported warnings and no errors. The official Retroid installation remained on code 324. Device scenarios ran in an isolated debug package.

- The 12 focused Android tests passed on each ABI. Coverage includes moving to a real SD card and back, simulated card absence, verification failure, recovery, competing move requests, old launch paths, menu orientation, portrait margin, reset and dialog bounds.
- A separate ARM64 native test moved a private N-Gage phone with Snakes and a SQLite favorite to SD, checked a synthetic progress file, restored a backup as a second phone on SD, then moved both phones back. Both native catalogs listed Snakes.
- Selecting the SD card through Settings completed the move. A fresh Sudoku J2ME import reached the board and had its converted game on SD. Lemtris reached its main menu after an app restart. With landscape menus and a portrait game override, Sudoku changed the screen from 1240×1080 to 1080×1240. Returning to the library restored 1240×1080.
- Physical GameSir Pocket Taco fit, live card extraction, complete playthroughs and in-place upgrades of the signed candidate were not tested.

## Outstanding Java ME file-connection path

MicroLoader.setProperties derives fileconn.dir.private and fileconn.dir.cache by removing the primary volume path length from the selected data path. On the Retroid, applying this calculation to the measured SD path produces a malformed guest URI beginning with `file:///c:Android/` and embeds the migration UUID. The old guest URI began with `file:///c:/Android/`. The virtual C drive is copied but a game using these properties can look in a different directory. Preserve the original guest-visible directory across moves and verify it using a FileConnection read/write round trip before release. No actual lost save was observed.

Raw local evidence is under `build/checks/2026-11-16-storage-layout/` (Android tests, native phone result, orientation transitions and URI comparison) and `build/releases/0.8.0-beta.1/` (target build and signing logs).
