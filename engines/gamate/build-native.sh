#!/usr/bin/env bash
set -eu
: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to the Android NDK}"
chips=${1:?Pass a directory containing the pinned m6502.h and ay38910.h}
out=${2:-build/gamate/jniLibs}/arm64-v8a
(cd "$chips" && sha256sum -c - <<'SUMS'
17d88a078d197f8fcfa9b3db5881aba01a6e861f0ac1c477045eb3eca4a4afb8  ay38910.h
b3fc32a01f1f7cdc62f919ccba06cc20c2a333e7ca30c38189e31aa65868d46e  m6502.h
SUMS
)
mkdir -p "$out"
src=$(dirname "$0")/native
"$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android29-clang" \
    -std=gnu11 -O2 -shared -fPIC -Wl,-z,max-page-size=16384 -Wl,--no-undefined \
    -I"$chips" "$src/machine.c" "$src/frontend.c" -laaudio -llog -lm -o "$out/libabyssgamate.so"
