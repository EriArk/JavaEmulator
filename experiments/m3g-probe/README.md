# Isolated Android M3G Probe

Developer test harness, **not an AbyssME replacement build or end-user APK**.
It does not include the existing EPL M3G code, JNI bindings, games, Mophun or
Symbian. Normal AbyssME Gradle settings do not include this project. The package
`io.github.eriark.m3gprobe` has its own data and cannot update either AbyssME app.

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
generated source adaptation removes one `java.awt.Image` type check unavailable
on Android; the MIDP Image path remains. The build fails if the exact adaptation
or expected revision no longer matches.

The tiny `Mobile`, `Graphics`, `Image` and `PlatformGraphics` hosts are test
adapters, **not a complete MIDP implementation**. Android Bitmap supplies PNG
decoding and presentation. Debug wireframe explicitly throws instead of faking
support. No complete game loader, resource sandbox or network loader is provided.
This experiment does not change the repository's root license or clear a future
combined APK. Distribution of a probe binary requires its corresponding source,
adaptation/build materials and notices; no release is made here.

## Build and Run

Use the repository Gradle wrapper, JDK 21, Android SDK 34 and Android 10+.
No NDK or private signing configuration is needed. From the worktree root:

```powershell
git clone https://github.com/TASEmulators/freej2me-plus.git local-games/freej2me-plus
git -C local-games/freej2me-plus checkout --detach baca76a4dda6f30abac67fb4a3db009eed05f91c
.\gradlew.bat -p experiments/m3g-probe :probe:assembleDebug -Pfreej2meSource=C:/absolute/path/to/local-games/freej2me-plus
.\experiments\m3g-probe\verify.ps1 -Serial emulator-5554
```

The verifier installs only the probe, removes its previous report, launches it
and checks all eight results. JSON evidence and the tested APK hash go under
`probe/build/evidence/<serial>/`. The on-device report and rendered PNGs are in
the probe's own `files/` directory. No existing AbyssME data is touched.

## Coverage and Limits

Eight synthetic checks: inverse transforms, immediate triangles, retained vs
immediate pixel equality, depth occlusion, a textured triangle, translated
clipping, PNG loading through M3G Loader and repeated release/rebind.

The scene is 160x160. Assertions inspect rendered pixels; a successful method
call alone is not a pass. Check the displayed images too. Per-case elapsed times
include initialization, allocations and cold Java execution; **they are not an
FPS benchmark**. Pixel parity between candidates and real games is not established.

Pending: `.m3g` scene loading and external references, animation, skinning,
morphing, lights/fog, transparency, picking, 2D/3D composition in the real host,
lifecycle interruptions, sustained performance and representative games.
Full-class/API compatibility and licensing review remain required before using
this as the AbyssME engine. See [the branch investigation](../../docs/M3G_REPLACEMENT.md).
