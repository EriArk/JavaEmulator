#!/usr/bin/env bash
set -eu
: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to the Android NDK}"
src=${1:?Pass the Potator source checkout}
out=${2:-build/supervision/jniLibs}/arm64-v8a
here=$(cd "$(dirname "$0")" && pwd)
stage=$(mktemp -d)
trap 'rm -rf "$stage"' EXIT
bash "$here/prepare-core.sh" "$src" "${VREMU6502_SRC:-$(dirname "$src")/vrEmu6502}" "$stage"
mkdir -p "$out"
retro="$stage/platform/libretro"
common="$stage/common"
"$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android29-clang" \
    -std=gnu11 -O2 -fPIC -shared -D__LIBRETRO__ -Wl,-z,max-page-size=16384 -Wl,--no-undefined \
    -I"$retro" -I"$retro/libretro-common/include" -I"$common" -I"$stage/src" -I"$here/native" \
    "$retro/libretro.c" "$common"/{controls,gpu,memorymap,sound,timer,watara}.c \
    "$here/native/watara_cpu.c" "$here/native/frontend.c" -laaudio -llog -lm -o "$out/libabysssupervision.so"
