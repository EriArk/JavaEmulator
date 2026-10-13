# Candidate Dependency Checkpoint - 2026-10-13

This is an artifact/source inventory, not release clearance. The production
engine and root license are unchanged. No dependency binary is redistributed
by this report.

## MobileFFmpeg: Identified Prebuilt Artifact

The dependency is `com.github.nikita36078:mobile-ffmpeg:v4.3.2-compact`.
Its generated POM calls the license `Other`, but that is not the only evidence:

- The tag resolves to commit `963b4eea110250811f4f4fc7d59c1a86d9dd6b2c`.
- The cached 3,009,088-byte AAR has SHA-256
  `0E77F023F0AE2665901DF8064DC59350DAC35543F8771A45EBC7BAA6574377C9`.
- Its Git blob hash is `0452f77b9192332eb2721426dc3b67265da12f60`, exactly
  matching [`releases/mobile-ffmpeg.aar`](https://github.com/nikita36078/mobile-ffmpeg/blob/963b4eea110250811f4f4fc7d59c1a86d9dd6b2c/releases/mobile-ffmpeg.aar)
  at that commit. The local Maven artifact is thus identified against the
  upstream prebuilt, not merely a similarly named download.
- [`jitpack.yml`](https://github.com/nikita36078/mobile-ffmpeg/blob/963b4eea110250811f4f4fc7d59c1a86d9dd6b2c/jitpack.yml)
  installs this prebuilt rather than compiling it. Its embedded Maven version
  argument says `4.3.3-compact`, despite the selected `v4.3.2-compact` tag.
  Use commit and hashes, not that argument, to identify the artifact.
- The arm64 and x86_64 `libavcodec.so` strings identify FFmpeg
  `v4.3-dev-2955` and LGPL version 3 or later. Both contain configuration flags
  enabling version 3, shared libraries, file input, WAV output, MMF/WAV input,
  IMA WAV/Yamaha ADPCM decoding, PCM U8 encoding and audio resampling.
  Neither inspected configuration contains `--enable-gpl` or `--enable-nonfree`.
- These codec switches match the pinned
  [`build/android-ffmpeg.sh`](https://github.com/nikita36078/mobile-ffmpeg/blob/963b4eea110250811f4f4fc7d59c1a86d9dd6b2c/build/android-ffmpeg.sh).
  The repository includes an FFmpeg source tree with Git tree ID
  `81bf62d59e72908f9c3f63b92fb795af94812f4f` under `src/ffmpeg`.
- The pinned [README license section](https://github.com/nikita36078/mobile-ffmpeg/tree/963b4eea110250811f4f4fc7d59c1a86d9dd6b2c#8-license)
  describes LGPLv3 as the default and GPL-enabled alternatives. The repository
  contains both license texts. The AAR itself contains Java classes and native
  libraries, but no license/source-notice file; AbyssME's inherited HTML list
  currently gives only a short MobileFFmpeg credit.

This narrows the previously unknown provenance. It does **not** establish that
every shipped native library was reproducibly rebuilt from that source tree,
or complete the corresponding-source/notice work for a new distribution.
Preserve the exact source, patches, build scripts/toolchain details and license
texts in the release preparation; verify all shipped ABIs and native libraries.
Do not silently replace this compact build with a different codec bundle.

## Candidate Native Surface

The inspected Handheld research APK is
`61F5248193672955C84540D455BE857C7B5C8712495A7ECCAC8F414AD14C67F4`.
Its arm64 native library list is:

```text
libabyssm3graster.so
libavcodec.so
libavdevice.so
libavfilter.so
libavformat.so
libavutil.so
libcpufeatures.so
libimage_processing_util_jni.so
libmicro3d.so
libmidi.so
libmobileffmpeg.so
libmobileffmpeg_abidetect.so
libswresample.so
libswscale.so
```

`libabyssm3graster.so` and `libmicro3d.so` are built from repository sources by
`experiments/m3g-host/native/Android.mk`. The compact FFmpeg AAR contributes ten
native library names. MIDI and camera processing binaries remain separate
dependency-inventory items; this is not a complete transitive dependency audit.
`libjavam3g.so` is absent, and the normal verifier also checks obsolete EPL
bindings/tone helper classes. Absence of those entries alone is not clearance.

The APK retains `FREEJ2ME-LICENSE.txt`, `FREEJ2ME-SOURCE.json` and the inherited
`licenses.html`. FreeJ2ME source identity remains pinned by
[`source-lock.json`](../m3g-probe/source-lock.json). The earlier external atan2
snippet is replaced in candidate preparation with `Math.atan2`; the old
readiness report's uncertainty about that helper is historical, not a new
finding.

Private Wolphun source/notices, the final production build layout, the complete
dependency inventory and signed upgrade/save checks still precede release.
