# Mixed-library DocumentsUI journey — 2026-11-10

Local-only follow-up to [individual real-game backups](2026-11-10-real-game-backups.md).
The existing combined Debug candidate runs on Android 11/API 30, x86_64 with
ARM64 translation, in the dedicated emulator. No handheld, production runtime
change, public source push or APK publication is part of this checkpoint.
Public release assets and live services were not rechecked.

## Fixture and result

`ProvidedLibraryJourneyTest` is opt-in (`-e libraryJourney true`). It uses the
same permitted Diamond Rush, SimCity Metropolis and Deep Abyss 1.4 payloads as
the linked game report. Six nested/per-game save files are **synthetic sentinels**,
not game progress. Favorites, play counts, last-played timestamps and three
320×240 profiles with distinct rotations are fixture metadata. Shared storage
contains one conflicting file and one new nested file.

Source and destination catalogs live in unique temporary roots. The test restores
Config afterward and waits for the transfer worker to stop before changing roots.
It operates the real transfer activity and actual Android DocumentsUI Create/Open
Document screens; no intercepted picker result or fabricated transfer state is used.
Shell access only reads the test-created archive, stages its corrupted copy and
removes uniquely named test documents. The owner's active library is not imported
into or replaced.

**One complete scenario passes in 22.335s** (`run-8.log`):

- Cancel both document pickers without modifying either catalog.
- Export all three games through DocumentsUI, and verify the archive excludes DEX.
- Restore preview shows three selected games with saves; recreation retains it.
- Cancel before the first import using the actual Cancel button. A test-only worker
  latch makes this boundary deterministic; no midway-copy interruption is claimed.
- Recreate the result activity, review Retry and restore all three games. Every
  save hash, payload hash, profile dimension/rotation and catalog field matches;
  Java DEX is regenerated. One shared file is copied and the existing one retained.
- Write newer destination saves, then reopen the original backup. All three entries
  are rejected as duplicates and Restore is disabled; newer data stays unchanged.
- Alter one ZIP payload without updating its manifest hash. The UI rejects it with
  an actionable source-data message and keeps the library unchanged. Selecting
  the valid archive afterward recovers to its duplicate-review screen.

## Evidence and harness corrections

Ignored evidence: `build/checks/2026-11-10-library-journey/`, including build logs,
instrumentation/logcat, screenshots, private altered ZIP and per-run APK digests.
The final test APK builds in 11s, SHA-256
`6e45f45ed61b794e693663b9f3f81f0f66023433bbe77a984bf0d0302ef13aad`.
Production candidate remains
`afaab3a0ac6ada2877279cc2440a80f188a5c1073e6a83afda1ea8ef94eee53b`.
The final exported archive SHA-256 is
`fb4915d7338851037d59499b451d49a3633db14f05f28ac01bcdfc166cff4f3d`.

Runs 1–7 are harness failures, not accepted restore results: DocumentsUI file rows
do not expose ordinary accessibility click actions; the tree also contains a
Downloads breadcrumb behind its drawer. Early touch sequences failed to activate
the file. One selector revision also failed on null node text. The final harness
selects the actual drawer row, waits for it to settle, recognizes an already
focused file row and activates it with DPAD Center. Failure captures/logs remain;
no production fix is inferred from those input failures. The successful restore,
duplicate and error screens were visually reviewed.

No broad JVM/lint rerun is claimed for this test-only addition. This is a small
mixed-library UI/storage checkpoint, not a large-collection, cloud-provider,
physical-input, upgrade or full-game acceptance result. The agreed next steps are
an in-place build update, meaningful guest progress/reopen, then paired real-game
network checks. Native ARM64 N95/LCD checks remain deferred.
