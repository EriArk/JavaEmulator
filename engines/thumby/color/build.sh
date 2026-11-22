#!/usr/bin/env bash
# Build the TinyCircuits WebAssembly port in a dedicated checkout.
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
root=$(cd "$here/../../.." && pwd)
mp=${1:-"$root/build/thumby-color/micropython"}
out=${2:-"$root/build/thumby-color/assets"}
rev=28cfbc90d4122f4e900c82ff14ff6c7ebd211711
if [[ ! -d "$mp/.git" ]]; then
    mkdir -p "$mp"
    git -C "$mp" init
    git -C "$mp" fetch --depth 1 https://github.com/TinyCircuits/micropython "$rev"
    git -C "$mp" checkout --detach FETCH_HEAD
fi
[[ $(git -C "$mp" rev-parse HEAD) == "$rev" ]]
git -C "$mp" submodule update --init TinyCircuits-Tiny-Game-Engine
engine="$mp/TinyCircuits-Tiny-Game-Engine"
git -C "$engine" submodule update --init --recursive
for target in micropython engine; do
    tree="$mp"
    [[ "$target" != engine ]] || tree="$engine"
    patch="$here/native/$target.patch"
    if ! git -C "$tree" apply --unidiff-zero --ignore-space-change --reverse --check "$patch" 2>/dev/null; then
        git -C "$tree" apply --unidiff-zero --ignore-space-change "$patch"
    fi
done
cp "$here/native/mixer.inc" "$engine/src/audio/abyss_color_mixer.inc"
make -C "$mp/mpy-cross" -j4 CC="${HOST_CC:-cc} -Wno-unterminated-string-initialization"
make -C "$mp/ports/webassembly" submodules
make -C "$mp/ports/webassembly" -j4 USER_C_MODULES=../../TinyCircuits-Tiny-Game-Engine
python3 "$here/stage.py" "$mp" "$out"
