# Multi-phone transfer and local build checks — 2026-11-10

## Availability and scope

Local, unreleased work on top of `a599f90b`. The maintainer explicitly authorized
local compilation for this session. The default server workflow remains unchanged.
Neither an official APK nor a new release was published. The Debug APK retains
the development version label `0.7.0-beta.2` / code 315; it is **not** the published
Beta 2 binary. It uses the `.debug` package and a local debug signing key.

The approved order is recorded in [ISSUE_STATUS](../ISSUE_STATUS.md#development-focus).
This checkpoint covers #49 and part of #43/#51. It does not complete the later
styles, multiplayer, ARMv7 or real-game stages.

## Implemented behavior

- Select several registered phones from one complete EKA2L1 folder/ZIP. Review
  names and copy size before starting; each phone gets isolated writable drives.
- Keep the staged source, selection and results in app-private storage across
  Activity/process interruption. Resume requires explicit review. Cancel stops
  at safe checkpoints and leaves completed phones available.
- Continue past an individual phone failure. Retry only unfinished phones;
  persist a committed destination before indexing, so indexing retries cannot
  replace newer saves. Recovery retains the original ZIP entry list, including
  implicit directories, to keep snapshot identities stable.
- Verify retained file sizes/hashes and reject linked or changed payloads.
  Discard removes the temporary queue/source copy, not installed phones.
  The active phone selection is not changed by batch publication.
- Retain typed failure metadata and render known failures in the current app
  language. Unknown diagnostics remain intact. Queue storage failures and two
  existing platform/archive error paths use localized presentation.
- Add a localized Website action to About and Help. External links retain the
  dialog and handle missing/blocked apps with a copy-address fallback. Existing
  About links use the same guarded opening path.

## Fresh verification

The pinned `tools/remote-build/Dockerfile` was built locally with Podman in Docker
format (to retain its Bash `SHELL`). The temporary Dockerfile only qualifies the
base image as `docker.io/library/eclipse-temurin`; its digest is unchanged. SDK,
Gradle and native outputs are under `/home/eriark/.cache/abyssme-local`, on the
Linux filesystem. An isolated source snapshot excludes private games and keys.
The published Beta 1 Symbian source bundle was verified against SHA-256
`2128b3019b5e2c8adbc0c57f4fd71e576b5d2707a18e3715da49aebf258b5c55` and restored
using the repository tool. Existing private engine checkouts were not modified.

- First full combined Debug build: **17m 20s**, successful.
- Current source: Debug APK and instrumentation APK assemble successfully;
  **424 JVM tests**, zero failures/errors/skips; lint **zero errors, 804 warnings**.
- [Standalone queue checks](../../tools/test-phone-transfer-queue.sh): **10 pass**.
  Cover copy/index failures, cancellation, interrupted journals, old queue loading,
  persisted failure metadata and preservation of newer destination saves.
- Android 11 / API 30 Google APIs x86_64 emulator, with ARM64 translation:
  **39 targeted Android checks pass**. These comprise 23 `Eka2l1TransferTest`,
  nine `SystemTransferLocalizationTest`, six `InfoContentTest`, and the synthetic
  `Eka2l1UiTest#savedSelectionReviewCancelAndDiscardWithoutGames` scenario.
  Initially 38/39 passed: the UI helper selected the confirmation heading instead
  of the same-named button. The helper was fixed to prefer an enabled clickable
  match, the test APK rebuilt, and that one scenario passed on rerun.
- All eight new/changed locale resource sets have matching keys and format
  placeholders. Layout tests include compact, square and landscape sizes,
  enlarged text and pseudo-locales. Queue and About captures were inspected.

Commands (inside the isolated builder, with the restored source directory):

```sh
bash tools/test-phone-transfer-queue.sh
bash gradlew --no-daemon --console=plain --max-workers=2 \
  -PsymbianRelease=true -PsymbianSourceDir=/sources/symbian -PnativeBuildJobs=2 \
  :app:assembleFdroidDebug :app:assembleFdroidDebugAndroidTest \
  :app:testFdroidDebugUnitTest --rerun :dexlib:testDebugUnitTest --rerun \
  :app:lintFdroidDebug
```

Local artifacts, source-file hashes, APK hashes, logs and captures are retained
in ignored `build/checks/2026-11-10-phone-transfer/`. `BUILD-INFO.json` identifies
the exact local snapshot and binaries. They are not public release assets.

## Limits and pending work

No real game, firmware boot, whole-collection acceptance or complete-game result
is claimed. The transfer fixtures use synthetic metadata/files and a test
validator. Standalone Drive E, external mounts and inaccessible Android private
folders remain outside the supported input boundary.

The physical handheld was offered for checks; USB discovery succeeded but ADB
authorization is pending. No installation or device acceptance is recorded yet.

The published product page `https://abysstail.art/en/projects/abyssme` responded
successfully. The old README Discord invite and the invite linked by the site
returned an invalid/expired invitation response; `/discord` returned 404.
The maintainer was asked for a valid permanent invitation. No replacement invite
or first-party redirect was invented, and the Discord action is not yet shipped.
Other backend/helper localization and the later functionality stages remain open.
