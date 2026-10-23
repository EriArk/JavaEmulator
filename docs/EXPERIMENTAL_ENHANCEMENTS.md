# Experimental Rendering and CPU Budget

These are opt-in **Debug-only** development features, not part of the published
0.4.0-beta.3-r1 APK. Release builds hide the page and ignore imported experimental
image/CPU settings. Existing profiles retain their original behavior.

Open **Game menu > Display > Enhancements**. Changes use the existing per-game
Try / Keep / Undo workflow and profile backup serialization.

## Platform Scope

| Setting | Current implementation | Not a promise of |
| --- | --- | --- |
| CPU budget, 100/150/200/300% | Mophun: 50,000/75,000/100,000/150,000 VM instructions per scheduling slice | A calibrated phone CPU frequency or a proportional performance increase |
| FSR 1.0 + sharpness | Java Canvas/GameCanvas presentation using OpenGL ES, without a custom shader | More guest FPS, reconstructed detail, or better pixel art in every game |
| Frame interpolation | Java OpenGL ES: bounded image-based motion estimation and intermediate presentation frames | Faster game logic/input, universal 60 FPS, FSR 3 or AI frame generation |

Java games run as converted DEX through Android ART. There is no emulated Java
phone CPU clock to increase. Mophun's budget reduces instruction-slice yielding;
its SDK clock, sleeps, audio clock and frame limit remain separate. Games that
time themselves by busy loops can nevertheless change speed. Increasing this
budget can increase CPU use, battery use and heat. The default remains 100%.
No Symbian CPU setting or Mophun/Symbian GPU enhancement path is added here.

## FSR Implementation and Provenance

The EASU edge-adaptive upscale and RCAS sharpening equations are adapted from
[AMD FidelityFX FSR 1.0](https://github.com/GPUOpen-Effects/FidelityFX-FSR/tree/a21ffb8f6c13233ba336352bdff293894c706575),
specifically `ffx-fsr/ffx_fsr1.h` at that revision. The unmodified
[AMD MIT notice](../app/src/main/assets/licenses/FSR-MIT.txt) ships in APK assets.
The two shader files retain AMD attribution and the pinned revision.

This GLES2 scalar-sampling port uses ordinary texture fetches instead of gather,
precise division instead of the upstream approximate reciprocals, epsilon
guards for flat black/white areas and clamped viewport sampling. RCAS denoising
is not enabled. The UI maps sharpness 0-100% to a 0-1 RCAS strength multiplier;
0% removes sharpening, not the EASU upscale. This is not a bit-exact validation
against AMD's reference GPU implementation or an AMD-certified integration.

FSR runs before host rotation. It excludes padded backing pixels and bypasses
upscaling unless the requested output is larger than the guest viewport in at
least one axis and no smaller in the other. At native size, only optional RCAS
sharpening runs; native size with 0% sharpness preserves the original pixels.
Zero sharpness bypasses RCAS, including during upscaling.
LCD presentation remains a separate overlay. In-app screenshot capture retains
its existing clean/LCD behavior; it does not capture these GPU passes.

## Interpolation and Smoothness

The prototype estimates backward motion in 8-pixel blocks, searching up to
four guest pixels in either direction with a nine-sample cross at pixel centers.
It warps the previous/current images
and rejects uncertain matches or disagreeing pixels to the current real frame.
Unchanged pixels retain their real coordinates to protect static HUD/text;
out-of-bounds warps and disagreeing color channels fall back to the current pixel.
This can still produce block boundaries, incorrect motion and occlusion artifacts.
Fast scrolling beyond the search radius and fine repeating patterns are limits.

Presentation waits behind the newest guest image by up to one measured source
interval, capped at 33.3 ms. Only intervals from 20 to 200 ms are interpolated;
startup, long pauses and already-fast streams fall back to real frames. This
delay can be undesirable in action games. Slower streams finish interpolation
early and hold the current frame: this does not produce evenly spaced 60 FPS.
The cap is an algorithmic presentation window, not measured end-to-end latency.

Intermediate redraws follow Android animation/vsync callbacks. They stop when
the interpolation finishes or the surface pauses. The FPS counter counts new
guest submissions, not synthetic redraws. Unchanged completed output is cached,
so an idle redraw does not repeat the upscale passes. Guest clocks, input and
game logic do not advance during a synthetic redraw.

Static motion blocks skip the displacement search; the current template is
sampled once per block. Streams outside the interpolation interval skip motion
estimation entirely. Finished frames skip the resolve pass, and uniform locations
are cached. These optimizations do not eliminate FSR cost on each new guest frame.

## Resource and Failure Boundaries

- Both image effects default off, independently, for every game.
- No renderer is silently changed and custom shaders are not combined with them.
- Two source textures and reusable intermediate targets are owned by the GL thread.
- Source allocation is bounded to 1,048,576 pixels; output to 4,194,304 pixels.
- Resize, interpolation toggles, pause and context recreation reset history.
- Shader/FBO/GL failure restores the original rendering path with a visible notice.
- Turning both effects off clears the failure latch for a later explicit retry.

This is a prototype boundary, not a recommended automatic profile. Visual quality,
end-to-end latency, thermal behavior and sustained game performance need more
work before automatic enablement or a release claim.
