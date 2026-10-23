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

EASU reuses four column and four row sample coordinates. On hardware renderers,
it also skips the outer eight samples and filter arithmetic when all four
nearest RGB values are exactly equal: the final deringing clamp already fixes
the answer to that color. No tolerance, palette quantization or reduced resolution
is used. Software renderers (SwiftShader, llvmpipe, softpipe and Software
Rasterizer), or a missing renderer string, retain branchless evaluation with the
same equations; per-fragment control flow regressed SwiftShader in local checks.
The variant is selected once when compiling the shader, not by game title or a
per-frame CPU image scan. Dense images may still pay a small branch cost on
hardware. See [quality and cost evidence](checks/2026-10-23-easu-flat-regions.md).

EASU runs before host rotation. RCAS uses guest-oriented coordinates in the final
presentation draw, including rotation. The retained texture-output path remains
the reference for GPU comparisons. The Canvas path avoids a separate full-output
RCAS texture and its subsequent copy. It excludes padded backing pixels and bypasses
upscaling unless the requested output is larger than the guest viewport in at
least one axis and no smaller in the other. At native size, only optional RCAS
sharpening runs; native size with 0% sharpness preserves the original pixels.
Zero sharpness bypasses RCAS, including during upscaling.
LCD presentation remains a separate overlay. GLES game-image screenshots include
the active GPU presentation; the clean/LCD choice controls the extra LCD effect.
Raw-image capture bypasses these GPU passes.

## Interpolation and Smoothness

The prototype estimates backward motion in 8-pixel blocks, searching up to
four guest pixels in either direction with a 3x3 patch at pixel centers.
It warps the previous/current images
and rejects uncertain matches or disagreeing pixels to the current real frame.
Unchanged pixels retain their real coordinates to protect static HUD/text;
out-of-bounds warps and disagreeing color channels fall back to the current pixel.
Matching patches cannot extend beyond the guest viewport. Both full-motion
endpoints must agree photometrically before intermediate warping, so a borrowed
neighboring vector cannot erase a newly appeared pixel or refill a removed one
with unrelated background. This is not forward/backward optical-flow estimation.

A one-pixel GPU pass samples 16 positions in the flow residual once per source
pair. If at least 12 have mean RGB mismatch >= 0.12, resolve uses the real current
frame throughout that transition. This coarse scene-cut guard prevents local
accidental matches from surviving a broad image change; it is not a complete
scene classifier. It adds no CPU readback or guest-buffer lock. See
[artifact regression checks](checks/2026-10-23-motion-guards.md).

This can still produce block boundaries, incorrect motion and occlusion artifacts.
Fast scrolling beyond the search radius and fine repeating patterns are limits.
The conservative HUD/endpoint guards can leave thin objects on unchanged
backgrounds and edge regions unsmoothed. Cuts with extensive shared imagery can
escape the coarse guard; difficult motion falls back rather than inventing detail.

Presentation follows guest-frame completion timestamps, captured alongside the
pixels and serial under the same buffer lock. Delayed texture upload does not
start another full interpolation window. The window is one measured source
interval, capped at 50 ms: about 33.3 ms at 30 FPS, 40 ms at 25 FPS and 50 ms at
20 FPS. This replaces the previous fixed 33.3 ms cap, which accelerated a slow
pair then held it. Slower streams skip the overdue part of the interval instead
of compressing its whole motion into a faster animation. Draw timestamps cannot
rewind an already presented point in the current timeline.

Only source intervals from 20 to 200 ms are eligible; startup, long pauses and
already-fast streams fall back to real frames. The delay can be undesirable in
action games, and 20 FPS now trades up to 16.7 ms more presentation delay for
steadier interpolation. Stable 20/25/30 FPS timestamps progress evenly in the
tested model, but variable cadence, missed frames, integer pixel sampling and
conservative motion rejection can still produce holds or jumps. There is no
extrapolation or extra frame queue. The cap is an algorithmic presentation
window, not measured end-to-end/input-to-photon latency; GPU/host stalls can add
more. See [cadence and timestamp checks](checks/2026-10-23-interpolation-cadence.md).

Intermediate redraws follow Android animation/vsync callbacks. They stop when
the interpolation finishes or the surface pauses. The FPS counter counts new
guest submissions, not synthetic redraws. Unchanged completed output is cached,
so an idle redraw does not repeat EASU or motion processing. RCAS still runs in
the final draw when sharpening is enabled; the swapped screen buffer cannot be
assumed to retain its previous contents. Guest clocks, input and
game logic do not advance during a synthetic redraw.

Static motion blocks skip the displacement search; the current template is
sampled once per block. Streams outside the interpolation interval skip motion
estimation entirely. Finished frames skip the resolve pass, and uniform locations
are cached. These optimizations do not eliminate FSR cost on each new guest frame.

The enhanced Canvas path locks guest pixels only while uploading the source
texture and capturing geometry. Shader compilation, motion search, upscale,
sharpening and final presentation run afterward using GPU-owned pixels and a
GL-thread-owned vertex snapshot. No extra CPU frame copy or unbounded queue is
introduced. Texture uploads can still stall on the driver; this is not a claim
of completely asynchronous or zero-wait rendering. The non-enhanced renderer
retains its existing upload/draw synchronization.

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
