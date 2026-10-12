# AbyssME Changelog

User-facing development history, reconstructed from commits, release notes and
local/private build records through **2026-10-12**. This is not a claim that all
listed work is in a public APK. See [current status](docs/PROJECT_STATUS.md) for
availability, verification, known limits and screenshot guidance.

Labels: **Public source** means implemented on `main` but unreleased unless
stated otherwise. **Experimental** means local/private combined builds only.
**Released** means an actual GitHub release asset.

## 2026-10-12

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
