#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
set -eu
set -o pipefail
src=${1:?Pass the littlefs source checkout}
out=${2:?Pass the generated Thumby assets directory}
here=$(cd "$(dirname "$0")" && pwd)
temp=$(mktemp -d)
trap 'rm -rf "$temp"' EXIT
git -C "$src" archive 1863dc7883d82bd6ca79faa164b65341064d1c16 \
    lfs.c lfs.h lfs_util.c lfs_util.h LICENSE.md | tar -x -C "$temp"
emcc -O2 -I"$temp" "$temp"/{lfs,lfs_util}.c "$here/littlefs.c" \
    -sMODULARIZE -sEXPORT_ES6 -sENVIRONMENT=web -sALLOW_TABLE_GROWTH \
    -sEXPORTED_FUNCTIONS=_start_disk,_make_directory,_finish_disk,_store_file,_malloc,_free \
    -sEXPORTED_RUNTIME_METHODS=addFunction,cwrap,HEAPU8 -o "$temp/littlefs.js"
mkdir -p "$out/thumby" "$out/licenses"
cp "$temp"/littlefs.{js,wasm} "$out/thumby/"
cp "$temp/LICENSE.md" "$out/licenses/thumby-littlefs.txt"
