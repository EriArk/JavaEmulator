# Licensing

## Current Source

The combined AbyssME application uses **GPL-3.0-only**, with the complete text
in [LICENSE](../LICENSE). This applies to the combined distribution and new
AbyssME work without a different explicit license. It does not relicense third-
party files, previously published versions, games or artwork. Individual source
headers and component notices remain authoritative for those components.
The former root Apache license is preserved in [licenses/Apache-2.0.txt](../licenses/Apache-2.0.txt).

On 2026-10-13 the maintainer approved removing the old engine entirely. The
Nokia/Symbian EPL-1.0 M3G native implementation, Java bindings and inherited EPL
tone compiler are removed from this source tree. Ordinary builds use the tested
FreeJ2ME-derived replacement and original tone compiler. They do not link the
old engine. Its history remains available at commit `05562126`; old APKs are
historical artifacts, not part of the new combined distribution.

## Engine Provenance

| Component | Source and terms |
| --- | --- |
| J2ME Loader / MIDPShell / JL-Mod and inherited Android implementation | Apache-2.0; retained headers |
| Nokia Asha gesture compatibility (optional Java ME debug build) | Original AbyssME gestures and controller integration, GPL-3.0-only. The guest thread adapter in dexlib carries an Apache-2.0 header. The build contains no Nokia SDK, firmware or test game. [Source and device checks](checks/2026-11-21-asha.md). |
| M3G replacement | FreeJ2ME-Plus `baca76a4dda6f30abac67fb4a3db009eed05f91c`, GPL-3.0-or-later; adapted sources, Android GLES/native paths and pinned generator included in [engines/m3g](../engines/m3g) |
| Tone compiler/player | Original Apache-2.0 implementation, not a relicensing of the removed EPL code |
| Mophun | Wolphun `780649421fc5b1faabc7589c3165f17a6a316848`, GPLv3; selected source and modifications in [engines/mophun](../engines/mophun) |
| Underlying Mophun VM/3D work | Wolphun credits nofun; Radrat Softworks copyright and Apache-2.0 license are included |
| Mascot Capsule | Existing Apache-2.0 implementation; independent of M3G, retained |
| piece-emu (optional ARM64 debug integration) | [Pinned upstream revision](https://github.com/autch/piece-emu/tree/9aedad032c5d06a8c6d7cf74142a2b9ac32d77ac), MIT. The libretro-only target links the emulator, its MIT libretro API header and the Android NDK C++ runtime. The optional updater reader uses OrangeSignal CSV 2.2.1 (Apache-2.0), with the retained jLHA redistribution notice. Firmware and game assets are user-supplied. [Bundled notices](../engines/piece/bridge/src/main/assets/licenses). |
| SameDuck (optional Mega Duck ARM64 debug host) | [Pinned MIT source](https://github.com/LIJI32/SameBoy/tree/5619abdb01cee6bedb47599cdb5532c318443b52). The optional module carries the SameDuck and libretro header notices, plus attribution for Evan-Amos’s public-domain handheld photograph. [Bundled notices](../engines/megaduck/bridge/src/main/assets/licenses). |
| Potator (optional Supervision ARM64 debug host) | [Pinned source](https://github.com/libretro/potator/tree/227c5f6f3ce74d32e9002ce24c1420288559a860). Upstream includes the Unlicense and the separate M65C02 source notice prohibiting commercial distribution. The project uses the unmodified CPU sources for local device testing. Public distribution requires review of these terms. [Bundled notices](../engines/supervision/bridge/src/main/assets/licenses). |
| Ruffle (optional Flash Lite debug integration) | [Ruffle 0.8.0 source](https://github.com/ruffle-rs/ruffle/tree/cd4cb8f45f144d5a9209f496ee09b60ad136b5b3), MIT or Apache-2.0. The build uses the integrity-checked self-hosted npm package, its license texts and the [upstream dependency notices and photograph credit](../engines/flash/bridge/src/main/assets/licenses). |
| Dragonfruit (optional Cybiko ARM64 debug integration) | [Pinned H8 and Cybiko core](https://github.com/SovGVD/Dragonfruit-JC4827W543-esp32/tree/f142c9287f156b559de8d98b0030d2432b938e48), MIT, with upstream MAME and ReBiko notices. The CyOS archive and DataFlash format references come from [cybiko-web-emulator](https://github.com/SovGVD/cybiko-web-emulator/tree/ee1f2bcc9c7f541863c5da333cb802a32a3b5676), MIT. The Classic device photograph is CC BY 2.5. [Packaged notices](../engines/cybiko/bridge/src/main/assets/licenses). The app does not bundle boot ROMs, CyOS or games. |
| GP32emu (optional ARM64 debug integration) | [Pinned upstream revision](https://github.com/gameblabla/gp32emu/tree/6e5091f115fb1976f128a9696f59b2e421eb62b0). The core metadata lists BSD-3-Clause and MIT. The interpreter build links the ARM/SoC core, card and package loaders, ZIP/miniz, audio resampler, video effects and libretro glue. [Notices and sticker attribution](../engines/gp32/bridge/src/main/assets/licenses). No top-level license covers all linked units explicitly. Scope clarification remains open before public distribution. Firmware and games are user-supplied. |
| DingooEmu (optional ARM64 debug integration) | [0.3.0 upstream source](https://github.com/AloysHF/DingooEmu/tree/f57c9339e6021cd37c4bd694c14b0813a9b5ee72), BSD-3-Clause. The pinned Android binary includes Cranelift/Wasmtime support crates (Apache-2.0 with LLVM exception) and region (MIT). [Core and asset notices](../engines/dingoo/bridge/src/main/assets/licenses). |
| Experimental FSR 1.0 shaders | AMD FidelityFX-FSR `a21ffb8f6c13233ba336352bdff293894c706575`, MIT; GLES2 EASU/RCAS adaptation, [provenance and modifications](EXPERIMENTAL_ENHANCEMENTS.md), [retained notice](../app/src/main/assets/licenses/FSR-MIT.txt) |
| xBRZ / CRT EasyMode shaders | Libretro GLSL `cba01a48b0974a1232bea68d999a7c21bc4c233e`; xBRZ has retained Hyllian MIT and Zenju GPLv3 notices, with an AbyssME two-pass adaptation preserving the single-pass reference and defaults. EasyMode is unchanged and declares GPL without a version, with GPLv3 selected under [section 14](https://www.gnu.org/licenses/gpl-3.0.en.html#section14). Original notices and [modification provenance](../app/src/main/assets/licenses/RETRO-SHADERS.txt) included; opt-in GLES2 integration for Java and Mophun |
| MicroEmulator portions | Existing Apache-2.0 alternative selected where the headers offer Apache/LGPL alternatives |
| Library UI icons | Seven Lucide icons at `7faf87628a1ad6175975bc595344337ce14a7db4`, ISC with retained Feather MIT notices for inherited icons; SVG converted to Android VectorDrawable with host palette tint. [Bundled notices](../app/src/main/assets/licenses/LUCIDE.txt) |

The root GPL scope does not remove Apache notices. The
[ASF compatibility explanation](https://www.apache.org/licenses/GPL-compatibility.html)
describes inclusion of Apache-2.0 code in a GPLv3 work. Filepicker's MPL-2.0
source remains MPL-2.0 and is additionally available under GPLv3 as part of this
larger work under MPL section 3.3; see the
[Mozilla FAQ](https://www.mozilla.org/en-US/MPL/2.0/FAQ/).

The platform launcher uses hardware illustrations derived from reference photographs. [Bundled credits](../app/src/main/assets/licenses/DEVICE-STICKERS.txt) identify each launcher illustration. The [design catalog](../design-concepts/platform-stickers/README.md) records the reference and attribution for each reserve sticker.

## Upstream Permission History

[Wolphun issue #1](https://github.com/jamisson2006/wolphun-a-Mophun-emulator/issues/1)
contains the author's positive response and GPLv3 clarification. The reply was
rechecked on 2026-10-13. We do **not** attribute an EPL linking exception or a
new license to that reply. Removing the EPL implementations, not stretching
the permission's wording, is the selected integration path.

## Dependency Materials

The multiplayer components described below are included in `0.6.0-beta.1`,
with their existing terms unchanged. "Unreleased" in the original checkpoint
descriptions below refers to their introduction before this release. The peer
corresponding-source archive is included inside the dependency source bundle;
Symbian sources remain a separate bundle. No games, firmware or credentials are
included. See [0.6 verification](releases/v0.6.0-beta.1-checks.md).

The multiplayer temporary-contact actor is a Java adaptation of the
owner's GPLv3 TrainerOS implementation at `d69e50dc`, not a Qt library linked into
Android. The adaptation retains a GPL-3.0-only source header and its protocol/
modification provenance in [MULTIPLAYER](MULTIPLAYER.md). It introduces no new
binary dependency, credentials, bundled service or license change.

The directory reader also adapts TrainerOS's directory/community flow
reviewed at `e7b24ba6`, with automatic admission, strict v2 product/capability
filtering and bounded Android lifecycle handling. It reuses public service IDs,
not bot credentials. [Directory provenance and limits](LOBBY_DIRECTORY.md) records
the separate GPLv3 shared-service extension. No Qt code is linked into Android.

The room workflow continues that GPL-3.0-only Java adaptation: host
admission, expiring invitations, incarnation fencing and generation-bound Ready.
It uses an isolated AbyssME message namespace, not TrainerOS cross-play. See
[room provenance, modifications and verification](LOBBY_ROOMS.md). The companion service rollout is recorded in the
[October 24 deployment checkpoint](FLUXER_PUBLIC_DIRECTORY_CHECKPOINT.md);
that record does not establish current live-service health.

The subsequent [room-contact integration](LOBBY_CONTACTS.md) also adapts the
TrainerOS manual-friend ownership boundary, with an Android asynchronous lease.
It has the same GPL-3.0-only scope and introduces no binary dependency.

The [virtual Java Bluetooth adapter](VIRTUAL_BLUETOOTH.md) is original
GPL-3.0-only code. It delegates from inherited Apache-2.0 JSR-82 wrappers without
removing their headers. Its first checkpoint introduced no transport dependency.

The [secure peer core](SECURE_PEER_TRANSPORT.md) adds
libdatachannel/libjuice (MPL-2.0), usrsctp (BSD-3-Clause), plog (MIT) and Mbed TLS
(Apache-2.0 selected from its dual license). Upstream source files are unmodified;
the original JNI/Java wrapper is GPL-3.0-only. Exact revisions, retained notices,
build configuration and the corresponding-source task are documented in
[engines/peer](../engines/peer/README.md). APK component license assets and the
additional source archive remain required for releases containing this core.

`tools/release-inventory.gradle` inventories the actual resolved release graph,
records artifact hashes and POM declarations, and collects available source JARs.
POM declarations alone are not a license audit: inherited/missing declarations,
native source and additional notices are reviewed separately. Release preparation
must include matching dependency materials, without commercial games, saved
games, signing keys or private diagnostics.

MobileFFmpeg's compact AAR is pinned to upstream commit
`963b4eea110250811f4f4fc7d59c1a86d9dd6b2c`; its hash and codec configuration match
the recorded upstream prebuilt. It uses LGPLv3-or-later, without GPL/nonfree
codec switches. The source bundle must include its source, build scripts and
native dependency provenance. This does not claim a bit-for-bit reproduction of
every upstream third-party binary.

The direct-MIDI/ADPCM bridge, released in 0.7.0 Beta 1, calls these existing pinned native
libraries; it introduces no replacement synthesizer, bank or prebuilt codec.
Public EAS/FFmpeg headers retain their Apache/LGPL notices and original contents,
except an original Android public-header configuration file. The original bridge
is GPL-3.0-only. [Header provenance and ABI requirements](../app/src/main/cpp/audio/README.md)
and `assets/licenses/AUDIO-BRIDGE.txt` identify the corresponding sources already
included in the dependency bundle. Inherited component terms remain unchanged.

APK assets retain full GPL, LGPL, Apache, MPL and component notices. Source and
build materials allow installing a modified version with a user's own signing
key. Maintainer signing keys are never shipped.

## Release Boundary

An updated LICENSE or a successful source build is not itself a published beta.
See [PROJECT_STATUS](PROJECT_STATUS.md) and the dated release report for the
actual distribution state, hashes and checks. New APKs belong in GitHub Releases,
not in the Git tree. GitHub Actions remains disabled.

EKA2L1 / Symbian / N-Gage was not included in 0.2.0-beta.1. The 0.3 combined
build uses `-PsymbianRelease=true`, curated upstream assets and matching pinned
dependency sources. The root GPL scope is unchanged. See the
[distribution inventory and sound-bank terms](../engines/symbian/DISTRIBUTION.md),
and PROJECT_STATUS for actual release availability. Original upstream SF2/HSB
banks, optional shader packs and Lua scripts are not shipped in the combined APK.
Games and firmware are not covered by AbyssME's license and must be supplied
separately.
