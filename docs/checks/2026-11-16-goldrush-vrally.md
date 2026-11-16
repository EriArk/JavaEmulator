# Supplied Gold Rush and V-Rally 2 follow-up — 2026-11-16

## Outcome and availability

No additional runtime change or APK is needed for the reported missing sound in
these two supplied files. Published ARM64 **0.7.0 Beta 10, code 324** reaches
Gold Rush level 1 and a running V-Rally 2 race. V-Rally 2 produces captured audio
after the starting countdown. This Gold Rush build imports no sound API.
The existing Beta 10 sound changes apply globally, without a game allowlist.

The public prerelease was rechecked on 2026-11-16; its publication timestamp is
2026-11-16T08:00:00Z. Runtime source/tag: `c44900442dc22d75988befdf078b70972a1d2121`.
No release assets were replaced for this follow-up.

## Private fixtures

Only the main MPN from each user-provided archive was tested. Game payloads,
raw captures and diagnostic APKs remain private and are not repository assets.

| File | Bytes | SHA-256 |
| --- | ---: | --- |
| `1849GoldRush_[vX.X]_[T6x0].mpn` | 73704 | `1b7b19230eb663530d998300d2d383451f206ec205e53e21f9feacf22a2be2d6` |
| `VRally2_[RC14EU]_[multiscreen]_M5.mpn` | 94256 | `dfbab38d48e95abe63045db8f1e988919e10afe0571321b4943943e07318ee08` |

## Checks and findings

Retroid Pocket Classic, Android 14, ARM64. Input was generated through ADB and
instrumentation; this is not a physical-controller acceptance test.

- Existing `ProvidedMophunLifecycleTest` passes separately for both files using
  isolated temporary library/config/save paths. Checks cover startup, generated
  input, pause/resume, reimport and relaunch. Selected runs: Gold Rush 14.103 s,
  V-Rally 2 extended race 34.964 s, one test passed per run.
- Gold Rush reaches level 1 in both the debug check and the published signed
  APK. Parsing its VMGP import table finds no `vPlayResource`, `vBeep`,
  `vSound*` or `vMusic*` imports; temporary SDK tracing observes no sound calls.
  Its silence is consistent with this build containing no sound playback path,
  rather than a demonstrated emulator audio failure. This does not establish
  the behavior of other Gold Rush versions or diagnose the file as corrupt.
- V-Rally 2 reaches a running race and responds to acceleration/steering input.
  Initial short captures ended before meaningful racing audio and were silent;
  those captures alone were insufficient to assess sound. Extended diagnostic
  tracing observes `vPlayResource` with 49/73-byte MIDI resources and flags 2.
  The game imports that audio API and contains six MIDI headers.
- The extended debug race recording measures mean/peak **-53.9/-21.6 dBFS**.
  A separate 40-second capture of the published signed ARM64 APK measures
  **-53.3/-21.6 dBFS**, including silence. Race frames and nonzero audio confirm
  that Beta 10 plays these effects. These are digital measurements, not
  subjective listening-quality acceptance.

No full playthrough, reference-phone speed comparison, ARMv7 repetition or
V-Rally extra/multipack MPC testing was performed in this follow-up. Earlier
release build, website and artifact-hash checks were not repeated. The check
neither establishes all-game compatibility nor adds a per-game restriction.

## Evidence and cleanup

Ignored local evidence: `build/checks/2026-11-16-goldrush-vrally/` contains
`imports.json`, fixture hashes, lifecycle logs, scripts and private captures.
Key directories are `goldrush-audio`, `vrally-race`, `signed-goldrush` and
`signed-vrally`. The latter two contain captures from the published APK.
Temporary diagnostic changes were confined to the cached debug build; they
were not added to public source or release assets. Diagnostic packages and
explicitly copied temporary device MPN files were removed after verification;
the signed ARM64 Beta 10 installation remains in place.
