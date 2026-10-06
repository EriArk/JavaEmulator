# Test fixtures

These are separately licensed development fixtures, not games bundled with the
AbyssME APK. Start with [Sudoku J2ME 0.1](sudoku-j2me/README.md), an unmodified
MIT-licensed example with its JAR, JAD, source snapshot and copyright notice.

Every new fixture must include an authoritative source, applicable license or
permission, pinned version/checksum and the behavior it exercises. Prefer small,
source-available examples with explicit redistribution terms. The emulator's
Apache-2.0 license does not replace a third-party fixture's license.

## Historical fixtures

Stalker, KoalaMines and the extracted Stalker artwork were removed from the
current tree following the maintainer's decision in
[#5](https://github.com/EriArk/JavaEmulator/issues/5). Their provenance had not
been documented. Personal test copies were preserved locally, outside the
repository. Git history was not rewritten; older commits still contain these
files, and historical reports may still refer to them.

Sudoku exercises MIDP 2.0/CLDC 1.1, manifest/icon handling and a numeric-keypad
game. It does not replace the old MIDP 1.0 or vendor-specific coverage; those
gaps remain in [#4](https://github.com/EriArk/JavaEmulator/issues/4).

The separately installed games used for the beta.2 website screenshots have
their source links listed in the [capture manifest](../screenshots/website-beta2/manifest.json);
their JARs are not part of that screenshot pack.
