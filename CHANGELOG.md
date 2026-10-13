# AbyssME Changelog

User-facing development history, reconstructed from commits, release notes and
local/private build records through **2026-10-13**. This is not a claim that all
listed work is in a public APK. See [current status](docs/PROJECT_STATUS.md) for
availability, verification, known limits and screenshot guidance.

Labels: **Public source** means implemented on `main` but unreleased unless
stated otherwise. **Experimental** means local/private combined builds only.
**Released** means an actual GitHub release asset.

## 2026-10-13

- **Public source / unreleased:** screen preparation no longer mistakes anonymous
  square textures for a phone display. This fixes the cause of PGR 3D's cropped
  128x128 menus on fresh preparation, without a game-specific override or changing
  existing settings. Four new regression cases; 24 compatibility checks pass on
  Retroid and 24 on the Phone emulator. Both ordinary APK variants build and
  14 fresh JVM cases pass per variant.
- **Separate experiment / not shipped:** compared DiRT 3D and PGR 3D gameplay
  against the original engine on Retroid. Both sides sustain about 16.6 and 13.3
  surface buffers/host updates per second respectively. PGR remains more costly
  to render, but these short races show no sustained update-rate deficit. Not
  unique-frame, physical-controller, audio or full-game acceptance. Mophun uses a
  different renderer and was excluded. No APK distribution or engine/license
  switch. [Captures and limits](https://github.com/EriArk/JavaEmulator/blob/codex/m3g-replacement/experiments/m3g-host/racing-20261013.md).

- **Separate experiment / not shipped:** specialized GPU programs by texture
  count and sprite/mesh path, reduced readback to color-writing rectangles and
  packed sprite lookup data in bulk. Same-APK Retroid comparison cuts flat/
  two-sided lighting costs by 22-34% and sprites by 9-14%; all 34 pixel CRCs
  match. Retroid passes 370 checks; Phone emulator passes 22 focused checks.
  Original speed parity remains open. No production switch, license change
  or APK distribution. [Changes and evidence](https://github.com/EriArk/JavaEmulator/blob/codex/m3g-replacement/experiments/m3g-host/shaders-20261013.md).

- **Separate experiment / not shipped:** reduced LCDUI framebuffer copying,
  added an opaque-bitmap NDK read path, whole-frame GPU submission, hardware
  clears and conservative mesh rejection. Fixed depth offsets, write-only depth
  passes, render-target validation and RGB888 quality hints after original-engine
  review. Retroid synthetic costs fall 17-34%; all 34 before/after pixel CRCs
  match. Final Retroid suite passes 361 checks and Phone focused suite passes 36;
  the full Phone attempt timed out. Fresh Deep and Asphalt gameplay captures
  are recorded, including Asphalt lap progression. Original speed parity is
  still pending. No engine/license switch or APK distribution.
  [Implementation and evidence](https://github.com/EriArk/JavaEmulator/blob/codex/m3g-replacement/experiments/m3g-host/throughput-20261013.md).

- **Public source / unreleased:** new game profiles now choose font sizes from
  the prepared virtual resolution instead of using 240x320 sizes everywhere.
  Advanced adds an Automatic font preset; saved explicit sizes stay unchanged.
  Constructo's small-screen tutorial is readable on Retroid. Four Android font
  tests pass on Retroid and Phone emulator; ordinary Handheld/Phone debug builds
  and 14 unit tests per variant pass. No APK distribution in this batch.
- **Separate experiment / not shipped:** fixed GPU diagnostics accessing an
  uninitialized Activity and enabled cadence capture from non-debuggable research
  APKs. Compared actual Retroid gameplay with the original engine across Asphalt,
  Constructo and Deep Java. Six short captures maintain about 60 surface buffers/s
  without >100 ms presentation gaps; this is not unique game FPS or speed parity.
  Scene differences, lower candidate host throughput and thermal-data limits
  remain explicit. Final Retroid measured suite passes 332 checks; focused tests
  pass 31 on Retroid and 31 on Phone emulator. No engine/license switch or release.
  [Implementation and evidence](https://github.com/EriArk/JavaEmulator/blob/01a8837b/experiments/m3g-host/gameplay-20261013.md).

- **Separate experiment / not shipped:** moved flat/two-sided lighting to
  indexed GPU geometry, corrected unlit flat colors and portable front/back
  selection, reduced repeated shader state updates and fixed mesh-cache churn.
  Split ordinary/extended shaders after detecting extra overhead. Retroid
  passes 325 checks; Phone emulator passes 14 focused checks, not the full suite.
  Synthetic flat/two-sided lighting cost is roughly halved; ordinary-scene
  timing and pixel-rounding limits remain explicit. Fresh Asphalt, Constructo
  and Tower Bloxx captures are recorded, with a final Asphalt recheck. No
  production engine/license switch or public APK.
  [Implementation, measurements and evidence](https://github.com/EriArk/JavaEmulator/blob/64c4f866/experiments/m3g-host/lighting-20261013.md).
- **Separate experiment / not shipped:** added compact indexed mesh buffers
  retained in GPU memory, bounded cache eviction and mutation/context-loss
  recovery. Four-texture meshes can now use hardware geometry. Retroid and Phone
  emulator each pass 311 checks; all 30 before/after fixture images match.
  Repeated dense-lighting scenes cost 14-15% less and multi-object scenes 6-8%
  less than the previous GPU checkpoint. Cold fallback costs and an unstable
  long benchmark run are documented, not presented as universal acceleration.
  Fresh Asphalt, Constructo and Tower Bloxx captures are included. No production
  engine/license switch or public APK.
  [Implementation and evidence](https://github.com/EriArk/JavaEmulator/blob/0697d76c/experiments/m3g-host/indexed-20261013.md).
- **Separate experiment / not shipped:** moved supported mesh transforms,
  clipping and lighting to the GPU, added a bounded immutable geometry cache
  and removed depth readback at target release while preserving CPU fallback
  barriers. Retroid and Phone emulator each pass 301 checks. Repeated synthetic
  dense-lighting scenes cost 60-66% less, multi-object/material scenes 23-29%
  less and sprites 14-17% less. Fresh Asphalt, Constructo and Tower Bloxx gameplay
  captures are included. Hardware geometry stays opt-in; pixel-rounding
  differences, unsupported states and the remaining native performance gap are
  explicit. No production engine/license switch or public APK.
  [Architecture, checks and screenshots](https://github.com/EriArk/JavaEmulator/blob/0dbea9db/experiments/m3g-host/hardware-20261013.md).
- **Separate experiment / not shipped:** reduced CPU overhead of GPU submission
  and fixed a mixed-material color-write regression found on Retroid. Repeated
  geometry/many-draw fixtures cost about 14-21% less; all 30 image checksums are
  unchanged. Portrait sprites remain slower, so universal speedup or parity with
  the original engine is not claimed. Retroid and Phone emulator each pass 262
  checks. Fresh Constructo Combat and Tower Bloxx gameplay captures and pinned
  audio dependency provenance are recorded. No production engine/license switch
  or new public APK.
  [Implementation and evidence](https://github.com/EriArk/JavaEmulator/blob/81c8b2d8/experiments/m3g-host/submission-20261013.md).
- **Separate experiment / not shipped:** fixed disappearing objects in large 3D
  scenes, camera ranges, spotlight behavior, transparency and CPU/GPU edge
  sampling. Hardened model-file parsing and nested resource loading. Retroid
  and Phone emulator each pass all 260 checks; 70 standalone models/resources
  from eight private games load. Fresh Asphalt, DiRT and Deep gameplay captures
  are recorded. This is not full-game or performance parity; GPU stays opt-in,
  and the production engine, license and public APKs are unchanged.
  [Implementation, native differences and evidence](https://github.com/EriArk/JavaEmulator/blob/a9f51dbe/experiments/m3g-host/scene-contracts-20261013.md).
- **Separate experiment / not shipped:** fixed 3D deformations ignoring changed
  geometry and colors, corrected overlapping bone bindings and lighting on
  scaled skeletons, and rejected overflowing bone ranges. Added loaded-model
  and animated-hierarchy coverage. Final Retroid passes 184 checks; Phone
  emulator passes 106 shared/animation/deformation checks. Fresh Deep:
  Submarine Odyssey Java gameplay screenshots are recorded. Native comparison
  differences and an interrupted crash are explicit; no complete-game parity,
  engine switch, root license change or public APK is claimed.
  [Implementation and verification](https://github.com/EriArk/JavaEmulator/blob/139aa143/experiments/m3g-host/deformation-20261013.md).
- **Separate experiment / not shipped:** corrected smooth 3D animation curves,
  looping transitions, long rotations and blending of simultaneous movements.
  Edited curves refresh correctly; reverse/fractional playback updates safely,
  circular key ranges load, and invalid sequence files are rejected. Final
  Retroid passes 163 checks; Phone passes 85 shared/animation checks. A fresh
  GPU-enabled Retroid build reaches an Asphalt race, with screenshots recorded.
  No full-game/audio/FPS guarantee, production engine switch or release APK.
  [Implementation, native comparison differences and evidence](https://github.com/EriArk/JavaEmulator/blob/beff7fc9/experiments/m3g-host/animation-20261013.md).
- **Public source, unreleased:** ordinary debug builds no longer require a
  private release-signing configuration. Both variants build, pass their JVM
  tests and complete lint without it. Release assembly still requires a real
  signing setup and fails clearly when it is missing. Closed issue #1.
- **Separate experiment / not shipped:** corrected copied 3D scenes losing
  alignment links, animation tracks being cloned instead of shared, and resources
  disappearing from traversal when one of several references is removed. Fixed
  camera validation while preserving camera-less file loading. Retroid passes
  140 checks; Phone passes 139 before the final loader guard and all 62 shared
  checks afterward. Eleven new cases also pass on the original engine.
  [Scope, results and artifact hashes](https://github.com/EriArk/JavaEmulator/blob/68866a93/experiments/m3g-host/scene-copies-20261013.md).
- **Local test preparation only:** stored user-provided N-Gage Asphalt 3 and
  Snakes archives and extracted files outside Git. Neither game was executed or
  bundled; this does not introduce N-Gage support.

- **Separate experiment / not shipped:** reuse GPU textures across material
  switches with bounded memory and revision-aware updates. Increased the number
  of cached small textures after an Asphalt smoke run exposed repeated uploads,
  without raising the byte limit. Retroid's material-switching fixtures cost
  about 10-11% less; all 30 comparison images are unchanged. Final Retroid passes
  129 checks; Phone passes 129 before capacity tuning and five affected checks
  afterward. Fresh Asphalt gameplay and cache counters are recorded, not a
  general FPS or full-game guarantee.
- **Research build preparation:** fetch source from a revision-pinned,
  hash-verified archive instead of requiring a maintainer-local checkout.
  Cached offline builds and invalid-input rejection were checked; source
  identity and license are retained in APK assets. Production engine, root
  license and public APKs are unchanged.
  [Implementation, checks and screenshot](https://github.com/EriArk/JavaEmulator/blob/a7a54bc3/experiments/m3g-host/gpu/README.md).

- **Separate experiment / not shipped:** batched GPU rendering of meshes and
  sprites, reducing repeated context switches and geometry uploads while
  preserving CPU/2D drawing order and context-loss recovery. Retroid's 16-mesh
  benchmark costs 23-26% less than immediate GPU submission; all 28 benchmark
  images are unchanged. Both final variants pass 124 checks without skips.
  Added fresh Retroid screenshots: Asphalt gameplay and Deep's animated menu,
  alongside original-engine references. These are not full-game parity or FPS
  guarantees. GPU remains off by default; main's engine/license and public APKs
  are unchanged.
  [Implementation, measurements and screenshots](https://github.com/EriArk/JavaEmulator/blob/33619e43/experiments/m3g-host/gpu/README.md).

## 2026-10-12

- **Separate experiment / not shipped:** added bounded recovery of pending GPU
  frames after private-context loss, retaining textures, transparency and depth.
  Removed repeated graphics setup and fixed allocation overhead introduced by
  replay storage. Added seven checks for controlled loss, changed draw data,
  memory/draw limits, repeated failure, reset and caller-context preservation.
  Final Handheld and Phone builds each pass 119 checks without skips on Retroid
  and the Android emulator.
  All 28 benchmark images remain unchanged; final multi-mesh cost is within 1%
  of the previous checkpoint in the recorded Retroid comparison. This is not
  a game-FPS or real OS/driver-reset guarantee. GPU stays off by default; no
  main engine/license change or release APK.
  [Implementation and evidence](https://github.com/EriArk/JavaEmulator/blob/a7b5d204/experiments/m3g-host/gpu/README.md).

- **Separate experiment / not shipped:** added GPU Sprite3D and the remaining
  alpha/luminance image formats. Sprites and 3D meshes now share the same frame;
  invisible sprites no longer trigger needless transfers. Preserved exact crop,
  flip and fractional sampling, and transparent pixels do not block later objects.
  Final Retroid/Phone emulator builds each pass 112 checks; all 28 benchmark
  scenes use GPU. Standalone sprites remain slower than CPU, so GPU stays off
  by default. A fresh Deep session reaches a tutorial dialogue over 3D scenery;
  screenshots and limits are recorded. No engine/license switch or release APK.
  [Implementation and evidence](https://github.com/EriArk/JavaEmulator/blob/285d26ce/experiments/m3g-host/gpu/README.md).

- **Separate experiment / not shipped:** moved depth bias and remaining
  framebuffer multiplication modes onto GPU. Fixed needless readbacks for empty
  models and incorrect object ordering after negative CPU depth. Final Retroid
  and Phone emulator builds each pass 104 checks; all 22 earlier benchmark
  images remain unchanged. A fresh Asphalt session confirms thousands of
  GPU-biased draws with visible gameplay. Effects are not uniformly faster than
  CPU in isolation; no full-game/audio acceptance or release is claimed.
  GPU stays off by default and main's engine/license/APKs are unchanged.
  [Implementation, screenshot and remaining limits](https://github.com/EriArk/JavaEmulator/blob/333dd8a0/experiments/m3g-host/gpu/README.md).

- **Separate experiment / not shipped:** added four texture layers, all texture
  blend modes and mipmapping to the experimental GPU renderer. Cached unchanged
  uploads, preserved mutable textures and fixed constant-coordinate mip selection.
  Final Retroid/Phone emulator builds each pass 98 checks. Texture-heavy fixtures
  improve about 1.7-2.9x versus native CPU; some simpler cases remain slower.
  GPU-enabled Asphalt 3 and Deep reach gameplay on Retroid, with real GPU calls
  confirmed and screenshots recorded. This is not full-game/audio/controller
  acceptance; unsupported effects still use CPU. GPU stays off by default and
  main's engine/license/public APKs are unchanged.
  [Implementation, screenshots and limits](https://github.com/EriArk/JavaEmulator/blob/5433c574/experiments/m3g-host/gpu/README.md).

- **Separate experiment / not shipped:** keep consecutive 3D models in one GPU
  frame and synchronize before CPU drawing or screen output. Added GPU alpha/
  additive transparency and linear/exponential fog. Retroid and Phone emulator
  each pass 92 checks. A 16-model fixture falls from 21-22 to 5.4-5.7 ms compared
  with per-model GPU transfers; all 22 residency comparison images are unchanged.
  This is not an equivalent game-FPS gain: some scenes remain slower than CPU.
  Unsupported effects retain CPU fallback; GPU stays off by default. Main's
  engine/license and published APKs are unchanged.
  [Implementation, evidence and remaining limits](https://github.com/EriArk/JavaEmulator/blob/88d48084/experiments/m3g-host/gpu/README.md).

- **Separate experiment / not shipped:** added opt-in GLES3 rendering for lit
  geometry and one texture, with perspective, alpha rejection and shared
  color/depth across GPU/CPU draws. Fixed the initial transfer bottleneck using
  bulk copies and shader packing. Both devices pass 87 checks, including six
  explicit GPU checks. Linear-filter and lit-grid samples improve versus native
  CPU, while simple nearest-filter samples remain slower. Unsupported effects
  fall back unchanged; GPU stays off by default. No production switch, licensing
  transition, new release or real-game GPU acceptance.
  [Implementation, comparison images and measured limits](https://github.com/EriArk/JavaEmulator/blob/5b777ae2/experiments/m3g-host/gpu/README.md).

- **Separate experiment / not shipped:** fixed wrong back-face lighting on
  shared vertices and transforms processing unused buffer tails. Reused lighting
  across a draw, retained sparse texture scratch and skipped unused light setup.
  Both devices pass 81 checks; standalone probe builds; Asphalt 3 reaches a
  moving Retroid race. Twenty cache-on/off scenes match and eighteen older
  images remain unchanged. A new lit grid costs 15-19% less than uncached
  lighting, not a game-FPS or old-engine parity claim. Main's engine, licensing
  and public APKs remain unchanged.
  [Implementation and measured limits](https://github.com/EriArk/JavaEmulator/blob/197f9955/experiments/m3g-host/native/README.md).

- **Separate experiment / not shipped:** replaced dithered mip-level switching
  with real level interpolation, including alpha, in Java and native rendering.
  Corrected detail selection for narrow projections and nearest-level rounding.
  Precomputed native scales reduce the cost of the higher-quality filter.
  Both devices pass 74 checks, all 18 Java/native benchmark scenes match, and
  the standalone probe builds. Filtering costs more than the old approximation;
  no legacy-GLES performance-parity claim, production switch or new release.
  [Implementation and limits](https://github.com/EriArk/JavaEmulator/blob/610302b1/experiments/m3g-host/native/README.md).

- **Separate experiment / not shipped:** accelerated mipmapped textures and
  Sprite3D rendering, retaining the shared-buffer Java fallback. Fixed lost
  fractional texture coordinates, rectangular/NPOT mip scaling and stale levels
  after image changes. Both devices pass 71 checks; all 18 benchmark scenes
  retain exact Java/native pixels. New mipmap/sprite workloads improve about
  1.3x/1.6x versus Java, not a game-FPS or legacy-GLES parity claim. Asphalt 3
  reaches a running Retroid race. Production engine and public APKs unchanged.
  [Implementation and limits](https://github.com/EriArk/JavaEmulator/blob/b624a71e/experiments/m3g-host/native/README.md).

- **Separate experiment / not shipped:** expanded native raster acceleration
  to four independent texture layers, all current texture blends and exponential
  fog. Fixed zero-density fog tinting for models and sprites. Newly accelerated
  benchmark scenes cost about 1.7-1.9x less than Java with identical pixels;
  simple-scene overhead and the legacy-GLES performance gap remain.
  Both devices pass 66 checks; Deep reaches gameplay, turns and resumes on
  Retroid. No production-engine switch or new release APK.
  [Implementation and limits](https://github.com/EriArk/JavaEmulator/blob/3605df3a/experiments/m3g-host/native/README.md).

- **Separate experiment / not shipped:** added native CPU acceleration for the
  alternative M3G renderer, keeping the Java fallback and shared color/depth
  buffers. Fixed independent repeat/clamp texture axes. Same-APK synthetic
  scenes render 1.74-2.02x faster with identical pixels; this is not game FPS
  or parity with the faster legacy GLES engine. Retroid Handheld and Phone
  emulator each pass 63 checks, and Asphalt reaches a moving Retroid race.
  Production M3G, root licensing and release APKs remain unchanged.
  [Implementation and measured limits](https://github.com/EriArk/JavaEmulator/blob/1b302bb9/experiments/m3g-host/native/README.md).

- **Separate experiment / not shipped:** fixed a crash with nonconsecutive M3G
  texture units and replaced the external angle approximation with platform
  math. Added two shared raster regressions, two candidate math checks, and
  repeatable eight-scene performance/image measurements. Final candidate passes
  58 focused checks on Retroid and Phone emulator; native passes 49 shared checks.
  Asphalt reaches a moving race. Matched non-debuggable samples still show a
  software-rendering cost gap; no performance-parity claim, quality reduction,
  production-engine switch, root-license change or release.
  [Evidence and next gate](https://github.com/EriArk/JavaEmulator/blob/codex/m3g-replacement/docs/m3g-performance-2026-10-12/README.md).

## 2026-10-11

- **Separate experiment / not shipped:** fixed the traced Asphalt shadow alpha,
  independent color/alpha write masks and sprite near/far clipping; added
  scene/picking checks and bounded drawing/copy timing. Brought current mainline
  UI/storage work into combined Handheld/Phone research builds. Replaced the
  additional inherited EPL tone compiler with original Apache-2.0 code and
  repaired direct ToneControl playback/completion/validation. Candidate passes
  61 focused checks per device, plus independent MIDI parsing; both combined
  app suites complete with explicit variant skips. Corrected 13 blocking XML
  lint findings without redesigning the UI. Production/license transition and
  public beta remain pending the documented release gates.
  [Evidence and remaining work](https://github.com/EriArk/JavaEmulator/blob/codex/m3g-replacement/docs/m3g-beta-readiness-2026-10-11/README.md).

- **Separate experiment / not shipped:** reproduced and fixed material
  emission/alpha with no lights and fog distance under parallel projection.
  Added regressions and bind-to-release timing for direct-flush games: 32 checks
  pass on both Retroid engines and candidate emulator; standalone candidate
  retains 18/18. Deep provided usable target-session measurements and resumed
  gameplay after Home/Recents. Asphalt still shows a hard black shadow. Source,
  failures and screenshots are on the experiment branch; production engine,
  licensing and release APKs are unchanged.
  [Dated evidence](https://github.com/EriArk/JavaEmulator/blob/044d9a28/docs/m3g-shading-2026-10-11/README.md).
- **Separate experiment / not shipped:** reproduced and fixed bilinear texture
  edge bleeding in the generated alternative M3G renderer. Added eleven raster
  cases to six host checks: 17/17 pass on Retroid for both engines and candidate
  emulator. Completed bounded Deep/DiRT observations, preserving rejected
  timing and unavailable Deep paint-cost data. Deep returns to its own pause
  menu in the same process; gameplay after return remains unverified. Asphalt
  shadow difference is not fixed. Published evidence/screenshots only; main
  engine, licensing and release APKs unchanged.
  [Dated evidence](https://github.com/EriArk/JavaEmulator/blob/86d79068/docs/m3g-filtering-2026-10-11/README.md).
- **Separate experiment / not shipped:** added opt-in host cadence/PSS collection
  with rejected-sample reporting and six parser checks. Both measured engines
  pass six Retroid host checks; candidate also passes on the emulator. One
  matched Asphalt pair completed about 91 seconds per engine; candidate paint
  cost is higher (window-p50 median 8.74 vs 3.66 ms) and shadows/filtering differ.
  Emulator Asphalt survived three resumes and a switch to DiRT reached gameplay;
  emulator surface timing was rejected for a polling gap. Published raw windows,
  hashes and screenshots, not APKs/games. Longer three-title performance and
  replacement acceptance remain open; main engine/licensing unchanged.
  [Dated evidence](https://github.com/EriArk/JavaEmulator/blob/08f38c78/docs/m3g-runtime-2026-10-11/README.md).
- **Separate experiment / not shipped:** connected the alternative M3G renderer
  to the real AbyssME host in isolated candidate/baseline debug builds. Six host
  checks pass on Retroid for both engines and on the emulator for the candidate;
  reproduced and fixed an Image2D/framebuffer ownership regression. Deep,
  Asphalt 3 and DiRT reached gameplay in the fixed Retroid smoke sample; DiRT
  required manual 320x240 instead of its incorrect auto-profile. Published
  eight screenshots, exact variants and build/test records, not game archives
  or APKs. Main engine/licensing unchanged; performance and broad compatibility
  remain open. [Dated evidence](https://github.com/EriArk/JavaEmulator/blob/d91321b0/docs/m3g-host-2026-10-11/README.md).
- **Separate experiment / not shipped:** expanded alternative M3G checks to
  scene loading and animation, with a separate unchanged-current-engine APK.
  Fixed premature multi-section loading in the candidate's generated sources.
  Candidate: 18/18 synthetic checks on Retroid and emulator; current engine:
  10/10 shared cases on Retroid, emulator attempts stalled/exited and remain
  unresolved. Published 17 frame comparisons and before/after reports, not
  game-compatibility claims or release APKs. Main engine/licensing unchanged.
  [Dated evidence](https://github.com/EriArk/JavaEmulator/blob/9e758abe/docs/m3g-scenes-2026-10-11/README.md).
- **Separate experiment / not shipped:** built a standalone Android probe for
  a FreeJ2ME-Plus M3G alternative on `codex/m3g-replacement`. Eight synthetic
  checks passed on Retroid Pocket Classic and an x86_64 emulator; diagnostic
  screenshots and raw reports are published on that branch. Current mainline
  M3G and licensing remain unchanged; no real-game compatibility or replacement
  is claimed. [Branch results](https://github.com/EriArk/JavaEmulator/blob/8d7aafdf/docs/M3G_REPLACEMENT.md).
- **Research / not shipped:** audited EKA2L1 as an embedded Symbian / N-Gage
  engine and documented one-app setup, import, controls and backup goals,
  source findings, licensing/build gates and staged acceptance. No native
  integration, game run or new APK in this batch.
  [Integration plan](docs/SYMBIAN_INTEGRATION.md).
- **Public source / experimental.15:** J2ME Loader whole-library import can
  request access to a blocked nested folder and resume scanning. Wrong-folder
  selection and cancellation are recoverable; save folders are checked and
  the access message remains readable. Source identity survives extra grants.
  [d39ed742](https://github.com/EriArk/JavaEmulator/commit/d39ed742),
  [workflow](docs/LIBRARY_TRANSFER.md).
- **Verification:** the combined Phone/Handheld APKs reported 132 selected
  Android cases OK per variant, subject to variant skips; 14 JVM tests per
  variant. A real J2ME Loader emulator import preserved the two RMS files by
  hash. This does not certify every provider or the reporter's devices.
- **Documentation/media:** published 12 fresh development screenshots covering
  the library, gameplay, mapping, touch layouts and settings, with an unchanged
  PNG pack and capture manifest.
  [2835c239](https://github.com/EriArk/JavaEmulator/commit/2835c239),
  [screenshots](screenshots/post-2026-10-11/README.md).
- **Documentation:** added the shared current-status source for website and
  post authors, including local-only work and evidence boundaries; added this
  historical changelog and an update contract. No new app build in this batch.

## 2026-10-10

- **Public source / experimental.13:** compact installer with scrollable
  details/archive choices, reachable actions and persistent errors. ZIPs
  selected through file pickers are recognized as archives.
  [696da084](https://github.com/EriArk/JavaEmulator/commit/696da084).
- **Public source / experimental.14:** accepting mismatched JAD/JAR metadata
  retains the selected source and reinstall identity instead of failing with
  a null URI. Different aliases for the same Downloads file remain a separate
  [open issue](https://github.com/EriArk/JavaEmulator/issues/20).
  [7ba384b2](https://github.com/EriArk/JavaEmulator/commit/7ba384b2).
- **Integration status:** recorded the positive Wolphun response and requested
  precise permission scope. A later reply reaffirmed GPL 3.0; no combined
  public release was created. [Licensing record](docs/LICENSING.md).

## 2026-10-09

- **Public source / experimental.8:** unified library and in-game Display and
  Controls pages, with Try/Keep/Undo trials and automatic rollback of an
  unconfirmed preview. Expert settings remain separate.
  [5a4bc726](https://github.com/EriArk/JavaEmulator/commit/5a4bc726).
- **Public source / experimental.9:** compact console-style library and mapper
  pages for small handheld screens, more space for games, on-demand actions
  and controller navigation.
  [ba252272](https://github.com/EriArk/JavaEmulator/commit/ba252272).
- **Public source / experimental.10:** library focus fixes, conservative icon
  and cover selection, custom art and explicit legacy-art refresh.
  [17ef6dcf](https://github.com/EriArk/JavaEmulator/commit/17ef6dcf).
- **Public source / experimental.11:** compact Phone library and shared menus
  with reachable Back/Close actions while content scrolls.
  [1ae5503b](https://github.com/EriArk/JavaEmulator/commit/1ae5503b).
- **Public source / experimental.12:** Settings grouped into Library, Playing
  and Support; transfer chooser, selection and results separated for small
  screens. [83b2fc2d](https://github.com/EriArk/JavaEmulator/commit/83b2fc2d).

## 2026-10-08

- **Public source / experimental.5:** menu-based library backup/restore, screenshot actions,
  Phone orientation/contrast fixes and transfer documentation.
  [4f4d1468](https://github.com/EriArk/JavaEmulator/commit/4f4d1468).
- **Experimental.4:** an optional camera button for both engines, including
  non-pausing Mophun frame capture; app-wide visibility and PNG gallery storage.
- **Public source / experimental.6-7:** more conservative first-launch
  preparation. Explicit metadata wins; ambiguous hints, advertisements and
  unusual image dimensions do not force a guessed phone profile. Existing
  settings remain intact.
  [749ea2ed](https://github.com/EriArk/JavaEmulator/commit/749ea2ed),
  [230122df](https://github.com/EriArk/JavaEmulator/commit/230122df).
- **Verification:** a six-game archive sample recorded mixed results, including
  a recurring Ghost Recon black screen. These checks did not produce a broad
  compatibility guarantee. [Smoke matrix](docs/GAME_SMOKE_MATRIX.md).

## 2026-10-07

- **Public source:** graphical per-game controller mapper, named schemes and
  presets, button identification, D-pad/stick diagonal bindings, improved Phone
  numeric/Gamepad controls, live Java image rotation and display adjustments.
  J2ME Loader import and non-destructive migrations were included in this
  accumulated batch.
  [c49fef57](https://github.com/EriArk/JavaEmulator/commit/c49fef57).
- **Experimental, first combined snapshot:** Java and Mophun in the same
  Handheld app, `.mpn` import/library integration, shared mapper, targeted Deep
  Abyss 1.1/1.4 work, game-controlled save/resume, native dialog and scene API
  fixes, and MIDI for the verified 1.4 profile. Gameplay and selected scene
  transitions were observed; next-level completion was not confirmed.
- **Experimental.2:** supplied both Phone and Handheld APKs; live Java image
  rotation and improved touch/display experience. At this snapshot, Mophun
  still required physical controls.
- **Experimental.3:** Mophun gained the shared Phone keypad and virtual Gamepad
  in portrait and landscape, independent touch/physical input ownership and
  saved per-game touch settings.
- **Publication boundary:** Java/UI source was published while optional-engine
  implementation and game fixtures remained ignored locally. Existing licenses
  were retained; no public combined release was made.
  [a0daaf2a](https://github.com/EriArk/JavaEmulator/commit/a0daaf2a).

## Earlier Milestones

- **2026-10-06, public source/docs:** contributor setup, support templates and
  public-repository cleanup; undocumented fixtures replaced. GitHub Actions
  remains intentionally disabled. New public APKs go to Releases, not the Git tree.
- **2026-10-04, media:** published the beta.2 screenshot set and source credits.
- **2026-07-20, released:** [beta.2](docs/releases/v0.1.0-beta.2.md) added a
  separately installable Phone variant with portrait/touch support while
  retaining the controller-first Handheld variant.
- **2026-07-18, released:** [beta.1](docs/releases/v0.1.0-beta.1.md), initial
  AbyssME beta release.
- **Before beta.1, public source:** retro-modern Gallery/List/Grid library,
  separated cover/icon presentation, enlarged icons, fullscreen game chrome,
  installer styling, quick display presets and early compatibility/artwork
  heuristics. These are baseline work, not newly announced October features.

## How to Add an Entry

Use the actual work/release date, a user-visible result, its availability label,
and a commit or evidence reference. Record limitations where they change the
claim. Test counts belong to the specific build and selected suites, not the
whole project. Do not erase earlier failures, invent release dates or rewrite
Git history to make a progress narrative look cleaner.
