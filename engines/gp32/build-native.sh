#!/usr/bin/env bash
set -eu
: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to the Android NDK}"
src=${1:?Pass a GP32emu checkout}
pin=6e5091f115fb1976f128a9696f59b2e421eb62b0
work=build/gp32/native
out=build/gp32/jniLibs/arm64-v8a
mkdir -p "$work" "$out"
git -C "$src" archive "$pin" include src | tar -x -C "$work"
python3 engines/gp32/prepare-core.py "$work"
bin="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin"
cc="$bin/aarch64-linux-android29-clang"
flags=(-std=gnu11 -O3 -fPIC -fvisibility=hidden -DGP32EMU_INTERPRETER_ONLY=1
       -DGP32EMU_ENABLE_THREADS=0 -I"$work/include" -I"$work/src")
objects=()
for unit in gp32 arm920t s3c2400 smartmedia smc_direct fxe fpk gp32_zip audio/gp32_audio_resampler media/gp32_video_effects third_party/zip/zip; do
    obj="$work/$unit.o"
    mkdir -p "$(dirname "$obj")"
    "$cc" "${flags[@]}" -D_FILE_OFFSET_BITS=64 -DZIP_HAVE_SYMLINK=0 -DZIP_ENABLE_DEFLATE=0 -c "$work/src/$unit.c" -o "$obj"
    objects+=("$obj")
done
"$cc" "${flags[@]}" -shared -Wl,-z,max-page-size=16384 -Wl,--no-undefined \
    engines/gp32/native/{frontend,backend}.c "${objects[@]}" -laaudio -llog -lm -o "$out/libabyssgp32.so"
