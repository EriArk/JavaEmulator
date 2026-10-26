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
| M3G replacement | FreeJ2ME-Plus `baca76a4dda6f30abac67fb4a3db009eed05f91c`, GPL-3.0-or-later; adapted sources, Android GLES/native paths and pinned generator included in [engines/m3g](../engines/m3g) |
| Tone compiler/player | Original Apache-2.0 implementation, not a relicensing of the removed EPL code |
| Mophun | Wolphun `780649421fc5b1faabc7589c3165f17a6a316848`, GPLv3; selected source and modifications in [engines/mophun](../engines/mophun) |
| Underlying Mophun VM/3D work | Wolphun credits nofun; Radrat Softworks copyright and Apache-2.0 license are included |
| Mascot Capsule | Existing Apache-2.0 implementation; independent of M3G, retained |
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

## Upstream Permission History

[Wolphun issue #1](https://github.com/jamisson2006/wolphun-a-Mophun-emulator/issues/1)
contains the author's positive response and GPLv3 clarification. The reply was
rechecked on 2026-10-13. We do **not** attribute an EPL linking exception or a
new license to that reply. Removing the EPL implementations, not stretching
the permission's wording, is the selected integration path.

## Dependency Materials

The unreleased multiplayer temporary-contact actor is a Java adaptation of the
owner's GPLv3 TrainerOS implementation at `d69e50dc`, not a Qt library linked into
Android. The adaptation retains a GPL-3.0-only source header and its protocol/
modification provenance in [MULTIPLAYER](MULTIPLAYER.md). It introduces no new
binary dependency, credentials, bundled service or license change.

The unreleased directory reader also adapts TrainerOS's directory/community flow
reviewed at `e7b24ba6`, with automatic admission, strict v2 product/capability
filtering and bounded Android lifecycle handling. It reuses public service IDs,
not bot credentials. [Directory provenance and limits](LOBBY_DIRECTORY.md) records
the separate GPLv3 shared-service extension. No Qt code is linked into Android.

The unreleased room workflow continues that GPL-3.0-only Java adaptation: host
admission, expiring invitations, incarnation fencing and generation-bound Ready.
It uses an isolated AbyssME message namespace, not TrainerOS cross-play. See
[room provenance, modifications and verification](LOBBY_ROOMS.md). The companion
service result extension stays in the separate TrainerOS PR until deployment.

The subsequent [room-contact integration](LOBBY_CONTACTS.md) also adapts the
TrainerOS manual-friend ownership boundary, with an Android asynchronous lease.
It has the same GPL-3.0-only scope and introduces no binary dependency.

The [virtual Java Bluetooth adapter](VIRTUAL_BLUETOOTH.md) is original
GPL-3.0-only code. It delegates from inherited Apache-2.0 JSR-82 wrappers without
removing their headers. Its first checkpoint introduced no transport dependency.

The subsequent unreleased [secure peer core](SECURE_PEER_TRANSPORT.md) adds
libdatachannel/libjuice (MPL-2.0), usrsctp (BSD-3-Clause), plog (MIT) and Mbed TLS
(Apache-2.0 selected from its dual license). Upstream source files are unmodified;
the original JNI/Java wrapper is GPL-3.0-only. Exact revisions, retained notices,
build configuration and the corresponding-source task are documented in
[engines/peer](../engines/peer/README.md). APK component license assets and the
additional source archive are required for any later release containing this core.

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
