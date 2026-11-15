# Tomb Raider: Underworld and Crash Titans checks — 2026-11-15

## Availability

The M3G fix and clearer startup diagnostics are published in both signed
[Beta 8 APKs](../releases/v0.7.0-beta.8-checks.md). Official ARM64 Beta 7 (code 321) reproduces both supplied reports on
Retroid Pocket Classic, Android 14. No complete-game acceptance is claimed.

## Tomb Raider: Underworld 1.7.1

Supplied `tr_underworld_3d_240x320_es.jar`, 1,107,150 bytes, SHA-256
`f6c432d5fe52656c084ac3bc201756607e684407b64ea4daaeafd4b4f1f743ec`.
English language, Adventure, first room: Lara is black with stretched stripes
and fragments of the previous loading screen. Both GLES and software paths
reproduce it. The assembled 256×256 texture atlas contains the correct artwork.

The problem is alpha export to MIDP, not missing texture data. The game draws
RGB textures with a zero incoming alpha using REPLACE. The Android adapter
exported that alpha into its ARGB bitmap, so later MIDP compositing exposed
old 2D pixels through Lara. [JSR-184 Graphics3D, Color buffer](https://nikita36078.github.io/J2ME_Docs/docs/jsr184/javax/microedition/m3g/Graphics3D.html)
specifies RGB targets for MIDP Graphics. The adapter now makes the exported
clip opaque; Image2D RGBA targets bypass this adapter and retain alpha.

A separate Debug package renders Lara normally in the same room and after
held movement/turn inputs. Production snapshot and generator adapter match.
88 Retroid renderer checks pass, including zero-alpha REPLACE, source alpha
blending, clipped output, RGBA target preservation, texture/batch/scene/release
contracts. The new fixture exercises Java, native, GPU prepared geometry and
GPU vertex geometry paths. Temporary texture dumps and backend switches exist
only in the private diagnosis build, not the source change or release candidate.

## Crash Titans 1.3.7

Supplied `crashtitans_sonys700i_v1_3_7_en_fr_de_it_es.jar`, 291,356 bytes,
SHA-256 `fe3144a104677f07514fe63fe75f6cf1e81751c364ed44168a6d4fd6f2855fa1`.
Official Beta 7 shows the reported InvocationTargetException at startup.
Its cause is ArrayIndexOutOfBoundsException: length=348; index=348 in the game's
`f.a`, called by `i.a` and `MainApp`'s constructor.

The unchanged game requests resource 204 from its packed `f` file as an integer
array. Its table gives offsets 262930–263278 (348 bytes), but that entry is a
GMYL layer resource. The integer-array reader interprets its second word as
338 integers and consequently expects 1360 bytes. This is inconsistent game
code/data, not evidence of a failed MIDP API call. A host JDK 21 harness loads
the original unconverted game classes, supplies the original resource table
and bytes, and invokes that reader directly: it throws the same bounds error
without Android or AbyssME. Minimal type stubs are used; no game code/data is
modified. This is a focused reader reproduction, not a full host game run.

A follow-up check on November 15 confirms that every ZIP entry passes its CRC
check and that the host harness loads game classes directly from the supplied
JAR, with no replacement game classes. The same reader failure repeats. The
archive is intact as a ZIP; the problem is its incompatible game code/resources.
Private evidence: `build/checks/2026-11-15-crash-137/host-recheck.txt`.

The emulator now unwraps reflection wrappers in its error dialog so the actual
guest exception is visible. It does not patch this commercial JAR, swallow the
bounds error or claim that this version is playable. Another matching JAR is
needed to test gameplay. The earlier working 1.0.1 sample is a different build.

## Private evidence

Ignored `build/checks/2026-11-15-tomb-underworld/`: package-before.txt,
lara-broken.png, opaque-scene.png, opaque-movement.png, baseline-config.json,
atlas/draw dumps, crash137-cause.txt, renderer-tests.log. Ignored
`build/checks/2026-11-15-crash-137/`: resource-check.json and host-result.txt.
Commercial JARs, extracted resources and bytecode dumps are not published.
