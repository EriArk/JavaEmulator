#!/usr/bin/env bash
set -eu
: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to the Android NDK}"
core=${1:?Pass the upstream ARM64 libretro shared library}
out=${2:-build/dingoo/jniLibs}/arm64-v8a
printf '%s  %s\n' 18f58f6a47b0ade058aa7e57e42ac4c1cfa3382b8b8f7d004831c29a05af8989 "$core" | sha256sum -c -
mkdir -p "$out"
cp "$core" "$out/libdingooemu.so"
"$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android29-clang" \
    -std=c11 -O2 -shared -fPIC -Wl,-z,max-page-size=16384 -Wl,--no-undefined \
    "$(dirname "$0")/native/frontend.c" -L"$out" -ldingooemu -laaudio -llog -o "$out/libabyssdingoo.so"
