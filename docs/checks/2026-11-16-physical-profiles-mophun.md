# Physical controller profiles and Mophun audio — 2026-11-16

## Availability

Local source and a private debug build, not a new public beta. The signed ARM64
Beta 9 application remains installed separately. Boulder Dash and Lemtris were subsequently supplied and checked; final
dual-architecture release checks are pending.

## Physical controller profiles

A third independent catalog, `physical-profiles.json`, stores global defaults
and named physical-controller profiles. Settings and each game's Controls page
open the manager. The existing mapper edits buttons, D-pad, both sticks and
diagonals. Shared changes apply when a linked game opens again. Existing games
retain their own bindings until explicitly reset; bulk assignment can reset
only physical mapping overrides. Touch and graphics selections remain separate.

The mapping preset list, selected preset and effective bindings form one atomic
setting. Try/Keep/Undo includes the new assignment. Library backups include the
catalog; portable per-game definitions preserve mappings across restore without
overwriting destination profiles. A legacy-library import regression was found
and corrected: imported settings without the new inheritance marker must become
explicit physical overrides, rather than being replaced by global defaults.

## Mophun audio

The old session gate enabled sound only for one Deep Abyss 1.4 executable hash.
That game-specific restriction is removed. Every game can now use the existing
MIDI/resource and PCM paths. PCM follows host pause/resume and emulation speed;
initializing while paused cannot start playback, and teardown wakes/joins the
mixer and releases AudioTrack. Output failures stop the worker instead of spinning.
This is an engine change, not an allowlist of tested games.

The supplied T610 Boulder Dash 1.1 and Lemtris 1.0 both use MIDI resources.
Boulder Dash also uses five-byte BEEP records (little-endian frequency and
millisecond duration, followed by unsigned volume). Previously those resources
were silently ignored. Original `BeepSequence` synthesis and `vBeep` support now
use the existing PCM mixer, including looping, rests, pause, speed and stop.
Malformed/unsupported resource formats return failure instead of false success.
No SDK implementation, binary, sound bank or commercial game is bundled.

## Mophun execution timing

Both games advance substantial gameplay through instruction/frame-dependent
loops. Limiting display presentation alone left the guest CPU effectively
unbounded. Neither supplied game's measured execution uses the suspected SLEEP
opcode; changing SLEEP or adding a title-specific FPS cap was not the fix.

A common VM work budget now paces short/extended instructions independently of
the presentation cap. Original synthetic loops were compiled and timed in the
original Mophun SDK 2.5 SP2 emulator. The T610 profile took 2,279/4,702 ms for
300,003/600,004 short instructions; the low-end reference profiles took about
10,600/20,932 ms. Stack/memory loops on T610 took 5,190/5,194 ms for 550,008
instructions, and 10,409/10,402 ms for 1,100,008 instructions.

The approximate budget charges 4 units per short instruction and 5 per extended
instruction, at 512 units/ms for T610 and 115 for T300/T230. CPU budget 100–300%
scales this throughput; emulation speed scales the virtual clock and audio;
frame limit remains an independent presentation cap. Idle credit is bounded,
and host pause/stop interrupts waits. This is calibration against SDK loop
measurements, **not cycle-exact phone hardware**, a complete API cost model,
or full-game compatibility acceptance. [Original probe source](mophun-cpu-probe.c)
is provided for review; SDK tools and compiled binaries remain private.

## Supplied-game results

On Retroid Pocket Classic / Android 14, the debug build reaches gameplay in
Boulder Dash 1.1 (T6x0) and the Lemtris 1.0 (T6x0) tutorial, accepts generated
button events, pauses/resumes, preserves saves across reimport and cold-launches.
Captured audio contains a nonzero signal: Boulder Dash mean/peak -51.7/-18.5 dBFS,
Lemtris -48.3/-25.9 dBFS over the 25-second lifecycle captures, including silence.
This is digital capture evidence, not subjective listening or a whole-game test.

The additional cracked Boulder Dash 1.0 T300 file reaches level selection but
these automated sequences did not enter gameplay (including a numeric-key
attempt). The continuous-frame resume assertion times out on that static menu;
that sample is **not accepted**, and no claim is made that its file is corrupt.
Demo/expired variants were not tested. V-Rally 2 and 1849 Gold Rush were not
available; general sound support is enabled for them without claiming a pass.

Fixture SHA-256:

| Private supplied file | SHA-256 |
| --- | --- |
| Boulder Dash 1.1 T6x0 | `a9cdb7443a38a769d55add7da7b0731cae16b66a9ec21de7c613886a940a2f9f` |
| Lemtris 1.0 T6x0 | `b0c6c1ec51f01d6d88183e6a4a5fa1c8ffed0ce4fd130f67617c56a6d1b5bef9` |
| Boulder Dash 1.0 T300 cracked | `3010cee00f961c3602eab5b4ed1f7424196aa4feed3e80428f07a0f5bda693f1` |

## Verification checkpoint

- After timing/BEEP changes: 434 fresh app JVM checks and 16 focused Android
  checks pass (6 pacing, 6 PCM/BEEP, 4 MIDI). Deep Abyss 1.4 and 1.1 each
  repeat the bounded lifecycle sequence successfully; gameplay captures reviewed.

- Debug build and 434 fresh app JVM tests pass, no failures/errors/skips.
- 25 Android profile checks pass: new physical catalog semantics and existing
  graphics/touch profile behavior.
- A later 37-test Android run passes: physical/UI profiles, real mapper editing,
  settings trials, right-stick integration and PCM lifecycle/mixing.
- All 27 library importer tests pass after the legacy physical-binding migration
  fix, including backup round-trip with an unassigned shared profile.
- Four existing MIDI tests pass (preparation, pause/resume, replacement, speed).
- The new shared mapper initially showed the old “Save & resume” action; changed
  it to “Use mapping”, then reran the UI tests successfully. UI captures were
  inspected on Retroid Pocket Classic / Android 14.
- Deep Abyss 1.4 and 1.1 each pass bounded launch, input, pause/resume, reimport
  and cold activity relaunch checks. Version 1.4 creates two save files; 1.1
  creates none during the sampled sequence. Captured gameplay frames from both
  versions were visually inspected. This is not full-game or audible
  output acceptance, and does not substitute for untested games.

Private evidence: `build/checks/2026-11-16-physical-profiles/` and
`build/checks/2026-11-16-mophun/` and
`build/checks/2026-11-16-mophun-reports/` (supplied games, SDK reference timings,
recordings and focused results). Commercial files are not published.
