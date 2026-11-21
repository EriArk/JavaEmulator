#!/bin/sh
# SPDX-License-Identifier: GPL-3.0-only
set -eu
: "${ANDROID_NDK_HOME:?Set ANDROID_NDK_HOME to the Android NDK}"
bin="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin"
export LIBCLANG_PATH="${LIBCLANG_PATH:-$bin/../lib}"
export BINDGEN_EXTRA_CLANG_ARGS="--target=aarch64-linux-android29 --sysroot=$bin/../sysroot -isystem $("$bin/clang" -print-resource-dir)/include"
export CC_aarch64_linux_android="$bin/aarch64-linux-android29-clang" CXX_aarch64_linux_android="$bin/aarch64-linux-android29-clang++" AR_aarch64_linux_android="$bin/llvm-ar" CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$bin/aarch64-linux-android29-clang"
xmp="${LIBXMP_SOURCE:?Set LIBXMP_SOURCE to the pinned libxmp checkout}"
xmp_build="$(pwd)/target/abyss-xmp"
cmake -S "$xmp" -B "$xmp_build" -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
    -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=29 -DBUILD_LITE=ON -DBUILD_STATIC=ON -DBUILD_SHARED=OFF -DCMAKE_POSITION_INDEPENDENT_CODE=ON -DCMAKE_BUILD_TYPE=Release
cmake --build "$xmp_build" --target xmp_lite_static -j4
export ABYSS_XMP_LIB="$xmp_build"
export LIBRARY_PATH="$xmp_build:${LIBRARY_PATH:-}"
cargo build --locked --release -p pocket-android-jni --target aarch64-linux-android
if [ -n "${ABYSSME_ROOT:-}" ]; then
    destination="$ABYSSME_ROOT/build/pocketpc"
    abi="$destination/jniLibs/arm64-v8a"
    notices="$destination/assets/licenses/pocketpc"
    mkdir -p "$abi" "$notices"
    cp "${CARGO_TARGET_DIR:-target}/aarch64-linux-android/release/libpockethle_jni.so" "$abi/"
    cp "$xmp/lite/README" "$notices/libxmp-lite.txt"
    cp LICENSE LICENSE-MIT LICENSE-APACHE "$notices/"
    unicorn=$(find "${CARGO_HOME:-$HOME/.cargo}/registry/src" -type d -name unicorn-engine-sys-2.1.5 -print -quit)
    for notice in COPYING COPYING.LGPL2 COPYING_GLIB AUTHORS.TXT CREDITS.TXT; do
        cp "$unicorn/$notice" "$notices/"
    done
fi
