# Unified APK preparation - 2026-10-18

## Availability

Development toward 0.4.0-beta.1, version code 306. No GitHub Release, signed
production installation, APK upload, Nextcloud upload or website deployment in
this checkpoint. Published beta.6 assets are unchanged; the release listing was
not rechecked. Local debug installations remain separate from the official app.

The primary `fdroid` build retains `io.github.eriark.abyssme` and is adaptive.
The `phone` flavor is kept only as a migration/testing target. Release verification
now targets the primary artifact, without building a second public Phone APK.
The deprecated HANDHELD_MODE build field is false in all flavors; remaining
legacy branches are inactive, while device-sensitive focus uses runtime detection.

## Behavior

- Java, Mophun and Symbian share device tracking and visibility policy. Auto
  recognizes Android GAMEPAD/JOYSTICK sources, excludes virtual/keyboard-only
  devices and offers explicit mode/visibility overrides for unusual hardware.
- Connection changes do not rewrite game settings, orientation or named schemes.
  A controller is input priority, not a prohibition on using touch concurrently.
- Every known controller removal releases input, cancels pending Select and
  latches a pause until a user menu action. A second controller or reconnection
  does not clear that latch. Removal while backgrounded defers the menu to return.
- Hidden controls neither draw nor claim their former touch area. Java keeps
  its keyboard instance attached, avoiding stale Canvas references. Legacy
  keypad cancellation now releases guest keys but never activates its menu key.
- Native engines retain their existing session pause methods. Java uses MIDlet
  pause/foreground notifications, not forced suspension of arbitrary game threads.
- Library, settings and installer focus/fullscreen behavior remains controller-
  aware; new activities do not force landscape. Existing orientation is preserved.
- Old Phone profiles with an explicitly hidden keypad retain Off. Old Handheld
  profiles with both keyboard/touch flags disabled use Auto without rewriting
  their mappings, rotation or orientation. Explicit visibility overrides win.

## Migration

`Library transfer > Move from AbyssME Phone` opens the old app or existing
Java/Mophun restore picker, and links separate Symbian whole-phone backups.
Java/Mophun backup round trips verify save hashes, mappings, new visibility/mode
settings, orientation and library metadata. Existing conflicts stay protected.
Symbian phone backup regression tests also pass. No old app is uninstalled.
App-wide preferences and external-launcher shortcuts are not migrated.

This is not a new automatic cross-package data reader, nor fresh end-to-end
acceptance of a production-signed Phone-to-unified migration. That release
rehearsal remains necessary; the existing menu transfer implementation is reused.

## Checks

| Check | Result / local evidence |
| --- | --- |
| Combined adaptive APK and instrumentation | Passed; `build/unified-verified-build.log`, final mapper-dialog guard rebuild `build/unified-final-guard-build.log` |
| Java/Mophun-only adaptive debug build | Passed; `build/unified-base.log` |
| JVM tests, explicit rerun | 38 passed; `build/unified-checks.log`, `build/unified-base.log` |
| Emulator Android 15 controller/overlay/settings | 43 passed; `build/unified-emulator.txt` |
| Retroid Android 14 controller/overlay/settings/backup | 66 passed; `build/unified-retroid.txt` |
| Real Java/Mophun controls trials, Symbian host lifecycle | 4 passed; `build/unified-live-retroid.txt` |
| Retroid library focus/settings/import plus real Snakes UI | 22 passed; `build/unified-native-library-final.txt` |
| Final controller/touch/settings/import regression batch | 63 passed on each device; `build/unified-final-emulator-5554.txt`, `build/unified-final-MC94516AQF040305380.txt` |
| Final mapper-dialog guard build installed on Retroid | 8 controller checks passed; `build/unified-final-guard-retroid.txt`; official app restored to foreground |
| Combined lint | No errors, 406 existing warnings; `build/unified-verified-build.log` |

Eight new DeviceControls tests cover policy combinations, source classification,
old-profile interpretation, release of diagonals/buttons/triggers, multiple devices,
unknown removals, idempotent close, preview/persistence, full viewport restoration
and the primary manifest. Connection callbacks are simulated deterministically;
this is not physical unplug/replug acceptance. A Symbian host test verifies
background removal/reconnection, pause persistence and fresh input after Resume.

The library focus test now checks actual controller presence instead of the old
flavor flag. An initially failing settings test still expected the obsolete
`Vibration` label; corrected it to the existing `Game vibration` label and reran
the whole affected batch successfully. No production label changed for that test.

Live fixtures: Age of Empires II Deluxe (`aoe.jar`), a Mophun bubble-menu fixture
and installed N-Gage Snakes. Tests exercise common menus, Always/Off/Auto, Undo,
screen capture, native display/orientation and keypad/gamepad settings. Captures
show title/menu screens, not complete gameplay, audio or save acceptance.

Inspected local captures are in `build/unified-captures/`: Java controls, Java
with a numeric deck, and Snakes with a portrait gamepad deck. Games, firmware,
game-art captures, signing keys and debug APKs are not committed.

Before publication: production-signed upgrade rehearsal with the old Handheld
package, menu-based migration rehearsal from the shipped Phone build, physical
controller reconnect acceptance and final source/dependency artifact verification.
