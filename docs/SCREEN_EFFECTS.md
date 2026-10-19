# LCD Screen Effects

Development after 0.4.0 Beta 2. Not included in the published Beta 2 APK.

Open **Game menu > Display > Screen effect** (also available in the library's
per-game Display settings). Choose **Off**, **LCD Soft** or **LCD Classic** and
adjust **Effect intensity**. Select **Try changes**, then **Keep** or **Undo**.
The effect and intensity are saved per game. Existing profiles default to Off;
turning the intensity down to zero also leaves the image unchanged.

Soft adds a faint pixel grid and mild contrast reduction. Classic uses stronger
pixel gaps and a slightly more muted image. Neither mode adds CRT scanlines,
curvature, ghosting or frame interpolation. Smoothing remains a separate option.

The shared effect is an original cached Android line-grid optical mask, composited
over the game picture. It is not a RetroArch preset importer or a multi-pass
GLSL pipeline. Gaps snap to scaled guest-pixel boundaries; crossings have a
consistent darker accent, without periodic bands. Simple batched lines avoid
GPU tessellation of a complex intersecting path. The earlier repeating
texture was replaced because minification produced uneven bands at fractional
scales. No guest-frame download, previous-frame history or full-screen temporary
image is required for live presentation. This works
with Android 10's existing Canvas/GPU presentation without changing emulation.

The grid follows guest pixels through scaling and rotation. It fades out between
2x and 4x enlargement to avoid harsh patterns when there are too few physical
pixels for a cell. The subtle contrast treatment remains at smaller scales.
The image is clipped to the game area; letterboxing, touch buttons and menus
are not filtered. The existing render backend and advanced Java shader selection
are preserved, rather than forcing a renderer change.

## Platform Integration

- Java Canvas games: a cached layer above the game surface, below soft keys and
  controls. Both OpenGL and Canvas presentation share this layer. Android LCDUI
  forms are not rendered as LCD pixels.
- Mophun: mask draws after the game bitmap, before touch controls. Grid dimensions
  use logical phone pixels, independently of internal HD rendering.
- Symbian / N-Gage: native geometry supplies the real guest pixel dimensions and
  picture viewport. The mask follows the TextureView transform. Geometry checks
  are limited to five per second while the effect is active; the layer redraws
  when geometry/settings change, not on every native frame.

## Screenshot Export

**Display > Screenshot image > Clean / With LCD effect** is saved per game and
supports Try / Keep / Undo. Clean is the default. The existing screenshot button
uses the selection without opening another dialog or including app controls.
With LCD effect uses the selected mode/intensity; Off or 0% produces a clean frame.

LCD PNGs enlarge the captured image to at least four pixels per guest pixel when
possible, bounded to four megapixels and 4096 pixels per side. Larger captures
within those bounds are retained. They are presentation exports, not necessarily
the exact live viewport dimensions. Smoothing follows the game setting. Clean
retains the existing capture dimensions. Mophun now also respects image rotation;
Java's explicit advanced raw-capture mode continues to export the unrotated source.
Automatic artwork stays unfiltered. Files are stored in Pictures/AbyssME.

[Verification, performance scope and screenshots](checks/2026-10-19-lcd-effects.md).
[Screenshot export and grid correction](checks/2026-10-19-lcd-export.md).
