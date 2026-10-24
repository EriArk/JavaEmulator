# Public Directory Checkpoint - 24 October 2026

## What Is Available

The existing shared Fluxer directory service is now deployed with AbyssME v2
and private command confirmations. It preserves 32 TrainerOS advertisements per
account and permits one independent AbyssME public room. No second bot, community,
credentials, channel layout or gameplay relay was introduced.

Android changes remain **unreleased development source/Debug APK**, not a new
public beta. The public `v0.5.0-beta.1` release (code 310, 23 October 21:32:23 UTC)
was rechecked and not changed. No Nextcloud upload or release asset replacement.

## Server Evidence

- Service updated at 13:46:47 UTC; Gateway ready at 13:46:53 UTC.
- Deployed baseline hashes matched the earlier read-only snapshot. Code and a
  consistent SQLite backup were retained privately before replacement.
- Migration was verified on a copy of the actual database. All 12 original
  records were also checked unchanged in the live database after paired testing.
- 26 service tests pass on Windows and Linux staging. These cover separate
  quotas, legacy meetings/category changes, product/owner guards, tombstones,
  migration, recovery and private correlated results.
- Merged TrainerOS source had duplicated v1 field projection outside its v1
  branch: an optional `room` could change a v2 listing's target and expose meeting
  metadata. A failing-before/passing-after regression covers the correction.
  The reviewed deployed modules never contained this merged-source defect.

[Service hashes, backup and rollback](https://github.com/EriArk/TrainerOS/blob/codex/abyssme-directory-rollout/services/lobby-directory/ROLLOUT_20261024.md).
The source correction and rollout record are in a separate TrainerOS branch; no
unrelated TrainerOS worktree or handheld application was modified.

## Live Pair Result

Retroid Pocket Classic (Android 14) hosted using the production room, mailbox and
publisher. An Android 35 emulator used the second dedicated test account and the
ordinary directory reader/room actor. Their existing friendship was not changed.

The final host run passed in **134.805 seconds**; guest in **29.356 seconds**:

1. The host published two v1 TrainerOS test meetings and one real v2 AbyssME room
   under the same authenticated account. The service projected all three.
2. The guest's real directory showed only the AbyssME room, not the two v1
   advertisements. The guest requested public admission without an invitation;
   both room actors observed two members. Host screen recreation retained the
   room; this is not uninterrupted connection or physical-button acceptance.
3. The guest left. The service rejected another AbyssME UUID with `already-listed`
   and a v2 withdrawal targeting the v1 listing with `wrong-product`.
4. Changing the actual host room to invite-only removed its public advertisement
   while both TrainerOS entries remained. Changing back republished the same room;
   closing removed it again. A v1 entry also passed withdraw/republish/close.
5. All test projections were removed. Final database check: no active or projected
   test listings; all 12 original records unchanged; service still active.

TrainerOS v1 publication above uses its real wire protocol through authenticated
test-account DMs, not a new live TrainerOS UI run. Its exact-v1 reader was inspected;
owner-spoof rejection and the 32-entry boundary have service regression coverage,
not new hostile-account live checks.

## Findings and Test Limits

The initial guest setup unnecessarily tried to open a bot DM and received HTTP
403. Browsing/joining does not need that conversation, so the guest path no longer
requires it. The guest account's email verification is still pending; no privacy,
verification or permission bypass was used. The exact cause of that 403 was not
established. Bot-command publishing as that account remains unverified.

One early test also closed its room ActivityScenario when opening a new-task
directory scenario; the harness now opens those sequentially. Another attempt
could select the previous host publication during recovery. Final paired runs
were explicitly correlated to the newly created room UUID, rather than silently
joining any room with the same test owner. Failed attempts are retained, not
counted as successful pair runs.

The stale-listing case prompted a shared room fix: a reachable peer that no longer
hosts the requested UUID returns an authenticated, probe-correlated `unavailable`
response. The guest can refresh instead of waiting the full join timeout. It
reveals no replacement/private room and cannot cancel an admitted session or a
different join attempt. Two JVM regressions cover replay, sender/probe fencing,
closed/replaced hosts and a subsequent valid join. The exact negative-response
path was not separately exercised live; the updated app passed the public pair.

Combined Debug app/test builds and **294 JVM tests** pass (zero failures/errors/
skips); **four Retroid native core tests** pass. No game adapter was registered:
Ready remains disabled. This is public discovery/admission, not online gameplay,
cross-product play, long-run network stability or two physical-device acceptance.

## Reproduction and Captures

`FluxerLiveDirectoryDeviceTest#publicRoomCoexistsWithLegacyListings` is explicitly
opt-in. Host arguments: `fluxerDirectoryRole=host`, `fluxerDirectoryPeer=<test-id>`.
It reports `public_test_room`; start the guest with role `guest`, the host peer ID
and `fluxerDirectoryRoom=<that-UUID>`. Only dedicated `abyssmetest*` accounts with
the named existing test friendship qualify. No account tokens leave Android's
normal credential store. Test meetings use fresh IDs and are closed in cleanup.

Installed development APK SHA-256:
`cb70696469a19ecc9adb8a4846f6233dc0ae179023d2f3a5d875635ef1610214`.
Separate Debug package, `0.5.0-beta.1-debug` / code 310, not the signed release.

[Labelled captures](../screenshots/development/fluxer-public-directory-2026-10-24/README.md).
Ignored local evidence: `build/fluxer-directory-scoped-{host,guest}.log`,
`build/fluxer-directory-unavailable-build.log`, `build/fluxer-directory-native-core.log`.
Earlier `fluxer-directory-host`, `guest`, `guest-retry`, `final-host` and
`final-guest` logs record the unsuccessful attempts above.

Next: the first real online game/transport adapter, plus independent physical
controller and broader connection-recovery acceptance. Do not turn room metadata
or this deployment into a claim that Java, Mophun or Symbian games are online-ready.
