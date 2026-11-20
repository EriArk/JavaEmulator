#!/usr/bin/env bash
set -eu
: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to the Android NDK}"
src=${1:?Pass the Potator source checkout}
out=${2:-build/supervision/jniLibs}/arm64-v8a
test "$(git -c safe.directory='*' -C "$src" rev-parse HEAD)" = 227c5f6f3ce74d32e9002ce24c1420288559a860
mkdir -p "$out"
retro="$src/platform/libretro"
common="$src/common"
"$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android29-clang" \
    -std=gnu11 -O2 -fPIC -shared -D__LIBRETRO__ -Wl,-z,max-page-size=16384 -Wl,--no-undefined \
    -I"$retro" -I"$retro/libretro-common/include" -I"$common" -I"$common/m6502" \
    "$retro/libretro.c" "$common"/{controls,gpu,memorymap,sound,timer,watara}.c \
    "$common/m6502/m6502.c" "$(dirname "$0")/native/frontend.c" -laaudio -llog -lm -o "$out/libabysssupervision.so"
