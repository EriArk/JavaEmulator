# AbyssME Dependency Sources

This bundle accompanies the matching AbyssME application source archive and
signed APKs. `dependencies.json` records the resolved release runtime graph,
binary hashes, source paths/hashes and upstream POM license declarations.
It contains 118 external runtime artifacts; Maven source JARs and pinned source
archives cover their sources. Guava's `listenablefuture:9999.0-empty-to-avoid-conflict-with-guava`
is an intentionally empty dependency-conflict placeholder.

POM declarations are not authoritative license overrides. Preserve the license
headers and notices inside each source archive. The application includes full
component license texts and collected source notices in its assets. No games,
firmware, saved games or signing credentials are supplied.

## Building the Application

Extract the matching app source archive, install JDK 21, Android SDK/Build Tools
34 and NDK 22.1.7171670, and follow its `CONTRIBUTING.md`. A source ZIP does not
require Git history; the optional dev-flavor counter falls back to zero.
The ordinary build compiles the checked-in M3G/Mophun sources;
research generation and private game fixtures are not prerequisites.

The combined APKs additionally use the separate Symbian source bundle.
Restore it with `tools/restore-symbian-sources.py`, follow
`engines/symbian/DISTRIBUTION.md` for its NDK/CMake/MSYS prerequisites, and pass
`-PsymbianRelease=true` to Gradle. Without that property the build contains only
Java/Mophun. The combined APKs target arm64-v8a.

`gradlew :app:assembleFdroidDebug` builds the adaptive APK using a debug key.
There is no separate Phone build task in the unified app.
For release builds, use your own key and the provided signing-config example.
An independently signed build cannot overwrite the maintainer-signed package;
use the app's library backup/restore menus to move your data. Android permits
installing modified versions; maintainer signing keys are neither needed nor
included. Dependencies may still need downloading from Maven repositories.

## Native Dependency Provenance

Starting with 0.6.0-beta.1, the experimental secure peer module adds
`abyssme-peer-corresponding-source.zip` to preparation output. It contains pinned
libdatachannel, libjuice, usrsctp, plog, Mbed TLS and build-tool source trees plus
the JNI/Java wrapper/build scripts and component notices. See the matching app's
`engines/peer/README.md` for revisions, CMake/NDK prerequisites and validation.
These native sources are separate from the Maven runtime graph below.

`source-lock.json` pins archive URLs and SHA-256 values. Preparation is available
as `tools/prepare-release-sources.ps1` in the application source.

- **MobileFFmpeg compact:** commit `963b4eea110250811f4f4fc7d59c1a86d9dd6b2c`.
  The selected AAR exactly matches `releases/mobile-ffmpeg.aar` at this commit.
  The archive includes the FFmpeg source, JNI sources, CPU helpers, build scripts
  and licenses. Follow its Android build instructions and `android.sh`; preserve
  the compact flags in `build/android-ffmpeg.sh`. All four shipped ABI codec
  strings identify LGPLv3-or-later, `--enable-version3`, no GPL/nonfree switches,
  and the narrow MMF/WAV/ADPCM/PCM codec configuration. This is not a full codec
  distribution. The historical prebuilt uses an Android NDK/Clang cross-toolchain;
  identical binary reproduction with the current NDK is not claimed.
- **MIDI:** `billthefarmer/mididriver` at
  `ce52b068ae3525edf6a8a946c396f9ce08d1dd69`, including its Sonivox/native sources,
  JNI glue and upstream build configuration. Preserve Apache notices.
- **CameraX image processing:** CameraX `1.3.0-beta01` release source at AndroidX
  commit `bef05e5a3f6d09babb620159f75c659bcc7df495`. Separate archives contain
  `camera/camera-core` and the `external/libyuv` Gradle wrapper. For a full AndroidX
  checkout/build, use that revision with the upstream AndroidX build instructions.
  Place the included external libyuv tree at its expected external source path.
  The accompanying libyuv `c707acd1dbf8899ac5ea47f369a3674081df95d0` is the last
  first-parent revision on `androidx-camera-release` before the June 21, 2023
  CameraX release. That selection is a branch-history inference, not a verified
  bit-for-bit reconstruction of Google's AAR. BSD license and patent notice are
  preserved. The app's camera feature is inherited, not a new rendering backend.
- **MPL filepicker:** `dc26b8281e2e71019501db85f526daeca09dd80d`. Its source archive
  includes build scripts and the MPL-2.0 license. No incompatible-secondary-license
  declaration was found in its Java/Kotlin source headers. Keep MPL terms for
  these files and the GPLv3 larger-work terms described in `docs/LICENSING.md`.

The app's `libabyssm3graster.so` and `libmicro3d.so` are built directly from the
matching application source. The removed Nokia M3G library is not shipped.
No claim is made that this inventory substitutes for independent license review
or reproduces every upstream binary byte-for-byte.
