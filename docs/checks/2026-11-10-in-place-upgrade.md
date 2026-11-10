# In-place Debug update — 2026-11-10

Local-only follow-up to the [mixed-library journey](2026-11-10-library-journey.md).
The dedicated Android 11/API 30 x86_64 emulator uses ARM64 translation. No physical
handheld, public-source push or APK publication is part of this result. Public
release assets and live services were not rechecked.

## Artifacts and scope

The baseline uses Beta 2 runtime sources/resources at `cc698da3`, with current
Linux/ARM64 Gradle plumbing at `7fdb6187` and the existing pinned native sources.
The 52 differing runtime files were temporarily swapped only in the build cache;
all were restored afterward. This is not an exact rebuild of the public APK.
Baseline build succeeds in 28s; SHA-256:
`94c377642e64d1f4d29122d60f39b2f67af89df3ea1a634540ca8d8c2919572c`.

The destination is the retained combined candidate, SHA-256:
`afaab3a0ac6ada2877279cc2440a80f188a5c1073e6a83afda1ea8ef94eee53b`.
Both APKs verify with the same local Android Debug certificate (SHA-256
`b66b26fbdf717e14106fec613c63feaa2042d711234daf9e01b6d4b0f0ab84a8`),
package `io.github.eriark.abyssme.debug`, version code 315. Installation uses
`adb -s emulator-5580 install -r`, without uninstall or data clearing. This is a
same-version Debug replacement across source revisions, not an official signed
release upgrade or version-code migration test.

## Verified journey

`ProvidedUpgradeJourneyTest` has opt-in `upgradePhase` values `seed`, `verify` and
`cleanup`. Seed runs on the baseline, the candidate is installed, then verify runs
in a fresh instrumentation invocation. A durable fixture journal supports cleanup
if installation is interrupted. Only a UUID library root and explicitly selected
library/vibration/sort preferences are modified; original preferences are restored.

The fixture reuses Diamond Rush, SimCity Metropolis and Deep Abyss 1.4 payloads,
with synthetic saves and metadata from the mixed-library test. It does not launch
these games or assert their progress. **Seed passes in 3.434s; verify in 2.997s.**

- All 19 payload/config/save/shared-file hashes match before and after update.
- All three catalog paths/titles, favorites, play counts and last-played values match.
- Owned preferences match before UI launch. Java remembers the Diamond search and
  List mode; Mophun remembers its empty search and Grid mode. Switching back shows
  Diamond Rush, with adapter and displayed-row checks.
- Fixture files and journal are removed; original preferences are restored.

Runs 1 and 2 passed data checks but captured transitional list frames. The harness
now waits for both the expected adapter title and bound row, then UI idle before
capture. Run 3 screenshots were visually reviewed. This is a test synchronization
correction, not evidence of a persistent production navigation defect.

Ignored evidence: `build/checks/2026-11-10-upgrade/` (source-swap manifest, build and
instrumentation logs, fixture journal, artifact inspections and screenshots).
Final test APK: `build/checks/2026-11-10-library-journey/upgrade-3-tests.apk`, SHA-256
`995da1024f90489938f21450da9970f99f027106e2cf1454f6fc8b3f19b77904`.
No broad JVM/lint rerun or production repair is claimed. Physical input, signed
release updating, larger libraries and meaningful guest progress remain separate.
