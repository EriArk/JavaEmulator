#!/usr/bin/env bash
set -eu
: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to the Android NDK}"
checkout=${1:?Pass the Dragonfruit source checkout}
out=${2:-build/cybiko/jniLibs}/arm64-v8a
test "$(git -c safe.directory='*' -C "$checkout" rev-parse HEAD)" = f142c9287f156b559de8d98b0030d2432b938e48
src="$checkout/src/components"
mkdir -p "$out"
"$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android29-clang" \
    -std=gnu11 -O2 -fPIC -shared -Wl,-z,max-page-size=16384 -Wl,--no-undefined \
    -I"$src/cybiko/include" -I"$src/h8s/include" \
    "$(dirname "$0")/native/frontend.c" "$src"/cybiko/{cybiko,cy_classic,cy_disk,cy_devices,cy_dma,cy_cart,cy_rf,cy_periph,cy_hle,cy_usb,cy_mp3c}.c \
    "$src/h8s/h8s.c" -laaudio -llog -lm -lz -o "$out/libabysscybiko.so"
