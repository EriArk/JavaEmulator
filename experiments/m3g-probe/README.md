# Isolated Android M3G Probe

Developer test harness, **not an AbyssME replacement build or end-user APK**.
Normal AbyssME Gradle settings do not include this project. Two independently
built APKs exercise the same synthetic scenes:

- `:probe`, `io.github.eriark.m3gprobe`: FreeJ2ME-Plus candidate only, no EPL
  M3G code or native libraries. 18 checks (8 initial checks plus 10 shared cases).
- `:baseline`, `io.github.eriark.m3gprobe.baseline`: existing, tree-hash-pinned
  EPL Java/native engine, no FreeJ2ME-Plus sources. 10 shared cases.

Neither can update an AbyssME app; they have independent data and contain no
games, Mophun or Symbian. `main` retains its current engine unchanged.

## Candidate and Provenance

The first runnable candidate is the Java software M3G renderer from
[FreeJ2ME-Plus](https://github.com/TASEmulators/freej2me-plus/tree/baca76a4dda6f30abac67fb4a3db009eed05f91c/src/javax/microedition/m3g),
pinned at `baca76a4dda6f30abac67fb4a3db009eed05f91c`.
All 33 inspected M3G Java files have GPLv3-or-later notices. This is a scoped
source/header check, not a completed historical provenance or whole-project audit.

No upstream engine files or binaries are checked into this harness. The build
requires a clean checkout at the pinned revision, copies only its M3G Java
package into ignored generated sources and retains the notices. The upstream
GPL text is included as `FREEJ2ME-LICENSE.txt` in the probe's assets. The only
Android adaptation removes one `java.awt.Image` type check unavailable on
Android; the MIDP Image path remains. A second guarded patch corrects the loader's
section loop: consumed bytes must be compared with the header's total file size,
not a shrinking `available()` count. Before the patch, both multi-section scene
cases failed on Retroid and the emulator. Header-bounded reading still stops
before trailing container bytes. The build fails if either exact patch or the
expected revision no longer matches; upstream files remain unmodified.

The tiny `Mobile`, `Graphics`, `Image` and `PlatformGraphics` hosts are test
adapters, **not a complete MIDP implementation**. Android Bitmap supplies PNG
decoding and presentation. Debug wireframe explicitly throws instead of faking
support. No complete game loader, resource sandbox or network loader is provided.
This experiment does not change the repository's root license or clear a future
combined APK. Distribution of a probe binary requires its corresponding source,
adaptation/build materials and notices; no release is made here.

## Build and Run

Use the repository Gradle wrapper, JDK 21, Android SDK 34 and Android 10+.
The candidate needs no NDK; the baseline uses NDK `22.1.7171670` and builds
`javam3g` for arm64-v8a and x86_64 with the existing Android.mk. Neither requires
private signing configuration. From the worktree root:

```powershell
git clone https://github.com/TASEmulators/freej2me-plus.git local-games/freej2me-plus
git -C local-games/freej2me-plus checkout --detach baca76a4dda6f30abac67fb4a3db009eed05f91c
.\gradlew.bat -p experiments/m3g-probe :probe:assembleDebug -Pfreej2meSource=C:/absolute/path/to/local-games/freej2me-plus
.\experiments\m3g-probe\verify.ps1 -Serial emulator-5554
.\gradlew.bat -p experiments/m3g-probe :baseline:assembleDebug
.\experiments\m3g-probe\verify.ps1 -Serial emulator-5554 -Baseline
.\experiments\m3g-probe\compare.ps1 -Serial emulator-5554
```

The verifier installs only the probe, removes its previous report, launches it
and checks the expected suite version/count and every result. JSON evidence,
rendered PNGs and the tested APK hash go under `<module>/build/evidence/<serial>/`.
Failed checks retain their evidence and return a nonzero exit code. ADB commands
have timeouts so a stalled emulator cannot leave the verifier waiting forever.
The on-device report and rendered PNGs are in each probe's own `files/` directory.
No existing AbyssME data is touched. `compare.ps1` reports per-frame RGB error and
geometry overlap, not a conformance/FPS verdict; missing frames stay visible.

## Coverage and Limits

Initial eight candidate checks: inverse transforms, immediate triangles, retained vs
immediate pixel equality, depth occlusion, a textured triangle, translated
clipping, PNG loading through M3G Loader and repeated release/rebind.

Ten identical shared cases: raw and zlib `.m3g` scenes, each in one or multiple
object sections; an embedded scene with byte offset and trailing data; truncated
input rejection; keyframed translation and quaternion rotation; vertex morphing;
single-bone deformation. Successful file loads must contain the expected graph
and render identically to an API-created scene **within that engine**. Animation
tests assert numerical state and movement of visible pixels, not calls alone.

`SceneFixtures.java` creates original minimal files at runtime, using standard
little-endian records, zlib and Adler32. No commercial game assets or third-party
demo files are used. Encoding reference: the
[JSR-184 file-format specification](https://nikita36078.github.io/J2ME_Docs/docs/jsr184/file-format.html).
The existing loader accepting the same bytes provides an independent check,
not proof that either implementation passes the entire specification.

The scene is 160x160. Assertions inspect rendered pixels; a successful method
call alone is not a pass. Check the displayed images too. Per-case elapsed times
include initialization, allocations and cold Java execution; **they are not an
FPS benchmark**. Cross-engine pixels are compared descriptively; real-game
compatibility is not established. The baseline uses minimal test host services,
not the complete AbyssME MIDlet lifecycle.

Pending: broader `.m3g` files/encodings and external references, loaded animation
tracks, multi-bone skinning, lights/fog, transparency, picking, 2D/3D composition in the real host,
lifecycle interruptions, sustained performance and representative games.
Full-class/API compatibility and licensing review remain required before using
this as the AbyssME engine. See [the branch investigation](../../docs/M3G_REPLACEMENT.md).
