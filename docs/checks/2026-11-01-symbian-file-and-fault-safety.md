# Symbian File Shares and Guest Memory Faults

Date: 2026-11-01. Source-only follow-up to
[#48](https://github.com/EriArk/JavaEmulator/issues/48), not a public APK update.
Worktree: `D:\Projects\JavaGameEmu`, `main`. Private N95/RM-320 and N-Gage
fixtures remain ignored. Testing uses the separate Debug package on Retroid
Pocket Classic, Android 14, not the official installation or its saves.

## Shared Repairs

- File duplication previously added the same `fs_node` to the handle table.
  Reads/seeks shared one stream cursor; closing a handle flushed and reset that
  cursor. A duplicate now opens a non-truncating stream with a copied position
  and independent node. Original guest mode fields and use accounting
  are retained. Failed opens/seeks do not replace the donor handle. This is not
  a complete implementation of Symbian file sharing/rename semantics.
- File adoption validates its handle/type rather than blindly returning it.
  Internal file lookup rejects missing/non-file nodes rather than dereferencing
  an invalid handle. The engine's existing unmangled adopt convention remains.
- Cleared Dynarmic TLB entries used zero as an invalid permission tag, although
  zero is also a valid guest page number. The generated ARM64 lookup could
  accept a null/unauthorized mapping near address zero. Use an invalid 64-bit
  tag consistently for initialization, missing permissions, dirtying and flush.
- Enable data-access abort checks and raise Dynarmic `MemoryAbort` after an
  unrecovered memory callback. Do not continue the translated block after the
  kernel has terminated a guest and potentially unmapped its pages. Recoverable
  callbacks can still retry the access. In this ARM64 TLB backend the abort
  check is emitted on the callback fallback, not the direct TLB-hit load/store.
- Correct the ARM64 A32 abort emitter to load the 32-bit halt word with a
  32-bit acquire load. The former 64-bit load can fault on four-byte alignment;
  the first device regression run exposed this after enabling abort checks.

All upstream modifications are guarded generated-source/header patches in
`engines/symbian/core/patches.cmake`; the pinned source checkout is not edited.
There are no title-specific exceptions, ROM modifications or protection bypasses.
The original Symbian implementation creates a separate file share and copies
its position: [TFsFileDuplicate::DoRequestL](https://github.com/SymbianSource/oss.FCL.sf.os.kernelhwsrv/blob/master/userlibandfileserver/fileserver/sfile/sf_file.cpp).

## Investigation Limits

The file-share-only build reached an Asphalt 3 race via direct library launch.
A subsequent cold retry nevertheless reproduced guest KERN-EXEC 3 / read at
address `0x8`, followed by an Android native crash. Therefore the file-share
repair is **not** a verified cure for the game's startup failure. Local evidence:
`build/issue48/asphalt-instant-play.png`, `asphalt-race-running.png`,
`file-share-cold-retry.log`, `file-share-native-crash.log`.

An initial timed moving-frame test failed on a static pre-game screen. The
fixture now optionally waits for `lcd-frames-ready` (five-minute timeout), with
stale signals removed, and can hold a specified gamepad key during each sample.
Foreground ownership is checked before injecting that key. A retry correctly
failed when the native game crashed back to the Android launcher. Neither run
is a passing LCD test. An accidentally started old-test-APK attempt was stopped
and excluded. Never run a second UiAutomation client during instrumentation.

The standalone actual-CPU regression reproduced a host SIGSEGV on the original
JIT, without any game or ROM. It checks TLB invalidation/permissions, aborting
reads and writes before subsequent side effects, recoverable reads and a later
run on the same core. The intermediate abort-check build exposed the alignment
fault described above. Neither intermediate build is a release candidate.

## Final Verification

- Combined Debug + AndroidTest build passes (5m41s). Both APKs install with
  `adb install -r -d` in the separate Debug namespace. Debug APK SHA-256:
  `1eb28c7d3dad91536600ea4850b8be2e41d4bd67c2c7ad49db6786a500e61e98`.
  Build log: `build/issue48/fault-safety-final-build.log`.
- Nine existing/helper native executables pass, including file duplication,
  partial/EOF transfers, card identity, frame/speed/audio, capture and geometry.
  Log: `build/issue48/file-share-native-final.log`.
- The actual compiled CPU/Dynarmic regression passes on Retroid: invalid TLB
  entries, real read-only zero-page access, forbidden writes, invalidation,
  failed read/write without subsequent side effects, recoverable read and a
  later run. Log: `build/issue48/jit-final-device.log`. The same initial fault
  case on the old CPU produced SIGSEGV; the intermediate abort emitter produced
  SIGBUS. Both failures are retained locally, not counted as passing checks.
- Snakes passes the live moving-frame test on this final APK (91.504s): before
  pause, Resume, LCD Soft, LCD Classic and Original. Each sample checks the
  central game area, not animated overlay controls. Log:
  `build/issue48/snakes-fault-safe-final.log`. The five corresponding PNGs in
  `build/issue48/fault-safe-snakes-captures/` are current; other files copied
  from that device folder belong to earlier runs.
- Focused LCD/session instrumentation reports `OK (21 tests)` in 21.665s:
  **15 executed checks pass, six opt-in fixture cases are skipped**, not six
  extra passes. Log: `build/issue48/ui-fault-safe-final.log`.
- Final-APK Asphalt cold launch reaches language selection, the main menu and
  a race. The pre-pause moving-frame check passes; after Resume only one of
  eight samples changes, below the threshold. Held D-pad Up is not established
  as the accelerator in this rotated guest, so this is not proof of a freeze
  or a passing LCD cycle. No LCD preset change is reached. Evidence:
  `build/issue48/asphalt-fault-safe-final.log`, `fault-safe-race.png`, and only
  `before-pause.png` / `after-pause-Resume.png` in
  `fault-safe-asphalt-captures/` (other files in that folder are older).
- A second final-APK cold launch remains on a black/white boot surface. The
  external readiness signal is deliberately sent to sample that static state,
  **not** to certify successful startup. The pre-pause gate fails with zero
  changed samples. Logs: `build/issue48/asphalt-menu-fault-safe.log`,
  `fault-safe-second-cold.log`; screenshot: `fault-safe-repeat-wait.png`.
  N95 cold-start and exact #48 acceptance remain open. Native safety regressions
  passing does not establish that the guest application can recover or launch.

Reproduce after building the combined native library:

```powershell
powershell -File engines/symbian/tests/run-native-tests.ps1 -Serial DEVICE_SERIAL
powershell -File engines/symbian/tests/run-jit-tests.ps1 -Serial DEVICE_SERIAL -NativeBuild engines/symbian/core/.cxx/Release/BUILD_ID/arm64-v8a
```

No full-game, audio, performance or exact-community-incident acceptance is
implied by these focused regressions. Public release metadata was rechecked:
`v0.7.0-beta.1` still lists Revision 1 and its five assets. Asset contents were
not downloaded/rehashed this turn. No public APK, tag or version was changed.
