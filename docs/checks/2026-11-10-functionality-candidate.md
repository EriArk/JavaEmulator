# Functionality Candidate — 2026-11-10

Implementation through local commit `6b3629c8`; subsequent cleanup changes only
indentation in the library capability checks and this handoff documentation.
No source push, signed release, public APK replacement or GitHub Actions change.

## Completed local sequence

1. [Multi-phone transfer](2026-11-10-phone-transfer-queue.md): retained selection,
   review, cancel/retry, save-safe copy/index recovery; 10 standalone queue checks,
   39 targeted Android cases and the 424 JVM checkpoint pass.
2. [Import/backup recovery](2026-11-10-library-transfer-messages.md): actionable
   text in eight languages and retained technical diagnostics; 38 Android cases
   pass. About/Help Website action and missing-handler recovery are included.
3. [Ocean and Amber](2026-11-10-built-in-palettes.md): light/dark styles, localized
   names, checked accessibility state; 11 Android cases pass and captures reviewed.
4. [Java multiplayer](2026-11-10-multiplayer-lifecycle.md): 52 Android cases pass
   with synthetic peers and a separate game process; no production repair needed.
5. [ARMv7 target](2026-11-10-armv7-target.md): Java/Mophun Debug and unsigned
   Release, ELF32/ARM verification, 70 selected Android cases, 419 fresh app JVM
   tests and four configuration guards pass. Existing converter result: five pass.

Counts describe selected, overlapping suites, not a single exhaustive test run.
Each linked record preserves failures, corrections, reruns and limitations.

## Final combined build and regression

Combined Debug, instrumentation and optimized unsigned Release assemble in
**3m 37s** after the four ARMv7 guard checks. The production guard confirms
Symbian payload/notices, expected Java/audio/peer dependencies, and absence of
private games, legacy research payloads and the Debug multiplayer probe.
The final library capability change adds no lasting lint warnings: indentation
cleanup returns the combined report to **zero errors, 804 warnings**.

The combined Debug APK updates the isolated emulator installation in place after
ARMv7, without clearing app data. **Seven further Android cases pass** (31.505s):
`LibraryTabsTest`, `ConsoleSurfaceTest` and the synthetic EKA2L1 queue
selection/review/cancel/discard scenario. All four combined platform tabs remain
usable, library state survives recreation, and the new queue still operates.
This is not signed release-to-release upgrade acceptance.

Local APKs, matching R8 mapping, hashes and logs are in ignored
`build/checks/2026-11-10-candidate/BUILD-INFO.json` and its directory. ARMv7
artifacts remain separately under `build/checks/2026-11-10-armv7/`.
Switching Gradle targets removed earlier ARMv7 Debug intermediates before they
were archived. ARMv7 Debug/test APKs were rebuilt after the indentation-only
cleanup and retained immediately; the 16-library ELF32/ARM check passes again.
Their manifest records this rebuild explicitly: earlier runtime results apply to
the same semantic source, not a fresh test run on the rebuilt bytes.
Debug APKs use this Linux environment's debug key. Unsigned Release APKs are
not installable release candidates until signed; no production key was used.

## Pending inputs and real-game handoff

The Website action is complete. **Discord remains unfinished**: both discovered
official invitations were invalid/expired, and the maintainer has not yet supplied
a working destination. No dead or invented replacement was added.

The connected handheld is still `unauthorized` in ADB. No APK was installed on
it and its existing app/signature/data were not touched. Once authorized, inspect
its installed package/signature first; do not uninstall data to resolve a debug
key mismatch. Physical controller/TalkBack and real-game testing remain next.

| Next acceptance | Concrete scope |
| --- | --- |
| Transfer/storage | Synthetic UI first, then copied representative multi-phone collection; cancel/retry and newer-save preservation, including accessible external storage. |
| Java/Mophun | Exact-title/edition/hash, launch, input, audio, pause/resume, save/relaunch and import/backup; use disposable copies, not the user's active saves. |
| #48 N95/LCD | Establish repeatable cold startup on the matching copied N95 phone before Original/LCD Soft/LCD Classic, Keep/Undo and Resume checks. |
| #52 Crash Bandicoot | Exact edition, engine and failure diagnostics remain unidentified; a different local Crash title cannot establish a fix for the report. |
| Java multiplayer | Two actual devices/accounts, Ready/start, sustained exchange, disconnect/return/rejoin, then different networks. Synthetic same-emulator results do not establish this. |
| ARMv7 | Native 32-bit hardware, performance, real game/save and signed-upgrade checks before distribution. |

No real games were run during this functionality checkpoint. Public release state
was last reconciled in the earlier November 10 documentation review; it was not
rechecked or changed by these local builds.

## Subsequent emulator game work

The maintainer subsequently requested continued work while away from the handheld.
[The November 10 real-game checkpoint](2026-11-10-real-games.md) records Java/Mophun
observations and the N95 ARM-translation blocker. It supersedes the handoff's
pending-game status, without changing this earlier build's evidence or establishing
physical-device acceptance.
