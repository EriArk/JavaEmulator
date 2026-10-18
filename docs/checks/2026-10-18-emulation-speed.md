# Emulation speed, 2026-10-18

## Scope

Separate per-game 50-200% guest-clock control for Java, Mophun and Symbian.
The default and legacy fallback are 100%. Shared Display/More pages use the
existing draft, Try, Keep and Undo transaction. FPS remains a separate limit.

Java conversion redirects clock reads, Date(), sleep/yield and timed monitor
waits. The custom Timer uses guest deadlines and short real-time waits so live
changes take effect without changing host UI/watchdog deadlines. Original JAR
classes are converted into a content-addressed private code cache; original
installed files and RMS saves are not replaced. Resource-only legacy imports
remain launchable at normal speed and explicitly require a JAR for this option.

Mophun's existing pause-aware clock now supports continuous rate changes.
Symbian uses a monotonic scaled kernel timer with real-time conversion of
event waits; its FPS interval is converted back to guest units to preserve an
independent presentation cap. Concurrent out-of-order samples cannot rewind
the anchor or count the same interval twice. No pinned upstream files changed.

Audio is deliberately unchanged, not claimed to be time-stretched. Guest RTC
and unsupported media/busy-loop timing paths can differ from scaled game time.
This is not a universal correction for all games that run too fast.

## Evidence

- Retroid Pocket Classic, Android 14: 29 shared/device tests passed, including
  actual converted bytecode at 50/100/200%, guest sleeps and Timer callbacks,
  unchanged original JAR/DEX bytes, profile persistence/Undo, and Mophun clocks.
  Local log: `build/speed-retroid-common.txt`.
- Native Retroid checks passed: capture cleanup 12, capture routing 27, file
  transfer 86 boundaries plus round-trip, card identity, 66 FPS checks, and
  speed continuity/pause/delay/FPS independence checks.
- Android 15 x86_64 emulator: the same 29 device tests passed, including the
  converted-clock fixture and actual sleep/Timer callback delays. Local log:
  `build/speed-emulator-common.txt`. No Symbian runtime on this emulator.
- Retroid combined Phone debug: five Symbian/Mophun UI cases passed, including
  50/200/100% live trials, FPS trials, mapping, phone layouts, guest-picture
  capture, restart and background return. Two explicitly unrequested fixtures
  were skipped, not passes. Java's real Age of Empires II Deluxe fixture then
  separately passed its menu/speed trials. Logs: `build/speed-retroid-live.txt`
  and `build/speed-retroid-java.txt`. This does not establish full gameplay.
- 32 app JVM tests passed per variant and four converter JVM tests passed.
  Source checks are not relabeled as signed APK runtime acceptance.
- Build and live-game/release checks are recorded separately in
  [Beta 6 verification](../releases/v0.3.0-beta.6-checks.md).

Game fixtures, firmware and private device logs are not public artifacts.
