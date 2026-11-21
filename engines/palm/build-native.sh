#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
out="$root/build/palm"
rev=064d5d578bcd1f9072de384d05aa217f83db2f7f
src=${PALM_SOURCE_DIR:-"$out/sources/cloudpilot"}
if [[ ! -d "$src/.git" ]]; then
    git clone --depth 1 --branch 2.3.0 --filter=blob:none --sparse https://github.com/cloudpilot-emu/cloudpilot-emu.git "$src"
    git -C "$src" sparse-checkout set src/uarm src/common
fi
[[ "$(git -C "$src" rev-parse HEAD)" == "$rev" ]] || { echo 'Unexpected CloudpilotEmu revision' >&2; exit 1; }
ndk=${ANDROID_NDK_HOME:-"${ANDROID_HOME:?}/ndk/28.2.13676358"}
cmake -S "$root/engines/palm/native" -B "$out/native" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$ndk/build/cmake/android.toolchain.cmake" -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-29 -DANDROID_STL=c++_static -DCMAKE_BUILD_TYPE=Release -DPALM_SOURCE_DIR="$src"
cmake --build "$out/native" --parallel "${BUILD_JOBS:-4}"
dest="$out/jniLibs/arm64-v8a/libabysspalm_runner.so"
mkdir -p "$(dirname "$dest")"
cp "$out/native/palm-runner" "$dest"
strip_tools=("$ndk"/toolchains/llvm/prebuilt/*/bin/llvm-strip)
"${strip_tools[0]}" --strip-all "$dest"
