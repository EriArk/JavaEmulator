#!/usr/bin/env bash
set -eu
: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to the Android NDK}"
src=${1:?Pass the SameDuck source checkout}
out=${2:-build/megaduck/jniLibs}/arm64-v8a
test "$(git -c safe.directory='*' -C "$src" rev-parse HEAD)" = 5619abdb01cee6bedb47599cdb5532c318443b52
mkdir -p "$out"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/Core" "$tmp/libretro"
cp "$src/Core/gb.c" "$tmp/Core/"
cp "$src/libretro/libretro.c" "$tmp/libretro/"
patch --quiet -d "$tmp" -p1 < "$(dirname "$0")/android.patch"
"$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android29-clang" \
    -std=gnu11 -O2 -fPIC -shared -DGB_INTERNAL -D__LIBRETRO__ -DSAMEBOY_CORE_VERSION='"0.13.6"' \
    -DGB_DISABLE_TIMEKEEPING -DGB_DISABLE_REWIND -DGB_DISABLE_DEBUGGER -DGB_DISABLE_CHEATS \
    -Wno-multichar -Wl,-z,max-page-size=16384 -Wl,--no-undefined -I"$src" -I"$src/libretro" -I"$src/Core" \
    "$tmp/Core/gb.c" "$src"/Core/{apu,memory,timing,display,symbol_hash,camera,sm83_cpu,joypad,save_state,random,rumble}.c \
    "$tmp/libretro/libretro.c" "$(dirname "$0")/native/frontend.c" -laaudio -llog -lm -o "$out/libabyssmegaduck.so"
