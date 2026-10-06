# Sudoku J2ME 0.1

- Author: John Woodell; the MIDlet manifest uses vendor `Netpress.com`.
- Upstream: [woodie/Sudoku-J2ME](https://github.com/woodie/Sudoku-J2ME).
- Pinned revision: [`2f31ba6d1d8b3ebf03b5526641b28566f0cf2d68`](https://github.com/woodie/Sudoku-J2ME/tree/2f31ba6d1d8b3ebf03b5526641b28566f0cf2d68).
- License: [MIT](LICENSE), copyright (c) 2019 John Woodell.
- Retrieved: 2026-10-06. No changes to upstream files or the source archive.

## Files and sources

| Local file | Authoritative source |
| --- | --- |
| `SudokuJ2ME-0.1.jar` | [Pinned upstream JAR](https://raw.githubusercontent.com/woodie/Sudoku-J2ME/2f31ba6d1d8b3ebf03b5526641b28566f0cf2d68/dist/SudokuJ2ME-0.1.jar) |
| `SudokuJ2ME-0.1.jad` | [Pinned upstream JAD](https://raw.githubusercontent.com/woodie/Sudoku-J2ME/2f31ba6d1d8b3ebf03b5526641b28566f0cf2d68/dist/SudokuJ2ME-0.1.jad) |
| `LICENSE` | [Pinned upstream license](https://raw.githubusercontent.com/woodie/Sudoku-J2ME/2f31ba6d1d8b3ebf03b5526641b28566f0cf2d68/LICENSE) |
| `SudokuJ2ME-source-2f31ba6.zip` | [Full source snapshot](https://codeload.github.com/woodie/Sudoku-J2ME/zip/2f31ba6d1d8b3ebf03b5526641b28566f0cf2d68) |

The source archive includes the author's sources, resources, build script and
published binaries at that revision. It was not rebuilt here. Preserve the MIT
copyright and permission notice when redistributing these files. File hashes
are in [SHA256SUMS](SHA256SUMS).

## Intended checks

1. Import the JAR and verify title `Sudoku J2ME`, vendor `Netpress.com`, version
   `0.1` and the declared `icon40x40.png` icon.
2. Import the JAD with the JAR beside it; the declared JAR name and byte size
   must match. Record any file-provider or sibling-access limitation separately.
3. Start the game and check menus, board rendering, numeric controls and exit.
4. Repeat with the Phone virtual keypad and Handheld mappings, noting any
   display setting that must be adjusted.

The manifest requests MIDP 2.0 and CLDC 1.1. The file/archive integrity and
metadata were checked when adding this fixture; no fresh AbyssME gameplay,
JAD-import or physical-device pass is claimed. It is not a replacement for
MIDP 1.0, 3D, vendor-specific APIs or broad compatibility testing.
