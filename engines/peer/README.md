# Android Secure Peer Core

Two-player ICE/DTLS/SCTP transport for AbyssME. The 0.6 candidate enables the
existing Java Bluetooth adapter as early experimental, low compatibility.
This is not universal game support or guaranteed connectivity across networks.
See [protocol and verification](../../docs/SECURE_PEER_TRANSPORT.md).

## Build

Requires Git, CMake 3.22.1 and Android NDK 28.2.13676358 in addition to the main
project prerequisites. `:peer-core` is an Android library without Qt, media,
WebSockets or a mandatory relay. It uses a hidden static C++ runtime, avoiding
replacement of the engines' shared runtime. Native loading is lazy.

`core/CMakeLists.txt` acquires and verifies these unmodified upstream checkouts
under ignored `build/peer-sources`. No game, account or firmware files are inputs.

| Component | Revision | License |
| --- | --- | --- |
| [libdatachannel 0.24.6](https://github.com/paullouisageneau/libdatachannel) | `6b1e2e620f1e37f0eafeee702eaea0043cb305fd` | MPL-2.0 |
| [libjuice](https://github.com/paullouisageneau/libjuice) | `b89c792e3612faf2f12cf35bcc56857313a06be3` | MPL-2.0 |
| [usrsctp](https://github.com/sctplab/usrsctp) | `fec583d54493f879d2ae44a743423bf8a04371ab` | BSD-3-Clause |
| [plog](https://github.com/SergiusTheBest/plog) | `94899e0b926ac1b0f4750bfbd495167b4a6ae9ef` | MIT |
| [Mbed TLS 3.6.7](https://github.com/Mbed-TLS/mbedtls) | `068ff080b369adfac81509f9b57b2afabaf82dc5` | Apache-2.0 selected from dual terms |
| Mbed TLS framework, build tooling | `dde0c4a0e448a0552f18817dcea633bb851fd288` | Retained upstream notices; not linked |

The wrapper/configuration are original GPL-3.0-only code. Upstream files and
notices retain their own terms. `mbedtls_config.h` enables the DTLS-SRTP extension
referenced by libdatachannel even with media disabled; it does not add a media
channel or link libsrtp. It also enables Mbed TLS pthread locking for concurrent
native workers. Certificate fingerprint verification remains enabled.
No copied Qt implementation is part of this module.

Checkouts with modified tracked files or unexpected revisions fail configuration.
Initial acquisition needs network access. Gradle `--offline` requires the cached
checkouts and submodules and does not fetch them. Main source ZIPs do not need
their own Git history, but dependency acquisition/validation does require Git.

```text
gradlew :peer-core:testDebugUnitTest
gradlew :peer-core:externalNativeBuildDebug -Pandroid.injected.build.abi=arm64-v8a
gradlew :peer-core:peerSourceBundle -Pandroid.injected.build.abi=arm64-v8a
```

`peerSourceBundle` produces `build/release-sources/abyssme-peer-corresponding-source.zip`
containing the upstream trees, submodule sources, notices and this module's build
and wrapper sources. `tools/prepare-release-sources.ps1` includes this task for
future release preparation. Distribute it with the matching application source
and other native source bundles, not instead of them. The APK includes component
license texts under `assets/licenses/peer`. No release is authorized by building
or documenting these artifacts.

## API Boundary

The single room owner creates `PeerBinding` from an admitted snapshot, supplying
the ordered host/guest account IDs, exact content hash, engine, adapter version,
mode, room generation and fresh connection-attempt UUID. Never construct that
snapshot from an untrusted incoming offer. Close the peer when any identity,
membership, game, account or room incarnation changes.

`SecurePeer` owns the native endpoint. Its listener emits bounded signaling
fragments to an authenticated private mailbox. The receive path must pass the
provider-authenticated author. `poll`, `receiveSignal` and `send` run on one actor
thread; `close` may interrupt it from another thread. Game messages are unavailable
until DTLS and the encrypted opposite-seat binding handshake both succeed.

Production room preparation/launch, the virtual Bluetooth service/channel
multiplexer, and separate-network acceptance are still required. Do not expose a
Ready button or advertise a game capability solely because this core connects.
