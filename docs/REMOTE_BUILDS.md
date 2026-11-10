# Remote Android Builds

Manual SSH builds use a dedicated Docker environment. They do not enable GitHub
Actions, publish a release, replace a device installation or move signing keys.
The current public Beta 2 remains the Windows-built, verified release.
This is now the default maintainer APK build path. Keep the local contributor
workflow available, but do not silently fall back to a heavy Windows build when
the server is unavailable; report the problem and preserve the local disk space.

## Layout and Limits

The build account owns `~/abyssme-build`: a public Git checkout, pinned Symbian
sources, Gradle caches, temporary files and dated output folders. No service data,
Docker socket, SSH keys, local games, firmware or production signing configuration
is mounted inside the compilation container. It has no exposed port, runs as the
host account UID with dropped capabilities and a read-only root filesystem.

Only one build may hold the account's lock. Compilation is limited to two CPUs,
4 GiB RAM with no additional swap, one Gradle worker and one native compile job.
The launcher waits up to ten minutes for 4.5 GiB available host RAM before
starting and checks again after setup; live services take priority. The initial
SDK image installation is separate from compilation.
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
# Java/Mophun-only ARMv7 target (no Symbian source restoration):
.\tools\remote-build\Invoke-RemoteBuild.ps1 -Revision HEAD -Mode unsigned -Target armv7
```

The server fetches the exact full Git SHA from the public origin. Dirty remote
checkouts are rejected rather than reset. Cached native sources are restored from
the hash-pinned published source bundle, never from a phone/game collection.
On Linux, restoration retains ZIP executable bits and normalizes CRLF in UTF-8
text inputs, including C headers scanned by FFmpeg's configure script. Binary
files are unchanged. Existing inputs must match the archive or its normalized
equivalent; modified files are not silently replaced. The original source
archives remain byte-identical and hash-verified.
Combined runs check source restoration and signing/mode/resource validation,
build all three engines and run app/converter JVM tests. ARMv7 runs skip Symbian
restoration, check incompatible modes/flavors, build Java/Mophun with 32-bit peer
and audio dependencies, and run the same JVM tasks. The default target remains
`combined`; artifact names, run directories and provenance record the selected
target. This target extension has local validation only; a fresh server round trip
is still required. Unsigned
Release also runs the production-payload guard. It exports the APK, checksum,
test XML, guard logs, build log and `BUILD-INFO.json`; unsigned Release also
retains the R8 crash-deobfuscation mapping. Downloads are verified locally under
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

`SignApk.java` performs alignment, signing and certificate validation locally
using Java's Properties parser for the existing signing configuration. Passwords
are child-process environment values, not command-line arguments or log output.
It refuses an existing output and removes only its own failed output/temp file.

```powershell
& 'C:/Program Files/Android/Android Studio/jbr/bin/java.exe' `
  tools/remote-build/SignApk.java path/to/remote-release-unsigned.apk `
  path/to/new-signed.apk keystore.properties `
  "$env:LOCALAPPDATA/Android/Sdk/build-tools/34.0.0" `
  722b6f8e74090bd3895eb0bc76433333c637e881758fc10ac276dd214499c38f
```

The final argument is the existing official certificate fingerprint. Contributors
using their own keys must supply their own expected fingerprint; those APKs are
not official updates. Signing a server build does not authorize replacing an
existing public version/tag with different bytes.

## Toolchain

The Dockerfile pins the Temurin 21 base image digest and Google command-line-tools
archive SHA-256. SDK 34, Build Tools 34.0.0, CMake 3.22.1 and NDKs 22.1/28.2 match
the existing project. Headless installation accepts the SDK package licenses.
References: [SDK package installation](https://developer.android.com/tools/sdkmanager)
and [official command-line-tools checksums](https://developer.android.com/studio#command-line-tools-only).
This is a pinned SDK/native toolchain, not a bit-for-bit reproducibility promise
for Ubuntu package updates or all third-party prebuilts.

## Verification

The full combined build, production-payload guard, verified download and local
signing round trip passed on 2026-11-02. All 424 JVM tests, six Linux restoration
tests and three negative build-configuration checks pass. A second warm run
also passed without cleaning native caches. First full Gradle build: 32m 43s;
warm Gradle runs: 30s and 20s. These timings exclude setup/source/guard overhead
and are observations, not future performance guarantees.

Peer notice generation was repaired for incremental builds and separate
Debug/Release outputs. Signed-output preservation and wrong-certificate
rejection were tested. No server-built APK was installed or runtime-tested;
Retroid was reserved by its owner. The published Beta 2 was not replaced.
See the [dated verification record](checks/2026-11-02-remote-build.md).
