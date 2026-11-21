#!/usr/bin/env bash
set -eu
: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to the Android NDK}"
src=$(realpath "${1:?Pass the Ardens checkout with deps/miniz initialized}")
out=${2:-build/arduboy/jniLibs}/arm64-v8a
test "$(git -c safe.directory='*' -C "$src" rev-parse HEAD)" = e811cc6be72c8a802bd3265a84daf4165c177b93
mkdir -p "$out"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
module=$(realpath "$(dirname "$0")")
cmake -S "$src" -B "$tmp" -G Ninja -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-29 -DANDROID_STL=c++_static \
    -DARDENS_LIBRETRO=ON -DCMAKE_PROJECT_Ardens_INCLUDE="$module/frontend.cmake"
cmake --build "$tmp" --target abyssarduboy --parallel 3
cp "$tmp/libabyssarduboy.so" "$out/"
