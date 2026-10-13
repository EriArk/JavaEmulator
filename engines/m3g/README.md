# Production M3G Runtime

The normal app build uses this checked-in source snapshot. No network fetch,
external checkout, research switch or legacy renderer is required to compile it.
GLES3 geometry/rasterization and the native fallback are enabled. Unsupported
GPU paths retain the existing software fallback; this is not the removed Nokia
engine. The separate Mascot Capsule implementation remains unchanged.

`assets/FREEJ2ME-SOURCE.json` identifies the upstream archive and license.
The Java/C++ files retain their individual terms. GPL-3.0-or-later covers the
FreeJ2ME-derived renderer; the independently written tone compiler is Apache-2.0.
All local modifications are supplied as source in this snapshot and in the
revision-guarded generator under `experiments/m3g-probe` and `experiments/m3g-host`.

To verify the snapshot against its generator:

```powershell
./engines/m3g/refresh.ps1 -Check -Offline
```

Omit `-Offline` for the first pinned upstream download. Omit `-Check` only when
intentionally regenerating after renderer changes. Refresh checks reject timing
and tracing hooks. `-Pm3gProductionTests=true` adds the renderer tests to the
normal debug test APK without changing the app's runtime sources or flags.

The legacy M3G bindings/native implementation and EPL tone compiler have been
removed, not relicensed. Original-engine comparisons must use a separate checkout
at commit `05562126`; historical reports still describe those earlier builds.
