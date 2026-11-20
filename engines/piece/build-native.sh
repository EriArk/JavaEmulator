#!/usr/bin/env bash
set -eu
: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to the Android NDK}"
src=${1:?Pass the piece-emu checkout}
pin=9aedad032c5d06a8c6d7cf74142a2b9ac32d77ac
test "$(git -C "$src" rev-parse HEAD)" = "$pin"
git -C "$src" diff --quiet HEAD -- src
work=build/piece/native
out=build/piece/jniLibs/arm64-v8a
mkdir -p "$out"
cmake -S "$src/src" -B "$work" -G Ninja -DPIECE_LIBRETRO_ONLY=ON -DLIBRETRO_STATIC=ON \
    -DCMAKE_BUILD_TYPE=Release -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
    -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-29 -DANDROID_STL=c++_static
cmake --build "$work" --target piece_libretro --parallel 4
bin="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin"
"$bin/aarch64-linux-android29-clang" -std=c11 -O2 -fPIC -c \
    engines/piece/native/frontend.c -o "$work/frontend.o"
"$bin/aarch64-linux-android29-clang++" -shared -static-libstdc++ -Wl,-z,max-page-size=16384 \
    -Wl,--no-undefined "$work/frontend.o" "$work/libretro/piece_libretro.a" \
    -laaudio -llog -o "$out/libabysspiece.so"
