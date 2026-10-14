# Symbian Distribution Materials

This host includes EKA2L1 at `11f5b6cd141c0884a130ee45743f1f5e17bb1783` under
GPL-3.0-or-later. AbyssME's combined distribution remains GPL-3.0-only; component
headers and terms are preserved. Firmware and games are not included.

## Runtime and Assets

The Android arm64 link graph includes EKA2L1, Dynarmic/mcl, fmt, spdlog, GLM,
yaml-cpp, pugixml, cubeb, libuv/uvw, libarchive, xz, zlib, FreeType, libtess2,
lunasvg/plutovg, SQLite, glad, libfat, miniBAE, mbedTLS, xxHash, miniupnpc and
RE2. Header-only dependencies include Boost, stb, TinySoundFont and thread-pool.
Their pinned sources and notices are included in the source bundle and APK
license directory. The source bundle also contains unused upstream build/test
dependencies; their presence does not mean that they are linked into the APK.

- EKA2L1 GPLv3+, Dynarmic/mcl BSD, miniBAE BSD-3-Clause, libfat MIT.
- FFmpeg is built from the pinned source with GPL, nonfree and version3 switches
  disabled, under LGPL-2.1-or-later. Configuration is retained in build evidence.
  Matching source and build scripts permit relinking a modified combined app.
- FreeType uses its GPLv2-or-later alternative for this combined GPLv3 build;
  libtess2 uses SGI Free Software License B 2.0. Boost uses BSL-1.0.
- Other listed runtime components use their retained MIT/BSD/ISC/zlib/Apache
  terms; SQLite is public domain. See individual notices, not this summary alone.
- Guest patch DLLs/maps come from EKA2L1's `src/patch`, with matching GPL source
  and SDK build scripts. They are replacement shims, not a bundled phone ROM.
  The desktop Symbian SDK/toolchain is not redistributed by this project.
- Only guest DLLs/maps, compatibility YAML and base GLES shaders are copied from
  upstream generated assets. Lua scripts, optional upscale shaders, font copies,
  Git metadata, build files and original SF2/HSB banks are excluded from APKs.
- MIDI uses unmodified **GeneralUser GS 2.0.3** by S. Christian Collins, at
  `684543d5e5efaef08d02be50dcda8d552478fa60`. Its separate License v2.0 is included
  as `assets/licenses/GeneralUser-GS.txt`, including the author's sample-origin
  caveat. It is not relicensed as GPL. Source:
  <https://github.com/mrbumpy409/GeneralUser-GS/tree/684543d5e5efaef08d02be50dcda8d552478fa60>.
  SF2 SHA-256: `9575028c7a1f589f5770fccc8cff2734566af40cd26ed836944e9a5152688cfe`.
  Existing phone MIDI settings are normalized to the supplied TSF bank when opened.

This is an inventory of the selected build, not a claim to ownership of upstream
assets or a guarantee about every third-party sample's provenance.

## Building the Published Source

The app source archive contains the host, patches, notices and GeneralUser GS.
The separate Symbian source bundle contains `sources.json` and pinned component
ZIPs exported from Git, never from arbitrary untracked working-tree files.
`tools/prepare-symbian-sources.py` produces the bundle and notice collection;
`source-lock.json` records component paths, revisions and archive SHA-256 values.

Restore the extracted source bundle into an empty directory:

```powershell
py -3 tools/restore-symbian-sources.py D:/sources/symbian local-games/eka2l1-audit/upstream
.\gradlew.bat -PsymbianRelease=true :app:assembleFdroidDebug :app:assemblePhoneDebug
```

Use Python 3.9+, JDK 21, Android SDK 34, NDK 28.2.13676358 for Symbian, NDK
22.1.7171670 for the existing Java native libraries, CMake 3.22.1, and MSYS2
Bash/Make on Windows. Put MSYS2 `usr/bin` on PATH, or set `-PsymbianMsys=...`.
For a private signing key use the documented `keystore.properties`; debug builds
do not require it. `-PsymbianReleaseCheck=true` builds an isolated debug-signed,
minified rehearsal, never the production application ID.

The exported sources omit unshipped upstream sound banks and miniBAE bank/tools
collections. Restore creates empty placeholders for two obsolete copy-command
inputs; those are never packaged or used for playback. The real GeneralUser
bank is in the app source archive. Bundled libuv prevents the corresponding
CMake FetchContent download. A normal Git checkout with recursive submodules
at the pinned revision can also be used instead of the source bundle.

Android build tools/SDKs are external prerequisites. No bit-for-bit reproduction
of all upstream guest patch binaries or Android dependency AARs is claimed;
their pinned matching source/build materials are supplied separately.
