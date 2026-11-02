# Server Build Migration - 2026-11-02

## Scope and Availability

Manual Linux compilation of the combined Java/Mophun/Symbian ARM64 APK, with
local Windows signing. This is build infrastructure, not a new application
release. The published `v0.7.0-beta.2` APK remains unchanged and Windows-built.
Server-validation APKs reuse code 315 solely for local verification; they are
not replacement release assets and were not installed on a device.

## Verified Runs

- First successful full native build: `770a6e2c470b97b8ef958acfd648da396ce08d1f`,
  output `20261102T183604Z-770a6e2c470b-unsigned`. Gradle took 32m 43s after SDK
  bootstrap/source preparation. All three engines and production payload
  guards passed; 424 JVM tests passed, with no failures, errors or skips.
- Updated workflow: `b90a37f8eb2eab7b5ffd499a47fdf67800a4979b`, output
  `20261102T191412Z-b90a37f8eb2e-unsigned`. Gradle took 30s using the native cache,
  excluding source checks and three separate guard invocations. Again 424 JVM
  tests passed. APK, XML reports, build/guard logs, metadata, image ID and R8
  mapping were downloaded to `build/remote-artifacts` and verified locally.
- An unchanged-revision warm repeat, output
  `20261102T191617Z-b90a37f8eb2e-unsigned`, also passed: Gradle 20s, 424 fresh JVM
  tests, six restoration tests and all three guards. Native caches were not
  deleted. Unsigned APK and R8 mapping hashes match the preceding run. The
  build container exited and the account lock was released.
- Six Linux source-restoration tests pass: archive hash/path rejection,
  executable bits, CRLF normalization (including C inputs and non-UTF8 shell
  scripts), binary preservation, retained local edits and refusal to overwrite
  a populated tree.
- Three negative Gradle checks pass: normal Release without signing keys fails;
  invalid native-job limits fail; unsigned export cannot be combined with a
  debug-signed rehearsal.
- Existing signed-output refusal and incorrect-certificate refusal pass. Failed
  signing leaves no output or temporary APK and does not modify unsigned input.
  PowerShell/Bash syntax checks and Java signer compilation pass.

## Artifact Identity

Verified updated-workflow unsigned artifact:

- Filename: `AbyssME-0.7.0-beta.2-release-unsigned.apk`
- Size: 71,529,769 bytes
- SHA-256: `36d5631b51fee593b9b5661827046041e8edf6c535f1c3706397fa4329f16d97`
- Package `io.github.eriark.abyssme`, version `0.7.0-beta.2`, code 315.
- Minimum Android API 29; target API 34; ARM64 only.
- Locally signed validation copy SHA-256:
  `959149963b19b5f14c4bc2aa4f9ef6d170f8ac19508e4e2ee5809e8a1bf57c58`.
- `apksigner verify` passes with the existing official certificate:
  `722b6f8e74090bd3895eb0bc76433333c637e881758fc10ac276dd214499c38f`.
- Manifest/package/SDK/ABI inspected with `aapt2`. The first complete server
  artifact's 18 native libraries also passed ARM64 ELF-header checks.

The server's `BUILD-INFO.json` intentionally says `production_signed: false`:
it describes the unsigned export, not the separately named local signed copy.

## Repairs Found During Migration

- Linux builds no longer receive Windows MSYS executable paths.
- The read-only container sends Java/Android user state to its writable cache.
- Published source ZIPs retain original hashes. Restored copies receive Unix
  executable bits and normalized text line endings; binary bytes and unrelated
  local edits are preserved. This also fixes FFmpeg configure probes scanning
  CRLF codec declarations.
- Peer notices now use variant-specific tracked `Sync` outputs, with explicit
  asset/lint dependencies. The first warm build exposed duplicate resources;
  deleting native caches is not used as the repair.

## Isolation and Limits

Builder data stays under the dedicated account directory `~/abyssme-build`.
Live container inspection confirmed two CPUs, 4 GiB RAM, no extra swap,
512 PIDs, a read-only root, dropped capabilities, no-new-privileges and no
published ports. A second lock acquisition was rejected during compilation.
Signing keys, SSH credentials, games, firmware and service directories are not
mounted or uploaded. GitHub Actions was rechecked and remains disabled.

Retroid was reserved by its owner and was not controlled or changed. There is
no server-built APK device/emulator acceptance in this record. Existing shared
Windows SDK/Gradle caches were not deleted. Build success does not replace the
runtime, source-bundle and publication steps in [RELEASING](../RELEASING.md).
