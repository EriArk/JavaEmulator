# Symbian file-transfer bounds and repeat-launch checks

Date: 2026-10-18. Unreleased development work after public beta.5.
Baseline source: `13929ed3`. Native dependency: pinned EKA2L1
`11f5b6cd141c0884a130ee45743f1f5e17bb1783`, restored in the ignored
`build/symbian-source-check` directory. Changes are generated adapter patches;
the dependency checkout, private games and phone firmware are not modified.

## Shared repairs

- `launchGame` now holds the guest kernel lock during application registry
  lookup as well as launch. Previously the running guest could mutate the
  registry before the old, narrower lock was acquired. RAII releases the lock
  on early return too.
- RFile writes validate length against available descriptor data, distinguish
  legacy current-position markers from wide offsets, and append at EOF when
  the requested position is past EOF. The former gap-filling path passed the
  address of one byte to a potentially much larger host write.
- Reads validate signed lengths and descriptor capacity before allocating or
  copying. Computing remaining bytes beyond EOF no longer underflows; an EOF
  read produces zero bytes. Failed descriptor writes are no longer reported as
  successful reads.
- RFs section reads use the same bounds, reject negative/oversized arguments
  and count partial reads in bytes instead of whole requested-size elements.
  This path previously wrote directly into guest memory without checking the
  destination capacity.
- Invalid transfers and reported seek/short-write failures return errors.
  No title, hash, startup delay or game-specific setting is involved.

Contract reference: the original
[Symbian SDK RFile documentation, preserved mirror](https://docs.huihoo.com/symbian/s60-3rd-edition-cpp-developers-library-v1.1/GUID-35228542-8C95-4849-A73F-2B4F082F0C44/html/SDL_93/doc_source/reference/reference-cpp/F32_EKA2/RFileClass.html).
The identified source defects are independently reproducible boundary problems;
their role in Asphalt's intermittent guest heap failure has **not** been proven.

## Verification

Physical device: Retroid Pocket Classic, Android 14. Used only the isolated
`io.github.eriark.abyssme.symbian.debug` package. The official Handheld beta.5
installation was not replaced or cleared. Existing virtual phones and games
were reused without reinstalling them or resetting their storage.

- Native runner: 12 capture-cleanup checks, 27 capture-routing checks, the
  card-identity suite and **86 file-transfer boundary cases** passed. The latter
  also exercised real partial reads, EOF buffer guards and append readback on
  the device. These verify the shared planner and byte transfers, not every
  guest IPC variant end to end.
- Both combined Handheld/Phone debug APKs built successfully.
- Fresh JVM test execution: **29 tests per variant**, zero failures/errors.
- Combined lint: zero errors; 403 Handheld / 410 Phone warnings remain.
- Snakes on RH-29: two 25-second cycles, including the real shared-menu Restart
  action, passed the visible-color-frame check and confirmed exit to the shared
  library. Inspected captures show the title screen on both cycles. This run
  does not establish gameplay, sound or save persistence.
- Native Asphalt3 on RM-320: a 55-second first cycle reached a visible race.
  The second cycle, after Restart through the menu, ended on a black/white
  frame. The final two-cycle test **failed**, correctly rejecting the blank
  frame. Repeated-launch compatibility remains open.

The fixture driver now selects an exact indexed game (with an optional phone
identifier), instead of assuming the currently selected phone's catalog. It
asserts that the game window remains active, can run repeated menu restarts,
and optionally rejects blank color-game frames. Color checking is opt-in so
monochrome titles are not rejected. A colorful screenshot does not establish
animation, gameplay correctness or input acceptance.

Earlier diagnostic runs recorded guest `USER 44` and `KERN-EXEC 3`, and one
native crash. A stale `last-session.log` initially resembled a new failure;
timestamps and the active runtime log distinguish it from later runs. Temporary
register/stack and transfer-rejection tracing was removed before the final
build. Do not describe the final blank frame as a freshly proven heap panic.

## Local evidence and availability

Ignored `build/` records, not public downloads:

- `relaunch-final-build.txt`, `relaunch-final-tests-build.txt`,
  `relaunch-final-lint.txt`, `relaunch-native-file-transfer.txt`.
- `relaunch-final-cycles.txt` (Asphalt failure),
  `relaunch-final-snakes.txt` (title/restart/exit pass).
- `relaunch-final-0-54.png` (race), `relaunch-final-1-54.png` (blank restart),
  `relaunch-snakes-0-24.png`, `relaunch-snakes-1-24.png` (title screens).
- `relaunch-final-current.log` and earlier diagnostic logs remain private.

Local debug APK SHA-256:

| Variant | SHA-256 |
| --- | --- |
| Combined Handheld debug | `3c8dfac997cd47cddd0f3a0bdb76dec79dad54c30ab4c7a3126d6fb4a2b880e2` |
| Combined Phone debug | `13d1647da7d4aa282897a8b8593e7887d527c67d8ff2b8c06ed4a688511d2db9` |

No release was created or replaced. Phone was build/lint/JVM checked here, not
newly installed on a physical phone. Public beta.5 APKs do not contain this batch.
Games, firmware, private runtime files and these debug APKs were not published.
Next investigation: isolate guest-memory/startup failures from the separate
repeat-session blank-frame path, using the failing cycle without resetting data.
