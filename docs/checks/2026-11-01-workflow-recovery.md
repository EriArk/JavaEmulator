# Trial Settings and Menu Recovery

Date: 2026-11-01. Starting revision: `d6ba65e4` on `main`.
Source-only UX follow-up; no public APK, version or release asset changes.
Asphalt/N95 investigation is deferred at the maintainer's request.

## Shared Repairs

- The 20-second settings trial previously subtracted a second every time the
  menu/focus returned, even when no trial time had elapsed. It now uses a
  monotonic active-time budget, preserves fractions of a second across pauses,
  and ignores repeated Resume/Suspend calls. Detached views suspend the timer;
  reattachment resumes only a previously running trial. Timeout fires once.
- Java Restart and restart-required Undo previously discarded the running
  preview before checking network restrictions or accepting the restart
  activity. Rollback now happens only after the handoff succeeds. A rejected
  request retains the trial and its baseline instead of allowing autosave to
  commit a partially reverted state.
- A failed pre-launch trial could leave `settings-trial.json` for a later
  ordinary Play. The shared launch helper removes that draft on synchronous
  staging/handoff failure. Library-card settings, standalone settings and the
  compatibility helper use it. Library settings dismiss only after the launch
  call succeeds, so errors remain visible and the user can retry.
- The common dialog's controller Back/B and header close bypassed Android's
  noncancelable state (notably during Mophun restart). They now respect it;
  explicit page-back routes remain usable. Disabled actions and canceled key
  releases cannot confirm a menu command. The JAR/archive installer also ignores
  canceled A/B/Back releases instead of starting or closing from an interrupted
  press.

These changes reuse shared controls and existing translated strings. No new
compatibility presets, title exceptions, game binaries, firmware changes or
platforms are introduced. Java/Mophun/Symbian use the same preview bar and game
dialog; Java's cross-process restart path has its own handoff guard.

## Build and Focused Device Evidence

Retroid Pocket Classic, Android 14, serial `MC94516AQF040305380`.
Only `io.github.eriark.abyssme.debug` and its instrumentation APK were updated.
The official package, game saves and confirmed settings were not reset.

- Combined build succeeds in 16s:
  `build/workflow-recovery-final-build.log`. Debug APK SHA-256:
  `2096dc1ed27463d04d4b82617cbf804d6f397e512642e9de501891265b78c87b`.
  An earlier build overlapped the addition of the shared launch helper and
  failed test compilation against the preceding app classes; it is not the
  final build (`build/workflow-recovery-build.log`).
- 79 selected Android tests pass in 53.213s, with no skipped fixture cases:
  `build/workflow-recovery-device.log`. Coverage: installer UI/error/cancel,
  menu focus/placement/navigation, preview budget and detach/reattach, saved
  settings/trial ownership, live picture Keep/Undo, phone presets and Java
  MIDlet startup lifecycle. These include nine newly added test methods.
- The clock fixture runs 50 menu/focus cycles without spending the budget,
  excludes long background intervals, verifies fractional time and one-shot
  expiration. It is deterministic clock injection, not a 50-cycle human test.
- Restart/launch fixtures inject ActivityNotFound/Security failures, retain
  in-memory edits, preserve the confirmed profile, and check one-shot trial
  claiming. This covers synchronous handoff rejection, not every possible
  asynchronous Android/engine startup failure.
- Cross-engine game UI fixtures follow Screenshot and Restart under More.
  Test-only rebuild: `build/workflow-recovery-test-build.log`.

## Installed-Game Scenarios

Three opt-in scenarios pass on the final Debug APK in 164.781s, none skipped:
`build/workflow-recovery-games.log`.

- Java **Absorbed**: launch through the existing JAR import entry, common menu,
  50/200/100 percent speed trials with Undo, 128x160 / 480x800 / 240x320 screen
  trials with Undo, cross-process Restart, cancel Exit and then confirm Exit.
- Mophun **Deep Abyss 1.1 T300**: launch through the MPN import entry, common
  menu, 50/200/100 percent speed trials with Undo, cancel Exit and confirm Exit.
- Original N-Gage **Snakes**: three restarts and three Home/return cycles, with
  exported nonblank guest-picture checks. These checks can capture startup
  logos; they do not assert gameplay movement or a completed level.

Inspected local captures: `build/workflow-java-restarted.png` (Absorbed menu),
`build/workflow-mophun-trial.png` (Deep Abyss menu with the trial timer), and
`build/workflow-snakes-restarted.png` (Snakes developer logo after restart).
No Asphalt run was added. Tests use existing maintainer-provided installations;
commercial game/firmware files are not included in source or artifacts.

## Limits

Injected Android key events are not physical-controller comfort or disconnect
acceptance. Lifecycle/fixture checks are not full-game, audio, FPS or broad
compatibility claims. Public release files were not revalidated in this batch.
Private ROM/game inputs and local diagnostic artifacts remain untracked.
