# BREW engine feasibility and component licensing

**Status (2026-11-16): experimental investigation only. No BREW runtime is
included in public AbyssME releases.** This is the implementation handoff
for [#56](https://github.com/EriArk/JavaEmulator/issues/56), connected to the
deferred platform epic [#44](https://github.com/EriArk/JavaEmulator/issues/44).

## Verified upstream baseline

- Project: [Melange](https://gitlab.com/usernameak/melange), GitLab project ID
  `29639180`, master at `59b66aebd25fdcc8f422f55b0d56616476073bbf`.
- **Licensing:** The Git tree (256 entries examined on 2026-11-16) contains
  no root or nested LICENSE/COPYING/NOTICE file. README does not grant
  redistribution permission. Public availability is *not* a software license.
  Request explicit permission/compatible licensing before moving Melange code
  into a public branch or distributing derivatives.
- **External binaries:** [Melange CMake](https://gitlab.com/usernameak/melange/-/blob/master/app/src/main/cpp/CMakeLists.txt)
  imports multiple BREW runtime archives such as `brew.fixed.lib`,
  `aee.fixed2.lib` and `aeedialog.fixed.lib`. The checkout does not track
  these objects. It also references BREW SDK includes via `BREWPK`.
  Exact authorship/redistribution rights must be established independently.
- **Architecture:** [Melange Gradle config](https://gitlab.com/usernameak/melange/-/blob/master/app/build.gradle)
  specifies `armeabi-v7a`. It does not demonstrate support for ARM64-only
  Android systems or ARM32 guest-code translation.
- **Independently licensed submodule:** `app/src/main/cpp/appui` references
  [BREW_AppUI](https://github.com/usernameak/BREW_AppUI) whose own LICENSE
  is BSD-3-Clause. Attribution and binary notice conditions apply; it does
  **not** license the remaining Melange core.
- **AbyssME combined app:** GPL-3.0-only (see [LICENSING](LICENSING.md)).
  Reused code must be compatible with that combined distribution.

| Component | Current rights evidence | Preferred action |
| --- | --- | --- |
| Main Melange Java/C/C++ source | No explicit license found | Request permission; use only in authorized local experimentation |
| BREW_AppUI submodule | BSD-3-Clause in its own repository | Reuse with preserved notice if technically helpful |
| `brewrt/*.fixed.lib` and Qualcomm SDK headers | Missing from checkout, redistribution unverified | Do not bundle; replace using licensed/owned implementations |
| Generated/precompiled `libbrewemu.so` from external APKs | Redistribution and source completeness unverified | Never copy into a release |
| Guest ARM32 execution on Android ARM64 | Not solved by Melange's ARMv7-only Gradle build | Separate technical proof of concept |

## Milestones

- [ ] Get an explicit upstream grant for any Melange code proposed for public
      distribution, including right to modify and use in the GPLv3 application.
- [ ] Use the ignored local checkout in `engines/brew/melange-upstream/`
      as a **research baseline**, never as public source while rights are pending.
- [ ] Inventory every imported BREW archive and SDK header with use sites and
      replacement plans; maintain an executable build-gap list.
- [ ] Produce owned/licensed replacements in independent modules with original
      tests, while retaining cleared third-party implementations and notices.
- [ ] Build reproducibly without vendor BREW objects and no hidden APK blobs.
- [ ] Run a freely redistributable BREW fixture through the guest execution
      path and verify screen, keys, audio, storage and exit/relaunch.
- [ ] Validate ARM64-only device support separately; do not promise 64-bit
      compatibility based on an ARMv7 build.
- [ ] Only after a functioning, licensed engine is tested: integrate import,
      library/navigation and per-game controls. No new unsupported tab.

**Release gate:** Any unclear licensing, missing build-time proprietary archive,
or non-reproducible runtime blocks distribution. A prototype/source experiment
is not a release or proof of real-device compatibility.
