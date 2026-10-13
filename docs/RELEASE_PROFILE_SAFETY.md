# Release Profile and Import Safety

Verified on 2026-10-13. These shared fixes are public source, not a new public
APK. The ordinary build still uses the current M3G engine.

## User-Visible Fixes

- Optimized APKs no longer lose the stable names of touch-layout/size/opacity,
  reach and preparation-hint fields. Ten fields now explicitly retain their
  existing JSON keys. This also prevents R8 from making a profile field
  read-only and crashing a restart trial or settings-copy operation.
- The profile format stays at version 4. Existing plain JSON files remain
  compatible; the change cannot recover values already discarded by an older
  optimized build.
- JAR conversion retains stack-map frames while computing the stack size after
  bytecode instrumentation. Valid Java 7/8 exception handlers could previously
  fail with "Dexing error" / "stack: overflow". Old frame-free bytecode is still
  supported. This is a general importer fix, not a game-specific exception.
- PNGJ's transitive JUnit dependency is excluded from application runtime.
  JUnit remains available to tests.

## Verification

Both ordinary Handheld and Phone debug APKs build. Each passes 14 existing JVM
cases; three new importer JVM cases cover Java 5, 7 and 8 bytecode.

The same shared code was tested in isolated R8-optimized research APKs:
five profile tests pass on physical Retroid Pocket Classic and five on an
Android 15 Phone emulator. Tests exercise stable JSON through the actual
profile file load/save boundary, writable fields and detached mapping/copying.
Retroid also passes 48 debug preparation/trial/preset/profile checks.

On Retroid, the earlier optimized build reproduced a restart-trial crash.
The fixed version successfully restarts and keeps a phone preset; the final
candidate also restarts a Nokia trial and rolls back to the confirmed Sony
Ericsson profile. This uses the real separate MIDlet process.

An original MIDlet fixture confirms RMS readback and subsequent writes after
installing the replacement-engine rehearsal APK over the original-engine
rehearsal APK on both targets. Eleven inspected data/config files retain
identical hashes; existing library entries remain. These are isolated,
debug-signed packages, not the final production-key update path or an
every-game save guarantee. Commercial game archives and save payloads remain
private.

[Reproducible procedure, artifact hashes, logs and screenshots](https://github.com/EriArk/JavaEmulator/blob/codex/m3g-replacement/experiments/m3g-host/release-20261013.md).
No engine switch, root-license change, APK distribution or GitHub Actions
enablement is included. See [licensing status](LICENSING.md) for remaining
release preparation.
