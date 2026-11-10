# Stabilization and Remaining Acceptance

Reviewed 2026-11-10. [Current issue status](ISSUE_STATUS.md) is the detailed queue;
[PROJECT_STATUS](PROJECT_STATUS.md) identifies distribution and dated evidence.

## Released Implementation Sequence

The maintainer-approved October 28 order was **#41 -> #42 -> #45 -> #43**.
These implementation batches shipped in 0.7 Beta 1; they are not pending feature
work. #47's platform-installer repair shipped with them. #46 centered card names
followed in Beta 1 Revision 1. Beta 2 includes the subsequent native safety,
transfer and launch-recovery work. Original chronology is retained in
[CHANGELOG](../CHANGELOG.md) and the linked verification reports.

| Issue | Released work | Remaining acceptance |
| --- | --- | --- |
| #41 | Nokia gain, player lifecycle, MIDI isolation, 16-bit ADPCM | Exact Avatar 1.1.6 / Cops 1.2.3, listening and routing |
| #42 | Movable/hideable handle, Back recovery, independent opacity | Cutouts, gestures and broader touch-phone/engine coverage |
| #45 | Per-game virtual Gamepad mapping and graphical Buttons editor | Treasure Towers reproduction and touch accessibility |
| #43 | Eight languages and persistent app language selection | Raw diagnostics, native-speaker review and broader workflow checks |
| #47 | Valid partial-update handling and production-equivalent N95 preflight | Original reporter's exact installer/outcome |

Evidence: [0.7 Beta 1 checks](releases/v0.7.0-beta.1-checks.md),
[Revision 1](releases/v0.7.0-beta.1-r1-checks.md),
[Beta 2](releases/v0.7.0-beta.2-checks.md).

## Proposed Next Development Pass

1. Identify/reproduce #52's launch failure and resume #48's unresolved N95
   startup investigation. A failure before LCD selection is not proof of an LCD cause.
2. Continue #50 only within proven guest-mode capabilities; preserve original
   phone data and avoid arbitrary-resolution or universal-widescreen claims.
3. Implement #51's small support-link task after verifying the official destinations.

This is a proposed order, not a promised release scope. #44 new-platform work
remains deferred by the existing maintainer decision. Additional languages remain
deferred after Japanese and Simplified Chinese. #24 VM research and #29 ARMv7
distribution are separate open tasks, not implicit parts of stabilization.

## Working Boundaries

Do not rebuild already delivered features from stale issue checklists. Keep exact
game editions, released APKs, local builds and physical acceptance distinct.
Tests and reports retain their original dates; this review ran no new runtime
checks. Preserve source phones, saves and private inputs. Maintainer APK builds
use the [remote workflow](REMOTE_BUILDS.md); GitHub Actions stays disabled.
