#!/usr/bin/env bash
set -eu
: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to Android NDK 28}"
src=$(realpath "${1:?Pass the PokittoEmu v1.0.5 checkout}")
out=$(realpath -m "${2:-build/pokitto/jniLibs}/arm64-v8a")
test "$(git -c safe.directory='*' -C "$src" rev-parse HEAD)" = 1bc908e39256accd427a0b8590bfcc2ef2e3c0d4
module=$(realpath "$(dirname "$0")")
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$out"
git -c safe.directory='*' -C "$src" archive HEAD | tar x -C "$tmp"
cd "$tmp"
sed -i 's/\r$//' thumb2.cpp
git apply --unidiff-zero "$module/android.patch"
tool="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin"
cc="$tool/aarch64-linux-android29-clang"
cxx="$cc++"
flags=(-O3 -fPIC -fno-strict-aliasing -std=c++17 -DPOKITTO_ANDROID -include cstring -include vector -I.)
for unit in sys iocon gpio adc iap timers usart rtc thumb2 screen mmu cpu sd sct spi ChaN/ff ChaN/ccsbcs "$module/native/machine" "$module/native/card"; do
    "$cxx" "${flags[@]}" -c "$unit.cpp" -o "$(basename "$unit").o"
done
"$cxx" -shared -static-libstdc++ -Wl,-z,defs -Wl,-z,max-page-size=16384 -o "$out/libpokittomachine.so" *.o
"$cc" -O2 -fPIC -shared -Wl,-z,defs -Wl,-z,max-page-size=16384 "$module/native/frontend.c" -laaudio -llog -ldl -o "$out/libabysspokitto.so"
"$tool/llvm-strip" --strip-unneeded "$out"/*.so
