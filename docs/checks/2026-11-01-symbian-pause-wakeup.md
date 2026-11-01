# Symbian Pause Wakeup Ownership

Date: 2026-11-01. Source-only follow-up to [#48](https://github.com/EriArk/JavaEmulator/issues/48).
Starting revision: `f060ff32`. Separate Debug package on Retroid Pocket Classic,
Android 14. No official-app replacement, save deletion or private-input publication.

## Repair

The Android bridge sent a counting-semaphore notification on every Resume,
including redundant callbacks from surface sizing and activity focus. A Resume
could also arrive before the worker reached its wait. The next pause performed
one unconditional wait, consuming that old credit and continuing while its pause
flag was still true. A graphics worker could publish another frame under a menu;
the OS worker could resume guest timing before the current pause was released.

Both workers now recheck the current pause/quit state after every wake. Resume
notifies only on a paused-to-running transition. This preserves a wake issued
before wait without allowing it to override a newer pause. Stop still releases
paused workers before joining them. Actual surface destruction remains separate
from menu pause; the earlier live-surface repair is retained.

The helper is shared by the host OS loop and the generated upstream OS/graphics
loops. Pinned engine sources and guest executables are not modified.

## Evidence

- Native pause regression reproduces the former one-wait escape with a pending
  credit and the pause flag still set. The repaired helper stays blocked through
  old/extra signals, ignores 100 redundant Resume calls, completes 200 threaded
  pause/resume races and stops from pause. This uses a controlled counting-wakeup
  fixture; installed-game evidence is recorded separately below.
- Ten native helper executables pass on Retroid, exit code 0:
  `build/issue48/pause-wait-native-final.log`. The actual CPU/Dynarmic regression
  also passes, exit code 0: `build/issue48/pause-wait-jit-final.log`.
- Test runners use `adb push -q` to prevent successful progress messages on
  stderr from becoming PowerShell terminating errors when saving logs. Transfer
  exit codes are still checked. Earlier wrapper-error runs are not the final logs.
- Combined Debug + AndroidTest build passes in 9m12s; updated Debug APK installs
  successfully. Build log: `build/issue48/pause-wait-build.log`. APK SHA-256:
  `65e2fa387e7865acc594414e9098b421ac16957771571c002e6586021ab654e7`.
- Final-APK Snakes moving-frame test passes before pause and after Resume,
  LCD Soft, LCD Classic and Original (91.558s):
  `build/issue48/pause-wait-snakes.log`. Current captures are the five
  `before-pause` / `after-pause-*` PNGs in
  `build/issue48/pause-wait-snakes-captures/`; other copied files are older.
- The first broader lifecycle run passes 11 checks but stops on an obsolete
  test route: Screenshot/Restart were expected on the menu home page instead
  of More. The fixture now follows the current product UI; do not count
  that run as a passed restart scenario. Log: `build/issue48/pause-wait-lifecycle.log`.
- Rebuilt and reinstalled the corrected test APK (Gradle success in 21s;
  `build/issue48/pause-wait-test-build.log`). The final run passes all 12 selected
  tests in 71.318s, with no optional cases skipped:
  `build/issue48/pause-wait-lifecycle-final.log`. This covers ten LCD geometry
  checks, cached-geometry preview, and Snakes through three restarts and three
  Home/background returns with exported guest-picture checks. Those picture
  checks establish rendered output, not motion; movement evidence is the
  separate Snakes test above.

## N95 Investigation Limit

The pre-change cold-launch retry reaches Asphalt's language screen, then returns
to the library with guest `KERN-EXEC 3` / read at `0x8`, before any LCD change.
The previous host-fault repair contains that guest failure rather than crashing
the Android process. Logs: `build/issue48/nov1-continuation-baseline.log` and
`nov1-last-session.log`; screen: `nov1-baseline.png`.

A second cold launch on the repaired APK stays on a static boot surface.
The external readiness signal was deliberately sent to sample that state,
not to certify that startup completed. The moving-frame assertion fails before
any pause (zero changed samples, 74.524s): `build/issue48/pause-wait-asphalt.log`.
Native log: `build/issue48/pause-wait-asphalt-native.log`; capture:
`build/issue48/pause-wait-asphalt-language.png` (despite the filename, this is
the boot surface, not the language screen). No game keys were injected in
this second run. It is not an after-Resume result.

The guest fault is near resource opening; that is an investigation lead, not
proof that the file server or a specific resource caused it. No title-specific
exception, binary patch, protection bypass or firmware reset was added.
This pause repair does not establish a cure for that cold-start fault or the
community's exact N95 filter incident. Keep #48 open pending that evidence.

No public APK, tag or version is changed. Public release asset contents were not
revalidated in this follow-up. No full-game, FPS, audio or manual-controller
acceptance is implied by these focused checks.
