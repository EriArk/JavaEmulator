#!/usr/bin/env bash
set -eu
: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to the Android NDK}"
core=$(realpath "${1:?Pass the pinned libretro/mame source checkout}")
out=$(realpath -m "${2:-build/gameking}")
here=$(cd "$(dirname "$0")" && pwd)
make -C "$core" -j"${JOBS:-4}" -f Makefile.libretro platform=android-arm64 SUBTARGET=gk \
    REGENIE=1 SOURCES=src/mame/handheld/gameking.cpp,src/mame/handheld/gmaster.cpp,src/mame/epoch/gamepock.cpp NO_OPENGL=1 NO_USE_PORTAUDIO=1 NO_USE_MIDI=1 \
    USE_QTDEBUG=0 SYMBOLS=0 OPTIMIZE=2
libs=$out/jniLibs/arm64-v8a
mkdir -p "$libs" "$out/assets/licenses/gameking"
cp "$core/gk_libretro_android.so" "$libs/libgkmame.so"
cp "$core/COPYING" "$out/assets/licenses/gameking/MAME.txt"
cp -r "$core/docs/legal" "$out/assets/licenses/gameking/"
"$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android29-clang" \
    -std=gnu11 -O2 -fPIC -shared -Wl,-z,max-page-size=16384 -Wl,--no-undefined \
    -I"$core/src/osd/libretro/libretro-internal" "$here/native/frontend.c" "$here/native/options.c" \
    -L"$libs" -lgkmame -laaudio -llog -o "$libs/libabyssgameking.so"
