# Storage and menu checks on Retroid Classic

Local candidate 9e5f9793, version 0.8.0-beta.1, code 325. The ARM64 and ARMv7 APKs built and passed production package guards, signing checks and 445 JVM tests each. Release lint reported warnings and no errors. The official Retroid installation remained on code 324. Device scenarios ran in an isolated debug package.

- The 12 focused Android tests passed on each ABI. Coverage includes moving to a real SD card and back, simulated card absence, verification failure, recovery, competing move requests, old launch paths, menu orientation, portrait margin, reset and dialog bounds.
- A separate ARM64 native test moved a private N-Gage phone with Snakes and a SQLite favorite to SD, checked a synthetic progress file, restored a backup as a second phone on SD, then moved both phones back. Both native catalogs listed Snakes.
- Selecting the SD card through Settings completed the move. A fresh Sudoku J2ME import reached the board and had its converted game on SD. Lemtris reached its main menu after an app restart. With landscape menus and a portrait game override, Sudoku changed the screen from 1240×1080 to 1080×1240. Returning to the library restored 1240×1080.
- Physical GameSir Pocket Taco fit, live card extraction, complete playthroughs and in-place upgrades of the signed candidate were not tested.

## Java ME FileConnection follow-up (fixed in local source)

The original candidate derived the private and cache URIs from the Android storage path. Moving to SD changed the guest-visible directory and produced a malformed drive prefix. MicroLoader now publishes the legacy virtual C: paths independently of the selected volume. FileConnection continues to resolve C: under the active library.

On Retroid Classic, three fresh ARM64 debug instrumentation processes used a dedicated package and the actual removable card. The first wrote one byte to both private and cache files through JSR-75, checked their URIs against the pre-migration formula and moved the library to SD. The second reopened both files through the same URIs, verified the old byte, wrote a different byte and moved the library back. The third reopened and verified both updated bytes. All three phases passed. The ARMv7 APK and full suite were not rebuilt for this follow-up. [Regression check](../../app/src/androidTest/java/javax/microedition/shell/GuestStorageMoveTest.java). Phase logs are under `build/checks/2026-11-20-private-uri/`.

Raw local evidence is under `build/checks/2026-11-16-storage-layout/` (Android tests, native phone result, orientation transitions and URI comparison) and `build/releases/0.8.0-beta.1/` (target build and signing logs).
