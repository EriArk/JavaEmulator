# BREW research engine (not shipped)

This is an **isolated experimental workspace** for a future Qualcomm BREW engine.
It is not integrated into the AbyssME library, included in APKs, or supported for
gameplay. See [issue #56](https://github.com/EriArk/JavaEmulator/issues/56) and
[the feasibility dossier](../../docs/BREW_FEASIBILITY.md).

## Development strategy

Reuse license-cleared components (for example upstream's separately BSD-3-Clause
BREW_AppUI) and preserve the working architecture where permissions allow.
Replace proprietary Qualcomm runtime objects, SDK dependencies, and any
unlicensed third-party pieces with our own GPLv3-compatible implementations.
Avoid an unnecessary rewrite of licensed code.

**Important:** Public GitHub branches are public distribution of their source.
Melange's main repository currently has no explicit license. While waiting for
written permission, keep any upstream source checkout, copies and experimental
modifications **local and untracked**. Do not push upstream files even to this
research branch or place them in a PR. A private experiment is not a substitute
for obtaining the necessary legal rights; confirm the applicable permissions
before modifying/reusing code.

### Prepare a pinned local baseline (requires network and git)

Run from the AbyssME root:

```sh
bash engines/brew/tools/setup-local-melange.sh
```

This checks out upstream commit `59b66aebd25fdcc8f422f55b0d56616476073bbf`
in `engines/brew/melange-upstream/`, ignored by this repository, and creates
a local `abyssme-local-experiment` branch. It does not pull or bundle Qualcomm
SDK objects; builds will remain blocked until those dependencies are lawfully
replaced. Network access and upstream availability are required.

Initial native bridge work can live in `engines/brew/native/` independently of
the unlicensed upstream. Keep game samples, firmware, vendor binaries, dumps and
generated artifacts outside Git.

## Gates for promotion

1. Obtain a GPLv3-compatible grant for any retained Melange core code; document
   provenance and per-component licenses. BSD_AppUI permission does *not* cover
   the main Melange source.
2. Replace all missing/nonredistributable BREW `*.fixed.lib` dependencies and
   Qualcomm SDK headers with licensed alternatives or owned implementations.
3. Prove a reproducible Android build and a guest-code execution strategy,
   including 64-bit-only devices (ARMv7 code is not ARM64-native).
4. Verify licensed test-app execution, display, input, audio, storage and clean
   shutdown without affecting existing engines. Only then add a library tab.
