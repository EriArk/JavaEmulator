# Remote Android Builds

Manual SSH builds use a dedicated Docker environment. They do not enable GitHub
Actions, publish a release, replace a device installation or move signing keys.
The current public Beta 2 remains the Windows-built, verified release.

## Layout and Limits

The build account owns `~/abyssme-build`: a public Git checkout, pinned Symbian
sources, Gradle caches, temporary files and dated output folders. No service data,
Docker socket, SSH keys, local games, firmware or production signing configuration
is mounted inside the compilation container. It has no exposed port, runs as the
host account UID with dropped capabilities and a read-only root filesystem.

Only one build may hold the account's lock. Compilation is limited to two CPUs,
4 GiB RAM with no additional swap, one Gradle worker and one native compile job.
The launcher requires 4.5 GiB available host RAM before starting; live services
take priority. The initial SDK image installation is separate from compilation.
No existing image, volume, service or Windows cache is automatically deleted.
Output and cache retention is explicit, not an unattended cleanup job.

## Run from Windows

Use the project's existing verified SSH alias. The default is `books-server`;
the launcher does not change host keys or enable password/key forwarding.

```powershell
# Commit/push first. No uncommitted/private files are copied to the server.
.\tools\remote-build\Invoke-RemoteBuild.ps1 -Revision HEAD -Mode unsigned
# Optional isolated debug APK instead:
.\tools\remote-build\Invoke-RemoteBuild.ps1 -Revision HEAD -Mode debug
```

The server fetches the exact full Git SHA from the public origin. Dirty remote
checkouts are rejected rather than reset. Cached native sources are restored from
the hash-pinned published source bundle, never from a phone/game collection.
Every run builds all three engines and runs app/converter JVM tests; unsigned
Release also runs the production-payload guard. It exports the APK, checksum,
test XML, build log and `BUILD-INFO.json`. Downloads are verified locally under
`build/remote-artifacts`, on the project drive rather than a temporary C: folder.

## Signing Boundary

`-PunsignedRelease=true` is an explicit export mode, not a fallback when keys are
missing. It uses the production application ID with an `-unsigned.apk` filename,
no signing configuration, and remains non-installable until signed. Ordinary
Release still fails when signing credentials are missing. Rehearsal/debug modes
remain separate. Do not upload an unsigned artifact as a public release APK.

Keep `keystore.properties` and the maintainer keystore on the Windows machine.
Use local Android Build Tools `zipalign` and `apksigner` to align, sign and verify
the downloaded APK before following [RELEASING](RELEASING.md). Never place the
keystore in the public checkout, builder image or SSH upload. APK signing, source
bundles, runtime testing and publication remain explicit release steps.

## Toolchain

The Dockerfile pins the Temurin 21 base image digest and Google command-line-tools
archive SHA-256. SDK 34, Build Tools 34.0.0, CMake 3.22.1 and NDKs 22.1/28.2 match
the existing project. Headless installation accepts the SDK package licenses.
References: [SDK package installation](https://developer.android.com/tools/sdkmanager)
and [official command-line-tools checksums](https://developer.android.com/studio#command-line-tools-only).
This is a pinned SDK/native toolchain, not a bit-for-bit reproducibility promise
for Ubuntu package updates or all third-party prebuilts.

## Verification

Server bootstrap and the first real combined build are pending. Do not treat
an available SSH session or a built image as a working Android build pipeline.
