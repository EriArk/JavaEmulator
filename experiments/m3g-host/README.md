# M3G in the Real AbyssME Host

Branch-only research, not an end-user engine option. The ordinary app build and
`main` retain the existing M3G engine. No release APK, license change, Mophun or
Symbian integration is part of this experiment.

## Build

Use the repository wrapper, JDK 21, SDK 34 and NDK 22.1.7171670. Prepare the
clean, pinned FreeJ2ME-Plus checkout described in the
[standalone probe](../m3g-probe/README.md). From the worktree root:

```powershell
.\gradlew.bat :app:assembleFdroidDebug :app:assembleFdroidDebugAndroidTest -Pm3gExperiment=candidate -Pfreej2meSource=C:/absolute/path/to/freej2me-plus
.\experiments\m3g-host\verify.ps1 -Serial YOUR_DEVICE -Engine candidate
.\gradlew.bat :app:assembleFdroidDebug :app:assembleFdroidDebugAndroidTest -Pm3gExperiment=baseline
.\experiments\m3g-host\verify.ps1 -Serial YOUR_DEVICE -Engine baseline
```

Set `ANDROID_HOME` or pass `-Adb` to the verifier. Only `fdroidDebug` is enabled
in research mode; private signing configuration is not required. Build outputs
are separated under `app/build/m3g-candidate` and `app/build/m3g-baseline`.
Both support arm64-v8a and x86_64. Application IDs are isolated:

- `io.github.eriark.abyssme.m3gcandidate.debug`
- `io.github.eriark.abyssme.m3gbaseline.debug`

They cannot update ordinary AbyssME packages and have independent libraries,
profiles and saves. The verifier checks package metadata, engine isolation and
notices before installing; it does not uninstall or erase user data. ADB calls
are bounded and a missing/failed test report is not accepted as a pass.

Do not run the baseline on an emulator as an unattended acceptance step: the
earlier standalone native-engine probe stalled/exited that target. This remains
unresolved. The real-host baseline was tested on Retroid only.

## Adapter

The candidate uses the same revision-guarded, generated FreeJ2ME M3G package as
the standalone probe. Its notices and GPL text are retained; no upstream engine
source or game archive is committed. The shared preparation still applies the
AWT removal and multi-section loader fix. The original checkout is untouched.

Additional exact-match guarded changes connect `Graphics3D` to this host:

- On binding real LCDUI `Graphics`, copy its bitmap into a reusable Android
  scratch buffer; on release, flush it back. This preserves 2D-before-3D and
  2D-after-3D paint order, translation and clipping.
- Keep that scratch buffer separate from an `Image2D` render target's storage.
  Reusing the renderer's current raster array corrupts an equal-sized texture.
- Resolve resources through the active `AppClassLoader`/`ContextHolder` and
  reset the singleton through the existing `MicroLoader` initialization hook.
- Debug wireframe is disabled and explicitly unsupported. Renderer flags are
  fixed in the research adapter, not exposed as new user settings.

The candidate excludes the original EPL Java bindings and `libjavam3g.so`; it
still builds the unrelated existing `micro3d` library. The baseline uses the
existing M3G Java/native sources without modification. No original LCDUI,
loader, compatibility heuristic, game JAR or native engine source is patched.

This adapter copies a framebuffer in both directions on each binding cycle.
Its sustained CPU/memory/frame-time cost is **not measured yet**. This is not
zero-copy rendering, full JSR-184 conformance, or publication-license clearance.
No combined binary is distributed here; any later distribution requires review
of the complete artifact and applicable source/notice obligations.

## Checks

`M3gHostTest` is compiled into both research test APKs and exercises the real
AbyssME LCDUI/resource host:

1. Preserve 2D background, translated clip and later HUD drawing.
2. Switch and resize Graphics targets across 20 dimensions without stale pixels.
3. Reset renderer state between sessions.
4. Release an Image2D target without writing the previous Graphics bitmap.
5. Load an original PNG through the active MIDlet archive class loader.
6. Bind an equal-sized Graphics target without overwriting an Image2D texture.

The sixth test demonstrably fails with the earlier shared-buffer adapter and
passes with the separate scratch buffer. These are focused integration checks,
not the complete existing app test suite or physical controller acceptance.

[Dated device results, game variants and screenshots](../../docs/m3g-host-2026-10-11/README.md).
