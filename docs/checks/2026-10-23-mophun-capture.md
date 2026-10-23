# Mophun Image Capture - 2026-10-23

## Availability

Unreleased source and locally installed Debug app/test APKs. No public APK, tag,
release asset, Nextcloud upload or GitHub Actions change. The live release list
was not rechecked in this checkpoint. GPU enhancements remain Debug-only,
opt-in and off by default. No Java/Symbian rendering or VM speed changes.

## Changes

- Mophun's per-game Enhancements page adds **Capture image: Enhanced / Original**.
  The choice is drafted until Try, can be kept/undone, and survives profile
  serialization. Existing profiles default to Enhanced; with effects off or an
  unavailable GPU image, capture still uses the completed source snapshot.
- Enhanced exports the full game image at presentation resolution, without
  controls, letterboxing or viewport cropping. The last processed texture is
  redrawn into a temporary FBO, including fused RCAS and any intermediate pixels.
  It never relies on retained swap-chain contents or re-runs the frame clock.
- Original retains source resolution. Screenshots and automatic missing artwork
  respect rotation. Existing Clean / With LCD effect screenshot selection still
  applies LCD decoration separately; automatic artwork does not include it.
- A single readback request is allowed at once. Target size is bounded to
  4,194,304 pixels and 4096 per side, with a two-second completion timeout.
  Context/layout failure, inactive presentation and timeout use a source snapshot
  taken at the shutter. Late results are recycled, with exactly-once completion.
- PNG encoding and MediaStore/AtomicFile writes run on the disk worker. Pause
  queues missing artwork before releasing GL resources; unfinished requests are
  resolved before Activity teardown closes that worker.
- Fix automatic artwork overriding a user-selected icon's database path. Use
  GameArtwork's existing path precedence and avoid creating replacement art when
  user-picked images already exist. Existing images are not overwritten.

An explicit readback can stall the graphics driver; asynchronous dispatch and a
completion timeout do not make glReadPixels cancellable or guarantee zero frame
time impact. No GPU readback is added to ordinary presentation frames.

## Verification

- Debug app and instrumentation APK assembly passed.
- Fresh JVM execution: **71 tests**, zero failures/errors.
- Final lint: **0 errors, 400 warnings**; no new warning count.
- **Retroid Pocket Classic**, `MC94516AQF040305380`: **33/33** selected instrumented
  tests passed in **28.442 s** on the final app/test builds.
- **Android 15 x86_64 emulator**, `emulator-5554`: the same **33/33** passed in
  **271.873 s**. This is functional emulation evidence, not device performance.

The selection includes ten Mophun presentation/capture tests, seven profile
serialization tests, nine settings UI tests, six LCD/screenshot storage tests,
and one optional installed-game capture test. Earlier 31-test passes on both
targets preceded the final artwork-priority fix and added capture checks; the
33-test results above supersede them.

Pixel tests compare exported FSR/sharpened pixels against PixelCopy of the actual
surface at all four rotations, within two channel levels. A deterministic
intermediate frame remains identical across repeated capture, with unchanged
clock position, frame identity and history. Pause/resume, duplicate requests,
timeout/late work, source snapshot coherence, Canvas fallback, load protection,
per-game choice serialization and Try/Undo are covered.

The optional test uses the already-installed **Deep Abyss 1.4 multiscreen** fixture.
It exports enhanced/source frames, invokes the real screenshot action and decodes
the completed MediaStore PNG. It checks the requested 90-degree rotation, then
pauses the Activity to create missing artwork in an isolated temporary catalog.
It verifies source-sized artwork and custom icon/cover preservation. It restores
the original settings/catalog references, deletes its own MediaStore image and
temporary catalog, and retains named diagnostic PNGs in the Debug app files.
No game data is included in the test source.

Retroid captures visually show the Deep Abyss menu: source **128 x 160**, enhanced
**493 x 616**, rotated gallery/artwork **616 x 493**, without touch controls. The
90-degree picture is intentional test configuration, not a discovered rotation
bug. Emulator enhanced output was also visually inspected. This checkpoint does
not establish gameplay smoothness, physical-controller input, audio, complete-game
compatibility or capture latency under sustained GPU load.

## Local Evidence

- `build/mophun-capture-final-build.txt`: app/test assembly and lint.
- `build/mophun-capture-test-build.txt`: final instrumentation assembly.
- `build/mophun-capture-final-checks.txt`: fresh JVM run and final lint.
- `build/mophun-capture-retroid-final.txt` / `build/mophun-capture-emulator-final.txt`.
- `build/mophun-capture-retroid-{source,enhanced,gallery,artwork}.png`.
- `build/mophun-capture-emulator-{enhanced,gallery}.png`.

App: `app/build/outputs/apk/fdroid/debug/AbyssME-0.4.0-beta.3-r1-debug.apk`

SHA-256: `56c4161922cf11bcce41d856c2aa546cb4ab7920bb1f496368e532a82fc7ad95`

Tests: `app/build/outputs/apk/androidTest/fdroid/debug/app-fdroid-debug-androidTest.apk`

SHA-256: `7997854d671363bc85d7418f96725ba35929cd52a431a6552c00b7d4da72a0c7`

These ignored logs, APKs and game-image captures are local evidence, not public
download artifacts. The installed Debug app is separate from the signed release.
